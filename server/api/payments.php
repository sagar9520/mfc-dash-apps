<?php
/**
 * Payments (in-app purchases) — MFC app ki Payments screen (login zaroori, wahi session jo data.php ka). Sirf padhna, DB me kuch nahi likhta.
 *
 * GET ?app=all|<package>&range=today|yesterday|7d|30d  ya  &from=YYYY-MM-DD&to=YYYY-MM-DD   (period data.php / app.php jaisa, app ke time zone ke din)
 *   totals  : {amount (₹, paid orders ka jod), paid (orders), failed, started}
 *   apps    : [{pkg, name, amount, paid, failed, started}] amount zyada -> kam (sirf jin apps me kuch hua)
 *   items   : sirf app=<package>: [{ts (local "Y-m-d H:i:s"), pkg, plan, amount, status paid|failed|started, order, reason}] naya pehle, max 500.
 *             started = shuru hua par us device + plan ka koi paid / failed baad me nahi aaya (beech me chhoda).
 *             reason = sirf jab event me sach me ho (failed event ka p2 order id nahi, jaise "USER_CANCELED" / "1" -> "Cancelled"), warna "".
 *   raw_from: sabse purana din jiska raw events data poora hai (config raw_days, default 35). Usse purane din ke totals agg_pay se:
 *             source = raw | agg | mixed, agg_from / agg_to = agg_pay wale din, agg_counts = agg_pay me ginti (paid / failed) thi ya sirf ₹.
 *             items sirf raw din ke hote hain.
 * events: ev purchase_start / purchase_done / purchase_failed; p1 = plan (product id), p2 = order id, val = ₹ amount.
 * purchase_done ek order (pkg, did, p2) ek hi baar (app.php / alerts.php / studio.php jaisa: SDK dobara bheje to bhi).
 * Sasta: events par (pkg, ts) index — har query pkg = / pkg IN (...) + ts range, range ke bahar kuch nahi padhta. Index me ev nahi,
 * to range ke saare events chhoote hain: isliye har request events par ek hi padhai (ek app: items wali rows se hi totals).
 */
declare(strict_types=1);
require_once __DIR__ . '/../lib/common.php';
require_login_json();
if (session_status() === PHP_SESSION_ACTIVE) session_write_close();   // session lock chhodo: warna app ki baaki requests atak kar timeout hoti hain

$pdo = db();
$apps = apps();
$tz = tz();
$today = local_today();

// ---- period (app.php jaisa)
$app = trim((string)($_GET['app'] ?? 'all'));
if ($app === '' || !preg_match('/^[A-Za-z0-9._-]{1,100}$/', $app)) $app = 'all';
$isDay = fn($v) => is_string($v) && preg_match('/^\d{4}-\d{2}-\d{2}$/', $v);
$back = fn(string $day, int $n) => (new DateTime($day))->modify("-$n day")->format('Y-m-d');
$d1 = (string)($_GET['from'] ?? ''); $d2 = (string)($_GET['to'] ?? '');
$preset = (string)($_GET['range'] ?? '');
if ($preset === 'today') { $d1 = $d2 = $today; }
elseif ($preset === 'yesterday') { $d1 = $d2 = $back($today, 1); }
elseif ($preset === '7d') { $d1 = $back($today, 6); $d2 = $today; }
elseif ($preset === '30d') { $d1 = $back($today, 29); $d2 = $today; }
if (!$isDay($d1)) $d1 = $today;
if (!$isDay($d2)) $d2 = $d1;
if ($d2 < $d1) [$d1, $d2] = [$d2, $d1];
if ($d2 > $today) $d2 = $today;
if ($d1 > $d2) $d1 = $d2;
$minD1 = $back($d2, 365);   // agg_pay sasta hai, phir bhi ek saal se zyada nahi
if ($d1 < $minD1) $d1 = $minD1;

// raw events kitne din rehte hain: raw_from se pehle ke din agg_pay (roz ka jod) se
$rawDays = max(1, (int)(cfg('raw_days') ?: 35));
$rawFrom = $back($today, $rawDays - 1);

function pay_q(PDO $pdo, string $sql, array $p = []): array { $st = $pdo->prepare($sql); $st->execute($p); return $st->fetchAll(); }

/** Ek app ki items ke liye zyada se zyada itni raw rows (naya pehle). Isse kam = totals bhi inhi se. */
const PAY_RAW_MAX = 3000;

