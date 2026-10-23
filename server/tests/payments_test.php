<?php
/**
 * Payments API (api/payments.php) — test against a real MySQL (DEVELOPMENT ENVIRONMENT, SIMULATED DATA ONLY, koi asli payment nahi).
 * Local: mysql -h127.0.0.1 -uroot -p... -e 'CREATE DATABASE IF NOT EXISTS mfc_pay_test'
 *        TEST_DB_HOST=127.0.0.1 TEST_DB_NAME=mfc_pay_test TEST_DB_USER=root TEST_DB_PASS=... php server/tests/payments_test.php
 *
 * Verifies (api/payments.php run as a CLI subprocess with the stub common.php, apps() = 3 named apps):
 *   totals / per-app (sorted by amount) for today / yesterday / 7d / 30d, a duplicate order counted once, IST day boundaries
 *   (23:59:59 vs 00:00:00 local), app filter + items (newest first, duplicate merged, started only when abandoned, reason only when
 *   the event carries one, failed amount from the plan's price), events of an app not in apps(), non-purchase events ignored,
 *   older-than-raw days from agg_pay (agg / mixed, with and without a count column, table missing), bad input, the (pkg, ts) index,
 *   one read over events per request (over 3000 rows for one app: two), negative Play Billing codes as reasons,
 *   auth: login check first (401 stops before any data), session closed before the first DB call.
 *   "Today" is fixed once at the start (PAY_TEST_TODAY), so a run across IST midnight still checks the seeded day.
 */
declare(strict_types=1);
if (!getenv('TEST_DB_HOST')) { echo "TEST_DB_HOST not set: skipping payments test\n"; exit(0); }

$src = dirname(__DIR__);
$T = sys_get_temp_dir() . '/mfc_pay_test_' . getmypid();
$M = "$T/mfc_dash";
foreach (['lib', 'api'] as $d) @mkdir("$M/$d", 0777, true);
@mkdir("$T/sess", 0777, true);

$fail = 0; $n = 0;
function ok(bool $c, string $m): void { global $fail, $n; $n++; echo ($c ? 'ok   ' : 'FAIL ') . $m . "\n"; if (!$c) $fail++; }
function near($a, float $b, float $eps = 0.001): bool { return is_numeric($a) && abs((float)$a - $b) < $eps; }

