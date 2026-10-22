<?php
/**
 * Play signals — integration test (DEVELOPMENT ENVIRONMENT, SIMULATED DATA ONLY, koi asli Play / mail account nahi):
 *   1) per-version vitals (canned Play Reporting API responses, versionCode dimension, failure tolerance)
 *   2) alerts.php new_version rule (day_ver tracker data, 5-device threshold, never re-fires)
 *   3) lib/playmail.php against a local fake IMAP server over TLS (self-signed cert): literals, MIME, classify, dedupe, 30-mail cap, status API
 * Local: TEST_DB_HOST=127.0.0.1 TEST_DB_PORT=3306 TEST_DB_NAME=mfc_test TEST_DB_USER=root TEST_DB_PASS=... php server/tests/play_signals_test.php
 */
declare(strict_types=1);
if (!getenv('TEST_DB_HOST')) { echo "TEST_DB_HOST not set: skipping integration test\n"; exit(0); }

$src = dirname(__DIR__);
$T = sys_get_temp_dir() . '/mfc_play_test_' . getmypid();
$M = "$T/mfc_dash";
foreach (['lib', 'api', 'secrets'] as $d) @mkdir("$M/$d", 0777, true);
// stub common.php + canned HTTP (sirf is test ke process me; subprocess me network band)
$stub = (string)file_get_contents("$src/tests/stub_common.php");
$httpOld = "function http_json(string \$method, string \$url, ?string \$body = null, array \$headers = [], int \$timeout = 30): array { return [0, '', 'network disabled in tests']; }";
$httpNew = "function http_json(string \$method, string \$url, ?string \$body = null, array \$headers = [], int \$timeout = 30): array { return isset(\$GLOBALS['FAKE_HTTP']) ? (\$GLOBALS['FAKE_HTTP'])(\$method, \$url, \$body) : [0, '', 'network disabled in tests']; }";
if (!str_contains($stub, $httpOld)) { echo "FAIL stub http_json signature changed\n"; exit(1); }
$stub = str_replace($httpOld, $httpNew, $stub);
// TEST_DB_PORT (optional)
$stub = str_replace("'mysql:host=' . getenv('TEST_DB_HOST') . ';dbname='", "'mysql:host=' . getenv('TEST_DB_HOST') . (getenv('TEST_DB_PORT') ? ';port=' . getenv('TEST_DB_PORT') : '') . ';dbname='", $stub);
file_put_contents("$M/lib/common.php", $stub);
file_put_contents("$M/lib/agg.php", "<?php\n");   // asli server par dashboard ka agg.php hota hai
foreach (['lib/cost.php', 'lib/health.php', 'lib/playmail.php', 'api/health.php', 'api/alerts.php'] as $f) copy("$src/$f", "$M/$f");

$fail = 0; $n = 0;
function ok(bool $c, string $m): void { global $fail, $n; $n++; echo ($c ? 'ok   ' : 'FAIL ') . $m . "\n"; if (!$c) $fail++; }

// ---------------------------------------------------------------- self-signed TLS cert (fake IMAP server ke liye)
$pk = openssl_pkey_new(['private_key_bits' => 2048, 'private_key_type' => OPENSSL_KEYTYPE_RSA]);
$csr = openssl_csr_new(['commonName' => '127.0.0.1'], $pk, ['digest_alg' => 'sha256']);
$x509 = openssl_csr_sign($csr, null, $pk, 2, ['digest_alg' => 'sha256']);
openssl_x509_export($x509, $certPem); openssl_pkey_export($pk, $keyPem);
file_put_contents("$T/ca.pem", $certPem);
file_put_contents("$T/server.pem", $certPem . $keyPem);
// Play service account (fake key, sirf JWT sign ke liye)
$saKey = openssl_pkey_new(['private_key_bits' => 2048, 'private_key_type' => OPENSSL_KEYTYPE_RSA]);
openssl_pkey_export($saKey, $saPem);
file_put_contents("$M/secrets/play_c1.json", json_encode(['client_email' => 'test@test.iam.gserviceaccount.com', 'private_key' => $saPem]));

function write_cfg(string $M, string $pass, int $port): void
{
    $cfg = ['consoles' => [['id' => 'c1', 'name' => 'Test console', 'sa' => 'play_c1.json']],
        'play_mail' => ['host' => '127.0.0.1', 'port' => $port, 'user' => 'playmail@test.local', 'pass' => $pass, 'forwarders' => ['sagar.test@gmail.com', 'sagar.second@gmail.com']]];
    file_put_contents("$M/health_config.php", '<?php return ' . var_export($cfg, true) . ';');
}

require "$M/lib/health.php";
$pdo = db();
foreach (['kv', 'day_ver', 'day_app', 'events', 'app_alerts', 'health_consoles', 'health_apps', 'health_runs', 'health_events', 'health_issues', 'health_fixes', 'health_fix_steps', 'health_builds', 'health_emails'] as $t) $pdo->exec("DROP TABLE IF EXISTS $t");
$pdo->exec("CREATE TABLE kv (k VARCHAR(64) NOT NULL PRIMARY KEY, v TEXT NOT NULL, updated_at DATETIME NOT NULL)");
$pdo->exec("CREATE TABLE day_ver (day DATE NOT NULL, pkg VARCHAR(100) NOT NULL, ver VARCHAR(40) NOT NULL, did CHAR(36) NOT NULL, PRIMARY KEY (day, pkg, ver, did))");
$pdo->exec("CREATE TABLE day_app (day DATE NOT NULL, pkg VARCHAR(100) NOT NULL, installs INT NOT NULL DEFAULT 0, uninstalls INT NOT NULL DEFAULT 0, crashes INT NOT NULL DEFAULT 0,
  dau INT NOT NULL DEFAULT 0, sessions INT NOT NULL DEFAULT 0, imps INT NOT NULL DEFAULT 0, rev_usd DOUBLE NOT NULL DEFAULT 0, purchase_inr DOUBLE NOT NULL DEFAULT 0, PRIMARY KEY (day, pkg))");
