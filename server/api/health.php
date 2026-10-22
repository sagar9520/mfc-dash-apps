<?php
/**
 * MFC Dash app — "AI App Health & Auto Fix" module ki API (sirf app ke liye; web page nahi).
 * Upload: mfc_dash/api/health.php  +  mfc_dash/lib/health.php  (+ mfc_dash/lib/playmail.php)  (+ mfc_dash/health_config.php, sample: server/health_config.sample.php)
 *
 * Cron (har 5 min):  php mfc_dash/api/health.php cron   ya URL  api/health.php?cron=1&key=CRON_KEY
 *
 * App (login cookie):
 *   GET ?view=overview | consoles | runs | run&id= | issues | issue&id= | fixes | builds | logs | settings | app&pkg=&days= | export
 *   POST ?action=scan {pkg?, type?} | investigate {id} | autofix {id} | reject {id, reason} | reopen {id} | fixed {id}
 *        | task {pkg, title, details} | app_setting {pkg, field, value} | setting {key, value} | verify {pkg}
 *        | link {kind: aab|apk, id} | build_status {id, status} | publish {id, track, rollout} | test_email
 *        | playmail_test  -> Play Console mailbox abhi check karo (lib/playmail.php + config 'play_mail')
 *   GET  ?action=playmail_status -> {configured, last_check, last_ok, last_error, last_count, last_mails, unseen_left, mailbox}
 * Bina login (signed / harmless):
 *   GET ?open=issue/12   -> chhota page jo app kholta hai (email ke deep link)
 *   GET ?s=aab|apk|csv&a=..&exp=..&sig=..  -> 15 min ka signed download
 */
declare(strict_types=1);
require_once __DIR__ . '/../lib/health.php';
if (is_file(__DIR__ . '/../lib/playmail.php')) require_once __DIR__ . '/../lib/playmail.php';   // Play Console emails -> alerts (optional)

$isCli = PHP_SAPI === 'cli';
$pdo = db();
h_schema($pdo);

// ---------------------------------------------------------------- cron
if (($isCli && in_array('cron', $argv ?? [], true)) || isset($_GET['cron'])) {
    require_cron();
    @set_time_limit(300);
    $log = function (string $m) { echo gmdate('H:i:s') . "  $m\n"; };
    $q = h_schedule($pdo, $log);
    $log('queued: ' . count($q));
    // Play Console emails (config 'play_mail' ho tabhi; apna lock + time limit)
    if (function_exists('pm_cron')) { try { pm_cron($pdo, $log); } catch (Throwable $e) { $log('play mail ERROR: ' . $e->getMessage()); } }
    $n = h_work($pdo, 240.0);
    $log("runs executed: $n");
    exit;
}

// ---------------------------------------------------------------- deep link page (koi data nahi)
if (isset($_GET['open'])) {
    $route = preg_replace('#[^a-z0-9/_-]#i', '', (string)$_GET['open']);
    $intent = 'intent://health/' . $route . '#Intent;scheme=mfcdash;package=com.myfuturecode.mfcdash;end';
    header('Content-Type: text/html; charset=utf-8');
    echo '<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>MFC Dash</title>'
        . '<body style="font:16px system-ui;background:#f3f2f8;color:#15132b;display:grid;place-items:center;height:90vh;margin:0">'
        . '<div style="text-align:center;padding:24px"><p>Open this in the MFC Dash app</p>'
        . '<a href="' . htmlspecialchars($intent) . '" style="display:inline-block;padding:14px 22px;border-radius:12px;background:linear-gradient(100deg,#9c02ba,#4a1fc4);color:#fff;text-decoration:none;font-weight:600">Open MFC Dash</a>'
        . '<p style="color:#6b6a80;font-size:13px">' . htmlspecialchars($route) . '</p></div><script>setTimeout(function(){location.href=' . json_encode($intent) . '},200)</script>';
    exit;
}

// ---------------------------------------------------------------- signed downloads (AAB / APK / CSV)
if (isset($_GET['s'])) {
    $kind = (string)$_GET['s']; $arg = (string)($_GET['a'] ?? ''); $exp = (int)($_GET['exp'] ?? 0); $sig = (string)($_GET['sig'] ?? '');
    if (!h_signed_ok($kind, $arg, $exp, $sig)) { http_response_code(403); exit('link expired — open it again from the app'); }
    if ($kind === 'aab' || $kind === 'apk') {
        $b = $pdo->query('SELECT * FROM health_builds WHERE id = ' . (int)$arg)->fetch();
        $fn = $b ? basename((string)($kind === 'aab' ? $b['file'] : $b['apk_file'])) : '';
        $path = h_aab_dir() . '/' . $fn;
        if (!$b || $fn === '' || !is_file($path)) { http_response_code(404); exit('file not found'); }
        h_log($pdo, "download $kind: {$b['pkg']} {$b['version_name']} ({$b['version_code']})", 'info', ['pkg' => $b['pkg']]);
        header('Content-Type: ' . ($kind === 'apk' ? 'application/vnd.android.package-archive' : 'application/octet-stream'));
        header('Content-Disposition: attachment; filename="' . preg_replace('/[^A-Za-z0-9._-]/', '_', $b['pkg'] . '-' . $b['version_name'] . '-' . $b['version_code']) . '.' . $kind . '"');
        header('Content-Length: ' . filesize($path));
        header('X-Checksum-SHA256: ' . ($kind === 'aab' ? $b['sha256'] : $b['apk_sha256']));
        readfile($path);
        exit;
    }
    if ($kind === 'csv') {
        $f = json_decode((string)base64_decode($arg), true) ?: [];
        $what = ($f['what'] ?? 'runs') === 'issues' ? 'issues' : 'runs';
        $tz = (string)cfg('timezone');
        header('Content-Type: text/csv; charset=utf-8');
        header('Content-Disposition: attachment; filename="mfc-health-' . $what . '-' . h_local()->format('Ymd-Hi') . '.csv"');
        $out = fopen('php://output', 'w');
        $loc = fn($ts) => $ts ? h_local(substr((string)$ts, 0, 19))->format('Y-m-d H:i:s') : '';
        if ($what === 'runs') {
            [$w, $p] = h_runs_filter($f);
            fputcsv($out, ['run_id', 'job_type', 'app', 'package', 'console', 'trigger', 'triggered_by', 'status', "queued ($tz)", "started ($tz)", "finished ($tz)", 'duration_sec', 'checks_done', 'checks_total', 'problems', 'errors', 'error_text']);
            foreach (h_q($pdo, "SELECT r.*, a.name app FROM health_runs r LEFT JOIN health_apps a ON a.pkg = r.pkg WHERE $w ORDER BY r.id DESC LIMIT 20000", $p) as $r)
                fputcsv($out, [$r['run_uid'], $r['job_type'], $r['app'], $r['pkg'], $r['console_id'], $r['trigger_type'], $r['triggered_by'], $r['status'], $loc($r['queued_at']), $loc($r['started_at']),
                    $loc($r['finished_at']), $r['duration_ms'] === null ? '' : round($r['duration_ms'] / 1000, 1), $r['checks_done'], $r['checks_total'], $r['problems'], $r['errors'], $r['error_text']]);
        } else {
            [$w, $p] = h_issues_filter($f);
            fputcsv($out, ['issue_id', 'app', 'package', 'console', 'type', 'severity', 'status', 'title', 'version', 'users', 'events', "first_seen ($tz)", "last_seen ($tz)", 'evidence', 'attempts', 'ai_confidence']);
            foreach (h_q($pdo, "SELECT i.*, a.name app FROM health_issues i LEFT JOIN health_apps a ON a.pkg = i.pkg WHERE $w ORDER BY i.id DESC LIMIT 20000", $p) as $r)
                fputcsv($out, ['ISS-' . $r['id'], $r['app'], $r['pkg'], $r['console_id'], $r['type'], $r['severity'], $r['status'], $r['title'], $r['version'] ?: $r['version_code'], $r['users'], $r['events'],
                    $loc($r['first_seen']), $loc($r['last_seen']), $r['source'], $r['attempts'], $r['ai_confidence']]);
        }
        exit;
    }
    http_response_code(400); exit('bad link');
}