// ---- stub common.php: 3 named apps, login hook (deny / session) for the auth checks, DB-call probe
$stub = (string)file_get_contents("$src/tests/stub_common.php");
$appsOld = 'function apps(): array { return []; }';
$loginOld = 'function require_login_json(): void {}';
$dbOld = "    static \$pdo = null;\n";
$todayOld = "function local_today(): string { return (new DateTime('now', tz()))->format('Y-m-d'); }";
$pdoOld = '$pdo = new PDO(';
foreach ([$appsOld, $loginOld, $dbOld, $todayOld, $pdoOld] as $s) if (!str_contains($stub, $s)) { echo "FAIL stub_common.php changed: missing `" . trim($s) . "`\n"; exit(1); }
// "aaj" test ke shuru me pakka: run IST aadhi raat paar kare to bhi seed wala din aur api() wale subprocess ka din ek
$stub = str_replace($todayOld, "function local_today(): string { \$p = (string)getenv('PAY_TEST_TODAY'); return \$p !== '' ? \$p : (new DateTime('now', tz()))->format('Y-m-d'); }", $stub);
// events par kitni query gayi (PAY_TEST_QLOG file me har SQL ek line)
$stub = str_replace($pdoOld, '$pdo = new PayTestPdo(', $stub) . "\n" . <<<'PHP'
class PayTestPdo extends PDO
{
    public function prepare(string $query, array $options = []): PDOStatement|false { pay_test_log($query); return parent::prepare($query, $options); }
    public function query(string $query, ?int $fetchMode = null, mixed ...$args): PDOStatement|false
    {
        pay_test_log($query);
        return $fetchMode === null ? parent::query($query) : parent::query($query, $fetchMode, ...$args);
    }
}
function pay_test_log(string $q): void { $f = (string)getenv('PAY_TEST_QLOG'); if ($f !== '') file_put_contents($f, preg_replace('/\s+/', ' ', $q) . "\n", FILE_APPEND); }
PHP;
$stub = str_replace($appsOld, "function apps(): array { return ['com.sim.cam' => ['name' => 'Sim Camera'], 'com.sim.vpn' => ['name' => 'Sim VPN'], 'com.sim.fun' => ['name' => 'Sim Fun']]; }", $stub);
$stub = str_replace($loginOld, <<<'PHP'
function require_login_json(): void
{
    $mode = (string)getenv('PAY_TEST_AUTH');
    if ($mode === 'deny') json_out(['ok' => false, 'error' => 'login required'], 401);
    if ($mode === 'session') {   // asli common.php jaisa: login check session kholta hai (file session, lock ke saath)
        session_save_path((string)getenv('PAY_TEST_SESS'));
        session_id('paytest');
        session_start();
        $_SESSION['user'] = 'test';
        register_shutdown_function(function () { file_put_contents((string)getenv('PAY_TEST_SESS') . '/at_end.txt', (string)session_status()); });
    }
}
PHP, $stub);
// pehli DB call par session ka haal likho (session_write_close usse pehle hona chahiye)
$stub = str_replace($dbOld, $dbOld . "    if (\$pdo === null && getenv('PAY_TEST_SESS')) @file_put_contents(getenv('PAY_TEST_SESS') . '/at_db.txt', (string)session_status());\n", $stub);
$stub = str_replace("'mysql:host=' . getenv('TEST_DB_HOST') . ';dbname='", "'mysql:host=' . getenv('TEST_DB_HOST') . (getenv('TEST_DB_PORT') ? ';port=' . getenv('TEST_DB_PORT') : '') . ';dbname='", $stub);
file_put_contents("$M/lib/common.php", $stub);
copy("$src/api/payments.php", "$M/api/payments.php");
file_put_contents("$T/run.php", '<?php $_SERVER["REQUEST_METHOD"] = "GET"; $_GET = (array)json_decode((string)($argv[1] ?? "{}"), true); require ' . var_export("$M/api/payments.php", true) . ';');

/** api/payments.php ek alag process me (json_out exit karta hai). auth = '' | deny | session. */
function api(array $get, string $auth = ''): array
{
    global $T;
    $env = 'PAY_TEST_TODAY=' . escapeshellarg((string)getenv('PAY_TEST_TODAY')) . ' PAY_TEST_QLOG=' . escapeshellarg("$T/qlog.txt") . ' ';
    if ($auth !== '') $env .= 'PAY_TEST_AUTH=' . escapeshellarg($auth) . ' PAY_TEST_SESS=' . escapeshellarg("$T/sess") . ' ';
    @unlink("$T/qlog.txt");
    $out = (string)shell_exec($env . escapeshellarg(PHP_BINARY) . ' ' . escapeshellarg("$T/run.php") . ' ' . escapeshellarg(json_encode($get)) . ' 2>&1');
    $j = json_decode($out, true);
    if (!is_array($j)) { echo "     (raw output: " . substr($out, 0, 600) . ")\n"; return []; }
    return $j;
}

/** Pichhli api() call ne events par kitni ts-range padhai ki (DISTINCT pkg wali loose scan nahi gini). */
function event_reads(): int
{
    global $T;
    $q = @file("$T/qlog.txt", FILE_IGNORE_NEW_LINES) ?: [];
    return count(array_filter($q, fn($l) => stripos($l, 'FROM events') !== false && stripos($l, 'ts >=') !== false));
}

