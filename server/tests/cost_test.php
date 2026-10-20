<?php
/**
 * AI Cost & Usage Manager — test against a real MySQL (DEVELOPMENT ENVIRONMENT, SIMULATED DATA ONLY).
 * CI: .github/workflows/server-tests.yml. Local: TEST_DB_HOST=127.0.0.1 TEST_DB_NAME=mfc_test TEST_DB_USER=root TEST_DB_PASS=... php server/tests/cost_test.php
 *
 * Verifies: token pricing, logging per module / app / task, budget gate (module, total, reserve, critical), daily caps, fix cap,
 * circuit breaker (no infinite retries), manual override, 70/90/100% warnings once each, summary + forecast, settings validation.
 */
declare(strict_types=1);
if (!getenv('TEST_DB_HOST')) { echo "TEST_DB_HOST not set: skipping cost test\n"; exit(0); }

$src = dirname(__DIR__);
$T = sys_get_temp_dir() . '/mfc_cost_test_' . getmypid();
$M = "$T/mfc_dash";
foreach (['lib', 'api'] as $d) @mkdir("$M/$d", 0777, true);
copy("$src/tests/stub_common.php", "$M/lib/common.php");
foreach (['lib/cost.php', 'api/cost.php'] as $f) copy("$src/$f", "$M/$f");
require "$M/lib/cost.php";

$fail = 0; $n = 0;
function ok(bool $c, string $m): void { global $fail, $n; $n++; echo ($c ? 'ok   ' : 'FAIL ') . $m . "\n"; if (!$c) $fail++; }
function near(float $a, float $b, float $eps = 1e-6): bool { return abs($a - $b) < $eps; }

$pdo = db();
foreach (['kv', 'app_alerts', 'cost_events', 'cost_billed', 'cost_paused'] as $t) $pdo->exec("DROP TABLE IF EXISTS $t");
$pdo->exec("CREATE TABLE kv (k VARCHAR(64) NOT NULL PRIMARY KEY, v TEXT NOT NULL, updated_at DATETIME NOT NULL)");
$pdo->exec("CREATE TABLE app_alerts (id INT AUTO_INCREMENT PRIMARY KEY, akey VARCHAR(190) NOT NULL UNIQUE, level TINYINT NOT NULL, rule VARCHAR(40) NOT NULL, pkg VARCHAR(100) NOT NULL DEFAULT '',
  account VARCHAR(40) NOT NULL DEFAULT '', campaign VARCHAR(255) NOT NULL DEFAULT '', title VARCHAR(255) NOT NULL, body TEXT NOT NULL, title_hi VARCHAR(255) NOT NULL DEFAULT '', body_hi TEXT NOT NULL,
  ai TEXT NOT NULL, metrics TEXT NOT NULL, fired INT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, resolved_at DATETIME NULL, read_at DATETIME NULL)");
cost_schema($pdo);

// ---- pricing
ok(near(cost_claude_usd('claude-opus-5-5', 1_000_000, 0), 4.0), 'Opus 5.5: 1M input = $4');
ok(near(cost_claude_usd('claude-opus-5-5', 0, 1_000_000), 20.0), 'Opus 5.5: 1M output = $20');
ok(near(cost_claude_usd('claude-opus-5-5', 0, 0, 1_000_000, 1_000_000), 5.2), 'Opus 5.5: cache read $0.20 + cache write $5');
ok(near(cost_claude_usd('claude-haiku-5-5', 1_000_000, 1_000_000), 0.6), 'Haiku 5.5: $0.10 + $0.50');
ok(near(cost_claude_usd('claude-opus-5-5-unknown-suffix', 1_000_000, 0), 4.0), 'unknown suffix falls back by prefix');