/** Failed event ka p2: order id hai (GPA.xxxx / lamba token) ya fail hone ki wajah? Wajah ho to chhota saaf shabd, warna "". */
function pay_reason(string $p2): string
{
    $s = trim($p2);
    if ($s === '' || preg_match('/^GPA\./i', $s)) return '';
    if (strlen($s) >= 20 && preg_match('/^[A-Za-z0-9._:\-]+$/', $s) && preg_match('/\d/', $s)) return '';   // purchase token / order id jaisa
    // number pehle (minus ke saath): "-1" / "code=-3" / "CODE -2"; '-' ko '_' banane ke baad "-1" = "_1" ho jaata tha
    if (preg_match('/^(?:code\s*[=:_ ]?\s*)?(-?\d+)$/i', $s, $m)) $k = $m[1];
    else $k = strtoupper(preg_replace('/[\s\-]+/', '_', $s));
    // Play Billing BillingResponseCode (number ya naam)
    $map = ['1' => 'Cancelled', 'USER_CANCELED' => 'Cancelled', 'USER_CANCELLED' => 'Cancelled', 'CANCELED' => 'Cancelled', 'CANCELLED' => 'Cancelled',
        '2' => 'No network', 'SERVICE_UNAVAILABLE' => 'No network', '12' => 'No network', 'NETWORK_ERROR' => 'No network',
        '3' => 'Billing unavailable', 'BILLING_UNAVAILABLE' => 'Billing unavailable', '4' => 'Item unavailable', 'ITEM_UNAVAILABLE' => 'Item unavailable',
        '5' => 'Developer error', 'DEVELOPER_ERROR' => 'Developer error', '6' => 'Play error', 'ERROR' => 'Play error',
        '7' => 'Already owned', 'ITEM_ALREADY_OWNED' => 'Already owned', '8' => 'Not owned', 'ITEM_NOT_OWNED' => 'Not owned',
        '-1' => 'Play disconnected', 'SERVICE_DISCONNECTED' => 'Play disconnected', '-2' => 'Not supported', 'FEATURE_NOT_SUPPORTED' => 'Not supported',
        '-3' => 'Play timeout', 'SERVICE_TIMEOUT' => 'Play timeout', 'PENDING' => 'Payment pending'];
    if (isset($map[$k])) return $map[$k];
    if (stripos($s, 'declin') !== false) return 'Card declined';
    if (stripos($s, 'cancel') !== false) return 'Cancelled';
    $s = preg_replace('/\s+/', ' ', str_replace('_', ' ', $s));
    return mb_strlen($s) > 60 ? rtrim(mb_substr($s, 0, 59)) . '…' : $s;
}

/** UTC DATETIME(3) -> app ke time zone ka "Y-m-d H:i:s". */
function pay_local(string $ts, DateTimeZone $tz): string
{
    try { return (new DateTime(substr($ts, 0, 19), new DateTimeZone('UTC')))->setTimezone($tz)->format('Y-m-d H:i:s'); }
    catch (Throwable $e) { return substr($ts, 0, 19); }
}

$per = [];   // pkg => totals
$add = function (string $pkg, string $k, float $v) use (&$per): void {
    $per[$pkg] = $per[$pkg] ?? ['amount' => 0.0, 'paid' => 0, 'failed' => 0, 'started' => 0];
    $per[$pkg][$k] += $k === 'amount' ? $v : (int)round($v);
};