putenv('PAY_TEST_TODAY=' . (new DateTime('now', new DateTimeZone('Asia/Kolkata')))->format('Y-m-d'));
require "$M/lib/common.php";
$pdo = db();
foreach (['events', 'agg_pay'] as $t) $pdo->exec("DROP TABLE IF EXISTS $t");
$pdo->exec("CREATE TABLE events (id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, pkg VARCHAR(100) NOT NULL, ev VARCHAR(40) NOT NULL, ts DATETIME(3) NOT NULL, did CHAR(36) NOT NULL,
  ver VARCHAR(40) NOT NULL DEFAULT '', vc INT NOT NULL DEFAULT 0, p1 VARCHAR(255) NOT NULL DEFAULT '', p2 TEXT NULL, model VARCHAR(80) NOT NULL DEFAULT '', os VARCHAR(40) NOT NULL DEFAULT '',
  val DOUBLE NOT NULL DEFAULT 0, KEY k_pkg_ts (pkg, ts))");
$pdo->exec("CREATE TABLE agg_pay (day DATE NOT NULL, pkg VARCHAR(100) NOT NULL, ev VARCHAR(40) NOT NULL, n INT NOT NULL DEFAULT 0, v DOUBLE NOT NULL DEFAULT 0, PRIMARY KEY (day, pkg, ev))");

