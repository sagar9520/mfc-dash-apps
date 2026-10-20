<?php
/**
 * MFC Dashboard — Smart Alerts (rule engine + Claude AI)
 * Poore dashboard par nazar: data aana band, crash, Google Ads (campaign / ROAS / CPI / spend), revenue, installs,
 * retention, payments, ad network / ad unit (fill, no-fill, CTR, eCPM). 3 level: 1 = Info, 2 = Dhyan do / Watch, 3 = URGENT.
 * Har alert English + Hinglish (title/body + title_hi/body_hi) me aata hai; app apni bhasha chunti hai (GET ?lang=en|hi),
 * aur Claude AI ki explanation bhi usi bhasha me (kya hua, nuksan/fayda, wajah, fix).
 *
 * Upload: mfc_dash/api/alerts.php  (lib/common.php + lib/agg.php wahi purane)
 *
 * App (login cookie ke saath):
 *   GET  alerts.php?since=<id>&limit=100[&open=1][&lang=en|hi]   -> {"ok":true,"alerts":[...],"open_count":..,"urgent_open":..,"ai":{...},"brief":{...}}
 *        (2 min se purana compute ho to pehle rules dobara chalte hain — app ka live monitor har minute poochta hai, isliye alag cron zaroori nahi)
 *   POST alerts.php?action=read     body: ids=[..] ya all=1
 *   POST alerts.php?action=resolve  body: id
 *   POST alerts.php?action=test     -> test notification (level 3)
 *   POST alerts.php?action=compute  -> abhi rules chalao
 *   POST alerts.php?action=ai_key   body: key (khali = AI band), model (optional)
 * Cron (optional, phone band ho tab bhi rules chalte rahen): alerts.php?cron=1&key=CRON_KEY  (config.php ka cron_key) — har 1-2 min
 *
 * Claude AI: key App > Settings > "Claude AI" me daalo (ya config.php me 'anthropic_api_key').
 *   - Har naye Urgent / Dhyan-do alert par 3 line Hinglish explanation (wajah + abhi kya karein)
 *   - Roz subah 9 baje "AI brief": kal ka poora hisaab + 5 sujhav
 *   Rules AI ke bina bhi poore chalte hain; AI sirf samjhaata hai.
 */
declare(strict_types=1);
require_once __DIR__ . '/../lib/common.php';
@require_once __DIR__ . '/../lib/agg.php';

// ---------------------------------------------------------------- thresholds (yahan badal sakte ho)
const AL_RECOMPUTE_SEC   = 120;   // app ke request par itna purana ho to dobara compute (live monitor har minute poochta hai)
const AL_ADUNIT_MIN_IMPS = 200;   // ad unit / network rule: baseline me kam se kam itne impressions/ghanta-window ho tabhi dekho
const AL_MIN_EV_HOUR     = 30;    // app "zinda" tab maani jaati hai jab normally itne events/ghanta aate hon
const AL_MIN_IMP_HOUR    = 30;    // ads ke liye wahi (impressions/ghanta)
const AL_GADS_STALE_MIN  = 150;   // Google Ads script itne minute se chup -> urgent
const AL_MIN_SPEND_NOINST = 500;  // ₹: itna kharch aur 0 install -> urgent
const AL_ROAS_SPEND_MIN  = 1500;  // ₹: 3 din me itna kharch ho tabhi ROAS alert
const AL_BRIEF_HOUR      = 9;     // subah 9 baje roz ka brief (local time)
const AL_AI_MAX_PER_RUN  = 6;     // ek run me max itne alerts AI se samjhao
const AL_AI_DEFAULT_MODEL = 'claude-opus-5-5';

// rules jo apne aap "theek ho gaya" ho jaate hain jab condition hat jaaye
const AL_STATEFUL = ['collector_down', 'data_stop', 'data_low', 'imps_stop', 'imps_low', 'agg_lag', 'gads_stale', 'camp_zero', 'camp_imps_low',
    'camp_noinst', 'cpi_high', 'roas_low', 'camp_roas', 'spend_spike', 'crash_spike', 'ver_crash', 'fill_drop', 'rev_drop', 'ecpm_drop',
    'inst_drop', 'uninst_high', 'pay_fail', 'ret_drop', 'adunit_nofill', 'adunit_drop', 'adunit_ctr', 'adunit_ecpm', 'net_drop'];

/** Bilingual text: [English, Hinglish]. Har al_add ka title/body isse banta hai. */
function L(string $en, string $hi): array { return [$en, $hi]; }

$pdo = db();
al_schema($pdo);

$isCron = isset($_GET['cron']);
if ($isCron) {
    require_cron();
    @set_time_limit(300);
    $log = function (string $m) { echo gmdate('H:i:s') . "  $m\n"; };
    $r = al_compute($pdo, $log, true);
    if (!empty($r['ai_pending'])) { $n = al_ai_process($pdo, $r['ai_pending']); $log("ai: $n alerts samjhaye"); }
    $log('done: ' . json_encode($r));
    exit;
}

