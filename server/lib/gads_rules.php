<?php
/**
 * Google Ads AI Health Monitor — rule engine (pure functions: koi DB nahi, koi network nahi).
 * lib/gads_health.php data jodta hai aur yahan bhejta hai; tests/gads_rules_test.php isi ko seedha test karta hai.
 *
 * Read-only: yahan sirf hisaab hota hai. Google Ads me kuch bhi badalne ka koi code nahi hai.
 *
 * Har finding:
 *   key    dedup key (ek issue = ek alert)
 *   rule   spend_drop | spend_spike | zero_spend | ...
 *   level  CRITICAL | WARNING | INFO
 *   klass  CONFIRMED | SUSPECTED | INSUFFICIENT
 *   title / title_hi, what / what_hi, metrics, causes[], next / next_hi, impact, evidence
 */
declare(strict_types=1);

const GR_DEFAULTS = [
    'drop_pct' => 40.0,          // spend comparable pace se itna % kam
    'spike_pct' => 60.0,         // spend comparable pace se itna % zyada
    'cpi_over_pct' => 25.0,      // actual CPI target se itna % upar
    'zero_hours' => 2.0,         // expected-to-serve campaign itne ghante se zero spend
    'min_impr_pace' => 1000.0,   // sample: comparable impressions kam se kam (currency-free)
    'min_spend_pace' => 0.0,     // sample: comparable spend kam se kam (account currency; 0 = off)
    'min_installs' => 30.0,      // sample: CPI / install rules ke liye kam se kam installs
    'min_baseline_days' => 2.0,  // comparable days (kal + same weekdays) kam se kam itne
    'stale_hours' => 3.0,        // itne ghante se data nahi = STALE DATA
    'spend_lag_hours' => 2.0,    // Google Ads hourly spend itna late aata hai (aakhri ghante compare nahi)
    'conv_lag_hours' => 3.0,     // conversions (installs) late aate hain: aaj ka CPI judge nahi hota
    'roas_under_pct' => 25.0,    // ROAS target se itna % neeche
    'install_drop_pct' => 50.0,  // installs pichhle hafte ke pace se itna % kam
    'learning_days' => 7.0,      // naya campaign itne din learning me maana jaata hai
    'minor_pct' => 20.0,         // isse upar (threshold se neeche) = minor change (INFO)
    'cpi_target' => null,        // campaign / country / account par set karo
    'roas_target' => null,       // ratio, jaise 1.2 = 120%
];

/** Threshold keys jo app se set ho sakte hain (scope: global / account / campaign / country). */
const GR_KEYS = ['drop_pct', 'spike_pct', 'cpi_over_pct', 'zero_hours', 'min_impr_pace', 'min_spend_pace', 'min_installs', 'min_baseline_days',
    'stale_hours', 'spend_lag_hours', 'conv_lag_hours', 'roas_under_pct', 'install_drop_pct', 'learning_days', 'minor_pct', 'cpi_target', 'roas_target'];

function gr_th(array $th, string $k): ?float
{
    $v = array_key_exists($k, $th) ? $th[$k] : (GR_DEFAULTS[$k] ?? null);
    return $v === null || $v === '' ? null : (float)$v;
}

function gr_median(array $v): ?float
{
    $v = array_values(array_filter($v, fn($x) => $x !== null));
    $n = count($v);
    if ($n === 0) return null;
    sort($v);
    return $n % 2 ? (float)$v[intdiv($n, 2)] : ($v[$n / 2 - 1] + $v[$n / 2]) / 2.0;
}

function gr_pct(float $now, ?float $base): ?float { return ($base === null || $base <= 0) ? null : ($now - $base) / $base * 100.0; }
function gr_r(?float $v, int $d = 2): ?float { return $v === null ? null : round($v, $d); }
function gr_money(?float $v, string $cur): string { return $v === null ? '–' : $cur . ' ' . number_format($v, $v >= 100 ? 0 : 2); }
function gr_pcts(?float $p): string { return $p === null ? '–' : (($p > 0 ? '+' : '') . number_format($p, 0) . '%'); }

/** Ghanton [0..$upto] ka jod. $hours = [hour => value]. */
function gr_sum_hours(array $hours, int $from, int $upto): float
{
    $s = 0.0;
    foreach ($hours as $h => $v) if ($h >= $from && $h <= $upto) $s += (float)$v;
    return $s;
}

/** Daily rows [day => [cost, impressions, clicks, conversions, conv_value]] ka jod, sirf diye gaye din. */
function gr_sum_days(array $daily, array $days): array
{
    $o = ['cost' => 0.0, 'impressions' => 0.0, 'clicks' => 0.0, 'conversions' => 0.0, 'conv_value' => 0.0, 'days' => 0];
    foreach ($days as $d) {
        if (!isset($daily[$d])) continue;
        foreach (['cost', 'impressions', 'clicks', 'conversions', 'conv_value'] as $k) $o[$k] += (float)($daily[$d][$k] ?? 0);
        $o['days']++;
    }
    return $o;
}

/** $day se pichhle $n din (aaj shamil nahi): ['2026-10-19', ...]. */
function gr_days_before(string $day, int $n, int $skip = 0): array
{
    $out = [];
    $d = new DateTimeImmutable($day);
    for ($i = 1 + $skip; $i <= $n + $skip; $i++) $out[] = $d->modify("-$i day")->format('Y-m-d');
    return $out;
}

function gr_f(string $key, string $rule, string $level, string $klass, array $t): array
{
    return ['key' => $key, 'rule' => $rule, 'level' => $level, 'klass' => $klass] + $t + [
        'title' => '', 'title_hi' => '', 'what' => '', 'what_hi' => '', 'metrics' => [], 'causes' => [], 'next' => '', 'next_hi' => '', 'impact' => '', 'evidence' => '',
    ];
}

/**
 * Ek campaign ke rules.
 * $c: cid, campaign_id, name, currency, status, status_prev, status_changed_h (ghante pehle, ya null), age_days, learning, budget, primary_status, primary_reasons[],
 *     serving_status, today (Y-m-d, account TZ), hour_now, reported_through (R, -1 = aaj ka hourly nahi), hourly_supported,
 *     today_cost_h / today_impr_h ([h=>v]), baseline ([['day'=>, 'cost_h'=>[], 'impr_h'=>[]], ...] sirf jin din account ka hourly data tha),
 *     daily ([day => row], aaj shamil ho sakta hai), cpi_target, cpi_target_src, roas_target, roas_target_src, fresh, data_age_min, evidence
 * Return: ['findings' => [...], 'checks' => [name => [state, text]]]
 */