// ---- SIMULATED events. Din = app ka local din (IST); DB me UTC.
$today = local_today();
$D = fn(int $n) => (new DateTime($today))->modify("-$n day")->format('Y-m-d');
$utc = fn(string $day, string $time) => (new DateTime("$day $time", tz()))->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s.v');
$ev = $pdo->prepare('INSERT INTO events (pkg, ev, ts, did, p1, p2, val) VALUES (?,?,?,?,?,?,?)');
$put = function (string $pkg, string $e, string $day, string $time, string $did, string $plan, ?string $p2, float $val) use ($ev, $utc): void {
    $ev->execute([$pkg, "purchase_$e", $utc($day, $time), str_pad($did, 36, '0'), $plan, $p2, $val]);
};
$T0 = $D(0); $Y = $D(1);
$pdo->beginTransaction();
// cam, aaj: yearly paid (order 2 baar aaya), monthly cancelled, weekly chhoda, aadhi raat 00:00:00 ka paid, bank declined
$put('com.sim.cam', 'start', $T0, '10:00:00', 'c1', 'yearly', null, 299);
$put('com.sim.cam', 'done', $T0, '10:01:00', 'c1', 'yearly', 'GPA.1001-0001', 299);
$put('com.sim.cam', 'done', $T0, '10:01:30', 'c1', 'yearly', 'GPA.1001-0001', 299);   // SDK retry: ek hi order
$put('com.sim.cam', 'start', $T0, '11:00:00', 'c2', 'monthly', null, 99);
$put('com.sim.cam', 'failed', $T0, '11:01:00', 'c2', 'monthly', 'USER_CANCELED', 0);
$put('com.sim.cam', 'start', $T0, '12:00:00', 'c3', 'weekly', null, 49);              // chhoda: started
$put('com.sim.cam', 'done', $T0, '00:00:00', 'c4', 'monthly', 'GPA.1002-0001', 99);   // aaj ka pehla second
$put('com.sim.cam', 'failed', $T0, '13:00:00', 'c10', 'monthly', 'Payment declined by bank', 0);
// cam, kal: 23:59:59 ka paid, do failed (code 6 = Play error; order id wala = reason nahi)
$put('com.sim.cam', 'done', $Y, '23:59:59', 'c5', 'monthly', 'GPA.1003-0001', 99);
$put('com.sim.cam', 'failed', $Y, '09:00:00', 'c6', 'yearly', '6', 299);
$put('com.sim.cam', 'failed', $Y, '09:10:00', 'c6', 'yearly', 'GPA.9999-1111', 0);
// cam, purane din
$put('com.sim.cam', 'done', $D(3), '15:00:00', 'c7', 'weekly', 'GPA.1004-0001', 49);
$put('com.sim.cam', 'done', $D(10), '15:00:00', 'c8', 'yearly', 'GPA.1005-0001', 299);
$put('com.sim.cam', 'done', $D(40), '15:00:00', 'c9', 'yearly', 'GPA.0001-0001', 999);   // raw window (35 din) ke bahar: agg_pay se
// vpn
$put('com.sim.vpn', 'done', $T0, '09:00:00', 'v1', 'monthly', 'GPA.2001-0001', 149);
$put('com.sim.vpn', 'done', $T0, '09:30:00', 'v2', 'monthly', 'GPA.2002-0001', 149);
$put('com.sim.vpn', 'failed', $T0, '09:45:00', 'v3', 'monthly', '', 0);
$put('com.sim.vpn', 'done', $Y, '10:00:00', 'v4', 'yearly', 'GPA.2003-0001', 999);
// fun: 7 din ki seema (D-6 00:00:00 andar, D-7 23:59:59 bahar)
$put('com.sim.fun', 'done', $T0, '08:00:00', 'f1', 'no_ads', 'GPA.3001-0001', 79);
$put('com.sim.fun', 'done', $D(6), '00:00:00', 'f2', 'no_ads', 'GPA.3002-0001', 79);
$put('com.sim.fun', 'done', $D(7), '23:59:59', 'f3', 'no_ads', 'GPA.3003-0001', 79);
// apps() me nahi, par events me hai
$put('com.other.app', 'done', $T0, '07:00:00', 'o1', 'coins', 'GPA.4001-0001', 10);
// fail ki wajah: Play Billing ke minus codes (D-20, alag app: baaki checks ki ginti par asar nahi)
$put('com.sim.err', 'failed', $D(20), '10:00:00', 'e1', 'monthly', '-1', 99);
$put('com.sim.err', 'failed', $D(20), '10:01:00', 'e2', 'monthly', 'code=-3', 99);
$put('com.sim.err', 'failed', $D(20), '10:02:00', 'e3', 'monthly', 'CODE -2', 99);
$put('com.sim.err', 'failed', $D(20), '10:03:00', 'e4', 'monthly', 'SERVICE-DISCONNECTED', 99);
// 3000 se zyada purchase rows (D-21): items 500 tak, totals phir bhi poore
for ($i = 0; $i < 3005; $i++) $put('com.sim.big', 'start', $D(21), sprintf('00:%02d:%02d', intdiv($i, 60) % 60, $i % 60), 'b' . $i, 'weekly', null, 49);
$put('com.sim.big', 'done', $D(21), '23:00:00', 'b0', 'weekly', 'GPA.5001-0001', 49);
// shor: doosre events (crash / session), 60 din me (index check ke liye bhi)
$noise = $pdo->prepare('INSERT INTO events (pkg, ev, ts, did, p1, p2, val) VALUES (?,?,?,?,?,?,?)');
foreach (['com.sim.cam', 'com.sim.vpn', 'com.sim.fun'] as $pkg) for ($i = 0; $i < 1200; $i++) {
    $noise->execute([$pkg, $i % 7 ? 'session' : 'crash', $utc($D($i % 60), sprintf('%02d:%02d:00', $i % 24, $i % 60)), 'noise', 'x', 'y', 500]);
}
// agg_pay: raw ke bahar ke din
$ag = $pdo->prepare('INSERT INTO agg_pay (day, pkg, ev, n, v) VALUES (?,?,?,?,?)');
foreach ([[40, 'com.sim.cam', 'purchase_done', 3, 1500], [40, 'com.sim.cam', 'purchase_failed', 2, 0], [39, 'com.sim.vpn', 'purchase_done', 1, 149],
    [39, 'com.sim.cam', 'purchase_start', 4, 0], [50, 'com.sim.cam', 'purchase_done', 9, 9999], [5, 'com.sim.cam', 'purchase_done', 7, 7777]] as [$d, $p, $e, $c, $v]) $ag->execute([$D($d), $p, $e, $c, $v]);
