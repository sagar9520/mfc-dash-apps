<?php
/**
 * Google Ads AI Health Monitor — server engine (Dash app ka module isi se data leta hai). SIRF MONITORING.
 *
 *  - Google Ads API ka koi use nahi (production access rejected). Data sources:
 *      1. dashboard ka maujooda data: gads_campaign_day / gads_campaign_hour / gads_asset / gads_account (purani Google Ads Script, api/gads.php)
 *      2. optional read-only script (setup/gads_health_script.js) jo har account me alag lagti hai, apne token ke saath -> api/gads_ingest.php -> ga_* tables
 *      3. optional Google Ads notification emails (alag mailbox, IMAP, sirf padhna) -> ga_emails
 *  - Har account alag: company, Customer ID, MCC label, invoice / credit line ref, currency, timezone, sources, alert settings, history.
 *    Accounts kabhi link / combine nahi hote; alag-alag currencies ka koi jod nahi banta.
 *  - Koi bhi code Google Ads me kuch nahi badalta: koi mutate / pause / enable / remove / create / update nahi. Apply button nahi.
 *  - Scan har 15 min (cron har 5 min), email intake har 5 min, AI deep analysis har 6 ghante (sirf actionable alerts), daily review roz.
 *  - Har scan ka audit record (ga_runs + ga_events). Data fetch fail / stale = PARTIAL ya FAILED, kabhi SUCCESS nahi.
 */
declare(strict_types=1);
require_once __DIR__ . '/common.php';
require_once __DIR__ . '/health.php';      // h_q / h_x / h_mail_send / h_ai_call / h_email_on reuse (AI App Health module)
require_once __DIR__ . '/gads_rules.php';

const GA_SCAN_EVERY  = 900;      // 15 min
const GA_MAIL_EVERY  = 300;      // 5 min
const GA_DEEP_EVERY  = 21600;    // 6 ghante
const GA_DAILY_HOUR  = 9;        // daily portfolio review (dashboard time)
const GA_AI_CAP      = 40;       // Claude calls / din (kv ga_ai_cap se badlo)
const GA_SCRIPT_VER  = '1.0';
const GA_LEVELS = ['INFO' => 1, 'WARNING' => 2, 'CRITICAL' => 3];
const GA_STATUS_RULES = ['suspended', 'payment_restricted', 'payment_notice', 'policy_restriction', 'account_closed', 'disapproved', 'limited', 'inactive'];
const GA_SETTINGS = ['ga_push' => '1', 'ga_mail_critical' => '1', 'ga_mail_daily' => '1', 'ga_daily_hour' => '9', 'ga_dev_mode' => '0', 'ga_ai_cap' => '40', 'ga_ai_on' => '1'];

/** mfc_dash/gads_config.php (IMAP mailbox ke secrets; git me nahi). Na ho to email intake band, baaki sab chalta hai. */
function gcfg(?string $k = null)
{
    static $c = null;
    if ($c === null) {
        $f = MFC_ROOT . '/gads_config.php';
        $c = is_file($f) ? (require $f) : [];
        if (!is_array($c)) $c = [];
    }
    return $k === null ? $c : ($c[$k] ?? null);
}

function ga_set(string $k): string { return (string)(kv_get($k) ?? (GA_SETTINGS[$k] ?? '')); }
function ga_cid(string $s): string { return preg_replace('/[^0-9]/', '', $s); }
function ga_fmt_cid(string $c): string { return gr_cid($c); }

// ================================================================ schema
function ga_schema(PDO $pdo): void
{
    static $done = false;
    if ($done) return;
    $done = true;
    ensure_gads_account($pdo);
    if ((kv_get('ga_schema_v') ?? '') === '1') return;
    $T = ' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4';
    $pdo->exec("CREATE TABLE IF NOT EXISTS ga_accounts (
  customer_id VARCHAR(20) NOT NULL PRIMARY KEY, company VARCHAR(160) NOT NULL, account_name VARCHAR(255) NOT NULL DEFAULT '',
  mcc_label VARCHAR(120) NOT NULL DEFAULT '', invoice_ref VARCHAR(120) NOT NULL DEFAULT '', credit_ref VARCHAR(120) NOT NULL DEFAULT '',
  currency CHAR(3) NOT NULL DEFAULT '', tz VARCHAR(64) NOT NULL DEFAULT '', notes VARCHAR(1000) NOT NULL DEFAULT '',
  src_dashboard TINYINT NOT NULL DEFAULT 1, src_script TINYINT NOT NULL DEFAULT 0, src_email TINYINT NOT NULL DEFAULT 0,
  monitoring TINYINT NOT NULL DEFAULT 1, push TINYINT NOT NULL DEFAULT 1, email TINYINT NOT NULL DEFAULT 1,
  token_hash CHAR(64) NOT NULL DEFAULT '', token_hint VARCHAR(8) NOT NULL DEFAULT '',
  authorized_by VARCHAR(60) NOT NULL DEFAULT '', authorized_at DATETIME NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
  script_last_at DATETIME NULL, script_version VARCHAR(20) NOT NULL DEFAULT '', script_status VARCHAR(20) NOT NULL DEFAULT '', script_status_at DATETIME NULL,
  script_errors TEXT NULL, policy_at DATETIME NULL,
  last_scan_at DATETIME NULL, last_state VARCHAR(20) NOT NULL DEFAULT '', health VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN', last_checks TEXT NULL,
  is_dev TINYINT NOT NULL DEFAULT 0)$T");
    $pdo->exec("CREATE TABLE IF NOT EXISTS ga_thresholds (scope VARCHAR(80) NOT NULL, k VARCHAR(30) NOT NULL, v VARCHAR(30) NOT NULL, updated_at DATETIME NOT NULL, PRIMARY KEY (scope, k))$T");
    $pdo->exec("CREATE TABLE IF NOT EXISTS ga_camp (
  customer_id VARCHAR(20) NOT NULL, campaign_id VARCHAR(30) NOT NULL, name VARCHAR(255) NOT NULL DEFAULT '', status VARCHAR(20) NOT NULL DEFAULT '',
  prev_status VARCHAR(20) NOT NULL DEFAULT '', status_changed_at DATETIME NULL, primary_status VARCHAR(40) NOT NULL DEFAULT '', primary_reasons VARCHAR(500) NOT NULL DEFAULT '',
  serving_status VARCHAR(20) NOT NULL DEFAULT '', channel VARCHAR(40) NOT NULL DEFAULT '', bidding VARCHAR(40) NOT NULL DEFAULT '',
  target_cpa DECIMAL(16,4) NULL, target_roas DECIMAL(10,4) NULL, budget DECIMAL(16,4) NULL, budget_period VARCHAR(20) NOT NULL DEFAULT '', start_date DATE NULL,
  pkg VARCHAR(100) NOT NULL DEFAULT '', first_seen DATE NULL, meta_at DATETIME NULL, last_checks TEXT NULL, updated_at DATETIME NOT NULL,
  PRIMARY KEY (customer_id, campaign_id))$T");
    $pdo->exec("CREATE TABLE IF NOT EXISTS ga_day (
  customer_id VARCHAR(20) NOT NULL, day DATE NOT NULL, campaign_id VARCHAR(30) NOT NULL, cost DECIMAL(16,4) NOT NULL DEFAULT 0, impressions BIGINT NOT NULL DEFAULT 0,
  clicks BIGINT NOT NULL DEFAULT 0, conversions DECIMAL(14,2) NOT NULL DEFAULT 0, conv_value DECIMAL(16,4) NOT NULL DEFAULT 0, updated_at DATETIME NOT NULL,
  PRIMARY KEY (customer_id, day, campaign_id))$T");
    $pdo->exec("CREATE TABLE IF NOT EXISTS ga_hour (
  customer_id VARCHAR(20) NOT NULL, day DATE NOT NULL, hour TINYINT NOT NULL, campaign_id VARCHAR(30) NOT NULL, cost DECIMAL(16,4) NOT NULL DEFAULT 0,
  impressions BIGINT NOT NULL DEFAULT 0, clicks BIGINT NOT NULL DEFAULT 0, conversions DECIMAL(14,2) NOT NULL DEFAULT 0, updated_at DATETIME NOT NULL,
  PRIMARY KEY (customer_id, day, hour, campaign_id))$T");
    $pdo->exec("CREATE TABLE IF NOT EXISTS ga_adgroup_day (
  customer_id VARCHAR(20) NOT NULL, day DATE NOT NULL, campaign_id VARCHAR(30) NOT NULL, ad_group_id VARCHAR(30) NOT NULL, name VARCHAR(255) NOT NULL DEFAULT '',
  status VARCHAR(20) NOT NULL DEFAULT '', cost DECIMAL(16,4) NOT NULL DEFAULT 0, impressions BIGINT NOT NULL DEFAULT 0, clicks BIGINT NOT NULL DEFAULT 0,
  conversions DECIMAL(14,2) NOT NULL DEFAULT 0, conv_value DECIMAL(16,4) NOT NULL DEFAULT 0, updated_at DATETIME NOT NULL,
  PRIMARY KEY (customer_id, day, ad_group_id), KEY k_camp (customer_id, campaign_id, day))$T");
    $pdo->exec("CREATE TABLE IF NOT EXISTS ga_geo_day (
  customer_id VARCHAR(20) NOT NULL, day DATE NOT NULL, campaign_id VARCHAR(30) NOT NULL, country CHAR(2) NOT NULL, cost DECIMAL(16,4) NOT NULL DEFAULT 0,
  impressions BIGINT NOT NULL DEFAULT 0, clicks BIGINT NOT NULL DEFAULT 0, conversions DECIMAL(14,2) NOT NULL DEFAULT 0, conv_value DECIMAL(16,4) NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL, PRIMARY KEY (customer_id, day, campaign_id, country))$T");
    $pdo->exec("CREATE TABLE IF NOT EXISTS ga_policy (
  customer_id VARCHAR(20) NOT NULL, ad_id VARCHAR(30) NOT NULL, campaign_id VARCHAR(30) NOT NULL, campaign VARCHAR(255) NOT NULL DEFAULT '', ad_group_id VARCHAR(30) NOT NULL DEFAULT '',
  approval VARCHAR(30) NOT NULL, review VARCHAR(30) NOT NULL DEFAULT '', topics VARCHAR(1000) NOT NULL DEFAULT '', updated_at DATETIME NOT NULL,
  PRIMARY KEY (customer_id, ad_id))$T");
    $pdo->exec("CREATE TABLE IF NOT EXISTS ga_asset (
  customer_id VARCHAR(20) NOT NULL, campaign_id VARCHAR(30) NOT NULL, asset_id VARCHAR(30) NOT NULL, field_type VARCHAR(30) NOT NULL DEFAULT '',
  campaign VARCHAR(255) NOT NULL DEFAULT '', asset_type VARCHAR(30) NOT NULL DEFAULT '', label VARCHAR(20) NOT NULL DEFAULT '', name VARCHAR(500) NOT NULL DEFAULT '',
  url VARCHAR(500) NOT NULL DEFAULT '', impressions BIGINT NOT NULL DEFAULT 0, clicks BIGINT NOT NULL DEFAULT 0, cost DECIMAL(16,4) NOT NULL DEFAULT 0,
  conversions DECIMAL(14,2) NOT NULL DEFAULT 0, updated_at DATETIME NOT NULL, PRIMARY KEY (customer_id, campaign_id, asset_id, field_type))$T");
    $pdo->exec("CREATE TABLE IF NOT EXISTS ga_emails (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, msg_id VARCHAR(190) NOT NULL, received_at DATETIME NOT NULL, from_addr VARCHAR(190) NOT NULL DEFAULT '',
  subject VARCHAR(500) NOT NULL DEFAULT '', customer_id VARCHAR(20) NOT NULL DEFAULT '', kind VARCHAR(30) NOT NULL DEFAULT 'OTHER', verified TINYINT NOT NULL DEFAULT 0,
  verify_note VARCHAR(255) NOT NULL DEFAULT '', reason VARCHAR(500) NOT NULL DEFAULT '', snippet VARCHAR(2000) NOT NULL DEFAULT '', sim TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL, UNIQUE KEY u_msg (msg_id), KEY k_cid (customer_id, received_at))$T");
    $pdo->exec("CREATE TABLE IF NOT EXISTS ga_alerts (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, akey VARCHAR(190) NOT NULL, customer_id VARCHAR(20) NOT NULL, campaign_id VARCHAR(30) NOT NULL DEFAULT '',
  campaign VARCHAR(255) NOT NULL DEFAULT '', ad_group_id VARCHAR(30) NOT NULL DEFAULT '', country CHAR(2) NOT NULL DEFAULT '', rule VARCHAR(40) NOT NULL,
  level VARCHAR(10) NOT NULL, klass VARCHAR(15) NOT NULL, title VARCHAR(255) NOT NULL, title_hi VARCHAR(255) NOT NULL DEFAULT '', what TEXT NULL, what_hi TEXT NULL,
  metrics TEXT NULL, causes TEXT NULL, next_step VARCHAR(1000) NOT NULL DEFAULT '', next_hi VARCHAR(1000) NOT NULL DEFAULT '', impact VARCHAR(300) NOT NULL DEFAULT '',
  evidence VARCHAR(300) NOT NULL DEFAULT '', freshness_min INT NULL, first_seen DATETIME NOT NULL, last_seen DATETIME NOT NULL, resolved_at DATETIME NULL,
  resolved_by VARCHAR(60) NOT NULL DEFAULT '', ack_at DATETIME NULL, ack_by VARCHAR(60) NOT NULL DEFAULT '', notified_level VARCHAR(10) NOT NULL DEFAULT '', notified_at DATETIME NULL,
  ai MEDIUMTEXT NULL, ai_conf INT NULL, ai_at DATETIME NULL, ai_err VARCHAR(300) NOT NULL DEFAULT '', sim TINYINT NOT NULL DEFAULT 0, run_id INT UNSIGNED NULL,
  KEY k_akey (akey, resolved_at), KEY k_cid (customer_id, resolved_at), KEY k_open (resolved_at, level), KEY k_seen (last_seen))$T");
    $pdo->exec("CREATE TABLE IF NOT EXISTS ga_runs (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, run_uid VARCHAR(40) NOT NULL, type VARCHAR(20) NOT NULL, trigger_type VARCHAR(20) NOT NULL DEFAULT 'scheduled',
  triggered_by VARCHAR(60) NOT NULL DEFAULT '', status VARCHAR(20) NOT NULL, started_at DATETIME(3) NOT NULL, finished_at DATETIME(3) NULL,
  accounts_checked INT NOT NULL DEFAULT 0, campaigns_checked INT NOT NULL DEFAULT 0, sources TEXT NULL, issues INT NOT NULL DEFAULT 0, recs INT NOT NULL DEFAULT 0,
  failed_checks INT NOT NULL DEFAULT 0, errors TEXT NULL, summary MEDIUMTEXT NULL, next_run_at DATETIME NULL, sim TINYINT NOT NULL DEFAULT 0,
  KEY k_started (started_at), KEY k_type (type, started_at))$T");
    $pdo->exec("CREATE TABLE IF NOT EXISTS ga_events (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, run_id INT UNSIGNED NULL, customer_id VARCHAR(20) NOT NULL DEFAULT '', alert_id INT UNSIGNED NULL,
  level VARCHAR(10) NOT NULL DEFAULT 'info', msg VARCHAR(1000) NOT NULL, created_at DATETIME(3) NOT NULL, KEY k_created (created_at), KEY k_run (run_id))$T");
    kv_set('ga_schema_v', '1');
}

