<?php
/**
 * AI App Research Studio — tests (DEVELOPMENT ENVIRONMENT, SIMULATED DATA ONLY; Claude API responses are canned fakes, nothing calls the network).
 * Part 1 (no DB): scoring formula, recommendation rule, spec diff, spec cleaning.
 * Part 2 (MySQL, TEST_DB_HOST): economics from simulated dashboard tables, rank job (evidence validation, label downgrade, policy REJECT),
 *   budget block, Gate 1 limits, competitor analysis -> Gate 2, artifact hash conflicts, request changes -> v2 + diff, approve, retries/crash recovery/cancel,
 *   notifications + dedupe, every app view over HTTP.
 * Portfolio (your own apps, pure SQL): eCPM, earnings (ads + payments), Google Ads spend (INR + USD accounts), ROAS, exact (Ads geo report) vs estimated country spend,
 *   unknown-country bucket, deterministic insights, AI context P ids in the rank prompt, own_fit factor, view=mine, cron once a day, schema upgrade 1 -> 2.
 * Owner's server failures: AI schemas without union types (API limit 16; -1 = no evidence, own_fit not asked), economics with cc '' / NULL / 'UNK' / 'in'
 *   (no duplicate key), categories merged by the DB collation (Café/Cafe, Straße/Strase), atomic st_econ save, new temp-table cohort SQL = old SQL (edge days),
 *   no row locks under dashboard writes (READ COMMITTED), fallback to the old SQL without CREATE TEMPORARY TABLES.
 * CI: .github/workflows/server-tests.yml. Local: TEST_DB_HOST=127.0.0.1 TEST_DB_NAME=mfc_test TEST_DB_USER=root TEST_DB_PASS=... php server/tests/studio_test.php
 */
declare(strict_types=1);

$src = dirname(__DIR__);
$T = sys_get_temp_dir() . '/mfc_studio_test_' . getmypid();
$M = "$T/mfc_dash";
foreach (['lib', 'api'] as $d) @mkdir("$M/$d", 0777, true);
// stub common.php, but http_json returns canned Claude responses from a queue (file based, so the HTTP test server sees the same queue)
$stub = (string)file_get_contents("$src/tests/stub_common.php");
$stub = preg_replace('/^function http_json\(.*$/m', <<<'PHP'
function http_json(string $method, string $url, ?string $body = null, array $headers = [], int $timeout = 30): array
{
    $q = getenv('STUDIO_FAKE_Q') ?: (sys_get_temp_dir() . '/studio_fake_q.json');
    $list = is_file($q) ? (array)json_decode((string)file_get_contents($q), true) : [];
    @file_put_contents($q . '.log', $url . ' ' . substr((string)$body, 0, 2000000) . "\n", FILE_APPEND);
    if (!str_contains($url, 'api.anthropic.com') || !$list) return [0, '', 'network disabled in tests'];
    $next = array_shift($list);
    file_put_contents($q, json_encode($list));
    return [(int)$next[0], is_string($next[1]) ? $next[1] : json_encode($next[1]), ''];
}
PHP, $stub, 1);
file_put_contents("$M/lib/common.php", $stub);
foreach (['lib/cost.php', 'lib/health.php', 'lib/studio.php', 'api/studio.php'] as $f) copy("$src/$f", "$M/$f");
$Q = "$T/fake_q.json";
putenv("STUDIO_FAKE_Q=$Q");
require "$M/lib/studio.php";

$fail = 0; $n = 0;
function ok(bool $c, string $m): void { global $fail, $n; $n++; echo ($c ? 'ok   ' : 'FAIL ') . $m . "\n"; if (!$c) $fail++; }

// ================================================================ Part 1: pure logic
$w = array_map(fn($f) => (float)$f[3], ST_FACTORS);
$F = function (array $r, string $basis = 'INFERENCE') { $o = []; foreach (ST_FACTORS as $k => $_) $o[$k] = ['rating' => $r[$k] ?? null, 'basis' => isset($r[$k]) ? $basis : 'UNKNOWN', 'evidence' => [], 'note' => '']; return $o; };
$s = st_score($F([]), $w, 'organic', 0);
ok($s['score'] === null && $s['coverage'] === 0.0 && $s['confidence'] === 'Low', 'no data -> no score (never filled with zero/average)');
$s = st_score($F(['demand' => 10]), $w, 'organic', 1);
ok($s['score'] === 100.0 && $s['coverage'] < 0.2, 'one good factor: score over available factors only, coverage shows the gap');
$s1 = st_score($F(['demand' => 8, 'competition' => 2]), $w, 'organic', 1);
$s2 = st_score($F(['demand' => 8, 'competition' => 9]), $w, 'organic', 1);
ok($s1['score'] > $s2['score'], 'more competition lowers the score (direction -1)');
$sv = st_score($F(['demand' => 6, 'search' => 6, 'growth' => 6, 'competition' => 4, 'retention' => 6, 'policy_risk' => 2, 'monetization' => 6, 'rev_per_user' => 6, 'complexity' => 3, 'differentiation' => 6], 'VERIFIED'), $w, 'organic', 3);
$si = st_score($F(['demand' => 6, 'search' => 6, 'growth' => 6, 'competition' => 4, 'retention' => 6, 'policy_risk' => 2, 'monetization' => 6, 'rev_per_user' => 6, 'complexity' => 3, 'differentiation' => 6], 'INFERENCE'), $w, 'organic', 3);
ok($sv['score'] === $si['score'] && $sv['confidence'] === 'High' && $si['confidence'] !== 'High', 'same numbers: verified data -> High confidence, AI inference -> lower confidence');
ok(st_score($F(['demand' => 6, 'search' => 6, 'growth' => 6, 'competition' => 4, 'retention' => 6, 'policy_risk' => 2, 'monetization' => 6, 'rev_per_user' => 6, 'complexity' => 3, 'differentiation' => 6], 'VERIFIED'), $w, 'organic', 1)['confidence'] !== 'High',
    'single source never gives High confidence');
$paidOnly = st_score($F(['ad_activity' => 9]), $w, 'organic', 1);
ok($paidOnly['score'] === null, 'paid-only factor does not leak into organic score');
ok(st_rule_rec(90, 90, 'High', $F(['policy_risk' => 8]), 1.0) === 'REJECT', 'high policy risk -> REJECT whatever the score');
ok(st_rule_rec(70, null, 'High', $F([]), 0.8) === 'STRONGLY CONSIDER' && st_rule_rec(70, null, 'Medium', $F([]), 0.8) === 'TEST FIRST', 'strong score needs High confidence for STRONGLY CONSIDER');
ok(st_rule_rec(80, 80, 'High', $F([]), 0.3) === 'WATCH MARKET', 'low coverage caps the recommendation');
ok(st_cautious('STRONGLY CONSIDER', 'WATCH MARKET') === 'WATCH MARKET' && st_cautious('REJECT', 'TEST FIRST') === 'REJECT', 'final = the more cautious of AI and rule');
$d = st_spec_diff(['features' => [['name' => 'Widget', 'priority' => 'MUST HAVE'], ['name' => 'Sound', 'priority' => 'SHOULD HAVE']]],
    ['features' => [['name' => 'Widget', 'priority' => 'OPTIONAL'], ['name' => 'Meter', 'priority' => 'MUST HAVE']]]);
ok(in_array('Widget: MUST HAVE → OPTIONAL', $d, true) && in_array('Added: Meter (MUST HAVE)', $d, true) && in_array('Removed: Sound', $d, true), 'spec diff lists priority change, added, removed');
$cs = st_clean_spec(['competitors' => [['name' => 'X', 'package' => 'not a package', 'play_url' => 'https://evil.example/x', 'evidence' => ['S1', 'S9'],
    'complaints' => [['issue' => 'crash', 'class' => 'USER-REPORTED ISSUE', 'frequency' => 'many', 'evidence' => []]]]],
    'features' => [['name' => 'GPS track earbuds', 'priority' => 'MUST HAVE', 'feasible_on_android' => false, 'evidence' => ['S1']]]], ['S1' => 11]);
ok($cs['competitors'][0]['package'] === '' && $cs['competitors'][0]['play_url'] === '' && $cs['competitors'][0]['evidence'] === [11], 'spec: bad package/URL dropped, unknown evidence id dropped');
ok($cs['competitors'][0]['complaints'][0]['class'] === 'SUSPECTED ISSUE', 'complaint without evidence downgraded to SUSPECTED ISSUE');
ok($cs['features'][0]['priority'] === 'REJECT', 'feature Android cannot do -> REJECT');
ok(json_encode(st_opp_schema()) !== false && json_encode(st_comp_schema()) !== false, 'JSON schemas encode');
// Claude API: more than 16 union-typed params (type arrays / anyOf / oneOf) -> HTTP 400 (owner's weekly ranking failed with 17 after own_fit was added)
$unions = function ($node) use (&$unions): int {
    if (!is_array($node)) return 0;
    $n = (isset($node['type']) && is_array($node['type']) && array_is_list($node['type'])) || isset($node['anyOf']) || isset($node['oneOf']) ? 1 : 0;
    foreach ($node as $v) $n += $unions($v);
    return $n;
};
$code = (string)file_get_contents("$src/lib/studio.php");
preg_match_all("/'schema'\s*=>\s*([a-z_]+)\(\)/", $code, $mm);
$schemaFns = array_values(array_unique($mm[1]));
$uc = array_combine($schemaFns, array_map(fn($f) => $unions($f()), $schemaFns));
ok(count($mm[1]) === preg_match_all("/'schema'\s*=>(?!\s*\\\$o\[)/", $code) && count($mm[1]) >= 4 && in_array('st_opp_schema', $schemaFns, true) && in_array('st_comp_schema', $schemaFns, true),
    'every schema passed to st_ai comes from a schema function (' . implode(', ', $schemaFns) . ')');
ok($uc['st_opp_schema'] === 0 && max($uc) <= 10, 'union types per schema: st_opp_schema 0, others <= 10 (' . json_encode($uc) . ')');
$ofp = st_opp_schema()['properties']['opportunities']['items']['properties']['factors'];
ok(array_keys($ofp['properties']) === st_ai_factors() && $ofp['required'] === st_ai_factors() && !isset($ofp['properties']['own_fit']) && count(st_ai_factors()) === count(ST_FACTORS) - 1
    && $ofp['properties']['demand']['properties']['rating']['type'] === 'number', 'AI schema: rating is a plain number; server-computed own_fit is not asked from the AI');
$cs2 = st_clean_spec(['features' => [['name' => 'A', 'priority' => 'MUST HAVE', 'feasible_on_android' => true, 'effort_days' => -1, 'evidence' => []],
    ['name' => 'B', 'priority' => 'OPTIONAL', 'feasible_on_android' => true, 'effort_days' => 3, 'evidence' => []]]], []);
ok($cs2['features'][0]['effort_days'] === null && $cs2['features'][1]['effort_days'] === 3, 'spec: effort_days -1 (unknown) -> null, 3 stays 3');
ok(st_pctl([1, 2, 3, 4], 50) === 2.5 && st_pctl([], 50) === null, 'percentile helper');

if (!getenv('TEST_DB_HOST')) { echo "\nTEST_DB_HOST not set: DB part skipped\n$n checks, $fail failed\n"; exit($fail > 0 ? 1 : 0); }

// ================================================================ Part 2: MySQL + simulated data
function q(string $sql, array $p = []): array { return h_q(db(), $sql, $p); }
function q1(string $sql, array $p = []) { return h_q1(db(), $sql, $p); }
$fake = function (array $responses) use ($Q) { file_put_contents($Q, json_encode($responses)); };
$left = function () use ($Q) { return count((array)json_decode((string)@file_get_contents($Q), true)); };
$pdo = db();
foreach (['kv', 'app_alerts', 'health_events', 'health_emails', 'day_install', 'day_user', 'imps', 'devices', 'gads_campaign_day', 'events', 'ga_geo_day', 'ga_camp', 'ga_accounts',
    'zz_imps', 'zz_events', 'zz_day_install', 'zz_devices', 'zz_gads_campaign_day',
    'st_runs', 'st_events', 'st_evidence', 'st_snapshots', 'st_econ', 'st_app_meta', 'st_opps', 'st_projects', 'st_artifacts', 'st_approvals', 'st_usage', 'st_sources', 'st_notified', 'st_port'] as $t) $pdo->exec("DROP TABLE IF EXISTS $t");
$pdo->exec("CREATE TABLE kv (k VARCHAR(64) NOT NULL PRIMARY KEY, v TEXT NOT NULL, updated_at DATETIME NOT NULL)");
$pdo->exec("CREATE TABLE app_alerts (id INT UNSIGNED NOT NULL AUTO_INCREMENT, akey VARCHAR(190) NOT NULL, level TINYINT NOT NULL, rule VARCHAR(40) NOT NULL, pkg VARCHAR(100) NOT NULL DEFAULT '',
  account VARCHAR(20) NOT NULL DEFAULT '', campaign VARCHAR(255) NOT NULL DEFAULT '', title VARCHAR(255) NOT NULL, body VARCHAR(2000) NOT NULL DEFAULT '', title_hi VARCHAR(255) NOT NULL DEFAULT '',
  body_hi VARCHAR(2000) NOT NULL DEFAULT '', ai VARCHAR(3000) NOT NULL DEFAULT '', metrics TEXT NULL, fired INT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
  resolved_at DATETIME NULL, read_at DATETIME NULL, PRIMARY KEY (id))");