function gr_campaign(array $c, array $th): array
{
    $F = []; $checks = [];
    $cur = (string)($c['currency'] ?? '');
    $cid = (string)$c['cid']; $camp = (string)$c['campaign_id']; $name = (string)$c['name'];
    $kb = "$cid|$camp";
    $enabled = ($c['status'] ?? '') === 'ENABLED';
    $learning = !empty($c['learning']);
    $ev = (string)($c['evidence'] ?? 'Google Ads reporting data');
    $R = (int)($c['reported_through'] ?? -1);
    $today = (string)$c['today'];
    $daily = (array)($c['daily'] ?? []);

    if (empty($c['fresh'])) {
        // data purana: koi metric rule nahi (adhoora data = zero nahi)
        return ['findings' => [], 'checks' => ['data' => ['INSUFFICIENT', 'stale data: metric rules skipped']]];
    }

    // ---- status badla (paused / removed)
    if (!empty($c['status_prev']) && $c['status_prev'] === 'ENABLED' && in_array($c['status'], ['PAUSED', 'REMOVED'], true)
        && $c['status_changed_h'] !== null && $c['status_changed_h'] <= 48) {
        $st = (string)$c['status'];
        $F[] = gr_f("paused|$kb", 'paused_removed', 'WARNING', 'CONFIRMED', [
            'title' => "Campaign \"$name\" was " . strtolower($st), 'title_hi' => "Campaign \"$name\" " . ($st === 'PAUSED' ? 'pause' : 'remove') . ' ho gaya',
            'what' => "Status changed from ENABLED to $st about " . (int)$c['status_changed_h'] . ' h ago.',
            'what_hi' => "Status ENABLED se $st hua, lagbhag " . (int)$c['status_changed_h'] . ' ghante pehle.',
            'metrics' => ['status_now' => $st, 'status_before' => 'ENABLED'],
            'causes' => ['Someone paused/removed it in Google Ads (this monitor never changes campaigns)', 'Automated rule or another script in the account', 'Campaign end date reached'],
            'next' => 'Check Google Ads > Change history for this campaign and confirm the change was intended.',
            'next_hi' => 'Google Ads > Change history me dekho ki ye change jaan-boojh kar hua tha ya nahi.',
            'evidence' => $ev . ' (campaign.status)',
        ]);
    }

    // ---- learning / naya / budget limited (INFO)
    if ($learning && $enabled) {
        $F[] = gr_f("learning|$kb", 'learning', 'INFO', 'CONFIRMED', [
            'title' => "\"$name\" is in the learning period", 'title_hi' => "\"$name\" abhi learning period me hai",
            'what' => 'Spend and CPI swings are expected while Google learns; spend/CPI alerts for this campaign are softened to INFO.',
            'what_hi' => 'Learning me spend aur CPI upar-neeche hote hain; is campaign ke spend/CPI alerts INFO tak rakhe gaye hain.',
            'metrics' => ['age_days' => $c['age_days'], 'primary_status' => $c['primary_status'] ?? ''],
            'next' => 'Avoid big budget or target changes until learning ends.', 'next_hi' => 'Learning khatam hone tak budget / target me bada badlav mat karo.',
            'evidence' => $ev,
        ]);
    }
    if (($c['age_days'] ?? 99) < 2) {
        $F[] = gr_f("new|$kb", 'new_campaign', 'INFO', 'CONFIRMED', [
            'title' => "New campaign detected: \"$name\"", 'title_hi' => "Naya campaign mila: \"$name\"",
            'what' => "Campaign ID $camp started reporting data recently.", 'what_hi' => "Campaign ID $camp ka data abhi aana shuru hua.",
            'next' => 'Set its CPI / ROAS target in Notification Settings so cost alerts can work.', 'next_hi' => 'Notification Settings me iska CPI / ROAS target set karo.',
            'evidence' => $ev,
        ]);
    }
    $reasons = array_map('strval', (array)($c['primary_reasons'] ?? []));
    if ($enabled && array_intersect($reasons, ['BUDGET_CONSTRAINED', 'LIMITED_BY_BUDGET'])) {
        $F[] = gr_f("budget|$kb", 'budget_limited', 'INFO', 'CONFIRMED', [
            'title' => "\"$name\" is limited by budget", 'title_hi' => "\"$name\" budget se limited hai",
            'what' => 'Google Ads reports the campaign as budget constrained.' . ($c['budget'] !== null ? ' Daily budget: ' . gr_money((float)$c['budget'], $cur) . '.' : ''),
            'what_hi' => 'Google Ads ke hisaab se campaign budget ki wajah se ruk raha hai.',
            'metrics' => ['budget' => $c['budget'], 'reasons' => $reasons],
            'next' => 'If CPI/ROAS are on target, consider raising the budget manually in Google Ads.', 'next_hi' => 'CPI/ROAS target par hain to Google Ads me khud budget badhane par socho.',
            'evidence' => $ev . ' (campaign.primary_status_reasons)',
        ]);
    }

    // ---- spend pace (hourly, account timezone)
    $minBase = (int)gr_th($th, 'min_baseline_days');
    $base = (array)($c['baseline'] ?? []);
    if (empty($c['hourly_supported'])) {
        $checks['pace'] = ['INSUFFICIENT', 'no hourly data from Google Ads for this account'];
    } elseif ($R < 0) {
        $checks['pace'] = ['INSUFFICIENT', 'today\'s hourly data not reported yet (reporting lag)'];
    } elseif (count($base) < $minBase) {
        $checks['pace'] = ['INSUFFICIENT', 'only ' . count($base) . " comparable days (need $minBase)"];
    } else {
        $now = gr_sum_hours((array)$c['today_cost_h'], 0, $R);
        $nowI = gr_sum_hours((array)($c['today_impr_h'] ?? []), 0, $R);
        $bv = []; $bi = [];
        foreach ($base as $b) { $bv[] = gr_sum_hours((array)$b['cost_h'], 0, $R); $bi[] = gr_sum_hours((array)($b['impr_h'] ?? []), 0, $R); }
        $bm = gr_median($bv); $bim = gr_median($bi);
        $diff = gr_pct($now, $bm);
        $through = sprintf('00:00–%02d:59', $R);
        $m = ['window' => "today $through (account time)", 'spend_now' => gr_r($now), 'spend_usual' => gr_r($bm), 'diff_pct' => gr_r($diff, 1),
            'impr_now' => (int)$nowI, 'impr_usual' => (int)round((float)$bim), 'baseline_days' => array_column($base, 'day'), 'currency' => $cur];
        $sampleOk = ($bim ?? 0) >= gr_th($th, 'min_impr_pace') && ($bm ?? 0) > 0 && ($bm ?? 0) >= (float)gr_th($th, 'min_spend_pace');
        if (!$sampleOk) {
            $checks['pace'] = ['INSUFFICIENT', 'comparable volume below the minimum sample'];
        } elseif ($diff !== null) {
            $drop = (float)gr_th($th, 'drop_pct'); $spike = (float)gr_th($th, 'spike_pct'); $minor = (float)gr_th($th, 'minor_pct');
            $checks['pace'] = ['OK', 'spend ' . gr_pcts($diff) . ' vs usual pace'];
            $klass = ($c['data_age_min'] ?? 0) > 90 ? 'SUSPECTED' : 'CONFIRMED';
            if ($diff <= -$drop && $enabled) {
                $checks['pace'] = ['WARN', 'spend ' . gr_pcts($diff) . ' vs usual pace'];
                $F[] = gr_f("drop|$kb", 'spend_drop', $learning ? 'INFO' : 'WARNING', $klass, [
                    'title' => "Spend dropped " . gr_pcts($diff) . " on \"$name\"", 'title_hi' => "\"$name\" ka spend " . gr_pcts($diff) . ' gir gaya',
                    'what' => "Spend $through is " . gr_money($now, $cur) . ' vs a usual ' . gr_money($bm, $cur) . " at this time (median of yesterday and same weekdays). Threshold: -$drop%.",
                    'what_hi' => "$through ka spend " . gr_money($now, $cur) . ', jabki is time tak aam taur par ' . gr_money($bm, $cur) . " hota hai. Limit: -$drop%.",
                    'metrics' => $m,
                    'causes' => ['Budget limit reached or budget lowered', 'Ad disapproval / limited policy eligibility', 'Bid target (tCPI / tROAS) too strict', 'Account or payment restriction', 'Conversion tracking problem lowering bids', 'A recent change in the campaign'],
                    'next' => 'In Google Ads check the campaign\'s budget, Policy column, bid target and Change history.',
                    'next_hi' => 'Google Ads me campaign ka budget, Policy column, bid target aur Change history dekho.',
                    'impact' => 'Delivery ' . gr_money(max(0.0, (float)$bm - $now), $cur) . ' below usual so far today',
                    'evidence' => $ev . ' (hourly spend)',
                ]);
            } elseif ($diff >= $spike && $enabled) {
                $checks['pace'] = ['WARN', 'spend ' . gr_pcts($diff) . ' vs usual pace'];
                $F[] = gr_f("spike|$kb", 'spend_spike', $learning ? 'INFO' : 'CRITICAL', $klass, [
                    'title' => "Spend spike " . gr_pcts($diff) . " on \"$name\"", 'title_hi' => "\"$name\" ka spend " . gr_pcts($diff) . ' badh gaya',
                    'what' => "Spend $through is " . gr_money($now, $cur) . ' vs a usual ' . gr_money($bm, $cur) . " at this time. Threshold: +$spike%.",
                    'what_hi' => "$through ka spend " . gr_money($now, $cur) . ', jabki is time tak aam taur par ' . gr_money($bm, $cur) . " hota hai. Limit: +$spike%.",
                    'metrics' => $m,
                    'causes' => ['Budget or bid target raised recently', 'Broader targeting or new countries', 'Google expanding delivery after learning', 'Traffic quality change (check CPI / installs)'],
                    'next' => 'Check Change history and today\'s CPI; reduce budget manually only if the extra spend is not converting.',
                    'next_hi' => 'Change history aur aaj ka CPI dekho; extra spend installs nahi la raha to budget khud kam karo.',
                    'impact' => gr_money(max(0.0, $now - (float)$bm), $cur) . ' above usual so far today',
                    'evidence' => $ev . ' (hourly spend)',
                ]);
            } elseif (abs($diff) >= $minor && $enabled) {
                $F[] = gr_f("minor|$kb", 'minor_change', 'INFO', $klass, [
                    'title' => "Spend " . gr_pcts($diff) . " vs usual on \"$name\"", 'title_hi' => "\"$name\" ka spend aam se " . gr_pcts($diff),
                    'what' => "Spend $through: " . gr_money($now, $cur) . ' vs usual ' . gr_money($bm, $cur) . ' (below alert thresholds).',
                    'what_hi' => "$through spend: " . gr_money($now, $cur) . ', aam ' . gr_money($bm, $cur) . ' (alert limit se kam).',
                    'metrics' => $m, 'evidence' => $ev . ' (hourly spend)',
                ]);
            }
            // delivery (impressions) bahut gir gaye par spend rule nahi laga
            $di = gr_pct($nowI, $bim);
            if ($di !== null && $di <= -$drop && $enabled && !$learning && $diff > -$drop) {
                $F[] = gr_f("delivery|$kb", 'delivery_drop', 'WARNING', $klass, [
                    'title' => "Delivery reduced " . gr_pcts($di) . " on \"$name\"", 'title_hi' => "\"$name\" ki delivery " . gr_pcts($di) . ' kam',
                    'what' => "Impressions $through: " . number_format($nowI) . ' vs usual ' . number_format((float)$bim) . '.',
                    'what_hi' => "$through impressions: " . number_format($nowI) . ', aam ' . number_format((float)$bim) . '.',
                    'metrics' => $m, 'causes' => ['Higher competition / CPM', 'Ad or creative disapprovals', 'Bid target too strict', 'Audience or country limits'],
                    'next' => 'Check the Policy column, asset status and bid target in Google Ads.', 'next_hi' => 'Google Ads me Policy column, assets aur bid target dekho.',
                    'evidence' => $ev . ' (hourly impressions)',
                ]);
            }
        }

        // ---- unexpected zero spend: sirf expected-to-serve campaign
        $zh = max(1, (int)gr_th($th, 'zero_hours'));
        $from = $R - $zh + 1;
        if ($from >= 0 && $enabled && !in_array((string)($c['serving_status'] ?? ''), ['ENDED', 'PENDING', 'SUSPENDED', 'NONE'], true) && ($c['budget'] === null || (float)$c['budget'] > 0)) {
            $nowW = gr_sum_hours((array)$c['today_cost_h'], $from, $R);
            $exp = []; $served = 0;
            foreach ($base as $b) { $v = gr_sum_hours((array)$b['cost_h'], $from, $R); $exp[] = $v; if ($v > 0) $served++; }
            $expM = gr_median($exp);
            $expI = gr_median(array_map(fn($b) => gr_sum_hours((array)($b['impr_h'] ?? []), $from, $R), $base));
            $spentToday = gr_sum_hours((array)$c['today_cost_h'], 0, $R);
            $budgetDone = $c['budget'] !== null && (float)$c['budget'] > 0 && $spentToday >= 0.98 * (float)$c['budget'];
            $consistent = count($base) > 0 && $served / count($base) >= 0.7;
            $winTxt = sprintf('%02d:00–%02d:59', $from, $R);
            if ($nowW <= 0 && ($expM ?? 0) > 0 && $consistent && ($expI ?? 0) >= gr_th($th, 'min_impr_pace') * $zh / 24.0) {
                if ($budgetDone) {
                    $F[] = gr_f("budgetdone|$kb", 'budget_limited', 'INFO', 'CONFIRMED', [
                        'title' => "\"$name\" used its daily budget", 'title_hi' => "\"$name\" ka aaj ka budget khatam",
                        'what' => 'No spend in ' . $winTxt . ' because today\'s spend ' . gr_money($spentToday, $cur) . ' reached the daily budget ' . gr_money((float)$c['budget'], $cur) . '.',
                        'what_hi' => $winTxt . ' me spend nahi, kyunki aaj ' . gr_money($spentToday, $cur) . ' kharch ho kar budget ' . gr_money((float)$c['budget'], $cur) . ' poora ho gaya.',
                        'metrics' => ['spend_today' => gr_r($spentToday), 'budget' => $c['budget'], 'currency' => $cur],
                        'next' => 'If performance is good, consider a higher budget manually.', 'next_hi' => 'Performance achha hai to budget khud badhane par socho.',
                        'evidence' => $ev,
                    ]);
                } else {
                    $F[] = gr_f("zero|$kb", 'zero_spend', 'CRITICAL', ($c['data_age_min'] ?? 0) > 90 ? 'SUSPECTED' : 'CONFIRMED', [
                        'title' => "\"$name\" had steady spend but spend has stopped", 'title_hi' => "\"$name\" ka spend achanak ruk gaya",
                        'what' => "Zero spend in $winTxt (last $zh reported hours) while the campaign is ENABLED. Usually " . gr_money($expM, $cur) . " in these hours ($served of " . count($base) . ' comparable days had spend).',
                        'what_hi' => "$winTxt (aakhri $zh reported ghante) me spend zero, jabki campaign ENABLED hai. Aam taur par in ghanton me " . gr_money($expM, $cur) . ' hota hai.',
                        'metrics' => ['window' => $winTxt . ' (account time)', 'spend_now' => 0, 'spend_usual' => gr_r($expM), 'impr_usual' => (int)round((float)$expI),
                            'spend_today' => gr_r($spentToday), 'budget' => $c['budget'], 'baseline_days' => array_column($base, 'day'), 'currency' => $cur, 'diff_pct' => -100],
                        'causes' => ['Ads disapproved / policy restriction', 'Account billing or payment problem', 'Bid target too low to win auctions', 'Schedule or end date', 'Conversion tracking stopped (smart bidding pulls back)', 'Recent change in the campaign'],
                        'next' => 'Open the campaign in Google Ads: check the status column, Policy, billing notifications and Change history.',
                        'next_hi' => 'Google Ads me campaign kholo: status column, Policy, billing notification aur Change history dekho.',
                        'impact' => 'About ' . gr_money($expM, $cur) . " of normal delivery missed in $zh h",
                        'evidence' => $ev . ' (hourly spend; account reported data through ' . sprintf('%02d:59', $R) . ')',
                    ]);
                }
            }
        }
    }

    // ---- daily fallback: kal poora din zero (hourly na ho tab bhi)
    $yest = gr_days_before($today, 1)[0];
    $prev7 = gr_days_before($today, 7, 1);
    $p7 = gr_sum_days($daily, $prev7);
    $daysSpent = 0; foreach ($prev7 as $d) if ((float)($daily[$d]['cost'] ?? 0) > 0) $daysSpent++;
    $yCost = (float)($daily[$yest]['cost'] ?? 0);
    if ($enabled && empty($c['hourly_supported']) && $yCost <= 0 && $daysSpent >= 5 && $p7['impressions'] / 7 >= gr_th($th, 'min_impr_pace')) {
        $F[] = gr_f("zeroday|$kb", 'zero_spend', 'CRITICAL', 'CONFIRMED', [
            'title' => "\"$name\" spent nothing yesterday", 'title_hi' => "\"$name\" ne kal kuch spend nahi kiya",
            'what' => "Zero spend on $yest while the campaign is ENABLED; it spent on $daysSpent of the previous 7 days (avg " . gr_money($p7['cost'] / 7, $cur) . '/day).',
            'what_hi' => "$yest ko spend zero, campaign ENABLED hai; pichhle 7 me se $daysSpent din spend hua tha (avg " . gr_money($p7['cost'] / 7, $cur) . '/din).',
            'metrics' => ['day' => $yest, 'spend' => 0, 'avg7' => gr_r($p7['cost'] / 7), 'currency' => $cur, 'diff_pct' => -100],
            'causes' => ['Ads disapproved / policy restriction', 'Billing or payment problem', 'Bid target too low', 'Schedule / end date'],
            'next' => 'Check the campaign status, Policy column and billing in Google Ads.', 'next_hi' => 'Google Ads me campaign status, Policy aur billing dekho.',
            'evidence' => $ev . ' (daily spend)',
        ]);
    }

    // ---- CPI (complete days; aaj ka nahi kyunki installs late aate hain)
    $minI = (float)gr_th($th, 'min_installs');
    $w3 = gr_days_before($today, 3);
    $s3 = gr_sum_days($daily, $w3);
    $cpiT = $c['cpi_target'] ?? null;
    if ($cpiT === null || (float)$cpiT <= 0) {
        $checks['cpi'] = ['INSUFFICIENT', 'no CPI target set'];
    } elseif ($s3['conversions'] < $minI) {
        $checks['cpi'] = ['INSUFFICIENT', number_format($s3['conversions'], 0) . " installs in 3 days (need $minI)"];
    } else {
        $cpi = $s3['cost'] / $s3['conversions'];
        $over = gr_pct($cpi, (float)$cpiT);
        $lim = (float)gr_th($th, 'cpi_over_pct');
        $checks['cpi'] = [$over >= $lim ? 'WARN' : 'OK', 'CPI ' . gr_money($cpi, $cur) . ' vs target ' . gr_money((float)$cpiT, $cur)];
        if ($over >= $lim) {
            $F[] = gr_f("cpi|$kb", 'cpi_high', $learning ? 'INFO' : 'WARNING', 'CONFIRMED', [
                'title' => "\"$name\" CPI is " . gr_pcts($over) . ' above target', 'title_hi' => "\"$name\" ka CPI target se " . gr_pcts($over) . ' upar',
                'what' => 'Actual CPI ' . gr_money($cpi, $cur) . ' (last 3 complete days, ' . number_format($s3['conversions'], 0) . ' installs) vs target ' . gr_money((float)$cpiT, $cur) . " (" . ($c['cpi_target_src'] ?? 'configured') . "). Threshold: +$lim%.",
                'what_hi' => 'Asli CPI ' . gr_money($cpi, $cur) . ' (pichhle 3 poore din, ' . number_format($s3['conversions'], 0) . ' installs), target ' . gr_money((float)$cpiT, $cur) . ". Limit: +$lim%.",
                'metrics' => ['window' => 'last 3 complete days', 'cpi' => gr_r($cpi), 'cpi_target' => (float)$cpiT, 'diff_pct' => gr_r($over, 1), 'installs' => gr_r($s3['conversions'], 1),
                    'spend' => gr_r($s3['cost']), 'currency' => $cur, 'days' => $w3],
                'causes' => ['Bid target (tCPI) set above what the market gives', 'Low conversion volume / learning', 'Expensive countries taking more budget', 'Weak creatives (LOW assets)', 'Install attribution lag (check again tomorrow)'],
                'next' => 'Review country CPI, creative labels and the bid target in Google Ads; change targets manually only after checking volume.',
                'next_hi' => 'Google Ads me country wise CPI, creative labels aur bid target dekho; volume dekh kar hi target khud badlo.',
                'impact' => gr_money(($cpi - (float)$cpiT) * $s3['conversions'], $cur) . ' extra cost vs target in 3 days',
                'evidence' => $ev . ' (daily cost + conversions)',
            ]);
        }
    }

    // ---- ROAS (7 complete days)
    $w7 = gr_days_before($today, 7);
    $s7 = gr_sum_days($daily, $w7);
    $roasT = $c['roas_target'] ?? null;
    if ($roasT === null || (float)$roasT <= 0) {
        $checks['roas'] = ['INSUFFICIENT', 'no ROAS target set'];
    } elseif ($s7['cost'] <= 0 || $s7['conversions'] < $minI) {
        $checks['roas'] = ['INSUFFICIENT', 'not enough conversions in 7 days'];
    } else {
        $roas = $s7['conv_value'] / $s7['cost'];
        $under = gr_pct($roas, (float)$roasT);
        $lim = (float)gr_th($th, 'roas_under_pct');
        $checks['roas'] = [$under < -$lim ? 'WARN' : 'OK', 'ROAS ' . number_format($roas * 100, 0) . '% vs target ' . number_format((float)$roasT * 100, 0) . '%'];
        if ($under < -$lim) {
            $F[] = gr_f("roas|$kb", 'roas_low', $learning ? 'INFO' : 'WARNING', 'CONFIRMED', [
                'title' => "\"$name\" ROAS is below target", 'title_hi' => "\"$name\" ka ROAS target se neeche",
                'what' => 'ROAS ' . number_format($roas * 100, 0) . '% (7 complete days, conversion value / cost) vs target ' . number_format((float)$roasT * 100, 0) . "%. Threshold: -$lim%.",
                'what_hi' => 'ROAS ' . number_format($roas * 100, 0) . '% (7 poore din) jabki target ' . number_format((float)$roasT * 100, 0) . "% hai. Limit: -$lim%.",
                'metrics' => ['window' => 'last 7 complete days', 'roas' => gr_r($roas, 3), 'roas_target' => (float)$roasT, 'diff_pct' => gr_r($under, 1), 'spend' => gr_r($s7['cost']),
                    'conv_value' => gr_r($s7['conv_value']), 'currency' => $cur],
                'causes' => ['Conversion values missing or late (attribution lag)', 'In-app revenue / ad monetization lower than before', 'Spend moved to weaker countries or creatives', 'tROAS too low for the campaign'],
                'next' => 'Compare conversion value with your app revenue report, then review the tROAS and country split manually.',
                'next_hi' => 'Conversion value ko app revenue report se milao, phir tROAS aur country split khud dekho.',
                'evidence' => $ev . ' (daily cost + conversion value)',
            ]);
        }
    }

    // ---- installs gire (3 din vs pichhle 7 din ka pace)
    $wPrev = gr_days_before($today, 7, 3);
    $sp = gr_sum_days($daily, $wPrev);
    if ($sp['days'] >= 5 && $sp['conversions'] >= $minI) {
        $expected = $sp['conversions'] / $sp['days'] * 3;
        $dd = gr_pct($s3['conversions'], $expected);
        $lim = (float)gr_th($th, 'install_drop_pct');
        if ($dd !== null && $dd <= -$lim && $enabled && !$learning) {
            $F[] = gr_f("inst|$kb", 'installs_drop', 'WARNING', 'CONFIRMED', [
                'title' => "Installs down " . gr_pcts($dd) . " on \"$name\"", 'title_hi' => "\"$name\" ke installs " . gr_pcts($dd) . ' gire',
                'what' => number_format($s3['conversions'], 0) . ' installs in the last 3 complete days vs ' . number_format($expected, 0) . ' expected from the week before.',
                'what_hi' => 'Pichhle 3 poore din me ' . number_format($s3['conversions'], 0) . ' installs, jabki usse pehle ke hafte ke pace se ' . number_format($expected, 0) . ' hone chahiye the.',
                'metrics' => ['installs_3d' => gr_r($s3['conversions'], 1), 'expected_3d' => gr_r($expected, 1), 'diff_pct' => gr_r($dd, 1), 'spend_3d' => gr_r($s3['cost']), 'currency' => $cur],
                'causes' => ['Lower spend / delivery', 'Higher CPI', 'Conversion tracking (Firebase / SDK) problem', 'Store listing or app version change'],
                'next' => 'Check spend trend, conversion action status in Google Ads, and Play Console installs for the same days.',
                'next_hi' => 'Spend trend, Google Ads conversion action status aur Play Console installs (same din) milao.',
                'evidence' => $ev . ' (daily conversions)',
            ]);
        }
    }

    // ---- conversion tracking anomaly: clicks theek, installs zero
    $c7 = gr_sum_days($daily, $prev7);
    $yClicks = (float)($daily[$yest]['clicks'] ?? 0);
    $yConv = (float)($daily[$yest]['conversions'] ?? 0);
    if ($enabled && $c7['days'] >= 5 && $c7['conversions'] / max(1, $c7['days']) >= 5 && $yClicks >= 0.5 * $c7['clicks'] / max(1, $c7['days']) && $yClicks > 0 && $yConv <= 0) {
        $F[] = gr_f("conv|$kb", 'conv_tracking', 'WARNING', 'SUSPECTED', [
            'title' => "Possible conversion tracking problem on \"$name\"", 'title_hi' => "\"$name\" me conversion tracking problem ho sakta hai",
            'what' => "Yesterday ($yest) had " . number_format($yClicks) . ' clicks but 0 installs, while the previous week averaged ' . number_format($c7['conversions'] / $c7['days'], 1) . ' installs/day.',
            'what_hi' => "Kal ($yest) " . number_format($yClicks) . ' clicks aaye par 0 installs, jabki pichhle hafte avg ' . number_format($c7['conversions'] / $c7['days'], 1) . ' installs/din the.',
            'metrics' => ['clicks_yesterday' => (int)$yClicks, 'installs_yesterday' => 0, 'installs_avg7' => gr_r($c7['conversions'] / $c7['days'], 1)],
            'causes' => ['Conversion action / Firebase link broken', 'New app version without the SDK event', 'Attribution delay (re-check later today)'],
            'next' => 'Check Google Ads > Goals > Conversions status and Play Console installs for yesterday.',
            'next_hi' => 'Google Ads > Goals > Conversions ka status aur Play Console ke kal ke installs dekho.',
            'evidence' => $ev . ' (daily clicks + conversions)',
        ]);
    }

    return ['findings' => $F, 'checks' => $checks];
}