// ---------------------------------------------------------------- app (login)
require_login_json();
$me = admin_user();
if (session_status() === PHP_SESSION_ACTIVE) session_write_close();   // session lock chhodo: warna scan/AI ka lamba kaam chalte waqt app ki baaki requests atak kar timeout hoti hain
$action = (string)($_GET['action'] ?? '');
$bg = false;   // response ke baad queue chalana hai?

if (($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
    $in = json_decode((string)file_get_contents('php://input'), true);
    if (!is_array($in)) $in = $_POST;
    $id = (int)($in['id'] ?? 0);
    try {
        switch ($action) {
            case 'scan':
                $pkg = (string)($in['pkg'] ?? '');
                $type = (string)($in['type'] ?? 'manual_full');
                if (!in_array($type, ['manual_full', 'critical', 'play_errors', 'vitals', 'ads', 'discovery'], true)) $type = 'manual_full';
                if ($pkg !== '' && !h_q1($pdo, 'SELECT pkg FROM health_apps WHERE pkg = ?', [$pkg])) throw new RuntimeException('unknown app');
                $rid = h_run_new($pdo, $type, ['pkg' => $pkg, 'trigger' => 'manual', 'by' => $me]);
                $resp = ['ok' => true, 'run_id' => $rid, 'run_uid' => h_q1($pdo, 'SELECT run_uid FROM health_runs WHERE id = ?', [$rid])];
                $bg = true;
                break;
            case 'investigate':
                if (h_ai_key() === '') throw new RuntimeException('Claude AI key nahi hai: Settings > Claude AI me daalo');
                $i = $pdo->query('SELECT pkg, console_id FROM health_issues WHERE id = ' . $id)->fetch();
                if (!$i) throw new RuntimeException('issue not found');
                h_x($pdo, 'UPDATE health_issues SET ai_at = NULL WHERE id = ?', [$id]);
                $rid = h_run_new($pdo, 'investigate', ['pkg' => $i['pkg'], 'console' => $i['console_id'], 'trigger' => 'manual', 'by' => $me, 'issue' => $id]);
                $resp = ['ok' => true, 'run_id' => $rid]; $bg = true;
                break;
            case 'autofix':
                $resp = ['ok' => true, 'fix_id' => h_fix_start($pdo, $id, 'manual · ' . $me)];
                break;
            case 'task':
                try { $resp = ['ok' => true, 'issue_id' => h_task_create($pdo, (string)($in['pkg'] ?? ''), (string)($in['title'] ?? ''), (string)($in['details'] ?? ''), $me)]; }
                catch (HTaskWarn $w) { $resp = ['ok' => true, 'issue_id' => $w->issueId, 'warning' => 'Task saved, but the AI worker did not start: ' . $w->getMessage()]; }
                break;
            case 'reject':
                h_x($pdo, 'UPDATE health_issues SET reject_reason = ? WHERE id = ?', [h_cut((string)($in['reason'] ?? ''), 500), $id]);
                h_issue_status($pdo, $id, 'REJECTED', 'by ' . $me . (($in['reason'] ?? '') !== '' ? ': ' . $in['reason'] : ''));
                $resp = ['ok' => true];
                break;
            case 'reopen': h_issue_status($pdo, $id, 'OPEN', 'reopened by ' . $me); $resp = ['ok' => true]; break;
            case 'fixed': h_issue_status($pdo, $id, 'FIXED', 'marked fixed by ' . $me); $resp = ['ok' => true]; break;
            case 'app_setting':
                $f = (string)($in['field'] ?? '');
                if (!in_array($f, ['monitoring', 'autofix', 'email'], true)) throw new RuntimeException('field not allowed');   // production publish: koi switch hi nahi
                h_x($pdo, "UPDATE health_apps SET `$f` = ?, updated_at = UTC_TIMESTAMP() WHERE pkg = ?", [!empty($in['value']) && $in['value'] !== '0' && $in['value'] !== 'false' ? 1 : 0, (string)($in['pkg'] ?? '')]);
                h_log($pdo, "setting $f = " . (int)!empty($in['value']) . " for " . ($in['pkg'] ?? '') . " by $me", 'info', ['pkg' => (string)($in['pkg'] ?? '')]);
                $resp = ['ok' => true];
                break;
            case 'setting':
                $k = (string)($in['key'] ?? '');
                if (!in_array($k, ['h_mail_all', 'h_mail_crash', 'h_mail_ad', 'h_mail_fixfail', 'h_mail_aab', 'h_mail_console', 'h_mail_summary'], true)) throw new RuntimeException('key not allowed');
                kv_set($k, !empty($in['value']) && $in['value'] !== '0' && $in['value'] !== 'false' ? '1' : '0');
                $resp = ['ok' => true];
                break;
            case 'verify': h_verify_start($pdo, (string)($in['pkg'] ?? '')); $resp = ['ok' => true]; break;
            case 'link':
                $kind = (string)($in['kind'] ?? 'aab') === 'apk' ? 'apk' : 'aab';
                $b = $pdo->query('SELECT id, file, apk_file FROM health_builds WHERE id = ' . $id)->fetch();
                if (!$b || ($kind === 'apk' ? $b['apk_file'] : $b['file']) === '') throw new RuntimeException('file not available');
                $resp = ['ok' => true, 'url' => h_signed($kind, (string)$id), 'expires_in' => 900];
                break;
            case 'build_status':
                $s = (string)($in['status'] ?? '');
                if (!in_array($s, ['READY_FOR_REVIEW', 'APPROVED', 'UPLOADED_BY_USER', 'DISCARDED'], true)) throw new RuntimeException('bad status');
                h_x($pdo, "UPDATE health_builds SET status = ? WHERE id = ? AND status <> 'PUBLISHED'", [$s, $id]);
                h_log($pdo, "build #$id → $s by $me");
                $resp = ['ok' => true];
                break;
            case 'publish':
                // Sirf yahi rasta Play par le jaata hai, aur sirf Sagar ke tap (confirm dialog) se.
                if (empty($in['confirm'])) throw new RuntimeException('confirmation missing');
                $resp = ['ok' => true] + h_publish($pdo, $id, (string)($in['track'] ?? 'internal'), (float)($in['rollout'] ?? 1), $me);
                break;
            case 'test_email':
                if (!h_email_on()) throw new RuntimeException('email_to not set in health_config.php');
                $ok = h_mail_send((string)hcfg('email_to'), '[MFC Health] Test email', "Email notifications are working.\n\n" . h_link('overview'));
                if (!$ok) throw new RuntimeException('send failed: ' . (kv_get('h_mail_err') ?? '?'));
                $resp = ['ok' => true];
                break;
            case 'playmail_test':
                if (!function_exists('pm_run')) throw new RuntimeException('lib/playmail.php not uploaded');
                if (!pm_cfg()) throw new RuntimeException("'play_mail' not set in health_config.php");
                @set_time_limit(180);
                $r = pm_run($pdo, 'manual · ' . $me);
                if (!$r['ok']) throw new RuntimeException($r['error']);
                $resp = ['ok' => true, 'result' => $r, 'status' => pm_status_out()];
                break;
            default: json_out(['ok' => false, 'error' => 'unknown action'], 400);
        }
    } catch (Throwable $e) {
        json_out(['ok' => false, 'error' => $e->getMessage()], 400);
    }
    h_respond_then_work($pdo, $resp, $bg);
}

// ---------------------------------------------------------------- GET actions
if ($action === 'playmail_status') {
    json_out(['ok' => true] + (function_exists('pm_status_out') ? pm_status_out()
        : ['configured' => false, 'last_check' => null, 'last_ok' => null, 'last_error' => '', 'last_count' => 0, 'last_mails' => 0, 'unseen_left' => 0, 'mailbox' => '']));
}

// ---------------------------------------------------------------- GET views
$view = (string)($_GET['view'] ?? 'overview');
h_sync_apps($pdo);
try {
    switch ($view) {
        case 'overview': $out = h_view_overview($pdo); break;
        case 'consoles': $out = h_view_consoles($pdo); break;
        case 'runs': $out = h_view_runs($pdo, $_GET); break;
        case 'run': $out = h_view_run($pdo, (int)($_GET['id'] ?? 0)); break;
        case 'issues': $out = h_view_issues($pdo, $_GET); break;
        case 'issue': $out = h_view_issue($pdo, (int)($_GET['id'] ?? 0)); break;
        case 'fixes': $out = h_view_fixes($pdo, $_GET); break;
        case 'builds': $out = h_view_builds($pdo, $_GET); break;
        case 'logs': $out = h_view_logs($pdo, $_GET); break;
        case 'settings': $out = h_view_settings($pdo); break;
        case 'app': $out = h_view_app($pdo, (string)($_GET['pkg'] ?? ''), (int)($_GET['days'] ?? 30)); break;
        case 'export':
            $f = array_intersect_key($_GET, array_flip(['what', 'console', 'pkg', 'status', 'severity', 'type', 'from', 'to', 'q', 'job']));
            $out = ['url' => h_signed('csv', base64_encode(h_json($f))), 'expires_in' => 900];
            break;
        default: json_out(['ok' => false, 'error' => 'unknown view'], 400);
    }
} catch (Throwable $e) {
    json_out(['ok' => false, 'error' => $e->getMessage()], 500);
}
$out = ['ok' => true, 'server_time' => gmdate('Y-m-d H:i:s'), 'tz' => (string)cfg('timezone'), 'tz_offset' => tz_offset_str()] + $out;
// app khuli ho aur cron atka ho to bhi queued run chal jaaye
$queued = (int)h_q1($pdo, "SELECT COUNT(*) FROM health_runs WHERE status = 'QUEUED' AND job_type <> 'ai_fix'");
h_respond_then_work($pdo, $out, $queued > 0);

// ================================================================ helpers
function h_respond_then_work(PDO $pdo, array $out, bool $work): void
{
    $s = h_json($out);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    header('Content-Length: ' . strlen($s));
    header('Connection: close');
    echo $s;
    if ($work) {
        ignore_user_abort(true);
        @set_time_limit(300);
        if (function_exists('fastcgi_finish_request')) fastcgi_finish_request();
        else { while (ob_get_level() > 0) ob_end_flush(); flush(); }
        try { h_work($pdo, 200.0); } catch (Throwable $e) { error_log('mfc health work: ' . $e->getMessage()); }
    }
    exit;
}

function h_dec(?string $j) { $v = $j === null ? null : json_decode($j, true); return $v; }

/** MFC tracker ka har app ka sabse naya live version (alerts.php ka new_version rule kv al_live_ver me rakhta hai): pkg => {ver, vc}. */
function h_tracker_live(): array
{
    static $m = null;
    if ($m === null) { $m = json_decode((string)(kv_get('al_live_ver') ?? ''), true); if (!is_array($m)) $m = []; }
    return $m;
}

function h_app_row(array $a): array
{
    $verified = (int)$a['repo_verified'] === 1 && (int)$a['signing_verified'] === 1;
    $vv = h_dec($a['vitals_ver'] ?? null);
    $tl = h_tracker_live()[$a['pkg']] ?? null;
    return ['pkg' => $a['pkg'], 'name' => $a['name'] !== '' ? $a['name'] : $a['pkg'], 'console_id' => $a['console_id'], 'icon' => $a['icon_url'],
        'in_dashboard' => (int)$a['in_dashboard'], 'status' => $a['status'], 'monitoring' => (int)$a['monitoring'], 'autofix' => (int)$a['autofix'],
        'autofix_effective' => (int)$a['autofix'] === 1 && $verified ? 1 : 0, 'email' => (int)$a['email'], 'publish_auto' => 0,
        'repo' => $a['repo'], 'repo_verified' => (int)$a['repo_verified'], 'signing_verified' => (int)$a['signing_verified'], 'verify_note' => $a['verify_note'],
        'crash_rate' => $a['crash_rate'] === null ? null : (float)$a['crash_rate'], 'anr_rate' => $a['anr_rate'] === null ? null : (float)$a['anr_rate'],
        'vitals_day' => $a['vitals_day'], 'vitals_ver' => is_array($vv) && isset($vv['rows']) ? $vv : null,
        'tracker_ver' => is_array($tl) && ($tl['ver'] ?? '') !== '' ? (string)$tl['ver'] : null, 'tracker_vc' => is_array($tl) && !empty($tl['vc']) ? (int)$tl['vc'] : null,
        'last_scan_at' => $a['last_scan_at'], 'discovered_at' => $a['discovered_at']];
}

function h_console_rows(PDO $pdo): array
{
    $cfg = h_consoles_cfg();
    $db = [];
    foreach (h_q($pdo, 'SELECT * FROM health_consoles') as $r) $db[$r['id']] = $r;
    $out = [];
    foreach ($cfg as $id => $c) {
        $r = $db[$id] ?? null;
        $n = (int)h_q1($pdo, 'SELECT COUNT(*) FROM health_apps WHERE console_id = ?', [$id]);
        $out[] = ['id' => $id, 'name' => $c['name'], 'status' => $r['status'] ?? 'NOT_SYNCED', 'apps' => $n,
            'last_sync_at' => $r['last_sync_at'] ?? null, 'last_ok_at' => $r['last_ok_at'] ?? null, 'last_error' => $r['last_error'] ?? '',
            'warnings' => array_keys(h_dec($r['warnings'] ?? null) ?: []), 'sa_set' => $c['sa'] !== '' && is_file(h_sa_path($c['sa']))];
    }
    return $out;
}

function h_issue_row(array $i): array
{
    return ['id' => (int)$i['id'], 'pkg' => $i['pkg'], 'app' => $i['app'] ?? $i['pkg'], 'icon' => $i['icon_url'] ?? '', 'console_id' => $i['console_id'], 'type' => $i['type'],
        'severity' => $i['severity'], 'status' => $i['status'], 'title' => $i['title'], 'version' => $i['version'] !== '' ? $i['version'] : ($i['version_code'] ? (string)$i['version_code'] : ''),
        'users' => $i['users'] === null ? null : (int)$i['users'], 'events' => (int)$i['events'], 'first_seen' => $i['first_seen'], 'last_seen' => $i['last_seen'],
        'source' => $i['source'], 'attempts' => (int)$i['attempts'], 'ai_confidence' => $i['ai_confidence'] === null ? null : (float)$i['ai_confidence'], 'status_at' => $i['status_at']];
}

function h_run_row(array $r): array
{
    return ['id' => (int)$r['id'], 'uid' => $r['run_uid'], 'type' => $r['job_type'], 'pkg' => $r['pkg'], 'app' => $r['app'] ?? '', 'console_id' => $r['console_id'],
        'trigger' => $r['trigger_type'], 'by' => $r['triggered_by'], 'status' => $r['status'], 'stage' => $r['stage'], 'checks_total' => (int)$r['checks_total'],
        'checks_done' => (int)$r['checks_done'], 'problems' => (int)$r['problems'], 'errors' => (int)$r['errors'], 'error_text' => $r['error_text'],
        'queued_at' => $r['queued_at'], 'started_at' => $r['started_at'] ? substr((string)$r['started_at'], 0, 19) : null,
        'finished_at' => $r['finished_at'] ? substr((string)$r['finished_at'], 0, 19) : null, 'duration_ms' => $r['duration_ms'] === null ? null : (int)$r['duration_ms'],
        'issue_id' => $r['issue_id'] === null ? null : (int)$r['issue_id'], 'fix_id' => $r['fix_id'] === null ? null : (int)$r['fix_id']];
}

function h_day_utc(string $d, bool $end): ?string
{
    if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $d)) return null;
    [$s, $e] = day_bounds_utc($d);
    return $end ? $e : $s;
}

