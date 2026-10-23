<?php
/**
 * Google Ads monitor — integration test against a real MySQL (DEVELOPMENT ENVIRONMENT, SIMULATED DATA ONLY).
 * CI: .github/workflows/server-tests.yml (MySQL service). Local: TEST_DB_HOST=127.0.0.1 TEST_DB_NAME=mfc_test TEST_DB_USER=root TEST_DB_PASS=... php server/tests/gads_integration_test.php
 *
 * Verifies: read-only data retrieval (ingest + token), spend drop / spike, missing data (stale), campaign stopped (zero spend) + paused,
 * suspension email detection (verified vs not), correct account attribution, notification delivery + dedup, audit logs, all app views.
 */
declare(strict_types=1);
if (!getenv('TEST_DB_HOST')) { echo "TEST_DB_HOST not set: skipping integration test\n"; exit(0); }

$src = dirname(__DIR__);
$T = sys_get_temp_dir() . '/mfc_gads_test_' . getmypid();
$M = "$T/mfc_dash";
foreach (['lib', 'api', 'setup'] as $d) @mkdir("$M/$d", 0777, true);
copy("$src/tests/stub_common.php", "$M/lib/common.php");
foreach (['lib/cost.php', 'lib/health.php', 'lib/gads_rules.php', 'lib/gads_health.php', 'api/gads_health.php', 'api/gads_ingest.php', 'setup/gads_health_script.js'] as $f) copy("$src/$f", "$M/$f");
require "$M/lib/gads_health.php";

$fail = 0; $n = 0;
function ok(bool $c, string $m): void { global $fail, $n; $n++; echo ($c ? 'ok   ' : 'FAIL ') . $m . "\n"; if (!$c) $fail++; }
function q(string $sql, array $p = []): array { return h_q(db(), $sql, $p); }
function q1(string $sql, array $p = []) { return h_q1(db(), $sql, $p); }

$pdo = db();
foreach (['kv', 'gads_campaign_day', 'gads_campaign_hour', 'gads_asset', 'gads_account', 'app_alerts', 'ga_accounts', 'ga_thresholds', 'ga_camp', 'ga_day', 'ga_hour', 'ga_adgroup_day',
    'ga_geo_day', 'ga_policy', 'ga_asset', 'ga_emails', 'ga_alerts', 'ga_runs', 'ga_events', 'health_events', 'health_emails'] as $t) $pdo->exec("DROP TABLE IF EXISTS $t");
$pdo->exec("CREATE TABLE kv (k VARCHAR(64) NOT NULL PRIMARY KEY, v TEXT NOT NULL, updated_at DATETIME NOT NULL)");
$pdo->exec("CREATE TABLE gads_campaign_day (customer_id VARCHAR(20) NOT NULL, day DATE NOT NULL, campaign_id VARCHAR(30) NOT NULL, campaign VARCHAR(255) NOT NULL DEFAULT '', pkg VARCHAR(100) NOT NULL DEFAULT '',
  status VARCHAR(20) NOT NULL DEFAULT '', currency CHAR(3) NOT NULL DEFAULT 'INR', cost DECIMAL(16,4) NOT NULL DEFAULT 0, impressions BIGINT NOT NULL DEFAULT 0, clicks BIGINT NOT NULL DEFAULT 0,
  conversions DECIMAL(14,2) NOT NULL DEFAULT 0, conv_value DECIMAL(16,4) NOT NULL DEFAULT 0, updated_at DATETIME NOT NULL, PRIMARY KEY (customer_id, day, campaign_id))");
$pdo->exec("CREATE TABLE gads_campaign_hour (customer_id VARCHAR(20) NOT NULL, day DATE NOT NULL, hour TINYINT NOT NULL, campaign_id VARCHAR(30) NOT NULL, cost DECIMAL(16,4) NOT NULL DEFAULT 0,
  impressions BIGINT NOT NULL DEFAULT 0, clicks BIGINT NOT NULL DEFAULT 0, conversions DECIMAL(14,2) NOT NULL DEFAULT 0, PRIMARY KEY (customer_id, day, hour, campaign_id))");
$pdo->exec("CREATE TABLE gads_asset (customer_id VARCHAR(20) NOT NULL, campaign_id VARCHAR(30) NOT NULL, asset_id VARCHAR(30) NOT NULL, field_type VARCHAR(30) NOT NULL DEFAULT '', campaign VARCHAR(255) NOT NULL DEFAULT '',
  pkg VARCHAR(100) NOT NULL DEFAULT '', asset_type VARCHAR(30) NOT NULL DEFAULT '', label VARCHAR(20) NOT NULL DEFAULT '', name VARCHAR(500) NOT NULL DEFAULT '', url VARCHAR(500) NOT NULL DEFAULT '',
  impressions BIGINT NOT NULL DEFAULT 0, clicks BIGINT NOT NULL DEFAULT 0, cost DECIMAL(16,4) NOT NULL DEFAULT 0, conversions DECIMAL(14,2) NOT NULL DEFAULT 0, updated_at DATETIME NOT NULL,
  PRIMARY KEY (customer_id, campaign_id, asset_id, field_type))");
