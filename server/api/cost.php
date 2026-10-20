<?php
/**
 * MFC Dash app — "AI Cost & Usage Manager" ki API (sirf app ke liye; web page nahi).
 * Upload: mfc_dash/api/cost.php + mfc_dash/lib/cost.php. Baaki modules (alerts.php, lib/health.php, lib/gads_health.php) har paid call
 * ka hisaab lib/cost.php me likhte hain aur usi se budget poochte hain.
 *
 * Cron (har 5 min, health cron ke saath apne aap bhi chalta hai):  php mfc_dash/api/cost.php cron   ya URL  api/cost.php?cron=1&key=CRON_KEY
 *
 * App (login cookie):
 *   GET  ?range=today|7d|month               -> aaj / 7 din / mahina, budget, forecast, modules, apps, tasks, billed vs estimate, paused jobs, settings
 *   POST ?action=settings   JSON {total, reserve_pct, actions_usd_min, warn_email, modules:{health:{budget,cap,fix_cap}, ...}, fixed:[{name, usd}]}
 *        ?action=keys       {admin_key?, gh_token?, gh_user?}   (khali string = hatao; field na bhejo = waisa hi)
 *        ?action=resume     {module, hours}    budget khatam hone par bhi non-critical kaam itne ghante chalne do (aapka OK)
 *        ?action=pause_now  {module}           override hatao (turant wapas budget ke niyam)
 *        ?action=breaker    {module}           circuit breaker reset
 *        ?action=sync                          Anthropic / GitHub billed numbers abhi lao
 */
declare(strict_types=1);
require_once __DIR__ . '/../lib/cost.php';

$isCli = PHP_SAPI === 'cli';
$pdo = db();
cost_schema($pdo);

if (($isCli && in_array('cron', $argv ?? [], true)) || isset($_GET['cron'])) {
    require_cron();
    @set_time_limit(120);
    echo json_encode(cost_tick($pdo)) . "\n";
    exit;
}

require_login_json();
$action = (string)($_GET['action'] ?? '');

if (($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
    $in = json_decode((string)file_get_contents('php://input'), true);
    if (!is_array($in)) {
        $in = $_POST;
        if (isset($in['json'])) $in = json_decode((string)$in['json'], true) ?: [];
    }
    $mod = (string)($in['module'] ?? '');
    try {
        switch ($action) {
            case 'settings':
                $s = cost_save_settings($in);
                cost_warn_check($pdo);
                json_out(['ok' => true, 'settings' => $s]);
            case 'keys':
                foreach (['admin_key' => 'cost_admin_key', 'gh_token' => 'cost_gh_token', 'gh_user' => 'cost_gh_user'] as $f => $k) {
                    if (!array_key_exists($f, $in)) continue;
                    $v = trim((string)$in[$f]);
                    if ($f === 'admin_key' && $v !== '' && !str_starts_with($v, 'sk-ant-admin')) throw new RuntimeException('This is not an Admin API key (it starts with sk-ant-admin).');
                    if ($f === 'gh_user' && $v !== '' && !preg_match('/^[A-Za-z0-9-]{1,39}$/', $v)) throw new RuntimeException('bad GitHub username');
                    if ($v === '') $pdo->prepare('DELETE FROM kv WHERE k = ?')->execute([$k]); else kv_set($k, $v);
                }
                $r = cost_sync_billed($pdo, true);
                json_out(['ok' => true, 'keys' => cost_keys_status(), 'sync' => $r]);
            case 'resume':
                if (!isset(COST_MODULES[$mod])) throw new RuntimeException('bad module');
                $h = max(1, min(72, (int)($in['hours'] ?? 24)));
                kv_set("cost_override_$mod", (string)(time() + $h * 3600));
                json_out(['ok' => true, 'until' => time() + $h * 3600]);
            case 'pause_now':
                if (!isset(COST_MODULES[$mod])) throw new RuntimeException('bad module');
                kv_set("cost_override_$mod", '0');
                json_out(['ok' => true]);
            case 'breaker':
                if (!isset(COST_MODULES[$mod])) throw new RuntimeException('bad module');
                kv_set("cost_brk_until_$mod", '0'); kv_set("cost_brk_fails_$mod", '0');
                json_out(['ok' => true]);
            case 'sync':
                json_out(['ok' => true, 'sync' => cost_sync_billed($pdo, true), 'keys' => cost_keys_status()]);
            default:
                json_out(['ok' => false, 'error' => 'unknown action'], 400);
        }
    } catch (Throwable $e) {
        json_out(['ok' => false, 'error' => $e->getMessage()], 400);
    }
}

// GET: summary. Warnings bhi yahin check (cron na ho tab bhi app khulte hi).
if (time() - (int)(kv_get('cost_warn_last') ?? 0) > 300) {
    kv_set('cost_warn_last', (string)time());
    try { cost_warn_check($pdo); } catch (Throwable $e) { error_log('mfc cost warn: ' . $e->getMessage()); }
}
$range = in_array($_GET['range'] ?? '', ['today', '7d', 'month'], true) ? (string)$_GET['range'] : 'month';
json_out(cost_summary($pdo, $range));