function h_runs_filter(array $f): array
{
    $w = ['1=1']; $p = [];
    if (($f['console'] ?? '') !== '') { $w[] = 'r.console_id = ?'; $p[] = $f['console']; }
    if (($f['pkg'] ?? '') !== '') { $w[] = 'r.pkg = ?'; $p[] = $f['pkg']; }
    if (($f['job'] ?? '') !== '') { $w[] = 'r.job_type = ?'; $p[] = $f['job']; }
    if (($f['status'] ?? '') === 'active') $w[] = "r.status IN ('QUEUED','RUNNING')";
    elseif (($f['status'] ?? '') !== '') { $w[] = 'r.status = ?'; $p[] = $f['status']; }
    if (($s = h_day_utc((string)($f['from'] ?? ''), false)) !== null) { $w[] = 'r.queued_at >= ?'; $p[] = $s; }
    if (($e = h_day_utc((string)($f['to'] ?? ''), true)) !== null) { $w[] = 'r.queued_at < ?'; $p[] = $e; }
    if (($f['q'] ?? '') !== '') { $w[] = '(r.run_uid LIKE ? OR r.pkg LIKE ? OR r.error_text LIKE ?)'; $q = '%' . $f['q'] . '%'; array_push($p, $q, $q, $q); }
    return [implode(' AND ', $w), $p];
}

