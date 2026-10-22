<?php
/**
 * MFC Dashboard app ke liye extra data (login zaroori, wahi session jo data.php ka).
 * Yeh sab data.php nahi deta; DB mein pehle se hai, yahan sirf query hai.
 *
 * GET  ?app=all|<package>&range=today|yesterday|7d|30d  ya  &from=YYYY-MM-DD&to=YYYY-MM-DD
 *   active        : live_1h (60 min), live_6m (live_minutes), wau (7 din), mau (30 din)  — devices / day_user se
 *   retention     : cohort (install ke din) ke D1/D3/D7/D14 retention, paid (src=google_ads) vs organic vs all — day_install + day_user se
 *   retention_days: har install-din ki alag line (all)
 *   campaign_roas : har utm_campaign (Play referrer) ke installs, spend (Google Ads campaign ka naam = utm_campaign),
 *                   cohort revenue D0/D1/D3/D7 (imps + purchase_done, device-wise), ROAS Dn = revenue Dn / spend
 *                   NOTE: imps raw table config raw_days (35) din rehti hai, isliye cohort usi window mein sahi hai.
 *   campaign_daily: campaign-wise roz ka cost / impressions / clicks / conversions (range mein)
 *   notes         : campaign change log (table app_notes, khud ban jaati hai)
 * POST ?action=note   body: campaign, pkg, note      -> {"ok":true,"id":..}
 * POST ?action=note_del body: id                      -> {"ok":true}
 */
declare(strict_types=1);
require_once __DIR__ . '/../lib/common.php';
require_login_json();
if (session_status() === PHP_SESSION_ACTIVE) session_write_close();   // session lock chhodo: warna scan/AI ka lamba kaam chalte waqt app ki baaki requests atak kar timeout hoti hain

$pdo = db();
$apps = apps();
$tzOff = tz_offset_str();
$today = local_today();

// ---- notes table (change log)
$pdo->exec("CREATE TABLE IF NOT EXISTS app_notes (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  campaign VARCHAR(255) NOT NULL DEFAULT '',
  pkg VARCHAR(100) NOT NULL DEFAULT '',
  note VARCHAR(1000) NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id), KEY k_camp (campaign), KEY k_pkg (pkg)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