// ================================================================ events / runs
function ga_log(PDO $pdo, string $msg, string $level = 'info', array $ref = []): void
{
    try {
        h_x($pdo, 'INSERT INTO ga_events (run_id, customer_id, alert_id, level, msg, created_at) VALUES (?,?,?,?,?,UTC_TIMESTAMP(3))',
            [$ref['run'] ?? null, (string)($ref['cid'] ?? ''), $ref['alert'] ?? null, $level, h_cut($msg, 1000)]);
    } catch (Throwable $e) { error_log('mfc gads log: ' . $e->getMessage()); }
}

function ga_run_new(PDO $pdo, string $type, string $trigger = 'scheduled', string $by = '', bool $sim = false): array
{
    $uid = 'GA-' . h_local()->format('Ymd-His') . '-' . substr(bin2hex(random_bytes(2)), 0, 4);
    h_x($pdo, "INSERT INTO ga_runs (run_uid, type, trigger_type, triggered_by, status, started_at, sim) VALUES (?,?,?,?, 'RUNNING', UTC_TIMESTAMP(3), ?)",
        [$uid, $type, $trigger, h_cut($by, 60), $sim ? 1 : 0]);
    $id = (int)$pdo->lastInsertId();
    ga_log($pdo, "$uid started · $type · $trigger" . ($by !== '' ? " · $by" : ''), 'info', ['run' => $id]);
    return ['id' => $id, 'uid' => $uid];
}

function ga_run_end(PDO $pdo, array $run, string $status, array $f = []): void
{
    $f['status'] = $status;
    $sets = []; $p = [];
    foreach ($f as $k => $v) { $sets[] = "`$k` = ?"; $p[] = is_array($v) ? h_json($v) : $v; }
    $p[] = $run['id'];
    h_x($pdo, 'UPDATE ga_runs SET ' . implode(', ', $sets) . ', finished_at = UTC_TIMESTAMP(3) WHERE id = ?', $p);
    ga_log($pdo, "{$run['uid']} finished · $status" . (isset($f['campaigns_checked']) ? ' · ' . ($f['accounts_checked'] ?? 0) . ' accounts · ' . $f['campaigns_checked'] . ' campaigns · ' . ($f['issues'] ?? 0) . ' issues · ' . ($f['failed_checks'] ?? 0) . ' failed checks' : (isset($f['accounts_checked']) ? " · {$f['accounts_checked']} accounts" : '')),
        in_array($status, ['FAILED'], true) ? 'error' : ($status === 'PARTIAL' ? 'warn' : 'info'), ['run' => $run['id']]);
}

// ================================================================ accounts + thresholds
function ga_accounts(PDO $pdo, bool $monitoredOnly = false): array
{
    $rows = h_q($pdo, 'SELECT * FROM ga_accounts' . ($monitoredOnly ? ' WHERE monitoring = 1' : '') . ' ORDER BY company, customer_id');
    $out = [];
    foreach ($rows as $r) $out[$r['customer_id']] = $r;
    return $out;
}

function ga_acct_tz(PDO $pdo, array $a): DateTimeZone
{
    $tz = (string)$a['tz'];
    if ($tz === '') $tz = (string)(h_q1($pdo, 'SELECT tz FROM gads_account WHERE customer_id = ?', [$a['customer_id']]) ?? '');
    try { return new DateTimeZone($tz !== '' ? $tz : (string)cfg('timezone')); } catch (Throwable $e) { return tz(); }
}

/** Har source ki aakhri receipt (UTC) + umar (minute). Sirf enabled sources. */
function ga_sources(PDO $pdo, array $a): array
{
    $now = time(); $out = [];
    $age = fn(?string $ts) => $ts ? (int)floor(($now - strtotime($ts . ' UTC')) / 60) : null;
    if ((int)$a['src_dashboard'] === 1) {
        $u = h_q1($pdo, 'SELECT updated_at FROM gads_account WHERE customer_id = ?', [$a['customer_id']]);
        $out['dashboard'] = ['at' => $u, 'age_min' => $age($u ? (string)$u : null)];
    }
    if ((int)$a['src_script'] === 1) $out['script'] = ['at' => $a['script_last_at'], 'age_min' => $age($a['script_last_at']), 'version' => $a['script_version']];
    if ((int)$a['src_email'] === 1) {
        $ok = kv_get('ga_mail_ok_at');
        $out['email'] = ['at' => $ok ? gmdate('Y-m-d H:i:s', (int)$ok) : null, 'age_min' => $ok ? (int)floor(($now - (int)$ok) / 60) : null, 'configured' => ga_imap_on()];
    }
    return $out;
}

/** Metric data ki sabse nayi receipt (dashboard ya script). null = kabhi nahi aaya. */
function ga_data_age(array $sources): ?int
{
    $ages = [];
    foreach (['dashboard', 'script'] as $s) if (isset($sources[$s]) && $sources[$s]['age_min'] !== null) $ages[] = $sources[$s]['age_min'];
    return $ages ? min($ages) : null;
}

function ga_th_all(PDO $pdo): array
{
    static $c = null;
    if ($c === null) {
        $c = [];
        foreach (h_q($pdo, 'SELECT scope, k, v FROM ga_thresholds') as $r) $c[$r['scope']][$r['k']] = $r['v'];
    }
    return $c;
}

/** Threshold chain: global -> account -> campaign (sabse specific jeet-ta hai). */
function ga_th(PDO $pdo, string $cid, string $camp = ''): array
{
    $all = ga_th_all($pdo);
    $th = GR_DEFAULTS;
    foreach (['global', "acct:$cid", $camp !== '' ? "camp:$cid:$camp" : null] as $s) if ($s !== null && isset($all[$s])) foreach ($all[$s] as $k => $v) $th[$k] = $v;
    return $th;
}

// ================================================================ data loading (dashboard + read-only script, dono ka merge)
/** [campaign_id => ['name','status','pkg','days' => [day => row]]]. Same (day, campaign) dono me ho to jo baad me update hua. */
function ga_daily(PDO $pdo, string $cid, string $from, string $to): array
{
    $out = []; $upd = [];
    foreach (h_q($pdo, 'SELECT day, campaign_id, campaign, pkg, status, cost, impressions, clicks, conversions, conv_value, updated_at FROM gads_campaign_day WHERE customer_id = ? AND day BETWEEN ? AND ?', [$cid, $from, $to]) as $r) {
        $c = (string)$r['campaign_id'];
        $out[$c]['name'] = $out[$c]['name'] ?? $r['campaign'];
        if (!isset($out[$c]['last']) || $r['day'] > $out[$c]['last']) { $out[$c]['last'] = $r['day']; $out[$c]['status'] = $r['status']; $out[$c]['name'] = $r['campaign']; }
        $out[$c]['pkg'] = $r['pkg'] ?: ($out[$c]['pkg'] ?? '');
        $out[$c]['days'][$r['day']] = ['cost' => (float)$r['cost'], 'impressions' => (float)$r['impressions'], 'clicks' => (float)$r['clicks'], 'conversions' => (float)$r['conversions'], 'conv_value' => (float)$r['conv_value']];
        $upd["$c|{$r['day']}"] = $r['updated_at'];
    }
    foreach (h_q($pdo, 'SELECT day, campaign_id, cost, impressions, clicks, conversions, conv_value, updated_at FROM ga_day WHERE customer_id = ? AND day BETWEEN ? AND ?', [$cid, $from, $to]) as $r) {
        $c = (string)$r['campaign_id'];
        $k = "$c|{$r['day']}";
        if (isset($upd[$k]) && $upd[$k] > $r['updated_at']) continue;
        $out[$c]['days'][$r['day']] = ['cost' => (float)$r['cost'], 'impressions' => (float)$r['impressions'], 'clicks' => (float)$r['clicks'], 'conversions' => (float)$r['conversions'], 'conv_value' => (float)$r['conv_value']];
    }
    foreach ($out as $c => &$o) { $o += ['name' => '', 'status' => '', 'pkg' => '', 'days' => []]; ksort($o['days']); }
    return $out;
}

/** [day => [campaign_id => ['c' => [h => cost], 'i' => [h => impr], 'v' => [h => conv]]]]. Ek din: script ka hourly ho to wahi, warna dashboard ka. */
function ga_hourly(PDO $pdo, string $cid, array $days): array
{
    if (!$days) return [];
    $in = implode(',', array_fill(0, count($days), '?'));
    $out = [];
    foreach (h_q($pdo, "SELECT day, hour, campaign_id, cost, impressions, conversions FROM ga_hour WHERE customer_id = ? AND day IN ($in)", array_merge([$cid], $days)) as $r) {
        $out[$r['day']][$r['campaign_id']]['c'][(int)$r['hour']] = (float)$r['cost'];
        $out[$r['day']][$r['campaign_id']]['i'][(int)$r['hour']] = (float)$r['impressions'];
        $out[$r['day']][$r['campaign_id']]['v'][(int)$r['hour']] = (float)$r['conversions'];
    }
    $have = array_keys($out);
    foreach (h_q($pdo, "SELECT day, hour, campaign_id, cost, impressions, conversions FROM gads_campaign_hour WHERE customer_id = ? AND day IN ($in)", array_merge([$cid], $days)) as $r) {
        if (in_array($r['day'], $have, true)) continue;
        $out[$r['day']][$r['campaign_id']]['c'][(int)$r['hour']] = (float)$r['cost'];
        $out[$r['day']][$r['campaign_id']]['i'][(int)$r['hour']] = (float)$r['impressions'];
        $out[$r['day']][$r['campaign_id']]['v'][(int)$r['hour']] = (float)$r['conversions'];
    }
    return $out;
}

