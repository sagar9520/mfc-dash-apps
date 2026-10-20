<?php
/**
 * Rule engine unit test (DB nahi chahiye):  php server/tests/gads_rules_test.php
 * SIMULATED test data only.
 */
declare(strict_types=1);
require __DIR__ . '/../lib/gads_rules.php';

$fail = 0; $n = 0;
function ok(bool $c, string $m): void { global $fail, $n; $n++; if (!$c) { $fail++; echo "FAIL: $m\n"; } else echo "ok   $m\n"; }
function rules(array $r): array { return array_map(fn($f) => $f['rule'] . ':' . $f['level'] . ':' . $f['klass'], $r['findings']); }
function has(array $r, string $rule, ?string $level = null): bool { foreach ($r['findings'] as $f) if ($f['rule'] === $rule && ($level === null || $f['level'] === $level)) return true; return false; }

$flat = fn(float $v, int $upto = 23) => array_fill(0, $upto + 1, $v);
$base = fn(float $v, int $nDays = 3) => array_map(fn($i) => ['day' => "2026-10-" . (10 + $i), 'cost_h' => array_fill(0, 24, $v), 'impr_h' => array_fill(0, 24, 500.0)], range(1, $nDays));
$c0 = ['cid' => '1234567890', 'campaign_id' => '111', 'name' => 'SIM camp', 'currency' => 'INR', 'status' => 'ENABLED', 'status_prev' => '', 'status_changed_h' => null,
    'age_days' => 60, 'learning' => false, 'budget' => null, 'primary_status' => '', 'primary_reasons' => [], 'serving_status' => 'SERVING', 'today' => '2026-10-20',
    'hour_now' => 15, 'reported_through' => 13, 'hourly_supported' => true, 'today_cost_h' => [], 'today_impr_h' => [], 'baseline' => [], 'daily' => [],
    'cpi_target' => null, 'roas_target' => null, 'fresh' => true, 'data_age_min' => 10];
$th = GR_DEFAULTS;

// spend drop 40%: 55 vs 100 = -45%
$r = gr_campaign(['today_cost_h' => $flat(55, 13), 'today_impr_h' => $flat(500, 13), 'baseline' => $base(100)] + $c0, $th);
ok(has($r, 'spend_drop', 'WARNING'), 'spend -45% -> spend_drop WARNING');
// -35%: no alert (minor INFO only)
$r = gr_campaign(['today_cost_h' => $flat(65, 13), 'today_impr_h' => $flat(500, 13), 'baseline' => $base(100)] + $c0, $th);
ok(!has($r, 'spend_drop') && has($r, 'minor_change', 'INFO'), 'spend -35% -> only minor INFO');
// spike +60%: 170 vs 100
$r = gr_campaign(['today_cost_h' => $flat(170, 13), 'today_impr_h' => $flat(500, 13), 'baseline' => $base(100)] + $c0, $th);
ok(has($r, 'spend_spike', 'CRITICAL'), 'spend +70% -> spend_spike CRITICAL');
$r = gr_campaign(['today_cost_h' => $flat(150, 13), 'today_impr_h' => $flat(500, 13), 'baseline' => $base(100)] + $c0, $th);
ok(!has($r, 'spend_spike'), 'spend +50% -> no spike');
// learning: spike only INFO
$r = gr_campaign(['today_cost_h' => $flat(200, 13), 'today_impr_h' => $flat(500, 13), 'baseline' => $base(100), 'learning' => true] + $c0, $th);
ok(has($r, 'spend_spike', 'INFO') && !has($r, 'spend_spike', 'CRITICAL'), 'learning campaign spike -> INFO only');
// custom threshold 80% spike
$r = gr_campaign(['today_cost_h' => $flat(170, 13), 'today_impr_h' => $flat(500, 13), 'baseline' => $base(100)] + $c0, ['spike_pct' => 80] + $th);
ok(!has($r, 'spend_spike'), 'configurable spike threshold 80% respected');