$action = (string)($_GET['action'] ?? '');
if (($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
    $in = json_decode((string)file_get_contents('php://input'), true);
    if (!is_array($in)) $in = $_POST;
    if ($action === 'note') {
        $note = trim((string)($in['note'] ?? ''));
        if ($note === '') json_out(['ok' => false, 'error' => 'note khali hai'], 400);
        $st = $pdo->prepare('INSERT INTO app_notes (campaign, pkg, note, created_at) VALUES (?,?,?,UTC_TIMESTAMP())');
        $st->execute([mb_substr(trim((string)($in['campaign'] ?? '')), 0, 255), mb_substr(trim((string)($in['pkg'] ?? '')), 0, 100), mb_substr($note, 0, 1000)]);
        json_out(['ok' => true, 'id' => (int)$pdo->lastInsertId()]);
    }
    if ($action === 'note_del') {
        $pdo->prepare('DELETE FROM app_notes WHERE id = ?')->execute([(int)($in['id'] ?? 0)]);
        json_out(['ok' => true]);
    }
    json_out(['ok' => false, 'error' => 'unknown action'], 400);
}

// ---- period (data.php jaisa)
$app = (string)($_GET['app'] ?? 'all');
if ($app !== 'all' && !isset($apps[$app])) $app = 'all';
$isDay = fn($v) => is_string($v) && preg_match('/^\d{4}-\d{2}-\d{2}$/', $v);
$back = fn(int $n) => (new DateTime($today))->modify("-$n day")->format('Y-m-d');
$d1 = (string)($_GET['from'] ?? ''); $d2 = (string)($_GET['to'] ?? '');
$preset = (string)($_GET['range'] ?? '');
if ($preset === 'today') { $d1 = $d2 = $today; }
elseif ($preset === 'yesterday') { $d1 = $d2 = $back(1); }
elseif ($preset === '7d') { $d1 = $back(6); $d2 = $today; }
elseif ($preset === '30d') { $d1 = $back(29); $d2 = $today; }
if (!$isDay($d1)) $d1 = $today;
if (!$isDay($d2)) $d2 = $d1;
if ($d2 < $d1) [$d1, $d2] = [$d2, $d1];
if ($d2 > $today) $d2 = $today;
$minD1 = (new DateTime($d2))->modify('-91 day')->format('Y-m-d');
if ($d1 < $minD1) $d1 = $minD1;

$rate = (float)cfg('inr_per_usd') ?: 88.0;
$rateSql = sprintf('%.6F', $rate);
$CX = "cost*IF(currency='USD',$rateSql,1)";
$liveMin = (int)cfg('live_minutes') ?: 6;
$pf = $app === 'all' ? '' : ' AND pkg = :pkg';
$pp = $app === 'all' ? [] : [':pkg' => $app];
$gd = [':d1' => $d1, ':d2' => $d2];
// Cohort window (retention + ROAS cohort): aaj / kal jaisi chhoti range me D1..D14 abhi bane hi nahi hote (sab khali dikhta tha),
// isliye range 14 din se chhoti ho to range ke aakhri din tak ke pichhle 14 din ke installs lo. App ko cohort_from / cohort_to milta hai.
$cd1 = $d1; $cd2 = $d2;
if ((new DateTime($d1))->diff(new DateTime($d2))->days < 13) $cd1 = (new DateTime($d2))->modify('-13 day')->format('Y-m-d');
$cg = [':d1' => $cd1, ':d2' => $cd2];

function q(PDO $pdo, string $sql, array $p = []): array { $st = $pdo->prepare($sql); $st->execute($p); return $st->fetchAll(); }
function q1(PDO $pdo, string $sql, array $p = []) { $st = $pdo->prepare($sql); $st->execute($p); $v = $st->fetchColumn(); return $v === false ? null : $v; }

$out = ['ok' => true, 'from' => $d1, 'to' => $d2, 'today' => $d2 === $today, 'app' => $app, 'inr_per_usd' => $rate, 'live_minutes' => $liveMin, 'server_time' => gmdate('c')];

// ---------------------------------------------------------------- active: 6 min / 1 ghanta / WAU / MAU
$out['active'] = [
    'live_6m' => (int)q1($pdo, "SELECT COUNT(*) FROM devices WHERE last_seen >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL $liveMin MINUTE)$pf", $pp),
    'live_30m' => (int)q1($pdo, "SELECT COUNT(*) FROM devices WHERE last_seen >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 MINUTE)$pf", $pp),
    'live_1h' => (int)q1($pdo, "SELECT COUNT(*) FROM devices WHERE last_seen >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 60 MINUTE)$pf", $pp),
    'live_24h' => (int)q1($pdo, "SELECT COUNT(*) FROM devices WHERE last_seen >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 24 HOUR)$pf", $pp),
    'dau' => (int)q1($pdo, "SELECT COUNT(DISTINCT did) FROM day_user WHERE day = :d$pf", [':d' => $today] + $pp),
    'wau' => (int)q1($pdo, "SELECT COUNT(DISTINCT did) FROM day_user WHERE day > DATE_SUB(:d, INTERVAL 7 DAY) AND day <= :d$pf", [':d' => $today] + $pp),
    'mau' => (int)q1($pdo, "SELECT COUNT(DISTINCT did) FROM day_user WHERE day > DATE_SUB(:d, INTERVAL 30 DAY) AND day <= :d$pf", [':d' => $today] + $pp),
    'installed_total' => (int)q1($pdo, "SELECT COUNT(*) FROM devices WHERE uninstalled_at IS NULL$pf", $pp),
    'uninstalled_total' => (int)q1($pdo, "SELECT COUNT(*) FROM devices WHERE uninstalled_at IS NOT NULL$pf", $pp),
];
$out['active']['dau_mau'] = $out['active']['mau'] > 0 ? $out['active']['dau'] / $out['active']['mau'] : null;

// ---------------------------------------------------------------- retention (cohort = install ka din; day_install + day_user)
// Dn = cohort ke kitne devices install ke n-ve din wapas aaye. Jo din abhi aaya nahi (day+n > today) -> null.
$retSql = "SELECT i.day, IF(d.src='google_ads','paid','organic') grp, COUNT(DISTINCT i.did) n,
    COUNT(DISTINCT u1.did) r1, COUNT(DISTINCT u3.did) r3, COUNT(DISTINCT u7.did) r7, COUNT(DISTINCT u14.did) r14
  FROM day_install i
  LEFT JOIN devices d ON d.pkg=i.pkg AND d.did=i.did
  LEFT JOIN day_user u1  ON u1.pkg=i.pkg  AND u1.did=i.did  AND u1.day  = DATE_ADD(i.day, INTERVAL 1 DAY)
  LEFT JOIN day_user u3  ON u3.pkg=i.pkg  AND u3.did=i.did  AND u3.day  = DATE_ADD(i.day, INTERVAL 3 DAY)
  LEFT JOIN day_user u7  ON u7.pkg=i.pkg  AND u7.did=i.did  AND u7.day  = DATE_ADD(i.day, INTERVAL 7 DAY)
  LEFT JOIN day_user u14 ON u14.pkg=i.pkg AND u14.did=i.did AND u14.day = DATE_ADD(i.day, INTERVAL 14 DAY)
  WHERE i.day >= :d1 AND i.day <= :d2" . ($app === 'all' ? '' : ' AND i.pkg = :pkg') . "
  GROUP BY i.day, grp ORDER BY i.day";
$ret = q($pdo, $retSql, $cg + $pp);
$agg = ['all' => [], 'paid' => [], 'organic' => []];
$days = [];
$avail = fn(string $day, int $n) => (new DateTime($day))->modify("+$n day")->format('Y-m-d') <= $today;   // n-vaan din aa chuka?
foreach ($ret as $r) {
    foreach (['all', $r['grp']] as $g) {
        $a = &$agg[$g];
        foreach ([1, 3, 7, 14] as $n) {
            if (!$avail($r['day'], $n)) continue;
            $a["n$n"] = ($a["n$n"] ?? 0) + (int)$r['n'];
            $a["r$n"] = ($a["r$n"] ?? 0) + (int)$r["r$n"];
        }
        $a['installs'] = ($a['installs'] ?? 0) + (int)$r['n'];
        unset($a);
    }
    $dd = &$days[$r['day']];
    $dd = $dd ?? ['day' => $r['day'], 'installs' => 0, 'paid' => 0, 'r1' => 0, 'r3' => 0, 'r7' => 0, 'r14' => 0, 'n1' => 0, 'n3' => 0, 'n7' => 0, 'n14' => 0];
    $dd['installs'] += (int)$r['n'];
    if ($r['grp'] === 'paid') $dd['paid'] += (int)$r['n'];
    foreach ([1, 3, 7, 14] as $n) { if ($avail($r['day'], $n)) { $dd["r$n"] += (int)$r["r$n"]; $dd["n$n"] += (int)$r['n']; } }
    unset($dd);
}
$pctOf = fn(array $a, int $n) => (($a["n$n"] ?? 0) > 0) ? $a["r$n"] / $a["n$n"] : null;
$out['cohort_from'] = $cd1; $out['cohort_to'] = $cd2;
$out['retention'] = [];
foreach ($agg as $g => $a) {
    $out['retention'][] = ['cohort' => $g, 'installs' => (int)($a['installs'] ?? 0), 'd1' => $pctOf($a, 1), 'd3' => $pctOf($a, 3), 'd7' => $pctOf($a, 7), 'd14' => $pctOf($a, 14)];
}
$out['retention_days'] = array_values(array_map(fn($d) => ['day' => $d['day'], 'installs' => $d['installs'], 'paid' => $d['paid'],
    'd1' => $pctOf($d, 1), 'd3' => $pctOf($d, 3), 'd7' => $pctOf($d, 7), 'd14' => $pctOf($d, 14)], $days));

// ---------------------------------------------------------------- campaign ROAS cohort (utm_campaign <-> Google Ads campaign naam)
// 1) range mein install hue devices, campaign-wise (devices.utm_campaign; first_seen local din range mein)
[$uf] = day_bounds_utc($cd1);
[, $ut] = day_bounds_utc($cd2);
$camp = [];
foreach (q($pdo, "SELECT pkg, utm_campaign c, COUNT(*) n FROM devices WHERE utm_campaign <> '' AND first_seen >= :f AND first_seen < :t$pf GROUP BY pkg, utm_campaign", [':f' => $uf, ':t' => $ut] + $pp) as $r) {
    $camp[$r['pkg'] . '|' . $r['c']] = ['campaign' => $r['c'], 'pkg' => $r['pkg'], 'installs' => (int)$r['n'], 'spend' => 0.0, 'gads_conv' => 0.0, 'gads_clicks' => 0,
        'rev_d0' => 0.0, 'rev_d1' => 0.0, 'rev_d3' => 0.0, 'rev_d7' => 0.0, 'rev_total' => 0.0, 'pay_total' => 0.0, 'active_d1' => 0, 'active_d7' => 0];
}
// 2) spend: Google Ads campaign jiska naam utm_campaign se match (case-insensitive)
foreach (q($pdo, "SELECT pkg, LOWER(TRIM(campaign)) c, campaign cname, SUM($CX) cost, SUM(conversions) conv, SUM(clicks) clicks FROM gads_campaign_day WHERE day>=:d1 AND day<=:d2$pf GROUP BY pkg, LOWER(TRIM(campaign))", $cg + $pp) as $r) {
    $hit = null;
    foreach ($camp as $k => $c) if ($c['pkg'] === $r['pkg'] && strtolower(trim($c['campaign'])) === $r['c']) { $hit = $k; break; }
    if ($hit === null) { $hit = $r['pkg'] . '|' . $r['cname']; $camp[$hit] = $camp[$hit] ?? ['campaign' => $r['cname'], 'pkg' => $r['pkg'], 'installs' => 0, 'spend' => 0.0, 'gads_conv' => 0.0, 'gads_clicks' => 0,
        'rev_d0' => 0.0, 'rev_d1' => 0.0, 'rev_d3' => 0.0, 'rev_d7' => 0.0, 'rev_total' => 0.0, 'pay_total' => 0.0, 'active_d1' => 0, 'active_d7' => 0, 'no_utm' => true]; }
    $camp[$hit]['spend'] += (float)$r['cost']; $camp[$hit]['gads_conv'] += (float)$r['conv']; $camp[$hit]['gads_clicks'] += (int)$r['clicks'];
}
// 3) in devices ka ad revenue, install se kitne din baad (local din ka farq) — imps raw (raw_days window)
$revSql = "SELECT d.pkg, d.utm_campaign c,
    DATEDIFF(DATE(CONVERT_TZ(i.ts,'+00:00','$tzOff')), DATE(CONVERT_TZ(d.first_seen,'+00:00','$tzOff'))) dn, SUM(i.rev_usd) rev
  FROM devices d JOIN imps i ON i.pkg=d.pkg AND i.did=d.did
  WHERE d.utm_campaign <> '' AND d.first_seen >= :f AND d.first_seen < :t" . ($app === 'all' ? '' : ' AND d.pkg = :pkg') . "
  GROUP BY d.pkg, d.utm_campaign, dn";
foreach (q($pdo, $revSql, [':f' => $uf, ':t' => $ut] + $pp) as $r) {
    $k = $r['pkg'] . '|' . $r['c']; if (!isset($camp[$k])) continue;
    $inr = (float)$r['rev'] * $rate; $dn = (int)$r['dn'];
    $camp[$k]['rev_total'] += $inr;
    if ($dn <= 0) $camp[$k]['rev_d0'] += $inr;
    if ($dn <= 1) $camp[$k]['rev_d1'] += $inr;
    if ($dn <= 3) $camp[$k]['rev_d3'] += $inr;
    if ($dn <= 7) $camp[$k]['rev_d7'] += $inr;
}
// 4) payments (purchase_done, order p2 par ek baar)
$paySql = "SELECT d.pkg, d.utm_campaign c, SUM(v) amt FROM (SELECT e.pkg, e.did, e.p2, MAX(e.val) v FROM events e WHERE e.ev='purchase_done' AND e.ts >= :f GROUP BY e.pkg, e.did, e.p2) x
  JOIN devices d ON d.pkg=x.pkg AND d.did=x.did WHERE d.utm_campaign <> '' AND d.first_seen >= :f AND d.first_seen < :t" . ($app === 'all' ? '' : ' AND d.pkg = :pkg') . " GROUP BY d.pkg, d.utm_campaign";
foreach (q($pdo, $paySql, [':f' => $uf, ':t' => $ut] + $pp) as $r) { $k = $r['pkg'] . '|' . $r['c']; if (isset($camp[$k])) $camp[$k]['pay_total'] += (float)$r['amt']; }
// 5) D1 / D7 wapas aaye (day_user)
$actSql = "SELECT d.pkg, d.utm_campaign c, COUNT(DISTINCT u1.did) a1, COUNT(DISTINCT u7.did) a7 FROM devices d
  LEFT JOIN day_user u1 ON u1.pkg=d.pkg AND u1.did=d.did AND u1.day = DATE_ADD(DATE(CONVERT_TZ(d.first_seen,'+00:00','$tzOff')), INTERVAL 1 DAY)
  LEFT JOIN day_user u7 ON u7.pkg=d.pkg AND u7.did=d.did AND u7.day = DATE_ADD(DATE(CONVERT_TZ(d.first_seen,'+00:00','$tzOff')), INTERVAL 7 DAY)
  WHERE d.utm_campaign <> '' AND d.first_seen >= :f AND d.first_seen < :t" . ($app === 'all' ? '' : ' AND d.pkg = :pkg') . " GROUP BY d.pkg, d.utm_campaign";
foreach (q($pdo, $actSql, [':f' => $uf, ':t' => $ut] + $pp) as $r) { $k = $r['pkg'] . '|' . $r['c']; if (isset($camp[$k])) { $camp[$k]['active_d1'] = (int)$r['a1']; $camp[$k]['active_d7'] = (int)$r['a7']; } }
foreach ($camp as &$c) {
    $sp = $c['spend'];
    $c['cpi'] = $c['installs'] > 0 && $sp > 0 ? $sp / $c['installs'] : null;
    $c['cost_per_conv'] = $c['gads_conv'] > 0 ? $sp / $c['gads_conv'] : null;
    $tot = $c['rev_total'] + $c['pay_total'];
    foreach (['d0', 'd1', 'd3', 'd7'] as $n) $c["roas_$n"] = $sp > 0 ? $c["rev_$n"] / $sp : null;
    $c['roas_total'] = $sp > 0 ? $tot / $sp : null;
    $c['profit'] = $tot - $sp;
    $c['rev_per_install'] = $c['installs'] > 0 ? $tot / $c['installs'] : null;
    $c['ret_d1'] = $c['installs'] > 0 ? $c['active_d1'] / $c['installs'] : null;
    $c['ret_d7'] = $c['installs'] > 0 ? $c['active_d7'] / $c['installs'] : null;
}
unset($c);
usort($camp, fn($a, $b) => [$b['spend'], $b['installs']] <=> [$a['spend'], $a['installs']]);
$out['campaign_roas'] = array_values($camp);
// Organic (bina utm) tulna ke liye
$out['organic'] = ['installs' => (int)q1($pdo, "SELECT COUNT(*) FROM devices WHERE utm_campaign = '' AND first_seen >= :f AND first_seen < :t$pf", [':f' => $uf, ':t' => $ut] + $pp)];

// ---------------------------------------------------------------- campaign-wise roz ka (range mein, max 60 din)
$out['campaign_daily'] = q($pdo, "SELECT day, MAX(campaign) campaign, pkg, SUM($CX) cost, SUM(impressions) impressions, SUM(clicks) clicks, SUM(conversions) conversions
    FROM gads_campaign_day WHERE day>=:d1 AND day<=:d2$pf GROUP BY day, customer_id, campaign_id ORDER BY day", $gd + $pp);

// ---------------------------------------------------------------- notes / change log
$out['notes'] = q($pdo, "SELECT id, campaign, pkg, note, created_at FROM app_notes" . ($app === 'all' ? '' : ' WHERE pkg = :pkg OR pkg = \'\'') . " ORDER BY id DESC LIMIT 200", $pp);

json_out($out);