$pdo->commit();
$pdo->query('ANALYZE TABLE events')->fetchAll();   // result set padh lo (warna agli query atakti hai)

$byPkg = fn(array $r) => array_column($r['apps'] ?? [], null, 'pkg');

// ---------------------------------------------------------------- all apps, today
$r = api(['app' => 'all', 'range' => 'today']);
ok(($r['ok'] ?? false) === true && $r['from'] === $T0 && $r['to'] === $T0 && $r['today'] === true && $r['tz'] === 'Asia/Kolkata', 'today: ok, from = to = local today, tz Asia/Kolkata');
ok(near($r['totals']['amount'] ?? null, 785) && $r['totals']['paid'] === 6 && $r['totals']['failed'] === 3 && $r['totals']['started'] === 3,
    'today totals: ₹785, 6 paid (duplicate order once, 00:00:00 IST in), 3 failed, 3 started (got ' . json_encode($r['totals'] ?? null) . ')');
ok(array_column($r['apps'] ?? [], 'pkg') === ['com.sim.cam', 'com.sim.vpn', 'com.sim.fun', 'com.other.app'], 'apps sorted by amount (cam 398, vpn 298, fun 79, other 10)');
$A = $byPkg($r);
ok(near($A['com.sim.cam']['amount'] ?? null, 398) && $A['com.sim.cam']['paid'] === 2 && $A['com.sim.cam']['failed'] === 2 && $A['com.sim.cam']['started'] === 3 && $A['com.sim.cam']['name'] === 'Sim Camera',
    'cam today: ₹398, 2 paid, 2 failed, 3 started, name from apps()');
ok(near($A['com.sim.vpn']['amount'] ?? null, 298) && $A['com.sim.vpn']['paid'] === 2 && $A['com.sim.vpn']['failed'] === 1, 'vpn today: ₹298, 2 paid, 1 failed');
ok(isset($A['com.other.app']) && $A['com.other.app']['name'] === 'com.other.app' && near($A['com.other.app']['amount'], 10), 'app not in apps() but with events: listed, name = package');
ok(!array_key_exists('items', $r), 'app=all: no items list');
ok($r['raw_from'] === $D(34) && $r['source'] === 'raw' && $r['agg_from'] === '' && $r['agg_to'] === '', 'raw_from = today - 34 (raw_days 35), source raw');
foreach (['ok', 'from', 'to', 'tz', 'totals', 'apps', 'raw_from'] as $k) if (!array_key_exists($k, $r)) ok(false, "response has $k");

// ---------------------------------------------------------------- yesterday (23:59:59 IST in, 00:00:00 aaj ka bahar)
$r = api(['app' => 'all', 'range' => 'yesterday']);
ok($r['from'] === $Y && $r['to'] === $Y && $r['today'] === false, 'yesterday: from = to = local yesterday, today false');
ok(near($r['totals']['amount'] ?? null, 1098) && $r['totals']['paid'] === 2 && $r['totals']['failed'] === 2 && $r['totals']['started'] === 0,
    'yesterday totals: ₹1098 (vpn 999 + cam 99 at 23:59:59 IST), 2 failed (got ' . json_encode($r['totals'] ?? null) . ')');
ok(array_column($r['apps'] ?? [], 'pkg') === ['com.sim.vpn', 'com.sim.cam'], 'yesterday apps: vpn then cam');

// ---------------------------------------------------------------- 7d / 30d
$r = api(['app' => 'all', 'range' => '7d']);
$A = $byPkg($r);
ok($r['from'] === $D(6) && $r['to'] === $T0 && near($A['com.sim.fun']['amount'] ?? null, 158) && $A['com.sim.fun']['paid'] === 2, '7d: D-6 00:00:00 IST counted, D-7 23:59:59 not (fun ₹158, 2 paid)');
ok(near($A['com.sim.cam']['amount'] ?? null, 398 + 99 + 49) && $A['com.sim.cam']['failed'] === 4, '7d cam: today + yesterday + D-3 (₹546), 4 failed');
$r = api(['app' => 'com.sim.cam', 'range' => '30d']);
ok($r['from'] === $D(29) && near($r['totals']['amount'] ?? null, 845) && $r['totals']['paid'] === 5 && $r['source'] === 'raw',
    '30d cam: D-10 + D-3 + yesterday + today = ₹845, 5 orders; D-40 raw event not counted (got ' . json_encode($r['totals'] ?? null) . ')');