function h_issues_filter(array $f): array
{
    $w = ['1=1']; $p = [];
    if (($f['console'] ?? '') !== '') { $w[] = 'i.console_id = ?'; $p[] = $f['console']; }
    if (($f['pkg'] ?? '') !== '') { $w[] = 'i.pkg = ?'; $p[] = $f['pkg']; }
    $st = (string)($f['status'] ?? 'open');
    if ($st === 'open') $w[] = "i.status IN ('" . implode("','", H_ISSUE_ACTIVE) . "')";
    elseif ($st === 'fixed') $w[] = "i.status IN ('FIXED','READY_FOR_REVIEW')";
    elseif ($st === 'rejected') $w[] = "i.status = 'REJECTED'";
    elseif ($st === 'resolved') $w[] = "i.status = 'RESOLVED'";
    elseif ($st !== 'all' && in_array($st, H_ISSUE_ALL, true)) { $w[] = 'i.status = ?'; $p[] = $st; }
    if (($f['severity'] ?? '') !== '') { $w[] = 'i.severity = ?'; $p[] = $f['severity']; }
    if (($f['type'] ?? '') !== '') { $w[] = 'i.type = ?'; $p[] = $f['type']; }
    if (($s = h_day_utc((string)($f['from'] ?? ''), false)) !== null) { $w[] = 'i.last_seen >= ?'; $p[] = $s; }
    if (($e = h_day_utc((string)($f['to'] ?? ''), true)) !== null) { $w[] = 'i.first_seen < ?'; $p[] = $e; }
    if (($f['q'] ?? '') !== '') { $w[] = '(i.title LIKE ? OR i.cause LIKE ? OR i.location LIKE ? OR i.pkg LIKE ? OR CONCAT(\'ISS-\', i.id) = ?)'; $q = '%' . $f['q'] . '%'; array_push($p, $q, $q, $q, $q, strtoupper((string)$f['q'])); }
    return [implode(' AND ', $w), $p];
}