$pdo->exec("CREATE TABLE app_alerts (id INT UNSIGNED NOT NULL AUTO_INCREMENT, akey VARCHAR(190) NOT NULL, level TINYINT NOT NULL, rule VARCHAR(40) NOT NULL, pkg VARCHAR(100) NOT NULL DEFAULT '',
  account VARCHAR(20) NOT NULL DEFAULT '', campaign VARCHAR(255) NOT NULL DEFAULT '', title VARCHAR(255) NOT NULL, body VARCHAR(2000) NOT NULL DEFAULT '', title_hi VARCHAR(255) NOT NULL DEFAULT '',
  body_hi VARCHAR(2000) NOT NULL DEFAULT '', ai VARCHAR(3000) NOT NULL DEFAULT '', metrics TEXT NULL, fired INT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
  resolved_at DATETIME NULL, read_at DATETIME NULL, PRIMARY KEY (id))");
$pdo->exec("CREATE TABLE health_events (id INT AUTO_INCREMENT PRIMARY KEY, run_id INT NULL, issue_id INT NULL, fix_id INT NULL, pkg VARCHAR(100), level VARCHAR(10), msg VARCHAR(1000), created_at DATETIME(3))");
ga_schema($pdo);
kv_set('h_schema_v', '1');

// ---------------------------------------------------------------- SIMULATED dataset
// Account A (INR): timezone chuna gaya taaki abhi local time ~15:00 ho (test kisi bhi UTC time par chale)
$utcH = (int)gmdate('G');
$off = 15 - $utcH; if ($off > 14) $off -= 24; if ($off < -12) $off += 24;
$tzA = $off >= 0 ? "Etc/GMT-$off" : 'Etc/GMT+' . (-$off);
$A = '1112223333'; $B = '4445556666';
$nowL = new DateTime('now', new DateTimeZone($tzA));
$today = $nowL->format('Y-m-d');
$recv = gmdate('Y-m-d H:i:s', time() - 60);
$lr = new DateTime($recv, new DateTimeZone('UTC')); $lr->setTimezone(new DateTimeZone($tzA));
$R = (int)$lr->format('G') - 2;
echo "account A tz $tzA · local now " . $nowL->format('H:i') . " · R=$R\n";
$pdo->prepare('INSERT INTO gads_account (customer_id, name, currency, tz, updated_at) VALUES (?,?,?,?,?)')->execute([$A, 'SIM Account A', 'INR', $tzA, $recv]);
$pdo->prepare('INSERT INTO gads_account (customer_id, name, currency, tz, updated_at) VALUES (?,?,?,?,?)')->execute([$B, 'SIM Account B', 'USD', 'America/New_York', gmdate('Y-m-d H:i:s', time() - 5 * 3600)]);
$day = fn(int $back) => (new DateTimeImmutable($today))->modify("-$back day")->format('Y-m-d');
$insD = $pdo->prepare('REPLACE INTO gads_campaign_day (customer_id, day, campaign_id, campaign, pkg, status, currency, cost, impressions, clicks, conversions, conv_value, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)');
$insH = $pdo->prepare('REPLACE INTO gads_campaign_hour (customer_id, day, hour, campaign_id, cost, impressions, clicks, conversions) VALUES (?,?,?,?,?,?,?,?)');
$camps = ['101' => 'SIM Zero', '102' => 'SIM Drop', '103' => 'SIM Spike', '104' => 'SIM Pause', '105' => 'SIM Steady'];
foreach ($camps as $id => $nm) {
    for ($b = 1; $b <= 30; $b++) $insD->execute([$A, $day($b), $id, $nm, 'com.sim.app', 'ENABLED', 'INR', 2400, 12000, 300, 100, 1800, $recv]);
    foreach ([1, 7, 14, 21, 28] as $b) for ($h = 0; $h < 24; $h++) $insH->execute([$A, $day($b), $h, $id, 100, 500, 12, 4]);
}
for ($h = 0; $h <= $R; $h++) {
    $insH->execute([$A, $today, $h, '101', $h >= $R - 1 ? 0 : 100, $h >= $R - 1 ? 0 : 500, 0, 0]);
    $insH->execute([$A, $today, $h, '102', 30, 500, 12, 4]);
    $insH->execute([$A, $today, $h, '103', 220, 500, 12, 4]);
    $insH->execute([$A, $today, $h, '104', 100, 500, 12, 4]);
    $insH->execute([$A, $today, $h, '105', 100, 500, 12, 4]);
}
foreach ($camps as $id => $nm) $insD->execute([$A, $today, $id, $nm, 'com.sim.app', 'ENABLED', 'INR', 100 * ($R + 1), 500 * ($R + 1), 0, 0, 0, $recv]);
for ($b = 0; $b <= 10; $b++) $insD->execute([$B, $day($b), '201', 'SIM B camp', 'com.sim.b', 'ENABLED', 'USD', 50, 9000, 200, 20, 60, gmdate('Y-m-d H:i:s', time() - 5 * 3600)]);
// LOW creative (dashboard script table)
$pdo->prepare("INSERT INTO gads_asset VALUES (?, '105', 'a1', 'HEADLINE', 'SIM Steady', 'com.sim.app', 'TEXT', 'LOW', 'Old headline', '', 5000, 20, 100, 2, ?)")->execute([$A, $recv]);

