<?php
/**
 * Read-only Google Ads Script (setup/gads_health_script.js) -> server. Har account ki script apna alag token bhejti hai.
 * Sirf registered (user ne app me authorize kiya) account ka data liya jaata hai; token galat = 403.
 *
 * POST JSON {customer_id, token, action: "hello" | "data", ...}
 *   hello -> {backfill_days, hourly_days}   (server ke paas history kam ho to zyada din maangta hai)
 *   data  -> account, campaigns (meta), days, hours, adgroups, geo, policy, assets, errors[]
 * Jo section script nahi padh payi (errors[] me) wo purana hi rehta hai: missing = zero nahi.
 */
declare(strict_types=1);
require_once __DIR__ . '/../lib/gads_health.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') json_out(['ok' => false, 'error' => 'POST only'], 405);
$b = json_decode((string)file_get_contents('php://input'), true);
if (!is_array($b)) json_out(['ok' => false, 'error' => 'bad json'], 400);
$pdo = db();
ga_schema($pdo);

$cid = ga_cid((string)($b['customer_id'] ?? ''));
$tok = (string)($_SERVER['HTTP_X_TOKEN'] ?? ($b['token'] ?? ''));
$st = $pdo->prepare('SELECT * FROM ga_accounts WHERE customer_id = ?');
$st->execute([$cid]);
$a = $st->fetch();
if (!$a || $tok === '' || (string)$a['token_hash'] === '' || !hash_equals((string)$a['token_hash'], hash('sha256', $tok))) {
    if ($a) ga_log($pdo, 'script data rejected for CID ' . ga_fmt_cid($cid) . ': wrong token', 'warn', ['cid' => $cid]);
    json_out(['ok' => false, 'error' => 'unknown account or wrong token (add the account in the MFC Dash app first)'], 403);
}
if ((int)$a['src_script'] !== 1) json_out(['ok' => false, 'error' => 'read-only script source is turned off for this account in the app'], 403);
if (!empty($b['account']['manager'])) {
    ga_log($pdo, 'script for CID ' . ga_fmt_cid($cid) . ' runs in a manager (MCC) account: rejected', 'warn', ['cid' => $cid]);
    json_out(['ok' => false, 'error' => 'install this script inside the individual advertiser account, not in an MCC'], 400);
}

$action = (string)($b['action'] ?? 'data');
if ($action === 'hello') {
    $days = (int)h_q1($pdo, 'SELECT COUNT(DISTINCT day) FROM ga_day WHERE customer_id = ? AND day >= DATE_SUB(UTC_DATE(), INTERVAL 35 DAY)', [$cid]);
    $hdays = (int)h_q1($pdo, 'SELECT COUNT(DISTINCT day) FROM ga_hour WHERE customer_id = ? AND day >= DATE_SUB(UTC_DATE(), INTERVAL 29 DAY)', [$cid]);
    json_out(['ok' => true, 'backfill_days' => $days < 25 ? 35 : 3, 'hourly_days' => $hdays < 20 ? 29 : 2, 'server_version' => GA_SCRIPT_VER]);
}

function gi_t($v, int $n): string { return is_scalar($v) ? mb_substr(trim((string)$v), 0, $n) : ''; }
function gi_n($v): float { return is_numeric($v) ? (float)$v : 0.0; }
function gi_day($v): ?string { return is_string($v) && preg_match('/^\d{4}-\d{2}-\d{2}$/', $v) ? $v : null; }