// zero spend 2h: hours 12,13 zero
$h = $flat(100, 13); $h[12] = 0; $h[13] = 0;
$r = gr_campaign(['today_cost_h' => $h, 'today_impr_h' => $flat(500, 13), 'baseline' => $base(100)] + $c0, $th);
ok(has($r, 'zero_spend', 'CRITICAL'), 'ENABLED campaign 2h zero spend -> zero_spend CRITICAL');
ok(!has($r, 'suspended'), 'zero spend is never classified as suspension');
// only 1h zero
$h = $flat(100, 13); $h[13] = 0;
$r = gr_campaign(['today_cost_h' => $h, 'today_impr_h' => $flat(500, 13), 'baseline' => $base(100)] + $c0, $th);
ok(!has($r, 'zero_spend'), '1h zero -> no zero_spend');
// paused campaign zero -> no zero alert
$h = $flat(100, 13); $h[12] = 0; $h[13] = 0;
$r = gr_campaign(['status' => 'PAUSED', 'today_cost_h' => $h, 'today_impr_h' => $flat(500, 13), 'baseline' => $base(100)] + $c0, $th);
ok(!has($r, 'zero_spend'), 'paused campaign -> no unexpected-zero alert');
// budget exhausted -> INFO budget, not CRITICAL
$r = gr_campaign(['budget' => 1100.0, 'today_cost_h' => $h, 'today_impr_h' => $flat(500, 13), 'baseline' => $base(100)] + $c0, $th);
ok(has($r, 'budget_limited', 'INFO') && !has($r, 'zero_spend'), 'budget used up -> budget INFO, no zero CRITICAL');
// baseline hours were also zero (campaign does not serve those hours) -> no alert
$bz = $base(100); foreach ($bz as &$b) { $b['cost_h'][12] = 0; $b['cost_h'][13] = 0; } unset($b);
$r = gr_campaign(['today_cost_h' => $h, 'today_impr_h' => $flat(500, 13), 'baseline' => $bz] + $c0, $th);
ok(!has($r, 'zero_spend'), 'not expected to serve those hours -> no zero alert');

// minimum sample: tiny campaign
$r = gr_campaign(['today_cost_h' => $flat(1, 13), 'today_impr_h' => $flat(2, 13), 'baseline' => array_map(fn($b) => ['day' => $b['day'], 'cost_h' => array_fill(0, 24, 5.0), 'impr_h' => array_fill(0, 24, 10.0)], $base(5))] + $c0, $th);
ok(!has($r, 'spend_drop') && ($r['checks']['pace'][0] ?? '') === 'INSUFFICIENT', 'below min sample -> INSUFFICIENT, no alert');
// too few comparable days
$r = gr_campaign(['today_cost_h' => $flat(10, 13), 'today_impr_h' => $flat(500, 13), 'baseline' => $base(100, 1)] + $c0, $th);
ok(!has($r, 'spend_drop') && ($r['checks']['pace'][0] ?? '') === 'INSUFFICIENT', '1 comparable day -> INSUFFICIENT');
// stale data -> no metric rules at all
$r = gr_campaign(['fresh' => false, 'today_cost_h' => [], 'baseline' => $base(100)] + $c0, $th);
ok($r['findings'] === [], 'stale data -> no metric findings (incomplete != zero)');
// today not reported yet (R = -1)
$r = gr_campaign(['reported_through' => -1, 'today_cost_h' => [], 'baseline' => $base(100)] + $c0, $th);
ok(!has($r, 'zero_spend') && !has($r, 'spend_drop'), 'no hourly data reported yet today -> no zero/drop alert');

// paused / removed
$r = gr_campaign(['status' => 'PAUSED', 'status_prev' => 'ENABLED', 'status_changed_h' => 1.0] + $c0, $th);
ok(has($r, 'paused_removed', 'WARNING'), 'ENABLED -> PAUSED detected');