function ga_days_list(string $today, array $offsets): array { $d = new DateTimeImmutable($today); return array_map(fn($o) => $d->modify("-$o day")->format('Y-m-d'), $offsets); }

/**
 * Ek account ka poora snapshot (rules aur monitor views dono isi se): local time, R (reported-through hour), daily, hourly, baseline days,
 * campaign meta, sources, freshness.
 */
function ga_snapshot(PDO $pdo, array $a): array
{
    $cid = (string)$a['customer_id'];
    $tz = ga_acct_tz($pdo, $a);
    $nowL = new DateTime('now', $tz);
    $today = $nowL->format('Y-m-d');
    $th = ga_th($pdo, $cid);
    $sources = ga_sources($pdo, $a);
    $age = ga_data_age($sources);
    $fresh = $age !== null && $age <= (float)gr_th($th, 'stale_hours') * 60;
    // R = aakhri reported ghanta (account time): aakhri receipt ka ghanta - spend lag
    $R = -1;
    $lastAt = null;
    foreach (['dashboard', 'script'] as $s) if (!empty($sources[$s]['at']) && ($lastAt === null || $sources[$s]['at'] > $lastAt)) $lastAt = (string)$sources[$s]['at'];
    if ($lastAt !== null) {
        $l = new DateTime($lastAt, new DateTimeZone('UTC'));
        $l->setTimezone($tz);
        if ($l->format('Y-m-d') === $today) $R = (int)$l->format('G') - (int)gr_th($th, 'spend_lag_hours');
    }
    $daily = ga_daily($pdo, $cid, ga_days_list($today, [35])[0], $today);
    $hDays = ga_days_list($today, [0, 1, 2, 3, 4, 5, 6, 7, 14, 21, 28]);
    $hourly = ga_hourly($pdo, $cid, $hDays);
    $hourlySupported = count(array_diff(array_keys($hourly), [$today])) > 0;
    $baseDays = array_values(array_filter(ga_days_list($today, [1, 7, 14, 21, 28]), fn($d) => isset($hourly[$d])));
    $meta = [];
    foreach (h_q($pdo, 'SELECT * FROM ga_camp WHERE customer_id = ?', [$cid]) as $m) $meta[$m['campaign_id']] = $m;
    $cur = (string)($a['currency'] ?: (h_q1($pdo, 'SELECT currency FROM gads_account WHERE customer_id = ?', [$cid]) ?? ''));
    $live = (int)$a['src_script'] === 1 && $a['script_last_at'] && strtotime($a['script_last_at'] . ' UTC') > time() - 21600 ? (json_decode((string)(kv_get('ga_live_status_' . $cid) ?? ''), true) ?: []) : [];
    return ['cid' => $cid, 'live_status' => $live, 'acct' => $a, 'tz' => $tz->getName(), 'now' => $nowL, 'today' => $today, 'hour' => (int)$nowL->format('G'), 'R' => $R,
        'th' => $th, 'sources' => $sources, 'age' => $age, 'fresh' => $fresh, 'daily' => $daily, 'hourly' => $hourly, 'hourly_supported' => $hourlySupported,
        'base_days' => $baseDays, 'meta' => $meta, 'currency' => $cur];
}

/** Campaign ka current status: read-only script ka live status (6 ghante tak taaza) warna dashboard ki latest row. */
function ga_camp_status(array $snap, string $camp): string
{
    if (isset($snap['live_status'][$camp]) && $snap['live_status'][$camp] !== '') return (string)$snap['live_status'][$camp];
    return (string)($snap['daily'][$camp]['status'] ?? ($snap['meta'][$camp]['status'] ?? ''));
}

/** Aaj ka budget: chalu (ENABLED) campaigns ke daily budget ka jod (sirf read-only script bhejti hai); na ho to null. Shared budget do baar gin sakta hai (script budget id nahi bhejta). */
function ga_budget_today(PDO $pdo, string $cid): ?float
{
    $b = (float)(h_q1($pdo, "SELECT SUM(budget) FROM ga_camp WHERE customer_id = ? AND status = 'ENABLED' AND budget > 0 AND budget_period IN ('', 'DAILY')", [$cid]) ?? 0);
    return $b > 0 ? $b : null;
}

/**
 * Har app (pkg) ka aaj ka budget, ek account me (extra query nahi, snapshot ka ga_camp meta): ga_budget_today wala hi rule, pkg ke hisaab se.
 * spend = us pkg ke sab campaigns ka aaj ka kharcha (snapshot daily, account TZ) — account ki budget line jaisa. pkg: ga_camp ka, warna dashboard row ka; khali = skip.
 * [pkg => ['budget' =>, 'spend' =>, 'campaigns' =>]], sirf budget > 0 wale.
 */
function ga_app_budgets_today(array $snap): array
{
    $pkgOf = fn(string $c): string => (string)($snap['meta'][$c]['pkg'] ?? '') ?: (string)($snap['daily'][$c]['pkg'] ?? '');
    $out = [];
    foreach ($snap['meta'] as $c => $m) {
        $b = $m['budget'] === null ? 0.0 : (float)$m['budget'];
        if (strtoupper((string)$m['status']) !== 'ENABLED' || $b <= 0 || !in_array(strtoupper(trim((string)$m['budget_period'])), ['', 'DAILY'], true)) continue;
        $p = $pkgOf((string)$c);
        if ($p === '') continue;
        $out[$p] = ['budget' => ($out[$p]['budget'] ?? 0.0) + $b, 'spend' => 0.0, 'campaigns' => ($out[$p]['campaigns'] ?? 0) + 1];
    }
    if (!$out) return [];
    foreach ($snap['daily'] as $c => $d) {
        $p = $pkgOf((string)$c);
        if ($p !== '' && isset($out[$p])) $out[$p]['spend'] += (float)($d['days'][$snap['today']]['cost'] ?? 0);
    }
    return $out;
}