// ---------------------------------------------------------------- views
function h_view_overview(PDO $pdo): array
{
    $apps = $pdo->query("SELECT COUNT(*) n, SUM(status = 'HEALTHY') ok, SUM(status IN ('PROBLEM','WATCH')) bad, SUM(monitoring = 1) mon FROM health_apps")->fetch();
    $act = "'" . implode("','", H_ISSUE_ACTIVE) . "'";
    $iss = $pdo->query("SELECT SUM(status IN ($act)) open, SUM(status IN ($act) AND severity = 'P0') p0, SUM(status = 'INVESTIGATING') inv, SUM(status IN ('FIX_IN_PROGRESS','TESTING','AAB_BUILDING')) fixing, SUM(status = 'NEEDS_HUMAN_REVIEW') human FROM health_issues")->fetch();
    $fx = $pdo->query('SELECT SUM(success = 1) ok, SUM(success = 0) bad FROM health_fixes WHERE started_at >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 DAY)')->fetch();
    $aab = (int)h_q1($pdo, "SELECT COUNT(*) FROM health_builds WHERE status IN ('READY_FOR_REVIEW','APPROVED')");
    $runs24 = $pdo->query("SELECT COUNT(*) n, SUM(status = 'COMPLETED_NO_ISSUES') clean, SUM(status = 'ISSUES_DETECTED') iss, SUM(status = 'FAILED') failed, SUM(status IN ('QUEUED','RUNNING')) active FROM health_runs WHERE queued_at >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)")->fetch();
    $lastId = (int)(kv_get('h_last_cycle') ?? 0);
    $last = $lastId ? $pdo->query('SELECT r.*, a.name app FROM health_runs r LEFT JOIN health_apps a ON a.pkg = r.pkg WHERE r.id = ' . $lastId)->fetch() : null;
    $att = h_q($pdo, "SELECT i.*, a.name app, a.icon_url FROM health_issues i LEFT JOIN health_apps a ON a.pkg = i.pkg WHERE i.status IN ($act) ORDER BY i.severity, IFNULL(i.users, 0) DESC, i.last_seen DESC LIMIT 8");
    $cronLast = (int)(kv_get('h_cron_last') ?? 0);
    $tracker = h_q1($pdo, "SELECT MAX(ts) FROM events WHERE ts >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)");
    $cons = h_console_rows($pdo);
    $integr = [];
    foreach ($cons as $c) $integr[] = ['name' => $c['name'], 'kind' => 'play', 'status' => $c['status'], 'detail' => $c['last_error'] !== '' ? $c['last_error'] : ($c['warnings'][0] ?? ($c['apps'] . ' apps'))];
    $integr[] = ['name' => 'MFC tracker (app events)', 'kind' => 'tracker', 'status' => $tracker && strtotime($tracker . ' UTC') > time() - 1800 ? 'OK' : 'ERROR', 'detail' => $tracker ? 'last event ' . $tracker . ' UTC' : 'no events in 24h'];
    $integr[] = ['name' => 'Health cron', 'kind' => 'cron', 'status' => $cronLast > time() - 900 ? 'OK' : ($cronLast ? 'ERROR' : 'NOT_SET'), 'detail' => $cronLast ? 'last run ' . gmdate('Y-m-d H:i:s', $cronLast) . ' UTC' : 'cron not running yet'];
    $integr[] = ['name' => 'Claude AI', 'kind' => 'ai', 'status' => h_ai_key() !== '' ? 'OK' : 'NOT_SET', 'detail' => h_ai_key() !== '' ? h_ai_model() : 'add key in Settings'];
    $integr[] = ['name' => 'Email', 'kind' => 'email', 'status' => h_email_on() ? 'OK' : 'NOT_SET', 'detail' => h_email_on() ? (empty((hcfg('smtp') ?? [])['host']) ? 'PHP mail()' : 'SMTP') : 'email_to not set'];
    $integr[] = ['name' => 'GitHub (AI fix worker)', 'kind' => 'github', 'status' => trim((string)(hcfg('github_token') ?? '')) !== '' ? 'OK' : 'NOT_SET', 'detail' => count((array)(hcfg('repos') ?? [])) . ' repos linked'];
    if (function_exists('pm_cfg') && pm_cfg()) {   // sirf config ho tab (warna list pehle jaisi)
        $pm = pm_status_out();
        $integr[] = ['name' => 'Play Console emails', 'kind' => 'playmail', 'status' => $pm['last_error'] !== '' ? 'ERROR' : ($pm['last_check'] ? 'OK' : 'NOT_SET'),
            'detail' => $pm['last_error'] !== '' ? $pm['last_error'] : ($pm['last_check'] ? 'last check ' . $pm['last_check'] . ' UTC · ' . $pm['last_count'] . ' new alerts' : 'waiting for the first cron check')];
    }
    return [
        'consoles' => count($cons), 'consoles_ok' => count(array_filter($cons, fn($c) => $c['status'] === 'OK')),
        'apps' => (int)$apps['n'], 'apps_monitored' => (int)$apps['mon'], 'healthy' => (int)$apps['ok'], 'with_problems' => (int)$apps['bad'],
        'issues_open' => (int)$iss['open'], 'issues_p0' => (int)$iss['p0'], 'investigating' => (int)$iss['inv'], 'fixing' => (int)$iss['fixing'], 'needs_human' => (int)$iss['human'],
        'fixes_ok_30d' => (int)$fx['ok'], 'fixes_failed_30d' => (int)$fx['bad'], 'aab_ready' => $aab,
        'runs_24h' => ['total' => (int)$runs24['n'], 'clean' => (int)$runs24['clean'], 'issues' => (int)$runs24['iss'], 'failed' => (int)$runs24['failed'], 'active' => (int)$runs24['active']],
        'last_cycle' => $last ? h_run_row($last) : null, 'next_scan' => h_next_scan(),
        'integrations' => $integr, 'attention' => array_map('h_issue_row', $att),
        'cron_ok' => $cronLast > time() - 900, 'play_configured' => count($cons) > 0,
    ];
}