// registry: user ki explicit authorization se
$r1 = ga_account_save($pdo, ['cid' => '111-222-3333', 'company' => 'SIM Company A', 'mcc_label' => 'MCC-A (not linked)', 'invoice_ref' => 'INV-A', 'credit_ref' => 'CL-A', 'authorize' => '1'], 'test-user');
ok($r1['ok'] && $r1['token'] === null, 'account A added with authorization (dashboard source only, no token)');
try { ga_account_save($pdo, ['cid' => '444-555-6666', 'company' => 'SIM B'], 'test-user'); ok(false, 'account without authorization must be refused'); }
catch (Throwable $e) { ok(str_contains($e->getMessage(), 'authorized'), 'account without explicit authorization refused'); }
ga_account_save($pdo, ['cid' => '444-555-6666', 'company' => 'SIM Company B', 'authorize' => '1'], 'test-user');
ok(q1('SELECT currency FROM ga_accounts WHERE customer_id = ?', [$A]) === 'INR' && q1('SELECT currency FROM ga_accounts WHERE customer_id = ?', [$B]) === 'USD', 'currency taken per account from Google data (INR / USD separate)');
$pdo->prepare('REPLACE INTO ga_thresholds (scope, k, v, updated_at) VALUES (?, ?, ?, UTC_TIMESTAMP())')->execute(["camp:$A:105", 'cpi_target', '15']);

// ---------------------------------------------------------------- scan 1
$s1 = ga_scan($pdo, 'manual', 'test-user');
ok($s1['ok'], 'scan 1 ran: ' . ($s1['status'] ?? ''));
$al = fn(string $cid, string $rule) => q("SELECT * FROM ga_alerts WHERE customer_id = ? AND rule = ? AND resolved_at IS NULL", [$cid, $rule]);
$z = $al($A, 'zero_spend');
ok(count($z) === 1 && $z[0]['campaign_id'] === '101' && $z[0]['level'] === 'CRITICAL' && $z[0]['klass'] === 'CONFIRMED', 'campaign stopped: zero_spend CRITICAL on 101 (account A)');
$d = $al($A, 'spend_drop');
ok(count($d) === 1 && $d[0]['campaign_id'] === '102' && $d[0]['level'] === 'WARNING', 'sudden spend drop: WARNING on 102');
$s = $al($A, 'spend_spike');
ok(count($s) === 1 && $s[0]['campaign_id'] === '103' && $s[0]['level'] === 'CRITICAL', 'sudden spend spike: CRITICAL on 103');
ok(!$al($A, 'spend_drop') || !array_filter($al($A, 'spend_drop'), fn($x) => $x['campaign_id'] === '105'), 'steady campaign 105: no spend alert');
$cpi = $al($A, 'cpi_high');
ok(count($cpi) === 1 && $cpi[0]['campaign_id'] === '105', 'CPI 24 vs target 15 (+60%) -> cpi_high on 105');
ok(count($al($A, 'creative_low')) === 1, 'LOW creative -> INFO creative_low');
$st = $al($B, 'stale');
ok(count($st) === 1 && $st[0]['level'] === 'WARNING', 'missing data: account B STALE DATA warning');
ok(count(q("SELECT * FROM ga_alerts WHERE customer_id = ? AND rule IN ('zero_spend','spend_drop','suspended')", [$B])) === 0, 'stale account B: no zero-spend / suspension guess');
ok(count(q("SELECT * FROM ga_alerts WHERE rule = 'suspended'")) === 0, 'no suspension without direct evidence');
$run = q("SELECT * FROM ga_runs WHERE type = 'scan' ORDER BY id DESC LIMIT 1")[0];
ok($run['status'] === 'PARTIAL' && (int)$run['failed_checks'] >= 1 && (int)$run['accounts_checked'] === 2, 'audit: scan PARTIAL (B stale), never SUCCESS when a fetch is stale');
ok($run['finished_at'] !== null && $run['next_run_at'] !== null && (int)$run['campaigns_checked'] >= 5, 'audit: run has end time, next run, campaigns checked');
$src = json_decode($run['sources'], true);
ok(count($src) === 2 && $src[0]['state'] !== $src[1]['state'], 'audit: per-account data source status + freshness recorded');
ok((int)q1('SELECT COUNT(*) FROM ga_events WHERE run_id = ?', [$run['id']]) >= 5, 'audit: events logged for the run');
$push = (int)q1("SELECT COUNT(*) FROM app_alerts WHERE rule LIKE 'gah_%'");
ok($push >= 4, "notification delivery: $push app alerts (critical + warning)");
ok((int)q1("SELECT COUNT(*) FROM app_alerts WHERE rule = 'gah_creative_low'") === 0, 'INFO alerts are not pushed');
$aa = q("SELECT * FROM app_alerts WHERE rule = 'gah_zero_spend'")[0];
ok($aa['account'] === $A && str_contains($aa['body'], 'SIM Company A') && json_decode($aa['metrics'], true)['ga_alert_id'] === (int)$z[0]['id'], 'notification carries company, CID and alert id');
ok(q1('SELECT health FROM ga_accounts WHERE customer_id = ?', [$A]) === 'CRITICAL' && q1('SELECT health FROM ga_accounts WHERE customer_id = ?', [$B]) === 'STALE', 'account health: A CRITICAL, B STALE');

