<?php
/**
 * AI Cost & Usage Manager: har paid kaam ka hisaab + budget ka pehra.
 *
 * Har module paid call se PEHLE cost_gate() aur BAAD me cost_claude() / cost_log() bulata hai:
 *   cost_ctx(['module' => 'health', 'pkg' => $pkg, 'task' => 'investigate', 'ref' => 'ISS-12', 'critical' => false]);
 *   if (!cost_gate()) return null;          // budget / daily cap / circuit breaker
 *   ... call ...
 *   cost_claude($responseJson, $ok);        // tokens x list price = ESTIMATE
 *
 * Modules: analytics (alerts.php), health (Play Console AI + auto fix), gads (Google Ads monitor), research (Research Studio).
 * Budget khatam: non-critical kaam ruk jaata hai (cost_paused me likha), critical kaam (urgent alert ka AI) reserve se chalta hai
 * jab tak total budget + reserve bhi khatam na ho. Rules / scans / notifications kabhi nahi rukte (woh free hain, yahan aate hi nahi).
 * Cloud budget alerts sirf alarm hote hain; asli rok yahi file hai.
 *
 * Paise USD me (provider USD me bill karte hain); app ₹ bhi dikhata hai (config inr_per_usd).
 */
declare(strict_types=1);
require_once __DIR__ . '/common.php';

const COST_MODULES = ['analytics' => 'App Analytics', 'health' => 'Play Console AI & Auto Fix', 'gads' => 'Google Ads AI Monitoring', 'research' => 'Research & Development Studio'];

/** USD per 1M tokens: input, output, cache read, cache write (5 min). Anthropic list price, 2026-10. */
const COST_CLAUDE_PRICES = [
    'claude-opus-5-5'   => [4.0, 20.0, 0.20, 5.0],
    'claude-opus-5'     => [5.0, 25.0, 0.50, 6.25],
    'claude-opus-4-8'   => [5.0, 25.0, 0.50, 6.25],
    'claude-opus-4-7'   => [5.0, 25.0, 0.50, 6.25],
    'claude-opus-4-6'   => [5.0, 25.0, 0.50, 6.25],
    'claude-fable-5-1'  => [10.0, 50.0, 0.25, 12.5],
    'claude-fable-5'    => [10.0, 50.0, 0.25, 12.5],
    'claude-sonnet-5-5' => [2.0, 10.0, 0.20, 2.5],
    'claude-sonnet-5'   => [2.0, 10.0, 0.20, 2.5],
    'claude-sonnet-4-6' => [3.0, 15.0, 0.30, 3.75],
    'claude-haiku-5-5'  => [0.10, 0.50, 0.01, 0.125],
    'claude-haiku-4-5'  => [1.0, 5.0, 0.10, 1.25],
];
const COST_WEB_SEARCH_USD = 0.01;        // $10 per 1,000 searches
const COST_BREAKER_FAILS = 3;            // itni lagataar fail -> module ka AI 30 min band (infinite retry nahi)
const COST_BREAKER_SEC = 1800;

function cost_defaults(): array
{
    return [
        'total' => 170.0, 'reserve_pct' => 10,
        'modules' => [
            'analytics' => ['budget' => 15.0, 'cap' => 40],
            'health'    => ['budget' => 80.0, 'cap' => 30, 'fix_cap' => 10],
            'gads'      => ['budget' => 20.0, 'cap' => 40],
            'research'  => ['budget' => 55.0, 'cap' => 60],
        ],
        'fixed' => [['name' => 'Hosting + database', 'usd' => 0.0]],   // har mahine ka fixed bill (Sagar app se bharega)
        'actions_usd_min' => 0.006,                                    // private repo Linux runner; public repo = 0
        'warn_email' => true,
    ];
}

/** Settings (kv cost_settings) defaults ke upar merge. */
function cost_settings(bool $reload = false): array
{
    static $c = null;
    if ($c !== null && !$reload) return $c;
    $d = cost_defaults();
    $s = json_decode((string)(kv_get('cost_settings') ?? ''), true);
    if (!is_array($s)) return $c = $d;
    foreach (['total', 'reserve_pct', 'actions_usd_min', 'warn_email'] as $k) if (array_key_exists($k, $s)) $d[$k] = $s[$k];
    foreach (COST_MODULES as $m => $_) foreach ((array)($s['modules'][$m] ?? []) as $k => $v) $d['modules'][$m][$k] = $v;
    if (isset($s['fixed']) && is_array($s['fixed'])) $d['fixed'] = array_values($s['fixed']);
    // JSON se aaye numbers ko sahi type do (10.0 JSON me 10 ban jaata hai)
    $d['total'] = (float)$d['total']; $d['reserve_pct'] = (int)$d['reserve_pct']; $d['actions_usd_min'] = (float)$d['actions_usd_min']; $d['warn_email'] = (bool)$d['warn_email'];
    foreach ($d['modules'] as $m => $v) { $d['modules'][$m]['budget'] = (float)$v['budget']; $d['modules'][$m]['cap'] = (int)$v['cap']; if (isset($v['fix_cap'])) $d['modules'][$m]['fix_cap'] = (int)$v['fix_cap']; }
    foreach ($d['fixed'] as $i => $f) $d['fixed'][$i] = ['name' => (string)($f['name'] ?? ''), 'usd' => (float)($f['usd'] ?? 0)];
    return $c = $d;
}