// ---------------------------------------------------------------- one app: items
$r = api(['app' => 'com.sim.cam', 'range' => 'today']);
$it = $r['items'] ?? [];
ok(array_column($r['apps'] ?? [], 'pkg') === ['com.sim.cam'] && near($r['totals']['amount'] ?? null, 398) && $r['totals']['paid'] === 2, 'app filter: only cam in apps + totals');
ok(array_column($it, 'status') === ['failed', 'started', 'failed', 'paid', 'paid'], 'items newest first: failed 13:00, started 12:00, failed 11:01, paid 10:01, paid 00:00 (got ' . json_encode(array_column($it, 'status')) . ')');
ok(array_column($it, 'ts') === ["$T0 13:00:00", "$T0 12:00:00", "$T0 11:01:00", "$T0 10:01:00", "$T0 00:00:00"], 'item ts = local time string (duplicate order keeps its first time)');
ok(count(array_filter($it, fn($x) => $x['order'] === 'GPA.1001-0001')) === 1 && near($it[3]['amount'], 299) && $it[3]['plan'] === 'yearly' && $it[3]['reason'] === '', 'duplicate order = one paid row (₹299, yearly, no reason)');
ok($it[0]['reason'] === 'Card declined' && near($it[0]['amount'], 99) && $it[0]['order'] === '', 'declined: reason "Card declined", amount from the plan price (₹99), no order');
ok($it[2]['reason'] === 'Cancelled' && near($it[2]['amount'], 99), 'USER_CANCELED -> "Cancelled", amount from the started price');
ok($it[1]['plan'] === 'weekly' && near($it[1]['amount'], 49) && $it[1]['reason'] === '', 'abandoned start (no paid / failed after it) = started row');
ok(count(array_filter($it, fn($x) => $x['status'] === 'started')) === 1, 'finished starts (yearly paid, monthly cancelled) not listed again');
ok(array_unique(array_column($it, 'pkg')) === ['com.sim.cam'], 'items only of the chosen app');
$keys = array_keys($it[0] ?? []); sort($keys);
ok($keys === ['amount', 'order', 'pkg', 'plan', 'reason', 'status', 'ts'], 'item fields: ts, pkg, plan, amount, status, order, reason');

$r = api(['app' => 'com.sim.cam', 'range' => 'yesterday']);
$it = $r['items'] ?? [];
ok(array_column($it, 'ts') === ["$Y 23:59:59", "$Y 09:10:00", "$Y 09:00:00"], 'yesterday items: 23:59:59 IST paid in, aaj 00:00:00 wala nahi');
ok($it[1]['status'] === 'failed' && $it[1]['reason'] === '' && $it[1]['order'] === 'GPA.9999-1111' && near($it[1]['amount'], 0), 'failed with an order id in p2: no reason, order kept, unknown price = 0');
ok($it[2]['reason'] === 'Play error' && near($it[2]['amount'], 299), 'failed code "6" -> "Play error", own amount kept');
$r = api(['app' => 'com.sim.vpn', 'range' => 'today']);
$f = array_values(array_filter($r['items'] ?? [], fn($x) => $x['status'] === 'failed'));
ok(count($f) === 1 && $f[0]['reason'] === '' && near($f[0]['amount'], 149), 'vpn failed without p2: reason empty, amount from the plan price');
$r = api(['app' => 'com.sim.err', 'from' => $D(20), 'to' => $D(20)]);
$rs = array_column($r['items'] ?? [], 'reason');
ok($rs === ['Play disconnected', 'Not supported', 'Play timeout', 'Play disconnected'], 'negative billing codes: "SERVICE-DISCONNECTED", "CODE -2", "code=-3", "-1" mapped (got ' . json_encode($rs) . ')');