$pdo->exec("CREATE TABLE events (id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, pkg VARCHAR(100) NOT NULL, ev VARCHAR(40) NOT NULL, ts DATETIME(3) NOT NULL, did CHAR(36) NOT NULL,
  ver VARCHAR(40) NOT NULL DEFAULT '', vc INT NOT NULL DEFAULT 0, p1 VARCHAR(255) NOT NULL DEFAULT '', p2 TEXT NULL, model VARCHAR(80) NOT NULL DEFAULT '', os VARCHAR(40) NOT NULL DEFAULT '',
  val DOUBLE NOT NULL DEFAULT 0, KEY k_pkg_ts (pkg, ts))");

// ---------------------------------------------------------------- 1) schema migration (purani install: vitals_ver column nahi)
$pdo->exec("CREATE TABLE health_apps (pkg VARCHAR(100) NOT NULL PRIMARY KEY, console_id VARCHAR(20) NOT NULL DEFAULT '', name VARCHAR(160) NOT NULL DEFAULT '',
  icon_url VARCHAR(500) NOT NULL DEFAULT '', icon_at DATETIME NULL, in_dashboard TINYINT NOT NULL DEFAULT 0, discovered_at DATETIME NOT NULL,
  monitoring TINYINT NOT NULL DEFAULT 1, autofix TINYINT NOT NULL DEFAULT 1, email TINYINT NOT NULL DEFAULT 1,
  repo VARCHAR(160) NOT NULL DEFAULT '', repo_verified TINYINT NOT NULL DEFAULT 0, signing_verified TINYINT NOT NULL DEFAULT 0,
  verify_note VARCHAR(500) NOT NULL DEFAULT '', verified_at DATETIME NULL, status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
  crash_rate DOUBLE NULL, anr_rate DOUBLE NULL, vitals_day DATE NULL, last_scan_at DATETIME NULL, updated_at DATETIME NOT NULL)");
kv_set('h_schema_v', '1');   // jaise live server par: schema v1 pehle se
h_schema($pdo);
ok((bool)$pdo->query("SHOW COLUMNS FROM health_apps LIKE 'vitals_ver'")->fetch(), 'migration: vitals_ver column added to an existing health_apps');
ok(kv_get('h_schema_m') === '2', 'migration recorded (runs once)');
$pdo->exec('DROP TABLE health_apps');
$pdo->exec("DELETE FROM kv WHERE k IN ('h_schema_v','h_schema_m')");
// fresh install path (naya process, kyunki h_schema ek process me ek hi baar chalta hai): CREATE TABLE me hi column
$code = "require '$M/lib/health.php'; h_schema(db()); echo (int)(bool)db()->query(\"SHOW COLUMNS FROM health_apps LIKE 'vitals_ver'\")->fetch();";
$out = shell_exec(escapeshellarg(PHP_BINARY) . ' -r ' . escapeshellarg($code) . ' 2>&1');
ok(trim((string)$out) === '1', 'fresh install: health_apps created with vitals_ver');
foreach ([['com.mfc.calc', 'Hide Calculator'], ['com.mfc.tracker', 'Phone Tracker Live Location'], ['com.mfc.notes', 'Secret Notes']] as [$p, $nm])
    h_x($pdo, "INSERT INTO health_apps (pkg, console_id, name, discovered_at, updated_at) VALUES (?, 'c1', ?, UTC_TIMESTAMP(), UTC_TIMESTAMP())", [$p, $nm]);