// ---------------------------------------------------------------- purane din (raw ke bahar): agg_pay (roz ka jod)
$source = 'raw';
$aggFrom = ''; $aggTo = ''; $aggCounts = false;
if ($d1 < $rawFrom) {
    $aggFrom = $d1;
    $aggTo = min($d2, $back($rawFrom, 1));
    $source = $d2 >= $rawFrom ? 'mixed' : 'agg';
    try {
        $cols = array_map(fn($r) => strtolower((string)$r['Field']), pay_q($pdo, 'SHOW COLUMNS FROM agg_pay'));
        $nCol = null;
        foreach (['n', 'c', 'cnt', 'orders'] as $cn) if (in_array($cn, $cols, true)) { $nCol = $cn; break; }
        $aggCounts = $nCol !== null;
        $pf = $app === 'all' ? '' : ' AND pkg = :pkg';
        $pp = $app === 'all' ? [] : [':pkg' => $app];
        $sql = "SELECT pkg, ev, SUM(v) amt" . ($nCol ? ", SUM($nCol) n" : '') . " FROM agg_pay
            WHERE day >= :d1 AND day <= :d2 AND ev IN ('purchase_done','purchase_failed','purchase_start')$pf GROUP BY pkg, ev";
        foreach (pay_q($pdo, $sql, [':d1' => $aggFrom, ':d2' => $aggTo] + $pp) as $r) {
            $pkg = (string)$r['pkg'];
            if ($r['ev'] === 'purchase_done') { $add($pkg, 'amount', (float)$r['amt']); if ($nCol) $add($pkg, 'paid', (float)$r['n']); }
            elseif ($nCol) $add($pkg, $r['ev'] === 'purchase_failed' ? 'failed' : 'started', (float)$r['n']);
        }
    } catch (Throwable $e) {
        $aggCounts = false;   // agg_pay table nahi / alag columns: purane din khaali (app raw_from dikhata hai)
    }
}