function h_view_consoles(PDO $pdo): array
{
    $act = "'" . implode("','", H_ISSUE_ACTIVE) . "'";
    $open = [];
    foreach (h_q($pdo, "SELECT pkg, COUNT(*) n, MIN(severity) sev FROM health_issues WHERE status IN ($act) GROUP BY pkg") as $r) $open[$r['pkg']] = $r;
    $apps = [];
    foreach (h_apps($pdo, '', false) as $a) { $row = h_app_row($a); $row['open_issues'] = (int)($open[$a['pkg']]['n'] ?? 0); $row['top_severity'] = $open[$a['pkg']]['sev'] ?? null; $apps[] = $row; }
    $cons = h_console_rows($pdo);
    $unlinked = array_values(array_filter($apps, fn($a) => $a['console_id'] === '' || !in_array($a['console_id'], array_column($cons, 'id'), true)));
    return ['consoles' => $cons, 'apps' => $apps, 'unlinked' => array_column($unlinked, 'pkg')];
}

function h_view_runs(PDO $pdo, array $g): array
{
    [$w, $p] = h_runs_filter($g);
    $limit = max(1, min(300, (int)($g['limit'] ?? 100)));
    $rows = h_q($pdo, "SELECT r.*, a.name app FROM health_runs r LEFT JOIN health_apps a ON a.pkg = r.pkg WHERE $w ORDER BY (r.status IN ('RUNNING','QUEUED')) DESC, r.id DESC LIMIT $limit", $p);
    $feed = h_q($pdo, 'SELECT id, run_id, issue_id, fix_id, pkg, level, msg, created_at FROM health_events ORDER BY id DESC LIMIT 40');
    $cnt = $pdo->query("SELECT SUM(status = 'RUNNING') running, SUM(status = 'QUEUED') queued, SUM(status <> 'QUEUED' AND status <> 'RUNNING' AND queued_at >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)) done24, SUM(status = 'FAILED' AND queued_at >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)) failed24 FROM health_runs")->fetch();
    return ['runs' => array_map('h_run_row', $rows), 'feed' => array_map(fn($e) => $e + ['created_at' => substr((string)$e['created_at'], 0, 19)], $feed),
        'counts' => ['running' => (int)$cnt['running'], 'queued' => (int)$cnt['queued'], 'done_24h' => (int)$cnt['done24'], 'failed_24h' => (int)$cnt['failed24']], 'next_scan' => h_next_scan()];
}

function h_view_run(PDO $pdo, int $id): array
{
    $r = $pdo->query('SELECT r.*, a.name app FROM health_runs r LEFT JOIN health_apps a ON a.pkg = r.pkg WHERE r.id = ' . $id)->fetch();
    if (!$r) throw new RuntimeException('run not found');
    $ev = h_q($pdo, 'SELECT level, msg, created_at, issue_id FROM health_events WHERE run_id = ? ORDER BY id LIMIT 500', [$id]);
    return ['run' => h_run_row($r), 'events' => $ev];
}

function h_view_issues(PDO $pdo, array $g): array
{
    [$w, $p] = h_issues_filter($g);
    $limit = max(1, min(500, (int)($g['limit'] ?? 200)));
    $rows = h_q($pdo, "SELECT i.*, a.name app, a.icon_url FROM health_issues i LEFT JOIN health_apps a ON a.pkg = i.pkg WHERE $w ORDER BY FIELD(i.status,'NEEDS_HUMAN_REVIEW','READY_FOR_REVIEW') DESC, i.severity, i.last_seen DESC LIMIT $limit", $p);
    $cnt = $pdo->query("SELECT SUM(status IN ('" . implode("','", H_ISSUE_ACTIVE) . "')) open, SUM(status IN ('FIXED','READY_FOR_REVIEW')) fixed, SUM(status = 'REJECTED') rejected, SUM(status = 'RESOLVED') resolved, COUNT(*) total FROM health_issues")->fetch();
    return ['issues' => array_map('h_issue_row', $rows), 'counts' => array_map('intval', $cnt)];
}

function h_fix_row(PDO $pdo, array $f, bool $full): array
{
    $o = ['id' => (int)$f['id'], 'issue_id' => (int)$f['issue_id'], 'attempt' => (int)$f['attempt'], 'status' => $f['status'], 'repo' => $f['repo'], 'branch' => $f['branch'],
        'base_sha' => $f['base_sha'], 'commit_sha' => $f['commit_sha'], 'gh_run_url' => $f['gh_run_url'], 'unit_ok' => $f['unit_ok'] === null ? null : (int)$f['unit_ok'],
        'build_ok' => $f['build_ok'] === null ? null : (int)$f['build_ok'], 'device_ok' => $f['device_ok'] === null ? null : (int)$f['device_ok'],
        'success' => $f['success'] === null ? null : (int)$f['success'], 'failure_reason' => $f['failure_reason'], 'started_at' => $f['started_at'], 'finished_at' => $f['finished_at'],
        'files_modified' => h_dec($f['files_modified']) ?: [], 'app' => $f['app'] ?? '', 'pkg' => $f['pkg'] ?? '', 'title' => $f['title'] ?? ''];
    if ($full) {
        $o += ['files_inspected' => h_dec($f['files_inspected']) ?: [], 'diff' => (string)$f['diff'], 'explanation' => (string)$f['explanation'],
            'unit_tests' => (string)$f['unit_tests'], 'build_log' => (string)$f['build_log'], 'device_test' => (string)$f['device_test'],
            'steps' => h_q($pdo, 'SELECT step, status, detail, created_at FROM health_fix_steps WHERE fix_id = ? ORDER BY id', [(int)$f['id']])];
        foreach ($o['steps'] as &$s) $s['created_at'] = substr((string)$s['created_at'], 0, 19);
    }
    return $o;
}