// ================================================================ scan (rules -> alerts)
/** Ek account: campaign state track karo, rules chalao. */
function ga_eval_account(PDO $pdo, array $a, int $runId): array
{
    $snap = ga_snapshot($pdo, $a);
    $cid = $snap['cid']; $th = $snap['th']; $today = $snap['today'];
    $findings = []; $campChecks = []; $nCamp = 0;
    $firstScan = (int)h_q1($pdo, 'SELECT COUNT(*) FROM ga_camp WHERE customer_id = ?', [$cid]) === 0;
    $ids = array_unique(array_merge(array_keys($snap['daily']), array_keys($snap['meta'])));
    $evidence = isset($snap['sources']['script']) && $snap['sources']['script']['age_min'] !== null ? 'Google Ads reporting (read-only script + dashboard data)' : 'Google Ads reporting (dashboard data)';
    foreach ($ids as $camp) {
        $camp = (string)$camp;
        $d = $snap['daily'][$camp] ?? ['name' => '', 'status' => '', 'pkg' => '', 'days' => []];
        $m = $snap['meta'][$camp] ?? null;
        $status = ga_camp_status($snap, $camp);
        $name = (string)($m['name'] ?? '') ?: (string)$d['name'] ?: $camp;
        // status tracking (paused / removed detection), pehli baar sirf yaad rakho
        $firstDay = $d['days'] ? array_key_first($d['days']) : null;
        if ($m === null) {
            h_x($pdo, 'INSERT INTO ga_camp (customer_id, campaign_id, name, status, pkg, first_seen, updated_at) VALUES (?,?,?,?,?,?,UTC_TIMESTAMP())',
                [$cid, $camp, h_cut($name, 255), $status, (string)$d['pkg'], $firstDay ?? $today]);
            $m = ['status' => $status, 'prev_status' => '', 'status_changed_at' => null, 'first_seen' => $firstDay ?? $today, 'start_date' => null, 'budget' => null,
                'primary_status' => '', 'primary_reasons' => '', 'serving_status' => '', 'target_cpa' => null, 'target_roas' => null, 'bidding' => '', 'channel' => ''];
            if (!$firstScan) ga_log($pdo, "new campaign seen: $name ($camp)", 'info', ['run' => $runId, 'cid' => $cid]);
        } elseif ($status !== '' && (string)$m['status'] !== '' && $status !== (string)$m['status']) {
            h_x($pdo, 'UPDATE ga_camp SET prev_status = status, status = ?, status_changed_at = UTC_TIMESTAMP(), name = ?, updated_at = UTC_TIMESTAMP() WHERE customer_id = ? AND campaign_id = ?',
                [$status, h_cut($name, 255), $cid, $camp]);
            ga_log($pdo, "campaign status changed: $name ($camp) {$m['status']} → $status", 'warn', ['run' => $runId, 'cid' => $cid]);
            $m['prev_status'] = $m['status']; $m['status'] = $status; $m['status_changed_at'] = gmdate('Y-m-d H:i:s');
        } elseif ($status !== '' && (string)$m['status'] === '') {
            h_x($pdo, 'UPDATE ga_camp SET status = ?, updated_at = UTC_TIMESTAMP() WHERE customer_id = ? AND campaign_id = ?', [$status, $cid, $camp]);
        }
        // purane, band campaigns jinka 7 din se data nahi: skip
        $recent = array_filter(array_keys($d['days']), fn($x) => $x >= ga_days_list($today, [7])[0]);
        $changedH = $m['status_changed_at'] ? (time() - strtotime($m['status_changed_at'] . ' UTC')) / 3600 : null;
        if ($status !== 'ENABLED' && !$recent && ($changedH === null || $changedH > 48)) continue;
        $nCamp++;
        $start = $m['start_date'] ?: ($m['first_seen'] ?: $firstDay);
        $loaded35 = ga_days_list($today, [35])[0];
        $ageDays = ($start && $start > $loaded35) ? (int)floor((strtotime($today) - strtotime((string)$start)) / 86400) : 99;
        if ($firstScan && $ageDays < 2 && $m['start_date'] === null) $ageDays = 99;   // pehle scan par sab "naya" na lage
        $reasons = array_filter(explode(',', (string)$m['primary_reasons']));
        $cth = ga_th($pdo, $cid, $camp);
        $learning = $ageDays < (int)gr_th($cth, 'learning_days') || (string)$m['primary_status'] === 'LEARNING' || (bool)array_filter($reasons, fn($x) => str_contains($x, 'LEARNING'));
        [$cpiT, $cpiSrc] = gr_th($cth, 'cpi_target') !== null ? [gr_th($cth, 'cpi_target'), 'configured'] : ($m['target_cpa'] !== null && (float)$m['target_cpa'] > 0 ? [(float)$m['target_cpa'], 'Google Ads target CPA'] : [null, '']);
        [$roasT, $roasSrc] = gr_th($cth, 'roas_target') !== null ? [gr_th($cth, 'roas_target'), 'configured'] : ($m['target_roas'] !== null && (float)$m['target_roas'] > 0 ? [(float)$m['target_roas'], 'Google Ads target ROAS'] : [null, '']);
        $base = [];
        foreach ($snap['base_days'] as $bd) $base[] = ['day' => $bd, 'cost_h' => $snap['hourly'][$bd][$camp]['c'] ?? [], 'impr_h' => $snap['hourly'][$bd][$camp]['i'] ?? []];
        $c = ['cid' => $cid, 'campaign_id' => $camp, 'name' => $name, 'currency' => $snap['currency'], 'status' => $status, 'status_prev' => (string)$m['prev_status'],
            'status_changed_h' => $changedH, 'age_days' => $ageDays, 'learning' => $learning, 'budget' => $m['budget'] !== null ? (float)$m['budget'] : null,
            'primary_status' => (string)$m['primary_status'], 'primary_reasons' => $reasons, 'serving_status' => (string)$m['serving_status'], 'today' => $today,
            'hour_now' => $snap['hour'], 'reported_through' => $snap['R'], 'hourly_supported' => $snap['hourly_supported'],
            'today_cost_h' => $snap['hourly'][$today][$camp]['c'] ?? [], 'today_impr_h' => $snap['hourly'][$today][$camp]['i'] ?? [], 'baseline' => $base,
            'daily' => $d['days'], 'cpi_target' => $cpiT, 'cpi_target_src' => $cpiSrc, 'roas_target' => $roasT, 'roas_target_src' => $roasSrc,
            'fresh' => $snap['fresh'], 'data_age_min' => $snap['age'], 'evidence' => $evidence];
        $res = gr_campaign($c, $cth);
        foreach ($res['findings'] as $f) $findings[] = $f + ['campaign_id' => $camp, 'campaign' => $name];
        $campChecks[$camp] = $res['checks'];
        h_x($pdo, 'UPDATE ga_camp SET last_checks = ? WHERE customer_id = ? AND campaign_id = ?', [h_json($res['checks']), $cid, $camp]);

        // country CPI (geo data ho tab)
        $ccT = [];
        foreach (ga_th_all($pdo) as $scope => $vals) if (str_starts_with($scope, "country:$cid:") && isset($vals['cpi_target'])) $ccT[substr($scope, strlen("country:$cid:"))] = (float)$vals['cpi_target'];
        if ($ccT && $snap['fresh']) {
            $w3 = gr_days_before($today, 3);
            $rows = [];
            foreach (h_q($pdo, 'SELECT country, SUM(cost) cost, SUM(conversions) conversions FROM ga_geo_day WHERE customer_id = ? AND campaign_id = ? AND day BETWEEN ? AND ? GROUP BY country',
                [$cid, $camp, end($w3), $w3[0]]) as $g) $rows[$g['country']] = ['cost' => (float)$g['cost'], 'conversions' => (float)$g['conversions']];
            foreach (gr_country_cpi($cid, $camp, $name, $snap['currency'], $rows, $ccT, $cth) as $f) $findings[] = $f + ['campaign_id' => $camp, 'campaign' => $name];
        }
    }

    // ---- account level
    $emails = [];
    foreach (h_q($pdo, "SELECT kind, verified, subject, received_at, reason, sim FROM ga_emails WHERE customer_id = ? AND kind IN ('SUSPENDED','PAYMENT_RESTRICTED','PAYMENT_NOTICE','POLICY')
        AND received_at >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 14 DAY) ORDER BY received_at", [$cid]) as $e) $emails[] = $e;
    $policy = null;
    if ((int)$a['src_script'] === 1 && $a['policy_at'] && strtotime($a['policy_at'] . ' UTC') > time() - 26 * 3600) {
        $policy = array_map(fn($p) => $p + ['topics' => array_filter(explode('|', (string)$p['topics']))],
            h_q($pdo, 'SELECT campaign_id, campaign, ad_group_id, ad_id, approval, topics FROM ga_policy WHERE customer_id = ?', [$cid]));
    }
    $assetTable = (int)h_q1($pdo, 'SELECT COUNT(*) FROM ga_asset WHERE customer_id = ?', [$cid]) > 0 ? 'ga_asset' : 'gads_asset';
    $low = h_q($pdo, "SELECT campaign_id, campaign, asset_id, name, field_type, impressions FROM $assetTable WHERE customer_id = ? AND label = 'LOW' AND impressions >= 1000 ORDER BY impressions DESC LIMIT 60", [$cid]);
    $errs = [];
    if ((int)$a['src_script'] === 1 && $a['script_last_at'] && strtotime($a['script_last_at'] . ' UTC') > time() - 6 * 3600) $errs = (array)(json_decode((string)$a['script_errors'], true) ?: []);
    // account inactivity: sab campaigns ka total aaj zero, jabki aam taur par is time tak spend hota
    $inactive = null;
    if ($snap['fresh'] && $snap['hourly_supported'] && $snap['R'] >= 2 && count($snap['base_days']) >= (int)gr_th($th, 'min_baseline_days')) {
        $nowT = 0.0;
        foreach ($snap['hourly'][$today] ?? [] as $hc) $nowT += gr_sum_hours((array)($hc['c'] ?? []), 0, $snap['R']);
        $bt = [];
        foreach ($snap['base_days'] as $bd) { $s = 0.0; foreach ($snap['hourly'][$bd] ?? [] as $hc) $s += gr_sum_hours((array)($hc['c'] ?? []), 0, $snap['R']); $bt[] = $s; }
        $bm = gr_median($bt);
        if ($nowT <= 0 && ($bm ?? 0) > 0 && count(array_filter($bt, fn($x) => $x > 0)) === count($bt)) $inactive = ['now' => 0, 'usual' => round((float)$bm, 2), 'window' => sprintf('today 00:00–%02d:59', $snap['R'])];
    }
    // aaj ka kharcha vs chalu campaigns ka daily budget (overview ki budget line jaisa); 100% = laal alert
    $budget = null;
    $bud = ga_budget_today($pdo, $cid);
    if ($bud !== null && $snap['fresh']) {
        $spT = 0.0;
        foreach ($snap['daily'] as $dc) $spT += (float)($dc['days'][$today]['cost'] ?? 0);
        $budget = ['spend' => round($spT, 2), 'budget' => round($bud, 2), 'pct' => round($spT / $bud * 100, 1), 'today' => $today];
    }
    $acc = gr_account(['cid' => $cid, 'company' => $a['company'], 'currency' => $snap['currency'], 'data_age_min' => $snap['age'], 'sources' => $snap['sources'],
        'script_status' => (int)$a['src_script'] === 1 ? (string)$a['script_status'] : '', 'script_status_at' => $a['script_status_at'], 'script_errors' => $errs,
        'emails' => $emails, 'policy' => $policy, 'assets_low' => $low, 'inactive' => $inactive, 'budget' => $budget], $th);
    foreach ($acc['findings'] as $f) $findings[] = $f;
    return ['snap' => $snap, 'findings' => $findings, 'checks' => $acc['checks'], 'camp_checks' => $campChecks, 'campaigns' => $nCamp];
}

/** Findings -> ga_alerts (dedup), jo ab nahi dikhe wo resolve, naye / bade = notification. */
function ga_apply(PDO $pdo, string $cid, array $findings, bool $fresh, int $runId, ?int $ageMin = null, bool $sim = false): array
{
    $keys = []; $new = []; $esc = [];
    foreach ($findings as $f) {
        $akey = h_cut(($sim ? 'sim|' : '') . $f['key'], 190);
        $keys[] = $akey;
        $row = $pdo->prepare('SELECT * FROM ga_alerts WHERE akey = ? AND resolved_at IS NULL ORDER BY id DESC LIMIT 1');
        $row->execute([$akey]);
        $cur = $row->fetch();
        $vals = [$f['level'], $f['klass'], h_cut($f['title'], 255), h_cut($f['title_hi'] ?: $f['title'], 255), $f['what'], $f['what_hi'] ?: $f['what'], h_json($f['metrics'] ?: new stdClass()),
            h_json($f['causes']), h_cut($f['next'], 1000), h_cut($f['next_hi'] ?: $f['next'], 1000), h_cut((string)$f['impact'], 300), h_cut((string)$f['evidence'], 300), $ageMin];
        if ($cur) {
            h_x($pdo, 'UPDATE ga_alerts SET level = ?, klass = ?, title = ?, title_hi = ?, what = ?, what_hi = ?, metrics = ?, causes = ?, next_step = ?, next_hi = ?, impact = ?, evidence = ?,
                freshness_min = ?, last_seen = UTC_TIMESTAMP(), run_id = ? WHERE id = ?', array_merge($vals, [$runId, $cur['id']]));
            if ((GA_LEVELS[$f['level']] ?? 1) > (GA_LEVELS[$cur['notified_level']] ?? 0) && $f['level'] !== 'INFO' && $f['klass'] !== 'INSUFFICIENT') $esc[] = (int)$cur['id'];
        } else {
            h_x($pdo, 'INSERT INTO ga_alerts (level, klass, title, title_hi, what, what_hi, metrics, causes, next_step, next_hi, impact, evidence, freshness_min,
                akey, customer_id, campaign_id, campaign, ad_group_id, country, rule, first_seen, last_seen, sim, run_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP(),?,?)',
                array_merge($vals, [$akey, $cid, (string)($f['campaign_id'] ?? ''), h_cut((string)($f['campaign'] ?? ''), 255), (string)($f['ad_group_id'] ?? ''), (string)($f['country'] ?? ''), $f['rule'], $sim ? 1 : 0, $runId]));
            $id = (int)$pdo->lastInsertId();
            ga_log($pdo, "new {$f['level']} " . strtolower($f['klass']) . ": " . $f['title'], $f['level'] === 'CRITICAL' ? 'error' : ($f['level'] === 'WARNING' ? 'warn' : 'info'), ['run' => $runId, 'cid' => $cid, 'alert' => $id]);
            if ($f['level'] !== 'INFO' && $f['klass'] !== 'INSUFFICIENT') $new[] = $id;
        }
    }
    // jo ab nahi dikhe: resolve. Data stale ho to metric alerts khule rehte hain (purana data par faisla nahi).
    $resolved = 0;
    if (!$sim) {
        $open = h_q($pdo, 'SELECT id, akey, rule, first_seen FROM ga_alerts WHERE customer_id = ? AND resolved_at IS NULL AND sim = 0', [$cid]);
        foreach ($open as $o) {
            if (in_array($o['akey'], $keys, true)) continue;
            $isMail = str_starts_with($o['akey'], 'mail|');
            if ($isMail) {
                if (strtotime($o['first_seen'] . ' UTC') > time() - 14 * 86400) continue;   // email wale 14 din ya manual close
            } elseif (!$fresh && !in_array($o['rule'], ['no_source', 'source_error'], true)) continue;
            h_x($pdo, "UPDATE ga_alerts SET resolved_at = UTC_TIMESTAMP(), resolved_by = 'auto' WHERE id = ?", [$o['id']]);
            ga_log($pdo, "resolved (condition cleared): alert #{$o['id']} {$o['rule']}", 'info', ['run' => $runId, 'cid' => $cid, 'alert' => (int)$o['id']]);
            $resolved++;
        }
    }
    foreach (array_merge($new, $esc) as $id) ga_notify($pdo, $id, $runId);
    return ['new' => $new, 'escalated' => $esc, 'resolved' => $resolved];
}

/** Account ki health: open alerts ka sabse bada level (stale alag). */
function ga_health_of(PDO $pdo, string $cid, bool $fresh, ?int $age): string
{
    if ($age === null) return 'NO_DATA';
    $lv = h_q1($pdo, "SELECT MAX(CASE level WHEN 'CRITICAL' THEN 3 WHEN 'WARNING' THEN 2 ELSE 1 END) FROM ga_alerts WHERE customer_id = ? AND resolved_at IS NULL AND sim = 0 AND rule <> 'stale'", [$cid]);
    if (!$fresh) return 'STALE';
    return ((int)$lv) >= 3 ? 'CRITICAL' : (((int)$lv) === 2 ? 'WARNING' : 'HEALTHY');
}

/** Poora scan: har monitored account, audit record ke saath. */
function ga_scan(PDO $pdo, string $trigger = 'scheduled', string $by = '', ?string $onlyCid = null): array
{
    $got = (int)$pdo->query("SELECT GET_LOCK('mfc_gads_scan', 0)")->fetchColumn();
    if ($got !== 1) return ['ok' => false, 'error' => 'another scan is running'];
    try {
        $run = ga_run_new($pdo, 'scan', $trigger, $by);
        $accts = ga_accounts($pdo, true);
        if ($onlyCid !== null) $accts = array_intersect_key($accts, [$onlyCid => 1]);
        $src = []; $issues = 0; $recs = 0; $failed = 0; $nCamp = 0; $errs = []; $okAcc = 0; $newCrit = [];
        foreach ($accts as $cid => $a) {
            $cid = (string)$cid;
            try {
                $r = ga_eval_account($pdo, $a, $run['id']);
                $snap = $r['snap'];
                $ap = ga_apply($pdo, $cid, $r['findings'], $snap['fresh'], $run['id'], $snap['age']);
                foreach ($ap['new'] as $id) if (h_q1($pdo, 'SELECT level FROM ga_alerts WHERE id = ?', [$id]) === 'CRITICAL') $newCrit[] = $id;
                $nCamp += $r['campaigns'];
                $iss = count(array_filter($r['findings'], fn($f) => $f['level'] !== 'INFO' && $f['klass'] !== 'INSUFFICIENT'));
                $issues += $iss;
                $recs += count(array_filter($r['findings'], fn($f) => $f['next'] !== ''));
                $state = $snap['age'] === null ? 'NO_DATA' : ($snap['fresh'] ? 'OK' : 'STALE');
                $errN = count(array_filter($r['findings'], fn($f) => $f['rule'] === 'source_error'));
                if ($state !== 'OK') $failed++;
                $failed += $errN;
                if ($state === 'OK') $okAcc++;
                $srcAges = array_map(fn($s) => $s['age_min'], $snap['sources']);
                $src[] = ['cid' => $cid, 'company' => $a['company'], 'state' => $state, 'age_min' => $snap['age'], 'sources' => $srcAges, 'campaigns' => $r['campaigns'], 'issues' => $iss,
                    'reported_through' => $snap['R'] >= 0 ? sprintf('%02d:59', $snap['R']) : null, 'tz' => $snap['tz'], 'source_errors' => $errN];
                $health = ga_health_of($pdo, $cid, $snap['fresh'], $snap['age']);
                h_x($pdo, 'UPDATE ga_accounts SET last_scan_at = UTC_TIMESTAMP(), last_state = ?, health = ?, last_checks = ? WHERE customer_id = ?', [$state, $health, h_json($r['checks']), $cid]);
                ga_log($pdo, ga_fmt_cid($cid) . " · {$a['company']} · $state" . ($snap['age'] !== null ? " · data {$snap['age']} min old" : ' · no data') . " · {$r['campaigns']} campaigns · $iss issues", $state === 'OK' ? 'info' : 'warn', ['run' => $run['id'], 'cid' => $cid]);
            } catch (Throwable $e) {
                $failed++;
                $errs[] = ga_fmt_cid($cid) . ': ' . $e->getMessage();
                $src[] = ['cid' => $cid, 'company' => $a['company'], 'state' => 'ERROR', 'error' => h_cut($e->getMessage(), 300)];
                h_x($pdo, "UPDATE ga_accounts SET last_scan_at = UTC_TIMESTAMP(), last_state = 'ERROR' WHERE customer_id = ?", [$cid]);
                ga_log($pdo, ga_fmt_cid($cid) . ' check failed: ' . $e->getMessage(), 'error', ['run' => $run['id'], 'cid' => $cid]);
            }
        }
        // naye CRITICAL par turant AI (max 2), baaki deep job me
        foreach (array_slice($newCrit, 0, 2) as $id) { try { if (ga_ai_investigate($pdo, (int)$id, $run['id'])) $recs++; } catch (Throwable $e) { $errs[] = 'AI: ' . $e->getMessage(); } }
        $status = !$accts ? 'NO_ACCOUNTS' : ($okAcc === count($accts) && !$errs && $failed === 0 ? 'SUCCESS' : ($okAcc > 0 ? 'PARTIAL' : 'FAILED'));
        $next = gmdate('Y-m-d H:i:s', time() + GA_SCAN_EVERY);
        ga_run_end($pdo, $run, $status, ['accounts_checked' => count($accts), 'campaigns_checked' => $nCamp, 'sources' => $src, 'issues' => $issues, 'recs' => $recs,
            'failed_checks' => $failed, 'errors' => h_cut(implode(' | ', $errs), 4000), 'next_run_at' => $next]);
        kv_set('ga_last_scan', (string)time());
        if (in_array($status, ['SUCCESS', 'PARTIAL'], true)) kv_set('ga_last_ok_run', (string)$run['id']);
        return ['ok' => true, 'run_id' => $run['id'], 'run_uid' => $run['uid'], 'status' => $status];
    } finally {
        $pdo->query("SELECT RELEASE_LOCK('mfc_gads_scan')");
    }
}

// ================================================================ notifications (app + email)
function ga_notify(PDO $pdo, int $id, ?int $runId = null): void
{
    $al = $pdo->query('SELECT * FROM ga_alerts WHERE id = ' . $id)->fetch();
    if (!$al || $al['level'] === 'INFO' || $al['ack_at']) return;
    $acc = $pdo->prepare('SELECT * FROM ga_accounts WHERE customer_id = ?');
    $acc->execute([$al['customer_id']]);
    $a = $acc->fetch() ?: ['company' => '', 'push' => 1, 'email' => 1];
    $where = ($al['sim'] ? '[SIMULATED] ' : '') . $a['company'] . ' · CID ' . ga_fmt_cid((string)$al['customer_id']) . ($al['campaign'] !== '' ? ' · ' . $al['campaign'] : '');
    $lv = $al['level'] === 'CRITICAL' ? 3 : 2;
    $sent = [];
    if (ga_set('ga_push') !== '0' && (int)$a['push'] === 1) {
        try {
            if ($pdo->query("SHOW TABLES LIKE 'app_alerts'")->fetch()) {
                h_x($pdo, 'INSERT INTO app_alerts (akey, level, rule, pkg, account, campaign, title, body, title_hi, body_hi, ai, metrics, fired, created_at, updated_at)
                    VALUES (?,?,?,\'\',?,?,?,?,?,?,\'\',?,1,UTC_TIMESTAMP(),UTC_TIMESTAMP())',
                    [h_cut("gah|$id|{$al['level']}", 190), $lv, h_cut('gah_' . $al['rule'], 40), (string)$al['customer_id'], h_cut((string)$al['campaign'], 255),
                        h_cut(($al['sim'] ? '[SIMULATED] ' : '') . $al['title'], 255), h_cut($where . "\n" . (string)$al['what'], 2000),
                        h_cut(($al['sim'] ? '[SIMULATED] ' : '') . $al['title_hi'], 255), h_cut($where . "\n" . (string)$al['what_hi'], 2000),
                        h_json(['ga_alert_id' => $id, 'level' => $al['level'], 'klass' => $al['klass'], 'sim' => (int)$al['sim']])]);
                $sent[] = 'app';
            }
        } catch (Throwable $e) { ga_log($pdo, "app notification failed for alert #$id: " . $e->getMessage(), 'error', ['run' => $runId, 'alert' => $id]); }
    }
    if ($al['level'] === 'CRITICAL' && ga_set('ga_mail_critical') !== '0' && (int)$a['email'] === 1 && function_exists('h_email_on') && h_email_on()) {
        $m = json_decode((string)$al['metrics'], true) ?: [];
        $body = "WHAT HAPPENED\n" . $al['title'] . "\n" . $al['what'] . "\n\nWHERE\n$where" . ($al['campaign_id'] !== '' ? " (campaign ID {$al['campaign_id']})" : '')
            . "\n\nWHEN\nFirst observed " . h_local((string)$al['first_seen'])->format('d M Y H:i') . ' (' . cfg('timezone') . ")\n\nEVIDENCE\n" . $al['evidence'] . ' · ' . $al['klass']
            . ($al['freshness_min'] !== null ? ' · data ' . $al['freshness_min'] . ' min old' : '') . "\n" . ga_metrics_text($m)
            . ($al['impact'] !== '' ? "\n\nBUSINESS IMPACT\n" . $al['impact'] : '') . "\n\nSUGGESTED NEXT STEP (manual)\n" . $al['next_step']
            . "\n\nThis monitor is read-only: nothing was changed in Google Ads.\nOpen in MFC Dash: " . h_base() . '/api/gads_health.php?open=alert/' . $id . "\n\n— MFC Dash · Google Ads AI Health Monitor";
        $ok = h_mail_send((string)hcfg('email_to'), ($al['sim'] ? '[SIMULATED] ' : '') . '[Google Ads CRITICAL] ' . $al['title'], $body);
        ga_log($pdo, ($ok ? 'email sent' : 'email FAILED: ' . (string)kv_get('h_mail_err')) . " · alert #$id", $ok ? 'info' : 'error', ['run' => $runId, 'cid' => $al['customer_id'], 'alert' => $id]);
        if ($ok) $sent[] = 'email';
    }
    h_x($pdo, 'UPDATE ga_alerts SET notified_level = ?, notified_at = UTC_TIMESTAMP() WHERE id = ?', [$al['level'], $id]);
    ga_log($pdo, "notified alert #$id ({$al['level']}): " . ($sent ? implode(' + ', $sent) : 'no channel on'), 'info', ['run' => $runId, 'cid' => $al['customer_id'], 'alert' => $id]);
}

function ga_metrics_text(array $m): string
{
    $out = [];
    foreach ($m as $k => $v) {
        if (is_array($v)) $v = implode(', ', array_map(fn($x) => is_array($x) ? h_json($x) : (string)$x, array_slice($v, 0, 8)));
        $out[] = str_replace('_', ' ', (string)$k) . ': ' . (is_bool($v) ? ($v ? 'yes' : 'no') : (string)$v);
    }
    return implode("\n", array_slice($out, 0, 14));
}

// ================================================================ Claude AI (sirf analysis; koi tool / action nahi)
function ga_ai_on(): bool { return ga_set('ga_ai_on') !== '0' && function_exists('h_ai_key') && h_ai_key() !== ''; }

/** Har Claude call isi se: roz ki limit (kv ga_ai_cap). */
function ga_ai(string $system, string $user, int $maxTokens = 1500): ?string
{
    if (!ga_ai_on()) return null;
    $day = gmdate('Y-m-d');
    $used = kv_get('ga_ai_day') === $day ? (int)(kv_get('ga_ai_used') ?? 0) : 0;
    if ($used >= max(1, (int)ga_set('ga_ai_cap'))) { kv_set('ga_ai_last_error', gmdate('Y-m-d H:i') . ' daily AI cap reached'); return null; }
    kv_set('ga_ai_day', $day); kv_set('ga_ai_used', (string)($used + 1));
    // AI Cost & Usage: ga_ai_ctx() se module 'gads' + alert ref; critical alert ka pehla AI = critical (reserve)
    $prev = cost_ctx(array_merge(['module' => 'gads', 'task' => 'ads_ai'], ga_ai_ctx()));
    try { return h_ai_call($system, $user, $maxTokens); } finally { cost_ctx($prev); ga_ai_ctx([]); }
}

/** Agle ga_ai() call ka hisaab kis kaam me jaaye (task, ref, critical). */
function ga_ai_ctx(?array $set = null): array
{
    static $c = [];
    $prev = $c;
    if ($set !== null) $c = $set;
    return $prev;
}

const GA_AI_SYSTEM = "You are a read-only Google Ads performance analyst for a mobile app publisher. You only explain and recommend; you cannot and must not change anything.\n"
    . "Rules:\n- Use only the numbers in the evidence JSON. Never invent metrics, asset-level CPI, policy reasons or account statuses.\n"
    . "- Never call an account suspended unless the evidence contains an official Google Ads notification or customer.status = SUSPENDED.\n"
    . "- Zero or low spend is never by itself evidence of suspension.\n- Treat incomplete / lagging data as unknown, not zero.\n"
    . "- Every account is a separate company with its own currency; never add or compare money across currencies.\n"
    . "- Recommended actions are manual steps the human performs in Google Ads. Never suggest automated changes, scripts that mutate, or linking accounts / MCCs.\n"
    . "Reply with JSON only, no markdown:\n{\"classification\":\"CONFIRMED ISSUE|SUSPECTED ISSUE|INSUFFICIENT DATA\",\"summary\":\"2 sentences\",\"summary_hi\":\"same in simple Hinglish\","
    . "\"causes\":[{\"cause\":\"...\",\"evidence\":\"which numbers support it\",\"likelihood\":\"high|medium|low\"}],\"confidence\":0-100,"
    . "\"manual_actions\":[\"step 1\",\"step 2\"],\"manual_actions_hi\":[\"...\"],\"business_impact\":\"...\",\"creative_suggestion\":\"only for creative issues: a replacement idea for manual review, else empty\"}";

function ga_ai_investigate(PDO $pdo, int $id, ?int $runId = null): bool
{
    $al = $pdo->query('SELECT * FROM ga_alerts WHERE id = ' . $id)->fetch();
    if (!$al) throw new RuntimeException("alert $id not found");
    if (!ga_ai_on()) { h_x($pdo, "UPDATE ga_alerts SET ai_err = 'AI key not set' WHERE id = ?", [$id]); return false; }
    $a = $pdo->query('SELECT customer_id, company, account_name, mcc_label, currency, tz FROM ga_accounts WHERE customer_id = ' . $pdo->quote((string)$al['customer_id']))->fetch() ?: [];
    $ev = ['account' => $a, 'alert' => ['rule' => $al['rule'], 'level' => $al['level'], 'classification' => $al['klass'], 'title' => $al['title'], 'what' => $al['what'],
        'first_seen_utc' => $al['first_seen'], 'evidence_source' => $al['evidence'], 'data_freshness_min' => $al['freshness_min'], 'metrics' => json_decode((string)$al['metrics'], true),
        'rule_based_possible_causes' => json_decode((string)$al['causes'], true)]];
    $camp = (string)$al['campaign_id'];
    if ($camp !== '') {
        $m = $pdo->prepare('SELECT name, status, prev_status, status_changed_at, primary_status, primary_reasons, serving_status, channel, bidding, target_cpa, target_roas, budget, start_date, meta_at FROM ga_camp WHERE customer_id = ? AND campaign_id = ?');
        $m->execute([$al['customer_id'], $camp]);
        $ev['campaign'] = $m->fetch() ?: null;
        $today = (new DateTime('now', ga_acct_tz($pdo, $a + ['tz' => $a['tz'] ?? '', 'customer_id' => $al['customer_id']])))->format('Y-m-d');
        $d = ga_daily($pdo, (string)$al['customer_id'], ga_days_list($today, [14])[0], $today)[$camp]['days'] ?? [];
        $ev['campaign_daily_14d'] = $d;
        $ev['countries_7d'] = h_q($pdo, 'SELECT country, ROUND(SUM(cost),2) cost, SUM(impressions) impressions, ROUND(SUM(conversions),1) installs, ROUND(SUM(conv_value),2) conv_value
            FROM ga_geo_day WHERE customer_id = ? AND campaign_id = ? AND day >= ? GROUP BY country ORDER BY cost DESC LIMIT 10', [$al['customer_id'], $camp, ga_days_list($today, [7])[0]]);
        $ev['policy_issues'] = h_q($pdo, 'SELECT ad_group_id, ad_id, approval, topics FROM ga_policy WHERE customer_id = ? AND campaign_id = ? LIMIT 20', [$al['customer_id'], $camp]);
        $ev['asset_labels_7d'] = h_q($pdo, 'SELECT field_type, asset_type, label, LEFT(name, 80) name, impressions, clicks FROM ' .
            ((int)h_q1($pdo, 'SELECT COUNT(*) FROM ga_asset WHERE customer_id = ?', [$al['customer_id']]) > 0 ? 'ga_asset' : 'gads_asset') .
            ' WHERE customer_id = ? AND campaign_id = ? ORDER BY impressions DESC LIMIT 25', [$al['customer_id'], $camp]);
        $ev['note'] = 'Asset rows have Google performance labels only; Google does not report independent asset-level CPI.';
    }
    $ev['recent_account_emails'] = h_q($pdo, 'SELECT kind, verified, LEFT(subject, 150) subject, received_at FROM ga_emails WHERE customer_id = ? ORDER BY received_at DESC LIMIT 5', [$al['customer_id']]);
    ga_ai_ctx(['task' => 'alert_ai', 'ref' => 'GA-' . $id, 'critical' => ($al['level'] ?? '') === 'CRITICAL']);
    $txt = ga_ai(GA_AI_SYSTEM, "Investigate this alert. Evidence JSON:\n" . h_json($ev));
    if ($txt === null) {
        h_x($pdo, 'UPDATE ga_alerts SET ai_err = ? WHERE id = ?', [h_cut((string)(kv_get('ga_ai_last_error') ?? kv_get('ai_last_error') ?? 'AI call failed'), 300), $id]);
        ga_log($pdo, "AI investigation failed for alert #$id", 'error', ['run' => $runId, 'cid' => $al['customer_id'], 'alert' => $id]);
        return false;
    }
    $j = json_decode(trim(preg_replace('/^```(json)?|```$/m', '', $txt)), true);
    if (!is_array($j)) $j = ['summary' => h_cut($txt, 1500), 'causes' => [], 'confidence' => null, 'manual_actions' => []];
    h_x($pdo, "UPDATE ga_alerts SET ai = ?, ai_conf = ?, ai_at = UTC_TIMESTAMP(), ai_err = '' WHERE id = ?", [h_json($j), isset($j['confidence']) ? (int)$j['confidence'] : null, $id]);
    ga_log($pdo, "AI investigation done for alert #$id · confidence " . ($j['confidence'] ?? '?') . '%', 'info', ['run' => $runId, 'cid' => $al['customer_id'], 'alert' => $id]);
    return true;
}

/** Har 6 ghante: khule actionable alerts jinka AI purana / nahi hai. */
function ga_deep(PDO $pdo, string $trigger = 'scheduled', string $by = ''): array
{
    $run = ga_run_new($pdo, 'deep', $trigger, $by);
    if (!ga_ai_on()) { ga_run_end($pdo, $run, 'SKIPPED', ['errors' => 'AI key not set (Settings > AI in the app)']); return ['ok' => true, 'status' => 'SKIPPED']; }
    $ids = h_q($pdo, "SELECT id FROM ga_alerts WHERE resolved_at IS NULL AND ack_at IS NULL AND sim = 0 AND level IN ('CRITICAL','WARNING') AND klass <> 'INSUFFICIENT'
        AND (ai_at IS NULL OR ai_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 6 HOUR)) ORDER BY FIELD(level,'CRITICAL','WARNING'), last_seen DESC LIMIT 8");
    $ok = 0; $fail = 0;
    foreach ($ids as $r) { try { ga_ai_investigate($pdo, (int)$r['id'], $run['id']) ? $ok++ : $fail++; } catch (Throwable $e) { $fail++; } }
    ga_run_end($pdo, $run, $fail > 0 ? ($ok > 0 ? 'PARTIAL' : 'FAILED') : 'SUCCESS', ['issues' => count($ids), 'recs' => $ok, 'failed_checks' => $fail]);
    kv_set('ga_last_deep', (string)time());
    return ['ok' => true, 'investigated' => $ok, 'failed' => $fail];
}

// ================================================================ daily portfolio review (har account alag, currency alag)
function ga_daily_review(PDO $pdo, string $trigger = 'scheduled', string $by = ''): array
{
    $run = ga_run_new($pdo, 'daily', $trigger, $by);
    $acc = []; $txt = '';
    foreach (ga_accounts($pdo, true) as $cid => $a) {
        $cid = (string)$cid;
        $tz = ga_acct_tz($pdo, $a);
        $today = (new DateTime('now', $tz))->format('Y-m-d');
        $y = ga_days_list($today, [1])[0];
        $daily = ga_daily($pdo, $cid, ga_days_list($today, [8])[0], $y);
        $sumY = ['cost' => 0.0, 'conversions' => 0.0, 'conv_value' => 0.0]; $sum7 = $sumY;
        foreach ($daily as $c) foreach ($c['days'] as $d => $r) {
            foreach (['cost', 'conversions', 'conv_value'] as $k) { if ($d === $y) $sumY[$k] += $r[$k]; if ($d < $y) $sum7[$k] += $r[$k] / 7; }
        }
        $cur = $a['currency'] ?: (string)(h_q1($pdo, 'SELECT currency FROM gads_account WHERE customer_id = ?', [$cid]) ?? '');
        $open = h_q($pdo, "SELECT level, title FROM ga_alerts WHERE customer_id = ? AND resolved_at IS NULL AND sim = 0 AND level <> 'INFO' ORDER BY FIELD(level,'CRITICAL','WARNING') LIMIT 6", [$cid]);
        $row = ['cid' => $cid, 'company' => $a['company'], 'currency' => $cur, 'health' => $a['health'], 'yesterday' => $y,
            'spend' => round($sumY['cost'], 2), 'installs' => round($sumY['conversions'], 1), 'cpi' => $sumY['conversions'] > 0 ? round($sumY['cost'] / $sumY['conversions'], 2) : null,
            'roas' => $sumY['cost'] > 0 ? round($sumY['conv_value'] / $sumY['cost'], 3) : null, 'spend_avg7' => round($sum7['cost'], 2), 'installs_avg7' => round($sum7['conversions'], 1),
            'open_alerts' => $open];
        $acc[] = $row;
        $txt .= "{$a['company']} · CID " . ga_fmt_cid($cid) . " · {$a['health']}\n  $y: spend $cur " . number_format($row['spend'], 2) . " (7d avg $cur " . number_format($row['spend_avg7'], 2) . ")"
            . " · installs " . $row['installs'] . ' · CPI ' . ($row['cpi'] === null ? '–' : "$cur " . number_format($row['cpi'], 2)) . ' · ROAS ' . ($row['roas'] === null ? '–' : round($row['roas'] * 100) . '%') . "\n";
        foreach ($open as $o) $txt .= "  - [{$o['level']}] {$o['title']}\n";
    }
    $ai = null;
    if ($acc && ga_ai_on()) {
        ga_ai_ctx(['task' => 'daily_review', 'ref' => 'review ' . gmdate('Y-m-d')]);
        $r = ga_ai(GA_AI_SYSTEM . "\nFor this portfolio review reply JSON: {\"accounts\":[{\"cid\":\"...\",\"recommendations\":[\"manual step\"],\"recommendations_hi\":[\"...\"]}],\"overall\":\"2 sentences, no cross-currency totals\"}",
            "Daily portfolio review. Each account is a separate company and currency. Data:\n" . h_json($acc), 2000);
        if ($r !== null) $ai = json_decode(trim(preg_replace('/^```(json)?|```$/m', '', $r)), true) ?: ['overall' => h_cut($r, 2000)];
    }
    $summary = ['accounts' => $acc, 'ai' => $ai, 'text' => $txt];
    if ($acc && ga_set('ga_mail_daily') !== '0' && function_exists('h_email_on') && h_email_on()) {
        $ok = h_mail_send((string)hcfg('email_to'), '[Google Ads] Daily health summary · ' . h_local()->format('d M Y'),
            "Per-account summary (each account in its own currency, no combined totals):\n\n$txt" . ($ai && !empty($ai['overall']) ? "\nAI: " . $ai['overall'] . "\n" : '')
            . "\nRead-only monitoring: nothing was changed in Google Ads.\n— MFC Dash · Google Ads AI Health Monitor");
        ga_log($pdo, $ok ? 'daily summary email sent' : 'daily summary email FAILED: ' . (string)kv_get('h_mail_err'), $ok ? 'info' : 'error', ['run' => $run['id']]);
    }
    ga_run_end($pdo, $run, $acc ? 'SUCCESS' : 'NO_ACCOUNTS', ['accounts_checked' => count($acc), 'recs' => $ai ? count((array)($ai['accounts'] ?? [])) : 0, 'summary' => h_json($summary)]);
    return ['ok' => true];
}

// ================================================================ email intake (IMAP, sirf padhna; mailbox me kuch nahi badalta)
function ga_imap_on(): bool { $i = (array)(gcfg('imap') ?? []); return !empty($i['host']) && !empty($i['user']); }

/** Chhota IMAP client (php-imap extension ki zaroorat nahi). Sirf naye UID padhta hai, flags nahi badalta. */
function ga_mail_job(PDO $pdo, string $trigger = 'scheduled', string $by = ''): array
{
    if (!ga_imap_on()) return ['ok' => true, 'skipped' => 'not configured'];
    $run = ga_run_new($pdo, 'email', $trigger, $by);
    $i = (array)gcfg('imap');
    $n = 0; $matched = 0; $err = '';
    try {
        $fp = @stream_socket_client('ssl://' . $i['host'] . ':' . (int)($i['port'] ?? 993), $en, $es, 20);
        if (!$fp) throw new RuntimeException("IMAP connect failed: $es");
        stream_set_timeout($fp, 30);
        $tag = 0;
        $cmd = function (string $c) use ($fp, &$tag): string {
            $t = 'A' . (++$tag);
            fwrite($fp, "$t $c\r\n");
            $buf = '';
            while (($l = fgets($fp, 65536)) !== false) {
                $buf .= $l;
                if (preg_match('/\{(\d+)\}\r\n$/', $l, $mm)) { $need = (int)$mm[1]; $got = ''; while (strlen($got) < $need && !feof($fp)) $got .= fread($fp, $need - strlen($got)); $buf .= $got; continue; }
                if (str_starts_with($l, "$t ")) { if (!preg_match("/^$t OK/", $l)) throw new RuntimeException('IMAP: ' . trim($l)); break; }
            }
            return $buf;
        };
        fgets($fp, 4096);
        $q = fn(string $s) => '"' . addcslashes($s, "\"\\") . '"';
        $cmd('LOGIN ' . $q((string)$i['user']) . ' ' . $q((string)($i['pass'] ?? '')));
        $sel = $cmd('EXAMINE ' . $q((string)($i['folder'] ?? 'INBOX')));     // EXAMINE = read-only mailbox
        $uv = preg_match('/UIDVALIDITY (\d+)/', $sel, $mm) ? $mm[1] : '0';
        $last = kv_get('ga_imap_uv') === $uv ? (int)(kv_get('ga_imap_uid') ?? 0) : 0;
        $since = date('d-M-Y', time() - 14 * 86400);
        $s = $cmd($last > 0 ? 'UID SEARCH UID ' . ($last + 1) . ':*' : "UID SEARCH SINCE $since");
        $uids = preg_match('/\* SEARCH([\d ]*)/', $s, $mm) ? array_values(array_filter(array_map('intval', explode(' ', trim($mm[1]))), fn($u) => $u > $last)) : [];
        sort($uids);
        foreach (array_slice($uids, 0, 50) as $uid) {
            $raw = $cmd("UID FETCH $uid (BODY.PEEK[]<0.300000>)");
            $p = strpos($raw, "}\r\n");
            $msg = $p !== false ? substr($raw, $p + 3) : $raw;
            $r = ga_mail_ingest($pdo, $msg, $run['id']);
            $n++;
            if ($r === 'matched') $matched++;
            kv_set('ga_imap_uid', (string)$uid);
        }
        kv_set('ga_imap_uv', $uv);
        if (!$uids && $last === 0) kv_set('ga_imap_uid', '0');
        $cmd('LOGOUT');
        fclose($fp);
        kv_set('ga_mail_ok_at', (string)time());
        kv_set('ga_mail_err', '');
    } catch (Throwable $e) {
        $err = $e->getMessage();
        kv_set('ga_mail_err', gmdate('Y-m-d H:i') . ' ' . h_cut($err, 250));
    }
    ga_run_end($pdo, $run, $err !== '' ? 'FAILED' : 'SUCCESS', ['issues' => $matched, 'failed_checks' => $err !== '' ? 1 : 0, 'errors' => $err, 'summary' => h_json(['messages' => $n, 'matched' => $matched])]);
    kv_set('ga_last_mail', (string)time());
    return ['ok' => $err === '', 'messages' => $n, 'matched' => $matched, 'error' => $err];
}

/** Ek email (raw RFC822) -> ga_emails. Return: matched | unmatched | ignored | duplicate. */
function ga_mail_ingest(PDO $pdo, string $raw, ?int $runId = null, bool $sim = false): string
{
    [$hdrRaw, $body] = array_pad(preg_split("/\r?\n\r?\n/", $raw, 2), 2, '');
    $hdr = [];
    foreach (preg_split("/\r?\n(?![ \t])/", $hdrRaw) as $line) {
        if (!str_contains($line, ':')) continue;
        [$k, $v] = explode(':', $line, 2);
        $hdr[strtolower(trim($k))][] = trim(preg_replace('/\r?\n[ \t]+/', ' ', $v));
    }
    $h1 = fn(string $k) => $hdr[$k][0] ?? '';
    $dec = function (string $s): string { $d = @iconv_mime_decode($s, ICONV_MIME_DECODE_CONTINUE_ON_ERROR, 'UTF-8'); return $d !== false ? $d : $s; };
    $subject = $dec($h1('subject'));
    $from = strtolower($dec($h1('from')));
    $fromAddr = preg_match('/<([^>]+)>/', $from, $m) ? $m[1] : trim($from);
    $msgId = h_cut(trim($h1('message-id'), '<> ') ?: sha1($raw), 190);
    $date = strtotime($h1('date')) ?: time();
    if ((int)h_q1($pdo, 'SELECT COUNT(*) FROM ga_emails WHERE msg_id = ?', [$msgId]) > 0) return 'duplicate';
    // verify: sender google.com domain + DKIM / DMARC pass for google.com (receiving server ka pehla Authentication-Results)
    $domOk = (bool)preg_match('/@([a-z0-9-]+\.)*google\.com$/', $fromAddr);
    $ar = strtolower($hdr['authentication-results'][0] ?? '');
    $authOk = (bool)(preg_match('/dkim=pass[^;]*header\.(d|i)=@?([a-z0-9-]+\.)*google\.com/', $ar) || preg_match('/dmarc=pass[^;]*header\.from=([a-z0-9-]+\.)*google\.com/', $ar));
    $verified = $domOk && $authOk;
    $note = $verified ? 'sender google.com + DKIM/DMARC pass' : ($domOk ? 'google.com sender but no DKIM/DMARC pass header' : 'sender is not google.com');
    $text = ga_mail_text($hdr, $body);
    $low = strtolower($subject . "\n" . $text);
    $kind = 'OTHER';
    if (preg_match('/reinstat|no longer suspended|suspension (has been )?lifted|account is now active/', $low)) $kind = 'REINSTATED';
    elseif (preg_match('/suspend/', $low)) $kind = 'SUSPENDED';
    elseif (preg_match('/suspicious payment/', $low)) $kind = 'PAYMENT_NOTICE';
    elseif (preg_match('/(payment|billing|invoice)[^.\n]{0,80}(declined|failed|restricted|on hold|overdue|unpaid|couldn.t be processed)|ads (have )?stopped running[^.\n]{0,40}payment/', $low)) $kind = 'PAYMENT_RESTRICTED';
    elseif (preg_match('/payment|billing/', $low) && preg_match('/google ads/', $low)) $kind = 'PAYMENT_NOTICE';
    elseif (preg_match('/disapprov|policy|violation|limited/', $low)) $kind = 'POLICY';
    $reason = preg_match('/(?:reason|policy|violation)\s*[:\-]\s*([^\n\r]{3,160})/i', $text, $m) ? trim($m[1]) : '';
    // CID: sirf registered account; ek se zyada registered CID = attribute nahi (kabhi guess nahi)
    preg_match_all('/\b(\d{3}-\d{3}-\d{4})\b/', $subject . "\n" . $text, $mm);
    $regs = array_map('strval', array_keys(ga_accounts($pdo)));
    $hits = array_values(array_unique(array_filter(array_map('ga_cid', $mm[1] ?? []), fn($c) => in_array($c, $regs, true))));
    $cid = count($hits) === 1 ? $hits[0] : '';
    if (count($hits) > 1) $note .= ' · several registered CIDs mentioned, not attributed';
    $isAds = $domOk || str_contains($low, 'google ads');
    if (!$isAds && !$sim) return 'ignored';
    h_x($pdo, 'INSERT INTO ga_emails (msg_id, received_at, from_addr, subject, customer_id, kind, verified, verify_note, reason, snippet, sim, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP())',
        [$msgId, gmdate('Y-m-d H:i:s', $date), h_cut($fromAddr, 190), h_cut($subject, 500), $cid, $kind, $verified ? 1 : 0, h_cut($note, 255), h_cut($reason, 500), h_cut($text, 2000), $sim ? 1 : 0]);
    $eid = (int)$pdo->lastInsertId();
    ga_log($pdo, 'email #' . $eid . " $kind" . ($cid !== '' ? ' · CID ' . ga_fmt_cid($cid) : ' · no registered CID') . ' · ' . ($verified ? 'verified' : 'NOT verified') . ' · ' . h_cut($subject, 120),
        $kind === 'SUSPENDED' || $kind === 'PAYMENT_RESTRICTED' ? 'warn' : 'info', ['run' => $runId, 'cid' => $cid]);
    if ($cid !== '' && $kind === 'REINSTATED' && $verified) {
        h_x($pdo, "UPDATE ga_alerts SET resolved_at = UTC_TIMESTAMP(), resolved_by = 'reinstated email' WHERE customer_id = ? AND resolved_at IS NULL AND rule = 'suspended' AND akey LIKE 'mail|%'", [$cid]);
    }
    if ($cid !== '' && in_array($kind, ['SUSPENDED', 'PAYMENT_RESTRICTED', 'PAYMENT_NOTICE', 'POLICY'], true)) {
        // turant account rules (pura scan ka intezaar nahi)
        $a = $pdo->query('SELECT * FROM ga_accounts WHERE customer_id = ' . $pdo->quote($cid))->fetch();
        if ($a && (int)$a['monitoring'] === 1) {
            $acc = gr_account(['cid' => $cid, 'company' => $a['company'], 'currency' => $a['currency'], 'data_age_min' => 0, 'emails' => [[
                'kind' => $kind, 'verified' => $verified, 'subject' => $subject, 'received_at' => gmdate('Y-m-d H:i:s', $date), 'reason' => $reason]]], ga_th($pdo, $cid));
            $mail = array_values(array_filter($acc['findings'], fn($f) => str_starts_with($f['key'], 'mail|')));
            ga_apply_partial($pdo, $cid, $mail, $runId, $sim);
        }
        return 'matched';
    }
    return $cid !== '' ? 'matched' : 'unmatched';
}

/** Sirf diye findings upsert (resolve kuch nahi): email aane par turant. */
function ga_apply_partial(PDO $pdo, string $cid, array $findings, ?int $runId, bool $sim = false): void
{
    $new = [];
    foreach ($findings as $f) {
        $akey = h_cut(($sim ? 'sim|' : '') . $f['key'], 190);
        if ((int)h_q1($pdo, 'SELECT COUNT(*) FROM ga_alerts WHERE akey = ? AND resolved_at IS NULL', [$akey]) > 0) continue;
        h_x($pdo, 'INSERT INTO ga_alerts (akey, customer_id, rule, level, klass, title, title_hi, what, what_hi, metrics, causes, next_step, next_hi, impact, evidence, freshness_min, first_seen, last_seen, sim, run_id)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,UTC_TIMESTAMP(),UTC_TIMESTAMP(),?,?)',
            [$akey, $cid, $f['rule'], $f['level'], $f['klass'], h_cut(($sim ? '[SIMULATED] ' : '') . $f['title'], 255), h_cut(($sim ? '[SIMULATED] ' : '') . ($f['title_hi'] ?: $f['title']), 255),
                $f['what'], $f['what_hi'] ?: $f['what'], h_json($f['metrics'] ?: new stdClass()), h_json($f['causes']), h_cut($f['next'], 1000), h_cut($f['next_hi'] ?: $f['next'], 1000),
                h_cut((string)$f['impact'], 300), h_cut((string)$f['evidence'], 300), $sim ? 1 : 0, $runId]);
        $id = (int)$pdo->lastInsertId();
        ga_log($pdo, "new {$f['level']} " . strtolower($f['klass']) . ' (email): ' . $f['title'], $f['level'] === 'CRITICAL' ? 'error' : 'warn', ['run' => $runId, 'cid' => $cid, 'alert' => $id]);
        if ($f['level'] !== 'INFO') $new[] = $id;
    }
    foreach ($new as $id) ga_notify($pdo, $id, $runId);
    if ($new && !$sim) h_x($pdo, 'UPDATE ga_accounts SET health = ? WHERE customer_id = ?', [ga_health_of($pdo, $cid, true, 0), $cid]);
}

/** MIME se padhne layak text (text/plain, warna html se tags hata kar). */
function ga_mail_text(array $hdr, string $body, int $depth = 0): string
{
    $ct = strtolower($hdr['content-type'][0] ?? 'text/plain');
    $enc = strtolower($hdr['content-transfer-encoding'][0] ?? '');
    if (str_starts_with($ct, 'multipart/') && $depth < 4 && preg_match('/boundary="?([^";]+)"?/i', $hdr['content-type'][0] ?? '', $m)) {
        $parts = explode('--' . $m[1], $body);
        $html = '';
        foreach ($parts as $p) {
            if (trim($p) === '' || str_starts_with(trim($p), '--')) continue;
            [$ph, $pb] = array_pad(preg_split("/\r?\n\r?\n/", ltrim($p, "\r\n"), 2), 2, '');
            $h = [];
            foreach (preg_split("/\r?\n(?![ \t])/", $ph) as $line) if (str_contains($line, ':')) { [$k, $v] = explode(':', $line, 2); $h[strtolower(trim($k))][] = trim($v); }
            $pct = strtolower($h['content-type'][0] ?? 'text/plain');
            if (str_starts_with($pct, 'text/plain')) return ga_mail_text($h, $pb, $depth + 1);
            if (str_starts_with($pct, 'multipart/')) { $t = ga_mail_text($h, $pb, $depth + 1); if ($t !== '') return $t; }
            if (str_starts_with($pct, 'text/html') && $html === '') $html = ga_mail_text($h, $pb, $depth + 1);
        }
        return $html;
    }
    if ($enc === 'base64') $body = (string)base64_decode(preg_replace('/\s+/', '', $body));
    elseif ($enc === 'quoted-printable') $body = quoted_printable_decode($body);
    if (preg_match('/charset="?([a-z0-9_-]+)/i', $ct, $m) && strtolower($m[1]) !== 'utf-8') { $c = @iconv($m[1], 'UTF-8//IGNORE', $body); if ($c !== false) $body = $c; }
    if (str_starts_with($ct, 'text/html')) $body = html_entity_decode(strip_tags(preg_replace('/<(br|\/p|\/div|\/tr)[^>]*>/i', "\n", $body)), ENT_QUOTES, 'UTF-8');
    return trim(preg_replace("/[ \t]+/", ' ', preg_replace("/(\r?\n\s*){3,}/", "\n\n", $body)));
}

// ================================================================ cron
function ga_cron(PDO $pdo, callable $log): void
{
    kv_set('ga_cron_last', (string)time());
    $due = fn(string $k, int $every) => time() - (int)(kv_get($k) ?? 0) >= $every;
    if ($due('ga_last_mail', GA_MAIL_EVERY) && ga_imap_on()) { $r = ga_mail_job($pdo); $log('email intake: ' . h_json($r)); }
    if ($due('ga_last_scan', GA_SCAN_EVERY - 30)) { $r = ga_scan($pdo); $log('scan: ' . h_json($r)); }
    if ($due('ga_last_deep', GA_DEEP_EVERY) && ga_ai_on()) { $r = ga_deep($pdo); $log('deep: ' . h_json($r)); }
    $loc = h_local();
    if ((int)$loc->format('G') >= (int)ga_set('ga_daily_hour') && kv_get('ga_daily_day') !== $loc->format('Y-m-d')) {
        kv_set('ga_daily_day', $loc->format('Y-m-d'));
        ga_daily_review($pdo);
        $log('daily review done');
    }
    if ($due('ga_cleanup', 86400)) {
        kv_set('ga_cleanup', (string)time());
        $pdo->exec('DELETE FROM ga_events WHERE created_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 120 DAY)');
        $pdo->exec('DELETE FROM ga_runs WHERE started_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 400 DAY)');
        $pdo->exec('DELETE FROM ga_hour WHERE day < DATE_SUB(UTC_DATE(), INTERVAL 60 DAY)');
        $pdo->exec('DELETE FROM ga_day WHERE day < DATE_SUB(UTC_DATE(), INTERVAL 400 DAY)');
        $pdo->exec('DELETE FROM ga_adgroup_day WHERE day < DATE_SUB(UTC_DATE(), INTERVAL 120 DAY)');
        $pdo->exec('DELETE FROM ga_geo_day WHERE day < DATE_SUB(UTC_DATE(), INTERVAL 120 DAY)');
        $pdo->exec('DELETE FROM ga_emails WHERE received_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 400 DAY)');
    }
}

function ga_next_scan(): ?string { $l = (int)(kv_get('ga_last_scan') ?? 0); return $l > 0 ? gmdate('Y-m-d H:i:s', $l + GA_SCAN_EVERY) : null; }

// ================================================================ account registry (sirf user ki explicit authorization se)
function ga_token_new(PDO $pdo, string $cid): string
{
    $tok = 'gah_' . bin2hex(random_bytes(20));
    h_x($pdo, 'UPDATE ga_accounts SET token_hash = ?, token_hint = ?, updated_at = UTC_TIMESTAMP() WHERE customer_id = ?', [hash('sha256', $tok), substr($tok, -4), $cid]);
    ga_log($pdo, 'script token created for CID ' . ga_fmt_cid($cid) . ' (only the hash is stored)', 'info', ['cid' => $cid]);
    return $tok;
}

function ga_account_save(PDO $pdo, array $in, string $by): array
{
    $cid = ga_cid((string)($in['cid'] ?? ''));
    if (strlen($cid) !== 10) throw new RuntimeException('Customer ID must have 10 digits (xxx-xxx-xxxx)');
    $exists = (int)h_q1($pdo, 'SELECT COUNT(*) FROM ga_accounts WHERE customer_id = ?', [$cid]) > 0;
    $company = trim((string)($in['company'] ?? ''));
    if ($company === '') throw new RuntimeException('Company name is required');
    if (!$exists && ($in['authorize'] ?? '') !== '1') throw new RuntimeException('Confirm that you are authorized to monitor this account (read-only)');
    $cur = strtoupper(substr(preg_replace('/[^A-Za-z]/', '', (string)($in['currency'] ?? '')), 0, 3));
    $tz = trim((string)($in['tz'] ?? ''));
    if ($tz !== '') { try { new DateTimeZone($tz); } catch (Throwable $e) { throw new RuntimeException('Unknown time zone: ' . $tz); } }
    $g = $pdo->prepare('SELECT name, currency, tz FROM gads_account WHERE customer_id = ?');
    $g->execute([$cid]);
    $known = $g->fetch() ?: [];
    if ($cur === '') $cur = (string)($known['currency'] ?? '');
    if ($tz === '') $tz = (string)($known['tz'] ?? '');
    $f = ['company' => h_cut($company, 160), 'account_name' => h_cut(trim((string)($in['account_name'] ?? '')) ?: (string)($known['name'] ?? ''), 255),
        'mcc_label' => h_cut(trim((string)($in['mcc_label'] ?? '')), 120), 'invoice_ref' => h_cut(trim((string)($in['invoice_ref'] ?? '')), 120),
        'credit_ref' => h_cut(trim((string)($in['credit_ref'] ?? '')), 120), 'currency' => $cur, 'tz' => $tz, 'notes' => h_cut(trim((string)($in['notes'] ?? '')), 1000),
        'src_dashboard' => ($in['src_dashboard'] ?? '1') === '0' ? 0 : 1, 'src_script' => ($in['src_script'] ?? '0') === '1' ? 1 : 0, 'src_email' => ($in['src_email'] ?? '0') === '1' ? 1 : 0];
    if ($exists) {
        $sets = implode(', ', array_map(fn($k) => "`$k` = ?", array_keys($f)));
        h_x($pdo, "UPDATE ga_accounts SET $sets, updated_at = UTC_TIMESTAMP() WHERE customer_id = ?", array_merge(array_values($f), [$cid]));
        ga_log($pdo, "account updated by $by: CID " . ga_fmt_cid($cid) . " · {$f['company']}", 'info', ['cid' => $cid]);
    } else {
        $cols = array_keys($f);
        h_x($pdo, 'INSERT INTO ga_accounts (customer_id, ' . implode(', ', $cols) . ', authorized_by, authorized_at, created_at, updated_at) VALUES (?, ' . implode(', ', array_fill(0, count($cols), '?')) . ', ?, UTC_TIMESTAMP(), UTC_TIMESTAMP(), UTC_TIMESTAMP())',
            array_merge([$cid], array_values($f), [h_cut($by, 60)]));
        ga_log($pdo, "account added with explicit authorization by $by: CID " . ga_fmt_cid($cid) . " · {$f['company']} (read-only monitoring)", 'info', ['cid' => $cid]);
    }
    $tok = null;
    if ($f['src_script'] === 1 && (string)h_q1($pdo, 'SELECT token_hash FROM ga_accounts WHERE customer_id = ?', [$cid]) === '') $tok = ga_token_new($pdo, $cid);
    return ['ok' => true, 'cid' => $cid, 'token' => $tok];
}

// ================================================================ simulated test events (sirf dev mode, saaf label)
function ga_simulate(PDO $pdo, string $cid, string $rule, string $by): array
{
    if (ga_set('ga_dev_mode') !== '1') throw new RuntimeException('Simulation works only in development mode (Settings > Development mode)');
    $a = $pdo->query('SELECT * FROM ga_accounts WHERE customer_id = ' . $pdo->quote($cid))->fetch();
    if (!$a) throw new RuntimeException('unknown account');
    $run = ga_run_new($pdo, 'simulation', 'manual', $by, true);
    $cur = $a['currency'] ?: 'INR';
    $base = ['cid' => $cid, 'campaign_id' => 'SIM-1', 'name' => '[SIMULATED] Test campaign', 'currency' => $cur, 'status' => 'ENABLED', 'status_prev' => '', 'status_changed_h' => null,
        'age_days' => 60, 'learning' => false, 'budget' => null, 'primary_status' => '', 'primary_reasons' => [], 'serving_status' => 'SERVING', 'today' => gmdate('Y-m-d'),
        'hour_now' => 15, 'reported_through' => 13, 'hourly_supported' => true, 'daily' => [], 'cpi_target' => null, 'roas_target' => null, 'fresh' => true, 'data_age_min' => 10,
        'evidence' => 'SIMULATED test data (development mode)'];
    $flat = fn(float $v) => array_fill(0, 24, $v);
    $bl = fn(float $v) => array_map(fn($d) => ['day' => "sim-$d", 'cost_h' => $flat($v), 'impr_h' => $flat(800.0)], [1, 7, 14]);
    $F = [];
    switch ($rule) {
        case 'spend_drop': $F = gr_campaign($base + ['today_cost_h' => $flat(40.0), 'today_impr_h' => $flat(800.0), 'baseline' => $bl(100.0)], GR_DEFAULTS)['findings']; break;
        case 'spend_spike': $F = gr_campaign($base + ['today_cost_h' => $flat(200.0), 'today_impr_h' => $flat(800.0), 'baseline' => $bl(100.0)], GR_DEFAULTS)['findings']; break;
        case 'zero_spend': $h = $flat(100.0); $h[12] = 0; $h[13] = 0; $F = gr_campaign($base + ['today_cost_h' => $h, 'today_impr_h' => $flat(800.0), 'baseline' => $bl(100.0)], GR_DEFAULTS)['findings']; break;
        case 'stale': $F = gr_account(['cid' => $cid, 'company' => $a['company'], 'currency' => $cur, 'data_age_min' => 400, 'sources' => []], GR_DEFAULTS)['findings']; break;
        case 'paused': $F = gr_campaign($base + ['status' => 'PAUSED', 'status_prev' => 'ENABLED', 'status_changed_h' => 1, 'today_cost_h' => [], 'baseline' => []], GR_DEFAULTS)['findings']; break;
        case 'suspended_email':
            $raw = "From: Google Ads <ads-account-noreply@google.com>\r\nSubject: [SIMULATED] Your Google Ads account has been suspended\r\nMessage-ID: <sim-" . bin2hex(random_bytes(6)) . "@mfc.test>\r\n"
                . "Date: " . gmdate('r') . "\r\nAuthentication-Results: mx.test; dkim=pass header.d=google.com; dmarc=pass header.from=google.com\r\nContent-Type: text/plain\r\n\r\n"
                . "SIMULATED TEST EMAIL (development mode).\r\nYour Google Ads account " . ga_fmt_cid($cid) . " has been suspended.\r\nReason: Circumventing systems policy (simulated)\r\n";
            $r = ga_mail_ingest($pdo, $raw, $run['id'], true);
            ga_run_end($pdo, $run, 'SIMULATED', ['accounts_checked' => 1, 'issues' => $r === 'matched' ? 1 : 0]);
            return ['ok' => true, 'result' => $r];
        default: throw new RuntimeException('unknown simulation');
    }
    $F = array_values(array_filter($F, fn($f) => $f['level'] !== 'INFO'));
    foreach ($F as &$f) { $f['title'] = '[SIMULATED] ' . $f['title']; $f['title_hi'] = '[SIMULATED] ' . $f['title_hi']; $f['campaign_id'] = $f['campaign_id'] ?? 'SIM-1'; $f['campaign'] = $f['campaign'] ?? '[SIMULATED] Test campaign'; }
    unset($f);
    $ap = ga_apply($pdo, $cid, $F, true, $run['id'], 10, true);
    ga_run_end($pdo, $run, 'SIMULATED', ['accounts_checked' => 1, 'campaigns_checked' => 1, 'issues' => count($F)]);
    return ['ok' => true, 'alerts' => count($ap['new']) + count($ap['escalated'])];
}