// ---------------------------------------------------------------- scan 2: dedup + paused + resolve
$pdo->prepare("UPDATE gads_campaign_day SET status = 'PAUSED' WHERE customer_id = ? AND campaign_id = '104'")->execute([$A]);
$pdo->prepare("UPDATE gads_campaign_hour SET cost = 100 WHERE customer_id = ? AND campaign_id = '102' AND day = ?")->execute([$A, $today]);
kv_set('ga_last_scan', '0');
ga_scan($pdo, 'manual', 'test-user');
ok((int)q1("SELECT COUNT(*) FROM app_alerts WHERE rule = 'gah_zero_spend'") === 1, 'dedup: zero_spend not notified twice');
ok(count($al($A, 'zero_spend')) === 1 && (int)q1("SELECT COUNT(*) FROM ga_alerts WHERE rule = 'zero_spend'") === 1, 'dedup: still one zero_spend alert row');
$p = $al($A, 'paused_removed');
ok(count($p) === 1 && $p[0]['campaign_id'] === '104', 'campaign paused detected (ENABLED -> PAUSED)');
ok(q1("SELECT resolved_at FROM ga_alerts WHERE rule = 'spend_drop' AND campaign_id = '102'") !== null, 'spend drop auto-resolved when spend recovered');

// ---------------------------------------------------------------- suspension emails
$mail = function (string $from, string $auth, string $subj, string $body, string $id) {
    return "From: Google Ads <$from>\r\nTo: monitor@example.test\r\nSubject: $subj\r\nMessage-ID: <$id@test>\r\nDate: " . gmdate('r') . "\r\n"
        . ($auth !== '' ? "Authentication-Results: mx.test; $auth\r\n" : '') . "Content-Type: text/plain; charset=UTF-8\r\n\r\n$body\r\n";
};
$r = ga_mail_ingest($pdo, $mail('ads-account-noreply@google.com', 'dkim=pass header.d=google.com; dmarc=pass header.from=google.com',
    'Your Google Ads account has been suspended', "SIMULATED TEST\r\nYour Google Ads account 111-222-3333 has been suspended.\r\nReason: Unpaid balance (simulated)", 'm1'));
ok($r === 'matched', 'verified suspension email matched to account A (' . $r . ')');
$sa = $al($A, 'suspended');
ok(count($sa) === 1 && $sa[0]['level'] === 'CRITICAL' && $sa[0]['klass'] === 'CONFIRMED' && str_contains($sa[0]['what'], 'Unpaid balance'), 'suspension email -> CRITICAL CONFIRMED with official reason');
ok((int)q1("SELECT COUNT(*) FROM app_alerts WHERE rule = 'gah_suspended'") === 1, 'suspension notified immediately (no scan wait)');
$r = ga_mail_ingest($pdo, $mail('random@notgoogle.example', '', 'Google Ads account suspended', 'Google Ads account 444-555-6666 suspended', 'm2'));
$sb = $al($B, 'suspended');
ok(count($sb) === 1 && $sb[0]['klass'] === 'SUSPECTED' && $sb[0]['level'] === 'WARNING', 'unverified sender -> only SUSPECTED WARNING');
$r = ga_mail_ingest($pdo, $mail('ads-account-noreply@google.com', 'dkim=pass header.d=google.com', 'Account suspended', 'Google Ads account 999-888-7777 suspended', 'm3'));
ok($r === 'unmatched' && count(q("SELECT * FROM ga_alerts WHERE customer_id = '9998887777'")) === 0, 'email for unregistered CID is never attributed');
$r = ga_mail_ingest($pdo, $mail('ads-account-noreply@google.com', 'dkim=pass header.d=google.com', 'Accounts suspended', 'Google Ads accounts 111-222-3333 and 444-555-6666 suspended', 'm4'));
ok(q1("SELECT customer_id FROM ga_emails WHERE msg_id = 'm4@test'") === '', 'email naming two accounts is not attributed to either');
ok(ga_mail_ingest($pdo, $mail('ads-account-noreply@google.com', 'dkim=pass header.d=google.com', 'Your Google Ads account has been suspended', 'x', 'm1')) === 'duplicate', 'same email twice -> duplicate');
$r = ga_mail_ingest($pdo, $mail('ads-account-noreply@google.com', 'dkim=pass header.d=google.com', 'Your Google Ads account has been reinstated', 'Account 111-222-3333 is no longer suspended', 'm5'));
ok(count($al($A, 'suspended')) === 0, 'verified reinstatement email closes the suspension alert');