/**
 * Account level rules.
 * $a: cid, company, currency, data_age_min (null = kabhi data nahi), sources[], script_status (customer.status) + script_status_at,
 *     script_errors[], emails[] ([kind, verified, subject, received_at, reason]), policy[] ([campaign_id, campaign, ad_group_id, ad_id, approval, topics]),
 *     assets_low[] ([campaign_id, campaign, asset_id, name, field_type, impressions]), inactive (['now'=>, 'usual'=>, 'window'=>] ya null)
 */
function gr_account(array $a, array $th): array
{
    $F = []; $checks = [];
    $cid = (string)$a['cid'];
    $cur = (string)($a['currency'] ?? '');
    $stale = (float)gr_th($th, 'stale_hours') * 60;
    $age = $a['data_age_min'];
    if ($age === null) {
        $checks['data'] = ['INSUFFICIENT', 'no data source has sent data yet'];
        $F[] = gr_f("nosource|$cid", 'no_source', 'INFO', 'INSUFFICIENT', [
            'title' => 'No reporting data yet for this account', 'title_hi' => 'Is account ka abhi koi data nahi aaya',
            'what' => 'Neither the dashboard\'s Google Ads Script nor the read-only health script has sent data for this Customer ID.',
            'what_hi' => 'Is Customer ID ke liye dashboard ki Google Ads Script ya health script ne abhi tak data nahi bheja.',
            'next' => 'Install the read-only script in this account (Notification Settings > Account > Script).', 'next_hi' => 'Is account me read-only script lagao (Settings > Account > Script).',
            'evidence' => 'no rows for this Customer ID',
        ]);
    } elseif ($age > $stale) {
        $checks['data'] = ['FAIL', 'last data ' . round($age / 60, 1) . ' h ago'];
        $F[] = gr_f("stale|$cid", 'stale', 'WARNING', 'CONFIRMED', [
            'title' => 'DATA SOURCE DISCONNECTED / STALE DATA', 'title_hi' => 'DATA SOURCE DISCONNECTED / STALE DATA',
            'what' => 'No new reporting data for ' . round($age / 60, 1) . ' hours (limit ' . gr_th($th, 'stale_hours') . ' h). Metric rules are paused for this account; this is not treated as zero spend or a suspension.',
            'what_hi' => round($age / 60, 1) . ' ghante se naya data nahi aaya (limit ' . gr_th($th, 'stale_hours') . ' h). Is account ke metric rules ruke hain; ise zero spend ya suspension nahi maana gaya.',
            'metrics' => ['last_data_min' => (int)$age, 'sources' => $a['sources'] ?? []],
            'causes' => ['Google Ads Script stopped / authorization expired', 'Script schedule turned off', 'Dashboard URL or token changed', 'Account access removed (check your email for Google notices)'],
            'next' => 'Open Google Ads > Tools > Scripts in this account, check the last run log and re-authorize if needed.',
            'next_hi' => 'Is account me Google Ads > Tools > Scripts kholo, aakhri run ka log dekho aur zaroorat ho to dobara authorize karo.',
            'evidence' => 'last receipt time per source',
        ]);
    } else {
        $checks['data'] = ['OK', 'fresh · ' . (int)$age . ' min'];
    }

    foreach ((array)($a['script_errors'] ?? []) as $e) {
        $sec = (string)($e['section'] ?? 'report');
        $F[] = gr_f("srcerr|$cid|$sec", 'source_error', 'WARNING', 'CONFIRMED', [
            'title' => "Reporting error: $sec", 'title_hi' => "Reporting error: $sec",
            'what' => 'The read-only script could not read "' . $sec . '": ' . mb_substr((string)($e['error'] ?? ''), 0, 300) . '. That part is shown as missing, not as zero.',
            'what_hi' => 'Read-only script "' . $sec . '" nahi padh payi: ' . mb_substr((string)($e['error'] ?? ''), 0, 300) . '. Ye hissa missing dikhega, zero nahi.',
            'metrics' => ['section' => $sec, 'at' => $e['at'] ?? null],
            'next' => 'Open the script log in Google Ads; the query may need a newer Ads API version or permission.', 'next_hi' => 'Google Ads me script ka log dekho.',
            'evidence' => 'read-only script error report',
        ]);
    }

    // ---- account status: sirf direct evidence
    $ss = strtoupper((string)($a['script_status'] ?? ''));
    $checks['status'] = ['OK', $ss !== '' ? "customer.status = $ss" : 'no status source (needs read-only script or email)'];
    if ($ss === '') $checks['status'][0] = 'INSUFFICIENT';
    if ($ss === 'SUSPENDED') {
        $checks['status'] = ['FAIL', 'customer.status = SUSPENDED'];
        $F[] = gr_f("susp|$cid", 'suspended', 'CRITICAL', 'CONFIRMED', [
            'title' => "Google Ads account CID " . gr_cid($cid) . ' is suspended', 'title_hi' => "Google Ads account CID " . gr_cid($cid) . ' suspended hai',
            'what' => 'Google Ads reports customer.status = SUSPENDED for this account (verified status from the read-only script).',
            'what_hi' => 'Google Ads is account ka customer.status = SUSPENDED bata raha hai (read-only script se verified).',
            'metrics' => ['customer_status' => $ss, 'at' => $a['script_status_at'] ?? null],
            'causes' => ['Policy violation (see the official Google Ads email for the exact reason)', 'Unpaid balance / suspicious payment activity', 'Advertiser verification pending'],
            'next' => 'Read the official notice in Google Ads (bell icon / email) and file an appeal manually if appropriate. This monitor does not change anything.',
            'next_hi' => 'Google Ads ka official notice (bell icon / email) padho aur zaroorat ho to khud appeal karo. Ye monitor kuch nahi badalta.',
            'impact' => 'All campaigns in this account stop serving', 'evidence' => 'customer.status (read-only script)',
        ]);
    } elseif (in_array($ss, ['CANCELED', 'CLOSED'], true)) {
        $F[] = gr_f("closed|$cid", 'account_closed', 'WARNING', 'CONFIRMED', [
            'title' => 'Account status is ' . $ss, 'title_hi' => 'Account status ' . $ss . ' hai',
            'what' => "Google Ads reports customer.status = $ss.", 'what_hi' => "Google Ads customer.status = $ss bata raha hai.",
            'metrics' => ['customer_status' => $ss], 'next' => 'Confirm in Google Ads whether this was intended.', 'next_hi' => 'Google Ads me confirm karo ki ye jaan-boojh kar hua.',
            'evidence' => 'customer.status (read-only script)',
        ]);
    }

    // ---- Google Ads notification emails
    foreach ((array)($a['emails'] ?? []) as $m) {
        $kind = (string)$m['kind'];
        $ver = !empty($m['verified']);
        $reason = trim((string)($m['reason'] ?? ''));
        $subj = mb_substr((string)$m['subject'], 0, 160);
        $base = ['metrics' => ['email_subject' => $subj, 'received_at' => $m['received_at'] ?? null, 'verified' => $ver, 'reason' => $reason],
            'evidence' => $ver ? 'official Google Ads email (sender + DKIM verified)' : 'email NOT verified (sender/DKIM check failed)'];
        $k = "mail|$cid|$kind|" . substr(sha1($subj . ($m['received_at'] ?? '')), 0, 10);
        if ($kind === 'SUSPENDED') {
            $F[] = gr_f($k, 'suspended', $ver ? 'CRITICAL' : 'WARNING', $ver ? 'CONFIRMED' : 'SUSPECTED', $base + [
                'title' => 'Google Ads account CID ' . gr_cid($cid) . ' has a ' . ($ver ? 'confirmed' : 'possible') . ' suspension notification',
                'title_hi' => 'Google Ads account CID ' . gr_cid($cid) . ' ka suspension notice ' . ($ver ? '(confirmed)' : '(verify nahi hua)'),
                'what' => 'Email: "' . $subj . '".' . ($reason !== '' ? " Policy reason: $reason." : ''),
                'what_hi' => 'Email: "' . $subj . '".' . ($reason !== '' ? " Policy wajah: $reason." : ''),
                'causes' => ['See the policy reason in the official email'],
                'next' => 'Open Google Ads > Policy manager / the email link and appeal manually if appropriate.',
                'next_hi' => 'Google Ads > Policy manager ya email ka link kholo aur zaroorat ho to khud appeal karo.',
                'impact' => 'Ads in this account stop serving while suspended',
            ]);
        } elseif ($kind === 'PAYMENT_RESTRICTED') {
            $F[] = gr_f($k, 'payment_restricted', $ver ? 'CRITICAL' : 'WARNING', $ver ? 'CONFIRMED' : 'SUSPECTED', $base + [
                'title' => 'Payment restriction notice for CID ' . gr_cid($cid), 'title_hi' => 'CID ' . gr_cid($cid) . ' par payment restriction notice',
                'what' => 'Email: "' . $subj . '".', 'what_hi' => 'Email: "' . $subj . '".',
                'next' => 'Check Billing in Google Ads for this account only. Do not move billing between accounts.', 'next_hi' => 'Sirf isi account ka Billing Google Ads me dekho.',
                'impact' => 'Ads may stop until billing is resolved',
            ]);
        } elseif ($kind === 'PAYMENT_NOTICE') {
            $F[] = gr_f($k, 'payment_notice', 'WARNING', $ver ? 'CONFIRMED' : 'SUSPECTED', $base + [
                'title' => 'Payment notification for CID ' . gr_cid($cid), 'title_hi' => 'CID ' . gr_cid($cid) . ' ka payment notification',
                'what' => 'Email: "' . $subj . '".', 'what_hi' => 'Email: "' . $subj . '".',
                'next' => 'Review the notice in Google Ads Billing for this account.', 'next_hi' => 'Is account ka Billing notice Google Ads me dekho.',
            ]);
        } elseif ($kind === 'POLICY') {
            $F[] = gr_f($k, 'policy_restriction', 'WARNING', $ver ? 'CONFIRMED' : 'SUSPECTED', $base + [
                'title' => 'Policy notice for CID ' . gr_cid($cid), 'title_hi' => 'CID ' . gr_cid($cid) . ' ka policy notice',
                'what' => 'Email: "' . $subj . '".' . ($reason !== '' ? " Reason: $reason." : ''), 'what_hi' => 'Email: "' . $subj . '".' . ($reason !== '' ? " Wajah: $reason." : ''),
                'next' => 'Open Policy manager in Google Ads and fix or appeal the listed items manually.', 'next_hi' => 'Google Ads Policy manager kholo aur items khud theek / appeal karo.',
            ]);
        }
    }

    // ---- ad disapprovals / limited (read-only script ka policy snapshot)
    $byCamp = [];
    foreach ((array)($a['policy'] ?? []) as $p) {
        $key = (string)$p['campaign_id'] . '|' . ((string)$p['approval'] === 'DISAPPROVED' ? 'D' : 'L');
        $byCamp[$key][] = $p;
    }
    foreach ($byCamp as $key => $rows) {
        [$camp, $t] = explode('|', $key);
        $nm = (string)($rows[0]['campaign'] ?? $camp);
        $topics = array_values(array_unique(array_filter(array_merge(...array_map(fn($r) => (array)($r['topics'] ?? []), $rows)))));
        $ids = array_slice(array_map(fn($r) => (string)$r['ad_id'], $rows), 0, 20);
        $ag = (string)($rows[0]['ad_group_id'] ?? '');
        if ($t === 'D') {
            $F[] = gr_f("disapp|$cid|$camp", 'disapproved', 'WARNING', 'CONFIRMED', [
                'title' => count($rows) . " disapproved ad(s) in \"$nm\"", 'title_hi' => "\"$nm\" me " . count($rows) . ' ad disapproved',
                'what' => 'Ad IDs: ' . implode(', ', $ids) . ($topics ? '. Policy topics: ' . implode(', ', array_slice($topics, 0, 6)) : '') . '.',
                'what_hi' => 'Ad IDs: ' . implode(', ', $ids) . ($topics ? '. Policy topics: ' . implode(', ', array_slice($topics, 0, 6)) : '') . '.',
                'metrics' => ['count' => count($rows), 'ad_ids' => $ids, 'topics' => $topics, 'campaign_id' => $camp, 'ad_group_id' => $ag],
                'causes' => ['Asset text/image breaks a policy (see topics)', 'Destination / app store listing issue'],
                'next' => 'Open the ads in Google Ads, read the policy detail and replace or appeal manually.', 'next_hi' => 'Google Ads me ads kholo, policy detail padho aur khud replace / appeal karo.',
                'evidence' => 'ad_group_ad.policy_summary (read-only script)', 'campaign_id' => $camp, 'campaign' => $nm, 'ad_group_id' => $ag,
            ]);
        } else {
            $F[] = gr_f("limited|$cid|$camp", 'limited', 'INFO', 'CONFIRMED', [
                'title' => count($rows) . " ad(s) with limited eligibility in \"$nm\"", 'title_hi' => "\"$nm\" me " . count($rows) . ' ad limited',
                'what' => 'Approved (limited). ' . ($topics ? 'Topics: ' . implode(', ', array_slice($topics, 0, 6)) . '.' : ''),
                'what_hi' => 'Approved (limited). ' . ($topics ? 'Topics: ' . implode(', ', array_slice($topics, 0, 6)) . '.' : ''),
                'metrics' => ['count' => count($rows), 'ad_ids' => $ids, 'topics' => $topics, 'campaign_id' => $camp],
                'next' => 'Check which countries/audiences are limited and decide manually.', 'next_hi' => 'Kaunse country / audience limited hain dekho, faisla khud lo.',
                'evidence' => 'ad_group_ad.policy_summary (read-only script)', 'campaign_id' => $camp, 'campaign' => $nm, 'ad_group_id' => $ag,
            ]);
        }
    }
    $checks['policy'] = isset($a['policy']) && $a['policy'] !== null
        ? [count(array_filter((array)$a['policy'], fn($p) => $p['approval'] === 'DISAPPROVED')) ? 'WARN' : 'OK', count((array)$a['policy']) . ' ads with policy issues']
        : ['INSUFFICIENT', 'needs read-only script (policy data)'];

    // ---- LOW creatives (asset performance label; individual CPI Google nahi deta)
    $lowBy = [];
    foreach ((array)($a['assets_low'] ?? []) as $x) $lowBy[(string)$x['campaign_id']][] = $x;
    foreach ($lowBy as $camp => $rows) {
        $nm = (string)$rows[0]['campaign'];
        $F[] = gr_f("creative|$cid|$camp", 'creative_low', 'INFO', 'CONFIRMED', [
            'title' => count($rows) . " LOW-rated asset(s) in \"$nm\"", 'title_hi' => "\"$nm\" me " . count($rows) . ' LOW assets',
            'what' => 'Google rates these assets LOW: ' . implode('; ', array_map(fn($r) => mb_substr((string)$r['name'], 0, 40) . ' (' . $r['field_type'] . ')', array_slice($rows, 0, 5))) . '. Asset-level CPI is not available from Google, so none is shown.',
            'what_hi' => 'Google ne ye assets LOW rate kiye: ' . implode('; ', array_map(fn($r) => mb_substr((string)$r['name'], 0, 40), array_slice($rows, 0, 5))) . '. Asset ka alag CPI Google nahi deta.',
            'metrics' => ['assets' => array_map(fn($r) => ['asset_id' => $r['asset_id'], 'name' => $r['name'], 'field_type' => $r['field_type'], 'impressions' => $r['impressions']], array_slice($rows, 0, 10))],
            'next' => 'Prepare replacement assets for manual review (see AI Creative Review) and swap them yourself in Google Ads.',
            'next_hi' => 'Naye assets taiyar karo (AI Creative Review dekho) aur Google Ads me khud badlo.',
            'evidence' => 'ad_group_ad_asset_view.performance_label', 'campaign_id' => $camp, 'campaign' => $nm,
        ]);
    }

    // ---- account inactivity (suspension nahi!)
    if (!empty($a['inactive'])) {
        $in = $a['inactive'];
        $F[] = gr_f("inactive|$cid", 'inactive', 'WARNING', 'SUSPECTED', [
            'title' => 'Unexpected account inactivity', 'title_hi' => 'Account achanak inactive',
            'what' => 'No spend in any campaign ' . $in['window'] . ' while the account usually spends ' . gr_money((float)$in['usual'], $cur) . ' by now. This alone is NOT a suspension signal.',
            'what_hi' => $in['window'] . ' kisi campaign me spend nahi, jabki aam taur par is time tak ' . gr_money((float)$in['usual'], $cur) . ' hota hai. Sirf isse suspension nahi maana jaata.',
            'metrics' => ['spend_now' => 0, 'spend_usual' => $in['usual'], 'window' => $in['window'], 'currency' => $cur],
            'causes' => ['Billing / payment problem', 'Account-level policy restriction', 'All campaigns paused or budgets exhausted', 'Reporting delay in Google Ads'],
            'next' => 'Open Google Ads for this account and check notifications, billing and campaign statuses.', 'next_hi' => 'Is account ka Google Ads kholo: notifications, billing aur campaign status dekho.',
            'evidence' => 'hourly spend of all campaigns',
        ]);
    }
    return ['findings' => $F, 'checks' => $checks];
}

