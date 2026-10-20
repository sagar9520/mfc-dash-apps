<?php
/**
 * AI App Research Studio — tests (DEVELOPMENT ENVIRONMENT, SIMULATED DATA ONLY; Claude API responses are canned fakes, nothing calls the network).
 * Part 1 (no DB): scoring formula, recommendation rule, spec diff, spec cleaning.
 * Part 2 (MySQL, TEST_DB_HOST): economics from simulated dashboard tables, rank job (evidence validation, label downgrade, policy REJECT),
 *   budget block, Gate 1 limits, competitor analysis -> Gate 2, artifact hash conflicts, request changes -> v2 + diff, approve, retries/crash recovery/cancel,
 *   notifications + dedupe, every app view over HTTP.
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
    @file_put_contents($q . '.log', $url . ' ' . substr((string)$body, 0, 4000) . "\n", FILE_APPEND);
    if (!str_contains($url, 'api.anthropic.com') || !$list) return [0, '', 'network disabled in tests'];
    $next = array_shift($list);
    file_put_contents($q, json_encode($list));
    return [(int)$next[0], is_string($next[1]) ? $next[1] : json_encode($next[1]), ''];
}
PHP, $stub, 1);
file_put_contents("$M/lib/common.php", $stub);
foreach (['lib/health.php', 'lib/studio.php', 'api/studio.php'] as $f) copy("$src/$f", "$M/$f");
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
ok(st_pctl([1, 2, 3, 4], 50) === 2.5 && st_pctl([], 50) === null, 'percentile helper');

if (!getenv('TEST_DB_HOST')) { echo "\nTEST_DB_HOST not set: DB part skipped\n$n checks, $fail failed\n"; exit($fail > 0 ? 1 : 0); }

// ================================================================ Part 2: MySQL + simulated data
function q(string $sql, array $p = []): array { return h_q(db(), $sql, $p); }
function q1(string $sql, array $p = []) { return h_q1(db(), $sql, $p); }
$fake = function (array $responses) use ($Q) { file_put_contents($Q, json_encode($responses)); };
$left = function () use ($Q) { return count((array)json_decode((string)@file_get_contents($Q), true)); };
$pdo = db();
foreach (['kv', 'app_alerts', 'health_events', 'health_emails', 'day_install', 'day_user', 'imps', 'devices', 'gads_campaign_day', 'st_runs', 'st_events', 'st_evidence', 'st_snapshots', 'st_econ',
    'st_app_meta', 'st_opps', 'st_projects', 'st_artifacts', 'st_approvals', 'st_usage', 'st_sources', 'st_notified'] as $t) $pdo->exec("DROP TABLE IF EXISTS $t");
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
kv_set('h_schema_v', '1');
st_schema($pdo);
ok((int)q1("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'st\\_%'") === 12, 'schema: 12 studio tables created');

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
$fx = function (array $r) { $o = []; foreach (ST_FACTORS as $k => $_) $o[$k] = $r[$k] ?? ['rating' => null, 'basis' => 'UNKNOWN', 'evidence' => [], 'note' => '']; return $o; };
$I = fn($v, $ev = ['S1']) => ['rating' => $v, 'basis' => 'INFERENCE', 'evidence' => $ev, 'note' => 'n'];
$card = ['what' => 'w', 'why' => 'y', 'evidence' => 'e', 'benefit' => 'b', 'cost' => 'c', 'risk' => 'r', 'alternatives' => 'a'];
$oppA = ['title' => 'Lost Earbuds Finder', 'concept' => 'BT signal meter', 'category' => 'Tools', 'countries' => ['IN', 'us', 'XX1'], 'competitors' => [['name' => 'Finder X', 'package' => 'com.finder.x', 'evidence' => ['S2']]],
    'monetization' => ['banner'], 'risks' => ['RSSI noisy'], 'why_build' => 'complaints', 'why_avoid' => 'crowded', 'complexity' => 'Low', 'recommendation' => 'STRONGLY CONSIDER', 'decision_card' => $card,
    'factors' => $fx(['demand' => $I(7), 'competition' => $I(5), 'policy_risk' => $I(2), 'differentiation' => $I(7), 'complexity' => $I(3),
        'search' => ['rating' => 6, 'basis' => 'VERIFIED', 'evidence' => ['S2'], 'note' => 'claims verified but is web'],
        'growth' => ['rating' => 8, 'basis' => 'UNKNOWN', 'evidence' => [], 'note' => 'no data'],
        'team_fit' => ['rating' => 9, 'basis' => 'INFERENCE', 'evidence' => ['S99'], 'note' => 'fake id']])];
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
$A = $pdo->query("SELECT * FROM st_opps WHERE title = 'Lost Earbuds Finder'")->fetch();
$B = $pdo->query("SELECT * FROM st_opps WHERE title = 'AI Prank Voice Call'")->fetch();
$fa = json_decode($A['factors'], true);
ok($fa['search']['basis'] === 'INFERENCE', 'factor claiming VERIFIED from a web source is downgraded to INFERENCE');
ok($fa['growth']['rating'] === null, 'rating without basis removed (UNKNOWN -> no number)');
ok($fa['team_fit']['evidence'] === [], 'invented evidence id S99 dropped');
ok($fa['rev_per_user']['basis'] === 'VERIFIED' && $fa['retention']['basis'] === 'VERIFIED' && $fa['acq_economics']['basis'] === 'VERIFIED', 'internal analytics override revenue/retention/acquisition factors as VERIFIED');
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
    'approvals', 'history&q=Gate', 'runs', 'run&id=1', 'sources', 'settings'] as $v) {
    [$c, $j, $raw] = $http('GET', "api/studio.php?view=$v");
    ok($c === 200 && ($j['ok'] ?? false), "view $v" . ($c !== 200 ? ' -> ' . substr($raw, 0, 300) : ''));
}
[$c, $j] = $http('GET', "api/studio.php?view=project&id=$pid");
ok(($j['item']['gates'][1]['state'] ?? '') === 'APPROVED' && ($j['item']['gates'][2]['state'] ?? '') === 'LOCKED' && ($j['item']['features']['version'] ?? 0) === 2, 'project view: gate timeline + approved spec v2');
[$c, $j] = $http('GET', "api/studio.php?view=opp&id=$aId");
ok(count($j['item']['factors'] ?? []) === 16 && count($j['evidence'] ?? []) >= 2, 'idea view: 16 factors with weights + evidence list');
[$c, $j] = $http('GET', 'api/studio.php?view=sources');
$ids = array_column($j['items'] ?? [], 'status', 'id');
ok(($ids['internal'] ?? '') === 'OK' && ($ids['appfigures'] ?? '') === 'NOT_CONFIGURED' && ($ids['play_scrape'] ?? '') === 'NOT_CONFIGURED', 'sources: internal OK, paid providers NOT_CONFIGURED, no scraping');
foreach ([['setting', ['key' => 'st_ai_budget_usd', 'value' => '25']], ['countries', ['list' => [['cc' => 'IN', 'name' => 'India', 'tier' => 3, 'on' => true], ['cc' => 'US', 'tier' => 1, 'on' => false]]]],
    ['categories', ['list' => ['Tools', 'Prank apps']]], ['weights', ['weights' => ['demand' => 20]]], ['app_category', ['pkg' => 'com.sim.torch', 'category' => 'Tools']],
    ['idea', ['title' => 'Battery health checker', 'concept' => 'shows battery wear', 'category' => 'Tools', 'countries' => ['IN']]], ['opp', ['id' => $bId, 'decision' => 'RESTORE', 'note' => '']],
    ['run', ['job' => 'economics']]] as [$act, $body]) {
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
[$c, $j, $raw] = $http('GET', 'api/studio.php?open=project/1');
ok($c === 200 && str_contains($raw, 'intent://studio/project/1'), 'email deep link page opens the app');
proc_terminate($srv);
$log = (string)@file_get_contents("$T/server.log");
ok(!preg_match('/PHP (Warning|Fatal|Deprecated|Notice)/', $log), 'no PHP warnings / errors in server log' . (preg_match('/PHP (Warning|Fatal|Deprecated|Notice)[^\n]*/', $log, $mm) ? ': ' . $mm[0] : ''));

echo "\n$n checks, $fail failed\n";
exit($fail > 0 ? 1 : 0);