// ---------------------------------------------------------------- cheap: one read over events per request
$r = api(['app' => 'all', 'range' => '30d']);
$nAll = event_reads();
ok($nAll === 1, "app=all 30d: one ts-range read over events (paid + failed + started together), got $nAll");
$cam = $byPkg($r)['com.sim.cam'] ?? [];
$r = api(['app' => 'com.sim.cam', 'range' => '30d']);
$nOne = event_reads();
ok($nOne === 1, "one app 30d: one read over events (totals from the item rows), got $nOne");
ok(near($cam['amount'] ?? null, (float)($r['totals']['amount'] ?? -1)) && ($cam['paid'] ?? -1) === $r['totals']['paid'] && ($cam['failed'] ?? -1) === $r['totals']['failed']
    && ($cam['started'] ?? -1) === $r['totals']['started'], 'one app totals (from its item rows) = its row in app=all (grouped query) (got ' . json_encode([$cam, $r['totals'] ?? null]) . ')');
$r = api(['app' => 'com.sim.big', 'from' => $D(21), 'to' => $D(21)]);
$nBig = event_reads();
ok($nBig === 2 && ($r['totals']['started'] ?? 0) === 3005 && ($r['totals']['paid'] ?? 0) === 1 && near($r['totals']['amount'] ?? null, 49) && count($r['items'] ?? []) === 500,
    "over 3000 rows: totals from a second grouped read (all 3005 starts, 1 paid), items capped at 500 (reads $nBig, got " . json_encode($r['totals'] ?? null) . ')');

// ---------------------------------------------------------------- older than raw: agg_pay
$r = api(['app' => 'all', 'from' => $D(41), 'to' => $D(38)]);
ok($r['source'] === 'agg' && $r['agg_from'] === $D(41) && $r['agg_to'] === $D(38) && $r['agg_counts'] === true, 'range before raw_from: source agg, agg_from/agg_to, counts from agg_pay.n');
ok(near($r['totals']['amount'] ?? null, 1649) && $r['totals']['paid'] === 4 && $r['totals']['failed'] === 2 && $r['totals']['started'] === 4,
    'agg totals: ₹1500 + ₹149, 4 paid, 2 failed, 4 started; raw D-40 event and agg rows outside the range ignored (got ' . json_encode($r['totals'] ?? null) . ')');
$r = api(['app' => 'com.sim.cam', 'from' => $D(40), 'to' => $T0]);
ok($r['source'] === 'mixed' && $r['agg_to'] === $D(35) && $r['raw_from'] === $D(34), 'range across raw_from: source mixed, agg_to = raw_from - 1');
ok(near($r['totals']['amount'] ?? null, 2345) && $r['totals']['paid'] === 8 && $r['totals']['failed'] === 6 && $r['totals']['started'] === 7,
    'mixed: agg ₹1500 (D-40) + raw ₹845; agg_pay rows inside the raw window not double counted (got ' . json_encode($r['totals'] ?? null) . ')');
ok(count($r['items'] ?? []) > 0 && min(array_map(fn($x) => substr($x['ts'], 0, 10), $r['items'])) >= $D(34), 'mixed: items only from raw days');
$pdo->exec('ALTER TABLE agg_pay DROP COLUMN n');
$r = api(['app' => 'all', 'from' => $D(41), 'to' => $D(38)]);
ok(($r['ok'] ?? false) && $r['agg_counts'] === false && near($r['totals']['amount'], 1649) && $r['totals']['paid'] === 0, 'agg_pay without a count column: ₹ kept, counts 0, agg_counts false');
$pdo->exec('DROP TABLE agg_pay');
$r = api(['app' => 'all', 'from' => $D(41), 'to' => $D(38)]);
ok(($r['ok'] ?? false) === true && near($r['totals']['amount'], 0) && $r['apps'] === [], 'agg_pay missing: still ok, empty');