$pdo->exec("CREATE TABLE health_events (id INT AUTO_INCREMENT PRIMARY KEY, run_id INT NULL, issue_id INT NULL, fix_id INT NULL, pkg VARCHAR(100), level VARCHAR(10), msg VARCHAR(1000), created_at DATETIME(3))");
$pdo->exec("CREATE TABLE day_install (day DATE NOT NULL, pkg VARCHAR(100) NOT NULL, did CHAR(36) NOT NULL, lh DATETIME NOT NULL, cc CHAR(2) NOT NULL DEFAULT '', PRIMARY KEY (day, pkg, did), KEY k_pkg_day (pkg, day))");
$pdo->exec("CREATE TABLE day_user (day DATE NOT NULL, pkg VARCHAR(100) NOT NULL, did CHAR(36) NOT NULL, PRIMARY KEY (day, pkg, did), KEY k_pkg_day (pkg, day))");
$pdo->exec("CREATE TABLE imps (id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, rid CHAR(36) NOT NULL, pkg VARCHAR(100) NOT NULL, did CHAR(36) NOT NULL, ts DATETIME(3) NOT NULL, cc CHAR(2) NOT NULL DEFAULT '',
  rev_usd DECIMAL(18,10) NOT NULL DEFAULT 0, UNIQUE KEY u_rid (rid), KEY k_pkg_ts (pkg, ts))");
$pdo->exec("CREATE TABLE devices (pkg VARCHAR(100) NOT NULL, did CHAR(36) NOT NULL, first_seen DATETIME NOT NULL, last_seen DATETIME NOT NULL, cc CHAR(2) NOT NULL DEFAULT '', src VARCHAR(40) NOT NULL DEFAULT '',
  utm_campaign VARCHAR(150) NOT NULL DEFAULT '', gclid VARCHAR(200) NOT NULL DEFAULT '', PRIMARY KEY (pkg, did))");
$pdo->exec("CREATE TABLE gads_campaign_day (customer_id VARCHAR(20) NOT NULL, day DATE NOT NULL, campaign_id VARCHAR(30) NOT NULL, campaign VARCHAR(255) NOT NULL DEFAULT '', pkg VARCHAR(100) NOT NULL DEFAULT '',
  status VARCHAR(20) NOT NULL DEFAULT '', currency CHAR(3) NOT NULL DEFAULT 'INR', cost DECIMAL(16,4) NOT NULL DEFAULT 0, impressions BIGINT NOT NULL DEFAULT 0, clicks BIGINT NOT NULL DEFAULT 0,
  conversions DECIMAL(14,2) NOT NULL DEFAULT 0, conv_value DECIMAL(16,4) NOT NULL DEFAULT 0, updated_at DATETIME NOT NULL, PRIMARY KEY (customer_id, day, campaign_id))");
// dashboard events (payments = purchase_done) + Ads Script tables (lib/gads_health.php): same columns the portfolio job reads
$pdo->exec("CREATE TABLE events (id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, pkg VARCHAR(100) NOT NULL, ev VARCHAR(40) NOT NULL, ts DATETIME(3) NOT NULL, did CHAR(36) NOT NULL,
  p1 VARCHAR(255) NOT NULL DEFAULT '', p2 TEXT NULL, val DOUBLE NOT NULL DEFAULT 0, KEY k_pkg_ts (pkg, ts))");
$pdo->exec("CREATE TABLE ga_accounts (customer_id VARCHAR(20) NOT NULL PRIMARY KEY, company VARCHAR(160) NOT NULL DEFAULT '', currency CHAR(3) NOT NULL DEFAULT '', updated_at DATETIME NULL)");
$pdo->exec("CREATE TABLE ga_camp (customer_id VARCHAR(20) NOT NULL, campaign_id VARCHAR(30) NOT NULL, name VARCHAR(255) NOT NULL DEFAULT '', status VARCHAR(20) NOT NULL DEFAULT '',
  pkg VARCHAR(100) NOT NULL DEFAULT '', updated_at DATETIME NULL, PRIMARY KEY (customer_id, campaign_id))");
$pdo->exec("CREATE TABLE ga_geo_day (customer_id VARCHAR(20) NOT NULL, day DATE NOT NULL, campaign_id VARCHAR(30) NOT NULL, country CHAR(2) NOT NULL, cost DECIMAL(16,4) NOT NULL DEFAULT 0,
  impressions BIGINT NOT NULL DEFAULT 0, clicks BIGINT NOT NULL DEFAULT 0, conversions DECIMAL(14,2) NOT NULL DEFAULT 0, conv_value DECIMAL(16,4) NOT NULL DEFAULT 0, updated_at DATETIME NULL,
  PRIMARY KEY (customer_id, day, campaign_id, country))");
kv_set('h_schema_v', '1');
st_schema($pdo);
ok((int)q1("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'st\\_%'") === 14, 'schema: 14 studio tables created (incl. st_port)');

// SIMULATED dashboard data: 2 Tools apps, IN organic + paid installs 10..20 days ago, retention, D7 ad revenue, Google Ads CPI
$pdo->beginTransaction();
$ins = $pdo->prepare('INSERT INTO day_install (day, pkg, did, lh, cc) VALUES (?,?,?,?,?)');
$usr = $pdo->prepare('INSERT IGNORE INTO day_user (day, pkg, did) VALUES (?,?,?)');
$dev = $pdo->prepare('INSERT INTO devices (pkg, did, first_seen, last_seen, cc, gclid) VALUES (?,?,?,?,?,?)');
$imp = $pdo->prepare('INSERT INTO imps (rid, pkg, did, ts, cc, rev_usd) VALUES (?,?,?,?,?,?)');
$apps = ['com.sim.torch' => 0.01, 'com.sim.cleaner' => 0.02];
$k = 0;
foreach ($apps as $pkg => $rev) for ($d = 10; $d <= 20; $d++) {
    $day = gmdate('Y-m-d', time() - $d * 86400);
    for ($i = 0; $i < 40; $i++) {
        $k++; $did = sprintf('%08d-0000-0000-0000-000000000000', $k);
        $paid = $i >= 30;
        $ins->execute([$day, $pkg, $did, "$day 10:00:00", 'IN']);
        $dev->execute([$pkg, $did, "$day 10:00:00", "$day 10:00:00", 'IN', $paid ? 'gclid-x' : '']);
        $usr->execute([$day, $pkg, $did]);
        if ($i % 5 < 2) $usr->execute([gmdate('Y-m-d', strtotime("$day +1 day")), $pkg, $did]);
        if ($i % 10 < 1) $usr->execute([gmdate('Y-m-d', strtotime("$day +7 day")), $pkg, $did]);
        $imp->execute([sprintf('r%010d', $k), $pkg, $did, "$day 11:00:00", 'IN', $paid ? $rev * 0.6 : $rev]);
        $imp->execute([sprintf('s%010d', $k), $pkg, $did, gmdate('Y-m-d', strtotime("$day +9 day")) . ' 11:00:00', 'IN', 5.0]);   // after D7: must NOT count
    }
}
for ($d = 1; $d <= 30; $d++) h_x($pdo, "INSERT INTO gads_campaign_day (customer_id, day, campaign_id, pkg, currency, cost, conversions, updated_at) VALUES ('1112223333', ?, '1', 'com.sim.torch', 'INR', 950, 100, UTC_TIMESTAMP())", [gmdate('Y-m-d', time() - $d * 86400)]);
$pdo->commit();
foreach (array_keys($apps) as $p) h_x($pdo, "INSERT INTO st_app_meta (pkg, category, set_by, updated_at) VALUES (?, 'Tools', 'test', UTC_TIMESTAMP())", [$p]);