function h_build_row(array $b): array
{
    return ['id' => (int)$b['id'], 'pkg' => $b['pkg'], 'app' => $b['app'] ?? $b['pkg'], 'icon' => $b['icon_url'] ?? '', 'issue_id' => $b['issue_id'] === null ? null : (int)$b['issue_id'],
        'fix_id' => $b['fix_id'] === null ? null : (int)$b['fix_id'], 'version_name' => $b['version_name'], 'version_code' => (int)$b['version_code'], 'commit_sha' => $b['commit_sha'],
        'size' => (int)$b['size'], 'sha256' => $b['sha256'], 'has_apk' => $b['apk_file'] !== '', 'apk_size' => (int)$b['apk_size'], 'release_notes' => (string)$b['release_notes'],
        'tests' => h_dec($b['tests']) ?: new stdClass(), 'status' => $b['status'], 'published_track' => $b['published_track'], 'published_at' => $b['published_at'],
        'publish_note' => $b['publish_note'], 'created_at' => $b['created_at']];
}

function h_view_issue(PDO $pdo, int $id): array
{
    $i = $pdo->query('SELECT i.*, a.name app, a.icon_url FROM health_issues i LEFT JOIN health_apps a ON a.pkg = i.pkg WHERE i.id = ' . $id)->fetch();
    if (!$i) throw new RuntimeException('issue not found');
    $o = h_issue_row($i) + ['cause' => $i['cause'], 'location' => $i['location'], 'play_uri' => $i['play_uri'], 'devices' => h_dec($i['devices']) ?: [],
        'os_versions' => h_dec($i['os_versions']) ?: [], 'stack' => (string)$i['stack'], 'metrics' => h_dec($i['metrics']) ?: new stdClass(),
        'ai_analysis' => (string)$i['ai_analysis'], 'ai_missing' => h_dec($i['ai_missing']) ?: [], 'ai_fix' => (string)$i['ai_fix'], 'ai_autofix_ok' => $i['ai_autofix_ok'] === null ? null : (int)$i['ai_autofix_ok'],
        'ai_at' => $i['ai_at'], 'reject_reason' => $i['reject_reason']];
    $a = $pdo->prepare('SELECT * FROM health_apps WHERE pkg = ?'); $a->execute([$i['pkg']]); $app = $a->fetch() ?: [];
    $o['autofix_block'] = $app ? h_autofix_block($app, $i) : 'app not found';
    $o['console_name'] = h_consoles_cfg()[$i['console_id']]['name'] ?? $i['console_id'];
    $fixes = h_q($pdo, 'SELECT * FROM health_fixes WHERE issue_id = ? ORDER BY id DESC', [$id]);
    $o['fixes'] = array_map(fn($f) => h_fix_row($pdo, $f, true), $fixes);
    $o['builds'] = array_map('h_build_row', h_q($pdo, 'SELECT b.*, a.name app FROM health_builds b LEFT JOIN health_apps a ON a.pkg = b.pkg WHERE b.issue_id = ? ORDER BY b.id DESC', [$id]));
    $o['log'] = array_map(fn($e) => $e + ['created_at' => substr((string)$e['created_at'], 0, 19)], h_q($pdo, 'SELECT level, msg, created_at FROM health_events WHERE issue_id = ? ORDER BY id DESC LIMIT 60', [$id]));
    $o['investigating'] = (int)h_q1($pdo, "SELECT COUNT(*) FROM health_runs WHERE job_type = 'investigate' AND issue_id = ? AND status IN ('QUEUED','RUNNING')", [$id]) > 0;
    return ['issue' => $o];
}

function h_view_fixes(PDO $pdo, array $g): array
{
    $w = '1=1'; $p = [];
    if (($g['pkg'] ?? '') !== '') { $w .= ' AND i.pkg = ?'; $p[] = $g['pkg']; }
    if (($g['result'] ?? '') === 'ok') $w .= ' AND f.success = 1'; elseif (($g['result'] ?? '') === 'fail') $w .= ' AND f.success = 0'; elseif (($g['result'] ?? '') === 'running') $w .= ' AND f.finished_at IS NULL';
    $rows = h_q($pdo, "SELECT f.*, i.pkg, i.title, a.name app FROM health_fixes f JOIN health_issues i ON i.id = f.issue_id LEFT JOIN health_apps a ON a.pkg = i.pkg WHERE $w ORDER BY f.id DESC LIMIT 200", $p);
    return ['fixes' => array_map(fn($f) => h_fix_row($pdo, $f, false), $rows)];
}

function h_view_builds(PDO $pdo, array $g): array
{
    $w = '1=1'; $p = [];
    if (($g['pkg'] ?? '') !== '') { $w .= ' AND b.pkg = ?'; $p[] = $g['pkg']; }
    $rows = h_q($pdo, "SELECT b.*, a.name app, a.icon_url FROM health_builds b LEFT JOIN health_apps a ON a.pkg = b.pkg WHERE $w ORDER BY (b.status IN ('READY_FOR_REVIEW','APPROVED')) DESC, b.id DESC LIMIT 200", $p);
    return ['builds' => array_map('h_build_row', $rows)];
}

function h_view_logs(PDO $pdo, array $g): array
{
    $w = '1=1'; $p = [];
    if (($g['level'] ?? '') !== '') { $w .= ' AND level = ?'; $p[] = $g['level']; }
    if (($g['pkg'] ?? '') !== '') { $w .= ' AND pkg = ?'; $p[] = $g['pkg']; }
    if (($g['q'] ?? '') !== '') { $w .= ' AND msg LIKE ?'; $p[] = '%' . $g['q'] . '%'; }
    $before = (int)($g['before'] ?? 0);
    if ($before > 0) { $w .= ' AND id < ?'; $p[] = $before; }
    $rows = h_q($pdo, "SELECT id, run_id, issue_id, fix_id, pkg, level, msg, created_at FROM health_events WHERE $w ORDER BY id DESC LIMIT 200", $p);
    return ['logs' => array_map(fn($e) => $e + ['created_at' => substr((string)$e['created_at'], 0, 19)], $rows)];
}