function cost_save_settings(array $in): array
{
    $cur = cost_settings();
    $num = fn($v, float $lo, float $hi) => max($lo, min($hi, round((float)$v, 2)));
    if (isset($in['total'])) $cur['total'] = $num($in['total'], 0, 100000);
    if (isset($in['reserve_pct'])) $cur['reserve_pct'] = (int)$num($in['reserve_pct'], 0, 50);
    if (isset($in['actions_usd_min'])) $cur['actions_usd_min'] = $num($in['actions_usd_min'], 0, 1);
    if (isset($in['warn_email'])) $cur['warn_email'] = (bool)$in['warn_email'];
    foreach (COST_MODULES as $m => $_) {
        foreach (['budget' => [0, 100000], 'cap' => [0, 5000], 'fix_cap' => [0, 500]] as $k => [$lo, $hi]) {
            if (isset($in['modules'][$m][$k])) $cur['modules'][$m][$k] = $k === 'budget' ? $num($in['modules'][$m][$k], $lo, $hi) : (int)$num($in['modules'][$m][$k], $lo, $hi);
        }
    }
    if (isset($in['fixed']) && is_array($in['fixed'])) {
        $f = [];
        foreach (array_slice($in['fixed'], 0, 10) as $r) {
            $n = trim(mb_substr((string)($r['name'] ?? ''), 0, 60));
            if ($n !== '') $f[] = ['name' => $n, 'usd' => $num($r['usd'] ?? 0, 0, 100000)];
        }
        $cur['fixed'] = $f;
    }
    kv_set('cost_settings', json_encode($cur, JSON_UNESCAPED_UNICODE));
    kv_set('cost_settings_at', (string)time());
    return cost_settings(true);
}