// ---------------------------------------------------------------- 2) per-version vitals (canned Play API)
$calls = [];
$GLOBALS['FAKE_HTTP'] = function (string $method, string $url, ?string $body) use (&$calls) {
    $calls[] = [$method, $url, $body];
    if (str_contains($url, 'oauth2.googleapis.com/token')) return [200, '{"access_token":"tok","expires_in":3600}', ''];
    if (!preg_match('#/apps/([^/]+)/(crashRateMetricSet|anrRateMetricSet)(:query)?$#', $url, $m)) return [404, '{"error":{"message":"not found"}}', ''];
    [$pkg, $set, $q] = [rawurldecode($m[1]), $m[2], $m[3] ?? ''];
    $metric = $set === 'crashRateMetricSet' ? 'userPerceivedCrashRate' : 'userPerceivedAnrRate';
    $tz = ['id' => 'America/Los_Angeles'];
    if ($q === '') return [200, json_encode(['freshnessInfo' => ['freshnesses' => [['aggregationPeriod' => 'HOURLY', 'latestEndTime' => ['year' => 2026, 'month' => 10, 'day' => 22, 'timeZone' => $tz]],
        ['aggregationPeriod' => 'DAILY', 'latestEndTime' => ['year' => 2026, 'month' => 10, 'day' => 21, 'timeZone' => $tz]]]]]), ''];
    $b = json_decode((string)$body, true);
    $dv = fn($v) => ['decimalValue' => ['value' => (string)$v]];
    if (empty($b['dimensions'])) {
        $rows = [];
        for ($d = 14; $d <= 20; $d++) $rows[] = ['startTime' => ['year' => 2026, 'month' => 10, 'day' => $d, 'timeZone' => $tz], 'metrics' => [['metric' => $metric] + $dv($set === 'crashRateMetricSet' ? 0.004 : 0.001), ['metric' => 'distinctUsers'] + $dv(5000)]];
        return [200, json_encode(['rows' => $rows]), ''];
    }
    // version-wise: calc ka ANR per-version fail (main vitals par asar nahi hona chahiye)
    if ($pkg === 'com.mfc.calc' && $set === 'anrRateMetricSet') return [400, '{"error":{"message":"dimension not supported (simulated)"}}', ''];
    $GLOBALS['VER_SPEC'] = $b['timelineSpec'];
    $vers = [['58', 20, 0.05], ['59', 50, 0.02], ['60', 100, 0.01], ['61', 300, 0.002], ['62', 1200, 0.003], ['63', 3000, 0.0123]];
    $rows = [];
    foreach ($vers as $i => [$vc, $u, $r]) $rows[] = ['startTime' => $b['timelineSpec']['startTime'], 'dimensions' => [['dimension' => 'versionCode'] + ($i % 2 ? ['int64Value' => $vc] : ['stringValue' => $vc])],
        'metrics' => [['metric' => $metric] + $dv($set === 'crashRateMetricSet' ? $r : $r / 4), ['metric' => 'distinctUsers'] + $dv($u)]];
    $rows[] = ['startTime' => $b['timelineSpec']['startTime'], 'dimensions' => [['dimension' => 'versionCode']], 'metrics' => [['metric' => $metric] + $dv(0.5)]];   // bina value: ignore
    return [200, json_encode(['rows' => $rows]), ''];
};
write_cfg($M, 'unused', 1);
$rid = h_run_new($pdo, 'vitals', ['trigger' => 'manual', 'by' => 'test']);
h_run_exec($pdo, $rid);
$run = $pdo->query("SELECT * FROM health_runs WHERE id = $rid")->fetch();
ok($run['status'] !== 'FAILED' && (int)$run['errors'] === 0, 'vitals run not failed by a per-version error (status ' . $run['status'] . ', errors ' . $run['errors'] . ')');
$calc = $pdo->query("SELECT * FROM health_apps WHERE pkg = 'com.mfc.calc'")->fetch();
ok(abs((float)$calc['crash_rate'] - 0.004) < 1e-9 && abs((float)$calc['anr_rate'] - 0.001) < 1e-9 && $calc['vitals_day'] === '2026-10-20', 'overall crash/ANR rate + vitals_day still stored');
$vv = json_decode((string)$calc['vitals_ver'], true);
ok(is_array($vv) && $vv['day'] === '2026-10-20' && count($vv['rows']) === 5, 'vitals_ver: latest day + max 5 versions');
ok(array_column($vv['rows'], 'vc') === ['63', '62', '61', '60', '59'], 'vitals_ver: top by users, sorted by versionCode desc (string vc)');
ok($vv['rows'][0]['crash'] === 0.0123 && $vv['rows'][0]['users'] === 3000 && $vv['rows'][0]['anr'] === null, 'vitals_ver row shape {vc, crash, anr, users}; failed ANR per-version = null');
ok((int)h_q1($pdo, "SELECT COUNT(*) FROM health_events WHERE level = 'warn' AND msg LIKE '%com.mfc.calc anrRateMetricSet per-version skipped%'") === 1, 'per-version failure logged as warn');
$tr = json_decode((string)h_q1($pdo, "SELECT vitals_ver FROM health_apps WHERE pkg = 'com.mfc.tracker'"), true);
ok(is_array($tr) && $tr['rows'][0]['anr'] === round(0.0123 / 4, 6), 'tracker app: crash + ANR per version merged');
ok(($GLOBALS['VER_SPEC']['startTime']['day'] ?? 0) === 20 && ($GLOBALS['VER_SPEC']['endTime']['day'] ?? 0) === 21, 'per-version query = exactly the latest day (end exclusive)');
$GLOBALS['FAKE_HTTP'] = function () { return [0, '', 'network disabled in tests']; };

// ---------------------------------------------------------------- 3) alerts.php: new_version rule (subprocess: alerts.php apna request handle karke exit karta hai)
$ca = escapeshellarg("$T/ca.pem");
$SUBOUT = '';
function sub(string $T, string $M, string $php, string $ca): string
{
    $f = "$T/run_" . md5($php) . '.php';
    file_put_contents($f, "<?php\n$php\n");
    $o = (string)shell_exec(escapeshellarg(PHP_BINARY) . " -d openssl.cafile=$ca -d display_errors=stderr -d error_reporting=-1 " . escapeshellarg($f) . ' 2>&1');
    $GLOBALS['SUBOUT'] .= $o;
    return $o;
}
$alerts = fn() => sub($T, $M, "\$_GET['cron'] = '1'; require '$M/api/alerts.php';", $ca);
$today = (new DateTime('now', new DateTimeZone('Asia/Kolkata')))->format('Y-m-d');
$old = (new DateTime('now', new DateTimeZone('Asia/Kolkata')))->modify('-5 day')->format('Y-m-d');
$dv = $pdo->prepare('INSERT INTO day_ver (day, pkg, ver, did) VALUES (?,?,?,?)');
for ($i = 0; $i < 12; $i++) $dv->execute([$old, 'com.mfc.calc', '1.9', "old-$i"]);          // history: live version 1.9
for ($i = 0; $i < 7; $i++) $dv->execute([$today, 'com.mfc.calc', '1.10', "new-$i"]);        // aaj: 1.10 (7 devices) -> alert (1.10 > 1.9)
for ($i = 0; $i < 2; $i++) $dv->execute([$today, 'com.mfc.calc', '1.11', "test-$i"]);       // test install (2 devices) -> nahi
for ($i = 0; $i < 9; $i++) $dv->execute([$today, 'com.mfc.notes', '3.0', "n-$i"]);          // history nahi -> chup-chaap base
$pdo->prepare("INSERT INTO events (pkg, ev, ts, did, ver, vc) VALUES ('com.mfc.calc', 'session_start', UTC_TIMESTAMP(), 'new-1', '1.10', 64)")->execute();
$o = $alerts();
$live = h_q($pdo, "SELECT * FROM app_alerts WHERE rule = 'new_version'");
ok(count($live) === 1 && $live[0]['akey'] === 'live|com.mfc.calc|1.10' && (int)$live[0]['level'] === 1, 'new_version: one info alert for the new highest version (test installs ignored)');
ok(str_contains($live[0]['title'], 'v1.10 is live on Play') && str_contains($live[0]['title_hi'], 'v1.10 Play par live') && str_contains($live[0]['body'], '7 users'), 'new_version: title / title_hi / user count');
$lm = json_decode((string)$live[0]['metrics'], true);
ok(($lm['version_code'] ?? null) === 64 && ($lm['previous_version'] ?? '') === '1.9', 'new_version metrics: version_code from tracker events, previous version');
$o2 = $alerts();
ok((int)h_q1($pdo, "SELECT COUNT(*) FROM app_alerts WHERE rule = 'new_version'") === 1, 'second compute: no duplicate');
$base = json_decode((string)kv_get('al_live_ver'), true);
ok(($base['com.mfc.calc']['ver'] ?? '') === '1.10' && ($base['com.mfc.calc']['vc'] ?? 0) === 64 && ($base['com.mfc.notes']['ver'] ?? '') === '3.0', 'base versions saved (kv al_live_ver)');
$pdo->exec("UPDATE app_alerts SET resolved_at = UTC_TIMESTAMP() WHERE rule = 'new_version'");   // 24h auto-resolve jaisa
$alerts();
ok((int)h_q1($pdo, "SELECT COUNT(*) FROM app_alerts WHERE rule = 'new_version'") === 1, 'resolved info alert never re-fires');
for ($i = 2; $i < 6; $i++) $dv->execute([$today, 'com.mfc.calc', '1.11', "test-$i"]);       // ab 1.11 par 6 devices
$alerts();
ok((int)h_q1($pdo, "SELECT COUNT(*) FROM app_alerts WHERE akey = 'live|com.mfc.calc|1.11'") === 1, 'next version reaching 5 devices fires once');
$alerts();   // agla compute: base 1.11 ho jaata hai
ok((int)h_q1($pdo, "SELECT COUNT(*) FROM app_alerts WHERE rule = 'new_version'") === 2 && (json_decode((string)kv_get('al_live_ver'), true)['com.mfc.calc']['ver'] ?? '') === '1.11', 'base moves to 1.11, still 2 alerts total');
ok(!str_contains($o, 'al_rule_new_version ERROR'), 'rule ran without error');