// ---------------------------------------------------------------- input
$r = api(['app' => "x' OR 1=1 --", 'range' => 'today']);
ok(($r['app'] ?? '') === 'all' && !array_key_exists('items', $r), 'bad app value -> all apps');
$r = api(['app' => 'com.none.app', 'range' => 'today']);
ok(($r['ok'] ?? false) && $r['apps'] === [] && $r['items'] === [] && $r['totals']['paid'] === 0, 'unknown app: empty, items []');
$r = api(['app' => 'all', 'from' => $T0, 'to' => $D(1)]);
ok($r['from'] === $Y && $r['to'] === $T0, 'from > to: swapped');
$r = api(['app' => 'all', 'from' => $D(2), 'to' => (new DateTime($today))->modify('+5 day')->format('Y-m-d')]);
ok($r['to'] === $T0, 'to in the future: clamped to today');
$r = api(['app' => 'all', 'range' => 'nonsense']);
ok($r['from'] === $T0 && $r['to'] === $T0, 'unknown range: today');

// ---------------------------------------------------------------- cheap: (pkg, ts) index range, no full scan
[$f] = day_bounds_utc($T0); [, $t] = day_bounds_utc($T0);
$x = $pdo->query("EXPLAIN SELECT pkg, ev, did, IF(ev = 'purchase_done', LEFT(COALESCE(p2, ''), 300), '') o, MAX(val) v, COUNT(*) n FROM events
    WHERE pkg IN ('com.sim.cam','com.sim.vpn','com.sim.fun','com.other.app') AND ev IN ('purchase_done','purchase_failed','purchase_start') AND ts >= '$f' AND ts < '$t' GROUP BY pkg, ev, did, o")->fetch();
ok(($x['key'] ?? '') === 'k_pkg_ts' && ($x['type'] ?? '') === 'range', 'totals query uses the (pkg, ts) index as a range (key ' . ($x['key'] ?? '-') . ', type ' . ($x['type'] ?? '-') . ')');

// ---------------------------------------------------------------- auth + session
$code = (string)file_get_contents("$src/api/payments.php");
$pLogin = strpos($code, 'require_login_json();'); $pClose = strpos($code, 'session_write_close()'); $pDb = strpos($code, 'db()');
ok($pLogin !== false && $pClose !== false && $pDb !== false && $pLogin < $pClose && $pClose < $pDb, 'source: login check, then session_write_close(), then the first db()');
$r = api(['app' => 'all', 'range' => 'today'], 'deny');
ok(($r['ok'] ?? null) === false && !isset($r['totals']) && !isset($r['apps']), 'not logged in: error JSON only, no payment data');
@unlink("$T/sess/at_db.txt"); @unlink("$T/sess/at_end.txt");
$r = api(['app' => 'com.sim.cam', 'range' => 'today'], 'session');
ok(($r['ok'] ?? false) === true && near($r['totals']['amount'] ?? null, 398), 'logged in with a session: data returned');
ok(@file_get_contents("$T/sess/at_db.txt") === (string)PHP_SESSION_NONE, 'session lock released before the first DB call (status ' . @file_get_contents("$T/sess/at_db.txt") . ')');
ok(@file_get_contents("$T/sess/at_end.txt") === (string)PHP_SESSION_NONE, 'session still closed at the end of the request');

// ---------------------------------------------------------------- cleanup
$pdo->exec('DROP TABLE IF EXISTS events');
$pdo->exec('DROP TABLE IF EXISTS agg_pay');
exec('rm -rf ' . escapeshellarg($T));
echo "\n" . ($n - $fail) . " / $n passed\n";
exit($fail ? 1 : 0);