// CPI: 3 complete days, target 20, actual 26 (+30%) with 60 installs
$daily = [];
foreach (['2026-10-17', '2026-10-18', '2026-10-19'] as $d) $daily[$d] = ['cost' => 520, 'impressions' => 20000, 'clicks' => 500, 'conversions' => 20, 'conv_value' => 300];
$r = gr_campaign(['daily' => $daily, 'cpi_target' => 20.0] + $c0, $th);
ok(has($r, 'cpi_high', 'WARNING'), 'CPI +30% over target -> cpi_high WARNING');
$r = gr_campaign(['daily' => $daily, 'cpi_target' => 22.0] + $c0, $th);
ok(!has($r, 'cpi_high'), 'CPI +18% -> no alert (threshold 25%)');
$small = array_map(fn($x) => ['conversions' => 5] + $x, $daily);
$r = gr_campaign(['daily' => $small, 'cpi_target' => 20.0] + $c0, $th);
ok(!has($r, 'cpi_high') && ($r['checks']['cpi'][0] ?? '') === 'INSUFFICIENT', 'CPI with 15 installs (<30) -> INSUFFICIENT');
// today's installs never used for CPI
$withToday = $daily + ['2026-10-20' => ['cost' => 5000, 'impressions' => 1, 'clicks' => 1, 'conversions' => 0, 'conv_value' => 0]];
$r = gr_campaign(['daily' => $withToday, 'cpi_target' => 30.0] + $c0, $th);
ok(!has($r, 'cpi_high'), 'today (lagging conversions) excluded from CPI');

// ROAS
$d7 = [];
foreach (range(13, 19) as $dd) $d7["2026-10-$dd"] = ['cost' => 1000, 'impressions' => 1, 'clicks' => 1, 'conversions' => 10, 'conv_value' => 600];
$r = gr_campaign(['daily' => $d7, 'roas_target' => 1.0] + $c0, $th);
ok(has($r, 'roas_low', 'WARNING'), 'ROAS 60% vs target 100% -> roas_low');

// account: suspension needs direct evidence
$a0 = ['cid' => '1234567890', 'company' => 'SIM Co', 'currency' => 'INR', 'data_age_min' => 10, 'sources' => []];
$r = gr_account($a0 + ['inactive' => ['now' => 0, 'usual' => 500, 'window' => 'today 00:00–13:59']], $th);
ok(has($r, 'inactive', 'WARNING') && !has($r, 'suspended'), 'account inactivity -> WARNING suspected, not suspension');
foreach ($r['findings'] as $f) if ($f['rule'] === 'inactive') ok($f['klass'] === 'SUSPECTED', 'inactivity classified SUSPECTED');
$r = gr_account($a0 + ['script_status' => 'SUSPENDED'], $th);
ok(has($r, 'suspended', 'CRITICAL'), 'customer.status SUSPENDED -> CRITICAL confirmed');
$r = gr_account($a0 + ['emails' => [['kind' => 'SUSPENDED', 'verified' => true, 'subject' => 'Your account is suspended', 'received_at' => '2026-10-20 10:00:00', 'reason' => 'Circumventing systems']]], $th);
ok(has($r, 'suspended', 'CRITICAL'), 'verified Google email -> suspension CRITICAL');
$r = gr_account($a0 + ['emails' => [['kind' => 'SUSPENDED', 'verified' => false, 'subject' => 'Your account is suspended', 'received_at' => '2026-10-20 10:00:00', 'reason' => '']]], $th);
ok(has($r, 'suspended', 'WARNING') && !has($r, 'suspended', 'CRITICAL'), 'unverified email -> only SUSPECTED WARNING');
$r = gr_account(['data_age_min' => 400] + $a0, $th);
ok(has($r, 'stale', 'WARNING'), 'no data for 6.7h -> STALE DATA');
$r = gr_account(['data_age_min' => null] + $a0, $th);
ok(has($r, 'no_source') && !has($r, 'stale'), 'never received data -> no_source INFO');
$r = gr_account($a0 + ['policy' => [['campaign_id' => '111', 'campaign' => 'SIM', 'ad_group_id' => '9', 'ad_id' => '77', 'approval' => 'DISAPPROVED', 'topics' => ['MISLEADING (PROHIBITED)']]]], $th);
ok(has($r, 'disapproved', 'WARNING'), 'disapproved ad -> WARNING');

echo "\n$n checks, $fail failed\n";
exit($fail > 0 ? 1 : 0);