/** Country level CPI (geo data ho tab). $rows = [cc => ['cost'=>, 'conversions'=>]] (3 complete days), $targets = [cc => cpi target]. */
function gr_country_cpi(string $cid, string $camp, string $name, string $cur, array $rows, array $targets, array $th): array
{
    $F = [];
    $lim = (float)gr_th($th, 'cpi_over_pct'); $minI = (float)gr_th($th, 'min_installs');
    foreach ($rows as $cc => $r) {
        $t = $targets[$cc] ?? null;
        if ($t === null || $t <= 0 || $r['conversions'] < $minI) continue;
        $cpi = $r['cost'] / $r['conversions'];
        $over = gr_pct($cpi, (float)$t);
        if ($over <= $lim) continue;
        $F[] = gr_f("cpicc|$cid|$camp|$cc", 'cpi_high', 'WARNING', 'CONFIRMED', [
            'title' => "\"$name\" CPI in $cc is " . gr_pcts($over) . ' above target', 'title_hi' => "\"$name\" ka $cc CPI target se " . gr_pcts($over) . ' upar',
            'what' => "CPI in $cc: " . gr_money($cpi, $cur) . ' (3 complete days, ' . number_format($r['conversions'], 0) . ' installs) vs target ' . gr_money((float)$t, $cur) . '.',
            'what_hi' => "$cc ka CPI: " . gr_money($cpi, $cur) . ' (3 poore din), target ' . gr_money((float)$t, $cur) . '.',
            'metrics' => ['country' => $cc, 'cpi' => gr_r($cpi), 'cpi_target' => (float)$t, 'diff_pct' => gr_r($over, 1), 'installs' => gr_r($r['conversions'], 1), 'currency' => $cur],
            'causes' => ['Country is more expensive than the target', 'Low volume in this country', 'Creative fit for this market'],
            'next' => 'Review this country\'s share of spend in Google Ads and decide manually.', 'next_hi' => 'Is country ka spend share Google Ads me dekho aur khud faisla lo.',
            'evidence' => 'geographic_view (read-only script)', 'country' => $cc,
        ]);
    }
    return $F;
}

function gr_cid(string $cid): string { return strlen($cid) === 10 ? substr($cid, 0, 3) . '-' . substr($cid, 3, 3) . '-' . substr($cid, 6) : $cid; }