$now = gmdate('Y-m-d H:i:s');
$n = 0;
$errors = array_values(array_filter(array_map(fn($e) => is_array($e) ? ['section' => gi_t($e['section'] ?? '', 40), 'error' => gi_t($e['error'] ?? '', 400), 'at' => $now] : null, (array)($b['errors'] ?? []))));
try {
    $pdo->beginTransaction();
    $acc = (array)($b['account'] ?? []);
    $status = strtoupper(gi_t($acc['status'] ?? '', 20));
    $cur = strtoupper(gi_t($acc['currency'] ?? '', 3));
    $tz = gi_t($acc['tz'] ?? '', 64);
    if ($cur !== '' && $a['currency'] !== '' && $cur !== $a['currency']) ga_log($pdo, "CID " . ga_fmt_cid($cid) . ": Google reports currency $cur but the registry says {$a['currency']} — Google's value is used for this account", 'warn', ['cid' => $cid]);
    h_x($pdo, "UPDATE ga_accounts SET script_last_at = ?, script_version = ?, script_errors = ?,
        script_status = IF(? <> '', ?, script_status), script_status_at = IF(? <> '', ?, script_status_at),
        currency = IF(? <> '', ?, currency), tz = IF(? <> '', ?, tz), account_name = IF(account_name = '' AND ? <> '', ?, account_name) WHERE customer_id = ?",
        [$now, gi_t($b['script_version'] ?? '', 20), json_encode($errors), $status, $status, $status, $now, $cur, $cur, $tz, $tz, gi_t($acc['name'] ?? '', 255), gi_t($acc['name'] ?? '', 255), $cid]);

    if (isset($b['campaigns']) && is_array($b['campaigns'])) {
        $q = $pdo->prepare("INSERT INTO ga_camp (customer_id, campaign_id, name, status, primary_status, primary_reasons, serving_status, channel, bidding, target_cpa, target_roas, budget, budget_period, start_date, pkg, first_seen, meta_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE name = VALUES(name), primary_status = VALUES(primary_status), primary_reasons = VALUES(primary_reasons), serving_status = VALUES(serving_status),
              channel = VALUES(channel), bidding = VALUES(bidding), target_cpa = VALUES(target_cpa), target_roas = VALUES(target_roas), budget = VALUES(budget),
              budget_period = VALUES(budget_period), start_date = COALESCE(VALUES(start_date), start_date), pkg = IF(VALUES(pkg) <> '', VALUES(pkg), pkg), meta_at = VALUES(meta_at), updated_at = VALUES(updated_at)");
        // status yahan nahi likhte: scan status badalna khud detect karta hai (prev_status ke saath). Naya row = status ke saath.
        foreach ($b['campaigns'] as $c) {
            if (!is_array($c) || empty($c['campaign_id'])) continue;
            $tc = gi_n($c['target_cpa'] ?? 0); $tr = gi_n($c['target_roas'] ?? 0); $bu = $c['budget'] ?? null;
            $q->execute([$cid, gi_t($c['campaign_id'], 30), gi_t($c['name'] ?? '', 255), strtoupper(gi_t($c['status'] ?? '', 20)), strtoupper(gi_t($c['primary_status'] ?? '', 40)),
                gi_t(implode(',', array_map('strval', (array)($c['primary_reasons'] ?? []))), 500), strtoupper(gi_t($c['serving_status'] ?? '', 20)), gi_t($c['channel'] ?? '', 40),
                gi_t($c['bidding'] ?? '', 40), $tc > 0 ? $tc : null, $tr > 0 ? $tr : null, is_numeric($bu) ? (float)$bu : null, gi_t($c['budget_period'] ?? '', 20),
                gi_day($c['start_date'] ?? null), gi_t($c['pkg'] ?? '', 100), gi_day($c['start_date'] ?? null), $now, $now]);
            h_x($pdo, "UPDATE ga_camp SET status = IF(status = '', ?, status) WHERE customer_id = ? AND campaign_id = ?", [strtoupper(gi_t($c['status'] ?? '', 20)), $cid, gi_t($c['campaign_id'], 30)]);
            $n++;
        }
        // live status kv me: scan ise ga_camp.status (pichhla dekha hua) se compare karke paused / removed pakadta hai
        $ls = $pdo->prepare('REPLACE INTO kv (k, v, updated_at) VALUES (?, ?, UTC_TIMESTAMP())');
        $live = [];
        foreach ($b['campaigns'] as $c) if (is_array($c) && !empty($c['campaign_id'])) $live[gi_t($c['campaign_id'], 30)] = strtoupper(gi_t($c['status'] ?? '', 20));
        $ls->execute(['ga_live_status_' . $cid, json_encode($live)]);
    }
    $up = function (string $table, array $cols, array $rows, callable $map) use ($pdo, &$n, $cid, $now) {
        if (!$rows) return;
        $sql = "INSERT INTO $table (customer_id, " . implode(', ', $cols) . ', updated_at) VALUES ' ;
        $one = '(' . implode(',', array_fill(0, count($cols) + 2, '?')) . ')';
        $upd = implode(', ', array_map(fn($c) => "$c = VALUES($c)", $cols)) . ', updated_at = VALUES(updated_at)';
        foreach (array_chunk($rows, 300) as $chunk) {
            $vals = []; $ph = [];
            foreach ($chunk as $r) { if (!is_array($r) || ($m = $map($r)) === null) continue; $vals = array_merge($vals, [$cid], $m, [$now]); $ph[] = $one; }
            if (!$ph) continue;
            h_x($pdo, $sql . implode(',', $ph) . " ON DUPLICATE KEY UPDATE $upd", $vals);
            $n += count($ph);
        }
    };
    $up('ga_day', ['day', 'campaign_id', 'cost', 'impressions', 'clicks', 'conversions', 'conv_value'], (array)($b['days'] ?? []), fn($r) => ($d = gi_day($r['day'] ?? null)) && !empty($r['campaign_id'])
        ? [$d, gi_t($r['campaign_id'], 30), gi_n($r['cost'] ?? 0), (int)gi_n($r['impressions'] ?? 0), (int)gi_n($r['clicks'] ?? 0), gi_n($r['conversions'] ?? 0), gi_n($r['conv_value'] ?? 0)] : null);
    $up('ga_hour', ['day', 'hour', 'campaign_id', 'cost', 'impressions', 'clicks', 'conversions'], (array)($b['hours'] ?? []), fn($r) => ($d = gi_day($r['day'] ?? null)) && !empty($r['campaign_id']) && ($h = (int)gi_n($r['hour'] ?? -1)) >= 0 && $h <= 23
        ? [$d, $h, gi_t($r['campaign_id'], 30), gi_n($r['cost'] ?? 0), (int)gi_n($r['impressions'] ?? 0), (int)gi_n($r['clicks'] ?? 0), gi_n($r['conversions'] ?? 0)] : null);
    $up('ga_adgroup_day', ['day', 'campaign_id', 'ad_group_id', 'name', 'status', 'cost', 'impressions', 'clicks', 'conversions', 'conv_value'], (array)($b['adgroups'] ?? []),
        fn($r) => ($d = gi_day($r['day'] ?? null)) && !empty($r['ad_group_id']) ? [$d, gi_t($r['campaign_id'] ?? '', 30), gi_t($r['ad_group_id'], 30), gi_t($r['name'] ?? '', 255),
            strtoupper(gi_t($r['status'] ?? '', 20)), gi_n($r['cost'] ?? 0), (int)gi_n($r['impressions'] ?? 0), (int)gi_n($r['clicks'] ?? 0), gi_n($r['conversions'] ?? 0), gi_n($r['conv_value'] ?? 0)] : null);
    $up('ga_geo_day', ['day', 'campaign_id', 'country', 'cost', 'impressions', 'clicks', 'conversions', 'conv_value'], (array)($b['geo'] ?? []),
        fn($r) => ($d = gi_day($r['day'] ?? null)) && !empty($r['campaign_id']) && preg_match('/^[A-Z]{2}$/', strtoupper((string)($r['country'] ?? ''))) ? [$d, gi_t($r['campaign_id'], 30),
            strtoupper((string)$r['country']), gi_n($r['cost'] ?? 0), (int)gi_n($r['impressions'] ?? 0), (int)gi_n($r['clicks'] ?? 0), gi_n($r['conversions'] ?? 0), gi_n($r['conv_value'] ?? 0)] : null);
    if (isset($b['policy']) && is_array($b['policy'])) {
        // snapshot: is account ka purana hatao, naya daalo (sirf jab script ne policy padh li)
        h_x($pdo, 'DELETE FROM ga_policy WHERE customer_id = ?', [$cid]);
        $up('ga_policy', ['ad_id', 'campaign_id', 'campaign', 'ad_group_id', 'approval', 'review', 'topics'], $b['policy'], fn($r) => !empty($r['ad_id']) ? [gi_t($r['ad_id'], 30),
            gi_t($r['campaign_id'] ?? '', 30), gi_t($r['campaign'] ?? '', 255), gi_t($r['ad_group_id'] ?? '', 30), strtoupper(gi_t($r['approval'] ?? '', 30)), strtoupper(gi_t($r['review'] ?? '', 30)),
            gi_t(implode('|', array_map('strval', (array)($r['topics'] ?? []))), 1000)] : null);
        h_x($pdo, 'UPDATE ga_accounts SET policy_at = ? WHERE customer_id = ?', [$now, $cid]);
    }
    if (isset($b['assets']) && is_array($b['assets'])) {
        h_x($pdo, 'DELETE FROM ga_asset WHERE customer_id = ?', [$cid]);
        $up('ga_asset', ['campaign_id', 'asset_id', 'field_type', 'campaign', 'asset_type', 'label', 'name', 'url', 'impressions', 'clicks', 'cost', 'conversions'], $b['assets'],
            fn($r) => !empty($r['asset_id']) && !empty($r['campaign_id']) ? [gi_t($r['campaign_id'], 30), gi_t($r['asset_id'], 30), gi_t($r['field_type'] ?? '', 30), gi_t($r['campaign'] ?? '', 255),
                gi_t($r['asset_type'] ?? '', 30), gi_t($r['label'] ?? '', 20), gi_t($r['name'] ?? '', 500), gi_t($r['url'] ?? '', 500), (int)gi_n($r['impressions'] ?? 0), (int)gi_n($r['clicks'] ?? 0),
                gi_n($r['cost'] ?? 0), gi_n($r['conversions'] ?? 0)] : null);
    }
    $pdo->commit();
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    error_log('mfc gads ingest: ' . $e->getMessage());
    ga_log($pdo, 'script data for CID ' . ga_fmt_cid($cid) . ' could not be saved: ' . $e->getMessage(), 'error', ['cid' => $cid]);
    json_out(['ok' => false, 'error' => 'db'], 500);
}
ga_log($pdo, 'read-only script data received: CID ' . ga_fmt_cid($cid) . " · $n rows" . ($errors ? ' · ' . count($errors) . ' section error(s): ' . implode(', ', array_column($errors, 'section')) : ''), $errors ? 'warn' : 'info', ['cid' => $cid]);
json_out(['ok' => true, 'n' => $n, 'errors_recorded' => count($errors)]);