// ---------------------------------------------------------------- 4) Play Console emails via fake IMAP (TLS)
$b64 = fn(string $s) => chunk_split(base64_encode($s), 76, "\r\n");
$H = fn(array $h) => implode("\r\n", array_map(fn($k, $v) => "$k: $v", array_keys($h), $h)) . "\r\n\r\n";
$msgs = [];
$add = function (int $uid, array $h, string $text, bool $quoted = false) use (&$msgs, $H) { $msgs[] = ['uid' => $uid, 'header' => $H($h), 'text' => $text, 'seen' => false, 'quoted' => $quoted]; };
$add(101, ['From' => 'Google Play Console <no-reply-googleplay-developer@google.com>', 'Subject' => '=?UTF-8?B?' . base64_encode('Action required: Hide Calculator was rejected – policy') . '?=',
    'Date' => 'Wed, 21 Oct 2026 10:00:00 +0000', 'Message-ID' => '<msgA-123@google.com>', 'Content-Type' => 'multipart/alternative; boundary="b1"',
    'Authentication-Results' => 'mx.google.com; dkim=pass header.i=@google.com'],
    "--b1\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\nHi Developers at MFC,\r\n\r\nAfter review, your app com.mfc.calc (Hide Calculator) has been rejected and won=E2=80=99t be pub=\r\nlished on Google Play.\r\n"
    . "Issue found: Deceptive behaviour policy. Visit https://play.google.com/console/u/0/developers/123456789/app/4976/policy-center/issues/very-long-tracking-path?utm_source=mail&utm_medium=x\r\n"
    . "Thanks,\r\nThe Google Play Team\r\n\r\n=C2=A9 2026 Google LLC 1600 Amphitheatre Parkway, Mountain View, CA 94043\r\nYou have received this mandatory service announcement to update you about important changes.\r\n"
    . "--b1\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n" . $b64('<html><body><p>Hi Developers</p><p>rejected</p></body></html>') . "--b1--\r\n");
$add(102, ['From' => 'Sagar <sagar.test@gmail.com>', 'Subject' => 'Fwd: Update approved: Phone Tracker Live Location', 'Date' => 'Wed, 21 Oct 2026 11:00:00 +0530', 'Message-ID' => '<fwdB@gmail.com>',
    'Content-Type' => 'text/plain; charset="UTF-8"'],
    "FYI\r\n\r\n---------- Forwarded message ---------\r\nFrom: Google Play Console <noreply-play-console@google.com>\r\nDate: Tue, 20 Oct 2026 at 09:15\r\nSubject: Update approved: Phone Tracker Live Location\r\nTo: <sagar.test@gmail.com>\r\n\r\n"
    . "Your update to Phone Tracker Live Location (com.mfc.tracker) has been approved and is now available on Google Play.\r\n\r\nThe Google Play Team\r\n");
$add(103, ['From' => 'Deals <news@example.org>', 'Subject' => 'Big sale on Google Play gift cards', 'Date' => 'Wed, 21 Oct 2026 12:00:00 +0000', 'Message-ID' => '<c@example.org>'], "Buy now\r\n");
$add(104, ['From' => 'Google <no-reply@accounts.google.com>', 'Subject' => 'Security alert', 'Date' => 'Wed, 21 Oct 2026 12:00:00 +0000', 'Message-ID' => '<d@google.com>'], "New sign-in on Windows\r\n");
$add(105, ['From' => 'Google Play Console <no-reply-googleplay-developer@google.com>', 'Subject' => 'Action required: Hide Calculator was rejected', 'Date' => 'Wed, 21 Oct 2026 10:00:00 +0000',
    'Message-ID' => '<msgA-123@google.com>'], "duplicate of 101\r\n");
$add(106, ['From' => 'Google Play <googleplay-noreply@google.com>', 'Subject' => 'Reminder: complete the Data safety declaration by Nov 15', 'Date' => 'Wed, 21 Oct 2026 13:00:00 +0000',
    'Message-ID' => '<f@google.com>', 'Content-Type' => 'text/html; charset=UTF-8', 'Content-Transfer-Encoding' => 'base64'],
    $b64('<html><head><style>p{color:red}</style></head><body><p>Hello,</p><p>Your app <b>Hide Calculator</b> needs a completed Data safety form in Play Console.</p><p>&copy; 2026 Google LLC</p></body></html>'));