function cost_schema(PDO $pdo): void
{
    static $done = false;
    if ($done) return;
    $done = true;
    if (kv_get('cost_schema') === '1') return;
    $pdo->exec("CREATE TABLE IF NOT EXISTS cost_events (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, at DATETIME NOT NULL, day DATE NOT NULL,
        module VARCHAR(20) NOT NULL, pkg VARCHAR(190) NOT NULL DEFAULT '', task VARCHAR(40) NOT NULL DEFAULT '', ref VARCHAR(80) NOT NULL DEFAULT '',
        provider VARCHAR(24) NOT NULL, model VARCHAR(60) NOT NULL DEFAULT '',
        in_tok INT UNSIGNED NOT NULL DEFAULT 0, out_tok INT UNSIGNED NOT NULL DEFAULT 0, cache_r INT UNSIGNED NOT NULL DEFAULT 0, cache_w INT UNSIGNED NOT NULL DEFAULT 0,
        units DECIMAL(14,3) NOT NULL DEFAULT 0, usd DECIMAL(14,6) NOT NULL DEFAULT 0, ok TINYINT NOT NULL DEFAULT 1, note VARCHAR(255) NOT NULL DEFAULT '',
        ukey VARCHAR(120) NULL, UNIQUE KEY ukey (ukey), KEY day (day), KEY mday (module, day), KEY pkg (pkg, day)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    $pdo->exec("CREATE TABLE IF NOT EXISTS cost_billed (
        day DATE NOT NULL, provider VARCHAR(24) NOT NULL, item VARCHAR(120) NOT NULL, usd DECIMAL(14,4) NOT NULL DEFAULT 0, synced_at DATETIME NOT NULL,
        PRIMARY KEY (day, provider, item)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    $pdo->exec("CREATE TABLE IF NOT EXISTS cost_paused (
        id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, at DATETIME NOT NULL, last_at DATETIME NOT NULL, module VARCHAR(20) NOT NULL, task VARCHAR(40) NOT NULL DEFAULT '',
        why VARCHAR(255) NOT NULL DEFAULT '', hits INT UNSIGNED NOT NULL DEFAULT 1, pkey VARCHAR(120) NOT NULL, UNIQUE KEY pkey (pkey), KEY at (at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    kv_set('cost_schema', '1');
}

// ================================================================ context (kaun sa module / app / task abhi paisa kharch kar raha hai)
function cost_ctx(?array $set = null): array
{
    static $ctx = ['module' => 'analytics', 'pkg' => '', 'task' => '', 'ref' => '', 'critical' => false];
    $prev = $ctx;
    if ($set !== null) $ctx = array_merge(['module' => 'analytics', 'pkg' => '', 'task' => '', 'ref' => '', 'critical' => false], $set);
    return $prev;
}

function cost_month_start(): string { return (new DateTime('now', tz()))->format('Y-m-01'); }
function cost_today(): string { return (new DateTime('now', tz()))->format('Y-m-d'); }

/** Is mahine ka kharcha (logged estimate) module ke hisaab se. */
function cost_month_by_module(PDO $pdo): array
{
    $out = array_fill_keys(array_keys(COST_MODULES), 0.0);
    $st = $pdo->prepare('SELECT module, SUM(usd) u FROM cost_events WHERE day >= ? GROUP BY module');
    $st->execute([cost_month_start()]);
    foreach ($st->fetchAll() as $r) $out[$r['module']] = (float)$r['u'];
    return $out;
}

/** Fixed bills (hosting) ka is mahine tak ka hissa: pura mahina gina jaata hai, kyunki woh bill hona hi hai. */
function cost_fixed_month(): float
{
    $t = 0.0;
    foreach (cost_settings()['fixed'] as $f) $t += (float)$f['usd'];
    return $t;
}

// ================================================================ gate: paid call se pehle
/**
 * true = chalao. false = ruko (wajah cost_last_block() me, aur cost_paused me ek row).
 * critical = sasta aur zaroori (urgent alert ka AI, naya P0 crash): module budget khatam ho tab bhi reserve se chalta hai.
 */
function cost_gate(?string $module = null, ?bool $critical = null, ?string $task = null): bool
{
    $c = cost_ctx();
    $module = $module ?? $c['module'];
    $critical = $critical ?? (bool)$c['critical'];
    $task = $task ?? $c['task'];
    try {
        $pdo = db();
        cost_schema($pdo);
        $why = cost_block_reason($pdo, $module, $critical, $task);
    } catch (Throwable $e) {
        error_log('mfc cost gate: ' . $e->getMessage());
        return true;   // hisaab ki galti par monitoring band nahi karte
    }
    if ($why === null) return true;
    cost_last_block($why);
    try {
        $pdo->prepare('INSERT INTO cost_paused (at, last_at, module, task, why, pkey) VALUES (UTC_TIMESTAMP(), UTC_TIMESTAMP(), ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE last_at = UTC_TIMESTAMP(), hits = hits + 1, why = VALUES(why)')
            ->execute([$module, mb_substr($task, 0, 40), mb_substr($why, 0, 255), $module . '|' . $task . '|' . cost_today()]);
    } catch (Throwable $e) { error_log('mfc cost paused: ' . $e->getMessage()); }
    return false;
}

function cost_last_block(?string $set = null): ?string
{
    static $w = null;
    if ($set !== null) $w = $set;
    return $w;
}

function cost_block_reason(PDO $pdo, string $module, bool $critical, string $task): ?string
{
    $s = cost_settings();
    $m = $s['modules'][$module] ?? ['budget' => 0, 'cap' => 0];
    // circuit breaker: lagataar fail -> thodi der ruko (yeh critical par bhi lagta hai, warna fail call baar baar)
    $until = (int)(kv_get("cost_brk_until_$module") ?? 0);
    if ($until > time()) return 'paused after ' . COST_BREAKER_FAILS . ' failed AI calls in a row, retry after ' . gmdate('H:i', $until) . ' UTC';
    $override = (int)(kv_get("cost_override_$module") ?? 0) > time();

    $byMod = cost_month_by_module($pdo);
    $total = array_sum($byMod) + cost_fixed_month();
    $budgetTotal = (float)$s['total'];
    $reserve = $budgetTotal * (float)$s['reserve_pct'] / 100;
    if ($budgetTotal > 0 && $total >= $budgetTotal + $reserve) return 'monthly budget and reserve used up ($' . round($total, 2) . ' of $' . round($budgetTotal + $reserve, 2) . ')';
    if ($critical) return null;
    if ($override) return null;
    if ($budgetTotal > 0 && $total >= $budgetTotal) return 'total monthly budget used up ($' . round($total, 2) . ' of $' . round($budgetTotal, 2) . ')';
    if ((float)$m['budget'] > 0 && $byMod[$module] >= (float)$m['budget']) return COST_MODULES[$module] . ' budget used up ($' . round($byMod[$module], 2) . ' of $' . round((float)$m['budget'], 2) . ')';
    if ((float)$m['budget'] <= 0) return COST_MODULES[$module] . ' budget is $0';
    // daily caps
    if ($task === 'ai_fix' || $task === 'task') {
        $cap = (int)($m['fix_cap'] ?? 0);
        if ($cap > 0 && cost_fix_count() >= $cap) return "daily limit of $cap AI fix / task runs reached";
    } elseif ((int)$m['cap'] > 0) {
        $n = (int)cost_q1($pdo, "SELECT COUNT(*) FROM cost_events WHERE module = ? AND day = ? AND provider = 'anthropic'", [$module, cost_today()]);
        if ($n >= (int)$m['cap']) return "daily limit of {$m['cap']} AI calls reached";
    }
    return null;
}

/** Aaj kitne AI fix / task worker dispatch hue (h_fix_start har dispatch par cost_fix_count(true) bulata hai). */
function cost_fix_count(bool $add = false): int
{
    $k = 'cost_fixes_' . cost_today();
    $n = (int)(kv_get($k) ?? 0);
    if ($add) kv_set($k, (string)++$n);
    return $n;
}

function cost_q1(PDO $pdo, string $sql, array $p = [])
{
    $st = $pdo->prepare($sql);
    $st->execute($p);
    $v = $st->fetchColumn();
    return $v === false ? null : $v;
}

// ================================================================ log: paid call ke baad
/** Claude Messages API ka jawab (decoded) -> tokens x price. $ok = jawab kaam ka tha ya nahi (breaker ke liye). */
function cost_claude(?array $resp, bool $ok, string $requestedModel = ''): void
{
    $c = cost_ctx();
    $u = is_array($resp) ? (array)($resp['usage'] ?? []) : [];
    cost_breaker($c['module'], $ok);
    if (!$u) return;   // HTTP error: koi token bill nahi hua
    $model = (string)($resp['model'] ?? $requestedModel);
    $in = (int)($u['input_tokens'] ?? 0); $out = (int)($u['output_tokens'] ?? 0);
    $cr = (int)($u['cache_read_input_tokens'] ?? 0); $cw = (int)($u['cache_creation_input_tokens'] ?? 0);
    $searches = (int)($u['server_tool_use']['web_search_requests'] ?? 0);
    $usd = cost_claude_usd($model, $in, $out, $cr, $cw) + $searches * COST_WEB_SEARCH_USD;
    cost_log($c['module'], 'anthropic', $usd, ['pkg' => $c['pkg'], 'task' => $c['task'], 'ref' => $c['ref'], 'model' => $model,
        'in' => $in, 'out' => $out, 'cache_r' => $cr, 'cache_w' => $cw, 'units' => $searches, 'ok' => $ok]);
}

function cost_claude_usd(string $model, int $in, int $out, int $cr = 0, int $cw = 0): float
{
    $p = COST_CLAUDE_PRICES[$model] ?? null;
    if ($p === null) foreach (COST_CLAUDE_PRICES as $k => $v) if (str_starts_with($model, $k)) { $p = $v; break; }
    $p = $p ?? COST_CLAUDE_PRICES['claude-opus-5-5'];
    return ($in * $p[0] + $out * $p[1] + $cr * $p[2] + $cw * $p[3]) / 1e6;
}

/**
 * Koi bhi kharcha likho. provider: anthropic | claude_code | github_actions | image | video | research_api | other.
 * o: pkg, task, ref, model, in, out, cache_r, cache_w, units, ok, note, ukey (dobara na likha jaaye).
 */
function cost_log(string $module, string $provider, float $usd, array $o = []): void
{
    try {
        $pdo = db();
        cost_schema($pdo);
        if (!isset(COST_MODULES[$module])) $module = 'analytics';
        $pdo->prepare('INSERT IGNORE INTO cost_events (at, day, module, pkg, task, ref, provider, model, in_tok, out_tok, cache_r, cache_w, units, usd, ok, note, ukey)
            VALUES (UTC_TIMESTAMP(),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)')->execute([
            cost_today(), $module, mb_substr((string)($o['pkg'] ?? ''), 0, 190), mb_substr((string)($o['task'] ?? ''), 0, 40), mb_substr((string)($o['ref'] ?? ''), 0, 80),
            mb_substr($provider, 0, 24), mb_substr((string)($o['model'] ?? ''), 0, 60), (int)($o['in'] ?? 0), (int)($o['out'] ?? 0), (int)($o['cache_r'] ?? 0), (int)($o['cache_w'] ?? 0),
            (float)($o['units'] ?? 0), max(0.0, $usd), !empty($o['ok']) || !array_key_exists('ok', $o) ? 1 : 0, mb_substr((string)($o['note'] ?? ''), 0, 255),
            isset($o['ukey']) ? mb_substr((string)$o['ukey'], 0, 120) : null]);
    } catch (Throwable $e) { error_log('mfc cost log: ' . $e->getMessage()); }
}

/** Lagataar fail gino; COST_BREAKER_FAILS par module 30 min ke liye ruk jaata hai. */
function cost_breaker(string $module, bool $ok): void
{
    $k = "cost_brk_fails_$module";
    if ($ok) { if ((int)(kv_get($k) ?? 0) !== 0) kv_set($k, '0'); return; }
    $n = (int)(kv_get($k) ?? 0) + 1;
    kv_set($k, (string)$n);
    if ($n >= COST_BREAKER_FAILS) { kv_set("cost_brk_until_$module", (string)(time() + COST_BREAKER_SEC)); kv_set($k, '0'); }
}

// ================================================================ summary (app ke liye)
function cost_range_days(string $range): string
{
    $d = new DateTime('now', tz());
    return match ($range) {
        'today' => $d->format('Y-m-d'),
        '7d' => (clone $d)->modify('-6 day')->format('Y-m-d'),
        default => $d->format('Y-m-01'),
    };
}

function cost_summary(PDO $pdo, string $range = 'month'): array
{
    cost_schema($pdo);
    $s = cost_settings();
    $today = cost_today(); $mStart = cost_month_start();
    $now = new DateTime('now', tz());
    $sum = fn(string $from, ?string $mod = null) => (float)cost_q1($pdo, 'SELECT IFNULL(SUM(usd),0) FROM cost_events WHERE day >= ?' . ($mod ? ' AND module = ?' : ''), $mod ? [$from, $mod] : [$from]);
    $fixed = cost_fixed_month();
    $dim = (int)$now->format('t');
    $elapsedDays = ((int)$now->format('j') - 1) + ((int)$now->format('G') * 60 + (int)$now->format('i')) / 1440;
    $var = $sum($mStart);
    $monthUsd = $var + $fixed;
    $last7 = $sum((clone $now)->modify('-6 day')->format('Y-m-d'));
    // forecast: is mahine ki raftaar aur last 7 din ki raftaar me jo zyada ho (kam aankne se behtar)
    $rate = max($elapsedDays > 0.25 ? $var / $elapsedDays : 0, $last7 / 7);
    $forecast = $var + $rate * max(0, $dim - $elapsedDays) + $fixed;

    $byMod = cost_month_by_module($pdo);
    $modules = [];
    foreach (COST_MODULES as $k => $name) {
        $cfg = $s['modules'][$k];
        $r = $pdo->prepare("SELECT provider, task, COUNT(*) n, COUNT(DISTINCT ref) refs, SUM(units) units FROM cost_events WHERE module = ? AND day >= ? GROUP BY provider, task");
        $r->execute([$k, $mStart]);
        $counts = [];
        foreach ($r->fetchAll() as $x) $counts[] = ['provider' => $x['provider'], 'task' => $x['task'], 'n' => (int)$x['n'], 'refs' => (int)$x['refs'], 'units' => (float)$x['units']];
        $why = cost_block_reason($pdo, $k, false, '');
        $modules[] = ['key' => $k, 'name' => $name, 'budget' => (float)$cfg['budget'], 'cap' => (int)$cfg['cap'], 'fix_cap' => (int)($cfg['fix_cap'] ?? 0),
            'month_usd' => round($byMod[$k], 4), 'today_usd' => round($sum($today, $k), 4),
            'calls_today' => (int)cost_q1($pdo, "SELECT COUNT(*) FROM cost_events WHERE module = ? AND day = ? AND provider = 'anthropic'", [$k, $today]),
            'pct' => (float)$cfg['budget'] > 0 ? round($byMod[$k] / (float)$cfg['budget'], 4) : null,
            'paused' => $why !== null, 'paused_why' => $why, 'override_until' => (int)(kv_get("cost_override_$k") ?? 0), 'counts' => $counts];
    }
    $from = cost_range_days($range);
    $apps = $pdo->prepare("SELECT pkg, SUM(usd) usd, COUNT(*) n, COUNT(DISTINCT CONCAT(task,'|',ref)) tasks, GROUP_CONCAT(DISTINCT task) kinds FROM cost_events WHERE day >= ? AND pkg <> '' GROUP BY pkg ORDER BY usd DESC LIMIT 60");
    $apps->execute([$from]);
    $appRows = [];
    $names = [];
    try { foreach (apps() as $p => $a) $names[$p] = (string)($a['name'] ?? $p); } catch (Throwable $e) { }
    foreach ($apps->fetchAll() as $a) $appRows[] = ['pkg' => $a['pkg'], 'name' => $names[$a['pkg']] ?? $a['pkg'], 'usd' => round((float)$a['usd'], 4), 'calls' => (int)$a['n'], 'tasks' => (int)$a['tasks'], 'kinds' => (string)$a['kinds']];
    $noApp = (float)cost_q1($pdo, "SELECT IFNULL(SUM(usd),0) FROM cost_events WHERE day >= ? AND pkg = ''", [$from]);

    $t = $pdo->prepare("SELECT module, task, ref, MAX(pkg) pkg, GROUP_CONCAT(DISTINCT provider) providers, GROUP_CONCAT(DISTINCT model) models, COUNT(*) calls,
        SUM(in_tok) in_tok, SUM(out_tok) out_tok, SUM(units) units, SUM(usd) usd, MIN(ok) all_ok, MAX(at) last_at, MAX(note) note
        FROM cost_events WHERE day >= ? GROUP BY module, task, ref ORDER BY usd DESC LIMIT 80");
    $t->execute([$from]);
    $tasks = [];
    foreach ($t->fetchAll() as $x) $tasks[] = ['module' => $x['module'], 'task' => $x['task'], 'ref' => $x['ref'], 'pkg' => $x['pkg'], 'app' => $x['pkg'] !== '' ? ($names[$x['pkg']] ?? $x['pkg']) : '',
        'providers' => (string)$x['providers'], 'models' => (string)$x['models'], 'calls' => (int)$x['calls'], 'in_tok' => (int)$x['in_tok'], 'out_tok' => (int)$x['out_tok'],
        'units' => (float)$x['units'], 'usd' => round((float)$x['usd'], 4), 'ok' => (int)$x['all_ok'] === 1, 'last_at' => $x['last_at'], 'note' => (string)$x['note']];

    $prov = [];
    $pv = $pdo->prepare('SELECT provider, SUM(usd) u FROM cost_events WHERE day >= ? GROUP BY provider');
    $pv->execute([$mStart]);
    foreach ($pv->fetchAll() as $x) $prov[$x['provider']] = round((float)$x['u'], 4);
    $billed = [];
    $bq = $pdo->prepare('SELECT provider, SUM(usd) u, MAX(synced_at) at FROM cost_billed WHERE day >= ? GROUP BY provider');
    $bq->execute([$mStart]);
    foreach ($bq->fetchAll() as $x) $billed[] = ['provider' => $x['provider'], 'usd' => round((float)$x['u'], 4), 'synced_at' => $x['at']];

    $paused = $pdo->query("SELECT module, task, why, hits, at, last_at FROM cost_paused WHERE last_at >= UTC_TIMESTAMP() - INTERVAL 3 DAY ORDER BY last_at DESC LIMIT 30")->fetchAll();
    $budget = (float)$s['total'];
    return ['ok' => true, 'range' => $range, 'month' => $now->format('Y-m'), 'inr_per_usd' => (float)(cfg('inr_per_usd') ?? 95),
        'today_usd' => round($sum($today), 4), 'd7_usd' => round($last7, 4), 'month_usd' => round($monthUsd, 4), 'variable_usd' => round($var, 4), 'fixed_usd' => round($fixed, 2),
        'budget_usd' => $budget, 'reserve_pct' => (int)$s['reserve_pct'], 'remaining_usd' => round($budget - $monthUsd, 4), 'forecast_usd' => round($forecast, 2),
        'pct' => $budget > 0 ? round($monthUsd / $budget, 4) : null,
        'modules' => $modules, 'apps' => $appRows, 'no_app_usd' => round($noApp, 4), 'tasks' => $tasks,
        'estimate_by_provider' => $prov, 'billed' => $billed, 'fixed' => $s['fixed'], 'paused' => $paused,
        'settings' => $s, 'keys' => cost_keys_status(),
        'warn_levels' => cost_warn_state()];
}

function cost_keys_status(): array
{
    $hint = fn(?string $k) => $k ? substr($k, 0, 12) . '…' . substr($k, -4) : '';
    return ['admin_key' => $hint(kv_get('cost_admin_key')), 'gh_token' => $hint(kv_get('cost_gh_token')), 'gh_user' => (string)(kv_get('cost_gh_user') ?? ''),
        'anthropic_sync' => kv_get('cost_sync_anthropic'), 'github_sync' => kv_get('cost_sync_github'), 'sync_error' => kv_get('cost_sync_error')];
}

// ================================================================ warnings 70 / 90 / 100%
function cost_warn_state(): array
{
    $j = json_decode((string)(kv_get('cost_warned') ?? ''), true);
    return is_array($j) && ($j['month'] ?? '') === cost_month_start() ? $j : ['month' => cost_month_start(), 'sent' => []];
}

/** Har module + total: 70/90/100% par ek baar (per mahina) app notification (app_alerts) + email. */
function cost_warn_check(PDO $pdo): int
{
    cost_schema($pdo);
    $s = cost_settings();
    $state = cost_warn_state();
    $byMod = cost_month_by_module($pdo);
    $items = [];
    foreach (COST_MODULES as $k => $name) $items[$k] = [$name, $byMod[$k], (float)$s['modules'][$k]['budget']];
    $items['total'] = ['All modules', array_sum($byMod) + cost_fixed_month(), (float)$s['total']];
    $n = 0;
    foreach ($items as $k => [$name, $spent, $budget]) {
        if ($budget <= 0) continue;
        $pct = $spent / $budget;
        foreach ([100, 90, 70] as $lvl) {
            if ($pct * 100 < $lvl) continue;
            $sk = "$k|$lvl";
            if (!empty($state['sent'][$sk])) break;
            $state['sent'][$sk] = time();
            // neeche wale levels bhi "bhej diye" maan lo, warna 100% par teen alerts ek saath
            foreach ([70, 90] as $low) if ($low < $lvl) $state['sent']["$k|$low"] = $state['sent']["$k|$low"] ?? time();
            $inr = (float)(cfg('inr_per_usd') ?? 95);
            $money = '$' . number_format($spent, 2) . ' (₹' . number_format($spent * $inr) . ') of $' . number_format($budget, 2);
            $stop = $k === 'total' ? 'All non-critical AI research, development and testing jobs pause now; critical monitoring keeps running from the reserve.'
                : 'Non-critical jobs of this module pause now; rules, scans and alerts keep running.';
            $stopHi = $k === 'total' ? 'Saare non-critical AI research, development aur testing kaam ab ruk gaye; zaroori monitoring reserve se chalti rahegi.'
                : 'Is module ke non-critical kaam ab ruk gaye; rules, scans aur alerts chalte rahenge.';
            $title = [$name . " budget $lvl% used", $name . " ka budget $lvl% kharch"];
            $body = [$money . ' this month.' . ($lvl >= 100 ? ' ' . $stop : ' Forecast and per-task costs are in AI Cost & Usage.'),
                $money . ' is mahine.' . ($lvl >= 100 ? ' ' . $stopHi : ' Forecast aur har task ka kharcha AI Cost & Usage me.')];
            cost_notify($pdo, $lvl >= 100 ? 3 : ($lvl >= 90 ? 2 : 1), $k, $title, $body, 'cost|' . cost_month_start() . "|$k|$lvl", ['module' => $k, 'pct' => round($pct * 100), 'spent_usd' => round($spent, 2), 'budget_usd' => $budget]);
            if (!empty($s['warn_email'])) cost_email("[MFC Cost] {$title[0]}", "{$body[0]}\n\nMFC Dash > AI Cost & Usage");
            $n++;
            break;
        }
    }
    kv_set('cost_warned', json_encode($state));
    return $n;
}

function cost_notify(PDO $pdo, int $level, string $module, array $title, array $body, string $key, array $metrics): void
{
    try {
        if (!$pdo->query("SHOW TABLES LIKE 'app_alerts'")->fetch()) return;
        $st = $pdo->prepare('SELECT COUNT(*) FROM app_alerts WHERE akey = ?');
        $st->execute([$key]);
        if ((int)$st->fetchColumn() > 0) return;
        $pdo->prepare('INSERT INTO app_alerts (akey, level, rule, pkg, account, campaign, title, body, title_hi, body_hi, ai, metrics, fired, created_at, updated_at)
            VALUES (?,?,?,\'\',\'\',\'\',?,?,?,?,\'\',?,1,UTC_TIMESTAMP(),UTC_TIMESTAMP())')
            ->execute([mb_substr($key, 0, 190), $level, 'cost_' . $module, mb_substr($title[0], 0, 255), mb_substr($body[0], 0, 2000), mb_substr($title[1], 0, 255), mb_substr($body[1], 0, 2000), json_encode($metrics)]);
    } catch (Throwable $e) { error_log('mfc cost notify: ' . $e->getMessage()); }
}

/** AI Health module ka email setup (health_config email_to / SMTP) reuse; woh na ho to chup. */
function cost_email(string $subject, string $text): void
{
    try {
        if (!function_exists('h_mail_send') && is_file(__DIR__ . '/health.php')) require_once __DIR__ . '/health.php';
        if (!function_exists('h_mail_send') || !function_exists('hcfg')) return;
        $to = trim((string)(hcfg('email_to') ?? ''));
        if ($to === '' || str_contains($to, 'example.com')) return;
        h_mail_send($to, $subject, $text . "\n\n— MFC Dash · AI Cost & Usage");
    } catch (Throwable $e) { error_log('mfc cost email: ' . $e->getMessage()); }
}

// ================================================================ actual billed: Anthropic Cost API + GitHub billing (read-only)
/** cron se: har 6 ghante. Keys na ho to kuch nahi (sab ESTIMATE hi dikhega). */
function cost_sync_billed(PDO $pdo, bool $force = false): array
{
    cost_schema($pdo);
    $out = [];
    if (!$force && time() - (int)(kv_get('cost_sync_last') ?? 0) < 21600) return ['skipped' => true];
    kv_set('cost_sync_last', (string)time());
    $err = [];
    $admin = trim((string)(kv_get('cost_admin_key') ?? ''));
    if ($admin !== '') {
        try { $out['anthropic'] = cost_sync_anthropic($pdo, $admin); kv_set('cost_sync_anthropic', gmdate('Y-m-d H:i') . ' UTC'); }
        catch (Throwable $e) { $err[] = 'Anthropic: ' . $e->getMessage(); }
    }
    $gh = trim((string)(kv_get('cost_gh_token') ?? ''));
    $user = trim((string)(kv_get('cost_gh_user') ?? ''));
    if ($gh !== '' && $user !== '') {
        try { $out['github'] = cost_sync_github($pdo, $gh, $user); kv_set('cost_sync_github', gmdate('Y-m-d H:i') . ' UTC'); }
        catch (Throwable $e) { $err[] = 'GitHub: ' . $e->getMessage(); }
    }
    kv_set('cost_sync_error', $err ? mb_substr(implode(' · ', $err), 0, 500) : '');
    return $out;
}

/** GET /v1/organizations/cost_report: daily buckets, amount = USD cents (decimal string). */
function cost_sync_anthropic(PDO $pdo, string $key): float
{
    $from = (new DateTime(cost_month_start(), new DateTimeZone('UTC')))->format('Y-m-d\T00:00:00\Z');
    $to = (new DateTime('tomorrow', new DateTimeZone('UTC')))->format('Y-m-d\T00:00:00\Z');
    $page = null; $total = 0.0; $rows = [];
    for ($i = 0; $i < 10; $i++) {
        $url = 'https://api.anthropic.com/v1/organizations/cost_report?starting_at=' . rawurlencode($from) . '&ending_at=' . rawurlencode($to) . '&group_by[]=description&limit=31'
            . ($page ? '&page=' . rawurlencode($page) : '');
        [$code, $resp] = http_json('GET', $url, null, ['x-api-key: ' . $key, 'anthropic-version: 2023-06-01', 'User-Agent: MFC-Dash/1.0'], 40);
        if ($code !== 200) {
            $j = json_decode((string)$resp, true);
            throw new RuntimeException("HTTP $code " . mb_substr((string)($j['error']['message'] ?? ''), 0, 150));
        }
        $j = json_decode((string)$resp, true) ?: [];
        foreach ((array)($j['data'] ?? []) as $b) {
            $day = substr((string)($b['starting_at'] ?? ''), 0, 10);
            foreach ((array)($b['results'] ?? []) as $r) {
                $usd = (float)($r['amount'] ?? 0) / 100;
                $item = mb_substr(trim((string)($r['description'] ?? $r['model'] ?? 'usage')) ?: 'usage', 0, 120);
                $rows["$day|$item"] = ($rows["$day|$item"] ?? 0) + $usd;
                $total += $usd;
            }
        }
        if (empty($j['has_more']) || empty($j['next_page'])) break;
        $page = (string)$j['next_page'];
    }
    $pdo->prepare("DELETE FROM cost_billed WHERE provider = 'anthropic' AND day >= ?")->execute([cost_month_start()]);
    $ins = $pdo->prepare("INSERT INTO cost_billed (day, provider, item, usd, synced_at) VALUES (?, 'anthropic', ?, ?, UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE usd = VALUES(usd), synced_at = UTC_TIMESTAMP()");
    foreach ($rows as $k => $usd) { [$day, $item] = explode('|', $k, 2); if ($day !== '') $ins->execute([$day, $item, round($usd, 4)]); }
    return round($total, 4);
}

/** GitHub enhanced billing: GET /users/{user}/settings/billing/usage (token: "Plan" read). Sirf Actions ki lines. */
function cost_sync_github(PDO $pdo, string $token, string $user): float
{
    $now = new DateTime('now', new DateTimeZone('UTC'));
    $url = 'https://api.github.com/users/' . rawurlencode($user) . '/settings/billing/usage?year=' . $now->format('Y') . '&month=' . (int)$now->format('n');
    [$code, $resp] = http_json('GET', $url, null, ['Authorization: Bearer ' . $token, 'Accept: application/vnd.github+json', 'X-GitHub-Api-Version: 2022-11-28', 'User-Agent: MFC-Dash'], 40);
    if ($code !== 200) throw new RuntimeException("HTTP $code");
    $j = json_decode((string)$resp, true) ?: [];
    $rows = []; $total = 0.0;
    foreach ((array)($j['usageItems'] ?? []) as $it) {
        if (stripos((string)($it['product'] ?? ''), 'actions') === false) continue;
        $day = substr((string)($it['date'] ?? ''), 0, 10);
        $item = mb_substr(trim((string)($it['repositoryName'] ?? '') . ' ' . (string)($it['sku'] ?? '')), 0, 120) ?: 'actions';
        $usd = (float)($it['netAmount'] ?? 0);
        $rows["$day|$item"] = ($rows["$day|$item"] ?? 0) + $usd;
        $total += $usd;
    }
    $pdo->prepare("DELETE FROM cost_billed WHERE provider = 'github' AND day >= ?")->execute([$now->format('Y-m-01')]);
    $ins = $pdo->prepare("INSERT INTO cost_billed (day, provider, item, usd, synced_at) VALUES (?, 'github', ?, ?, UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE usd = VALUES(usd), synced_at = UTC_TIMESTAMP()");
    foreach ($rows as $k => $usd) { [$day, $item] = explode('|', $k, 2); if ($day !== '') $ins->execute([$day, $item, round($usd, 4)]); }
    return round($total, 4);
}

/** Cron tick (health cron har 5 min, ya api/cost.php?cron=1): warnings + billed sync + purana data saaf. */
function cost_tick(PDO $pdo): array
{
    cost_schema($pdo);
    $r = ['warned' => cost_warn_check($pdo)];
    try { $r['sync'] = cost_sync_billed($pdo); } catch (Throwable $e) { $r['sync_error'] = $e->getMessage(); }
    if (time() - (int)(kv_get('cost_cleanup') ?? 0) > 86400) {
        kv_set('cost_cleanup', (string)time());
        $pdo->exec('DELETE FROM cost_events WHERE day < CURDATE() - INTERVAL 400 DAY');
        $pdo->exec('DELETE FROM cost_paused WHERE last_at < UTC_TIMESTAMP() - INTERVAL 60 DAY');
        $pdo->prepare("DELETE FROM kv WHERE k LIKE 'cost\\_fixes\\_%' AND k < ?")->execute(['cost_fixes_' . cost_today()]);
    }
    return $r;
}