// ---- settings
$s = cost_save_settings(['total' => 10, 'reserve_pct' => 10, 'modules' => ['analytics' => ['budget' => 2, 'cap' => 3], 'health' => ['budget' => 5, 'cap' => 100, 'fix_cap' => 2],
    'gads' => ['budget' => 1, 'cap' => 50], 'research' => ['budget' => 2, 'cap' => 50]], 'fixed' => [['name' => 'Hosting', 'usd' => 0]], 'actions_usd_min' => 0]);
ok($s['total'] === 10.0 && $s['modules']['analytics']['cap'] === 3, 'settings saved');
$bad = cost_save_settings(['total' => -50, 'reserve_pct' => 99]);
ok($bad['total'] === 0.0 && $bad['reserve_pct'] === 50, 'settings clamp negative budget and reserve > 50%');
cost_save_settings(['total' => 10, 'reserve_pct' => 10]);

// ---- logging via cost_claude with a simulated Messages API response
cost_ctx(['module' => 'analytics', 'pkg' => 'com.test.app', 'task' => 'alert_explain', 'ref' => 'AL-1', 'critical' => false]);
ok(cost_gate(), 'gate open under budget');
cost_claude(['model' => 'claude-opus-5-5', 'usage' => ['input_tokens' => 3000, 'output_tokens' => 600]], true);
$row = $pdo->query("SELECT * FROM cost_events ORDER BY id DESC LIMIT 1")->fetch();
ok($row['module'] === 'analytics' && $row['pkg'] === 'com.test.app' && $row['task'] === 'alert_explain' && $row['ref'] === 'AL-1', 'event tagged with module / app / task');
ok(near((float)$row['usd'], 0.024), 'alert explain 3k in + 600 out = $0.024 (estimate)');
cost_claude(['model' => 'claude-opus-5-5', 'usage' => ['input_tokens' => 1000, 'output_tokens' => 0, 'server_tool_use' => ['web_search_requests' => 5]]], true);
$row = $pdo->query("SELECT * FROM cost_events ORDER BY id DESC LIMIT 1")->fetch();
ok(near((float)$row['usd'], 0.004 + 0.05), 'web search billed $10 / 1000');

// ---- daily cap (analytics cap 3: 2 calls logged, 3rd allowed, 4th blocked)
ok(cost_gate(), 'call 3 of cap 3 allowed');
cost_claude(['model' => 'claude-opus-5-5', 'usage' => ['input_tokens' => 10, 'output_tokens' => 10]], true);
ok(!cost_gate(), 'daily cap reached -> non-critical blocked');
ok(str_contains((string)cost_last_block(), 'daily limit'), 'block reason says daily limit');
ok(cost_gate('analytics', true), 'critical (urgent alert) still allowed after cap');
ok((int)$pdo->query("SELECT COUNT(*) FROM cost_paused WHERE module = 'analytics'")->fetchColumn() === 1, 'paused job recorded once per module/task/day');

// ---- module budget: research $2
cost_log('research', 'image', 2.5, ['task' => 'icon', 'ref' => 'R-1', 'pkg' => 'research:photo']);
ok(!cost_gate('research', false, 'weekly'), 'research budget used up -> research paused');
ok(str_contains((string)cost_last_block(), 'budget used up'), 'reason names the module budget');
ok(cost_gate('gads', false, 'alert_ai'), 'other module (gads) unaffected');
ok(cost_gate('research', true, 'x'), 'critical still allowed from reserve');

// ---- manual override (Sagar's OK) then revoke
kv_set('cost_override_research', (string)(time() + 3600));
ok(cost_gate('research', false, 'weekly'), 'override lets research run');
kv_set('cost_override_research', '0');
ok(!cost_gate('research', false, 'weekly'), 'override removed -> paused again');

// ---- total + reserve exhausted: even critical stops
cost_log('health', 'claude_code', 8.6, ['task' => 'ai_fix', 'ref' => 'FIX-1', 'pkg' => 'com.test.app']);
ok(!cost_gate('gads', true, 'alert_ai'), 'total budget + 10% reserve used up -> critical blocked too');
ok(str_contains((string)cost_last_block(), 'reserve'), 'reason mentions reserve');