$add(107, ['From' => 'Gmail Team <forwarding-noreply@google.com>', 'Subject' => '(#123456789) Gmail Forwarding Confirmation - Receive Mail from sagar.test@gmail.com', 'Date' => 'Wed, 21 Oct 2026 14:00:00 +0000',
    'Message-ID' => '<g@google.com>'], 'sagar.test@gmail.com has requested to automatically forward mail. Confirmation code: 123456789. To allow, open the link "below": https://mail-settings.google.com/mail/vf-%5Babc%5D-xyz', true);   // server ise quoted string bhejta hai
$add(108, ['From' => 'Google Play <no-reply@google.com>', 'Subject' => 'Your app was removed from Google Play', 'Date' => 'Wed, 21 Oct 2026 15:00:00 +0000', 'Message-ID' => '<spoof@x.test>',
    'Authentication-Results' => 'mail.test.local; dkim=fail reason="signature verification failed" header.d=google.com'], "Click here to restore\r\n");
$add(109, ['From' => 'Mallory <attacker@evil.test>', 'Subject' => 'Fwd: app removed', 'Date' => 'Wed, 21 Oct 2026 15:00:00 +0000', 'Message-ID' => '<i@evil.test>'],
    "---------- Forwarded message ---------\r\nFrom: Google Play <no-reply@google.com>\r\nSubject: Your app was removed from Google Play\r\n\r\nremoved\r\n");
$add(110, ['From' => 'Sagar 2 <sagar.second@gmail.com>', 'Subject' => 'Fwd: Update approved: Phone Tracker Live Location', 'Date' => 'Wed, 21 Oct 2026 11:05:00 +0530', 'Message-ID' => '<fwdJ@gmail.com>'],
    "---------- Forwarded message ---------\r\nFrom: Google Play Console <noreply-play-console@google.com>\r\nDate: Tue, 20 Oct 2026 at 09:15\r\nSubject: Update approved: Phone Tracker Live Location\r\nTo: <sagar.second@gmail.com>\r\n\r\n"
    . "Your update to Phone Tracker Live Location (com.mfc.tracker) has been approved and is now available on Google Play.\r\n\r\nThe Google Play Team\r\n");
$add(111, ['From' => 'Sagar <sagar.test@gmail.com>', 'Subject' => 'Fwd: important', 'Date' => 'Wed, 21 Oct 2026 16:00:00 +0530', 'Message-ID' => '<k@gmail.com>', 'Content-Type' => 'multipart/mixed; boundary=outer'],
    "--outer\r\nContent-Type: text/plain\r\n\r\nsee attached\r\n--outer\r\nContent-Type: message/rfc822\r\nContent-Disposition: attachment; filename=\"orig.eml\"\r\n\r\n"
    . "From: Google Play Console <no-reply-googleplay-developer@google.com>\r\nSubject: =?utf-8?Q?Your_app_Secret_Notes_has_been_suspended?=\r\nDate: Mon, 19 Oct 2026 08:00:00 +0000\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n"
    . "We have suspended Secret Notes from Google Play due to a policy violation.\r\n--outer--\r\n");
for ($i = 0; $i < 25; $i++) $add(200 + $i, ['From' => "junk$i@example.org", 'Subject' => "Newsletter $i", 'Message-ID' => "<j$i@example.org>"], "hello\r\n");
file_put_contents("$T/imap_state.json", json_encode(['msgs' => $msgs, 'logins' => []]));