function h_view_settings(PDO $pdo): array
{
    $mail = [];
    foreach (['h_mail_all' => 'All email', 'h_mail_crash' => 'Critical crash detected', 'h_mail_ad' => 'Important ad issue', 'h_mail_fixfail' => 'Fix attempt failed',
        'h_mail_aab' => 'Tested AAB available', 'h_mail_console' => 'Play Console integration broken', 'h_mail_summary' => 'Daily portfolio summary'] as $k => $l)
        $mail[] = ['key' => $k, 'label' => $l, 'on' => kv_get($k) !== '0'];
    $to = (string)(hcfg('email_to') ?? '');
    return ['apps' => array_map('h_app_row', h_apps($pdo, '', false)), 'email' => ['configured' => h_email_on(), 'to_hint' => $to === '' ? '' : preg_replace('/(?<=.).(?=[^@]*@)/', '•', $to), 'toggles' => $mail],
        'schedule' => array_merge([['label' => 'Critical health (tracker crashes)', 'every' => '15 min'], ['label' => 'Play crash / ANR issues', 'every' => '1 hour'], ['label' => 'Ad network / ad unit checks', 'every' => '1 hour'],
            ['label' => 'Play vitals (crash / ANR rate)', 'every' => '6 hours (Google data is 1-2 days late)'], ['label' => 'App discovery', 'every' => 'daily'],
            ['label' => 'Full portfolio audit', 'every' => 'daily ' . H_AUDIT_HOUR . ':00'], ['label' => 'Email summary', 'every' => 'daily ' . H_SUMMARY_HOUR . ':00'],
            ['label' => 'AI investigation', 'every' => 'on new P0/P1 crash / ANR'], ['label' => 'Max fix attempts per issue', 'every' => (string)h_max_attempts()]],
            function_exists('pm_cfg') && pm_cfg() ? [['label' => 'Play Console emails (mailbox)', 'every' => '5 min']] : []),
        'production_publish' => 'manual approval only', 'ai' => ['enabled' => h_ai_key() !== '', 'model' => h_ai_model()],
        'github' => trim((string)(hcfg('github_token') ?? '')) !== '', 'consoles' => h_console_rows($pdo), 'cron_ok' => (int)(kv_get('h_cron_last') ?? 0) > time() - 900];
}

/** Ek app ki poori detail: logo, console, installs / uninstalls / crashes (roz), issues (open / fixed / rejected), vitals, runs, builds. */
function h_view_app(PDO $pdo, string $pkg, int $days): array
{
    $a = $pdo->prepare('SELECT * FROM health_apps WHERE pkg = ?'); $a->execute([$pkg]); $app = $a->fetch();
    if (!$app) throw new RuntimeException('app not found');
    if ($app['icon_at'] === null) { try { h_icon($pdo, $pkg); $a->execute([$pkg]); $app = $a->fetch(); } catch (Throwable $e) { } }
    $days = in_array($days, [7, 14, 30, 90], true) ? $days : 30;
    $from = (new DateTime(local_today(), tz()))->modify('-' . ($days - 1) . ' day')->format('Y-m-d');
    $series = h_q($pdo, 'SELECT day, installs, uninstalls, crashes, dau, sessions, rev_usd FROM day_app WHERE pkg = ? AND day >= ? ORDER BY day', [$pkg, $from]);
    $tot = ['installs' => 0, 'uninstalls' => 0, 'crashes' => 0, 'rev_usd' => 0.0];
    foreach ($series as &$s) { foreach (['installs', 'uninstalls', 'crashes', 'dau', 'sessions'] as $k) $s[$k] = (int)$s[$k]; $s['rev_usd'] = (float)$s['rev_usd']; $tot['installs'] += $s['installs']; $tot['uninstalls'] += $s['uninstalls']; $tot['crashes'] += $s['crashes']; $tot['rev_usd'] += $s['rev_usd']; }
    unset($s);
    $dauAvg = $series ? array_sum(array_column($series, 'dau')) / count($series) : 0;
    $sessions = $series ? array_sum(array_column($series, 'sessions')) : 0;
    $cnt = $pdo->prepare("SELECT SUM(status IN ('" . implode("','", H_ISSUE_ACTIVE) . "')) open, SUM(status IN ('FIXED','READY_FOR_REVIEW')) fixed, SUM(status = 'REJECTED') rejected, SUM(status = 'RESOLVED') resolved,
        SUM(type = 'CRASH') crash, SUM(type = 'ANR') anr, SUM(type = 'AD') ad, SUM(type = 'TASK') task FROM health_issues WHERE pkg = ?");
    $cnt->execute([$pkg]);
    $issues = h_q($pdo, 'SELECT i.*, a.name app, a.icon_url FROM health_issues i LEFT JOIN health_apps a ON a.pkg = i.pkg WHERE i.pkg = ? ORDER BY i.last_seen DESC LIMIT 200', [$pkg]);
    $runs = h_q($pdo, 'SELECT r.*, a.name app FROM health_runs r LEFT JOIN health_apps a ON a.pkg = r.pkg WHERE r.pkg = ? ORDER BY r.id DESC LIMIT 20', [$pkg]);
    $builds = h_q($pdo, 'SELECT b.*, a.name app, a.icon_url FROM health_builds b LEFT JOIN health_apps a ON a.pkg = b.pkg WHERE b.pkg = ? ORDER BY b.id DESC LIMIT 20', [$pkg]);
    $ver = h_q($pdo, "SELECT ver, COUNT(DISTINCT did) users FROM day_ver WHERE pkg = ? AND day >= ? GROUP BY ver ORDER BY users DESC LIMIT 6", [$pkg, (new DateTime(local_today(), tz()))->modify('-2 day')->format('Y-m-d')]);
    return ['app' => h_app_row($app) + ['console_name' => h_consoles_cfg()[$app['console_id']]['name'] ?? ''], 'days' => $days, 'from' => $from,
        'totals' => $tot + ['dau_avg' => round($dauAvg), 'crash_per_1k_sessions' => $sessions > 0 ? round($tot['crashes'] / $sessions * 1000, 2) : null,
            'uninstall_rate' => $tot['installs'] > 0 ? round($tot['uninstalls'] / $tot['installs'], 4) : null],
        'series' => $series, 'issue_counts' => array_map('intval', $cnt->fetch() ?: []), 'issues' => array_map('h_issue_row', $issues),
        'runs' => array_map('h_run_row', $runs), 'builds' => array_map('h_build_row', $builds), 'versions' => $ver, 'inr_per_usd' => (float)cfg('inr_per_usd')];
}