// ---------------------------------------------------------------- HTTP: ingest (token) + every app view
$port = 18000 + getmypid() % 1000;
$env = 'TEST_DB_HOST=' . escapeshellarg((string)getenv('TEST_DB_HOST')) . ' TEST_DB_NAME=' . escapeshellarg((string)getenv('TEST_DB_NAME')) . ' TEST_DB_USER=' . escapeshellarg((string)getenv('TEST_DB_USER')) . ' TEST_DB_PASS=' . escapeshellarg((string)getenv('TEST_DB_PASS'));
$srv = proc_open("$env exec php -S 127.0.0.1:$port -t " . escapeshellarg($M), [1 => ['file', "$T/server.log", 'a'], 2 => ['file', "$T/server.log", 'a']], $pipes);
usleep(800000);
$http = function (string $method, string $path, $body = null) use ($port) {
    $ch = curl_init("http://127.0.0.1:$port/$path");
    curl_setopt_array($ch, [CURLOPT_RETURNTRANSFER => true, CURLOPT_CUSTOMREQUEST => $method, CURLOPT_TIMEOUT => 60, CURLOPT_HTTPHEADER => ['Content-Type: application/json']]);
    if ($body !== null) curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($body));
    $r = curl_exec($ch); $c = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
    return [$c, json_decode((string)$r, true), (string)$r];
};
[$c, $j] = $http('POST', 'api/gads_health.php?action=account_save', ['cid' => '777-888-9999', 'company' => 'SIM Company C', 'src_script' => '1', 'authorize' => '1']);
$tok = (string)($j['token'] ?? '');
ok($c === 200 && str_starts_with($tok, 'gah_'), 'app: account C added with read-only script source -> token shown once');
ok(q1("SELECT token_hash FROM ga_accounts WHERE customer_id = '7778889999'") === hash('sha256', $tok), 'only the token hash is stored');
[$c] = $http('POST', 'api/gads_ingest.php', ['customer_id' => '777-888-9999', 'token' => 'gah_wrong', 'action' => 'hello']);
ok($c === 403, 'ingest with wrong token -> 403');
[$c] = $http('POST', 'api/gads_ingest.php', ['customer_id' => '123-123-1234', 'token' => $tok, 'action' => 'hello']);
ok($c === 403, 'ingest for unregistered CID -> 403 (no unauthorized accounts)');
[$c, $j] = $http('POST', 'api/gads_ingest.php', ['customer_id' => '777-888-9999', 'token' => $tok, 'action' => 'hello', 'account' => ['manager' => true]]);
ok($c === 400, 'script running in an MCC is rejected (no linking)');
[$c, $j] = $http('POST', 'api/gads_ingest.php', ['customer_id' => '777-888-9999', 'token' => $tok, 'action' => 'hello']);
ok($c === 200 && $j['backfill_days'] === 35, 'hello asks for 35-day backfill on a new account');
$days = []; $hours = [];
// account C ka "aaj" uske apne tz (Europe/Berlin) me: A ka din (Etc/GMT-x) 22-24 UTC ke beech Berlin se ek din peeche hota hai
$todayCb = (new DateTime('now', new DateTimeZone('Europe/Berlin')))->format('Y-m-d');
$dayC = fn(int $back) => (new DateTimeImmutable($todayCb))->modify("-$back day")->format('Y-m-d');
for ($b = 0; $b <= 10; $b++) $days[] = ['day' => $dayC($b), 'campaign_id' => '301', 'cost' => 1000, 'impressions' => 9000, 'clicks' => 100, 'conversions' => 40, 'conv_value' => 900];
$payload = ['customer_id' => '777-888-9999', 'token' => $tok, 'action' => 'data', 'script_version' => '1.0',
    'account' => ['name' => 'SIM C', 'currency' => 'EUR', 'tz' => 'Europe/Berlin', 'status' => 'ENABLED', 'manager' => false],
    'campaigns' => [['campaign_id' => '301', 'name' => 'SIM C camp', 'status' => 'ENABLED', 'primary_status' => 'ELIGIBLE', 'primary_reasons' => [], 'channel' => 'MULTI_CHANNEL', 'bidding' => 'TARGET_CPA', 'target_cpa' => 20, 'budget' => 1500, 'start_date' => '2025-03-01']],
    'days' => $days, 'hours' => $hours,
    'adgroups' => [['day' => $dayC(1), 'campaign_id' => '301', 'ad_group_id' => '3011', 'name' => 'SIM AG', 'status' => 'ENABLED', 'cost' => 1000, 'impressions' => 9000, 'clicks' => 100, 'conversions' => 40, 'conv_value' => 900]],
    'geo' => [['day' => $day(1), 'campaign_id' => '301', 'country' => 'DE', 'cost' => 600, 'impressions' => 5000, 'clicks' => 60, 'conversions' => 20, 'conv_value' => 500]],
    'policy' => [['campaign_id' => '301', 'campaign' => 'SIM C camp', 'ad_group_id' => '3011', 'ad_id' => '90001', 'approval' => 'DISAPPROVED', 'review' => 'REVIEWED', 'topics' => ['MISLEADING_CONTENT (PROHIBITED)']]],
    'errors' => [['section' => 'assets', 'error' => 'SIMULATED query error']]];
