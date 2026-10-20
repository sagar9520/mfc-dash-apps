<?php
/**
 * MFC Dash app — "Google Ads AI Health Monitor" module ki API (sirf app ke liye; web page nahi). SIRF MONITORING, Google Ads me kuch nahi badalta.
 * Upload: mfc_dash/api/gads_health.php + api/gads_ingest.php + lib/gads_health.php + lib/gads_rules.php (+ optional mfc_dash/gads_config.php)
 * Zaroori: lib/health.php (AI App Health module) bhi upload ho — email aur Claude helpers wahi se aate hain.
 *
 * Cron (har 5 min):  php mfc_dash/api/gads_health.php cron   ya URL  api/gads_health.php?cron=1&key=CRON_KEY
 *
 * App (login cookie):
 *   GET ?view=overview | filters | account&cid= | alerts&group=status|all | alert&id= | monitor&kind=spend|cpi|roas | campaigns | campaign&cid=&campaign_id=
 *            | creatives | recs | live | run&id= | settings | script&cid=
 *       filters (sab views): company, account, country, currency, campaign, adgroup, status, range (today|yesterday|7d|14d|30d)
 *   POST ?action=account_save | account_toggle {cid, field, value} | token {cid} | threshold {scope, key, value} | setting {key, value}
 *        | scan {cid?} | ai {id} | ack {id} | close {id} | test_notify | simulate {cid, rule} | sim_clear | daily_now | mail_check | deep_now
 *   GET ?open=alert/12 -> chhota page jo app kholta hai (email ke link)
 */
declare(strict_types=1);
require_once __DIR__ . '/../lib/gads_health.php';

$isCli = PHP_SAPI === 'cli';
$pdo = db();
ga_schema($pdo);

// ---------------------------------------------------------------- cron
if (($isCli && in_array('cron', $argv ?? [], true)) || isset($_GET['cron'])) {
    require_cron();
    @set_time_limit(300);
    $log = function (string $m) { echo gmdate('H:i:s') . "  $m\n"; };
    ga_cron($pdo, $log);
    $log('done');
    exit;
}

// ---------------------------------------------------------------- deep link page (koi data nahi)
if (isset($_GET['open'])) {
    $route = preg_replace('#[^a-z0-9/_-]#i', '', (string)$_GET['open']);
    $intent = 'intent://gads/' . $route . '#Intent;scheme=mfcdash;package=com.myfuturecode.mfcdash;end';
    header('Content-Type: text/html; charset=utf-8');
    echo '<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>MFC Dash</title>'
        . '<body style="font:16px system-ui;background:#f3f2f8;color:#15132b;display:grid;place-items:center;height:90vh;margin:0">'
        . '<div style="text-align:center;padding:24px"><p>Open this in the MFC Dash app</p>'
        . '<a href="' . htmlspecialchars($intent) . '" style="display:inline-block;padding:14px 22px;border-radius:12px;background:linear-gradient(100deg,#9c02ba,#4a1fc4);color:#fff;text-decoration:none;font-weight:600">Open MFC Dash</a>'
        . '<p style="color:#6b6a80;font-size:13px">' . htmlspecialchars($route) . '</p></div><script>setTimeout(function(){location.href=' . json_encode($intent) . '},200)</script>';
    exit;
}

// ---------------------------------------------------------------- app (login)
require_login_json();
$me = admin_user();
$action = (string)($_GET['action'] ?? '');