// ---------------------------------------------------------------- raw events (raw_from .. d2)
$rf = max($d1, $rawFrom);
$items = [];
if ($rf <= $d2) {
    [$f] = day_bounds_utc($rf);
    [, $t] = day_bounds_utc($d2);
    // pkg filter: ek app = pkg = ?; sab = events ke apps ki list (index ka loose scan, sasta) -> pkg IN (...): (pkg, ts) index ka range
    if ($app === 'all') {
        $pk = array_values(array_unique(array_merge(array_map('strval', array_keys($apps)), array_map(fn($r) => (string)$r['pkg'], pay_q($pdo, 'SELECT DISTINCT pkg FROM events')))));
    } else $pk = [$app];
    if ($pk) {
        $ph = []; $pp = [':f' => $f, ':t' => $t];
        foreach ($pk as $i => $p) { $ph[] = ":p$i"; $pp[":p$i"] = $p; }
        $pin = 'pkg IN (' . implode(',', $ph) . ')';
        // (pkg, ts) index me ev nahi: har padhai range ke SAARE events (session, crash, ...) chhooti hai. Isliye events par ek hi
        // padhai: ek app = neeche wali items rows se totals bhi (3000 se kam hon); sab apps (ya 3000 bhar gaye) = yeh ek query.
        $rows = null;
        if ($app !== 'all') {
            $rows = pay_q($pdo, "SELECT ts, ev, did, p1, LEFT(COALESCE(p2, ''), 300) p2, val FROM events
                WHERE pkg = :p0 AND ev IN ('purchase_done','purchase_failed','purchase_start') AND ts >= :f AND ts < :t ORDER BY ts DESC, id DESC LIMIT " . PAY_RAW_MAX,
                [':p0' => $app, ':f' => $f, ':t' => $t]);
        }
        if ($rows !== null && count($rows) < PAY_RAW_MAX) {
            // paid: ek order (did, p2) ek baar, amount = MAX(val); failed / started = ginti (neeche wali query jaisa)
            $ord = [];
            foreach ($rows as $r) {
                if ($r['ev'] === 'purchase_done') { $ok = $r['did'] . '|' . $r['p2']; $ord[$ok] = max($ord[$ok] ?? 0.0, (float)$r['val']); }
                else $add($app, $r['ev'] === 'purchase_failed' ? 'failed' : 'started', 1);
            }
            foreach ($ord as $v) { $add($app, 'paid', 1); $add($app, 'amount', $v); }
        } else {
            // teeno ev ek saath: done = order (pkg, did, p2) par joda (MAX(val)), failed / start = device par ginti; jod PHP me
            foreach (pay_q($pdo, "SELECT pkg, ev, did, IF(ev = 'purchase_done', LEFT(COALESCE(p2, ''), 300), '') o, MAX(val) v, COUNT(*) n FROM events
                WHERE $pin AND ev IN ('purchase_done','purchase_failed','purchase_start') AND ts >= :f AND ts < :t GROUP BY pkg, ev, did, o", $pp) as $r) {
                $pkg = (string)$r['pkg'];
                if ($r['ev'] === 'purchase_done') { $add($pkg, 'paid', 1); $add($pkg, 'amount', (float)$r['v']); }
                else $add($pkg, $r['ev'] === 'purchase_failed' ? 'failed' : 'started', (float)$r['n']);
            }
        }
        // ek app: har payment (naya pehle). Duplicate order ek row (pehli baar ka waqt), shuru-par-adhoora = started.
        if ($rows !== null) {
            $price = [];   // plan => sabse naya non-zero amount (failed event me amount na ho to wahi)
            foreach ($rows as $r) if ((float)$r['val'] > 0 && $r['ev'] !== 'purchase_failed') $price[(string)$r['p1']] = $price[(string)$r['p1']] ?? (float)$r['val'];
            $seen = []; $fin = [];
            foreach ($rows as $r) {
                $ev = (string)$r['ev']; $did = (string)$r['did']; $plan = (string)$r['p1']; $p2 = (string)$r['p2'];
                $fk = $did . '|' . $plan;
                if ($ev === 'purchase_done') {
                    $ok = $did . '|' . $p2;
                    if (isset($seen[$ok])) {   // wahi order dobara aaya (purana copy): pehli baar ka waqt, bada amount
                        $j = $seen[$ok];
                        $items[$j]['ts'] = pay_local((string)$r['ts'], $tz);
                        $items[$j]['amount'] = max($items[$j]['amount'], (float)$r['val']);
                        continue;
                    }
                    $fin[$fk] = ($fin[$fk] ?? 0) + 1;
                    $seen[$ok] = count($items);
                    $items[] = ['ts' => pay_local((string)$r['ts'], $tz), 'pkg' => $app, 'plan' => $plan, 'amount' => (float)$r['val'], 'status' => 'paid', 'order' => $p2, 'reason' => ''];
                } elseif ($ev === 'purchase_failed') {
                    $fin[$fk] = ($fin[$fk] ?? 0) + 1;
                    $reason = pay_reason($p2);
                    $amt = (float)$r['val'];
                    if ($amt <= 0) $amt = $price[$plan] ?? 0.0;
                    $items[] = ['ts' => pay_local((string)$r['ts'], $tz), 'pkg' => $app, 'plan' => $plan, 'amount' => $amt, 'status' => 'failed', 'order' => $reason === '' ? $p2 : '', 'reason' => $reason];
                } else {
                    // start: baad me (upar, naya pehle) isi device + plan ka paid / failed aaya = poora hua, alag row nahi
                    if (($fin[$fk] ?? 0) > 0) { $fin[$fk]--; continue; }
                    $amt = (float)$r['val'];
                    if ($amt <= 0) $amt = $price[$plan] ?? 0.0;
                    $items[] = ['ts' => pay_local((string)$r['ts'], $tz), 'pkg' => $app, 'plan' => $plan, 'amount' => $amt, 'status' => 'started', 'order' => $p2, 'reason' => ''];
                }
            }
            usort($items, fn($a, $b) => strcmp($b['ts'], $a['ts']));
            $items = array_slice($items, 0, 500);
        }
    }
}

// ---------------------------------------------------------------- jawab
$list = [];
$tot = ['amount' => 0.0, 'paid' => 0, 'failed' => 0, 'started' => 0];
foreach ($per as $pkg => $a) {
    if ($app !== 'all' && $pkg !== $app) continue;
    if ($a['amount'] <= 0 && $a['paid'] === 0 && $a['failed'] === 0 && $a['started'] === 0) continue;
    $a['amount'] = round($a['amount'], 2);
    $list[] = ['pkg' => (string)$pkg, 'name' => (string)($apps[$pkg]['name'] ?? $pkg)] + $a;
    foreach ($tot as $k => $_) $tot[$k] += $a[$k];
}
usort($list, fn($a, $b) => [$b['amount'], $b['paid'], $b['failed'], $a['name']] <=> [$a['amount'], $a['paid'], $a['failed'], $b['name']]);
$tot['amount'] = round($tot['amount'], 2);

$out = ['ok' => true, 'app' => $app, 'from' => $d1, 'to' => $d2, 'today' => $d2 === $today, 'tz' => $tz->getName(), 'server_time' => gmdate('c'),
    'totals' => $tot, 'apps' => $list, 'raw_from' => $rawFrom, 'source' => $source, 'agg_from' => $aggFrom, 'agg_to' => $aggTo, 'agg_counts' => $aggCounts];
if ($app !== 'all') $out['items'] = $items;
json_out($out);