// fake IMAP server (alag process): LOGIN (quoted / literal), SELECT, UID SEARCH UNSEEN, UID FETCH (literal / quoted), UID STORE, LOGOUT
$server = <<<'PHPS'
<?php
[$_, $state, $pem, $portFile, $expectFile] = $argv;
$srv = stream_socket_server('ssl://127.0.0.1:0', $en, $es, STREAM_SERVER_BIND | STREAM_SERVER_LISTEN, stream_context_create(['ssl' => ['local_cert' => $pem, 'verify_peer' => false]]));
if (!$srv) { fwrite(STDERR, "server: $es\n"); exit(1); }
file_put_contents($portFile, (string)parse_url('tcp://' . stream_socket_get_name($srv, false), PHP_URL_PORT));
$load = fn() => json_decode((string)file_get_contents($state), true);
$save = fn($s) => file_put_contents($state, json_encode($s));
$t0 = time();
while (time() - $t0 < 120 && !is_file($state . '.stop')) {
    $c = @stream_socket_accept($srv, 2);
    if (!$c) continue;
    stream_set_timeout($c, 10);
    $w = function (string $s) use ($c) { fwrite($c, $s); };
    $w("* OK [CAPABILITY IMAP4rev1] fake imap ready\r\n");
    while (($line = fgets($c)) !== false) {
        $full = ''; $lits = [];
        while (preg_match('/\{(\d+)\}\r\n$/', $line, $m)) {
            $full .= substr($line, 0, -strlen($m[0])) . "\x00L" . count($lits) . "\x00";
            $w("+ go ahead\r\n");
            $d = ''; while (strlen($d) < (int)$m[1]) $d .= fread($c, (int)$m[1] - strlen($d));
            $lits[] = $d;
            $line = (string)fgets($c);
        }
        $full .= rtrim($line, "\r\n");
        [$tag, $rest] = explode(' ', $full, 2) + [1 => ''];
        $s = $load();
        if (preg_match('/^LOGIN (.*)$/i', $rest, $m)) {
            preg_match_all('/"((?:[^"\\\\]|\\\\.)*)"|\x00L(\d+)\x00|(\S+)/', $m[1], $mm, PREG_SET_ORDER);
            $args = array_map(fn($x) => isset($x[2]) && $x[2] !== '' ? $lits[(int)$x[2]] : (($x[1] ?? '') !== '' || ($x[3] ?? '') === '' ? preg_replace('/\\\\(.)/', '$1', $x[1]) : $x[3]), $mm);
            $exp = json_decode((string)file_get_contents($expectFile), true);
            $s['logins'][] = ['user' => $args[0] ?? '', 'literal' => count($lits) > 0, 'ok' => ($args[0] ?? '') === $exp['user'] && ($args[1] ?? '') === $exp['pass']];
            $save($s);
            $w(end($s['logins'])['ok'] ? "$tag OK LOGIN completed\r\n" : "$tag NO [AUTHENTICATIONFAILED] Authentication failed.\r\n");
        } elseif (preg_match('/^SELECT INBOX$/i', $rest)) {
            $w('* ' . count($s['msgs']) . " EXISTS\r\n* FLAGS (\\Seen)\r\n$tag OK [READ-WRITE] SELECT completed\r\n");
        } elseif (preg_match('/^UID SEARCH UNSEEN$/i', $rest)) {
            $ids = array_column(array_filter($s['msgs'], fn($x) => !$x['seen']), 'uid');
            rsort($ids);   // ulta kram: client khud sort kare
            $w('* SEARCH' . ($ids ? ' ' . implode(' ', $ids) : '') . "\r\n$tag OK SEARCH completed\r\n");
        } elseif (preg_match('/^UID FETCH (\d+) \(BODY\.PEEK\[HEADER\.FIELDS \(([A-Z -]+)\)\] BODY\.PEEK\[TEXT\]<0\.(\d+)>\)$/i', $rest, $m)) {
            foreach ($s['msgs'] as $i => $x) {
                if ($x['uid'] !== (int)$m[1]) continue;
                $txt = substr($x['text'], 0, (int)$m[3]);
                $w('* ' . ($i + 1) . " FETCH (FLAGS ())\r\n");   // bina UID wala unsolicited response
                $body = $x['quoted'] && !preg_match('/[\r\n]/', $txt) ? '"' . addcslashes($txt, "\"\\") . '"' : '{' . strlen($txt) . "}\r\n" . $txt;
                $w('* ' . ($i + 1) . " FETCH (UID {$x['uid']} BODY[HEADER.FIELDS ({$m[2]})] {" . strlen($x['header']) . "}\r\n{$x['header']} BODY[TEXT]<0> $body)\r\n");
            }
            $w("$tag OK FETCH completed\r\n");
        } elseif (preg_match('/^UID STORE (\d+) \+FLAGS(\.SILENT)? \(\\\\Seen\)$/i', $rest, $m)) {
            foreach ($s['msgs'] as $i => &$x) if ($x['uid'] === (int)$m[1]) { $x['seen'] = true; $w('* ' . ($i + 1) . " FETCH (UID {$x['uid']} FLAGS (\\Seen))\r\n"); }
            unset($x);
            $save($s);
            $w("$tag OK STORE completed\r\n");
        } elseif (preg_match('/^LOGOUT$/i', $rest)) {
            $w("* BYE logging out\r\n$tag OK LOGOUT completed\r\n");
            break;
        } else $w("$tag BAD unknown command\r\n");
    }
    fclose($c);
}
PHPS;
file_put_contents("$T/imapd.php", $server);
$proc = proc_open([PHP_BINARY, "$T/imapd.php", "$T/imap_state.json", "$T/server.pem", "$T/port", "$T/expect.json"], [1 => ['file', "$T/imapd.out", 'w'], 2 => ['file', "$T/imapd.err", 'w']], $pipes);
for ($i = 0; $i < 50 && !is_file("$T/port"); $i++) usleep(100000);
$port = (int)@file_get_contents("$T/port");
ok($port > 0, "fake IMAP (TLS) server listening on 127.0.0.1:$port");

// run 1: cron (ASCII password with quote + backslash -> quoted LOGIN)
$pass1 = 'p"q\\r-1';
file_put_contents("$T/expect.json", json_encode(['user' => 'playmail@test.local', 'pass' => $pass1]));
write_cfg($M, $pass1, $port);
$o = sub($T, $M, "\$argv = ['health.php', 'cron']; require '$M/api/health.php';", $ca);
ok(str_contains($o, 'play mail: 30 mails, 5 alerts'), 'cron: 30-mail cap, 5 alerts (' . trim((string)preg_replace('/\s+/', ' ', (string)strstr($o, 'play mail'))) . ')');
$st = json_decode((string)file_get_contents("$T/imap_state.json"), true);
ok(($st['logins'][0]['ok'] ?? false) && !$st['logins'][0]['literal'], 'LOGIN with quoted string (escaped " and \\)');
ok(count(array_filter($st['msgs'], fn($x) => $x['seen'])) === 30, 'processed mails marked \\Seen (30)');
$pm = fn(string $akeyLike) => h_q($pdo, "SELECT * FROM app_alerts WHERE rule = 'play_mail' AND akey = ?", [$akeyLike])[0] ?? null;
$A = $pm('mail|' . sha1('msga-123@google.com'));
ok($A && (int)$A['level'] === 3 && $A['pkg'] === 'com.mfc.calc' && $A['title'] === 'Action required: Hide Calculator was rejected – policy', 'urgent: RFC2047 subject, package match, level 3');
ok($A && str_starts_with($A['title_hi'], 'Play: reject · Hide Calculator'), 'urgent: Hinglish prefix + app name (' . ($A['title_hi'] ?? '') . ')');
ok($A && str_contains($A['body'], 'won’t be published on Google Play') && !str_contains($A['body'], 'Amphitheatre') && !str_contains($A['body'], 'mandatory service'), 'body: quoted-printable decoded, footer removed');
ok($A && str_contains($A['body'], 'https://play.google.com/…') && !str_contains($A['body'], 'utm_source'), 'body: long tracking link shortened');
$Am = json_decode((string)($A['metrics'] ?? ''), true);
ok(($Am['level_word'] ?? '') === 'urgent' && ($Am['from'] ?? '') === 'no-reply-googleplay-developer@google.com' && ($Am['date'] ?? '') !== '' && ($Am['dkim'] ?? '') === 'pass', 'metrics {from, date, level_word, dkim}');
$all = h_q($pdo, "SELECT * FROM app_alerts WHERE rule = 'play_mail' ORDER BY id");
$by = []; foreach ($all as $r) $by[$r['title']] = $r;
$B = $by['Update approved: Phone Tracker Live Location'] ?? null;
ok($B && (int)$B['level'] === 1 && $B['pkg'] === 'com.mfc.tracker' && str_starts_with($B['title_hi'], 'Play: approve · Phone Tracker Live Location'), 'forwarded (Gmail block): original subject, approve (app name "Live" not counted as live)');
$Bm = json_decode((string)($B['metrics'] ?? ''), true);
ok(($Bm['via'] ?? '') === 'forward' && ($Bm['forwarded_by'] ?? '') === 'sagar.test@gmail.com' && ($Bm['from'] ?? '') === 'noreply-play-console@google.com', 'forwarded metrics: via, forwarded_by, original from');
$F = $by['Reminder: complete the Data safety declaration by Nov 15'] ?? null;
ok($F && (int)$F['level'] === 2 && $F['pkg'] === 'com.mfc.calc' && str_contains($F['body'], 'Data safety form') && !str_contains($F['body'], 'color:red') && !str_contains($F['body'], '©'), 'HTML-only base64: watch, app by name, tags/style/footer stripped');
$G = $by['Gmail forwarding confirmation'] ?? null;
ok($G && (int)$G['level'] === 1 && str_contains($G['body'], 'https://mail-settings.google.com/mail/vf-%5Babc%5D-xyz') && str_contains($G['body'], '123456789'), 'Gmail forwarding confirmation (quoted IMAP string): link + code in app');
$K = $by['Your app Secret Notes has been suspended'] ?? null;
ok($K && (int)$K['level'] === 3 && $K['pkg'] === 'com.mfc.notes' && (json_decode((string)$K['metrics'], true)['via'] ?? '') === 'attached', 'message/rfc822 attachment forward: Q-encoded subject, suspended = urgent');
ok(count($all) === 5, 'non-Google, non-Play, DKIM-fail spoof, unknown forwarder, duplicate Message-ID and same-content copy all skipped (' . count($all) . ' alerts)');
$s1 = json_decode((string)kv_get('pm_status'), true);
ok(($s1['last_count'] ?? -1) === 5 && ($s1['last_mails'] ?? -1) === 30 && ($s1['unseen_left'] ?? -1) === 6 && ($s1['last_error'] ?? 'x') === '' && !empty($s1['last_check']), 'status kv: last_check, last_count, unseen_left, no error');