// ---------------------------------------------------------------- economics (VERIFIED)
$rid = st_run_new($pdo, 'economics', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
ok(q1('SELECT status FROM st_runs WHERE id = ?', [$rid]) === 'DONE', 'economics run DONE');
$e = st_econ_get($pdo, 'Tools', 'IN', 'organic');
ok($e && (int)$e['installs'] === 660 && abs((float)$e['d1'] - 0.4) < 0.01 && abs((float)$e['d7'] - 0.1) < 0.01, 'economics: 660 organic installs, D1 40%, D7 10% (simulated)');
ok($e && abs((float)$e['rev7_p50'] - 0.015) < 1e-6, 'economics: D7 revenue/install median $0.015; revenue after day 7 excluded');
ok($e && abs((float)$e['cpi_usd'] - 0.1) < 1e-6, 'economics: CPI = INR cost / 95 / conversions = $0.10');
$ev = q('SELECT * FROM st_evidence WHERE source = \'internal\' AND label = \'VERIFIED\'');
ok(count($ev) >= 4 && str_contains($ev[0]['method'], 'cohort'), 'economics evidence stored as VERIFIED with methodology');
$pf = st_profit($pdo, 'Tools', ['IN'], '');
ok($pf['status'] === 'ok' && $pf['basis'] === 'category' && abs($pf['scenarios']['base']['ltv_usd'] - 0.6 * 0.015 * 1.8) < 1e-6, 'profit: base LTV from PAID cohort (paid users earn less) x 1.8');
ok($pf['scenarios']['base']['payback_installs'] === null, 'profit: dev cost not set -> payback unknown (no invented cost)');
$pf2 = st_profit($pdo, 'Entertainment', ['US'], '');
ok($pf2['basis'] === 'portfolio' && str_contains($pf2['why'], 'portfolio'), 'profit: no same-category apps -> portfolio average, clearly labelled');
// AI factor cleaning: -1 = "no evidence" (schema has no nullable types) must become null/UNKNOWN, never 0
$cf = st_clean_factors($pdo, ['demand' => ['rating' => -1, 'basis' => 'INFERENCE', 'evidence' => [], 'note' => 'none'], 'growth' => ['rating' => 7, 'basis' => 'INFERENCE', 'evidence' => [], 'note' => 'n'],
    'competition' => ['rating' => 0, 'basis' => 'INFERENCE', 'evidence' => [], 'note' => 'n'], 'search' => ['rating' => -0.5, 'basis' => 'INFERENCE', 'evidence' => [], 'note' => 'n'],
    'cost' => ['rating' => 14, 'basis' => 'INFERENCE', 'evidence' => [], 'note' => 'n'], 'own_fit' => ['rating' => 9, 'basis' => 'INFERENCE', 'evidence' => [], 'note' => 'ai should not rate this']], []);
ok($cf['demand']['rating'] === null && $cf['demand']['basis'] === 'UNKNOWN' && $cf['search']['rating'] === null && $cf['growth']['rating'] === 7.0 && $cf['growth']['basis'] === 'INFERENCE'
    && $cf['competition']['rating'] === 0.0 && $cf['cost']['rating'] === 10.0 && $cf['own_fit']['rating'] === null && $cf['own_fit']['basis'] === 'UNKNOWN' && array_keys($cf) === array_keys(ST_FACTORS),
    'clean factors: rating -1 -> null/UNKNOWN (not 0), 7 stays 7, 0 stays 0, 14 capped 10, AI own_fit ignored, all factors returned');

// ---------------------------------------------------------------- economics: owner's server failures (Duplicate entry '*--paid', 34-minute cohort SQL)
// SIMULATED edge cohort, install day D = 12 days ago unless noted. cc NULL / 'UNK' / ' us' need a wider nullable column (some dashboards have one); restored below.
$pdo->exec("ALTER TABLE day_install MODIFY cc VARCHAR(8) NULL DEFAULT ''");
$pdo->exec("ALTER TABLE st_app_meta MODIFY category VARCHAR(100) NOT NULL DEFAULT ''");   // to feed 60+ char categories (cap check)
$ek = 0;
$seed = function (string $pkg, string $did, int $ago, ?string $cc, string $paid = '', array $active = [], array $rev = [], bool $device = true, ?string $userPkg = null) use ($pdo, &$ek): void {
    $day = gmdate('Y-m-d', time() - $ago * 86400);
    $at = fn(int $o) => gmdate('Y-m-d', strtotime(sprintf('%s %+d day', $day, $o)));
    h_x($pdo, 'INSERT INTO day_install (day, pkg, did, lh, cc) VALUES (?,?,?,?,?)', [$day, $pkg, $did, "$day 10:00:00", $cc]);
    if ($device) h_x($pdo, 'INSERT IGNORE INTO devices (pkg, did, first_seen, last_seen, cc, gclid, utm_campaign) VALUES (?,?,?,?,?,?,?)',
        [strtolower($pkg), $did, "$day 10:00:00", "$day 10:00:00", '', $paid === 'gclid' ? 'g-1' : '', $paid === 'utm' ? 'camp-1' : '']);
    foreach ($active as $o) h_x($pdo, 'INSERT IGNORE INTO day_user (day, pkg, did) VALUES (?,?,?)', [$at($o), $userPkg ?? $pkg, $did]);
    foreach ($rev as [$o, $t, $v]) { $ek++; h_x($pdo, 'INSERT INTO imps (rid, pkg, did, ts, cc, rev_usd) VALUES (?,?,?,?,?,?)', ["edge-$ek", strtolower($pkg), $did, $at($o) . " $t", '', $v]); }
};
$EA = 'com.edge.app';
$seed($EA, 'e01', 12, 'in', '', [0, 1, 7, 8], [[0, '00:00:00.000', '0.1000000001'], [6, '23:59:59.999', '0.2'], [7, '00:00:00.000', '5'], [-1, '23:59:59.999', '7']]);   // lowercase cc; rev at D+7 00:00 and before D excluded
$seed($EA, 'e02', 12, 'IN', 'gclid', [1], [[3, '12:00:00', '0.3']]);
$seed($EA, 'e03', 12, 'IN', 'utm', [8]);                                     // paid via utm only; active on day 8 is not D7
$seed($EA, 'e04', 12, '', 'gclid', [7], [[2, '12:00:00', '0.05']]);          // blank cc
$seed($EA, 'e05', 12, null, '', [1], [[0, '12:00:00', '0.01']]);             // NULL cc
$seed($EA, 'e06', 12, 'UNK', 'utm', [1, 7], [[6, '12:00:00', '0.02']]);      // 3-letter junk cc
$seed($EA, 'e07', 12, ' us', '', [7], [], false);                            // padded lowercase cc, no devices row (organic)
$seed($EA, 'e08', 12, 'IN', '', [1], [[2, '12:00:00', '1'], [5, '12:00:00', '2']]);
$seed($EA, 'e08', 9, 'IN', '', [1, 7]);                                      // same device installed again 3 days later: D+5 revenue counts for both installs
$seed($EA, 'e09', 28, 'IN', '', [7], [[6, '12:00:00', '0.4']]);              // first day of the window
$seed($EA, 'e10', 8, 'IN', 'gclid', [1, 7], [[6, '23:00:00', '0.6'], [7, '00:00:00.000', '9']]);   // last day of the window
$seed($EA, 'e11', 29, 'IN', '', [1, 7], [[1, '12:00:00', '3']]);             // outside the window
$seed($EA, 'e12', 7, 'IN', '', [1], [[1, '12:00:00', '3']]);                 // outside the window
$seed('COM.EDGE.APP', 'e13', 12, 'IN', '', [1], [[0, '12:00:00', '0.07']], true, 'Com.Edge.App');   // pkg spelled in other cases
h_x($pdo, "INSERT INTO imps (rid, pkg, did, ts, cc, rev_usd) VALUES ('edge-x', 'com.edge.app', 'not-in-cohort', ?, '', 50)", [gmdate('Y-m-d', time() - 12 * 86400) . ' 12:00:00']);
$seed('com.edge.two', 'f01', 12, 'IN', '', [1]);
$longA = 'Long' . str_repeat('y', 56) . ' one'; $longB = 'LONG' . str_repeat('Y', 56) . ' two';   // same first 60 chars apart from case
$seed('com.edge.l1', 'g01', 12, 'IN'); $seed('com.edge.l2', 'g02', 12, 'IN');
foreach (['com.edge.app' => 'Edge', 'com.edge.two' => 'edge', 'com.edge.l1' => $longA, 'com.edge.l2' => $longB] as $p => $c) h_x($pdo, "INSERT INTO st_app_meta (pkg, category, set_by, updated_at) VALUES (?, ?, 'test', UTC_TIMESTAMP())", [$p, $c]);
// category names only the DB collation decides on (MariaDB general_ci: é = e, ß = s; MySQL 0900_ai_ci: é = e, ß = ss): one app each, 1 organic IN install. Not PHP's fold (with or without intl)
$catPairs = [['Café', 'Cafe'], ['Straße', 'Strase'], ['खेल', 'खल'], ['Tab', "\u{3000}tab\u{00A0}"]];
foreach ($catPairs as $i => $pair) foreach ($pair as $j => $c) {
    $seed("com.edge.c$i$j", "c$i$j", 12, 'IN');
    h_x($pdo, "INSERT INTO st_app_meta (pkg, category, set_by, updated_at) VALUES (?, ?, 'test', UTC_TIMESTAMP())", ["com.edge.c$i$j", $c]);
}
$seed('com.edge.star', 'cst', 12, 'IN');   // fullwidth asterisk: if the DB collation says it equals '*' it must not collide with the all-apps row
h_x($pdo, "INSERT INTO st_app_meta (pkg, category, set_by, updated_at) VALUES ('com.edge.star', ?, 'test', UTC_TIMESTAMP())", ["\u{FF0A}"]);
$eFrom = gmdate('Y-m-d', time() - 28 * 86400); $eTo = gmdate('Y-m-d', time() - 8 * 86400);
// old SQL (correlated EXISTS + imps join) vs new temp-table SQL: identical per (pkg, cc, users type)
$byKey = function (array $res): array {
    $o = [];
    foreach ($res[0] as $r) { $k = strtolower($r['pkg']) . '|' . ($r['cc'] === null ? 'NULL' : strtoupper($r['cc'])) . '|' . $r['ut']; $o[$k] = ['i' => (int)$r['installs'], 'r1' => (int)$r['r1'], 'r7' => (int)$r['r7'], 'rev' => 0.0]; }
    foreach ($res[1] as $r) { $k = strtolower($r['pkg']) . '|' . ($r['cc'] === null ? 'NULL' : strtoupper($r['cc'])) . '|' . $r['ut']; $o[$k]['rev'] = ($o[$k]['rev'] ?? 0) + (float)$r['rev']; }
    ksort($o);
    return $o;
};
$old = st_econ_cohorts_legacy($pdo, $eFrom, $eTo);
$new = st_econ_cohorts_fast($pdo, $eFrom, $eTo);
$ko = $byKey($old); $kn = $byKey($new);
$same = count($old[0]) === count($new[0]) && count($old[1]) === count($new[1]) && array_keys($ko) === array_keys($kn);
foreach ($ko as $gk => $v) $same = $same && $v['i'] === $kn[$gk]['i'] && $v['r1'] === $kn[$gk]['r1'] && $v['r7'] === $kn[$gk]['r7'] && abs($v['rev'] - $kn[$gk]['rev']) < 1e-12;
ok($same && count($ko) >= 10, 'cohort SQL: new temp-table query gives exactly the old results (' . count($ko) . ' pkg/cc/type groups incl. NULL, blank, UNK, lowercase, paid gclid/utm)');
$x = $kn['com.edge.app|IN|organic'] ?? []; $y = $kn['com.edge.app|IN|paid'] ?? [];
ok(($x['i'] ?? 0) === 5 && $x['r1'] === 4 && $x['r7'] === 3 && abs($x['rev'] - 5.7700000001) < 1e-12 && ($y['i'] ?? 0) === 3 && $y['r1'] === 2 && $y['r7'] === 1 && abs($y['rev'] - 0.9) < 1e-12
    && ($kn['com.edge.app|UNK|paid']['r7'] ?? 0) === 1 && ($kn['com.edge.app|NULL|organic']['r1'] ?? 0) === 1 && ($kn['com.edge.app| US|organic']['r7'] ?? 0) === 1,
    'cohort SQL: D1 = day+1 only, D7 = day+7 (not day+8), D7 revenue from install day 00:00 to day+7 00:00 (boundary excluded), window edges, re-install, pkg case');
$tmpGone = true;
foreach (['st_tmp_coh', 'st_tmp_ret', 'st_tmp_rev'] as $t) { try { $pdo->query("SELECT 1 FROM $t"); $tmpGone = false; } catch (PDOException $e) {} }
ok($tmpGone, 'temp tables dropped after the cohort SQL');
// fast path reads without locks (READ COMMITTED), like the old plain SELECTs: a dashboard transaction holding row locks in the window does not make it wait / time out / fall back
$dsn = 'mysql:host=' . getenv('TEST_DB_HOST') . ';dbname=' . getenv('TEST_DB_NAME') . ';charset=utf8mb4';
$lk = new PDO($dsn, (string)getenv('TEST_DB_USER'), (string)getenv('TEST_DB_PASS'), [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
$lk->beginTransaction();
foreach (['day_install' => 'day BETWEEN ? AND ?', 'day_user' => 'day BETWEEN ? AND ? + INTERVAL 7 DAY', 'imps' => "pkg = 'com.edge.app' AND ts >= ? AND ts < ? + INTERVAL 7 DAY"] as $t => $w) {
    $st = $lk->prepare("SELECT COUNT(*) FROM $t WHERE $w FOR UPDATE"); $st->execute([$eFrom, $eTo]); $st->fetchAll();
}
$isoBefore = st_iso_get($pdo);
$pdo->exec('SET SESSION innodb_lock_wait_timeout = 1');
$pdo->exec('CREATE TEMPORARY TABLE st_tmp_lockchk (did VARCHAR(36))');
try { h_x($pdo, 'INSERT INTO st_tmp_lockchk SELECT did FROM day_user WHERE day BETWEEN ? AND ?', [$eFrom, $eTo]); $rrWaits = false; } catch (PDOException $e) { $rrWaits = str_contains($e->getMessage(), '1205'); }
$pdo->exec('DROP TEMPORARY TABLE st_tmp_lockchk');
try { $locked = $byKey(st_econ_cohorts_fast($pdo, $eFrom, $eTo)) === $kn; $lkErr = ''; } catch (PDOException $e) { $locked = false; $lkErr = $e->getMessage(); }
$pdo->exec('SET SESSION innodb_lock_wait_timeout = DEFAULT');
$lk->rollBack(); $lk = null;
ok($isoBefore === 'REPEATABLE READ' && $locked && st_iso_get($pdo) === $isoBefore,
    'fast cohort SQL under dashboard row locks: same result, no lock wait (READ COMMITTED), isolation level restored (control: same INSERT ... SELECT at REPEATABLE READ '
    . ($rrWaits ? 'times out' : 'did not wait') . ')' . ($lkErr ? " · $lkErr" : ''));
// log_bin + binlog_format=STATEMENT: RC would raise 1665 (-> slow fallback), so the level stays as it is and the fast path still runs
final class StStmtBinlogPdo extends PDO
{
    public function query(string $query, ?int $fetchMode = null, mixed ...$args): PDOStatement|false
    {
        if (str_contains($query, '@@GLOBAL.log_bin')) $query = "SELECT 1, 'STATEMENT'";
        return $fetchMode === null ? parent::query($query) : parent::query($query, $fetchMode, ...$args);
    }
}
$sp = new StStmtBinlogPdo($dsn, (string)getenv('TEST_DB_USER'), (string)getenv('TEST_DB_PASS'), [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC]);
$sp->exec("SET time_zone = '+00:00'");
$warnsBefore = (int)q1("SELECT COUNT(*) FROM st_events WHERE level = 'warn'");
ok(st_iso_rc($sp) === null && st_iso_get($sp) === 'REPEATABLE READ' && $byKey(st_econ_cohorts($sp, $eFrom, $eTo)) === $kn && (int)q1("SELECT COUNT(*) FROM st_events WHERE level = 'warn'") === $warnsBefore,
    'binlog_format=STATEMENT: isolation not switched (would be error 1665), fast path still used, no fallback warning');
$sp = null;
// no CREATE TEMPORARY TABLES privilege -> warn + old query, same result (job keeps working)
final class StNoTmpPdo extends PDO
{
    private function deny(string $q): void { if (stripos($q, 'CREATE TEMPORARY') !== false) throw new PDOException("SQLSTATE[42000]: Syntax error or access violation: 1044 Access denied for user 'mfc'@'localhost' to database 'mfc'"); }
    public function prepare(string $query, array $options = []): PDOStatement|false { $this->deny($query); return parent::prepare($query, $options); }
    public function exec(string $statement): int|false { $this->deny($statement); return parent::exec($statement); }
}
$np = new StNoTmpPdo('mysql:host=' . getenv('TEST_DB_HOST') . ';dbname=' . getenv('TEST_DB_NAME') . ';charset=utf8mb4', (string)getenv('TEST_DB_USER'), (string)getenv('TEST_DB_PASS'),
    [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC, PDO::ATTR_EMULATE_PREPARES => true]);
$np->exec("SET time_zone = '+00:00'");
ok(st_econ_cohorts($np, $eFrom, $eTo) === $old && (int)q1("SELECT COUNT(*) FROM st_events WHERE level = 'warn' AND msg LIKE '%fast cohort SQL failed%Access denied%'") === 1,
    'no CREATE TEMPORARY TABLES privilege: warning logged, old query used, same result');
$np = null;
$rid = st_run_new($pdo, 'economics', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
$er = $pdo->query("SELECT * FROM st_runs WHERE id = $rid")->fetch();
$ec = []; foreach (q('SELECT * FROM st_econ') as $r) $ec[$r['category'] . '|' . $r['cc'] . '|' . $r['users_type']] = $r;
ok($er['status'] === 'DONE' && (int)$er['steps_done'] === 4 && (int)$er['steps_total'] === 4, "economics with cohorts in cc '' / NULL / 'UNK' / 'in' + 'IN': run DONE, no duplicate key" . ($er['status'] !== 'DONE' ? ' · ' . $er['error_text'] : ''));
$bad = array_filter(array_keys($ec), fn($k) => !preg_match('/^[^|]*\|([A-Z]{2})?\|(organic|paid)$/', $k));
$blank = array_count_values(array_map(fn($r) => mb_strtolower($r['category']) . '|' . $r['users_type'], array_filter($ec, fn($r) => $r['cc'] === '')));
ok(!$bad && max($blank) === 1 && count($blank) === count(array_unique(array_map(fn($r) => mb_strtolower($r['category']) . '|' . $r['users_type'], $ec))),
    "st_econ: every cc is a 2-letter code or '' (all countries); exactly one '' row per category + users type");
ok((int)($ec['Edge|IN|organic']['installs'] ?? 0) === 6 && (int)($ec['Edge|IN|paid']['installs'] ?? 0) === 3 && (int)($ec['Edge|US|organic']['installs'] ?? 0) === 1
    && (int)($ec['Edge||paid']['installs'] ?? 0) === 5 && (int)($ec['Edge||organic']['installs'] ?? 0) === 8 && count(array_filter(array_keys($ec), fn($k) => str_starts_with($k, 'Edge|'))) === 5
    && abs((float)$ec['Edge||paid']['d1'] - 0.6) < 1e-9 && (int)($ec['*||paid']['installs'] ?? 0) === 225 && (int)($ec['*||organic']['installs'] ?? 0) === 679,
    "unknown-country installs count only in the all-countries row; 'in' + 'IN' merged; 'edge' + 'Edge' one category");
$colCo = (string)q1("SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'st_econ' AND COLUMN_NAME = 'category'");
$dbEq = fn(string $a, string $b): bool => (int)q1("SELECT CONVERT(? USING utf8mb4) COLLATE $colCo = CONVERT(? USING utf8mb4)", [$a, $b]) === 1;
$pairOk = true; $pairTxt = [];
foreach ($catPairs as [$a, $b]) {
    $b = preg_replace('/^[\s\p{Z}]+|[\s\p{Z}]+$/u', '', $b);   // stored trimmed (incl. Unicode spaces)
    $na = (int)($ec["$a|IN|organic"]['installs'] ?? 0); $nb = (int)($ec["$b|IN|organic"]['installs'] ?? 0);
    $same = $dbEq($a, $b);
    $pairOk = $pairOk && ($same ? $na + $nb === 2 && min($na, $nb) === 0 : $na === 1 && $nb === 1);
    $pairTxt[] = "$a/$b " . ($same ? 'merged' : 'apart');
}
$star = (int)($ec["\u{FF0A}|IN|organic"]['installs'] ?? 0);
ok($pairOk && ($dbEq('*', "\u{FF0A}") ? $star === 0 : $star === 1) && !array_filter(array_keys($ec), fn($k) => preg_match('/^[\s\p{Z}]|[\s\p{Z}]\|/u', $k)),
    "categories merged exactly where the DB collation ($colCo) says equal, no PHP fold (" . implode(', ', $pairTxt) . '); Unicode spaces trimmed; no clash with the all-apps row');
$longRows = array_filter($ec, fn($r) => stripos($r['category'], 'longyyy') === 0);
ok(count($longRows) === 2 && (int)($longRows[array_key_first($longRows)]['installs']) === 2 && mb_strlen((string)$longRows[array_key_first($longRows)]['category']) === 60,
    'categories capped at 60 chars; two that become equal (case-insensitive) after the cap are merged, not a duplicate key');
// a failure while saving must leave the previous st_econ (and its evidence) intact
$before = q('SELECT * FROM st_econ ORDER BY category, cc, users_type');
$evBefore = (int)q1('SELECT COUNT(*) FROM st_evidence'); $snBefore = q('SELECT * FROM st_snapshots ORDER BY id');
$pdo->exec("CREATE TRIGGER st_econ_boom BEFORE INSERT ON st_econ FOR EACH ROW BEGIN SET @st_econ_n = COALESCE(@st_econ_n, 0) + 1; IF @st_econ_n >= 3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced failure on insert 3'; END IF; END");
$pdo->exec('SET @st_econ_n = 0');
$rid = st_run_new($pdo, 'economics', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
$pdo->exec('DROP TRIGGER st_econ_boom');
$er = $pdo->query("SELECT * FROM st_runs WHERE id = $rid")->fetch();
ok($er['status'] === 'FAILED' && str_contains($er['error_text'], 'forced failure') && q('SELECT * FROM st_econ ORDER BY category, cc, users_type') === $before && count($before) > 3
    && (int)q1('SELECT COUNT(*) FROM st_evidence') === $evBefore && q('SELECT * FROM st_snapshots ORDER BY id') === $snBefore,
    'failure on the 3rd st_econ insert: run FAILED, previous st_econ rows, evidence and snapshots untouched (one transaction)');
// edge data out again: economics back to the simulated Tools numbers used below
foreach (["DELETE FROM day_install WHERE pkg LIKE 'com.edge.%'", "DELETE FROM day_user WHERE pkg LIKE 'com.edge.%'", "DELETE FROM imps WHERE rid LIKE 'edge-%'",
    "DELETE FROM devices WHERE pkg LIKE 'com.edge.%'", "DELETE FROM st_app_meta WHERE pkg LIKE 'com.edge.%'"] as $sql) $pdo->exec($sql);
$pdo->exec("ALTER TABLE day_install MODIFY cc CHAR(2) NOT NULL DEFAULT ''");
$pdo->exec("ALTER TABLE st_app_meta MODIFY category VARCHAR(60) NOT NULL DEFAULT ''");
$rid = st_run_new($pdo, 'economics', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
$e = st_econ_get($pdo, 'Tools', 'IN', 'organic');
ok(q1('SELECT status FROM st_runs WHERE id = ?', [$rid]) === 'DONE' && $e && (int)$e['installs'] === 660 && !q("SELECT 1 FROM st_econ WHERE category = 'Edge'"), 'edge data removed: economics back to the simulated Tools rows');

// ---------------------------------------------------------------- rank job with canned Claude responses
kv_set('ai_api_key', 'sk-test');
kv_set('st_categories', json_encode(['Tools']));
kv_set('st_countries', json_encode([['cc' => 'IN', 'name' => 'India', 'tier' => 3, 'on' => true], ['cc' => 'US', 'name' => 'United States', 'tier' => 1, 'on' => true]]));
$usage = ['input_tokens' => 3000, 'output_tokens' => 800, 'server_tool_use' => ['web_search_requests' => 2]];
$webResp = fn(array $urls, string $text, string $stop = 'end_turn') => [200, ['model' => 'claude-opus-5-5', 'stop_reason' => $stop, 'usage' => $usage, 'content' => [
    ['type' => 'server_tool_use', 'id' => 'srv1', 'name' => 'web_search', 'input' => ['query' => 'x']],
    ['type' => 'web_search_tool_result', 'tool_use_id' => 'srv1', 'content' => array_map(fn($u) => ['type' => 'web_search_result', 'url' => $u, 'title' => "T $u", 'page_age' => '3 days ago'], $urls)],
    ['type' => 'text', 'text' => $text]]]];
$jsonResp = fn(array $j) => [200, ['model' => 'claude-opus-5-5', 'stop_reason' => 'end_turn', 'usage' => ['input_tokens' => 5000, 'output_tokens' => 3000], 'content' => [['type' => 'text', 'text' => json_encode($j)]]]];
// canned AI factors follow the schema: only st_ai_factors() (no own_fit), -1 = no evidence
$fx = function (array $r) { $o = []; foreach (st_ai_factors() as $k) $o[$k] = $r[$k] ?? ['rating' => -1, 'basis' => 'UNKNOWN', 'evidence' => [], 'note' => '']; return $o; };
$I = fn($v, $ev = ['S1']) => ['rating' => $v, 'basis' => 'INFERENCE', 'evidence' => $ev, 'note' => 'n'];
$card = ['what' => 'w', 'why' => 'y', 'evidence' => 'e', 'benefit' => 'b', 'cost' => 'c', 'risk' => 'r', 'alternatives' => 'a'];
$oppA = ['title' => 'Lost Earbuds Finder', 'concept' => 'BT signal meter', 'category' => 'Tools', 'countries' => ['IN', 'us', 'XX1'], 'competitors' => [['name' => 'Finder X', 'package' => 'com.finder.x', 'evidence' => ['S2']]],
    'monetization' => ['banner'], 'risks' => ['RSSI noisy'], 'why_build' => 'complaints', 'why_avoid' => 'crowded', 'complexity' => 'Low', 'recommendation' => 'STRONGLY CONSIDER', 'decision_card' => $card,
    'factors' => $fx(['demand' => $I(7), 'competition' => $I(5), 'policy_risk' => $I(2), 'differentiation' => $I(7), 'complexity' => $I(3),
        'search' => ['rating' => 6, 'basis' => 'VERIFIED', 'evidence' => ['S2'], 'note' => 'claims verified but is web'],
        'growth' => ['rating' => 8, 'basis' => 'UNKNOWN', 'evidence' => [], 'note' => 'no data'],
        'team_fit' => ['rating' => 9, 'basis' => 'INFERENCE', 'evidence' => ['S99'], 'note' => 'fake id'],
        // owner's-apps P ids (portfolio runs inline first): P1 = totals (VERIFIED), P2 = IN, whose spend is estimated from paid installs (ESTIMATE)
        'monetization' => ['rating' => 6, 'basis' => 'VERIFIED', 'evidence' => ['P1'], 'note' => 'your apps earn'],
        'expansion' => ['rating' => 5, 'basis' => 'ESTIMATE', 'evidence' => ['P2'], 'note' => 'IN estimated'],
        'time_to_market' => ['rating' => 6, 'basis' => 'VERIFIED', 'evidence' => ['P2'], 'note' => 'claims verified but P2 is an estimate']])];
$oppB = ['title' => 'AI Prank Voice Call', 'concept' => 'fake calls', 'category' => 'Prank apps', 'countries' => ['US'], 'competitors' => [], 'monetization' => [], 'risks' => ['impersonation'],
    'why_build' => 'viral', 'why_avoid' => 'policy', 'complexity' => 'Medium', 'recommendation' => 'STRONGLY CONSIDER', 'decision_card' => $card,
    'factors' => $fx(['demand' => $I(9), 'ad_activity' => $I(9), 'policy_risk' => $I(9)])];
@unlink("$Q.log");
$fake([$webResp(['https://example.com/a'], 'part one', 'pause_turn'), $webResp(['https://example.com/b'], 'notes [SOURCE SAYS]'), $jsonResp(['opportunities' => [$oppA, $oppB]])]);
$rid = st_run_new($pdo, 'rank', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
$run = $pdo->query("SELECT * FROM st_runs WHERE id = $rid")->fetch();
ok($run['status'] === 'DONE' && $left() === 0, 'rank run DONE, all canned responses used (pause_turn resumed)' . ($run['status'] !== 'DONE' ? ' · ' . $run['error_text'] : ''));
ok((int)$run['ai_calls'] === 3 && (int)$run['searches'] === 4 && (float)$run['cost_usd'] > 0, 'usage + cost recorded per call (3 calls, 4 searches)');
$log = (string)@file_get_contents("$Q.log");
ok(str_contains($log, 'web_search_20260209') && str_contains($log, 'json_schema') && str_contains($log, 'UNTRUSTED DATA'), 'requests use web search tool, structured output and the untrusted-content rule');
ok(str_contains($log, "=== Owner's own apps, last 30 days (verified) ===\\nP1: all your apps") && preg_match('/\\\\nP\\d+: your apps in IN: 880 installs \\(220 paid\\)[^"]*\\(spend estimated from paid installs\\)/', $log)
    && str_contains($log, "paid ROAS is below 100%") && str_contains($log, 'cite those with basis ESTIMATE') && (bool)kv_get('st_port_meta'),
    "rank prompt: owner's-apps section with P ids (portfolio ran inline first), estimated spend marked, guidance in system prompt (estimated P lines = ESTIMATE)");
// what really went to the API: every structured-output schema in the request log (one JSON body per line)
$sent = [];
foreach (explode("\n", $log) as $line) { $j = json_decode(substr($line, (int)strpos($line, ' ') + 1), true); if (is_array($j['output_config']['format']['schema'] ?? null)) $sent[] = $j; }
$sf = $sent[0]['output_config']['format']['schema']['properties']['opportunities']['items']['properties']['factors']['properties'] ?? [];
ok(count($sent) === 1 && $unions($sent[0]['output_config']['format']['schema']) === 0 && array_keys($sf) === st_ai_factors() && !str_contains(json_encode($sent[0]), 'own_fit')
    && str_contains($sent[0]['system'], 'or -1 if there is no evidence') && str_contains($sent[0]['system'], 'UNKNOWN when rating is -1') && !str_contains($sent[0]['system'], 'null if'),
    'rank request sent: schema has 0 union types, no own_fit anywhere (server computes it), prompt says -1 = no evidence');
$A = $pdo->query("SELECT * FROM st_opps WHERE title = 'Lost Earbuds Finder'")->fetch();
$B = $pdo->query("SELECT * FROM st_opps WHERE title = 'AI Prank Voice Call'")->fetch();
$fa = json_decode($A['factors'], true);
ok($fa['search']['basis'] === 'INFERENCE', 'factor claiming VERIFIED from a web source is downgraded to INFERENCE');
ok($fa['growth']['rating'] === null, 'rating without basis removed (UNKNOWN -> no number)');
ok($fa['team_fit']['evidence'] === [], 'invented evidence id S99 dropped');
ok($fa['rev_per_user']['basis'] === 'VERIFIED' && $fa['retention']['basis'] === 'VERIFIED' && $fa['acq_economics']['basis'] === 'VERIFIED', 'internal analytics override revenue/retention/acquisition factors as VERIFIED');
$pin = (int)q1("SELECT id FROM st_evidence WHERE data_type = 'portfolio' AND cc = 'IN' AND label = 'ESTIMATE'");
ok($fa['monetization']['basis'] === 'VERIFIED' && $fa['monetization']['evidence'] === [(int)json_decode((string)kv_get('st_port_meta'), true)['total_eid']]
    && $fa['expansion']['basis'] === 'ESTIMATE' && $fa['expansion']['evidence'] === [$pin] && $pin > 0 && $fa['time_to_market']['basis'] === 'INFERENCE',
    'AI cites P ids: P1 (totals) kept VERIFIED, estimated-spend country P2 kept as ESTIMATE, VERIFIED claim on P2 downgraded to INFERENCE');
$pAid = array_map('intval', array_column(q("SELECT id FROM st_evidence WHERE data_type = 'portfolio' AND label = 'VERIFIED' AND category = 'Tools'"), 'id'));
ok($fa['own_fit']['basis'] === 'VERIFIED' && $fa['own_fit']['rating'] !== null && array_intersect($fa['own_fit']['evidence'], $pAid) && (json_decode($A['profit'], true)['fit']['rating'] ?? null) === $fa['own_fit']['rating'],
    'own_fit applied from your own Tools apps (VERIFIED, P evidence) + fit stored in profit JSON');
$fb = json_decode($B['factors'], true);
ok($fb['own_fit']['rating'] === null && $fb['own_fit']['basis'] === 'UNKNOWN' && !isset(json_decode($B['profit'], true)['fit']), 'own_fit: no own apps in Prank apps, no matching countries -> nothing invented');
ok(array_keys($fb) === array_keys(ST_FACTORS) && $fb['competition']['rating'] === null && $fb['competition']['basis'] === 'UNKNOWN' && (float)$fb['demand']['rating'] === 9.0,
    'stored factor set complete (all ' . count(ST_FACTORS) . ' incl. own_fit); AI -1 stored as null/UNKNOWN, 9 stays 9');
ok($A['countries'] === 'IN,US', 'countries normalised (invalid dropped)');
ok($B['final_rec'] === 'REJECT' && $B['ai_rec'] === 'STRONGLY CONSIDER', 'AI said STRONGLY CONSIDER, policy rule says REJECT -> final REJECT');
ok($A['organic_score'] !== null && $A['paid_score'] !== null && $A['final_rec'] !== 'STRONGLY CONSIDER', 'scores computed; single-source idea is not STRONGLY CONSIDER');
ok((int)q1("SELECT COUNT(*) FROM st_evidence WHERE source = 'claude_web' AND label = 'INFERENCE' AND url LIKE 'https://example.com/%'") === 2, 'web results stored as INFERENCE evidence with URL');
ok((int)q1("SELECT COUNT(*) FROM st_artifacts WHERE kind = 'ranking' AND project_id IS NULL") === 1, 'ranking artifact v1 saved');
ok((int)q1("SELECT COUNT(*) FROM app_alerts WHERE rule = 'studio_research'") === 1, 'notification: research ready (Gate 1)');
ok((int)$A['rank_organic'] >= 1, 'organic rank assigned');

// re-run with same idea -> upsert (idempotent), not duplicate
$fake([$webResp(['https://example.com/b'], 'notes'), $jsonResp(['opportunities' => [$oppA]])]);
st_run_new($pdo, 'rank', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
ok((int)q1("SELECT COUNT(*) FROM st_opps WHERE title = 'Lost Earbuds Finder'") === 1, 'same idea next run updates the row (no duplicates)');
ok((int)q1("SELECT COUNT(*) FROM app_alerts WHERE rule = 'studio_research'") === 2, 'new ranking run -> one new notification (dedupe per run)');

// ---------------------------------------------------------------- budget guard
kv_set('st_ai_budget_usd', '0.0001');
$fake([$webResp([], 'x')]);
$rid = st_run_new($pdo, 'discover', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
ok(q1('SELECT status FROM st_runs WHERE id = ?', [$rid]) === 'BLOCKED' && $left() === 1, 'budget reached -> run BLOCKED, no API call made');
ok((int)q1("SELECT COUNT(*) FROM app_alerts WHERE rule = 'studio_budget'") === 1, 'budget notification sent once');
kv_set('st_ai_budget_usd', '40');
$fake([]);

// ---------------------------------------------------------------- failure -> retry once -> FAILED
$rid = st_run_new($pdo, 'discover', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
$r = $pdo->query("SELECT * FROM st_runs WHERE id = $rid")->fetch();
ok($r['status'] === 'FAILED' && (int)$r['attempts'] === 2, 'API failure: retried once, then FAILED (never DONE)');

// ---------------------------------------------------------------- portfolio: your own apps, last 30 days (pure SQL, no AI)
// Fresh SIMULATED data so every number is exact (old cohort rows removed). rate = 95 INR/USD (stub cfg).
//   com.sim.cam  Utilities      USD account: campaign c1 has an Ads geo report (US $3, IN $7 = exact), c2 ($6) has none -> split by cam's paid installs (US 40, BD 10)
//   com.sim.vpn  Tools          INR account: Rs 1000, 10-14 days ago (ads off now), no paid installs -> unknown country ('')
//   com.sim.fun  Entertainment  organic only, no spend -> ROAS null
foreach (['imps', 'day_install', 'devices', 'gads_campaign_day', 'events', 'ga_geo_day', 'ga_camp', 'ga_accounts'] as $t) $pdo->exec("DELETE FROM $t");
$ago = fn(int $d, string $t = '10:00:00') => gmdate('Y-m-d', time() - $d * 86400) . " $t";
$D = fn(int $d) => gmdate('Y-m-d', time() - $d * 86400);
$pdo->beginTransaction();
$dids = [];
foreach ([['com.sim.cam', 'US', 100, 40], ['com.sim.cam', 'IN', 200, 0], ['com.sim.cam', 'BD', 60, 10], ['com.sim.vpn', 'IN', 80, 0], ['com.sim.vpn', 'BD', 40, 0],
    ['com.sim.fun', 'US', 20, 0], ['com.sim.fun', 'BD', 100, 0]] as [$pkg, $cc, $nI, $nP]) for ($i = 0; $i < $nI; $i++) {
    $k++; $did = sprintf('p%07d-0000-0000-0000-000000000000', $k); $d = 2 + $i % 18;
    $ins->execute([$D($d), $pkg, $did, $ago($d), $cc]);
    $dev->execute([$pkg, $did, $ago($d), $ago($d), $cc, $i < $nP ? 'gclid-p' : '']);
    $dids[$pkg][$cc][] = $did;
}
foreach ([['com.sim.cam', 'US', 200, 0.05], ['com.sim.cam', 'IN', 400, 0.005], ['com.sim.cam', 'BD', 100, 0.002], ['com.sim.vpn', 'IN', 300, 0.005], ['com.sim.vpn', 'BD', 100, 0.002],
    ['com.sim.fun', 'US', 50, 0.04], ['com.sim.fun', 'BD', 400, 0.001]] as [$pkg, $cc, $nm, $rv]) for ($i = 0; $i < $nm; $i++) {
    $k++; $imp->execute([sprintf('p%010d', $k), $pkg, $dids[$pkg][$cc][$i % count($dids[$pkg][$cc])], $ago(2 + $i % 18, '12:00:00'), $cc, $rv]);
}
$imp->execute(['p-old-0001', 'com.sim.cam', $dids['com.sim.cam']['US'][0], $ago(40), 'US', 9.0]);   // older than 30 days: must NOT count
$evt = $pdo->prepare("INSERT INTO events (pkg, ev, ts, did, p2, val) VALUES (?,?,?,?,?,?)");
$u = $dids['com.sim.cam']['US'];
foreach ([['purchase_done', 3, $u[0], 'GPA.o1', 300], ['purchase_done', 3, $u[0], 'GPA.o1', 300], ['purchase_done', 5, $u[1], 'GPA.o2', 200],   // o1 sent twice: counted once
    ['purchase_done', 40, $u[2], 'GPA.o0', 999], ['purchase_start', 4, $u[3], 'GPA.o3', 700]] as [$ev, $d, $did, $ord, $val]) $evt->execute(['com.sim.cam', $ev, $ago($d), $did, $ord, $val]);
$gc = $pdo->prepare("INSERT INTO gads_campaign_day (customer_id, day, campaign_id, campaign, pkg, currency, cost, updated_at) VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP())");
foreach ([['2223334444', 2, 'c1', 'com.sim.cam', 'USD', 6], ['2223334444', 12, 'c1', 'com.sim.cam', 'USD', 4], ['2223334444', 3, 'c2', 'com.sim.cam', 'USD', 2], ['2223334444', 15, 'c2', 'com.sim.cam', 'USD', 4],
    ['1112223333', 10, 'v1', 'com.sim.vpn', 'INR', 600], ['1112223333', 14, 'v1', 'com.sim.vpn', 'INR', 400]] as [$cid, $d, $camp, $pkg, $cur, $cost]) $gc->execute([$cid, $D($d), $camp, "Camp $camp", $pkg, $cur, $cost]);
h_x($pdo, "INSERT INTO ga_accounts (customer_id, company, currency) VALUES ('2223334444', 'Sim USD', 'USD'), ('1112223333', 'Sim INR', 'INR')");
h_x($pdo, "INSERT INTO ga_camp (customer_id, campaign_id, pkg) VALUES ('2223334444', 'c1', 'com.sim.cam'), ('2223334444', 'c2', 'com.sim.cam'), ('1112223333', 'v1', 'com.sim.vpn')");
$geo = $pdo->prepare("INSERT INTO ga_geo_day (customer_id, day, campaign_id, country, cost) VALUES ('2223334444', ?, 'c1', ?, ?)");
foreach ([[2, 'US', 2], [2, 'IN', 4], [12, 'US', 1], [12, 'IN', 3]] as [$d, $cc, $cost]) $geo->execute([$D($d), $cc, $cost]);
foreach (['com.sim.cam' => 'Utilities', 'com.sim.vpn' => 'Tools', 'com.sim.fun' => 'Entertainment'] as $p => $c) h_x($pdo, "REPLACE INTO st_app_meta (pkg, category, set_by, updated_at) VALUES (?, ?, 'test', UTC_TIMESTAMP())", [$p, $c]);
$pdo->commit();
$rid = st_run_new($pdo, 'portfolio', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
$pr = $pdo->query("SELECT * FROM st_runs WHERE id = $rid")->fetch();
ok($pr['status'] === 'DONE' && (int)$pr['ai_calls'] === 0 && (float)$pr['cost_usd'] === 0.0, 'portfolio run DONE, no AI call (pure SQL)' . ($pr['status'] !== 'DONE' ? ' · ' . $pr['error_text'] : ''));
ok(str_starts_with($pr['summary'], '3 apps · 3 countries · profit -₹472 · ROAS 81%'), 'portfolio summary: apps · countries · profit · ROAS (' . $pr['summary'] . ')');
$V = st_port_view($pdo);
$PT = $V['totals'];
$near = fn($a, $b, float $e = 0.006) => $a !== null && abs((float)$a - (float)$b) < $e;
ok($V['empty'] === false && $near($PT['ad_rev_inr'], 1548.5) && $near($PT['pay_inr'], 500) && $near($PT['spend_inr'], 2520) && $PT['imps'] === 1550 && $PT['installs'] === 600,
    'totals: ad revenue 16.30 USD x 95, payments Rs 500 (duplicate order once, old + non-purchase events ignored), spend Rs 2520 (USD x rate + INR), 1550 imps, 600 installs');
ok($near($PT['earn_inr'], $PT['ad_rev_inr'] + $PT['pay_inr'], 0.001) && $near($PT['profit_inr'], $PT['earn_inr'] - $PT['spend_inr'], 0.001) && $near($PT['profit_inr'], -471.5) && $near($PT['roas'], 0.8129, 0.00006),
    'totals add up: earn = ads + payments, profit = earn - spend, ROAS = earn / spend');
ok($near(array_sum(array_column($V['apps'], 'profit_inr')), $PT['profit_inr'], 0.02) && $near(array_sum(array_column($V['countries'], 'spend_inr')), $PT['spend_inr'], 0.02)
    && $near(array_sum(array_column($V['categories'], 'earn_inr')), $PT['earn_inr'], 0.02), 'apps, countries and categories each add up to the totals');
$AP = array_column($V['apps'], null, 'pkg'); $CC = array_column($V['countries'], null, 'cc'); $CG = array_column($V['categories'], null, 'category');
ok($near($AP['com.sim.cam']['ecpm_usd'], 17.4286, 0.00006) && $near($CC['US']['ecpm_usd'], 48.0, 0.00006) && $near($PT['ecpm_usd'], 10.5161, 0.00006) && $CC['']['ecpm_usd'] === null,
    'eCPM = ad revenue USD / impressions x 1000 (app, country, portfolio); no imps -> null');
ok($AP['com.sim.fun']['roas'] === null && $AP['com.sim.fun']['spend_inr'] == 0 && $near($AP['com.sim.fun']['profit_inr'], 228) && $CG['Entertainment']['roas'] === null, 'no ad spend -> ROAS null (app + category)');
ok($AP['com.sim.cam']['ads_on'] === true && $near($AP['com.sim.cam']['spend7_inr'], 760) && $AP['com.sim.vpn']['ads_on'] === false && $near($AP['com.sim.vpn']['spend_inr'], 1000) && $PT['apps_ads_on'] === 1,
    'ads_on from last-7-days spend (vpn spent 10-14 days ago: off)');
ok(array_column($V['apps'], 'pkg') === ['com.sim.fun', 'com.sim.cam', 'com.sim.vpn'] && $AP['com.sim.cam']['category'] === 'Utilities' && $AP['com.sim.cam']['paid_installs'] === 50, 'apps sorted by profit, category + paid installs');
ok($near($CC['IN']['spend_inr'], 665) && $CC['IN']['spend_est'] === false && $near($CC['US']['spend_inr'], 741) && $CC['US']['spend_est'] === true && $near($CC['BD']['spend_inr'], 114) && $CC['BD']['spend_est'] === true,
    'country spend: IN exact from geo report, US = exact Rs 285 + estimated Rs 456 (paid-install share), BD estimated');
ok(isset($CC['']) && $near($CC['']['spend_inr'], 1000) && $CC['']['spend_est'] === true && $V['spend_by_cc'] === 'mixed', "app with spend but no paid installs -> unknown country bucket (cc ''); spend_by_cc 'mixed'");
ok($near($CC['US']['pay_inr'], 500) && $near($CC['US']['profit_inr'], 899) && $near($CC['IN']['roas'], 0.5) && $near($CC['IN']['profit_inr'], -332.5), 'country profit/loss: payments by device country, IN ROAS 50%');
$lab = fn(?int $id) => (string)q1('SELECT label FROM st_evidence WHERE id = ?', [(int)$id]);
ok($lab($CC['IN']['evidence_id']) === 'VERIFIED' && $lab($CC['US']['evidence_id']) === 'ESTIMATE' && $lab($AP['com.sim.cam']['evidence_id']) === 'VERIFIED' && $lab($CG['Tools']['evidence_id']) === 'VERIFIED'
    && $lab($V['totals']['evidence_id']) === 'VERIFIED', 'evidence: app/category/exact-country rows VERIFIED, estimated-spend country ESTIMATE');
ok(str_contains((string)q1("SELECT method FROM st_evidence WHERE id = ?", [$CC['US']['evidence_id']]), 'ESTIMATED') && (int)q1("SELECT COUNT(*) FROM st_port") === 3 + 4 + 3, 'st_port: 3 app + 4 country + 3 category rows; estimate method explained');
$I = array_column($V['insights'], null, 'type');
ok(($I['focus']['ccs'] ?? null) === ['US'] && $near($I['focus']['ecpm_min_usd'] ?? null, 48.0) && $near($I['focus']['profit_inr'] ?? null, 899), 'insight focus: US (profit, 120 installs, eCPM above average)');
ok(($I['avoid_paid']['ccs'] ?? null) === ['IN'] && $near($I['avoid_paid']['roas_min'] ?? null, 0.5) && $near($I['avoid_paid']['loss_inr'] ?? null, 332.5) && ($I['avoid_paid']['est'] ?? null) === false,
    'insight avoid_paid: IN (Rs 665 spend, ROAS 50%); BD under Rs 500 spend not judged');
ok(($I['app_loss']['pkgs'] ?? null) === ['com.sim.vpn'] && $near($I['app_loss']['loss_inr'] ?? null, 838.5), 'insight app_loss: vpn lost Rs 838.50 on Google Ads');
ok(($I['strong']['category'] ?? '') === 'Utilities' && $near($I['strong']['roas'] ?? null, 1.0914, 0.00006) && ($I['weak']['category'] ?? '') === 'Tools' && $near($I['weak']['profit_inr'] ?? null, -838.5),
    'insights strong = Utilities (best paid ROAS), weak = Tools (loss)');
[$ptxt, $pmap] = st_portfolio_context($pdo);
ok(str_starts_with($ptxt, 'P1: all your apps') && preg_match('/P\d+: your apps in US:[^\n]*\(spend estimated from paid installs\)/', $ptxt) && preg_match('/P\d+: your apps in IN: 280 installs \(0 paid\)[^\n]*ROAS 50%, eCPM \$5\n/', $ptxt)
    && str_contains($ptxt, '- paid acquisition unproven') && count($pmap) === 1 + 3 + 3 + 2 && !array_diff($pmap, array_map('intval', array_column(q("SELECT id FROM st_evidence WHERE data_type = 'portfolio'"), 'id')))
    && preg_match('/P\d+: your app com\.sim\.vpn \(com\.sim\.vpn, Tools\): Google Ads off in the last 7 days/', $ptxt) && preg_match('/- apps losing money on Google Ads: com\.sim\.vpn \(P\d+\)/', $ptxt)
    && preg_match('/unproven[^\n]*: IN \(P\d+\)/', $ptxt),
    'AI context: P ids -> portfolio evidence (totals, 3 countries >= 50 installs, 3 categories, app with ads on + app named in app_loss), estimated spend marked, rules cite P ids');
$base = $fx([]);
$base['own_fit'] = ['rating' => 7, 'basis' => 'INFERENCE', 'evidence' => [], 'note' => 'ai'];
$pf = st_apply_portfolio($pdo, 'utilities', ['us', 'IN', 'BR'], $base);
$of = $pf['factors']['own_fit'];
ok($of['basis'] === 'VERIFIED' && $of['rating'] === 5.5 && $pf['fit']['focus_ccs'] === ['US'] && $pf['fit']['avoid_paid_ccs'] === ['IN'] && $near($pf['fit']['category_roas'], 1.0914, 0.00006)
    && count($of['evidence']) === 3 && str_contains($of['note'], 'organic only'), 'own_fit: Utilities ROAS 109% x5 = 5.5, +1 focus US, -1 paid-unproven IN -> 5.5 VERIFIED with P evidence');
$pf = st_apply_portfolio($pdo, 'Tools', ['BD'], $base);
ok($pf['factors']['own_fit']['rating'] === 0.8 && $pf['factors']['own_fit']['basis'] === 'VERIFIED', 'own_fit: losing category (Tools ROAS 16%) rated low');
$pf = st_apply_portfolio($pdo, 'Prank apps', ['GB'], $base);
ok($pf['fit'] === null && $pf['factors']['own_fit'] === $base['own_fit'], 'own_fit: no data for category and no matching countries -> AI rating left as is');
$pf = st_apply_portfolio($pdo, 'Prank apps', ['US'], $base);
ok($pf['factors']['own_fit']['rating'] === 6.0 && $pf['factors']['own_fit']['basis'] === 'ESTIMATE' && $pf['fit']['basis'] === 'ESTIMATE' && $pf['factors']['own_fit']['evidence'] === [$CC['US']['evidence_id']],
    'own_fit from an estimated-spend country only (no category data) -> ESTIMATE, not VERIFIED');
// insights need a real sample: a 3-impression category is never "strong", a Rs 20 loss is never "weak"
$cg = fn(string $c, float $sp, ?float $ro, float $pr, int $in, int $im, ?float $ec) => ['category' => $c, 'apps' => 1, 'spend_inr' => $sp, 'roas' => $ro, 'profit_inr' => $pr, 'installs' => $in, 'imps' => $im, 'ecpm_usd' => $ec, 'd7' => null];
$i1 = array_column(st_port_insights(['totals' => ['ecpm_usd' => 1.0], 'countries' => [], 'apps' => [], 'categories' => [$cg('Games', 900, 0.5, -450, 500, 50000, 1.0), $cg('Tiny', 0, null, 2, 1, 3, 9.0)]]), null, 'type');
$i2 = array_column(st_port_insights(['totals' => ['ecpm_usd' => 1.0], 'countries' => [], 'apps' => [], 'categories' => [$cg('Games', 0, null, 100, 500, 50000, 1.0), $cg('Small', 20, 0.25, -15, 5, 100, 0.4)]]), null, 'type');
ok(!isset($i1['strong']) && ($i1['weak']['category'] ?? '') === 'Games' && ($i2['strong']['category'] ?? '') === 'Games' && !isset($i2['weak']),
    'insights: strong/weak categories need enough installs/impressions/spend (tiny samples ignored)');

// edge cases: Ads Script-only campaign + campaign whose app comes only from ga_camp, pkg/category spelled in another case, payments-heavy category, tiny category, window start
$fromP = (new DateTime(local_today()))->modify('-' . (ST_PORT_DAYS - 1) . ' day')->format('Y-m-d');
$pdo->beginTransaction();
h_x($pdo, "INSERT INTO ga_accounts (customer_id, company, currency) VALUES ('3334445555', 'Script only', 'INR')");
h_x($pdo, "INSERT INTO ga_camp (customer_id, campaign_id, pkg) VALUES ('3334445555', 's1', 'com.sim.vpn'), ('1112223333', 'v2', 'com.sim.vpn')");
h_x($pdo, "INSERT INTO ga_geo_day (customer_id, day, campaign_id, country, cost) VALUES ('3334445555', ?, 's1', 'GB', 600)", [$D(3)]);   // no gads_campaign_day rows for s1
$gc->execute(['1112223333', $D(4), 'v2', 'Camp v2', '', 'INR', 200]);                // pkg only via ga_camp
$gc->execute(['2223334444', $D(5), 'c3', 'Camp c3', 'COM.SIM.CAM', 'USD', 1]);       // same app, other case
foreach (['com.sim.ring' => 'tools', 'com.sim.tiny' => 'Prank apps'] as $p => $c) h_x($pdo, "REPLACE INTO st_app_meta (pkg, category, set_by, updated_at) VALUES (?, ?, 'test', UTC_TIMESTAMP())", [$p, $c]);
for ($i = 0; $i < 5; $i++) $imp->execute(["w-ring-$i", 'com.sim.ring', 'w-ring', $ago(3), 'IN', 0.01]);
for ($i = 0; $i < 3; $i++) $imp->execute(["w-tiny-$i", 'com.sim.tiny', 'w-tiny', $ago(3), 'US', 1.0]);
$imp->execute(['w-edge', 'com.sim.cam', $dids['com.sim.cam']['US'][0], gmdate('Y-m-d H:i:s', strtotime(day_bounds_utc($fromP)[0] . ' UTC') - 60), 'US', 9.0]);   // 1 min before the window
$evt->execute(['com.sim.fun', 'purchase_done', $ago(3), $dids['com.sim.fun']['BD'][0], 'GPA.f1', 5000]);
$pdo->commit();
$rid = st_run_new($pdo, 'portfolio', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
$pr = $pdo->query("SELECT * FROM st_runs WHERE id = $rid")->fetch();
$V2 = st_port_view($pdo);
$A2 = array_column($V2['apps'], null, 'pkg'); $C2 = array_column($V2['countries'], null, 'cc'); $G2 = array_column($V2['categories'], null, 'category');
ok($pr['status'] === 'DONE' && count($V2['apps']) === 5 && !isset($A2['COM.SIM.CAM']) && $near($A2['com.sim.cam']['spend_inr'] ?? null, 1615) && $near($A2['com.sim.cam']['spend7_inr'] ?? null, 855)
    && ($G2['Tools']['apps'] ?? 0) === 2 && !isset($G2['tools']), "category 'tools' vs 'Tools' and pkg in another case merge (no duplicate key, run DONE)" . ($pr['status'] !== 'DONE' ? ' · ' . $pr['error_text'] : ''));
ok($near($A2['com.sim.vpn']['spend_inr'] ?? null, 1800) && $near($A2['com.sim.vpn']['spend7_inr'] ?? null, 800) && ($A2['com.sim.vpn']['ads_on'] ?? null) === true
    && $near($C2['GB']['spend_inr'] ?? null, 600) && ($C2['GB']['spend_est'] ?? null) === false && $near($C2['']['spend_inr'] ?? null, 1200),
    "geo spend subtracted per campaign: Script-only campaign adds Rs 600 (GB exact), ga_camp-only campaign adds Rs 200, old Rs 1000 still split (unknown country)");
ok($near($V2['totals']['ad_rev_inr'], 1838.25) && $near($V2['totals']['pay_inr'], 5500), 'raw tables use the same window as day tables (impression 1 min before local day 1 not counted)');
$I2 = array_column($V2['insights'], null, 'type');
[$ptxt2] = st_portfolio_context($pdo);
ok(($I2['avoid_paid']['ccs'] ?? null) === ['GB', 'IN'] && preg_match('/P\d+: your apps in GB: 0 installs/', $ptxt2) && preg_match('/unproven[^\n]*: GB \(P\d+\), IN \(P\d+\)/', $ptxt2),
    'country named in avoid_paid gets its own P line even under 50 installs (AI can cite it)');
$pf = st_apply_portfolio($pdo, 'Entertainment', [], $base);
ok($pf['factors']['own_fit']['rating'] === 10.0 && $pf['factors']['own_fit']['basis'] === 'VERIFIED' && str_contains($pf['factors']['own_fit']['note'], 'per install'),
    'own_fit without ad spend counts payments: Entertainment earns Rs 43.57/install vs Rs 12.23 average -> 10 (ad eCPM alone said 2.5)');
$pf = st_apply_portfolio($pdo, 'Prank apps', [], $base);
ok($pf['fit'] === null && $pf['factors']['own_fit'] === $base['own_fit'] && isset($G2['Prank apps']), 'own_fit: category with 3 impressions and no installs is too small to judge -> AI rating left as is');
foreach (["DELETE FROM ga_geo_day WHERE customer_id = '3334445555'", "DELETE FROM ga_accounts WHERE customer_id = '3334445555'", "DELETE FROM ga_camp WHERE campaign_id IN ('s1', 'v2')",
    "DELETE FROM gads_campaign_day WHERE campaign_id IN ('v2', 'c3')", "DELETE FROM imps WHERE rid LIKE 'w-%'", "DELETE FROM events WHERE p2 = 'GPA.f1'",
    "DELETE FROM st_app_meta WHERE pkg IN ('com.sim.ring', 'com.sim.tiny')"] as $sql) $pdo->exec($sql);
st_run_new($pdo, 'portfolio', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
ok(str_starts_with((string)q1("SELECT summary FROM st_runs WHERE job = 'portfolio' ORDER BY id DESC LIMIT 1"), '3 apps · 3 countries · profit -₹472'), 'edge-case rows removed: portfolio back to the base numbers');
// fresh server without dashboard tables: cron queues nothing (no daily FAILED runs)
foreach (['st_daily', 'st_weekly', 'st_monthly'] as $sk) kv_set($sk, '0');
$pdo->exec('RENAME TABLE imps TO zz_imps, events TO zz_events, day_install TO zz_day_install, devices TO zz_devices, gads_campaign_day TO zz_gads_campaign_day');
$nr = (int)q1('SELECT COUNT(*) FROM st_runs');
st_cron($pdo, fn(string $m) => null);
ok(!st_port_ready($pdo) && (int)q1('SELECT COUNT(*) FROM st_runs') === $nr && kv_get('st_port_day') === local_today(), 'no dashboard tables: cron does not queue the portfolio job');
$pdo->exec('RENAME TABLE zz_imps TO imps, zz_events TO events, zz_day_install TO day_install, zz_devices TO devices, zz_gads_campaign_day TO gads_campaign_day');
$pdo->exec("DELETE FROM kv WHERE k = 'st_port_day'");
// cron: once per local day, worker runs it (other schedules still off for this check)
st_cron($pdo, fn(string $m) => null);
st_cron($pdo, fn(string $m) => null);
ok((int)q1("SELECT COUNT(*) FROM st_runs WHERE job = 'portfolio' AND trigger_type = 'scheduled'") === 1 && q1("SELECT status FROM st_runs WHERE job = 'portfolio' AND trigger_type = 'scheduled'") === 'DONE'
    && kv_get('st_port_day') === local_today(), 'cron: portfolio queued once per local day and run by the worker');
$pdo->exec("DELETE FROM kv WHERE k IN ('st_daily', 'st_weekly', 'st_monthly')");
ok(!st_port_stale($pdo), 'fresh portfolio is not stale (rank will not recompute it)');

// ---------------------------------------------------------------- Gate 1
$aId = (int)$A['id']; $bId = (int)$B['id'];
$err = function (callable $f) { try { $f(); return ''; } catch (Throwable $e) { return get_class($e) . ': ' . $e->getMessage(); } };
ok(str_contains($err(fn() => st_opp_decide($pdo, $bId, 'REJECT', '', 'sagar')), 'reason'), 'reject needs a reason');
st_opp_decide($pdo, $bId, 'REJECT', 'policy risk', 'sagar');
ok(q1('SELECT status FROM st_opps WHERE id = ?', [$bId]) === 'REJECTED' && (int)q1("SELECT COUNT(*) FROM st_approvals WHERE opp_id = ? AND decision = 'REJECT' AND user = 'sagar'", [$bId]) === 1, 'reject saved permanently with user + reason');
ok(str_contains($err(fn() => st_gate1($pdo, [$bId], 'sagar', '')), 'cannot be selected'), 'rejected idea cannot be selected');
ok(str_contains($err(fn() => st_gate1($pdo, [1, 2, 3, 4, 5, 6], 'sagar', '')), 'at most 5'), 'max 5 ideas per selection');
$g = st_gate1($pdo, [$aId], 'sagar', 'go');
$pid = (int)$g['projects'][0];
$P = $pdo->query("SELECT * FROM st_projects WHERE id = $pid")->fetch();
ok($P['stage'] === 'COMPETITOR_RESEARCH' && $P['puid'] === sprintf('PRJ-%03d', $pid), 'Gate 1 approve -> project created, competitor research stage');
$ap = $pdo->query("SELECT * FROM st_approvals WHERE project_id = $pid AND gate = 1")->fetch();
ok($ap && $ap['decision'] === 'APPROVE' && $ap['user'] === 'sagar' && strlen($ap['artifact_hash']) === 64 && (int)$ap['artifact_version'] === 1, 'Gate 1 approval tied to project + idea artifact version/hash + user + time');
ok((int)q1("SELECT COUNT(*) FROM st_runs WHERE job = 'competitors' AND ref_id = ? AND status = 'QUEUED'", [$pid]) === 1, 'competitor analysis queued only for the approved project');
ok(str_contains($err(fn() => st_gate_decide($pdo, $pid, 2, 0, '', 'APPROVE', '', 'sagar')), 'not waiting'), 'Gate 2 cannot be approved before the spec exists');

// ---------------------------------------------------------------- competitors -> Gate 2
$feat = fn(string $name, string $prio, bool $ok = true) => ['name' => $name, 'priority' => $prio, 'what' => 'w', 'problem' => 'p', 'why_add' => 'a', 'why_not' => 'n', 'competitor_evidence' => 'c',
    'user_benefit' => 'u', 'business_benefit' => 'b', 'difficulty' => 'Low', 'implementation' => 'i', 'android_apis' => 'BluetoothLeScanner', 'permissions' => ['BLUETOOTH_SCAN'], 'privacy' => 'none',
    'battery_performance' => 'scan only while open', 'monetization_impact' => 'banner', 'effort_days' => 2, 'risks_alternatives' => 'r', 'feasible_on_android' => $ok, 'evidence' => ['S1']];
$comp = fn(string $n, string $pkg) => ['name' => $n, 'package' => $pkg, 'developer' => 'D', 'play_url' => "https://play.google.com/store/apps/details?id=$pkg", 'why_chosen' => 'top', 'countries' => 'IN, US',
    'installs_indicator' => '1M+ (listing)', 'rating' => '4.1', 'rating_count' => '20K', 'category' => 'Tools', 'update_frequency' => 'monthly', 'core_features' => ['scan'], 'secondary_features' => [],
    'navigation_ui' => 'tabs', 'monetization' => 'ads', 'ads_experience' => 'interstitial on every scan', 'premium' => 'no ads $2', 'technical_limits' => 'RSSI', 'satisfaction' => 'mixed', 'weak_points' => ['ads'],
    'complaints' => [['issue' => 'ads before every scan', 'class' => 'USER-REPORTED ISSUE', 'frequency' => 'frequent', 'evidence' => ['S1']]], 'evidence' => ['S1']];
$specJ = fn(array $features) => ['competitors' => [$comp('Finder X', 'com.finder.x'), $comp('Finder Y', 'com.finder.y'), $comp('Finder Z', 'com.finder.z'), $comp('Extra', 'com.extra.w')],
    'comparison' => array_fill_keys(['all_do_well', 'users_need', 'repeated_complaints', 'too_complex', 'missing', 'simplify', 'avoid_technical', 'differentiators'], ['x']),
    'strategy' => 's', 'features' => $features, 'avoid' => ['GPS'], 'permissions_summary' => ['BLUETOOTH_SCAN'], 'technical_limits' => ['RSSI'], 'monetization_options' => ['banner'], 'effort_summary' => '2 weeks',
    'risks' => ['crowded'], 'decision_card' => array_fill_keys(['what', 'why', 'evidence', 'benefit', 'cost', 'risk', 'alternatives', 'confidence', 'recommendation'], 'x')];
$fake([$webResp(['https://example.com/reviews'], 'review notes'), $jsonResp($specJ([$feat('Signal meter', 'MUST HAVE'), $feat('Widget', 'MUST HAVE'), $feat('GPS track', 'MUST HAVE', false)]))]);
st_work($pdo, 60);
$P = $pdo->query("SELECT * FROM st_projects WHERE id = $pid")->fetch();
$a1 = st_artifact_latest($pdo, $pid, 'features');
$c1 = json_decode($a1['content'], true);
ok($P['stage'] === 'FEATURES_PENDING' && (int)$P['next_gate'] === 2 && (int)$a1['version'] === 1, 'competitor analysis done -> spec v1, waiting for Gate 2');
ok(count($c1['competitors']) === 3, 'exactly top 3 competitors kept');
ok($c1['features'][2]['priority'] === 'REJECT', 'impossible feature auto-marked REJECT');
ok((int)q1("SELECT COUNT(*) FROM app_alerts WHERE rule = 'studio_gate'") === 1, 'notification: Gate 2 decision needed');
ok(str_contains($err(fn() => st_gate_decide($pdo, $pid, 2, (int)$a1['id'], str_repeat('0', 64), 'APPROVE', '', 'sagar')), 'changed'), 'wrong artifact hash -> conflict (must re-review)');
ok(str_contains($err(fn() => st_gate_decide($pdo, $pid, 2, (int)$a1['id'], $a1['hash'], 'REQUEST_CHANGES', ' ', 'sagar')), 'what should change'), 'request changes needs feedback');
ok(str_contains($err(fn() => st_gate_decide($pdo, $pid, 3, (int)$a1['id'], $a1['hash'], 'APPROVE', '', 'sagar')), 'not available'), 'Gate 3 not available in Release 1');
st_gate_decide($pdo, $pid, 2, (int)$a1['id'], $a1['hash'], 'REQUEST_CHANGES', 'Widget optional, add sound', 'sagar');
ok((int)q1("SELECT COUNT(*) FROM st_runs WHERE job = 'revise_features' AND ref_id = ? AND status = 'QUEUED'", [$pid]) === 1, 'request changes -> revision job queued, feedback saved');
$fake([$jsonResp($specJ([$feat('Signal meter', 'MUST HAVE'), $feat('Widget', 'OPTIONAL'), $feat('Play sound', 'SHOULD HAVE'), $feat('GPS track', 'MUST HAVE', false)]))]);
st_work($pdo, 60);
$a2 = st_artifact_latest($pdo, $pid, 'features');
$c2 = json_decode($a2['content'], true);
ok((int)$a2['version'] === 2 && in_array('Widget: MUST HAVE → OPTIONAL', $c2['changes'], true) && in_array('Added: Play sound (SHOULD HAVE)', $c2['changes'], true), 'spec v2 with "what changed" list');
ok((int)q1('SELECT version FROM st_artifacts WHERE id = ?', [(int)$a1['id']]) === 1, 'v1 kept unchanged (version history)');
ok(str_contains($err(fn() => st_gate_decide($pdo, $pid, 2, (int)$a1['id'], $a1['hash'], 'APPROVE', '', 'sagar')), 'changed'), 'approving old v1 after v2 exists -> conflict');
$r = st_gate_decide($pdo, $pid, 2, (int)$a2['id'], $a2['hash'], 'APPROVE', '', 'sagar');
$P = $pdo->query("SELECT * FROM st_projects WHERE id = $pid")->fetch();
ok($r['version'] === 2 && $P['stage'] === 'FEATURES_APPROVED' && $P['next_gate'] === null, 'Gate 2 approve v2 -> FEATURES_APPROVED');
ok((int)q1("SELECT COUNT(*) FROM st_approvals WHERE project_id = ? AND gate = 2 AND decision = 'APPROVE' AND artifact_version = 2", [$pid]) === 1, 'approval recorded for exact version 2');
ok(str_contains($err(fn() => st_gate_decide($pdo, $pid, 2, (int)$a2['id'], $a2['hash'], 'APPROVE', '', 'sagar')), 'not waiting'), 'cannot approve the same gate twice');

// ---------------------------------------------------------------- cancel + crash recovery
$rid = st_run_new($pdo, 'discover', ['trigger' => 'manual', 'by' => 'test']);
ok(st_cancel($pdo, $rid, 'sagar') && q1('SELECT status FROM st_runs WHERE id = ?', [$rid]) === 'CANCELLED', 'queued run cancelled');
$rid = st_run_new($pdo, 'economics', ['trigger' => 'manual', 'by' => 'test']);
h_x($pdo, "UPDATE st_runs SET status = 'RUNNING', attempts = 1, started_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 40 MINUTE) WHERE id = ?", [$rid]);
st_work($pdo, 60);
ok(q1('SELECT status FROM st_runs WHERE id = ?', [$rid]) === 'DONE' && (int)q1('SELECT attempts FROM st_runs WHERE id = ?', [$rid]) === 2, 'interrupted run (server restart) requeued and completed');
ok((int)q1("SELECT COUNT(*) FROM st_events WHERE kind = 'decision' AND actor = 'sagar'") >= 4, 'decisions in searchable activity history');

// ---------------------------------------------------------------- HTTP: every view + actions
$port = 18000 + getmypid() % 1000 + 7;
$env = 'STUDIO_FAKE_Q=' . escapeshellarg($Q) . ' TEST_DB_HOST=' . escapeshellarg((string)getenv('TEST_DB_HOST')) . ' TEST_DB_NAME=' . escapeshellarg((string)getenv('TEST_DB_NAME'))
    . ' TEST_DB_USER=' . escapeshellarg((string)getenv('TEST_DB_USER')) . ' TEST_DB_PASS=' . escapeshellarg((string)getenv('TEST_DB_PASS'));
$srv = proc_open("$env exec php -S 127.0.0.1:$port -t " . escapeshellarg($M), [1 => ['file', "$T/server.log", 'a'], 2 => ['file', "$T/server.log", 'a']], $pipes);
usleep(800000);
$http = function (string $method, string $path, $body = null) use ($port) {
    $ch = curl_init("http://127.0.0.1:$port/$path");
    curl_setopt_array($ch, [CURLOPT_RETURNTRANSFER => true, CURLOPT_CUSTOMREQUEST => $method, CURLOPT_TIMEOUT => 60, CURLOPT_HTTPHEADER => ['Content-Type: application/json']]);
    if ($body !== null) curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($body));
    $r = curl_exec($ch); $c = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
    return [$c, json_decode((string)$r, true), (string)$r];
};
foreach (['overview', 'research&engine=organic', 'research&engine=paid&cc=US', 'research&engine=combined&tier=1', "opp&id=$aId", "compare&ids=$aId,$bId", 'evidence&ids=1,2', 'projects', "project&id=$pid",
    'approvals', 'history&q=Gate', 'runs', 'run&id=1', 'sources', 'settings', 'mine'] as $v) {
    [$c, $j, $raw] = $http('GET', "api/studio.php?view=$v");
    ok($c === 200 && ($j['ok'] ?? false), "view $v" . ($c !== 200 ? ' -> ' . substr($raw, 0, 300) : ''));
}
[$c, $j] = $http('GET', 'api/studio.php?view=mine');
$jf = array_column($j['apps'] ?? [], null, 'pkg');
ok(($j['empty'] ?? true) === false && count($j['apps'] ?? []) === 3 && ($j['spend_by_cc'] ?? '') === 'mixed' && abs(($j['totals']['profit_inr'] ?? 0) + 471.5) < 0.006 && ($j['running'] ?? null) === false
    && array_key_exists('roas', $jf['com.sim.fun'] ?? []) && $jf['com.sim.fun']['roas'] === null && ($jf['com.sim.cam']['ads_on'] ?? null) === true && count($j['insights'] ?? []) === 5
    && ($j['days'] ?? 0) === 30 && ($j['rate'] ?? 0) == 95 && count($j['countries'] ?? []) === 4 && is_array($j['last_run'] ?? null) && isset($j['last_run']['status']),
    'view=mine over HTTP: totals, apps, countries, insights, not running, last portfolio run');
[$c, $j] = $http('GET', 'api/studio.php?view=overview');
ok(($j['mine']['best']['cc'] ?? '') === 'US' && ($j['mine']['loss']['cc'] ?? '') === 'IN' && ($j['mine']['apps_ads_on'] ?? 0) === 1 && abs(($j['mine']['roas'] ?? 0) - 0.8129) < 0.0001
    && is_bool($j['mine']['best']['spend_est'] ?? null) && is_bool($j['mine']['loss']['spend_est'] ?? null) && ($j['schedule']['weekly_dow'] ?? 0) === 1,
    'overview.mine: profit country US, biggest loss IN (with spend_est), 1 app with ads on, ROAS; schedule.weekly_dow');
[$c, $j] = $http('GET', "api/studio.php?view=opp&id=$aId");
ok(is_array($j['item']['fit'] ?? null) && isset($j['item']['fit']['rating']), 'opp row exposes fit (own_fit summary) at top level');
[$c, $j] = $http('GET', "api/studio.php?view=project&id=$pid");
ok(($j['item']['gates'][1]['state'] ?? '') === 'APPROVED' && ($j['item']['gates'][2]['state'] ?? '') === 'LOCKED' && ($j['item']['features']['version'] ?? 0) === 2, 'project view: gate timeline + approved spec v2');
[$c, $j] = $http('GET', "api/studio.php?view=opp&id=$aId");
ok(count($j['item']['factors'] ?? []) === count(ST_FACTORS) && count(ST_FACTORS) === 17 && count($j['evidence'] ?? []) >= 2, 'idea view: 17 factors (incl. own_fit) with weights + evidence list');
[$c, $j] = $http('GET', 'api/studio.php?view=sources');
$ids = array_column($j['items'] ?? [], 'status', 'id');
ok(($ids['internal'] ?? '') === 'OK' && ($ids['appfigures'] ?? '') === 'NOT_CONFIGURED' && ($ids['play_scrape'] ?? '') === 'NOT_CONFIGURED', 'sources: internal OK, paid providers NOT_CONFIGURED, no scraping');
foreach ([['setting', ['key' => 'st_ai_budget_usd', 'value' => '25']], ['countries', ['list' => [['cc' => 'IN', 'name' => 'India', 'tier' => 3, 'on' => true], ['cc' => 'US', 'tier' => 1, 'on' => false]]]],
    ['categories', ['list' => ['Tools', 'Prank apps']]], ['weights', ['weights' => ['demand' => 20]]], ['app_category', ['pkg' => 'com.sim.torch', 'category' => 'Tools']],
    ['idea', ['title' => 'Battery health checker', 'concept' => 'shows battery wear', 'category' => 'Tools', 'countries' => ['IN']]], ['opp', ['id' => $bId, 'decision' => 'RESTORE', 'note' => '']],
    ['run', ['job' => 'economics']], ['run', ['job' => 'portfolio']]] as [$act, $body]) {
    [$c, $j, $raw] = $http('POST', "api/studio.php?action=$act", $body);
    ok($c === 200 && ($j['ok'] ?? false), "action $act" . ($c !== 200 ? ' -> ' . substr($raw, 0, 300) : ''));
}
[$c] = $http('POST', 'api/studio.php?action=run', ['job' => 'economics']);
ok($c === 409, 'same job twice while queued -> 409');
[$c] = $http('POST', 'api/studio.php?action=setting', ['key' => 'st_ai_budget_usd', 'value' => 'abc']);
ok($c === 400, 'bad setting value refused');
[$c] = $http('POST', 'api/studio.php?action=gate', ['project_id' => $pid, 'gate' => 2, 'artifact_id' => (int)$a1['id'], 'hash' => $a1['hash'], 'decision' => 'APPROVE']);
ok($c === 409, 'HTTP gate approval with stale artifact -> 409');
ok(st_on_countries() === ['IN'], 'country selection saved (US switched off)');
ok((int)q1("SELECT COUNT(*) FROM st_runs WHERE job = 'evaluate_idea' AND status = 'QUEUED'") === 1, 'own idea queued for critical evaluation');
[$c, $j] = $http('GET', 'api/studio.php?view=mine');
ok(($j['running'] ?? null) === true, 'view=mine: queued portfolio run shows running');
// own idea evaluation (st_reassess, also used by more research / monthly deep) gets the same owner's-apps section and accepts P ids
@unlink("$Q.log");
$oppE = ['title' => 'Battery health checker', 'concept' => 'shows battery wear', 'category' => 'Tools', 'countries' => ['IN'], 'competitors' => [], 'monetization' => ['banner'], 'risks' => [],
    'why_build' => 'b', 'why_avoid' => 'a', 'complexity' => 'Low', 'recommendation' => 'TEST FIRST', 'decision_card' => $card,
    'factors' => $fx(['demand' => ['rating' => 6, 'basis' => 'INFERENCE', 'evidence' => ['S1'], 'note' => 'n'], 'monetization' => ['rating' => 5, 'basis' => 'VERIFIED', 'evidence' => ['P1'], 'note' => 'your apps']])];
$fake([$webResp(['https://example.com/e'], 'eval notes'), $jsonResp(['opportunities' => [$oppE]])]);
st_work($pdo, 60);
$er = $pdo->query("SELECT * FROM st_runs WHERE job = 'evaluate_idea' ORDER BY id DESC LIMIT 1")->fetch();
$E = $pdo->query("SELECT * FROM st_opps WHERE title = 'Battery health checker' AND factors IS NOT NULL ORDER BY id DESC LIMIT 1")->fetch();
$fe = $E ? json_decode($E['factors'], true) : [];
ok($er['status'] === 'DONE' && $left() === 0 && str_contains((string)@file_get_contents("$Q.log"), "=== Owner's own apps, last 30 days (verified) ===\\nP1: all your apps")
    && ($fe['monetization']['basis'] ?? '') === 'VERIFIED' && ($fe['monetization']['evidence'] ?? []) === [(int)json_decode((string)kv_get('st_port_meta'), true)['total_eid']] && ($fe['own_fit']['rating'] ?? null) !== null,
    "own idea evaluation: prompt has the owner's-apps section, P1 cited as VERIFIED is kept, own_fit applied" . ($er['status'] !== 'DONE' ? ' · ' . $er['error_text'] : ''));
$sent = [];
foreach (explode("\n", (string)@file_get_contents("$Q.log")) as $line) { $j = json_decode(substr($line, (int)strpos($line, ' ') + 1), true); if (is_array($j['output_config']['format']['schema'] ?? null)) $sent[] = $j; }
ok(count($sent) === 1 && $unions($sent[0]['output_config']['format']['schema']) === 0 && !str_contains(json_encode($sent[0]), 'own_fit') && str_contains($sent[0]['system'], '-1 if no evidence (basis UNKNOWN)'),
    'idea re-assess request sent: same schema, 0 union types, no own_fit, -1 = no evidence');
$pdo->exec('DROP TABLE st_port');
kv_set('st_schema_v', '1');
[$c] = $http('GET', 'api/studio.php?view=mine');
ok($c === 200 && (bool)$pdo->query("SHOW TABLES LIKE 'st_port'")->fetch() && kv_get('st_schema_v') === '2', 'schema upgrade: server already at v1 -> v2 creates st_port');
[$c, $j, $raw] = $http('GET', 'api/studio.php?open=project/1');
ok($c === 200 && str_contains($raw, 'intent://studio/project/1'), 'email deep link page opens the app');
proc_terminate($srv);
$log = (string)@file_get_contents("$T/server.log");
ok(!preg_match('/PHP (Warning|Fatal|Deprecated|Notice)/', $log), 'no PHP warnings / errors in server log' . (preg_match('/PHP (Warning|Fatal|Deprecated|Notice)[^\n]*/', $log, $mm) ? ': ' . $mm[0] : ''));

echo "\n$n checks, $fail failed\n";
exit($fail > 0 ? 1 : 0);