[$c, $j] = $http('POST', 'api/gads_ingest.php', $payload);
ok($c === 200 && ($j['n'] ?? 0) >= 13, 'read-only script data stored (' . ($j['n'] ?? 0) . ' rows)');
ok(q1("SELECT start_date FROM ga_camp WHERE customer_id = '7778889999' AND campaign_id = '301'") === '2025-03-01', 'campaign start date stored');
$p2 = $payload; $p2['campaigns'][0]['start_date'] = ''; $http('POST', 'api/gads_ingest.php', $p2);   // script v1.1 bina date wala fallback
ok(q1("SELECT start_date FROM ga_camp WHERE customer_id = '7778889999' AND campaign_id = '301'") === '2025-03-01', 'run without a start date keeps the stored one (missing != cleared)');
ok(q1("SELECT currency FROM ga_accounts WHERE customer_id = '7778889999'") === 'EUR' && q1("SELECT script_status FROM ga_accounts WHERE customer_id = '7778889999'") === 'ENABLED', 'account currency/status from Google stored per account');
kv_set('ga_last_scan', '0');
ga_scan($pdo, 'manual', 'test-user', '7778889999');
ok(count($al('7778889999', 'disapproved')) === 1, 'ad disapproval detected from policy snapshot');
ok(count($al('7778889999', 'source_error')) === 1, 'reporting connection error recorded (missing section != zero)');
ok(count($al('7778889999', 'cpi_high')) === 1 && str_contains($al('7778889999', 'cpi_high')[0]['what'], 'Google Ads target CPA'), 'CPI 25 vs Google tCPA 20 (+25.0%)... checked against target source');
ok(count($al('7778889999', 'budget_used')) === 0, 'budget 67% used -> no budget alert');
$payload['days'][0]['cost'] = 1530;   // aaj: 1530 / 1500 = 102%
$http('POST', 'api/gads_ingest.php', $payload);
ga_scan($pdo, 'manual', 'test-user', '7778889999');
$bu = $al('7778889999', 'budget_used');
ok(count($bu) === 1 && $bu[0]['level'] === 'CRITICAL' && str_contains($bu[0]['title'], '102%'), 'today 102% of daily budget -> red (CRITICAL) budget alert');
$payload['days'][0]['cost'] = 1000;
$http('POST', 'api/gads_ingest.php', $payload);
ga_scan($pdo, 'manual', 'test-user', '7778889999');
ok(count($al('7778889999', 'budget_used')) === 0, 'budget alert resolves when spend is back under budget');
$payload['account']['status'] = 'SUSPENDED'; $payload['errors'] = [];
$http('POST', 'api/gads_ingest.php', $payload);
ga_scan($pdo, 'manual', 'test-user', '7778889999');
$sc = $al('7778889999', 'suspended');
ok(count($sc) === 1 && $sc[0]['klass'] === 'CONFIRMED' && str_contains($sc[0]['evidence'], 'customer.status'), 'verified status SUSPENDED -> CRITICAL with customer.status evidence');
ok(count(q("SELECT * FROM ga_alerts WHERE customer_id <> '7778889999' AND campaign_id = '301'")) === 0, 'attribution: C campaign alerts only on C');

foreach (['overview', 'filters', 'account&cid=1112223333', 'alerts&group=status', 'alerts&group=all&status=open', 'alert&id=' . $z[0]['id'], 'monitor&kind=spend', 'monitor&kind=cpi',
    'monitor&kind=roas', 'monitor&kind=cpi&country=DE', 'campaigns&range=30d', 'campaigns&adgroup=3011', 'campaign&cid=7778889999&campaign_id=301', 'campaign&cid=1112223333&campaign_id=101',
    'creatives', 'recs', 'live', 'run&id=' . $run['id'], 'settings', 'script&cid=7778889999', 'overview&currency=USD', 'alerts&account=4445556666&status=open'] as $v) {
    [$c, $j, $raw] = $http('GET', "api/gads_health.php?view=$v");
    ok($c === 200 && ($j['ok'] ?? false) === true, "view $v" . ($c !== 200 || !($j['ok'] ?? false) ? ' -> ' . substr($raw, 0, 300) : ''));
    if ($v === 'overview') ok(count($j['currency_totals']) === 3 && !isset($j['total_spend']), 'overview: totals per currency only (INR / USD / EUR separate)');
    if ($v === 'overview') ok(str_contains($raw, '"app_budgets":[]'), 'overview: app_budgets = [] while no app has a daily budget (C budget has no pkg)');
    if ($v === 'overview&currency=USD') ok(count($j['accounts']) === 1 && $j['accounts'][0]['cid'] === $B, 'currency filter');
    if ($v === 'script&cid=7778889999') ok(str_contains($j['script'], "'777-888-9999'") && !str_contains($j['script'], $tok), 'script text filled with CID, token never sent back');
    if ($v === 'monitor&kind=spend') { $r103 = array_values(array_filter($j['rows'], fn($x) => $x['campaign_id'] === '103'))[0] ?? null; ok($r103 && $r103['pace_pct'] > 60, 'spend monitor shows pace vs usual for 103'); }
}
foreach ([['setting', ['key' => 'ga_dev_mode', 'value' => '1']], ['simulate', ['cid' => $A, 'rule' => 'spend_drop']], ['simulate', ['cid' => $A, 'rule' => 'suspended_email']],
    ['threshold', ['scope' => 'global', 'key' => 'drop_pct', 'value' => '40']], ['threshold', ['scope' => "camp:$A:101", 'key' => 'zero_hours', 'value' => '3']], ['ack', ['id' => $z[0]['id']]],
    ['test_notify', []], ['sim_clear', []], ['account_toggle', ['cid' => $B, 'field' => 'push', 'value' => '0']]] as [$act, $body]) {
    [$c, $j, $raw] = $http('POST', "api/gads_health.php?action=$act", $body);
    ok($c === 200 && ($j['ok'] ?? false), "action $act" . ($c !== 200 ? ' -> ' . substr($raw, 0, 300) : ''));
}
ok((int)q1('SELECT COUNT(*) FROM ga_alerts WHERE sim = 1') === 0 && (int)q1("SELECT COUNT(*) FROM ga_runs WHERE type = 'simulation' AND sim = 1") >= 1, 'simulated alerts labelled + cleared; simulation runs stay in the audit log');
[$c] = $http('POST', 'api/gads_health.php?action=threshold', ['scope' => 'global', 'key' => 'drop_pct', 'value' => 'abc']);
ok($c === 400, 'bad threshold value refused');