require_login_json();
$action = (string)($_GET['action'] ?? '');
if (($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
    $in = json_decode((string)file_get_contents('php://input'), true);
    if (!is_array($in)) $in = $_POST;
    switch ($action) {
        case 'read':
            if (!empty($in['all'])) $pdo->exec('UPDATE app_alerts SET read_at = UTC_TIMESTAMP() WHERE read_at IS NULL');
            else {
                $ids = $in['ids'] ?? [];
                if (is_string($ids)) $ids = array_filter(array_map('intval', explode(',', $ids)));
                foreach ((array)$ids as $id) $pdo->prepare('UPDATE app_alerts SET read_at = UTC_TIMESTAMP() WHERE id = ? AND read_at IS NULL')->execute([(int)$id]);
            }
            json_out(['ok' => true]);
        case 'resolve':
            $pdo->prepare('UPDATE app_alerts SET resolved_at = UTC_TIMESTAMP(), read_at = IFNULL(read_at, UTC_TIMESTAMP()) WHERE id = ?')->execute([(int)($in['id'] ?? 0)]);
            json_out(['ok' => true]);
        case 'test':
            $id = al_insert($pdo, ['akey' => 'test|' . time(), 'level' => 3, 'rule' => 'test',
                'title' => L('Test alert: notifications are working ✅', 'Test alert: notification chal rahi hai ✅'),
                'body' => L('This is only a test. Real alerts arrive the same way: Urgent = sound + vibration.', 'Yeh sirf test hai. Asli alerts isi tarah aayenge: Urgent = awaaz + vibration.'), 'metrics' => ['test' => true]]);
            json_out(['ok' => true, 'id' => $id]);
        case 'compute':
            $r = al_compute($pdo, null, true);
            if (!empty($r['ai_pending'])) $r['ai'] = al_ai_process($pdo, $r['ai_pending']);
            json_out(['ok' => true, 'result' => $r]);
        case 'ai_key':
            $key = trim((string)($in['key'] ?? ''));
            $model = trim((string)($in['model'] ?? ''));
            if ($key === '') { $pdo->exec("DELETE FROM kv WHERE k IN ('ai_api_key','ai_last_error')"); }
            else {
                if (!preg_match('/^sk-ant-[A-Za-z0-9_\-]{20,}$/', $key)) json_out(['ok' => false, 'error' => 'Key sk-ant- se shuru honi chahiye'], 400);
                kv_set('ai_api_key', $key);
                $pdo->exec("DELETE FROM kv WHERE k = 'ai_last_error'");
            }
            if ($model !== '' && preg_match('/^claude-[a-z0-9\-]+$/', $model)) kv_set('ai_model', $model);
            // turant check: chhota sa call
            $test = null;
            if ($key !== '') $test = al_ai_call('Reply with exactly: OK', 'ping', 16, 'low');
            json_out(['ok' => true, 'ai' => al_ai_status(), 'test_ok' => $test !== null]);
        default:
            json_out(['ok' => false, 'error' => 'unknown action'], 400);
    }
}

// ---------------------------------------------------------------- GET: list
$lang = (string)($_GET['lang'] ?? '');
if (in_array($lang, ['en', 'hi'], true) && (kv_get('ai_lang') ?? 'en') !== $lang) kv_set('ai_lang', $lang);   // AI explanations isi bhasha me
$last = (int)(kv_get('alerts_last_compute') ?? 0);
$aiPending = [];
if (time() - $last > AL_RECOMPUTE_SEC) {
    try { $aiPending = al_compute($pdo, null, false)['ai_pending'] ?? []; } catch (Throwable $e) { error_log('mfc alerts: ' . $e->getMessage()); }
}
$since = (int)($_GET['since'] ?? 0);
$limit = max(1, min(300, (int)($_GET['limit'] ?? 100)));
$onlyOpen = !empty($_GET['open']);
$w = ['id > ?']; $p = [$since];
if ($onlyOpen) $w[] = 'resolved_at IS NULL';
$st = $pdo->prepare('SELECT id, akey, level, rule, pkg, account, campaign, title, body, title_hi, body_hi, ai, metrics, fired, created_at, updated_at, resolved_at, read_at
    FROM app_alerts WHERE ' . implode(' AND ', $w) . ' ORDER BY id DESC LIMIT ' . $limit);
$st->execute($p);
$rows = $st->fetchAll();
$appsAll = apps();
foreach ($rows as &$r) {
    $r['id'] = (int)$r['id']; $r['level'] = (int)$r['level']; $r['fired'] = (int)$r['fired'];
    $r['app_name'] = $r['pkg'] !== '' ? (string)($appsAll[$r['pkg']]['name'] ?? $r['pkg']) : '';
    $r['metrics'] = $r['metrics'] !== null ? (json_decode((string)$r['metrics'], true) ?: new stdClass()) : new stdClass();
}
unset($r);
$cnt = $pdo->query('SELECT COUNT(*) n, SUM(level = 3) u, SUM(level = 2) w, SUM(read_at IS NULL) unread FROM app_alerts WHERE resolved_at IS NULL')->fetch();
$out = json_encode([
    'ok' => true, 'alerts' => $rows,
    'open_count' => (int)($cnt['n'] ?? 0), 'urgent_open' => (int)($cnt['u'] ?? 0), 'warn_open' => (int)($cnt['w'] ?? 0), 'unread' => (int)($cnt['unread'] ?? 0),
    'computed_at' => (int)(kv_get('alerts_last_compute') ?? 0), 'server_time' => gmdate('c'),
    'ai' => al_ai_status(), 'ai_pending' => count($aiPending),
    'brief' => al_brief_latest(),
], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
// Jawab pehle bhejo (app ko turant notification), Claude AI explanation uske BAAD background me (agle refresh par dikhegi)
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('Content-Length: ' . strlen($out));
header('Connection: close');
echo $out;
if ($aiPending) {
    ignore_user_abort(true);
    @set_time_limit(300);
    if (function_exists('fastcgi_finish_request')) fastcgi_finish_request();
    else { while (ob_get_level() > 0) ob_end_flush(); flush(); }
    try { al_ai_process($pdo, $aiPending); } catch (Throwable $e) { error_log('mfc alerts ai: ' . $e->getMessage()); }
}
exit;

// ================================================================ schema
function al_schema(PDO $pdo): void
{
    static $done = false;
    if ($done) return;
    $done = true;
    $pdo->exec("CREATE TABLE IF NOT EXISTS app_alerts (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  akey VARCHAR(190) NOT NULL,
  level TINYINT NOT NULL,
  rule VARCHAR(40) NOT NULL,
  pkg VARCHAR(100) NOT NULL DEFAULT '',
  account VARCHAR(20) NOT NULL DEFAULT '',
  campaign VARCHAR(255) NOT NULL DEFAULT '',
  title VARCHAR(255) NOT NULL,
  body VARCHAR(2000) NOT NULL DEFAULT '',
  ai VARCHAR(3000) NOT NULL DEFAULT '',
  metrics TEXT NULL,
  fired INT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  resolved_at DATETIME NULL,
  read_at DATETIME NULL,
  PRIMARY KEY (id), KEY k_akey (akey, resolved_at), KEY k_created (created_at), KEY k_open (resolved_at, level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    // v1.2: Hinglish copy of title/body (English is the default text)
    $has = $pdo->query("SHOW COLUMNS FROM app_alerts LIKE 'title_hi'")->fetch();
    if (!$has) $pdo->exec("ALTER TABLE app_alerts ADD COLUMN title_hi VARCHAR(255) NOT NULL DEFAULT '' AFTER body, ADD COLUMN body_hi VARCHAR(2000) NOT NULL DEFAULT '' AFTER title_hi");
}

/** title/body string ya [en, hi] array -> [en, hi] */
function al_txt($t): array
{
    if (is_array($t)) return [(string)($t[0] ?? ''), (string)($t[1] ?? ($t[0] ?? ''))];
    return [(string)$t, (string)$t];
}

function al_insert(PDO $pdo, array $a): int
{
    [$te, $th] = al_txt($a['title']); [$be, $bh] = al_txt($a['body'] ?? '');
    $st = $pdo->prepare('INSERT INTO app_alerts (akey, level, rule, pkg, account, campaign, title, body, title_hi, body_hi, ai, metrics, fired, created_at, updated_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,1,UTC_TIMESTAMP(),UTC_TIMESTAMP())');
    $st->execute([mb_substr($a['akey'], 0, 190), (int)$a['level'], mb_substr($a['rule'], 0, 40), mb_substr((string)($a['pkg'] ?? ''), 0, 100),
        mb_substr((string)($a['account'] ?? ''), 0, 20), mb_substr((string)($a['campaign'] ?? ''), 0, 255), mb_substr($te, 0, 255),
        mb_substr($be, 0, 2000), mb_substr($th, 0, 255), mb_substr($bh, 0, 2000), mb_substr((string)($a['ai'] ?? ''), 0, 3000),
        json_encode($a['metrics'] ?? new stdClass(), JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES)]);
    return (int)$pdo->lastInsertId();
}

// ================================================================ compute (rules -> table)
function al_compute(PDO $pdo, ?callable $log, bool $force): array
{
    $log = $log ?? function (string $m) {};
    $got = (int)$pdo->query("SELECT GET_LOCK('mfc_alerts', 0)")->fetchColumn();
    if ($got !== 1) { $log('doosra compute chal raha hai'); return ['skipped' => true]; }
    $out = ['new' => 0, 'updated' => 0, 'resolved' => 0, 'ai_pending' => [], 'errors' => []];
    try {
        $last = (int)(kv_get('alerts_last_compute') ?? 0);
        if (!$force && time() - $last <= AL_RECOMPUTE_SEC) return ['skipped' => true];
        kv_set('alerts_last_compute', (string)time());   // pehle hi: error aaye to bhi baar-baar na chale

        $ctx = al_context($pdo);
        $found = [];
        $rules = ['al_rule_activity', 'al_rule_agg', 'al_rule_gads_feed', 'al_rule_campaigns', 'al_rule_roas', 'al_rule_crash', 'al_rule_ver_crash',
            'al_rule_fill', 'al_rule_adunit', 'al_rule_revenue', 'al_rule_installs', 'al_rule_uninstall', 'al_rule_payments', 'al_rule_retention', 'al_rule_brief', 'al_rule_records'];
        foreach ($rules as $fn) {
            try { $fn($pdo, $ctx, $found); }
            catch (Throwable $e) { $out['errors'][] = "$fn: " . $e->getMessage(); $log("$fn ERROR: " . $e->getMessage()); }
        }

        // ---- open rows
        $open = [];
        foreach ($pdo->query('SELECT id, akey, level, rule FROM app_alerts WHERE resolved_at IS NULL') as $r) $open[$r['akey']] = $r;

        $new = [];
        foreach ($found as $k => $a) {
            $a['akey'] = $k;
            if (isset($open[$k])) {
                $o = $open[$k];
                if ((int)$a['level'] > (int)$o['level']) {
                    // badh gaya (dhyan do -> urgent): purana band, naya notify
                    $pdo->prepare('UPDATE app_alerts SET resolved_at = UTC_TIMESTAMP() WHERE id = ?')->execute([(int)$o['id']]);
                    $a['id'] = al_insert($pdo, $a); $new[] = $a; $out['new']++;
                } else {
                    [$te, $th] = al_txt($a['title']); [$be, $bh] = al_txt($a['body'] ?? '');
                    $pdo->prepare('UPDATE app_alerts SET title = ?, body = ?, title_hi = ?, body_hi = ?, metrics = ?, fired = fired + 1, updated_at = UTC_TIMESTAMP() WHERE id = ?')
                        ->execute([mb_substr($te, 0, 255), mb_substr($be, 0, 2000), mb_substr($th, 0, 255), mb_substr($bh, 0, 2000),
                            json_encode($a['metrics'] ?? new stdClass(), JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), (int)$o['id']]);
                    $out['updated']++;
                }
                unset($open[$k]);
            } else {
                $a['id'] = al_insert($pdo, $a); $new[] = $a; $out['new']++;
            }
        }
        // ---- jo ab nahi mile: stateful -> theek ho gaya
        foreach ($open as $k => $o) {
            if (!in_array($o['rule'], AL_STATEFUL, true)) continue;
            $pdo->prepare('UPDATE app_alerts SET resolved_at = UTC_TIMESTAMP(), read_at = IFNULL(read_at, UTC_TIMESTAMP()) WHERE id = ?')->execute([(int)$o['id']]);
            $out['resolved']++;
            if ((int)$o['level'] === 3) {
                $row = $pdo->query('SELECT title, title_hi, pkg, account, campaign FROM app_alerts WHERE id = ' . (int)$o['id'])->fetch();
                $th = (string)($row['title_hi'] ?? ''); if ($th === '') $th = (string)($row['title'] ?? '');
                $a = ['akey' => 'ok|' . $k . '|' . time(), 'level' => 1, 'rule' => 'ok', 'pkg' => $row['pkg'] ?? '', 'account' => $row['account'] ?? '', 'campaign' => $row['campaign'] ?? '',
                    'title' => L('Recovered: ' . mb_substr((string)($row['title'] ?? ''), 0, 200), 'Theek ho gaya: ' . mb_substr($th, 0, 200)),
                    'body' => L('This urgent issue is no longer detected.', 'Yeh urgent issue ab nahi dikh raha.'), 'metrics' => ['was' => $k]];
                $a['id'] = al_insert($pdo, $a); $new[] = $a; $out['new']++;
            }
        }
        // event-type (info) alerts 24 ghante baad apne aap band
        $pdo->exec('UPDATE app_alerts SET resolved_at = UTC_TIMESTAMP(), read_at = IFNULL(read_at, UTC_TIMESTAMP())
            WHERE resolved_at IS NULL AND rule NOT IN (' . implode(',', array_map(fn($s) => "'$s'", AL_STATEFUL)) . ") AND created_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 24 HOUR)");
        // purani history 90 din
        $pdo->exec('DELETE FROM app_alerts WHERE created_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 90 DAY)');

        // ---- Claude AI: naye urgent / dhyan-do alerts (+ brief) baad me samjhao (al_ai_process)
        $out['ai_pending'] = [];
        if (al_ai_key() !== '' && $new) {
            usort($new, fn($a, $b) => $b['level'] <=> $a['level']);
            foreach ($new as $a) {
                if ($a['rule'] === 'brief') { $out['ai_pending'][] = ['id' => (int)$a['id'], 'kind' => 'brief']; continue; }
                if ((int)$a['level'] < 2 || $a['rule'] === 'test') continue;
                if (count($out['ai_pending']) >= AL_AI_MAX_PER_RUN) continue;
                $out['ai_pending'][] = ['id' => (int)$a['id'], 'kind' => 'explain'];
            }
        }
        $log("new {$out['new']}, updated {$out['updated']}, resolved {$out['resolved']}, ai pending " . count($out['ai_pending']));
    } finally {
        $pdo->query("SELECT RELEASE_LOCK('mfc_alerts')");
    }
    return $out;
}

/** Sab rules ke liye common values */
function al_context(PDO $pdo): array
{
    $now = new DateTime('now', new DateTimeZone('UTC'));
    $loc = (clone $now)->setTimezone(tz());
    $today = $loc->format('Y-m-d');
    $h = (int)$loc->format('G');
    $m = (int)$loc->format('i');
    $rate = (float)cfg('inr_per_usd') ?: 88.0;
    $apps = apps();
    $names = [];
    foreach ($apps as $pkg => $a) $names[$pkg] = (string)($a['name'] ?? $pkg);
    $back = fn(int $n) => (new DateTime($today))->modify("-$n day")->format('Y-m-d');
    [$tf, $tt] = day_bounds_utc($today);
    return [
        'now' => $now, 'today' => $today, 'yest' => $back(1), 'h' => $h, 'min' => $m, 'frac' => max(0.02, ($h * 60 + $m) / 1440),
        'rate' => $rate, 'rateSql' => sprintf('%.6F', $rate), 'apps' => $apps, 'names' => $names, 'back' => $back,
        'today_utc_from' => $tf, 'today_utc_to' => $tt, 'tzoff' => tz_offset_str(),
        'cur_hour_lh' => $today . sprintf(' %02d:00:00', $h),
    ];
}

function al_name(array $ctx, string $pkg): string { return $ctx['names'][$pkg] ?? $pkg; }
function al_inr(float $v): string { return '₹' . number_format($v, $v >= 100 ? 0 : 1); }
function al_pct(?float $v, int $d = 0): string { return $v === null ? '-' : number_format($v * 100, $d) . '%'; }
function al_q(PDO $pdo, string $sql, array $p = []): array { $st = $pdo->prepare($sql); $st->execute($p); return $st->fetchAll(); }
function al_q1(PDO $pdo, string $sql, array $p = []) { $st = $pdo->prepare($sql); $st->execute($p); $v = $st->fetchColumn(); return $v === false ? null : $v; }
function al_add(array &$found, string $key, int $level, string $rule, $title, $body, array $m = [], string $pkg = '', string $account = '', string $campaign = ''): void
{
    $found[$key] = ['level' => $level, 'rule' => $rule, 'title' => $title, 'body' => $body, 'metrics' => $m, 'pkg' => $pkg, 'account' => $account, 'campaign' => $campaign];
}
/** Pichhle 7 din ke "isi ghante" (aur pichhle ghante) ke local-hour keys — baseline ke liye */
function al_hour_keys(array $ctx): array
{
    $keys = [];
    for ($d = 1; $d <= 7; $d++) {
        $day = $ctx['back']($d);
        foreach ([$ctx['h'], ($ctx['h'] + 23) % 24] as $hh) $keys[] = "'$day " . sprintf('%02d', $hh) . ":00:00'";
    }
    return $keys;
}

// ================================================================ rules
/** 1) App se data aana band / kam (events), ads band (imps) */
function al_rule_activity(PDO $pdo, array $ctx, array &$found): void
{
    $keys = implode(',', al_hour_keys($ctx));
    $base = [];
    foreach ($pdo->query("SELECT pkg, SUM(c)/14 b FROM agg_ev_h WHERE lh IN ($keys) GROUP BY pkg") as $r) $base[$r['pkg']] = (float)$r['b'];
    $cur = [];
    foreach ($pdo->query('SELECT pkg, COUNT(*) n FROM events WHERE ts >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 60 MINUTE) GROUP BY pkg') as $r) $cur[$r['pkg']] = (int)$r['n'];
    $stopped = []; $alive = 0;
    foreach ($base as $pkg => $b) {
        if (!isset($ctx['apps'][$pkg]) || $b < AL_MIN_EV_HOUR) continue;
        $alive++;
        $n = $cur[$pkg] ?? 0;
        if ($n === 0) $stopped[$pkg] = $b;
        elseif ($b >= 100 && $n < 0.2 * $b) {
            al_add($found, "data_low|$pkg", 2, 'data_low', L(al_name($ctx, $pkg) . ': very little data coming in', al_name($ctx, $pkg) . ': data bahut kam aa raha'),
                L('Only ' . $n . ' events in the last 60 min, normally ~' . round($b) . '/hour. Cause: server slow, app update, or fewer users. Impact: live numbers for this app are unreliable. Fix: open the dashboard in a browser and check api/collect.php; if the app is fine, wait one hour.',
                  'Pichhle 60 min me sirf ' . $n . ' events, normally ~' . round($b) . '/ghanta. Wajah: server slow, app update, ya users kam. Asar: is app ke live numbers bharose ke nahi. Fix: browser me dashboard kholo, api/collect.php check karo; app theek ho to ek ghanta dekho.'),
                ['events_60m' => $n, 'baseline_hour' => round($b)], $pkg);
        }
    }
    if (count($stopped) >= 3 && count($stopped) >= 0.5 * $alive) {
        $names = implode(', ', array_map(fn($p) => al_name($ctx, $p), array_keys($stopped)));
        al_add($found, 'collector_down', 3, 'collector_down', L('SERVER: data stopped from ' . count($stopped) . ' apps at once', 'SERVER: ' . count($stopped) . ' apps se data ek saath band'),
            L("0 events for 60 min: $names. This looks like a server problem, not the apps: hosting down / SSL expired / api/collect.php error / DB full. Impact: no tracking, no alerts, revenue data lost for this period. Fix: open the dashboard in a browser; check hosting status, SSL and DB space.",
              "60 min se 0 events: $names. Yeh apps ki nahi, server ki dikkat lagti hai — hosting down / SSL expire / api/collect.php error / DB full. Asar: tracking band, alerts band, is time ka revenue data gaya. Fix: browser me dashboard kholo; hosting status, SSL aur DB space check karo."),
            ['apps' => array_keys($stopped), 'alive_apps' => $alive]);
    } else {
        foreach ($stopped as $pkg => $b) {
            al_add($found, "data_stop|$pkg", 3, 'data_stop', L(al_name($ctx, $pkg) . ': data STOPPED', al_name($ctx, $pkg) . ': data aana BAND'),
                L('0 events in the last 60 min (normally ~' . round($b) . '/hour). Cause: app crashing on launch, tracker_key changed, or removed from Play Store. Impact: if users cannot open the app you lose ~' . round($b) . ' sessions/hour of ad revenue. Fix: open the app on a phone, check Play Console status and the tracker config.',
                  'Pichhle 60 min me 0 events (normally ~' . round($b) . '/ghanta). Wajah: app crash on launch, tracker_key badla, ya Play Store se hata diya. Asar: users app nahi khol pa rahe to ~' . round($b) . ' sessions/ghanta ki ad kamai ja rahi hai. Fix: phone me app kholo, Play Console status aur tracker config check karo.'),
                ['events_60m' => 0, 'baseline_hour' => round($b)], $pkg);
        }
    }
    // ads: users aa rahe par impressions nahi
    $ibase = [];
    foreach ($pdo->query("SELECT pkg, SUM(imps)/14 b FROM agg_imp_h WHERE lh IN ($keys) GROUP BY pkg") as $r) $ibase[$r['pkg']] = (float)$r['b'];
    $icur = [];
    foreach ($pdo->query('SELECT pkg, COUNT(*) n FROM imps WHERE ts >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 60 MINUTE) GROUP BY pkg') as $r) $icur[$r['pkg']] = (int)$r['n'];
    foreach ($ibase as $pkg => $b) {
        if (!isset($ctx['apps'][$pkg]) || $b < AL_MIN_IMP_HOUR || isset($stopped[$pkg])) continue;
        $n = $icur[$pkg] ?? 0;
        if ($n === 0) al_add($found, "imps_stop|$pkg", 3, 'imps_stop', L(al_name($ctx, $pkg) . ': ADS stopped (0 impressions)', al_name($ctx, $pkg) . ': ADS band (0 impressions)'),
            L('Users are active (' . ($cur[$pkg] ?? 0) . ' events) but 0 ad impressions in 60 min, normally ~' . round($b) . '/hour. Impact: every hour this continues costs the full ad revenue of this app. Cause: MAX/AdMob account issue, ad units disabled, ad serving limit, or SDK init failing in a new version. Fix: open AppLovin/AdMob dashboards for this app, check account status and ad unit status, then test an ad on a phone.',
              'Users active hain (' . ($cur[$pkg] ?? 0) . ' events) par 60 min me 0 ad impressions, normally ~' . round($b) . '/ghanta. Asar: har ghanta is app ki poori ad kamai ja rahi hai. Wajah: MAX/AdMob account issue, ad units band, ad serving limit, ya naye version me SDK init fail. Fix: AppLovin/AdMob dashboard me is app ka account aur ad unit status dekho, phir phone par ad test karo.'),
            ['imps_60m' => 0, 'baseline_hour' => round($b), 'events_60m' => $cur[$pkg] ?? 0], $pkg);
        elseif ($b >= 100 && $n < 0.25 * $b) al_add($found, "imps_low|$pkg", 2, 'imps_low', L(al_name($ctx, $pkg) . ': impressions dropped', al_name($ctx, $pkg) . ': impressions gir gaye'),
            L($n . ' impressions in 60 min, normally ~' . round($b) . ' (−' . round((1 - $n / $b) * 100) . '%). Cause: fill rate down, ad load errors, or fewer users. Impact: roughly ' . round((1 - $n / $b) * 100) . '% of this hour\'s ad revenue. Fix: check the ad unit alerts below / Ads tab for which network or format is failing.',
              '60 min me ' . $n . ' impressions, normally ~' . round($b) . ' (−' . round((1 - $n / $b) * 100) . '%). Wajah: fill rate gira, ad load errors, ya users kam. Asar: is ghante ki ~' . round((1 - $n / $b) * 100) . '% ad kamai. Fix: neeche ad unit alerts / Ads tab me dekho kaunsa network ya format fail ho raha hai.'),
            ['imps_60m' => $n, 'baseline_hour' => round($b)], $pkg);
    }
}

/** 2) Dashboard summary cron peeche */
function al_rule_agg(PDO $pdo, array $ctx, array &$found): void
{
    if (!function_exists('agg_lag_sec')) return;
    $lag = agg_lag_sec();
    if ($lag > 3600) al_add($found, 'agg_lag', 3, 'agg_lag', L('Dashboard cron is down (' . intdiv($lag, 60) . ' min behind)', 'Dashboard cron band hai (' . intdiv($lag, 60) . ' min peeche)'),
        L('cron/run.php is not running: dashboard numbers will be stale and most alert rules go blind. Fix: check the Scheduled Task in Plesk/cPanel (every minute) and run it once by hand.', 'cron/run.php chal nahi raha — dashboard ke numbers purane dikhenge aur zyadatar alert rules andhe ho jaayenge. Fix: Plesk/cPanel me Scheduled Task (har minute) check karo, ek baar haath se chalao.'), ['lag_sec' => $lag]);
    elseif ($lag > 900) al_add($found, 'agg_lag', 2, 'agg_lag', L('Dashboard summary ' . intdiv($lag, 60) . ' min behind', 'Dashboard summary ' . intdiv($lag, 60) . ' min peeche'),
        L('cron/run.php should run every minute. If this keeps growing the cron has stopped.', 'cron/run.php har minute chalna chahiye. Agar yeh badhta rahe to cron ruk gaya hai.'), ['lag_sec' => $lag]);
}

/** 3) Google Ads script ne data bhejna band kiya (account-wise) */
function al_rule_gads_feed(PDO $pdo, array $ctx, array &$found): void
{
    ensure_gads_account($pdo);
    $rows = al_q($pdo, 'SELECT a.customer_id, a.name, a.updated_at, (SELECT SUM(cost) FROM gads_campaign_day g WHERE g.customer_id = a.customer_id AND g.day >= :d7) spend7
        FROM gads_account a', [':d7' => $ctx['back'](7)]);
    foreach ($rows as $r) {
        if ((float)($r['spend7'] ?? 0) <= 0) continue;   // dormant account
        $age = (time() - strtotime($r['updated_at'] . ' UTC')) / 60;
        if ($age > AL_GADS_STALE_MIN) {
            $nm = $r['name'] !== '' ? $r['name'] : $r['customer_id'];
            al_add($found, 'gads_stale|' . $r['customer_id'], 3, 'gads_stale', L("Google Ads ($nm): no data from the script for " . round($age / 60, 1) . ' hours', "Google Ads ($nm): script ne " . round($age / 60, 1) . ' ghante se data nahi bheja'),
                L('The Ads Script sends every hour. Impact: campaign, CPI and ROAS alerts for this account are blind until it resumes. Fix: Google Ads > Tools > Scripts: schedule removed, authorization expired, or script error; re-authorize and run once.',
                  'Ads Script har ghante bhejti hai. Asar: is account ki campaign, CPI aur ROAS alerts tab tak andhi hain. Fix: Google Ads > Tools > Scripts me dekho: schedule hat gayi, authorization expire, ya script error; dobara authorize karke ek baar chalao.'),
                ['minutes_since' => round($age), 'customer_id' => $r['customer_id']], '', (string)$r['customer_id']);
        }
    }
}

/** 4) Campaign-level: 0 impressions, impressions gir gaye, spend bina install, CPI high, spend spike, nayi/paused campaign */
function al_rule_campaigns(PDO $pdo, array $ctx, array &$found): void
{
    $today = $ctx['today'];
    $rows = al_q($pdo, "SELECT t.customer_id, t.campaign_id, t.campaign, t.pkg, t.status, IFNULL(a.name,'') account,
          t.cost*IF(t.currency='USD',{$ctx['rateSql']},1) cost, t.impressions impr, t.clicks, t.conversions conv,
          (SELECT AVG(x.impressions) FROM gads_campaign_day x WHERE x.customer_id=t.customer_id AND x.campaign_id=t.campaign_id AND x.day >= :d3 AND x.day < :today) impr3,
          (SELECT SUM(x.cost*IF(x.currency='USD',{$ctx['rateSql']},1)) FROM gads_campaign_day x WHERE x.customer_id=t.customer_id AND x.campaign_id=t.campaign_id AND x.day >= :d7 AND x.day < :today) cost7,
          (SELECT SUM(x.conversions) FROM gads_campaign_day x WHERE x.customer_id=t.customer_id AND x.campaign_id=t.campaign_id AND x.day >= :d7 AND x.day < :today) conv7,
          (SELECT COUNT(*) FROM gads_campaign_day x WHERE x.customer_id=t.customer_id AND x.campaign_id=t.campaign_id AND x.day < :today) days_before,
          (SELECT SUM(x.cost*IF(x.currency='USD',{$ctx['rateSql']},1)) FROM gads_campaign_day x WHERE x.customer_id=t.customer_id AND x.campaign_id=t.campaign_id AND x.day = :yest) cost_yest
        FROM gads_campaign_day t LEFT JOIN gads_account a ON a.customer_id = t.customer_id
        WHERE t.day = :today", [':today' => $today, ':d3' => $ctx['back'](3), ':d7' => $ctx['back'](7), ':yest' => $ctx['yest']]);
    // campaigns jo kal chali par aaj row hi nahi (script ne aaj ka data nahi bheja ya campaign gayab)
    $missing = al_q($pdo, "SELECT y.customer_id, y.campaign_id, y.campaign, y.pkg, y.status, IFNULL(a.name,'') account, y.cost*IF(y.currency='USD',{$ctx['rateSql']},1) cost_yest,
          (SELECT AVG(x.impressions) FROM gads_campaign_day x WHERE x.customer_id=y.customer_id AND x.campaign_id=y.campaign_id AND x.day >= :d3 AND x.day < :today) impr3
        FROM gads_campaign_day y LEFT JOIN gads_account a ON a.customer_id = y.customer_id
        WHERE y.day = :yest AND y.status = 'ENABLED' AND y.cost > 0
          AND NOT EXISTS (SELECT 1 FROM gads_campaign_day z WHERE z.customer_id=y.customer_id AND z.campaign_id=y.campaign_id AND z.day = :today)",
        [':today' => $today, ':yest' => $ctx['yest'], ':d3' => $ctx['back'](3)]);
    $h = $ctx['h']; $frac = $ctx['frac'];
    $acc = [];
    foreach ($rows as $r) {
        $cid = $r['customer_id'] . '|' . $r['campaign_id'];
        $cname = $r['campaign'] !== '' ? $r['campaign'] : ('Campaign ' . $r['campaign_id']);
        $appn = $r['pkg'] !== '' ? al_name($ctx, $r['pkg']) : '';
        $lbl = $cname . ($appn !== '' ? " ($appn)" : '') . ($r['account'] !== '' ? ' · ' . $r['account'] : '');
        $cost = (float)$r['cost']; $impr = (int)$r['impr']; $conv = (float)$r['conv'];
        $impr3 = (float)($r['impr3'] ?? 0); $cost7 = (float)($r['cost7'] ?? 0); $conv7 = (float)($r['conv7'] ?? 0);
        $cpi7 = $conv7 > 0 ? $cost7 / $conv7 : null;
        $m = ['campaign' => $cname, 'account' => $r['account'], 'status' => $r['status'], 'cost_today' => round($cost), 'impressions_today' => $impr, 'clicks_today' => (int)$r['clicks'],
            'installs_today' => $conv, 'avg_impressions_3d' => round($impr3), 'cost_7d' => round($cost7), 'installs_7d' => $conv7, 'cpi_7d' => $cpi7 !== null ? round($cpi7) : null, 'local_hour' => $h];
        $acc[$r['customer_id']] = ($acc[$r['customer_id']] ?? ['name' => $r['account'], 'cost' => 0.0, 'cost7' => 0.0]);
        $acc[$r['customer_id']]['cost'] += $cost; $acc[$r['customer_id']]['cost7'] += $cost7;

        if ((int)$r['days_before'] === 0 && $cost > 0) {
            al_add($found, "camp_new|$cid|$today", 1, 'camp_new', L("New campaign started: $lbl", "Nayi campaign shuru: $lbl"),
            L('So far today ' . al_inr($cost) . ' spent, ' . $impr . ' impressions, ' . $conv . ' installs. First 3 days are the learning phase: CPI will be a bit high; do not judge ROAS before D3.', 'Aaj ab tak ' . al_inr($cost) . ' kharch, ' . $impr . ' impressions, ' . $conv . ' installs. Pehle 3 din learning phase: CPI thoda high rahega; D3 se pehle ROAS mat aanko.'), $m, $r['pkg'], $r['customer_id'], $cname);
            continue;
        }
        if ($r['status'] !== 'ENABLED') {
            if ((float)($r['cost_yest'] ?? 0) >= 200) al_add($found, "camp_paused|$cid|$today", 1, 'camp_paused', L("Campaign paused: $lbl", "Campaign ruki: $lbl"),
            L('Status is now ' . $r['status'] . ' (spent ' . al_inr((float)$r['cost_yest']) . ' yesterday). Fine if intentional; otherwise check budget, payment method and policy status in Google Ads.', 'Status ab ' . $r['status'] . ' hai (kal ' . al_inr((float)$r['cost_yest']) . ' kharch tha). Jaan-boojh kar roki hai to theek, warna Google Ads me budget, payment aur policy status check karo.'), $m, $r['pkg'], $r['customer_id'], $cname);
            continue;
        }
        // 0 impressions / gir gaye (ENABLED)
        if ($impr3 >= 300 && $h >= 9) {
            if ($impr === 0) al_add($found, "camp_zero|$cid", 3, 'camp_zero', L("Campaign not running: $lbl", "Campaign chal nahi rahi: $lbl"),
            L('0 impressions by ' . $h . ':00 today, normally ~' . round($impr3) . '/day. Cause: budget exhausted, payment failed, ad disapproved, or "limited by budget". Impact: no new paid installs today, ~' . round($impr3) . ' impressions lost. Fix: open the campaign in Google Ads and read the status column.', 'Aaj ' . $h . ' baje tak 0 impressions, normally ~' . round($impr3) . '/din. Wajah: budget khatam, payment fail, ad disapproved, ya "limited by budget". Asar: aaj naye paid installs nahi, ~' . round($impr3) . ' impressions gaye. Fix: Google Ads me campaign kholo, status column padho.'), $m, $r['pkg'], $r['customer_id'], $cname);
            elseif ($h >= 12 && $impr3 >= 1000 && $impr < 0.25 * $impr3 * $frac) al_add($found, "camp_imps_low|$cid", 2, 'camp_imps_low', L("Impressions dropped: $lbl", "Impressions gir gaye: $lbl"),
            L($impr . ' impressions so far today, normally ~' . round($impr3 * $frac) . ' by this time. Cause: bid/budget lowered, ads disapproved, or ad rank fell. Impact: fewer installs today. Fix: check ad status and "limited by budget" in Google Ads; raise bid or budget only if ROAS allows.', 'Aaj ab tak ' . $impr . ' impressions, is time tak normally ~' . round($impr3 * $frac) . '. Wajah: bid/budget kam hua, ads disapprove, ya ad rank gira. Asar: aaj installs kam. Fix: Google Ads me ad status aur "limited by budget" dekho; bid/budget tabhi badhao jab ROAS allow kare.'), $m, $r['pkg'], $r['customer_id'], $cname);
        }
        // kharch par install nahi
        $limit = max(AL_MIN_SPEND_NOINST, $cpi7 !== null ? 3 * $cpi7 : 0);
        if ($conv <= 0 && $cost >= $limit) al_add($found, "camp_noinst|$cid", 3, 'camp_noinst', L("Spending, 0 installs: $lbl", "Paisa ja raha, install 0: $lbl"),
            L(al_inr($cost) . ' spent today and 0 installs' . ($cpi7 !== null ? ' (7-day CPI was ' . al_inr($cpi7) . ')' : '') . '. Impact: ' . al_inr($cost) . ' wasted so far, growing every hour. Cause: conversion tracking broken, Play listing/APK problem, or wrong targeting. Fix: pause the campaign, check Google Ads conversions and the Play listing, then resume.', 'Aaj ' . al_inr($cost) . ' kharch aur 0 installs' . ($cpi7 !== null ? ' (7 din ka CPI ' . al_inr($cpi7) . ' tha)' : '') . '. Asar: ab tak ' . al_inr($cost) . ' bekaar gaya, har ghante badh raha. Wajah: conversion tracking tooti, Play listing/APK me dikkat, ya galat targeting. Fix: campaign pause karo, Google Ads conversions aur Play listing check karo, phir chalao.'), $m, $r['pkg'], $r['customer_id'], $cname);
        // CPI
        if ($cpi7 !== null && $conv >= 5 && $cost >= 300) {
            $cpi = $cost / $conv; $m['cpi_today'] = round($cpi);
            if ($cpi >= 2.5 * $cpi7) al_add($found, "cpi_high|$cid", 3, 'cpi_high', L("CPI 2.5x up: $lbl", "CPI 2.5x upar: $lbl"),
            L('CPI today ' . al_inr($cpi) . ' vs 7-day ' . al_inr($cpi7) . '. Impact: each install costs ' . al_inr($cpi - $cpi7) . ' more; ROAS will not recover at this price (~' . al_inr(($cpi - $cpi7) * $conv) . ' extra spent today). Fix: lower bid/budget or pause; check if a creative got disapproved or the audience changed.', 'Aaj CPI ' . al_inr($cpi) . ' vs 7 din ' . al_inr($cpi7) . '. Asar: har install ' . al_inr($cpi - $cpi7) . ' mehnga; is daam par ROAS nahi niklega (aaj ~' . al_inr(($cpi - $cpi7) * $conv) . ' extra gaya). Fix: bid/budget ghatao ya pause; dekho koi creative disapprove hua ya audience badli.'), $m, $r['pkg'], $r['customer_id'], $cname);
            elseif ($cpi >= 1.5 * $cpi7) al_add($found, "cpi_high|$cid", 2, 'cpi_high', L("CPI up: $lbl", "CPI badh gaya: $lbl"),
            L('CPI today ' . al_inr($cpi) . ' vs 7-day ' . al_inr($cpi7) . ' (+' . round(($cpi / $cpi7 - 1) * 100) . '%). Cause: creative fatigue or more competition. Impact: ~' . al_inr(($cpi - $cpi7) * $conv) . ' extra spent today. Fix: rotate in a fresh creative; do not raise budget until CPI returns.', 'Aaj CPI ' . al_inr($cpi) . ' vs 7 din ' . al_inr($cpi7) . ' (+' . round(($cpi / $cpi7 - 1) * 100) . '%). Wajah: creatives thak gaye ya competition badhi. Asar: aaj ~' . al_inr(($cpi - $cpi7) * $conv) . ' extra gaya. Fix: naya creative daalo; CPI wapas aane tak budget mat badhao.'), $m, $r['pkg'], $r['customer_id'], $cname);
        }
    }
    foreach ($missing as $r) {
        $cid = $r['customer_id'] . '|' . $r['campaign_id'];
        if ((float)($r['impr3'] ?? 0) < 300 || $h < 11) continue;
        $cname = $r['campaign'] !== '' ? $r['campaign'] : ('Campaign ' . $r['campaign_id']);
        al_add($found, "camp_zero|$cid", 3, 'camp_zero', L("No data today for campaign: $cname", "Campaign ka aaj ka data hi nahi: $cname"),
            L('Spent ' . al_inr((float)$r['cost_yest']) . ' yesterday, but no row for today by ' . $h . ':00. Either the campaign was removed or the Ads Script did not send today. Fix: check the campaign in Google Ads and the script run log.', 'Kal ' . al_inr((float)$r['cost_yest']) . ' kharch thi, aaj ' . $h . ' baje tak koi row nahi aayi. Campaign hata di gayi ya script ne aaj ka data nahi bheja. Fix: Google Ads me campaign aur script ka run log dekho.'), ['cost_yesterday' => round((float)$r['cost_yest'])], $r['pkg'], $r['customer_id'], $cname);
    }
    // account-level spend spike
    foreach ($acc as $cust => $a) {
        $exp = ($a['cost7'] / 7) * $frac;
        if ($a['cost7'] > 0 && $a['cost'] >= 2 * $exp && $a['cost'] - $exp >= 1000) {
            $nm = $a['name'] !== '' ? $a['name'] : $cust;
            al_add($found, "spend_spike|$cust", 2, 'spend_spike', L("Spend 2x faster: $nm", "Spend 2x tez: $nm"),
            L(al_inr($a['cost']) . ' spent so far today, normally ~' . al_inr($exp) . ' by this time (+' . al_inr($a['cost'] - $exp) . '). Did you raise a budget? If not, a campaign may be overspending. Fix: sort campaigns by cost today in Google Ads and check budgets.', 'Aaj ab tak ' . al_inr($a['cost']) . ' kharch, is time tak normally ~' . al_inr($exp) . ' (+' . al_inr($a['cost'] - $exp) . '). Budget badhaya tha? Nahi to koi campaign zyada kha rahi hai. Fix: Google Ads me campaigns ko aaj ke cost se sort karke budgets dekho.'), ['cost_today' => round($a['cost']), 'expected_by_now' => round($exp), 'avg_daily_7d' => round($a['cost7'] / 7)], '', (string)$cust);
        }
    }
}

/** 5) ROAS: app-level blended (3 din) + campaign cohort D3 (utm_campaign) */
function al_rule_roas(PDO $pdo, array $ctx, array &$found): void
{
    $CX = "cost*IF(currency='USD',{$ctx['rateSql']},1)";
    $d1 = $ctx['back'](3); $d2 = $ctx['yest'];
    $spend = [];
    foreach (al_q($pdo, "SELECT pkg, SUM($CX) c FROM gads_campaign_day WHERE day >= :d1 AND day <= :d2 AND pkg <> '' GROUP BY pkg", [':d1' => $d1, ':d2' => $d2]) as $r) $spend[$r['pkg']] = (float)$r['c'];
    if (!$spend) return;
    $rev = [];
    foreach (al_q($pdo, 'SELECT pkg, SUM(rev) r FROM agg_imp_d WHERE day >= :d1 AND day <= :d2 GROUP BY pkg', [':d1' => $d1, ':d2' => $d2]) as $r) $rev[$r['pkg']] = (float)$r['r'] * $ctx['rate'];
    foreach (al_q($pdo, "SELECT pkg, SUM(v) p FROM agg_pay WHERE ev='purchase_done' AND day >= :d1 AND day <= :d2 GROUP BY pkg", [':d1' => $d1, ':d2' => $d2]) as $r) $rev[$r['pkg']] = ($rev[$r['pkg']] ?? 0) + (float)$r['p'];
    foreach ($spend as $pkg => $sp) {
        if ($sp < AL_ROAS_SPEND_MIN) continue;
        $rv = $rev[$pkg] ?? 0.0; $roas = $rv / $sp;
        $m = ['days' => "$d1..$d2", 'spend_inr' => round($sp), 'revenue_inr' => round($rv), 'roas' => round($roas, 2), 'loss_inr' => round($sp - $rv)];
        $lbl = al_name($ctx, $pkg);
        if ($roas < 0.6) al_add($found, "roas_low|$pkg", 3, 'roas_low', L("$lbl: ROAS " . al_pct($roas) . ' — LOSS', "$lbl: ROAS " . al_pct($roas) . ' — LOSS'),
        L('3 days: ' . al_inr($sp) . ' spent, ' . al_inr($rv) . ' earned (ads + payments), loss ' . al_inr($sp - $rv) . '. Break-even is 100%. Fix: cut campaign budgets or pause the costliest campaign; keep only campaigns whose D3 cohort ROAS is above 60%.', '3 din: ' . al_inr($sp) . ' kharch, ' . al_inr($rv) . ' kamai (ads + payments), nuksan ' . al_inr($sp - $rv) . '. Break-even 100% hai. Fix: campaign budget ghatao ya sabse mehengi campaign pause karo; sirf wahi campaigns rakho jinka D3 cohort ROAS 60% se upar hai.'), $m, $pkg);
        elseif ($roas < 0.85) al_add($found, "roas_low|$pkg", 2, 'roas_low', L("$lbl: ROAS " . al_pct($roas) . ' (below break-even)', "$lbl: ROAS " . al_pct($roas) . ' (break-even se neeche)'),
        L('3 days: ' . al_inr($sp) . ' spent, ' . al_inr($rv) . ' earned (gap ' . al_inr($sp - $rv) . '). Target 150%. New users keep earning until D7, but under 85% is risky. Fix: do not scale; trim the weakest campaign.', '3 din: ' . al_inr($sp) . ' kharch, ' . al_inr($rv) . ' kamai (farq ' . al_inr($sp - $rv) . '). Target 150%. Naye users ki kamai D7 tak badhti hai, par 85% se neeche risky hai. Fix: scale mat karo; sabse kamzor campaign ghatao.'), $m, $pkg);
    }
    // campaign cohort: 4-7 din pehle install hue (utm_campaign) -> D3 revenue vs un dino ka spend
    $cf = $ctx['back'](7); $ct = $ctx['back'](3);
    [$uf] = day_bounds_utc($cf); [, $ut] = day_bounds_utc($ct);   // [cf 00:00, ct 00:00) = 4 poore din
    $camps = [];
    foreach (al_q($pdo, "SELECT pkg, utm_campaign c, COUNT(*) n FROM devices WHERE utm_campaign <> '' AND first_seen >= :f AND first_seen < :t GROUP BY pkg, utm_campaign", [':f' => $uf, ':t' => $ut]) as $r)
        $camps[$r['pkg'] . '|' . strtolower(trim($r['c']))] = ['pkg' => $r['pkg'], 'campaign' => $r['c'], 'installs' => (int)$r['n'], 'spend' => 0.0, 'rev3' => 0.0];
    if (!$camps) return;
    foreach (al_q($pdo, "SELECT pkg, LOWER(TRIM(campaign)) c, SUM($CX) cost FROM gads_campaign_day WHERE day >= :d1 AND day < :d2 GROUP BY pkg, LOWER(TRIM(campaign))", [':d1' => $cf, ':d2' => $ct]) as $r) {
        $k = $r['pkg'] . '|' . $r['c']; if (isset($camps[$k])) $camps[$k]['spend'] += (float)$r['cost'];
    }
    $tz = $ctx['tzoff'];
    foreach (al_q($pdo, "SELECT d.pkg, LOWER(TRIM(d.utm_campaign)) c, SUM(i.rev_usd) rev FROM devices d JOIN imps i ON i.pkg=d.pkg AND i.did=d.did
        WHERE d.utm_campaign <> '' AND d.first_seen >= :f AND d.first_seen < :t
          AND DATEDIFF(DATE(CONVERT_TZ(i.ts,'+00:00','$tz')), DATE(CONVERT_TZ(d.first_seen,'+00:00','$tz'))) <= 3 GROUP BY d.pkg, LOWER(TRIM(d.utm_campaign))", [':f' => $uf, ':t' => $ut]) as $r) {
        $k = $r['pkg'] . '|' . $r['c']; if (isset($camps[$k])) $camps[$k]['rev3'] += (float)$r['rev'] * $ctx['rate'];
    }
    foreach (al_q($pdo, "SELECT d.pkg, LOWER(TRIM(d.utm_campaign)) c, SUM(x.v) amt FROM (SELECT e.pkg, e.did, e.p2, MAX(e.val) v FROM events e WHERE e.ev='purchase_done' AND e.ts >= :f GROUP BY e.pkg, e.did, e.p2) x
        JOIN devices d ON d.pkg=x.pkg AND d.did=x.did WHERE d.utm_campaign <> '' AND d.first_seen >= :f AND d.first_seen < :t GROUP BY d.pkg, LOWER(TRIM(d.utm_campaign))", [':f' => $uf, ':t' => $ut]) as $r) {
        $k = $r['pkg'] . '|' . $r['c']; if (isset($camps[$k])) $camps[$k]['rev3'] += (float)$r['amt'];
    }
    foreach ($camps as $c) {
        if ($c['spend'] < AL_ROAS_SPEND_MIN) continue;
        $roas = $c['rev3'] / $c['spend'];
        $m = ['cohort_days' => "$cf..$ct", 'installs' => $c['installs'], 'spend_inr' => round($c['spend']), 'revenue_d3_inr' => round($c['rev3']), 'roas_d3' => round($roas, 2), 'target_d3' => 0.75];
        $lbl = $c['campaign'] . ' (' . al_name($ctx, $c['pkg']) . ')';
        if ($roas < 0.4) al_add($found, 'camp_roas|' . $c['pkg'] . '|' . strtolower($c['campaign']), 3, 'camp_roas', L("Campaign in loss: $lbl — D3 ROAS " . al_pct($roas), "Campaign loss me: $lbl — D3 ROAS " . al_pct($roas)),
            L($c['installs'] . ' installs from 4-7 days ago: ' . al_inr($c['spend']) . ' spent, ' . al_inr($c['rev3']) . ' earned in 3 days (loss so far ' . al_inr($c['spend'] - $c['rev3']) . '). Target D3 = 75%. This campaign will not pay back. Fix: pause or cut budget; keep its best creative for a new test.', '4-7 din pehle wale ' . $c['installs'] . ' installs: ' . al_inr($c['spend']) . ' kharch, 3 din me ' . al_inr($c['rev3']) . ' kamai (ab tak nuksan ' . al_inr($c['spend'] - $c['rev3']) . '). Target D3 = 75%. Yeh campaign paisa nahi lautayegi. Fix: pause ya budget kam; iska best creative naye test ke liye rakho.'), $m, $c['pkg'], '', $c['campaign']);
        elseif ($roas < 0.6) al_add($found, 'camp_roas|' . $c['pkg'] . '|' . strtolower($c['campaign']), 2, 'camp_roas', L("Campaign slow: $lbl — D3 ROAS " . al_pct($roas), "Campaign dheemi: $lbl — D3 ROAS " . al_pct($roas)),
            L(al_inr($c['spend']) . ' spent, ' . al_inr($c['rev3']) . ' earned in 3 days (target 75%). Fix: change creatives/targeting, do not scale until D3 crosses 75%.', al_inr($c['spend']) . ' kharch, 3 din me ' . al_inr($c['rev3']) . ' kamai (target 75%). Fix: creatives/targeting badlo, D3 75% paar hone tak scale mat karo.'), $m, $c['pkg'], '', $c['campaign']);
    }
}

/** 6) Crash spike (60 min) */
function al_rule_crash(PDO $pdo, array $ctx, array &$found): void
{
    $base = [];
    foreach (al_q($pdo, "SELECT pkg, SUM(c)/168 b FROM agg_ev_h WHERE ev='crash' AND lh >= :f AND lh < :t GROUP BY pkg", [':f' => $ctx['back'](7) . ' 00:00:00', ':t' => $ctx['today'] . ' 00:00:00']) as $r) $base[$r['pkg']] = (float)$r['b'];
    $sess = [];
    foreach ($pdo->query("SELECT pkg, COUNT(*) n FROM events WHERE ev='session_start' AND ts >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 60 MINUTE) GROUP BY pkg") as $r) $sess[$r['pkg']] = (int)$r['n'];
    foreach ($pdo->query("SELECT pkg, COUNT(*) n, COUNT(DISTINCT did) u FROM events WHERE ev='crash' AND ts >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 60 MINUTE) GROUP BY pkg") as $r) {
        $pkg = $r['pkg']; $n = (int)$r['n']; $b = $base[$pkg] ?? 0.0; $s = $sess[$pkg] ?? 0;
        $rate = $s > 0 ? $n / $s : null;
        $lvl = 0;
        if ($n >= 10 && $n >= 4 * $b + 5) $lvl = 3;
        elseif ($n >= 5 && $n >= 2 * $b + 3) $lvl = 2;
        if ($lvl === 2 && $rate !== null && $rate >= 0.05 && $s >= 50) $lvl = 3;
        if ($lvl === 0) continue;
        $top = al_q($pdo, "SELECT ver, p1, LEFT(p2, 160) p2, COUNT(*) n FROM events WHERE ev='crash' AND pkg = ? AND ts >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 60 MINUTE) GROUP BY ver, p1, LEFT(p2,160) ORDER BY n DESC LIMIT 3", [$pkg]);
        $t = $top[0] ?? null;
        $m = ['crashes_60m' => $n, 'crash_users_60m' => (int)$r['u'], 'baseline_per_hour' => round($b, 1), 'sessions_60m' => $s, 'crash_per_session' => $rate !== null ? round($rate, 3) : null, 'top' => $top];
        $where = $t ? ' Sabse zyada: v' . $t['ver'] . ' · ' . $t['p1'] . ($t['p2'] !== '' ? ' — ' . $t['p2'] : '') . ' (' . $t['n'] . 'x).' : '';
        $whereEn = $t ? ' Most common: v' . $t['ver'] . ' · ' . $t['p1'] . ($t['p2'] !== '' ? ' — ' . $t['p2'] : '') . ' (' . $t['n'] . 'x).' : '';
        al_add($found, "crash_spike|$pkg", $lvl, 'crash_spike', L(al_name($ctx, $pkg) . ": $n crashes in 60 min" . ($lvl === 3 ? ' — URGENT' : ''), al_name($ctx, $pkg) . ": $n crashes 60 min me" . ($lvl === 3 ? ' — URGENT' : '')),
            L('Normally ~' . round($b, 1) . '/hour.' . ($rate !== null ? ' Crash/session ' . al_pct($rate, 1) . '.' : '') . $whereEn . ' Impact: crashing users see no ads and uninstall more. Fix: if a new version is rolling out, halt it in Play Console; open Vitals for the stack trace and fix the top error first.',
              'Normally ~' . round($b, 1) . '/ghanta.' . ($rate !== null ? ' Crash/session ' . al_pct($rate, 1) . '.' : '') . $where . ' Asar: crash wale users ko ads nahi dikhte aur uninstall zyada. Fix: naya version roll out ho raha ho to Play Console me halt karo; Vitals me stack trace dekho, pehle top error fix karo.'), $m, $pkg);
    }
}

/** 7) Naya version crash kar raha (aaj, version-wise) */
function al_rule_ver_crash(PDO $pdo, array $ctx, array &$found): void
{
    $rows = al_q($pdo, 'SELECT v.pkg, v.ver, COUNT(DISTINCT v.did) users, IFNULL((SELECT SUM(c.n) FROM agg_crash_d c WHERE c.day = :d2 AND c.pkg = v.pkg AND c.ver = v.ver), 0) crashes
        FROM day_ver v WHERE v.day = :d GROUP BY v.pkg, v.ver HAVING users >= 100', [':d' => $ctx['today'], ':d2' => $ctx['today']]);
    $byPkg = [];
    foreach ($rows as $r) $byPkg[$r['pkg']][] = $r;
    foreach ($byPkg as $pkg => $vs) {
        $totU = 0; $totC = 0;
        foreach ($vs as $v) { $totU += (int)$v['users']; $totC += (int)$v['crashes']; }
        foreach ($vs as $v) {
            $u = (int)$v['users']; $c = (int)$v['crashes']; $rate = $c / $u;
            $oU = $totU - $u; $oRate = $oU > 0 ? ($totC - $c) / $oU : 0.0;
            if ($rate < 0.05 || $c < 10 || ($oU > 0 && $rate < 2 * $oRate)) continue;
            $lvl = ($u >= 300 && $rate >= 0.10) ? 3 : 2;
            $m = ['version' => $v['ver'], 'users_today' => $u, 'crashes_today' => $c, 'crash_rate' => round($rate, 3), 'other_versions_rate' => round($oRate, 3)];
            al_add($found, "ver_crash|$pkg|" . $v['ver'], $lvl, 'ver_crash', L(al_name($ctx, $pkg) . ' v' . $v['ver'] . ': ' . al_pct($rate, 1) . ' of users crash', al_name($ctx, $pkg) . ' v' . $v['ver'] . ': ' . al_pct($rate, 1) . ' users crash'),
            L("$c crashes among $u users today (other versions " . al_pct($oRate, 1) . '). Impact: this version loses ad revenue and retention. Fix: halt the rollout / roll back, then read the stack trace in Play Console > Vitals.', "Aaj $u users me $c crashes (baaki versions " . al_pct($oRate, 1) . '). Asar: is version par ad kamai aur retention dono girte hain. Fix: rollout roko / purana version wapas lao, phir Play Console > Vitals me stack trace dekho.'), $m, $pkg);
        }
    }
}

/** 8) Ad fill gir gaya (aaj) */
function al_rule_fill(PDO $pdo, array $ctx, array &$found): void
{
    $today = $ctx['today'];
    $fails = []; $imps = []; $bf = []; $bi = [];
    foreach (al_q($pdo, 'SELECT pkg, SUM(n) n FROM agg_fail_d WHERE day = :d GROUP BY pkg', [':d' => $today]) as $r) $fails[$r['pkg']] = (int)$r['n'];
    foreach (al_q($pdo, 'SELECT pkg, SUM(imps) n FROM agg_imp_d WHERE day = :d GROUP BY pkg', [':d' => $today]) as $r) $imps[$r['pkg']] = (int)$r['n'];
    foreach (al_q($pdo, 'SELECT pkg, SUM(n) n FROM agg_fail_d WHERE day >= :f AND day < :t GROUP BY pkg', [':f' => $ctx['back'](7), ':t' => $today]) as $r) $bf[$r['pkg']] = (int)$r['n'];
    foreach (al_q($pdo, 'SELECT pkg, SUM(imps) n FROM agg_imp_d WHERE day >= :f AND day < :t GROUP BY pkg', [':f' => $ctx['back'](7), ':t' => $today]) as $r) $bi[$r['pkg']] = (int)$r['n'];
    foreach ($fails as $pkg => $f) {
        if ($f < 200 || !isset($ctx['apps'][$pkg])) continue;
        $i = $imps[$pkg] ?? 0; $rate = $f / ($f + $i);
        $bfr = (($bf[$pkg] ?? 0) + ($bi[$pkg] ?? 0)) > 0 ? ($bf[$pkg] ?? 0) / (($bf[$pkg] ?? 0) + ($bi[$pkg] ?? 0)) : 0.0;
        if ($rate < 0.6 || $rate < $bfr + 0.2) continue;
        $top = al_q($pdo, 'SELECT med, fmt, LEFT(reason, 120) reason, SUM(n) n FROM agg_fail_d WHERE day = :d AND pkg = :p GROUP BY med, fmt, LEFT(reason,120) ORDER BY n DESC LIMIT 3', [':d' => $today, ':p' => $pkg]);
        $t = $top[0] ?? null;
        al_add($found, "fill_drop|$pkg", 2, 'fill_drop', L(al_name($ctx, $pkg) . ': ad fail rate ' . al_pct($rate), al_name($ctx, $pkg) . ': ad fail rate ' . al_pct($rate)),
            L("$f ad fails vs $i impressions today (7-day fail rate " . al_pct($bfr) . ').' . ($t ? ' Most: ' . $t['med'] . ' ' . $t['fmt'] . ' — ' . $t['reason'] . '.' : '') . ' Impact: each failed load is a missed impression. Fix: check that ad unit / mediation waterfall and the "no fill" regions; add a backup network for that format.',
              "Aaj $f ad fails vs $i impressions (7 din me fail rate " . al_pct($bfr) . ').' . ($t ? ' Sabse zyada: ' . $t['med'] . ' ' . $t['fmt'] . ' — ' . $t['reason'] . '.' : '') . ' Asar: har fail load = ek impression gaya. Fix: us ad unit / mediation waterfall aur "no fill" region check karo; us format ke liye backup network lagao.'),
            ['fails_today' => $f, 'imps_today' => $i, 'fail_rate' => round($rate, 3), 'fail_rate_7d' => round($bfr, 3), 'top' => $top], $pkg);
    }
}

/** 8b) Ad network / ad unit level (60 min vs 7 din isi ghante): no-fill, impressions gire, eCPM gira, CTR gira. Har alert me nuksan ka andaza + fix. */
function al_rule_adunit(PDO $pdo, array $ctx, array &$found): void
{
    $keys = implode(',', al_hour_keys($ctx));
    $rate = $ctx['rate'];
    // baseline: pichhle 7 din ke isi ghante + pichhle ghante (14 slots) -> per-hour avg
    $base = [];
    foreach ($pdo->query("SELECT pkg, med, fmt, net, unit, SUM(imps)/14 i, SUM(rev)/14 r FROM agg_imp_h WHERE lh IN ($keys) GROUP BY pkg, med, fmt, net, unit") as $r)
        $base[$r['pkg'] . '|' . $r['med'] . '|' . $r['fmt'] . '|' . $r['net'] . '|' . $r['unit']] = ['i' => (float)$r['i'], 'r' => (float)$r['r'], 'pkg' => $r['pkg'], 'med' => $r['med'], 'fmt' => $r['fmt'], 'net' => $r['net'], 'unit' => $r['unit']];
    $cur = [];
    foreach ($pdo->query('SELECT pkg, med, fmt, net, unit, COUNT(*) i, SUM(rev_usd) r FROM imps WHERE ts >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 60 MINUTE) GROUP BY pkg, med, fmt, net, unit') as $r)
        $cur[$r['pkg'] . '|' . $r['med'] . '|' . $r['fmt'] . '|' . $r['net'] . '|' . $r['unit']] = ['i' => (int)$r['i'], 'r' => (float)$r['r']];
    // app-level: agar poori app ki ads band hain to unit alerts mat bharo (imps_stop already)
    $appCur = [];
    foreach ($cur as $k => $c) $appCur[explode('|', $k)[0]] = ($appCur[explode('|', $k)[0]] ?? 0) + $c['i'];
    // fails aaj: per app/med/fmt (agg_fail_d) + top reason
    $fails = [];
    foreach (al_q($pdo, 'SELECT pkg, med, fmt, SUM(n) n, SUBSTRING_INDEX(GROUP_CONCAT(LEFT(reason,80) ORDER BY n DESC SEPARATOR 0x1f), 0x1f, 1) reason FROM agg_fail_d WHERE day = :d GROUP BY pkg, med, fmt', [':d' => $ctx['today']]) as $r)
        $fails[$r['pkg'] . '|' . $r['med'] . '|' . $r['fmt']] = ['n' => (int)$r['n'], 'reason' => (string)$r['reason']];
    $failsBase = [];
    foreach (al_q($pdo, 'SELECT pkg, med, fmt, SUM(n)/7 n FROM agg_fail_d WHERE day >= :f AND day < :t GROUP BY pkg, med, fmt', [':f' => $ctx['back'](7), ':t' => $ctx['today']]) as $r)
        $failsBase[$r['pkg'] . '|' . $r['med'] . '|' . $r['fmt']] = (float)$r['n'];
    // clicks (CTR) 60 min vs baseline: agg_ev_h ev='ad_clicked' per pkg/fmt/med (agar app yeh event bhejti hai)
    $clkBase = []; $clkCur = [];
    foreach ($pdo->query("SELECT pkg, med, fmt, SUM(c)/14 c FROM agg_ev_h WHERE ev = 'ad_clicked' AND lh IN ($keys) GROUP BY pkg, med, fmt") as $r) $clkBase[$r['pkg'] . '|' . $r['med'] . '|' . $r['fmt']] = (float)$r['c'];
    foreach ($pdo->query("SELECT pkg, med, fmt, COUNT(*) c FROM events WHERE ev = 'ad_clicked' AND ts >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 60 MINUTE) GROUP BY pkg, med, fmt") as $r) $clkCur[$r['pkg'] . '|' . $r['med'] . '|' . $r['fmt']] = (int)$r['c'];

    $perNet = [];   // network-level roll-up
    foreach ($base as $k => $b) {
        if (!isset($ctx['apps'][$b['pkg']]) || $b['i'] < AL_ADUNIT_MIN_IMPS) continue;
        if (($appCur[$b['pkg']] ?? 0) === 0) continue;   // poori app band: imps_stop sambhalta hai
        $c = $cur[$k] ?? ['i' => 0, 'r' => 0.0];
        $app = al_name($ctx, $b['pkg']);
        $unitLbl = ($b['unit'] !== '' ? $b['unit'] : $b['fmt']) . ' (' . $b['fmt'] . ($b['net'] !== '' ? ' · ' . $b['net'] : '') . ($b['med'] !== '' ? ' · ' . $b['med'] : '') . ')';
        $lossHr = max(0.0, ($b['r'] - $c['r'])) * $rate;   // is ghante ka nuksan ₹
        $fk = $b['pkg'] . '|' . $b['med'] . '|' . $b['fmt'];
        $f = $fails[$fk] ?? null; $fb = $failsBase[$fk] ?? 0.0;
        $m = ['app' => $app, 'network' => $b['net'], 'ad_unit' => $b['unit'], 'format' => $b['fmt'], 'mediation' => $b['med'], 'imps_60m' => $c['i'], 'imps_baseline_hour' => round($b['i']),
            'rev_60m_inr' => round($c['r'] * $rate), 'rev_baseline_hour_inr' => round($b['r'] * $rate), 'loss_this_hour_inr' => round($lossHr),
            'ecpm_60m_usd' => $c['i'] > 0 ? round($c['r'] / $c['i'] * 1000, 3) : null, 'ecpm_baseline_usd' => $b['i'] > 0 ? round($b['r'] / $b['i'] * 1000, 3) : null,
            'fails_today' => $f['n'] ?? 0, 'fails_avg_day_7d' => round($fb), 'top_fail_reason' => $f['reason'] ?? ''];
        $nk = $b['pkg'] . '|' . $b['net'];
        $perNet[$nk] = $perNet[$nk] ?? ['pkg' => $b['pkg'], 'net' => $b['net'], 'bi' => 0.0, 'br' => 0.0, 'ci' => 0, 'cr' => 0.0];
        $perNet[$nk]['bi'] += $b['i']; $perNet[$nk]['br'] += $b['r']; $perNet[$nk]['ci'] += $c['i']; $perNet[$nk]['cr'] += $c['r'];

        // 1) NO FILL: unit ne 60 min me 0 impressions, aur fails badh gaye
        if ($c['i'] === 0) {
            $reason = $f ? $f['reason'] : '';
            $nofill = $reason !== '' && (stripos($reason, 'no fill') !== false || stripos($reason, 'no_fill') !== false || stripos($reason, 'NO_FILL') !== false || stripos($reason, '204') !== false);
            al_add($found, "adunit_nofill|$k", 3, 'adunit_nofill',
                L("$app: $unitLbl — 0 impressions" . ($nofill ? ' (NO FILL)' : ''), "$app: $unitLbl — 0 impressions" . ($nofill ? ' (NO FILL)' : '')),
                L('This ad unit normally serves ~' . round($b['i']) . ' impressions/hour (' . al_inr($b['r'] * $rate) . '/hour) and served 0 in the last 60 min while the app is active.' . ($f ? ' Today ' . $f['n'] . ' load failures on ' . $b['fmt'] . ' (' . $b['med'] . '), top reason: ' . $reason . '.' : '')
                  . ' Loss: ~' . al_inr($b['r'] * $rate) . ' per hour while it lasts. Cause: ad unit paused/disabled, network no-fill for your countries, waterfall misconfigured, or SDK adapter broken in a new version. Fix: open ' . ($b['med'] !== '' ? $b['med'] : 'the mediation') . ' dashboard > this ad unit: status, networks and fill by country; if only one network is empty, move it down and add a backup bidder.',
                  'Yeh ad unit normally ~' . round($b['i']) . ' impressions/ghanta (' . al_inr($b['r'] * $rate) . '/ghanta) deta hai, pichhle 60 min me 0 diye jabki app active hai.' . ($f ? ' Aaj ' . $f['n'] . ' load fail ' . $b['fmt'] . ' (' . $b['med'] . ') par, top wajah: ' . $reason . '.' : '')
                  . ' Nuksan: jab tak chalega ~' . al_inr($b['r'] * $rate) . ' har ghante. Wajah: ad unit paused/band, network ka tumhare countries me no-fill, waterfall galat, ya naye version me SDK adapter toota. Fix: ' . ($b['med'] !== '' ? $b['med'] : 'mediation') . ' dashboard > yeh ad unit: status, networks aur country-wise fill dekho; sirf ek network khali hai to use neeche karo aur backup bidder lagao.'),
                $m, $b['pkg']);
            continue;
        }
        // 2) impressions gire (fill issue)
        if ($b['i'] >= 300 && $c['i'] < 0.3 * $b['i']) {
            al_add($found, "adunit_drop|$k", 2, 'adunit_drop',
                L("$app: $unitLbl — impressions −" . round((1 - $c['i'] / $b['i']) * 100) . '%', "$app: $unitLbl — impressions −" . round((1 - $c['i'] / $b['i']) * 100) . '%'),
                L($c['i'] . ' impressions in 60 min vs ~' . round($b['i']) . ' normally.' . ($f && $f['n'] > 2 * $fb + 20 ? ' Load failures up today: ' . $f['n'] . ' (avg ' . round($fb) . '/day), top reason: ' . $f['reason'] . '.' : '')
                  . ' Loss this hour ~' . al_inr($lossHr) . '. Cause: lower fill from this network, a bid floor set too high, or fewer users on this screen. Fix: check fill rate and bid floor of this unit in the mediation dashboard; compare with the same unit yesterday.',
                  '60 min me ' . $c['i'] . ' impressions vs normally ~' . round($b['i']) . '.' . ($f && $f['n'] > 2 * $fb + 20 ? ' Aaj load fail badhe: ' . $f['n'] . ' (avg ' . round($fb) . '/din), top wajah: ' . $f['reason'] . '.' : '')
                  . ' Is ghante ka nuksan ~' . al_inr($lossHr) . '. Wajah: is network ka fill gira, bid floor zyada, ya is screen par users kam. Fix: mediation dashboard me is unit ka fill rate aur bid floor dekho; kal ke isi unit se compare karo.'),
                $m, $b['pkg']);
            continue;
        }
        // 3) eCPM gira (impressions theek)
        if ($c['i'] >= 100 && $b['i'] > 0 && $b['r'] > 0) {
            $e = $c['r'] / $c['i'] * 1000; $eb = $b['r'] / $b['i'] * 1000;
            if ($e < 0.45 * $eb && $eb >= 0.2) {
                al_add($found, "adunit_ecpm|$k", 2, 'adunit_ecpm',
                    L("$app: $unitLbl — eCPM $" . number_format($e, 2) . ' vs $' . number_format($eb, 2), "$app: $unitLbl — eCPM $" . number_format($e, 2) . ' vs $' . number_format($eb, 2)),
                    L('Impressions are normal (' . $c['i'] . ') but revenue per 1000 fell ' . round((1 - $e / $eb) * 100) . '%. Loss this hour ~' . al_inr($lossHr) . '. Cause: cheaper countries in traffic, a high-paying network stopped bidding, or ad quality/viewability issue on this placement. Fix: in the mediation dashboard compare this unit by network and country; if one network vanished, check its account.',
                      'Impressions theek hain (' . $c['i'] . ') par har 1000 par kamai ' . round((1 - $e / $eb) * 100) . '% giri. Is ghante ka nuksan ~' . al_inr($lossHr) . '. Wajah: traffic me saste countries, koi high-paying network bid nahi kar raha, ya is placement par ad quality/viewability issue. Fix: mediation dashboard me is unit ko network aur country-wise compare karo; koi network gayab hai to uska account dekho.'),
                    $m, $b['pkg']);
                continue;
            }
        }
        // 4) CTR gira (sirf tab jab app clicks bhejti hai)
        $cb = $clkBase[$fk] ?? 0.0; $cc = $clkCur[$fk] ?? 0;
        if ($cb >= 5 && $b['i'] > 0 && $c['i'] >= 200) {
            $ctrB = $cb / $b['i']; $ctr = $cc / $c['i'];
            if ($ctr < 0.3 * $ctrB) {
                al_add($found, "adunit_ctr|$fk", 2, 'adunit_ctr',
                    L("$app: " . $b['fmt'] . ' (' . $b['med'] . ') — CTR ' . al_pct($ctr, 2) . ' vs ' . al_pct($ctrB, 2), "$app: " . $b['fmt'] . ' (' . $b['med'] . ') — CTR ' . al_pct($ctr, 2) . ' vs ' . al_pct($ctrB, 2)),
                    L("$cc clicks on " . $c['i'] . ' impressions in 60 min; normally ' . al_pct($ctrB, 2) . '. Impact: networks pay less for low-CTR placements, so eCPM will follow. Cause: ad not visible (layout/overlay bug), wrong placement size, or a bad creative batch. Fix: open the app on a phone and check this placement renders fully; compare CTR by network.',
                      "60 min me " . $c['i'] . " impressions par $cc clicks; normally " . al_pct($ctrB, 2) . '. Asar: kam CTR wale placement par networks kam pay karte hain, eCPM bhi girega. Wajah: ad dikh nahi raha (layout/overlay bug), galat size, ya bura creative batch. Fix: phone par app kholke dekho yeh placement poora dikh raha hai; network-wise CTR compare karo.'),
                    array_merge($m, ['clicks_60m' => $cc, 'ctr_60m' => round($ctr, 4), 'ctr_baseline' => round($ctrB, 4)]), $b['pkg']);
            }
        }
    }
    // network-level: poora network app me gir gaya (kai units ek saath)
    foreach ($perNet as $nk => $n) {
        if ($n['bi'] < 500 || $n['net'] === '' || $n['ci'] >= 0.3 * $n['bi']) continue;
        if (($appCur[$n['pkg']] ?? 0) === 0) continue;
        $app = al_name($ctx, $n['pkg']);
        $loss = max(0.0, $n['br'] - $n['cr']) * $rate;
        al_add($found, "net_drop|$nk", $n['ci'] === 0 ? 3 : 2, 'net_drop',
            L("$app: network " . $n['net'] . ($n['ci'] === 0 ? ' stopped serving' : ' −' . round((1 - $n['ci'] / $n['bi']) * 100) . '%'), "$app: network " . $n['net'] . ($n['ci'] === 0 ? ' ne serve karna band kiya' : ' −' . round((1 - $n['ci'] / $n['bi']) * 100) . '%')),
            L($n['ci'] . ' impressions in 60 min from ' . $n['net'] . ' vs ~' . round($n['bi']) . ' normally (all ad units). Loss this hour ~' . al_inr($loss) . '. Cause: network account/payment issue, policy limit, or the adapter broke after an SDK update. Fix: open the ' . $n['net'] . ' account for this app and check status; in mediation see if other networks took over the fill.',
              $n['net'] . ' se 60 min me ' . $n['ci'] . ' impressions vs normally ~' . round($n['bi']) . ' (sab ad units). Is ghante ka nuksan ~' . al_inr($loss) . '. Wajah: network account/payment issue, policy limit, ya SDK update ke baad adapter toota. Fix: is app ka ' . $n['net'] . ' account kholke status dekho; mediation me dekho baaki networks ne fill uthaya ya nahi.'),
            ['app' => $app, 'network' => $n['net'], 'imps_60m' => $n['ci'], 'imps_baseline_hour' => round($n['bi']), 'loss_this_hour_inr' => round($loss)], $n['pkg']);
    }
}

/** 9) Revenue gira / upar (aaj poore ghanton tak vs 7 din isi time tak), eCPM gira */
function al_rule_revenue(PDO $pdo, array $ctx, array &$found): void
{
    if ($ctx['h'] < 6) return;   // subah jaldi comparison bekaar
    $hh = sprintf('%02d:00:00', $ctx['h']);
    $cur = []; $base = []; $ci = []; $bi = [];
    foreach (al_q($pdo, 'SELECT pkg, SUM(rev) r, SUM(imps) i FROM agg_imp_h WHERE lh >= :f AND lh < :t GROUP BY pkg', [':f' => $ctx['today'] . ' 00:00:00', ':t' => $ctx['today'] . ' ' . $hh]) as $r) { $cur[$r['pkg']] = (float)$r['r']; $ci[$r['pkg']] = (int)$r['i']; }
    foreach (al_q($pdo, "SELECT pkg, SUM(rev)/7 r, SUM(imps)/7 i FROM agg_imp_h WHERE lh >= :f AND lh < :t AND TIME(lh) < :hh GROUP BY pkg", [':f' => $ctx['back'](7) . ' 00:00:00', ':t' => $ctx['today'] . ' 00:00:00', ':hh' => $hh]) as $r) { $base[$r['pkg']] = (float)$r['r']; $bi[$r['pkg']] = (float)$r['i']; }
    $tc = 0.0; $tb = 0.0;
    foreach ($base as $pkg => $b) {
        if (!isset($ctx['apps'][$pkg])) continue;
        $c = $cur[$pkg] ?? 0.0; $tc += $c; $tb += $b;
        $lbl = al_name($ctx, $pkg);
        $m = ['till_hour' => $ctx['h'], 'rev_today_inr' => round($c * $ctx['rate']), 'rev_avg7_inr' => round($b * $ctx['rate']), 'rev_today_usd' => round($c, 2), 'rev_avg7_usd' => round($b, 2)];
        if ($b >= 3) {
            if ($c < 0.15 * $b && $b >= 10) al_add($found, "rev_drop|$pkg", 3, 'rev_drop', L("$lbl: revenue " . round((1 - $c / $b) * 100) . '% down', "$lbl: revenue " . round((1 - $c / $b) * 100) . '% neeche'),
            L(al_inr($c * $ctx['rate']) . ' by ' . $ctx['h'] . ':00 today, normally ' . al_inr($b * $ctx['rate']) . ' (gap ' . al_inr(($b - $c) * $ctx['rate']) . '). Cause: ads stopped / eCPM crash / users gone. Fix: open the Ads tab: if impressions are normal it is eCPM (network/country mix); if impressions are down it is fill or the app.', 'Aaj ' . $ctx['h'] . ' baje tak ' . al_inr($c * $ctx['rate']) . ', normally ' . al_inr($b * $ctx['rate']) . ' (farq ' . al_inr(($b - $c) * $ctx['rate']) . '). Wajah: ads band / eCPM crash / users gayab. Fix: Ads tab kholo: impressions normal hain to eCPM (network/country mix) ka issue, impressions gire hain to fill ya app ka.'), $m, $pkg);
            elseif ($c < 0.4 * $b) al_add($found, "rev_drop|$pkg", 2, 'rev_drop', L("$lbl: revenue " . round((1 - $c / $b) * 100) . '% down', "$lbl: revenue " . round((1 - $c / $b) * 100) . '% neeche'),
            L(al_inr($c * $ctx['rate']) . ' by ' . $ctx['h'] . ':00 today vs normally ' . al_inr($b * $ctx['rate']) . ' (gap ' . al_inr(($b - $c) * $ctx['rate']) . '). Could be a weekend/holiday; if it repeats tomorrow it is a real problem. Fix: compare eCPM and impressions in the Ads tab.', 'Aaj ' . $ctx['h'] . ' baje tak ' . al_inr($c * $ctx['rate']) . ' vs normally ' . al_inr($b * $ctx['rate']) . ' (farq ' . al_inr(($b - $c) * $ctx['rate']) . '). Weekend/holiday ho sakta hai; agar kal bhi aisa raha to problem hai. Fix: Ads tab me eCPM aur impressions compare karo.'), $m, $pkg);
        }
        if ($b >= 5 && $c >= 1.6 * $b) al_add($found, "rev_up|$pkg|" . $ctx['today'], 1, 'rev_up', L("$lbl: revenue " . round(($c / $b - 1) * 100) . '% up 🎉', "$lbl: revenue " . round(($c / $b - 1) * 100) . '% upar 🎉'),
            L(al_inr($c * $ctx['rate']) . ' by ' . $ctx['h'] . ':00 today vs normally ' . al_inr($b * $ctx['rate']) . '. Benefit: +' . al_inr(($c - $b) * $ctx['rate']) . ' so far. Note what you changed (campaign/update) in the change log so it can be repeated.', 'Aaj ' . $ctx['h'] . ' baje tak ' . al_inr($c * $ctx['rate']) . ' vs normally ' . al_inr($b * $ctx['rate']) . '. Fayda: ab tak +' . al_inr(($c - $b) * $ctx['rate']) . '. Jo kaam kiya (campaign/update), change log me note kar lo taaki dobara ho sake.'), $m, $pkg);
        // eCPM
        $i = $ci[$pkg] ?? 0; $ib = $bi[$pkg] ?? 0.0;
        if ($i >= 2000 && $ib > 0 && $b > 0) {
            $e = $c / $i * 1000; $eb = $b / $ib * 1000;
            if ($e < 0.5 * $eb) al_add($found, "ecpm_drop|$pkg", 2, 'ecpm_drop', L("$lbl: eCPM halved ($" . number_format($e, 2) . ' vs $' . number_format($eb, 2) . ')', "$lbl: eCPM aadha ($" . number_format($e, 2) . ' vs $' . number_format($eb, 2) . ')'),
            L('Impressions are fine but earnings per 1000 are half. Impact so far: ~' . al_inr(($eb - $e) / 1000 * $i * $ctx['rate']) . '. Cause: country mix changed (cheaper traffic) or a mediation network dropped out. Fix: check network-wise eCPM in the Ads tab and the campaign countries.', 'Impressions theek hain par har 1000 par kamai aadhi. Asar ab tak: ~' . al_inr(($eb - $e) / 1000 * $i * $ctx['rate']) . '. Wajah: traffic ka country mix badla (sasta traffic) ya mediation network gir gaya. Fix: Ads tab me network-wise eCPM aur campaign ke countries dekho.'), ['ecpm_today' => round($e, 3), 'ecpm_7d' => round($eb, 3), 'imps_today' => $i], $pkg);
        }
    }
    if ($tb >= 20 && $tc < 0.4 * $tb) al_add($found, 'rev_drop|ALL', $tc < 0.15 * $tb ? 3 : 2, 'rev_drop', L('Total revenue ' . round((1 - $tc / $tb) * 100) . '% down', 'Poora revenue ' . round((1 - $tc / $tb) * 100) . '% neeche'),
        L('All apps together: ' . al_inr($tc * $ctx['rate']) . ' by ' . $ctx['h'] . ':00 today, normally ' . al_inr($tb * $ctx['rate']) . ' (gap ' . al_inr(($tb - $tc) * $ctx['rate']) . '). When every app drops at once the cause is usually the ad network or the server, not one app.', 'Sab apps milakar aaj ' . $ctx['h'] . ' baje tak ' . al_inr($tc * $ctx['rate']) . ', normally ' . al_inr($tb * $ctx['rate']) . ' (farq ' . al_inr(($tb - $tc) * $ctx['rate']) . '). Sab apps ek saath girein to wajah aksar ad network ya server hoti hai, ek app nahi.'), ['rev_today_inr' => round($tc * $ctx['rate']), 'rev_avg7_inr' => round($tb * $ctx['rate'])]);
}

/** 10) Installs gire / double */
function al_rule_installs(PDO $pdo, array $ctx, array &$found): void
{
    if ($ctx['h'] < 8) return;
    $hh = $ctx['today'] . sprintf(' %02d:00:00', $ctx['h']);
    $cur = []; $base = [];
    foreach (al_q($pdo, 'SELECT pkg, COUNT(*) n FROM day_install WHERE day = :d AND lh < :hh GROUP BY pkg', [':d' => $ctx['today'], ':hh' => $hh]) as $r) $cur[$r['pkg']] = (int)$r['n'];
    foreach (al_q($pdo, 'SELECT pkg, COUNT(*)/7 n FROM day_install WHERE day >= :f AND day < :t AND TIME(lh) < :tt GROUP BY pkg', [':f' => $ctx['back'](7), ':t' => $ctx['today'], ':tt' => sprintf('%02d:00:00', $ctx['h'])]) as $r) $base[$r['pkg']] = (float)$r['n'];
    foreach ($base as $pkg => $b) {
        if ($b < 20 || !isset($ctx['apps'][$pkg])) continue;
        $c = $cur[$pkg] ?? 0; $lbl = al_name($ctx, $pkg);
        $m = ['till_hour' => $ctx['h'], 'installs_today' => $c, 'installs_avg7' => round($b)];
        if ($c < 0.35 * $b) al_add($found, "inst_drop|$pkg", 2, 'inst_drop', L("$lbl: installs " . round((1 - $c / $b) * 100) . '% down', "$lbl: installs " . round((1 - $c / $b) * 100) . '% kam'),
            L("$c installs by {$ctx['h']}:00 today, normally ~" . round($b) . '. Cause: campaign stopped, Play ranking fell, or a listing problem. Fix: check campaign status first, then Play Console > Store performance.', "Aaj {$ctx['h']} baje tak $c installs, normally ~" . round($b) . '. Wajah: campaign ruki, Play ranking giri, ya listing me dikkat. Fix: pehle campaign status, phir Play Console > Store performance dekho.'), $m, $pkg);
        elseif ($c >= 2 * $b && $c >= 30) al_add($found, "inst_up|$pkg|" . $ctx['today'], 1, 'inst_up', L("$lbl: installs " . round(($c / $b - 1) * 100) . '% up 🚀', "$lbl: installs " . round(($c / $b - 1) * 100) . '% upar 🚀'),
            L("$c installs by {$ctx['h']}:00 today vs normally ~" . round($b) . '. If these are paid, watch CPI/ROAS; if organic, note what changed (ranking, feature, update).', "Aaj {$ctx['h']} baje tak $c installs vs normally ~" . round($b) . '. Paid hain to CPI/ROAS par nazar rakho; organic hain to note karo kya badla (ranking, feature, update).'), $m, $pkg);
    }
}

/** 11) Uninstall rate */
function al_rule_uninstall(PDO $pdo, array $ctx, array &$found): void
{
    $un = []; $in = [];
    foreach (al_q($pdo, 'SELECT pkg, COUNT(*) n FROM devices WHERE uninstalled_at >= :f AND uninstalled_at < :t GROUP BY pkg', [':f' => $ctx['today_utc_from'], ':t' => $ctx['today_utc_to']]) as $r) $un[$r['pkg']] = (int)$r['n'];
    foreach (al_q($pdo, 'SELECT pkg, COUNT(*) n FROM day_install WHERE day = :d GROUP BY pkg', [':d' => $ctx['today']]) as $r) $in[$r['pkg']] = (int)$r['n'];
    foreach ($un as $pkg => $u) {
        $i = $in[$pkg] ?? 0;
        if ($u < 30 || $i <= 0 || $u < 0.6 * $i) continue;
        al_add($found, "uninst_high|$pkg", 2, 'uninst_high', L(al_name($ctx, $pkg) . ": $u uninstalls vs $i installs today", al_name($ctx, $pkg) . ": aaj $u uninstalls vs $i installs"),
        L('Uninstall rate ' . al_pct($u / $i) . '. Cause: bad new version, too many ads, or a campaign bringing the wrong audience. Impact: paid installs that uninstall never earn back their CPI. Fix: compare uninstalls by version and by campaign source.', 'Uninstall rate ' . al_pct($u / $i) . '. Wajah: naya version bura, zyada ads, ya campaign galat audience la rahi hai. Asar: jo paid install uninstall ho gaya uska CPI kabhi wapas nahi aata. Fix: version-wise aur campaign source-wise uninstalls compare karo.'), ['uninstalls_today' => $u, 'installs_today' => $i], $pkg);
    }
}

/** 12) Payments fail ho rahe (24 ghante) */
function al_rule_payments(PDO $pdo, array $ctx, array &$found): void
{
    foreach ($pdo->query("SELECT pkg, SUM(ev='purchase_done') d, SUM(ev='purchase_failed') f FROM events WHERE ev IN ('purchase_done','purchase_failed') AND ts >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 24 HOUR) GROUP BY pkg") as $r) {
        $d = (int)$r['d']; $f = (int)$r['f'];
        if ($f < 5 || $f < 0.5 * ($d + $f)) continue;
        al_add($found, 'pay_fail|' . $r['pkg'], 2, 'pay_fail', L(al_name($ctx, $r['pkg']) . ": $f payments failed, $d succeeded (24h)", al_name($ctx, $r['pkg']) . ": $f payments fail, $d success (24h)"),
        L('Fail rate ' . al_pct($f / ($d + $f)) . '. Impact: direct lost income from users who wanted to pay. Fix: check Play Billing product ids, merchant account status and the app payment flow on a test device.', 'Fail rate ' . al_pct($f / ($d + $f)) . '. Asar: jo users pay karna chahte the unki seedhi kamai gayi. Fix: Play Billing product id, merchant account status aur test phone par app ka payment flow check karo.'), ['failed_24h' => $f, 'done_24h' => $d], $r['pkg']);
    }
}

/** 13) D1 retention giri (kal ke cohort ka D1 = parson install, kal wapas) */
function al_rule_retention(PDO $pdo, array $ctx, array &$found): void
{
    $sql = 'SELECT i.pkg, COUNT(*) n, COUNT(u.did) r FROM day_install i LEFT JOIN day_user u ON u.pkg=i.pkg AND u.did=i.did AND u.day = DATE_ADD(i.day, INTERVAL 1 DAY) WHERE i.day >= :f AND i.day <= :t GROUP BY i.pkg';
    $cur = []; $base = [];
    foreach (al_q($pdo, $sql, [':f' => $ctx['back'](2), ':t' => $ctx['back'](2)]) as $r) $cur[$r['pkg']] = [(int)$r['n'], (int)$r['r']];
    foreach (al_q($pdo, $sql, [':f' => $ctx['back'](9), ':t' => $ctx['back'](3)]) as $r) $base[$r['pkg']] = [(int)$r['n'], (int)$r['r']];
    foreach ($cur as $pkg => [$n, $r]) {
        if ($n < 50 || !isset($base[$pkg]) || $base[$pkg][0] < 100) continue;
        $d1 = $r / $n; $b = $base[$pkg][1] / $base[$pkg][0];
        if ($d1 >= 0.15 || $b < 0.25) continue;
        al_add($found, "ret_drop|$pkg", 2, 'ret_drop', L(al_name($ctx, $pkg) . ': D1 retention ' . al_pct($d1) . ' (was ' . al_pct($b) . ')', al_name($ctx, $pkg) . ': D1 retention ' . al_pct($d1) . ' (pehle ' . al_pct($b) . ')'),
        L("Only $r of $n installs from two days ago came back yesterday. Cause: wrong campaign audience or a bad first-open experience (benchmark D1 35%). Impact: low retention means low ROAS for those installs. Fix: check which campaign those installs came from and the first screen of the app.", "Parson ke $n installs me se sirf $r kal wapas aaye. Wajah: campaign ki audience galat ya app ka pehla experience kharab (benchmark D1 35%). Asar: kam retention = un installs ka ROAS kam. Fix: dekho woh installs kis campaign se aaye aur app ki pehli screen kaisi hai."), ['cohort_day' => $ctx['back'](2), 'installs' => $n, 'd1' => round($d1, 3), 'd1_7d' => round($b, 3)], $pkg);
    }
}

/** 14) Roz subah ka brief (kal ka hisaab) + AI brief */
function al_rule_brief(PDO $pdo, array $ctx, array &$found): void
{
    if ($ctx['h'] < AL_BRIEF_HOUR) return;
    $key = 'brief|' . $ctx['today'];
    if (al_q1($pdo, 'SELECT id FROM app_alerts WHERE akey = ?', [$key]) !== null) return;   // aaj ban chuka (resolved ho ya na ho)
    $snap = al_snapshot($pdo, $ctx);
    $y = $snap['yesterday_total'];
    $roasTxt = $y['gads_cost_inr'] > 0 ? ' · ROAS ' . al_pct($y['roas']) : '';
    $title = L('Yesterday: profit ' . al_inr($y['profit_inr']) . $roasTxt, 'Kal ka hisaab: profit ' . al_inr($y['profit_inr']) . $roasTxt);
    $en = ['Revenue ' . al_inr($y['rev_inr']) . ' + payments ' . al_inr($y['purchase_inr']) . ' − ads ' . al_inr($y['gads_cost_inr']) . ' = ' . al_inr($y['profit_inr']) . ' (7-day avg profit ' . al_inr($snap['avg7_total']['profit_inr']) . ')',
        'Installs ' . $y['installs'] . ' (avg ' . round($snap['avg7_total']['installs']) . ') · DAU ' . $y['dau'] . ' · Crashes ' . $y['crashes']];
    $hi = ['Revenue ' . al_inr($y['rev_inr']) . ' + payments ' . al_inr($y['purchase_inr']) . ' − ads ' . al_inr($y['gads_cost_inr']) . ' = ' . al_inr($y['profit_inr']) . ' (7 din avg profit ' . al_inr($snap['avg7_total']['profit_inr']) . ')',
        'Installs ' . $y['installs'] . ' (avg ' . round($snap['avg7_total']['installs']) . ') · DAU ' . $y['dau'] . ' · Crashes ' . $y['crashes']];
    if ($snap['best']) { $en[] = 'Best: ' . $snap['best']['name'] . ' ' . al_inr($snap['best']['profit_inr']); $hi[] = 'Best: ' . $snap['best']['name'] . ' ' . al_inr($snap['best']['profit_inr']); }
    if ($snap['worst'] && $snap['worst']['profit_inr'] < 0) { $en[] = 'Loss: ' . $snap['worst']['name'] . ' ' . al_inr($snap['worst']['profit_inr']); $hi[] = 'Loss: ' . $snap['worst']['name'] . ' ' . al_inr($snap['worst']['profit_inr']); }
    if ($snap['open_urgent'] > 0) { $en[] = $snap['open_urgent'] . ' URGENT alerts are open right now.'; $hi[] = 'Abhi ' . $snap['open_urgent'] . ' URGENT alert khule hain.'; }
    $found[$key] = ['level' => 1, 'rule' => 'brief', 'title' => $title, 'body' => L(implode("\n", $en), implode("\n", $hi)), 'metrics' => $snap, 'pkg' => '', 'account' => '', 'campaign' => ''];
}

/** 15) Records (kal all-time high) */
function al_rule_records(PDO $pdo, array $ctx, array &$found): void
{
    if ($ctx['h'] < AL_BRIEF_HOUR) return;
    $y = $ctx['yest'];
    $days = (int)al_q1($pdo, 'SELECT COUNT(DISTINCT day) FROM day_app WHERE day < :y', [':y' => $y]);
    if ($days < 30) return;
    foreach ([['rev_usd', 'revenue', 'rev'], ['installs', 'installs', 'inst']] as [$col, $lbl, $k]) {
        $yv = (float)al_q1($pdo, "SELECT SUM($col) FROM day_app WHERE day = :y", [':y' => $y]);
        $mx = (float)al_q1($pdo, "SELECT MAX(s) FROM (SELECT SUM($col) s FROM day_app WHERE day < :y GROUP BY day) t", [':y' => $y]);
        if ($yv > 0 && $yv > $mx) {
            $v = $col === 'rev_usd' ? al_inr($yv * $ctx['rate']) : (string)(int)$yv;
            al_add($found, "record_$k|$y", 1, 'record', L("New record: $lbl $v yesterday 🎉", "Naya record: kal $lbl $v 🎉"),
            L("Highest single-day $lbl ever (previous: " . ($col === 'rev_usd' ? al_inr($mx * $ctx['rate']) : (string)(int)$mx) . ').', "Ab tak ka sabse zyada ek din ka $lbl (pehle: " . ($col === 'rev_usd' ? al_inr($mx * $ctx['rate']) : (string)(int)$mx) . ').'), ['day' => $y, 'value' => $yv, 'previous_max' => $mx]);
        }
    }
}

/** Kal + 7 din ka snapshot (brief / AI ke liye) */
function al_snapshot(PDO $pdo, array $ctx): array
{
    $CX = "cost*IF(currency='USD',{$ctx['rateSql']},1)";
    $y = $ctx['yest']; $f7 = $ctx['back'](8); $t7 = $ctx['back'](2);
    $per = [];
    foreach ($ctx['apps'] as $pkg => $a) $per[$pkg] = ['pkg' => $pkg, 'name' => al_name($ctx, $pkg), 'installs' => 0, 'dau' => 0, 'imps' => 0, 'rev_inr' => 0.0, 'purchase_inr' => 0.0, 'crashes' => 0, 'uninstalls' => 0, 'gads_cost_inr' => 0.0, 'gads_installs' => 0.0,
        'avg7' => ['installs' => 0.0, 'rev_inr' => 0.0, 'gads_cost_inr' => 0.0, 'profit_inr' => 0.0]];
    foreach (al_q($pdo, 'SELECT * FROM day_app WHERE day = :y', [':y' => $y]) as $r) {
        if (!isset($per[$r['pkg']])) continue;
        $p = &$per[$r['pkg']];
        $p['installs'] = (int)$r['installs']; $p['dau'] = (int)$r['dau']; $p['imps'] = (int)$r['imps']; $p['rev_inr'] = (float)$r['rev_usd'] * $ctx['rate'];
        $p['purchase_inr'] = (float)$r['purchase_inr']; $p['crashes'] = (int)$r['crashes']; $p['uninstalls'] = (int)$r['uninstalls'];
        unset($p);
    }
    foreach (al_q($pdo, "SELECT pkg, SUM($CX) c, SUM(conversions) v FROM gads_campaign_day WHERE day = :y GROUP BY pkg", [':y' => $y]) as $r) if (isset($per[$r['pkg']])) { $per[$r['pkg']]['gads_cost_inr'] = (float)$r['c']; $per[$r['pkg']]['gads_installs'] = (float)$r['v']; }
    $g7 = [];
    foreach (al_q($pdo, "SELECT pkg, SUM($CX)/7 c FROM gads_campaign_day WHERE day >= :f AND day <= :t GROUP BY pkg", [':f' => $f7, ':t' => $t7]) as $r) $g7[$r['pkg']] = (float)$r['c'];
    foreach (al_q($pdo, 'SELECT pkg, SUM(installs)/7 i, SUM(rev_usd)/7 r, SUM(purchase_inr)/7 p FROM day_app WHERE day >= :f AND day <= :t GROUP BY pkg', [':f' => $f7, ':t' => $t7]) as $r) {
        if (!isset($per[$r['pkg']])) continue;
        $rev = (float)$r['r'] * $ctx['rate'] + (float)$r['p']; $gc = $g7[$r['pkg']] ?? 0.0;
        $per[$r['pkg']]['avg7'] = ['installs' => round((float)$r['i'], 1), 'rev_inr' => round($rev), 'gads_cost_inr' => round($gc), 'profit_inr' => round($rev - $gc)];
    }
    $tot = ['installs' => 0, 'dau' => 0, 'rev_inr' => 0.0, 'purchase_inr' => 0.0, 'gads_cost_inr' => 0.0, 'crashes' => 0, 'uninstalls' => 0];
    $avg = ['installs' => 0.0, 'profit_inr' => 0.0];
    foreach ($per as &$p) {
        $p['profit_inr'] = round($p['rev_inr'] + $p['purchase_inr'] - $p['gads_cost_inr']);
        $p['roas'] = $p['gads_cost_inr'] > 0 ? round(($p['rev_inr'] + $p['purchase_inr']) / $p['gads_cost_inr'], 2) : null;
        $p['rev_inr'] = round($p['rev_inr']); $p['gads_cost_inr'] = round($p['gads_cost_inr']);
        foreach (['installs', 'dau', 'rev_inr', 'purchase_inr', 'gads_cost_inr', 'crashes', 'uninstalls'] as $k) $tot[$k] += $p[$k];
        $avg['installs'] += $p['avg7']['installs']; $avg['profit_inr'] += $p['avg7']['profit_inr'];
    }
    unset($p);
    $tot['profit_inr'] = round($tot['rev_inr'] + $tot['purchase_inr'] - $tot['gads_cost_inr']);
    $tot['roas'] = $tot['gads_cost_inr'] > 0 ? ($tot['rev_inr'] + $tot['purchase_inr']) / $tot['gads_cost_inr'] : null;
    $active = array_values(array_filter($per, fn($p) => $p['dau'] > 0 || $p['gads_cost_inr'] > 0 || $p['installs'] > 0));
    usort($active, fn($a, $b) => $b['profit_inr'] <=> $a['profit_inr']);
    $camps = al_q($pdo, "SELECT MAX(campaign) campaign, pkg, SUM($CX) cost, SUM(impressions) impressions, SUM(clicks) clicks, SUM(conversions) installs FROM gads_campaign_day WHERE day = :y GROUP BY customer_id, campaign_id HAVING cost > 0 ORDER BY cost DESC LIMIT 12", [':y' => $y]);
    foreach ($camps as &$c) { $c['app'] = $c['pkg'] !== '' ? al_name($ctx, $c['pkg']) : ''; $c['cost'] = round((float)$c['cost']); $c['cpi'] = (float)$c['installs'] > 0 ? round($c['cost'] / (float)$c['installs']) : null; unset($c['pkg']); }
    unset($c);
    $openAl = al_q($pdo, 'SELECT level, title FROM app_alerts WHERE resolved_at IS NULL AND level >= 2 ORDER BY level DESC, id DESC LIMIT 10');
    return ['day' => $y, 'yesterday_total' => $tot, 'avg7_total' => $avg, 'apps' => $active, 'best' => $active[0] ?? null, 'worst' => $active ? $active[count($active) - 1] : null,
        'campaigns_yesterday' => $camps, 'open_alerts' => $openAl, 'open_urgent' => count(array_filter($openAl, fn($a) => (int)$a['level'] === 3))];
}

// ================================================================ Claude AI (raw HTTP: shared hosting par composer nahi hota)
function al_ai_key(): string
{
    static $k = null;
    if ($k === null) $k = trim((string)(kv_get('ai_api_key') ?? (cfg('anthropic_api_key') ?? '')));
    return $k;
}
function al_ai_model(): string
{
    $m = trim((string)(kv_get('ai_model') ?? ''));
    return $m !== '' ? $m : AL_AI_DEFAULT_MODEL;
}
function al_ai_status(): array
{
    $k = al_ai_key();
    return ['enabled' => $k !== '', 'key_hint' => $k !== '' ? substr($k, 0, 10) . '…' . substr($k, -4) : '', 'model' => al_ai_model(),
        'last_error' => kv_get('ai_last_error'), 'last_ok' => (int)(kv_get('ai_last_ok') ?? 0)];
}
function al_brief_latest(): ?array
{
    $j = json_decode((string)(kv_get('alerts_brief') ?? ''), true);
    return is_array($j) ? $j : null;
}

/** POST /v1/messages. Fail ho to null (rules par asar nahi). */
function al_ai_call(string $system, string $user, int $maxTokens, string $effort): ?string
{
    $key = al_ai_key();
    if ($key === '') return null;
    $model = al_ai_model();
    $body = ['model' => $model, 'max_tokens' => $maxTokens, 'system' => $system, 'messages' => [['role' => 'user', 'content' => $user]], 'output_config' => ['effort' => $effort]];
    $headers = ['Content-Type: application/json', 'x-api-key: ' . $key, 'anthropic-version: 2023-06-01'];
    // Opus 5.5 / Fable / Sonnet 5.5: safety decline par server khud doosre model par chala de
    if (preg_match('/^claude-(opus-5|fable|sonnet-5-5)/', $model)) { $body['fallbacks'] = 'default'; $headers[] = 'anthropic-beta: server-side-fallback-2026-07-01'; }
    [$code, $resp, $err] = http_json('POST', 'https://api.anthropic.com/v1/messages', json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), $headers, 90);
    if ($code !== 200) {
        $j = json_decode($resp, true);
        kv_set('ai_last_error', gmdate('Y-m-d H:i') . " HTTP $code: " . mb_substr((string)($j['error']['message'] ?? ($err ?: $resp)), 0, 300));
        return null;
    }
    $j = json_decode($resp, true);
    if (!is_array($j)) return null;
    if (($j['stop_reason'] ?? '') === 'refusal') { kv_set('ai_last_error', gmdate('Y-m-d H:i') . ' refusal'); return null; }
    $text = '';
    foreach ((array)($j['content'] ?? []) as $b) if (($b['type'] ?? '') === 'text') $text .= (string)$b['text'];
    $text = trim($text);
    if ($text === '') return null;
    kv_set('ai_last_ok', (string)time());
    if (kv_get('ai_last_error') !== null) db()->exec("DELETE FROM kv WHERE k = 'ai_last_error'");
    return $text;
}

const AL_AI_RULES = 'Rules of thumb: ROAS = (ad revenue + payments) / ad spend, break-even 100%, scale only above 150%; cohort targets D0 30% / D1 50% / D3 75% / D7 110%; D1 retention benchmark 35%, D7 12%; '
    . 'CPI 50%+ above the 7-day average = problem; crash/session 1%+ = bad; a new install keeps earning for ~7 days; ad fill = loaded/requests, no-fill means the network had no ad for that user/country; '
    . 'low CTR on a placement lowers its eCPM over time.';
const AL_AI_SYSTEM_HI = 'Tum "MFC Apps" (India ki ek chhoti Android apps company: free apps, kamai AppLovin MAX/AdMob ads + in-app payments se, user acquisition Google Ads se) ke dashboard ke senior analyst ho. '
    . 'Malik (Sagar) ko Hinglish me (Roman Hindi + English mix, jaise WhatsApp par likhte hain) seedha, chhota, practical jawab do. '
    . 'Paisa INR me. Koi markdown heading/bold nahi, koi disclaimer nahi. Agar data kam hai to guess ko guess bolo. ' . AL_AI_RULES;
const AL_AI_SYSTEM_EN = 'You are the senior analyst of the dashboard of "MFC Apps" (a small Indian Android apps company: free apps, revenue from AppLovin MAX/AdMob ads + in-app payments, user acquisition via Google Ads). '
    . 'Answer the owner (Sagar) in plain, short, practical English. Money in INR. No markdown headings/bold, no disclaimers. If data is thin, say a guess is a guess. ' . AL_AI_RULES;
/** App ki chuni hui bhasha (GET ?lang= se kv me): en default */
function al_ai_lang(): string { return (kv_get('ai_lang') ?? 'en') === 'hi' ? 'hi' : 'en'; }
function al_ai_system(): string { return al_ai_lang() === 'hi' ? AL_AI_SYSTEM_HI : AL_AI_SYSTEM_EN; }

/** Pending alerts ko Claude se samjhao (response bhejne ke baad / cron me). Return: kitne update hue. */
function al_ai_process(PDO $pdo, array $pending): int
{
    if (al_ai_key() === '' || !$pending) return 0;
    $ctx = al_context($pdo);
    $n = 0;
    foreach ($pending as $p) {
        $row = $pdo->query('SELECT * FROM app_alerts WHERE id = ' . (int)$p['id'])->fetch();
        if (!$row || $row['ai'] !== '') continue;
        $row['metrics'] = json_decode((string)$row['metrics'], true) ?: [];
        $row['level'] = (int)$row['level'];
        $txt = ($p['kind'] ?? '') === 'brief' ? al_ai_brief($row['metrics']) : al_ai_explain($row, $ctx);
        if ($txt === null) continue;
        $pdo->prepare('UPDATE app_alerts SET ai = ? WHERE id = ?')->execute([mb_substr($txt, 0, 3000), (int)$row['id']]);
        if (($p['kind'] ?? '') === 'brief') kv_set('alerts_brief', json_encode(['day' => $ctx['today'], 'text' => $txt, 'at' => time(), 'id' => (int)$row['id']], JSON_UNESCAPED_UNICODE));
        $n++;
    }
    return $n;
}

function al_ai_explain(array $a, array $ctx): ?string
{
    $payload = ['alert' => ['level' => $a['level'] === 3 ? 'URGENT' : 'WATCH', 'rule' => $a['rule'], 'title' => $a['title'], 'detail' => $a['body'],
        'app' => $a['pkg'] !== '' ? al_name($ctx, $a['pkg']) : null, 'account' => $a['account'] ?: null, 'campaign' => $a['campaign'] ?: null],
        'metrics' => $a['metrics'], 'local_time' => (new DateTime('now', tz()))->format('Y-m-d H:i (D)')];
    if (al_ai_lang() === 'hi') {
        $user = "Yeh alert abhi dashboard rule engine ne banaya hai. Exactly 4 chhoti lines do, bina numbering ke:\n"
            . "Line 1: kya hua, kahan (app / account / ad network / ad unit) aur kitna bada issue hai.\nLine 2: nuksan ya fayda paise me (₹/ghanta ya ₹/din ka andaza) aur agar ignore kiya to kya hoga.\n"
            . "Line 3: sabse likely wajah (1-2 options).\nLine 4: abhi 1-2 concrete kaam jo Sagar kare (kahan click kare).\n\n";
    } else {
        $user = "This alert was just raised by the dashboard rule engine. Give exactly 4 short lines, no numbering:\n"
            . "Line 1: what happened, where (app / account / ad network / ad unit) and how big it is.\nLine 2: the loss or gain in money (estimate ₹/hour or ₹/day) and what happens if ignored.\n"
            . "Line 3: the most likely cause (1-2 options).\nLine 4: 1-2 concrete actions Sagar should take now (where to click).\n\n";
    }
    $user .= json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
    return al_ai_call(al_ai_system(), $user, 1024, 'low');
}

function al_ai_brief(array $snap): ?string
{
    if (al_ai_lang() === 'hi') {
        $user = "Kal ka poora data (sab apps, campaigns, khule alerts). Subah ka brief likho: pehli line me kal ka overall verdict (profit/loss, kyun), phir 4-6 chhote bullets (har bullet '- ' se shuru): "
            . "sabse important observations (kaunsi app/campaign paisa bana rahi, kaunsi kha rahi, kya badla 7 din avg se), aur aaj ke 2-3 concrete action items. Max 140 words.\n\n";
    } else {
        $user = "Yesterday's full data (all apps, campaigns, open alerts). Write the morning brief: first line = overall verdict for yesterday (profit/loss and why), then 4-6 short bullets (each starting with '- '): "
            . "the most important observations (which app/campaign makes money, which eats it, what changed vs the 7-day average) and 2-3 concrete action items for today. Max 140 words.\n\n";
    }
    $user .= json_encode($snap, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    return al_ai_call(al_ai_system(), $user, 2048, 'medium');
}