// ---- fix cap
cost_save_settings(['total' => 1000, 'modules' => ['health' => ['budget' => 1000, 'fix_cap' => 2], 'research' => ['budget' => 1000]]]);
ok(cost_gate('health', false, 'ai_fix'), 'fix 1 allowed'); cost_fix_count(true);
ok(cost_gate('health', false, 'task'), 'fix 2 allowed'); cost_fix_count(true);
ok(!cost_gate('health', false, 'ai_fix'), 'fix cap 2 per day reached');

// ---- circuit breaker: 3 failed calls in a row -> module paused (no infinite retries)
cost_ctx(['module' => 'gads', 'task' => 'alert_ai', 'ref' => 'GA-1', 'critical' => true]);
cost_claude(null, false); cost_claude(null, false);
ok(cost_gate(), 'two failures: still open');
cost_claude(null, false);
ok(!cost_gate(), 'third failure in a row: breaker opens even for critical');
kv_set('cost_brk_until_gads', '0');
cost_claude(['model' => 'claude-opus-5-5', 'usage' => ['input_tokens' => 1, 'output_tokens' => 1]], true);
ok((int)kv_get('cost_brk_fails_gads') === 0, 'success resets the failure count');

// ---- warnings 70 / 90 / 100 once each
$pdo->exec('DELETE FROM cost_events');
cost_save_settings(['total' => 100, 'modules' => ['analytics' => ['budget' => 10]]]);
cost_log('analytics', 'anthropic', 7.2, ['task' => 't', 'ref' => 'x1']);
ok(cost_warn_check($pdo) === 1, '72% -> one warning');
ok((string)$pdo->query("SELECT rule FROM app_alerts ORDER BY id DESC LIMIT 1")->fetchColumn() === 'cost_analytics', 'warning is an app alert with rule cost_analytics');
ok(cost_warn_check($pdo) === 0, 'no duplicate at the same level');
cost_log('analytics', 'anthropic', 3.0, ['task' => 't', 'ref' => 'x2']);
cost_warn_check($pdo);
$lv = $pdo->query("SELECT level, title FROM app_alerts WHERE rule = 'cost_analytics' ORDER BY id DESC LIMIT 1")->fetch();
ok((int)$lv['level'] === 3 && str_contains($lv['title'], '100%'), '102% -> urgent 100% warning (skips a separate 90%)');
ok((int)$pdo->query("SELECT COUNT(*) FROM app_alerts WHERE rule = 'cost_analytics'")->fetchColumn() === 2, 'exactly two analytics warnings this month');

// ---- duplicate-safe logging (ukey)
cost_log('health', 'claude_code', 1.5, ['ukey' => 'cc|fix|9', 'ref' => 'FIX-9']);
cost_log('health', 'claude_code', 1.5, ['ukey' => 'cc|fix|9', 'ref' => 'FIX-9']);
ok((int)$pdo->query("SELECT COUNT(*) FROM cost_events WHERE ukey = 'cc|fix|9'")->fetchColumn() === 1, 'worker cost logged once per fix');

// ---- summary
$sum = cost_summary($pdo, 'month');
ok(near($sum['month_usd'], 7.2 + 3.0 + 1.5, 1e-3), 'month total = sum of events');
ok($sum['forecast_usd'] >= $sum['month_usd'], 'forecast >= spent so far');
ok(count($sum['modules']) === 4 && $sum['modules'][0]['key'] === 'analytics', 'four modules in summary');
ok(isset($sum['tasks'][0]['usd']) && $sum['tasks'][0]['usd'] >= $sum['tasks'][count($sum['tasks']) - 1]['usd'], 'tasks sorted by cost');
ok(near($sum['remaining_usd'], 100 - $sum['month_usd'], 1e-3), 'remaining = budget - spent');
ok(cost_summary($pdo, 'today')['range'] === 'today', 'today range');

echo "\n$n checks, $fail failed\n";
exit($fail ? 1 : 0);