if (($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
    $in = json_decode((string)file_get_contents('php://input'), true);
    if (!is_array($in)) $in = $_POST;
    $id = (int)($in['id'] ?? 0);
    $after = null;
    try {
        switch ($action) {
            case 'account_save': $resp = ga_account_save($pdo, $in, $me); break;
            case 'account_toggle':
                $cid = ga_cid((string)($in['cid'] ?? ''));
                $field = (string)($in['field'] ?? '');
                if (!in_array($field, ['monitoring', 'push', 'email', 'src_dashboard', 'src_script', 'src_email'], true)) throw new RuntimeException('bad field');
                $v = ($in['value'] ?? '') === '1' ? 1 : 0;
                if (h_x($pdo, "UPDATE ga_accounts SET `$field` = ?, updated_at = UTC_TIMESTAMP() WHERE customer_id = ?", [$v, $cid]) === 0 && !h_q1($pdo, 'SELECT 1 FROM ga_accounts WHERE customer_id = ?', [$cid])) throw new RuntimeException('unknown account');
                ga_log($pdo, "CID " . ga_fmt_cid($cid) . ": $field " . ($v ? 'ON' : 'OFF') . " by $me", 'info', ['cid' => $cid]);
                $tok = null;
                if ($field === 'src_script' && $v === 1 && (string)h_q1($pdo, 'SELECT token_hash FROM ga_accounts WHERE customer_id = ?', [$cid]) === '') $tok = ga_token_new($pdo, $cid);
                $resp = ['ok' => true, 'token' => $tok];
                break;
            case 'token':
                $cid = ga_cid((string)($in['cid'] ?? ''));
                if (!h_q1($pdo, 'SELECT 1 FROM ga_accounts WHERE customer_id = ?', [$cid])) throw new RuntimeException('unknown account');
                $resp = ['ok' => true, 'token' => ga_token_new($pdo, $cid), 'note' => 'Paste the new token into this account\'s script; the old one stops working now.'];
                break;
            case 'threshold':
                $scope = (string)($in['scope'] ?? 'global');
                if (!preg_match('/^(global|acct:\d{10}|camp:\d{10}:\d{1,30}|country:\d{10}:[A-Z]{2})$/', $scope)) throw new RuntimeException('bad scope');
                $k = (string)($in['key'] ?? '');
                if (!in_array($k, GR_KEYS, true)) throw new RuntimeException('bad key');
                $v = trim((string)($in['value'] ?? ''));
                if ($v === '') { h_x($pdo, 'DELETE FROM ga_thresholds WHERE scope = ? AND k = ?', [$scope, $k]); }
                else {
                    if (!is_numeric($v) || (float)$v < 0) throw new RuntimeException('value must be a positive number');
                    h_x($pdo, 'REPLACE INTO ga_thresholds (scope, k, v, updated_at) VALUES (?,?,?,UTC_TIMESTAMP())', [$scope, $k, (string)(float)$v]);
                }
                ga_log($pdo, "threshold $scope.$k = " . ($v === '' ? '(default)' : $v) . " by $me");
                $resp = ['ok' => true];
                break;
            case 'setting':
                $k = (string)($in['key'] ?? '');
                if (!array_key_exists($k, GA_SETTINGS)) throw new RuntimeException('bad key');
                $v = trim((string)($in['value'] ?? ''));
                if ($k === 'ga_daily_hour' && (!ctype_digit($v) || (int)$v > 23)) throw new RuntimeException('hour 0-23');
                if ($k === 'ga_ai_cap' && (!ctype_digit($v) || (int)$v < 1 || (int)$v > 500)) throw new RuntimeException('1-500');
                if (in_array($k, ['ga_push', 'ga_mail_critical', 'ga_mail_daily', 'ga_dev_mode', 'ga_ai_on'], true)) $v = $v === '1' ? '1' : '0';
                kv_set($k, $v);
                ga_log($pdo, "setting $k = $v by $me");
                $resp = ['ok' => true];
                break;
            case 'scan':
                $cid = ga_cid((string)($in['cid'] ?? ''));
                $resp = ['ok' => true, 'queued' => true];
                $after = fn() => ga_scan($pdo, 'manual', $me, $cid !== '' ? $cid : null);
                break;
            case 'ai':
                if (!ga_ai_on()) throw new RuntimeException('AI key not set: add it in the app Settings > AI (smart alerts) first');
                $resp = ['ok' => true, 'queued' => true];
                $after = fn() => ga_ai_investigate($pdo, $id);
                break;
            case 'ack':
                h_x($pdo, 'UPDATE ga_alerts SET ack_at = UTC_TIMESTAMP(), ack_by = ? WHERE id = ? AND ack_at IS NULL', [h_cut($me, 60), $id]);
                ga_log($pdo, "alert #$id acknowledged by $me (no more notifications; closes automatically when the condition clears)", 'info', ['alert' => $id]);
                $resp = ['ok' => true];
                break;
            case 'close':
                // sirf email / simulated alerts haath se band (metric alerts khud band hote hain jab condition theek ho)
                $r = $pdo->query('SELECT akey, sim FROM ga_alerts WHERE id = ' . $id)->fetch();
                if (!$r) throw new RuntimeException('unknown alert');
                if (!str_starts_with((string)$r['akey'], 'mail|') && !(int)$r['sim'] && !str_starts_with((string)$r['akey'], 'sim|')) throw new RuntimeException('This alert closes automatically when the data shows the problem is gone. Use Acknowledge to stop notifications.');
                h_x($pdo, 'UPDATE ga_alerts SET resolved_at = UTC_TIMESTAMP(), resolved_by = ? WHERE id = ?', [h_cut($me, 60), $id]);
                ga_log($pdo, "alert #$id closed by $me", 'info', ['alert' => $id]);
                $resp = ['ok' => true];
                break;
            case 'test_notify':
                h_x($pdo, "INSERT INTO app_alerts (akey, level, rule, pkg, account, campaign, title, body, title_hi, body_hi, ai, metrics, fired, created_at, updated_at)
                    VALUES (?, 2, 'gah_test', '', '', '', ?, ?, ?, ?, '', ?, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP())",
                    ['gah|test|' . time(), '[TEST] Google Ads monitor notification', 'This is a test. If you see it on your phone, alerts from the Google Ads monitor will reach you.',
                        '[TEST] Google Ads monitor notification', 'Ye test hai. Phone par dikh raha hai to Google Ads monitor ke alerts aap tak pahunchenge.', h_json(['test' => 1])]);
                $mail = null;
                if (h_email_on()) $mail = h_mail_send((string)hcfg('email_to'), '[TEST] Google Ads monitor email', "Test email from the Google Ads AI Health Monitor.\nRead-only monitoring: nothing is changed in Google Ads.");
                ga_log($pdo, "test notification by $me · app alert inserted" . ($mail === null ? ' · email not configured' : ($mail ? ' · email sent' : ' · email FAILED: ' . (string)kv_get('h_mail_err'))));
                $resp = ['ok' => true, 'email' => $mail];
                break;
            case 'simulate': $resp = ga_simulate($pdo, ga_cid((string)($in['cid'] ?? '')), (string)($in['rule'] ?? ''), $me); break;
            case 'sim_clear':
                $n = h_x($pdo, 'DELETE FROM ga_alerts WHERE sim = 1');
                h_x($pdo, 'DELETE FROM ga_emails WHERE sim = 1');
                h_x($pdo, "DELETE FROM app_alerts WHERE rule LIKE 'gah%' AND metrics LIKE '%\"sim\":1%'");
                ga_log($pdo, "simulated alerts cleared by $me ($n)");
                $resp = ['ok' => true, 'deleted' => $n];
                break;
            case 'daily_now': $resp = ['ok' => true, 'queued' => true]; $after = fn() => ga_daily_review($pdo, 'manual', $me); break;
            case 'mail_check':
                if (!ga_imap_on()) throw new RuntimeException('Email intake is not configured (gads_config.php > imap)');
                $resp = ['ok' => true, 'queued' => true]; $after = fn() => ga_mail_job($pdo, 'manual', $me); break;
            case 'deep_now': $resp = ['ok' => true, 'queued' => true]; $after = fn() => ga_deep($pdo, 'manual', $me); break;
            default: json_out(['ok' => false, 'error' => 'unknown action'], 400);
        }
    } catch (Throwable $e) {
        json_out(['ok' => false, 'error' => $e->getMessage()], 400);
    }
    gv_respond($resp, $after);
}

// ---------------------------------------------------------------- GET views
$view = (string)($_GET['view'] ?? 'overview');
$f = gv_filters($_GET);
try {
    switch ($view) {
        case 'overview': $out = gv_overview($pdo, $f); break;
        case 'filters': $out = gv_filter_options($pdo); break;
        case 'account': $out = gv_account($pdo, ga_cid((string)($_GET['cid'] ?? ''))); break;
        case 'alerts': $out = gv_alerts($pdo, $f, (string)($_GET['group'] ?? 'all')); break;
        case 'alert': $out = gv_alert($pdo, (int)($_GET['id'] ?? 0)); break;
        case 'monitor': $out = gv_monitor($pdo, $f, (string)($_GET['kind'] ?? 'spend')); break;
        case 'campaigns': $out = gv_campaigns($pdo, $f); break;
        case 'campaign': $out = gv_campaign($pdo, ga_cid((string)($_GET['cid'] ?? '')), preg_replace('/\D/', '', (string)($_GET['campaign_id'] ?? '')), $f); break;
        case 'creatives': $out = gv_creatives($pdo, $f); break;
        case 'recs': $out = gv_recs($pdo, $f); break;
        case 'live': $out = gv_live($pdo, $f); break;
        case 'run': $out = gv_run($pdo, (int)($_GET['id'] ?? 0)); break;
        case 'settings': $out = gv_settings($pdo); break;
        case 'script': $out = gv_script($pdo, ga_cid((string)($_GET['cid'] ?? ''))); break;
        default: json_out(['ok' => false, 'error' => 'unknown view'], 400);
    }
} catch (Throwable $e) {
    json_out(['ok' => false, 'error' => $e->getMessage()], 500);
}
gv_respond(['ok' => true, 'server_time' => gmdate('Y-m-d H:i:s'), 'tz' => (string)cfg('timezone'), 'read_only' => true] + $out, null);

// ================================================================ helpers
function gv_respond(array $out, ?callable $after): void
{
    $s = h_json($out);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    if ($after === null) { echo $s; exit; }
    ignore_user_abort(true);
    @set_time_limit(300);
    header('Content-Length: ' . strlen($s));
    header('Connection: close');
    echo $s;
    if (function_exists('fastcgi_finish_request')) fastcgi_finish_request(); else { @ob_end_flush(); flush(); }
    try { $after(); } catch (Throwable $e) { error_log('mfc gads after: ' . $e->getMessage()); }
    exit;
}

function gv_filters(array $g): array
{
    $r = (string)($g['range'] ?? '7d');
    return ['company' => trim((string)($g['company'] ?? '')), 'cid' => ga_cid((string)($g['account'] ?? '')), 'country' => strtoupper(substr(preg_replace('/[^A-Za-z]/', '', (string)($g['country'] ?? '')), 0, 2)),
        'currency' => strtoupper(substr(preg_replace('/[^A-Za-z]/', '', (string)($g['currency'] ?? '')), 0, 3)), 'campaign' => preg_replace('/\D/', '', (string)($g['campaign'] ?? '')),
        'adgroup' => preg_replace('/\D/', '', (string)($g['adgroup'] ?? '')), 'status' => preg_replace('/[^a-z_]/', '', strtolower((string)($g['status'] ?? ''))),
        'range' => in_array($r, ['today', 'yesterday', '7d', '14d', '30d'], true) ? $r : '7d'];
}

/** Registry ke accounts jo company / account / currency filter se match karein. */
function gv_accts(PDO $pdo, array $f, bool $monitoredOnly = false): array
{
    $out = [];
    foreach (ga_accounts($pdo, $monitoredOnly) as $cid => $a) {
        $cur = $a['currency'] ?: (string)(h_q1($pdo, 'SELECT currency FROM gads_account WHERE customer_id = ?', [$cid]) ?? '');
        $a['currency'] = $cur;
        if ($f['company'] !== '' && $a['company'] !== $f['company']) continue;
        if ($f['cid'] !== '' && (string)$cid !== $f['cid']) continue;
        if ($f['currency'] !== '' && $cur !== $f['currency']) continue;
        $out[(string)$cid] = $a;
    }
    return $out;
}

/** [from, to] local din (account TZ) range ke liye. */
function gv_range(string $today, string $range): array
{
    $d = new DateTimeImmutable($today);
    return match ($range) {
        'today' => [$today, $today],
        'yesterday' => [$d->modify('-1 day')->format('Y-m-d'), $d->modify('-1 day')->format('Y-m-d')],
        '14d' => [$d->modify('-14 day')->format('Y-m-d'), $d->modify('-1 day')->format('Y-m-d')],
        '30d' => [$d->modify('-30 day')->format('Y-m-d'), $d->modify('-1 day')->format('Y-m-d')],
        default => [$d->modify('-7 day')->format('Y-m-d'), $d->modify('-1 day')->format('Y-m-d')],
    };
}

function gv_utc_since(string $range): string
{
    $days = ['today' => 1, 'yesterday' => 2, '7d' => 7, '14d' => 14, '30d' => 30][$range] ?? 7;
    return gmdate('Y-m-d H:i:s', time() - $days * 86400);
}

function gv_acct_out(array $a): array
{
    return ['cid' => (string)$a['customer_id'], 'cid_fmt' => ga_fmt_cid((string)$a['customer_id']), 'company' => $a['company'], 'account_name' => $a['account_name'], 'mcc_label' => $a['mcc_label'],
        'invoice_ref' => $a['invoice_ref'], 'credit_ref' => $a['credit_ref'], 'currency' => $a['currency'], 'tz' => $a['tz'], 'notes' => $a['notes'],
        'src_dashboard' => (int)$a['src_dashboard'] === 1, 'src_script' => (int)$a['src_script'] === 1, 'src_email' => (int)$a['src_email'] === 1,
        'monitoring' => (int)$a['monitoring'] === 1, 'push' => (int)$a['push'] === 1, 'email' => (int)$a['email'] === 1, 'token_hint' => $a['token_hint'],
        'authorized_by' => $a['authorized_by'], 'authorized_at' => $a['authorized_at'], 'health' => $a['health'], 'last_state' => $a['last_state'], 'last_scan_at' => $a['last_scan_at'],
        'script_last_at' => $a['script_last_at'], 'script_version' => $a['script_version'], 'script_status' => $a['script_status'], 'script_status_at' => $a['script_status_at']];
}

function gv_alert_out(array $r): array
{
    $ai = $r['ai'] ? json_decode((string)$r['ai'], true) : null;
    return ['id' => (int)$r['id'], 'cid' => (string)$r['customer_id'], 'cid_fmt' => ga_fmt_cid((string)$r['customer_id']), 'company' => (string)($r['company'] ?? ''),
        'currency' => (string)($r['currency'] ?? ''), 'campaign_id' => $r['campaign_id'], 'campaign' => $r['campaign'], 'ad_group_id' => $r['ad_group_id'], 'country' => $r['country'],
        'rule' => $r['rule'], 'level' => $r['level'], 'klass' => $r['klass'], 'title' => $r['title'], 'title_hi' => $r['title_hi'], 'what' => (string)$r['what'], 'what_hi' => (string)$r['what_hi'],
        'metrics' => json_decode((string)$r['metrics'], true) ?: new stdClass(), 'causes' => json_decode((string)$r['causes'], true) ?: [], 'next' => $r['next_step'], 'next_hi' => $r['next_hi'],
        'impact' => $r['impact'], 'evidence' => $r['evidence'], 'freshness_min' => $r['freshness_min'] === null ? null : (int)$r['freshness_min'],
        'first_seen' => $r['first_seen'], 'last_seen' => $r['last_seen'], 'resolved_at' => $r['resolved_at'], 'resolved_by' => $r['resolved_by'], 'ack_at' => $r['ack_at'],
        'notified_at' => $r['notified_at'], 'ai' => $ai, 'ai_conf' => $r['ai_conf'] === null ? null : (int)$r['ai_conf'], 'ai_at' => $r['ai_at'], 'ai_err' => $r['ai_err'],
        'sim' => (int)$r['sim'] === 1, 'closable' => str_starts_with((string)$r['akey'], 'mail|') || (int)$r['sim'] === 1];
}

/** Account level pace: sab campaigns ka aaj (R tak) vs usual. */
function gv_acct_pace(array $snap): array
{
    $t = $snap['today']; $R = $snap['R'];
    $todayCost = 0.0;
    foreach ($snap['daily'] as $c) $todayCost += (float)($c['days'][$t]['cost'] ?? 0);
    $pace = null; $usual = null;
    if ($R >= 0 && $snap['hourly_supported'] && count($snap['base_days']) >= 2) {
        $now = 0.0;
        foreach ($snap['hourly'][$t] ?? [] as $h) $now += gr_sum_hours((array)($h['c'] ?? []), 0, $R);
        $b = [];
        foreach ($snap['base_days'] as $d) { $s = 0.0; foreach ($snap['hourly'][$d] ?? [] as $h) $s += gr_sum_hours((array)($h['c'] ?? []), 0, $R); $b[] = $s; }
        $usual = gr_median($b);
        $pace = gr_pct($now, $usual);
    }
    return ['spend_today' => round($todayCost, 2), 'pace_pct' => $pace === null ? null : round($pace, 1), 'usual_by_now' => $usual === null ? null : round($usual, 2),
        'reported_through' => $R >= 0 ? sprintf('%02d:59', $R) : null];
}

// ---------------------------------------------------------------- A. All Accounts (overview)
function gv_overview(PDO $pdo, array $f): array
{
    $acc = [];
    $byCur = [];
    foreach (gv_accts($pdo, $f) as $cid => $a) {
        $snap = ga_snapshot($pdo, $a);
        $p = gv_acct_pace($snap);
        $cnt = $pdo->prepare("SELECT SUM(level = 'CRITICAL') c, SUM(level = 'WARNING') w, SUM(level = 'INFO') i FROM ga_alerts WHERE customer_id = ? AND resolved_at IS NULL AND sim = 0");
        $cnt->execute([$cid]);
        $c = $cnt->fetch();
        if ($f['status'] !== '' && !gv_status_match($f['status'], (string)$a['health'], (int)$c['c'], (int)$c['w'])) continue;
        $acc[] = gv_acct_out($a) + ['data_age_min' => $snap['age'], 'fresh' => $snap['fresh'], 'sources' => $snap['sources'], 'tz_used' => $snap['tz'],
            'critical' => (int)$c['c'], 'warning' => (int)$c['w'], 'info' => (int)$c['i']] + $p;
        if ($a['currency'] !== '') { $byCur[$a['currency']]['spend_today'] = ($byCur[$a['currency']]['spend_today'] ?? 0) + $p['spend_today']; $byCur[$a['currency']]['accounts'] = ($byCur[$a['currency']]['accounts'] ?? 0) + 1; }
    }
    $cur = [];
    foreach ($byCur as $k => $v) $cur[] = ['currency' => $k, 'spend_today' => round($v['spend_today'], 2), 'accounts' => $v['accounts']];
    $reg = array_keys(ga_accounts($pdo));
    $disc = [];
    foreach (h_q($pdo, 'SELECT customer_id, name, currency, tz, updated_at FROM gads_account ORDER BY updated_at DESC') as $g) if (!in_array((string)$g['customer_id'], $reg, true)) $disc[] = $g + ['cid_fmt' => ga_fmt_cid((string)$g['customer_id'])];
    $last = $pdo->query("SELECT * FROM ga_runs WHERE type = 'scan' ORDER BY id DESC LIMIT 1")->fetch() ?: null;
    return ['accounts' => $acc, 'currency_totals' => $cur, 'discovered' => $disc, 'last_run' => $last ? gv_run_out($last) : null, 'next_scan_at' => ga_next_scan(),
        'counts' => ['accounts' => count($acc), 'critical' => count(array_filter($acc, fn($x) => $x['critical'] > 0)), 'warning' => count(array_filter($acc, fn($x) => $x['warning'] > 0 && $x['critical'] === 0)),
            'stale' => count(array_filter($acc, fn($x) => !$x['fresh']))],
        'setup' => gv_setup()];
}

function gv_status_match(string $s, string $health, int $crit, int $warn): bool
{
    return match ($s) {
        'critical' => $crit > 0, 'warning' => $warn > 0, 'problem' => $crit + $warn > 0, 'healthy' => $health === 'HEALTHY', 'stale' => in_array($health, ['STALE', 'NO_DATA'], true), default => true,
    };
}

function gv_setup(): array
{
    $cron = (int)(kv_get('ga_cron_last') ?? 0);
    return ['cron_ok' => $cron > time() - 900, 'cron_last' => $cron ? gmdate('Y-m-d H:i:s', $cron) : null, 'ai_on' => ga_ai_on(), 'email_out' => function_exists('h_email_on') && h_email_on(),
        'email_in' => ga_imap_on(), 'email_in_error' => (string)(kv_get('ga_mail_err') ?? ''), 'dev_mode' => ga_set('ga_dev_mode') === '1', 'push' => ga_set('ga_push') !== '0'];
}

// ---------------------------------------------------------------- filter options
function gv_filter_options(PDO $pdo): array
{
    $acc = []; $companies = []; $cur = [];
    foreach (ga_accounts($pdo) as $cid => $a) {
        $c = $a['currency'] ?: (string)(h_q1($pdo, 'SELECT currency FROM gads_account WHERE customer_id = ?', [$cid]) ?? '');
        $acc[] = ['cid' => (string)$cid, 'label' => $a['company'] . ' · ' . ga_fmt_cid((string)$cid), 'company' => $a['company'], 'currency' => $c];
        $companies[$a['company']] = 1; if ($c !== '') $cur[$c] = 1;
    }
    $reg = array_map('strval', array_keys(ga_accounts($pdo)));
    $camps = [];
    foreach (h_q($pdo, "SELECT customer_id, campaign_id, MAX(campaign) name FROM gads_campaign_day WHERE day >= DATE_SUB(UTC_DATE(), INTERVAL 30 DAY) GROUP BY customer_id, campaign_id") as $r)
        if (in_array((string)$r['customer_id'], $reg, true)) $camps[$r['customer_id'] . '|' . $r['campaign_id']] = ['cid' => (string)$r['customer_id'], 'id' => (string)$r['campaign_id'], 'name' => (string)$r['name']];
    foreach (h_q($pdo, 'SELECT customer_id, campaign_id, name FROM ga_camp') as $r)
        if (in_array((string)$r['customer_id'], $reg, true) && !isset($camps[$r['customer_id'] . '|' . $r['campaign_id']])) $camps[$r['customer_id'] . '|' . $r['campaign_id']] = ['cid' => (string)$r['customer_id'], 'id' => (string)$r['campaign_id'], 'name' => (string)$r['name']];
    $ags = [];
    foreach (h_q($pdo, 'SELECT customer_id, campaign_id, ad_group_id, MAX(name) name FROM ga_adgroup_day WHERE day >= DATE_SUB(UTC_DATE(), INTERVAL 30 DAY) GROUP BY customer_id, campaign_id, ad_group_id') as $r)
        if (in_array((string)$r['customer_id'], $reg, true)) $ags[] = ['cid' => (string)$r['customer_id'], 'campaign_id' => (string)$r['campaign_id'], 'id' => (string)$r['ad_group_id'], 'name' => (string)$r['name']];
    $cc = array_column(h_q($pdo, 'SELECT DISTINCT country FROM ga_geo_day WHERE day >= DATE_SUB(UTC_DATE(), INTERVAL 30 DAY) ORDER BY country'), 'country');
    ksort($companies); ksort($cur);
    return ['companies' => array_keys($companies), 'accounts' => $acc, 'currencies' => array_keys($cur), 'campaigns' => array_values($camps), 'adgroups' => $ags, 'countries' => $cc];
}

// ---------------------------------------------------------------- B. Account Health
function gv_account(PDO $pdo, string $cid): array
{
    $a = $pdo->query('SELECT * FROM ga_accounts WHERE customer_id = ' . $pdo->quote($cid))->fetch();
    if (!$a) throw new RuntimeException('unknown account');
    if ($a['currency'] === '') $a['currency'] = (string)(h_q1($pdo, 'SELECT currency FROM gads_account WHERE customer_id = ?', [$cid]) ?? '');
    $snap = ga_snapshot($pdo, $a);
    $camps = [];
    $open = [];
    foreach (h_q($pdo, "SELECT campaign_id, MAX(CASE level WHEN 'CRITICAL' THEN 3 WHEN 'WARNING' THEN 2 ELSE 1 END) lv FROM ga_alerts WHERE customer_id = ? AND resolved_at IS NULL AND sim = 0 AND campaign_id <> '' GROUP BY campaign_id", [$cid]) as $r) $open[$r['campaign_id']] = (int)$r['lv'];
    $y = ga_days_list($snap['today'], [1])[0];
    foreach (array_unique(array_merge(array_keys($snap['daily']), array_keys($snap['meta']))) as $camp) {
        $camp = (string)$camp;
        $d = $snap['daily'][$camp] ?? ['name' => '', 'days' => []];
        $m = $snap['meta'][$camp] ?? [];
        $st = ga_camp_status($snap, $camp);
        $recent = array_filter(array_keys($d['days']), fn($x) => $x >= ga_days_list($snap['today'], [7])[0]);
        if ($st === 'REMOVED' && !$recent) continue;
        $camps[] = ['campaign_id' => $camp, 'name' => ($m['name'] ?? '') ?: $d['name'], 'status' => $st, 'prev_status' => $m['prev_status'] ?? '', 'status_changed_at' => $m['status_changed_at'] ?? null,
            'primary_status' => $m['primary_status'] ?? '', 'budget' => isset($m['budget']) ? (float)$m['budget'] : null, 'learning_reasons' => $m['primary_reasons'] ?? '',
            'spend_today' => round((float)($d['days'][$snap['today']]['cost'] ?? 0), 2), 'spend_yesterday' => round((float)($d['days'][$y]['cost'] ?? 0), 2),
            'installs_yesterday' => round((float)($d['days'][$y]['conversions'] ?? 0), 1), 'alert_level' => ['', 'INFO', 'WARNING', 'CRITICAL'][$open[$camp] ?? 0],
            'checks' => json_decode((string)($m['last_checks'] ?? ''), true) ?: new stdClass()];
    }
    usort($camps, fn($x, $z) => [$z['status'] === 'ENABLED', $z['spend_yesterday']] <=> [$x['status'] === 'ENABLED', $x['spend_yesterday']]);
    $alerts = array_map('gv_alert_out', h_q($pdo, "SELECT * FROM ga_alerts WHERE customer_id = ? AND resolved_at IS NULL ORDER BY FIELD(level,'CRITICAL','WARNING','INFO'), last_seen DESC LIMIT 100", [$cid]));
    $emails = h_q($pdo, 'SELECT id, received_at, from_addr, subject, kind, verified, verify_note, reason, sim FROM ga_emails WHERE customer_id = ? ORDER BY received_at DESC LIMIT 10', [$cid]);
    $th = [];
    foreach (ga_th_all($pdo) as $scope => $vals) if ($scope === "acct:$cid" || str_starts_with($scope, "camp:$cid:") || str_starts_with($scope, "country:$cid:")) foreach ($vals as $k => $v) $th[] = ['scope' => $scope, 'key' => $k, 'value' => $v];
    return ['account' => gv_acct_out($a), 'data_age_min' => $snap['age'], 'fresh' => $snap['fresh'], 'sources' => $snap['sources'], 'tz_used' => $snap['tz'],
        'checks' => json_decode((string)$a['last_checks'], true) ?: new stdClass(), 'campaigns' => $camps, 'alerts' => $alerts, 'emails' => $emails, 'thresholds' => $th,
        'script_errors' => json_decode((string)$a['script_errors'], true) ?: [], 'policy_at' => $a['policy_at']] + gv_acct_pace($snap);
}

// ---------------------------------------------------------------- C / K / I. alerts (Suspensions & Policy, Alert History)
function gv_alerts(PDO $pdo, array $f, string $group): array
{
    $accts = gv_accts($pdo, $f);
    if (!$accts) return ['alerts' => [], 'total' => 0];
    $w = ['a.customer_id IN (' . implode(',', array_map(fn($c) => $pdo->quote((string)$c), array_keys($accts))) . ')'];
    $p = [];
    if ($group === 'status') $w[] = "a.rule IN ('" . implode("','", GA_STATUS_RULES) . "')";
    if ($f['campaign'] !== '') { $w[] = 'a.campaign_id = ?'; $p[] = $f['campaign']; }
    if ($f['adgroup'] !== '') { $w[] = 'a.ad_group_id = ?'; $p[] = $f['adgroup']; }
    if ($f['country'] !== '') { $w[] = 'a.country = ?'; $p[] = $f['country']; }
    switch ($f['status']) {
        case 'open': $w[] = 'a.resolved_at IS NULL'; break;
        case 'resolved': $w[] = 'a.resolved_at IS NOT NULL'; break;
        case 'critical': case 'warning': case 'info': $w[] = 'a.level = ?'; $p[] = strtoupper($f['status']); break;
        case 'confirmed': case 'suspected': case 'insufficient': $w[] = 'a.klass = ?'; $p[] = strtoupper($f['status']); break;
        case 'simulated': $w[] = 'a.sim = 1'; break;
    }
    $w[] = '(a.last_seen >= ? OR a.resolved_at IS NULL)'; $p[] = gv_utc_since($f['range']);
    $rows = h_q($pdo, 'SELECT a.*, g.company, g.currency FROM ga_alerts a LEFT JOIN ga_accounts g ON g.customer_id = a.customer_id WHERE ' . implode(' AND ', $w)
        . " ORDER BY (a.resolved_at IS NULL) DESC, FIELD(a.level,'CRITICAL','WARNING','INFO'), a.last_seen DESC LIMIT 300", $p);
    return ['alerts' => array_map('gv_alert_out', $rows), 'total' => count($rows)];
}

function gv_alert(PDO $pdo, int $id): array
{
    $r = $pdo->query('SELECT a.*, g.company, g.currency FROM ga_alerts a LEFT JOIN ga_accounts g ON g.customer_id = a.customer_id WHERE a.id = ' . $id)->fetch();
    if (!$r) throw new RuntimeException('alert not found');
    $a = $pdo->query('SELECT * FROM ga_accounts WHERE customer_id = ' . $pdo->quote((string)$r['customer_id']))->fetch();
    $events = h_q($pdo, 'SELECT id, run_id, level, msg, created_at FROM ga_events WHERE alert_id = ? ORDER BY id DESC LIMIT 30', [$id]);
    return ['alert' => gv_alert_out($r), 'account' => $a ? gv_acct_out($a) : null, 'events' => $events, 'ai_on' => ga_ai_on()];
}

// ---------------------------------------------------------------- D / E / F. monitors (spend, CPI/CPA, ROAS)
/** Ek campaign ki windows: aaj (R tak), kal isi time, same weekday median, usual, 3/7/14/30 poore din. */
function gv_windows(array $snap, string $camp): array
{
    $t = $snap['today']; $R = $snap['R'];
    $days = $snap['daily'][$camp]['days'] ?? [];
    $sum = fn(array $ds) => gr_sum_days($days, $ds);
    $w = [];
    $hr = function (string $d) use ($snap, $camp, $R) {
        $h = $snap['hourly'][$d][$camp] ?? [];
        return ['cost' => gr_sum_hours((array)($h['c'] ?? []), 0, $R), 'impressions' => gr_sum_hours((array)($h['i'] ?? []), 0, $R), 'conversions' => gr_sum_hours((array)($h['v'] ?? []), 0, $R)];
    };
    if ($R >= 0 && $snap['hourly_supported']) {
        $w['today'] = $hr($t) + ['label' => sprintf('today 00:00–%02d:59', $R)];
        $y = ga_days_list($t, [1])[0];
        if (isset($snap['hourly'][$y])) $w['yesterday_same'] = $hr($y) + ['label' => sprintf('yesterday 00:00–%02d:59', $R)];
        $wd = array_values(array_filter(ga_days_list($t, [7, 14, 21, 28]), fn($d) => isset($snap['hourly'][$d])));
        if ($wd) { $c = array_map(fn($d) => $hr($d), $wd); $w['weekday_same'] = ['cost' => gr_median(array_column($c, 'cost')), 'impressions' => gr_median(array_column($c, 'impressions')),
            'conversions' => gr_median(array_column($c, 'conversions')), 'label' => 'same weekday ×' . count($wd) . ' (median)']; }
        if ($snap['base_days']) { $c = array_map(fn($d) => $hr($d), $snap['base_days']); $w['usual'] = ['cost' => gr_median(array_column($c, 'cost')), 'impressions' => gr_median(array_column($c, 'impressions')),
            'conversions' => gr_median(array_column($c, 'conversions')), 'label' => 'usual by now (median)']; }
    } else {
        $w['today'] = ['cost' => (float)($days[$t]['cost'] ?? 0), 'impressions' => (float)($days[$t]['impressions'] ?? 0), 'conversions' => (float)($days[$t]['conversions'] ?? 0),
            'label' => 'today so far (no hourly data; reporting lag)', 'partial' => true];
    }
    foreach ([3, 7, 14, 30] as $n) { $s = $sum(gr_days_before($t, $n)); $s['label'] = "last $n complete days"; $w["d$n"] = $s; }
    foreach ($w as $k => &$x) {
        $x['cost'] = round((float)$x['cost'], 2);
        $x['cpi'] = ($x['conversions'] ?? 0) > 0 ? round($x['cost'] / $x['conversions'], 2) : null;
        $x['roas'] = isset($x['conv_value']) && $x['cost'] > 0 ? round($x['conv_value'] / $x['cost'], 3) : null;
        if (isset($x['clicks'])) { $x['ctr'] = ($x['impressions'] ?? 0) > 0 ? round($x['clicks'] / $x['impressions'], 4) : null; $x['cpc'] = $x['clicks'] > 0 ? round($x['cost'] / $x['clicks'], 3) : null; }
    }
    return $w;
}

function gv_geo_windows(PDO $pdo, string $cid, string $camp, string $cc, string $today): array
{
    $w = [];
    foreach ([3, 7, 14, 30] as $n) {
        $ds = gr_days_before($today, $n);
        $r = $pdo->prepare('SELECT SUM(cost) cost, SUM(impressions) impressions, SUM(clicks) clicks, SUM(conversions) conversions, SUM(conv_value) conv_value, COUNT(DISTINCT day) days
            FROM ga_geo_day WHERE customer_id = ? AND campaign_id = ? AND country = ? AND day BETWEEN ? AND ?');
        $r->execute([$cid, $camp, $cc, end($ds), $ds[0]]);
        $x = $r->fetch();
        $x = array_map(fn($v) => (float)$v, $x ?: []);
        $x['label'] = "last $n complete days · $cc";
        $x['cpi'] = ($x['conversions'] ?? 0) > 0 ? round($x['cost'] / $x['conversions'], 2) : null;
        $x['roas'] = ($x['cost'] ?? 0) > 0 ? round($x['conv_value'] / $x['cost'], 3) : null;
        $w["d$n"] = $x;
    }
    return $w;
}

function gv_monitor(PDO $pdo, array $f, string $kind): array
{
    $kind = in_array($kind, ['spend', 'cpi', 'roas'], true) ? $kind : 'spend';
    $rules = ['spend' => ['spend_drop', 'spend_spike', 'zero_spend', 'delivery_drop', 'minor_change', 'budget_limited'], 'cpi' => ['cpi_high', 'installs_drop', 'conv_tracking'], 'roas' => ['roas_low']][$kind];
    $rows = []; $totals = []; $notes = [];
    foreach (gv_accts($pdo, $f, true) as $cid => $a) {
        $snap = ga_snapshot($pdo, $a);
        $open = [];
        foreach (h_q($pdo, "SELECT id, campaign_id, level, klass, rule, title FROM ga_alerts WHERE customer_id = ? AND resolved_at IS NULL AND sim = 0 AND rule IN ('" . implode("','", $rules) . "')", [$cid]) as $o) $open[$o['campaign_id']][] = $o;
        foreach (array_unique(array_merge(array_keys($snap['daily']), array_keys($snap['meta']))) as $camp) {
            $camp = (string)$camp;
            if ($f['campaign'] !== '' && $camp !== $f['campaign']) continue;
            $m = $snap['meta'][$camp] ?? [];
            $st = ga_camp_status($snap, $camp);
            $name = ($m['name'] ?? '') ?: ($snap['daily'][$camp]['name'] ?? $camp);
            $hasRecent = array_filter(array_keys($snap['daily'][$camp]['days'] ?? []), fn($x) => $x >= ga_days_list($snap['today'], [30])[0]);
            if (!$hasRecent && $st !== 'ENABLED') continue;
            $lvl = $open[$camp] ?? [];
            if ($f['status'] === 'enabled' && $st !== 'ENABLED') continue;
            if ($f['status'] === 'paused' && !in_array($st, ['PAUSED', 'REMOVED'], true)) continue;
            if ($f['status'] === 'problem' && !$lvl) continue;
            if ($f['status'] === 'healthy' && $lvl) continue;
            $cth = ga_th($pdo, (string)$cid, $camp);
            $cpiT = gr_th($cth, 'cpi_target') ?? (isset($m['target_cpa']) && (float)$m['target_cpa'] > 0 ? (float)$m['target_cpa'] : null);
            $roasT = gr_th($cth, 'roas_target') ?? (isset($m['target_roas']) && (float)$m['target_roas'] > 0 ? (float)$m['target_roas'] : null);
            if ($f['country'] !== '') {
                $ccT = ga_th_all($pdo)["country:$cid:{$f['country']}"]['cpi_target'] ?? null;
                $win = gv_geo_windows($pdo, (string)$cid, $camp, $f['country'], $snap['today']);
                if (($win['d30']['cost'] ?? 0) <= 0) continue;
                if ($ccT !== null) $cpiT = (float)$ccT;
            } else {
                $win = gv_windows($snap, $camp);
            }
            $u = $win['usual']['cost'] ?? null;
            $rows[] = ['cid' => (string)$cid, 'cid_fmt' => ga_fmt_cid((string)$cid), 'company' => $a['company'], 'currency' => $snap['currency'], 'campaign_id' => $camp, 'name' => $name, 'status' => $st,
                'budget' => isset($m['budget']) ? (float)$m['budget'] : null, 'bidding' => $m['bidding'] ?? '', 'channel' => $m['channel'] ?? '', 'is_app' => ($m['channel'] ?? 'MULTI_CHANNEL') === 'MULTI_CHANNEL' || ($snap['daily'][$camp]['pkg'] ?? '') !== '',
                'cpi_target' => $cpiT, 'roas_target' => $roasT, 'min_installs' => gr_th($cth, 'min_installs'), 'thresholds' => ['drop_pct' => gr_th($cth, 'drop_pct'), 'spike_pct' => gr_th($cth, 'spike_pct'), 'cpi_over_pct' => gr_th($cth, 'cpi_over_pct'), 'roas_under_pct' => gr_th($cth, 'roas_under_pct')],
                'pace_pct' => ($u !== null && $u > 0 && isset($win['today'])) ? round(($win['today']['cost'] - $u) / $u * 100, 1) : null,
                'windows' => $win, 'alerts' => array_map(fn($o) => ['id' => (int)$o['id'], 'level' => $o['level'], 'klass' => $o['klass'], 'rule' => $o['rule'], 'title' => $o['title']], $lvl),
                'data_age_min' => $snap['age'], 'fresh' => $snap['fresh']];
            $cur = $snap['currency'] ?: '?';
            $totals[$cur]['d7_cost'] = ($totals[$cur]['d7_cost'] ?? 0) + ($win['d7']['cost'] ?? 0);
            $totals[$cur]['d7_conversions'] = ($totals[$cur]['d7_conversions'] ?? 0) + ($win['d7']['conversions'] ?? 0);
            $totals[$cur]['d7_value'] = ($totals[$cur]['d7_value'] ?? 0) + ($win['d7']['conv_value'] ?? 0);
        }
        if (!$snap['fresh']) $notes[] = ga_fmt_cid((string)$cid) . ': STALE DATA — numbers may be incomplete';
    }
    usort($rows, fn($x, $y) => [count($y['alerts']) > 0, $y['windows']['d7']['cost'] ?? 0] <=> [count($x['alerts']) > 0, $x['windows']['d7']['cost'] ?? 0]);
    $tot = [];
    foreach ($totals as $c => $t) $tot[] = ['currency' => $c, 'd7_cost' => round($t['d7_cost'], 2), 'd7_installs' => round($t['d7_conversions'], 1),
        'd7_cpi' => $t['d7_conversions'] > 0 ? round($t['d7_cost'] / $t['d7_conversions'], 2) : null, 'd7_roas' => $t['d7_cost'] > 0 ? round($t['d7_value'] / $t['d7_cost'], 3) : null];
    return ['kind' => $kind, 'rows' => $rows, 'currency_totals' => $tot, 'notes' => $notes,
        'note' => 'Totals are per currency only. Conversions (installs) arrive late, so today\'s CPI is not judged; CPI alerts use complete days.'];
}

// ---------------------------------------------------------------- G. Campaign & Ad Group analysis
function gv_campaigns(PDO $pdo, array $f): array
{
    $rows = [];
    foreach (gv_accts($pdo, $f, true) as $cid => $a) {
        $cid = (string)$cid;
        $tz = ga_acct_tz($pdo, $a);
        $today = (new DateTime('now', $tz))->format('Y-m-d');
        [$from, $to] = gv_range($today, $f['range']);
        $cur = $a['currency'];
        if ($f['adgroup'] !== '' || $f['country'] !== '') {
            $tbl = $f['adgroup'] !== '' ? 'ga_adgroup_day' : 'ga_geo_day';
            $w = 'customer_id = ? AND day BETWEEN ? AND ?'; $p = [$cid, $from, $to];
            if ($f['adgroup'] !== '') { $w .= ' AND ad_group_id = ?'; $p[] = $f['adgroup']; }
            if ($f['country'] !== '') { $w .= ' AND country = ?'; $p[] = $f['country']; }
            if ($f['campaign'] !== '') { $w .= ' AND campaign_id = ?'; $p[] = $f['campaign']; }
            $q = "SELECT campaign_id, SUM(cost) cost, SUM(impressions) impressions, SUM(clicks) clicks, SUM(conversions) conversions, SUM(conv_value) conv_value FROM $tbl WHERE $w GROUP BY campaign_id";
            $names = [];
            foreach (h_q($pdo, 'SELECT campaign_id, name FROM ga_camp WHERE customer_id = ?', [$cid]) as $n) $names[$n['campaign_id']] = $n['name'];
            foreach (h_q($pdo, $q, $p) as $r) $rows[] = gv_metric_row($r, ['cid' => $cid, 'cid_fmt' => ga_fmt_cid($cid), 'company' => $a['company'], 'currency' => $cur,
                'campaign_id' => (string)$r['campaign_id'], 'name' => $names[$r['campaign_id']] ?? (string)$r['campaign_id'], 'status' => '', 'scope' => $f['adgroup'] !== '' ? 'ad group ' . $f['adgroup'] : 'country ' . $f['country']]);
            continue;
        }
        $daily = ga_daily($pdo, $cid, $from, $to);
        $meta = [];
        foreach (h_q($pdo, 'SELECT campaign_id, name, status, budget, bidding, channel FROM ga_camp WHERE customer_id = ?', [$cid]) as $m) $meta[$m['campaign_id']] = $m;
        $agN = [];
        foreach (h_q($pdo, 'SELECT campaign_id, COUNT(DISTINCT ad_group_id) n FROM ga_adgroup_day WHERE customer_id = ? AND day BETWEEN ? AND ? GROUP BY campaign_id', [$cid, $from, $to]) as $x) $agN[$x['campaign_id']] = (int)$x['n'];
        foreach ($daily as $camp => $d) {
            $camp = (string)$camp;
            if ($f['campaign'] !== '' && $camp !== $f['campaign']) continue;
            $st = (string)($meta[$camp]['status'] ?? $d['status']);
            if ($f['status'] === 'enabled' && $st !== 'ENABLED') continue;
            if ($f['status'] === 'paused' && $st === 'ENABLED') continue;
            $s = gr_sum_days($d['days'], array_keys($d['days']));
            $rows[] = gv_metric_row($s, ['cid' => $cid, 'cid_fmt' => ga_fmt_cid($cid), 'company' => $a['company'], 'currency' => $cur, 'campaign_id' => $camp,
                'name' => (string)(($meta[$camp]['name'] ?? '') ?: $d['name']), 'status' => $st, 'budget' => isset($meta[$camp]['budget']) ? (float)$meta[$camp]['budget'] : null,
                'ad_groups' => $agN[$camp] ?? null, 'scope' => 'campaign']);
        }
    }
    usort($rows, fn($x, $y) => $y['cost'] <=> $x['cost']);
    return ['rows' => $rows, 'range' => $f['range'], 'note' => 'Each row is in its account currency; no cross-currency totals.'];
}

function gv_metric_row(array $s, array $extra): array
{
    $c = (float)($s['cost'] ?? 0); $i = (float)($s['impressions'] ?? 0); $k = (float)($s['clicks'] ?? 0); $v = (float)($s['conversions'] ?? 0); $val = (float)($s['conv_value'] ?? 0);
    return $extra + ['cost' => round($c, 2), 'impressions' => (int)$i, 'clicks' => (int)$k, 'installs' => round($v, 1), 'conv_value' => round($val, 2),
        'cpi' => $v > 0 ? round($c / $v, 2) : null, 'cpc' => $k > 0 ? round($c / $k, 3) : null, 'ctr' => $i > 0 ? round($k / $i, 4) : null, 'roas' => $c > 0 ? round($val / $c, 3) : null,
        'cvr' => $k > 0 ? round($v / $k, 4) : null];
}

function gv_campaign(PDO $pdo, string $cid, string $camp, array $f): array
{
    $a = $pdo->query('SELECT * FROM ga_accounts WHERE customer_id = ' . $pdo->quote($cid))->fetch();
    if (!$a) throw new RuntimeException('unknown account');
    if ($a['currency'] === '') $a['currency'] = (string)(h_q1($pdo, 'SELECT currency FROM gads_account WHERE customer_id = ?', [$cid]) ?? '');
    $snap = ga_snapshot($pdo, $a);
    $m = $snap['meta'][$camp] ?? null;
    $d = $snap['daily'][$camp] ?? ['name' => '', 'days' => [], 'status' => '', 'pkg' => ''];
    [$from, $to] = gv_range($snap['today'], $f['range']);
    $trend = [];
    foreach (gr_days_before($snap['today'], 30) as $day) { $r = $d['days'][$day] ?? null; $trend[] = gv_metric_row($r ?? [], ['day' => $day, 'has_data' => $r !== null]); }
    $trend = array_reverse($trend);
    $hourNow = []; $hourUsual = [];
    for ($h = 0; $h < 24; $h++) {
        $hourNow[] = isset($snap['hourly'][$snap['today']][$camp]['c'][$h]) ? round($snap['hourly'][$snap['today']][$camp]['c'][$h], 2) : ($h <= $snap['R'] ? 0.0 : null);
        $hourUsual[] = $snap['base_days'] ? round((float)gr_median(array_map(fn($bd) => (float)($snap['hourly'][$bd][$camp]['c'][$h] ?? 0), $snap['base_days'])), 2) : null;
    }
    $ags = array_map(fn($r) => gv_metric_row($r, ['ad_group_id' => (string)$r['ad_group_id'], 'name' => (string)$r['name'], 'status' => (string)$r['status']]),
        h_q($pdo, 'SELECT ad_group_id, MAX(name) name, MAX(status) status, SUM(cost) cost, SUM(impressions) impressions, SUM(clicks) clicks, SUM(conversions) conversions, SUM(conv_value) conv_value
            FROM ga_adgroup_day WHERE customer_id = ? AND campaign_id = ? AND day BETWEEN ? AND ?' . ($f['adgroup'] !== '' ? ' AND ad_group_id = ' . $pdo->quote($f['adgroup']) : '') . ' GROUP BY ad_group_id ORDER BY cost DESC', [$cid, $camp, $from, $to]));
    $geo = array_map(fn($r) => gv_metric_row($r, ['country' => (string)$r['country']]),
        h_q($pdo, 'SELECT country, SUM(cost) cost, SUM(impressions) impressions, SUM(clicks) clicks, SUM(conversions) conversions, SUM(conv_value) conv_value
            FROM ga_geo_day WHERE customer_id = ? AND campaign_id = ? AND day BETWEEN ? AND ?' . ($f['country'] !== '' ? ' AND country = ' . $pdo->quote($f['country']) : '') . ' GROUP BY country ORDER BY cost DESC LIMIT 40', [$cid, $camp, $from, $to]));
    $ccT = [];
    foreach (ga_th_all($pdo) as $scope => $vals) if (str_starts_with($scope, "country:$cid:") && isset($vals['cpi_target'])) $ccT[substr($scope, -2)] = (float)$vals['cpi_target'];
    $assetT = (int)h_q1($pdo, 'SELECT COUNT(*) FROM ga_asset WHERE customer_id = ?', [$cid]) > 0 ? 'ga_asset' : 'gads_asset';
    $assets = h_q($pdo, "SELECT asset_id, field_type, asset_type, label, name, url, impressions, clicks, updated_at FROM $assetT WHERE customer_id = ? AND campaign_id = ? ORDER BY impressions DESC LIMIT 60", [$cid, $camp]);
    $policy = h_q($pdo, 'SELECT ad_group_id, ad_id, approval, review, topics FROM ga_policy WHERE customer_id = ? AND campaign_id = ?', [$cid, $camp]);
    $alerts = array_map('gv_alert_out', h_q($pdo, 'SELECT a.*, ? company, ? currency FROM ga_alerts a WHERE customer_id = ? AND campaign_id = ? AND (resolved_at IS NULL OR last_seen >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 DAY)) ORDER BY resolved_at IS NULL DESC, id DESC LIMIT 50',
        [$a['company'], $a['currency'], $cid, $camp]));
    $cth = ga_th($pdo, $cid, $camp);
    return ['account' => gv_acct_out($a), 'campaign' => ['campaign_id' => $camp, 'name' => ($m['name'] ?? '') ?: $d['name'], 'status' => ga_camp_status($snap, $camp), 'pkg' => ($m['pkg'] ?? '') ?: $d['pkg'],
        'meta' => $m, 'checks' => json_decode((string)($m['last_checks'] ?? ''), true) ?: new stdClass()],
        'windows' => gv_windows($snap, $camp), 'trend' => $trend, 'hours_today' => $hourNow, 'hours_usual' => $hourUsual, 'reported_through' => $snap['R'] >= 0 ? sprintf('%02d:59', $snap['R']) : null,
        'ad_groups' => $ags, 'countries' => $geo, 'country_targets' => $ccT ?: new stdClass(), 'assets' => $assets, 'policy' => $policy, 'alerts' => $alerts, 'range' => [$from, $to],
        'thresholds' => array_intersect_key($cth, array_flip(GR_KEYS)), 'data_age_min' => $snap['age'], 'fresh' => $snap['fresh'],
        'sources_note' => ['ad_groups' => $ags ? '' : 'Ad group data needs the read-only script in this account', 'countries' => $geo ? '' : 'Country data needs the read-only script in this account']];
}

// ---------------------------------------------------------------- H. AI Creative Review
function gv_creatives(PDO $pdo, array $f): array
{
    $out = [];
    foreach (gv_accts($pdo, $f, true) as $cid => $a) {
        $cid = (string)$cid;
        $t = (int)h_q1($pdo, 'SELECT COUNT(*) FROM ga_asset WHERE customer_id = ?', [$cid]) > 0 ? 'ga_asset' : 'gads_asset';
        $w = 'customer_id = ?'; $p = [$cid];
        if ($f['campaign'] !== '') { $w .= ' AND campaign_id = ?'; $p[] = $f['campaign']; }
        if (in_array($f['status'], ['low', 'good', 'best', 'learning'], true)) { $w .= ' AND label = ?'; $p[] = strtoupper($f['status']); }
        $sugg = [];
        foreach (h_q($pdo, "SELECT id, campaign_id, ai, ai_at FROM ga_alerts WHERE customer_id = ? AND rule = 'creative_low' AND resolved_at IS NULL AND sim = 0", [$cid]) as $s) {
            $j = json_decode((string)$s['ai'], true);
            $sugg[$s['campaign_id']] = ['alert_id' => (int)$s['id'], 'suggestion' => $j['creative_suggestion'] ?? null, 'summary' => $j['summary'] ?? null, 'ai_at' => $s['ai_at']];
        }
        $by = [];
        foreach (h_q($pdo, "SELECT campaign_id, campaign, asset_id, field_type, asset_type, label, name, url, impressions, clicks, updated_at FROM $t WHERE $w ORDER BY campaign_id, FIELD(label,'LOW','LEARNING','GOOD','BEST'), impressions DESC LIMIT 400", $p) as $r) {
            $r['ctr'] = $r['impressions'] > 0 ? round($r['clicks'] / $r['impressions'], 4) : null;
            $by[$r['campaign_id']]['campaign'] = $r['campaign'];
            $by[$r['campaign_id']]['assets'][] = $r;
        }
        foreach ($by as $camp => $x) {
            $labels = array_count_values(array_column($x['assets'], 'label'));
            $out[] = ['cid' => $cid, 'cid_fmt' => ga_fmt_cid($cid), 'company' => $a['company'], 'campaign_id' => (string)$camp, 'campaign' => $x['campaign'], 'labels' => $labels ?: new stdClass(),
                'assets' => $x['assets'], 'ai' => $sugg[$camp] ?? null, 'source' => $t === 'ga_asset' ? 'read-only script' : 'dashboard script', 'window' => 'last 7 days'];
        }
    }
    return ['campaigns' => $out, 'note' => 'Google gives performance labels (BEST / GOOD / LOW / LEARNING) per asset, not an independent CPI per asset, so no asset CPI is shown. Replacement ideas are for your manual review; nothing is uploaded or changed.'];
}

// ---------------------------------------------------------------- I. AI Recommendations
function gv_recs(PDO $pdo, array $f): array
{
    $al = gv_alerts($pdo, ['status' => 'open'] + $f, 'all')['alerts'];
    $al = array_values(array_filter($al, fn($x) => !$x['sim']));
    $daily = $pdo->query("SELECT * FROM ga_runs WHERE type = 'daily' AND status = 'SUCCESS' ORDER BY id DESC LIMIT 1")->fetch();
    $sum = $daily ? json_decode((string)$daily['summary'], true) : null;
    $accts = array_keys(gv_accts($pdo, $f));
    if ($sum && isset($sum['accounts'])) $sum['accounts'] = array_values(array_filter($sum['accounts'], fn($x) => in_array((string)$x['cid'], array_map('strval', $accts), true)));
    return ['alerts' => $al, 'daily' => $sum ? ['at' => $daily['finished_at'], 'accounts' => $sum['accounts'] ?? [], 'ai' => $sum['ai'] ?? null] : null, 'ai_on' => ga_ai_on(),
        'ai_used_today' => kv_get('ga_ai_day') === gmdate('Y-m-d') ? (int)(kv_get('ga_ai_used') ?? 0) : 0, 'ai_cap' => (int)ga_set('ga_ai_cap'), 'last_deep' => kv_get('ga_last_deep') ? gmdate('Y-m-d H:i:s', (int)kv_get('ga_last_deep')) : null];
}

// ---------------------------------------------------------------- J. Live Monitoring Activity
function gv_run_out(array $r): array
{
    return ['id' => (int)$r['id'], 'uid' => $r['run_uid'], 'type' => $r['type'], 'trigger' => $r['trigger_type'], 'by' => $r['triggered_by'], 'status' => $r['status'],
        'started_at' => substr((string)$r['started_at'], 0, 19), 'finished_at' => $r['finished_at'] ? substr((string)$r['finished_at'], 0, 19) : null,
        'accounts_checked' => (int)$r['accounts_checked'], 'campaigns_checked' => (int)$r['campaigns_checked'], 'sources' => json_decode((string)$r['sources'], true) ?: [],
        'issues' => (int)$r['issues'], 'recs' => (int)$r['recs'], 'failed_checks' => (int)$r['failed_checks'], 'errors' => (string)$r['errors'], 'next_run_at' => $r['next_run_at'], 'sim' => (int)$r['sim'] === 1];
}

function gv_live(PDO $pdo, array $f): array
{
    $w = ['started_at >= ?']; $p = [gv_utc_since($f['range'] === '7d' ? '7d' : $f['range'])];
    if (in_array($f['status'], ['success', 'partial', 'failed', 'running'], true)) { $w[] = 'status = ?'; $p[] = strtoupper($f['status']); }
    $runs = array_map('gv_run_out', h_q($pdo, 'SELECT * FROM ga_runs WHERE ' . implode(' AND ', $w) . ' ORDER BY id DESC LIMIT 80', $p));
    if ($f['cid'] !== '' || $f['company'] !== '' || $f['currency'] !== '') {
        $ids = array_map('strval', array_keys(gv_accts($pdo, $f)));
        $runs = array_values(array_filter($runs, fn($r) => $r['type'] !== 'scan' || array_filter($r['sources'], fn($s) => in_array((string)($s['cid'] ?? ''), $ids, true))));
    }
    $ew = ['1=1']; $ep = [];
    if ($f['cid'] !== '') { $ew[] = "(customer_id = ? OR customer_id = '')"; $ep[] = $f['cid']; }
    $events = h_q($pdo, "SELECT id, run_id, customer_id, alert_id, level, msg, created_at FROM ga_events WHERE " . implode(' AND ', $ew) . " AND level <> 'debug' ORDER BY id DESC LIMIT 150", $ep);
    $running = $pdo->query("SELECT * FROM ga_runs WHERE status = 'RUNNING' AND started_at > DATE_SUB(UTC_TIMESTAMP(), INTERVAL 20 MINUTE) ORDER BY id DESC LIMIT 1")->fetch();
    return ['runs' => $runs, 'events' => $events, 'running' => $running ? gv_run_out($running) : null, 'next_scan_at' => ga_next_scan(), 'setup' => gv_setup(),
        'schedule' => ['scan_min' => GA_SCAN_EVERY / 60, 'email_min' => GA_MAIL_EVERY / 60, 'deep_h' => GA_DEEP_EVERY / 3600, 'daily_hour' => (int)ga_set('ga_daily_hour')],
        'last' => ['scan' => kv_get('ga_last_scan'), 'email' => kv_get('ga_last_mail'), 'deep' => kv_get('ga_last_deep')]];
}

function gv_run(PDO $pdo, int $id): array
{
    $r = $pdo->query('SELECT * FROM ga_runs WHERE id = ' . $id)->fetch();
    if (!$r) throw new RuntimeException('run not found');
    $out = gv_run_out($r);
    $out['summary'] = json_decode((string)$r['summary'], true);
    return ['run' => $out, 'events' => h_q($pdo, 'SELECT id, customer_id, alert_id, level, msg, created_at FROM ga_events WHERE run_id = ? ORDER BY id LIMIT 500', [$id])];
}

// ---------------------------------------------------------------- L. Notification Settings
function gv_settings(PDO $pdo): array
{
    $all = ga_th_all($pdo);
    $glob = [];
    foreach (GR_KEYS as $k) $glob[] = ['key' => $k, 'value' => $all['global'][$k] ?? null, 'default' => GR_DEFAULTS[$k]];
    $over = [];
    foreach ($all as $scope => $vals) if ($scope !== 'global') foreach ($vals as $k => $v) $over[] = ['scope' => $scope, 'key' => $k, 'value' => $v];
    $set = [];
    foreach (GA_SETTINGS as $k => $d) $set[$k] = ga_set($k);
    return ['thresholds' => $glob, 'overrides' => $over, 'settings' => $set, 'setup' => gv_setup(),
        'accounts' => array_values(array_map('gv_acct_out', ga_accounts($pdo))),
        'ai' => ['on' => ga_ai_on(), 'used_today' => kv_get('ga_ai_day') === gmdate('Y-m-d') ? (int)(kv_get('ga_ai_used') ?? 0) : 0, 'cap' => (int)ga_set('ga_ai_cap'), 'last_error' => kv_get('ga_ai_last_error')],
        'email_in' => ['configured' => ga_imap_on(), 'last_ok' => kv_get('ga_mail_ok_at') ? gmdate('Y-m-d H:i:s', (int)kv_get('ga_mail_ok_at')) : null, 'error' => (string)(kv_get('ga_mail_err') ?? ''),
            'mailbox' => ga_imap_on() ? preg_replace('/^(.).*(@.*)$/', '$1***$2', (string)((array)gcfg('imap'))['user']) : ''],
        'email_out' => ['configured' => function_exists('h_email_on') && h_email_on(), 'to' => function_exists('hcfg') ? preg_replace('/^(.).*(@.*)$/', '$1***$2', (string)(hcfg('email_to') ?? '')) : ''],
        'ingest_url' => h_base() . '/api/gads_ingest.php', 'keys' => GR_KEYS];
}

/** Read-only script ka text, is account ke CID + URL ke saath (token app khud bharta hai; server ke paas sirf hash). */
function gv_script(PDO $pdo, string $cid): array
{
    if (!h_q1($pdo, 'SELECT 1 FROM ga_accounts WHERE customer_id = ?', [$cid])) throw new RuntimeException('unknown account');
    $src = @file_get_contents(MFC_ROOT . '/setup/gads_health_script.js');
    if ($src === false) throw new RuntimeException('setup/gads_health_script.js is not uploaded on the server');
    $src = str_replace(["'https://YOUR-DOMAIN/mfc_dash/api/gads_ingest.php'", "'000-000-0000'"], ["'" . h_base() . "/api/gads_ingest.php'", "'" . ga_fmt_cid($cid) . "'"], $src);
    return ['script' => $src, 'placeholder' => 'PASTE-TOKEN-HERE', 'version' => GA_SCRIPT_VER];
}