// ---------------------------------------------------------------- overview app_budgets: (pkg, currency) ka aaj ka daily budget vs us pkg ka aaj ka kharcha
$C = '7778889999'; $D = '2223334444';
$pdo->prepare('INSERT INTO gads_account (customer_id, name, currency, tz, updated_at) VALUES (?,?,?,?,?)')->execute([$D, 'SIM Account D', 'INR', $tzA, $recv]);
ga_account_save($pdo, ['cid' => '222-333-4444', 'company' => 'SIM Company D', 'authorize' => '1'], 'test-user');
$tday = fn(string $cid) => (new DateTime('now', ga_acct_tz($pdo, ga_accounts($pdo)[$cid])))->format('Y-m-d');
$todayB = $tday($B); $todayC = $tday($C);
$gc = $pdo->prepare("INSERT INTO ga_camp (customer_id, campaign_id, name, status, budget, budget_period, pkg, updated_at) VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP())
  ON DUPLICATE KEY UPDATE status = VALUES(status), budget = VALUES(budget), budget_period = VALUES(budget_period), pkg = VALUES(pkg)");
foreach ([[$A, '101', 'com.sim.app', 'ENABLED', 1000, 'DAILY'], [$A, '102', 'com.sim.app', 'ENABLED', 500, ''], [$A, '103', 'com.sim.app', 'ENABLED', 2000, 'CUSTOM_PERIOD'],
    [$A, '104', 'com.sim.app', 'PAUSED', 700, 'DAILY'], [$A, '105', 'com.sim.app', 'ENABLED', null, 'DAILY'], [$A, '106', 'com.sim.two', 'ENABLED', 300, 'daily'],
    [$A, '107', 'com.sim.two', 'ENABLED', 0, 'DAILY'], [$A, '108', '', 'ENABLED', 400, 'DAILY'], [$A, '109', '', 'ENABLED', 250, 'DAILY'], [$D, '401', 'com.sim.app', 'ENABLED', 200, 'DAILY'],
    [$B, '201', 'com.sim.b', 'ENABLED', 80, 'DAILY'], [$B, '202', 'com.sim.app', 'ENABLED', 40, 'DAILY'], [$B, '203', 'com.sim.b', 'ENABLED', 25, 'DAILY'], [$B, '204', 'com.sim.b', 'REMOVED', 90, 'DAILY'],
    [$C, '301', 'com.sim.c', 'ENABLED', 1500, '']] as [$cid, $id, $pkg, $st, $bu, $per]) $gc->execute([$cid, $id, "SIM $id", $st, $bu, $per, $pkg]);
// aaj ke dashboard rows (106 ka kal wala 999 nahi ginna; 109: ga_camp me pkg nahi, dashboard row se)
foreach ([[$A, $today, '106', 'com.sim.two', 'INR', 120], [$A, $day(1), '106', 'com.sim.two', 'INR', 999], [$A, $today, '107', 'com.sim.two', 'INR', 15], [$A, $today, '108', '', 'INR', 90],
    [$A, $today, '109', 'com.sim.two', 'INR', 60], [$D, $today, '401', 'com.sim.app', 'INR', 80], [$B, $todayB, '202', 'com.sim.app', 'USD', 30]] as [$cid, $dd, $id, $pkg, $cu, $cost])
    $insD->execute([$cid, $dd, $id, "SIM $id", $pkg, 'ENABLED', $cu, $cost, 1000, 10, 1, 0, $recv]);
$spA = (float)q1("SELECT SUM(cost) FROM gads_campaign_day WHERE customer_id = ? AND day = ? AND pkg = 'com.sim.app'", [$A, $today]);
$spB = (float)q1("SELECT COALESCE(SUM(cost), 0) FROM gads_campaign_day WHERE customer_id = ? AND day = ? AND pkg = 'com.sim.b'", [$B, $todayB]);
$spC = (float)q1('SELECT COALESCE(SUM(cost), 0) FROM ga_day WHERE customer_id = ? AND day = ?', [$C, $todayC]);
$exp = [$A => [['com.sim.app', 'INR', 1500, $spA, 2], ['com.sim.two', 'INR', 550, 195, 2]], $D => [['com.sim.app', 'INR', 200, 80, 1]],
    $B => [['com.sim.app', 'USD', 40, 30, 1], ['com.sim.b', 'USD', 105, $spB, 2]], $C => [['com.sim.c', 'EUR', 1500, $spC, 1]]];
$want = function (array $cids) use ($exp): array {
    $m = [];
    foreach ($cids as $cid) foreach ($exp[$cid] ?? [] as [$p, $cu, $b, $s, $k]) { $m["$p|$cu"] ??= [$p, $cu, 0.0, 0.0, 0]; $m["$p|$cu"][2] += $b; $m["$p|$cu"][3] += $s; $m["$p|$cu"][4] += $k; }
    ksort($m);
    return array_values($m);
};
$same = function (array $j) use ($want): bool {
    $g = $j['app_budgets'] ?? null; $w = $want(array_column($j['accounts'] ?? [], 'cid'));
    if (!is_array($g) || count($g) !== count($w)) return false;
    foreach ($w as $i => [$p, $cu, $b, $s, $k]) {
        $r = $g[$i];
        if ($r['pkg'] !== $p || $r['currency'] !== $cu || abs((float)$r['budget_today'] - $b) > 0.005 || abs((float)$r['spend_today'] - $s) > 0.005 || $r['campaigns'] !== $k) return false;
    }
    return true;
};
[$c, $j] = $http('GET', 'api/gads_health.php?view=overview');
$row = fn(string $p, string $cu) => array_values(array_filter($j['app_budgets'] ?? [], fn($x) => $x['pkg'] === $p && $x['currency'] === $cu))[0] ?? [];
ok($c === 200 && count($j['accounts']) === 4 && count($j['app_budgets']) === 5 && $same($j), 'app_budgets: 5 rows (pkg, currency), sorted, sums + spend per pkg match' . ($same($j) ? '' : ': ' . json_encode($j['app_budgets'] ?? null)));
ok(($row('com.sim.app', 'INR')['budget_today'] ?? 0) == 1700 && ($row('com.sim.app', 'INR')['campaigns'] ?? 0) === 3 && ($row('com.sim.app', 'USD')['budget_today'] ?? 0) == 40,
    'app_budgets: same pkg in 2 INR accounts adds up (A + D), its USD campaign stays a separate row');
ok(($row('com.sim.app', 'INR')['budget_today'] ?? 0) == 1700 && ($row('com.sim.b', 'USD')['budget_today'] ?? 0) == 105, "app_budgets: PAUSED / REMOVED / non-daily / zero / null budgets left out, '' + lowercase 'daily' periods count");
ok(abs(($row('com.sim.two', 'INR')['spend_today'] ?? 0) - 195) < 0.005 && ($row('com.sim.two', 'INR')['budget_today'] ?? 0) == 550,
    'app_budgets: pkg spend = all its campaigns today (unbudgeted 107 counts, yesterday not); pkg from dashboard row when ga_camp has none (109)');
ok(!array_filter($j['app_budgets'] ?? [], fn($x) => $x['pkg'] === ''), 'app_budgets: campaign without pkg (108) makes no row');
ok(($row('com.sim.c', 'EUR')['budget_today'] ?? 0) == 1500 && abs(($row('com.sim.c', 'EUR')['spend_today'] ?? -1) - $spC) < 0.005, 'app_budgets: script-only campaign (pkg from ga_camp, spend from ga_day) -> EUR row of its own');
$acA = array_values(array_filter($j['accounts'] ?? [], fn($x) => $x['cid'] === $A))[0] ?? [];
ok(abs(($acA['budget_today'] ?? 0) - 2450) < 0.005, 'account A budget_today (ga_budget_today) = its app rows 2050 + no-pkg 108 (400): same budget rule');
foreach (['currency=USD' => [$B], 'currency=INR' => [$A, $D], 'account=' . $A => [$A], 'company=' . rawurlencode('SIM Company D') => [$D]] as $qs => $only) {
    [$c, $j] = $http('GET', "api/gads_health.php?view=overview&$qs");
    $cids = array_column($j['accounts'] ?? [], 'cid'); sort($cids); sort($only);
    ok($c === 200 && $cids === $only && $same($j) && count($j['app_budgets']) > 0, "app_budgets follow the overview filter $qs" . ($same($j) ? '' : ': ' . json_encode($j['app_budgets'] ?? null)));
}
[$c, $j] = $http('GET', 'api/gads_health.php?view=overview&status=critical');
$cids = array_column($j['accounts'] ?? [], 'cid');
ok($c === 200 && in_array($A, $cids, true) && !in_array($D, $cids, true) && $same($j), 'app_budgets: status filter -> only the listed accounts');
[$c, $j, $raw] = $http('GET', 'api/gads_health.php?view=overview&currency=GBP');
ok($c === 200 && str_contains($raw, '"app_budgets":[]'), 'app_budgets: [] when no listed account has budgets');
proc_terminate($srv);
$log = (string)@file_get_contents("$T/server.log");
ok(!preg_match('/PHP (Warning|Fatal|Deprecated|Notice)/', $log), 'no PHP warnings / errors in server log' . (preg_match('/PHP (Warning|Fatal|Deprecated|Notice)[^\n]*/', $log, $mm) ? ': ' . $mm[0] : ''));

echo "\n$n checks, $fail failed\n";
exit($fail > 0 ? 1 : 0);