// run 2: POST playmail_test (non-ASCII password -> LOGIN literals)
$pass2 = 'pässwörd {x}';
file_put_contents("$T/expect.json", json_encode(['user' => 'playmail@test.local', 'pass' => $pass2]));
write_cfg($M, $pass2, $port);
$o = sub($T, $M, "\$_SERVER['REQUEST_METHOD'] = 'POST'; \$_GET['action'] = 'playmail_test'; require '$M/api/health.php';", $ca);
$j = json_decode(trim((string)substr($o, (int)strpos($o, '{'))), true);
ok(($j['ok'] ?? false) === true && ($j['result']['mails'] ?? -1) === 6 && ($j['result']['alerts'] ?? -1) === 0 && ($j['status']['unseen_left'] ?? -1) === 0, 'POST playmail_test: remaining 6 mails, 0 alerts');
$st = json_decode((string)file_get_contents("$T/imap_state.json"), true);
ok((end($st['logins'])['ok'] ?? false) && end($st['logins'])['literal'], 'LOGIN with literals {n} (non-ASCII password)');
// run 3: wrong password -> error recorded, nothing marked
write_cfg($M, 'wrong', $port);
$o = sub($T, $M, "\$_SERVER['REQUEST_METHOD'] = 'POST'; \$_GET['action'] = 'playmail_test'; require '$M/api/health.php';", $ca);
$j = json_decode(trim((string)substr($o, (int)strpos($o, '{'))), true);
ok(($j['ok'] ?? true) === false && str_contains((string)($j['error'] ?? ''), 'LOGIN failed') && !str_contains((string)($j['error'] ?? ''), 'wrong'), 'login failure: ok=false, error without the password');
$o = sub($T, $M, "\$_GET['action'] = 'playmail_status'; require '$M/api/health.php';", $ca);
$j = json_decode(trim((string)substr($o, (int)strpos($o, '{'))), true);
ok(($j['ok'] ?? false) && ($j['configured'] ?? false) === true && str_contains((string)($j['last_error'] ?? ''), 'LOGIN failed') && array_key_exists('last_check', $j) && array_key_exists('last_count', $j)
    && str_starts_with((string)($j['mailbox'] ?? ''), 'p•'), 'GET playmail_status: {configured, last_check, last_error, last_count, mailbox masked}');
// TLS verify: cafile ke bina self-signed cert reject
write_cfg($M, $pass2, $port);
$o = (string)shell_exec(escapeshellarg(PHP_BINARY) . ' ' . escapeshellarg((function () use ($T, $M) { $f = "$T/run_noca.php"; file_put_contents($f, "<?php \$_SERVER['REQUEST_METHOD'] = 'POST'; \$_GET['action'] = 'playmail_test'; require '$M/api/health.php';"); return $f; })()) . ' 2>&1');
$terr = (string)(json_decode((string)substr($o, (int)strpos($o, '{')), true)['error'] ?? '');
ok(str_contains($o, '"ok":false') && str_contains($terr, 'connect') && stripos($terr, 'certificate') !== false, 'TLS certificate is verified (untrusted cert refused, reason shown: ' . h_cut($terr, 110) . ')');
// run 4: nakli "dkim=pass" neeche wale header me, "will be removed" newsletter (watch), aur ek kharab mail (DB error) jo 3 baar ke baad chhod di jaati hai
$msgs = [];
$add(301, ['From' => 'Google Play <no-reply@google.com>', 'Subject' => 'Your app was removed from Google Play', 'Date' => 'Wed, 21 Oct 2026 17:00:00 +0000', 'Message-ID' => '<spoof2@x.test>',
    'Authentication-Results' => "mail.test.local; dkim=fail header.d=google.com\r\nAuthentication-Results: evil.test; dkim=pass header.d=google.com"], "Click here to restore\r\n");
$add(302, ['From' => 'Google Play <googleplay-noreply@google.com>', 'Subject' => 'Upcoming changes for developers', 'Date' => 'Wed, 21 Oct 2026 17:00:00 +0000', 'Message-ID' => '<news@google.com>'],
    "Hello,\r\nApps that violate the updated Google Play policy will be removed, and accounts may be suspended. Action required by Dec 1.\r\n");
$add(303, ['From' => 'Google Play Console <no-reply-googleplay-developer@google.com>', 'Subject' => 'POISON update approved', 'Date' => 'Wed, 21 Oct 2026 17:00:00 +0000', 'Message-ID' => '<poison@google.com>'],
    "Your update is live on Google Play.\r\n");
$st = json_decode((string)file_get_contents("$T/imap_state.json"), true);
$st['msgs'] = array_merge($st['msgs'], $msgs);
file_put_contents("$T/imap_state.json", json_encode($st));
$pdo->exec("CREATE TRIGGER t_poison BEFORE INSERT ON app_alerts FOR EACH ROW BEGIN IF NEW.title LIKE 'POISON%' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'simulated insert error'; END IF; END");
$runs = [];
for ($i = 0; $i < 3; $i++) {
    $o = sub($T, $M, "\$_SERVER['REQUEST_METHOD'] = 'POST'; \$_GET['action'] = 'playmail_test'; require '$M/api/health.php';", $ca);
    $runs[] = json_decode(trim((string)substr($o, (int)strpos($o, '{'))), true);
}
$pdo->exec('DROP TRIGGER t_poison');
ok(($runs[0]['ok'] ?? true) === false && str_contains((string)($runs[0]['error'] ?? ''), 'simulated insert error') && ($runs[1]['ok'] ?? true) === false && ($runs[2]['ok'] ?? false) === true,
    'bad mail: run fails twice (mail stays unseen), third run skips it and succeeds');
$st = json_decode((string)file_get_contents("$T/imap_state.json"), true);
ok(count(array_filter($st['msgs'], fn($x) => !$x['seen'])) === 0 && (int)h_q1($pdo, "SELECT COUNT(*) FROM health_events WHERE msg LIKE 'Play mail UID 303 skipped after 3 failures%'") === 1, 'bad mail marked Seen after 3 tries + error logged');
ok((int)h_q1($pdo, "SELECT COUNT(*) FROM app_alerts WHERE akey = ?", ['mail|' . sha1('spoof2@x.test')]) === 0 && (int)h_q1($pdo, "SELECT COUNT(*) FROM health_events WHERE level = 'warn' AND msg LIKE 'Play mail skipped (DKIM fail%'") >= 1,
    'forged lower Authentication-Results (dkim=pass) ignored: spoof skipped + warn logged');
$N = $pm('mail|' . sha1('news@google.com'));
ok($N && (int)$N['level'] === 2 && (json_decode((string)$N['metrics'], true)['match'] ?? '') === 'removal warning', '"will be removed / may be suspended" newsletter = watch, not urgent');
touch("$T/imap_state.json.stop");
proc_terminate($proc); proc_close($proc);

// config band: feature chup
file_put_contents("$M/health_config.php", '<?php return ' . var_export(['consoles' => [['id' => 'c1', 'name' => 'Test console', 'sa' => 'play_c1.json']], 'play_mail' => ['host' => '', 'port' => 993, 'user' => '', 'pass' => '']], true) . ';');
$o = sub($T, $M, "\$_GET['action'] = 'playmail_status'; require '$M/api/health.php';", $ca);
$j = json_decode(trim((string)substr($o, (int)strpos($o, '{'))), true);
ok(($j['configured'] ?? true) === false, 'empty play_mail config = feature off');

// ---------------------------------------------------------------- 5) health API output: vitals_day / vitals_ver / tracker_ver
$o = sub($T, $M, "\$_GET['view'] = 'consoles'; require '$M/api/health.php';", $ca);
$j = json_decode(trim((string)substr($o, (int)strpos($o, '{'))), true);
$capp = null; foreach ((array)($j['apps'] ?? []) as $a) if ($a['pkg'] === 'com.mfc.calc') $capp = $a;
ok($capp && $capp['vitals_day'] === '2026-10-20' && ($capp['vitals_ver']['day'] ?? '') === '2026-10-20' && ($capp['vitals_ver']['rows'][0]['vc'] ?? '') === '63', 'view=consoles: vitals_day + decoded vitals_ver');
ok($capp && $capp['tracker_ver'] === '1.11' && $capp['tracker_vc'] === null, 'view=consoles: tracker_ver (latest live version), tracker_vc null when unknown');
$o = sub($T, $M, "\$_GET['view'] = 'app'; \$_GET['pkg'] = 'com.mfc.tracker'; require '$M/api/health.php';", $ca);
$j = json_decode(trim((string)substr($o, (int)strpos($o, '{'))), true);
ok(count($j['app']['vitals_ver']['rows'] ?? []) === 5 && array_key_exists('tracker_ver', $j['app'] ?? []), 'view=app: vitals_ver + tracker fields');
$o = sub($T, $M, "\$_GET['view'] = 'settings'; require '$M/api/health.php';", $ca);
$j = json_decode(trim((string)substr($o, (int)strpos($o, '{'))), true);
ok(isset($j['apps'][0]) && array_key_exists('vitals_ver', $j['apps'][0]) && array_is_list($j['schedule'] ?? null), 'view=settings: apps carry vitals_ver, schedule stays a list');

ok(!preg_match('/(PHP )?(Warning|Fatal error|Deprecated|Notice):/', $SUBOUT), 'no PHP warnings / errors in API runs' . (preg_match('/(PHP )?(Warning|Fatal error|Deprecated|Notice):[^\n]*/', $SUBOUT, $mm) ? ': ' . $mm[0] : ''));
$rm = function (string $d) use (&$rm) { foreach ((array)@scandir($d) as $f) if ($f !== '.' && $f !== '..') { is_dir("$d/$f") ? $rm("$d/$f") : @unlink("$d/$f"); } @rmdir($d); };
$rm($T);

echo "\n$n checks, $fail failed\n";
exit($fail ? 1 : 0);
