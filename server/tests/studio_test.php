<?php
/**
 * AI App Research Studio — tests (DEVELOPMENT ENVIRONMENT, SIMULATED DATA ONLY; Claude API responses are canned fakes from a local fake API, nothing calls the network).
 * Part 1 (no DB): scoring formula, recommendation rule, spec diff, spec cleaning.
 * Part 2 (MySQL, TEST_DB_HOST): economics from simulated dashboard tables, rank job (evidence validation, label downgrade, policy REJECT),
 *   budget block, Gate 1 limits, competitor analysis -> Gate 2, artifact hash conflicts, request changes -> v2 + diff, approve, retries/crash recovery/cancel,
 *   notifications + dedupe, every app view over HTTP.
 * Portfolio (your own apps, pure SQL): eCPM, earnings (ads + payments), Google Ads spend (INR + USD accounts), ROAS, exact (Ads geo report) vs estimated country spend,
 *   unknown-country bucket, deterministic insights, AI context P ids in the rank prompt, own_fit factor, view=mine, cron once a day, schema upgrade 1 -> 2.
 * Owner's server failures: AI schemas without union types (API limit 16; -1 = no evidence, own_fit not asked), economics with cc '' / NULL / 'UNK' / 'in'
 *   (no duplicate key), categories merged by the DB collation (Café/Cafe, Straße/Strase), atomic st_econ save, new temp-table cohort SQL = old SQL (edge days),
 *   no row locks under dashboard writes (READ COMMITTED), fallback to the old SQL without CREATE TEMPORARY TABLES.
 * Daily discovery: notes -> structured findings (2nd AI call, no search; invented S ids dropped), structuring failure / budget keeps the notes, weekly ranking reads
 *   the last 7 days of findings (continued S numbers, citable evidence; busy week capped by whole scans, read scans recorded), view=discovery (latest / id / run,
 *   old format, used_in_ranking = the ranking really read it), overview.discovery.
 * Streaming Claude calls (local fake SSE API, php -S fake_claude.php): SSE accumulator (chunks cut anywhere incl. UTF-8, \r\n, comments, cumulative usage, cut streams, tool input
 *   given in content_block_start), clear failure texts + retry rules (429/529/5xx, overloaded/api_error before output, nothing received; never after output), idle abort, healthy
 *   slow stream not aborted (2 s MySQL wait_timeout kept alive by the in-stream ping), Cancel mid-stream, usage recorded on failure (ok 0), mid-stream fallback (streaming: partial +
 *   continuation; non-streaming: text after the fallback block; pause_turn echo rule keeps the fallback block in place), ST_NO_STREAM non-streaming path, streaming connect failure -> owner's http_json
 *   (6 h), st_work wait_timeout;
 *   ranking salvage at max_tokens and on a stream cut after output (>= 3 complete ideas kept -> PARTIAL, fewer -> FAILED), rank_notes reuse (24 h, FAILED / PARTIAL / BLOCKED /
 *   CANCELLED or the same run's crash-requeued attempt, same categories + countries, evidence still there, the notes chain used once; only missing categories researched again).
 * Live progress (view=live): labelled steps per job (done / on / wait / fail, notes like "9 sources", "reused"), frac only forward inside a step and reset by step(), throttled DB
 *   writes + heartbeat, honest progress from the stream (searches, thinking, complete ideas) really written mid-stream (fake API probes the DB), view=live over HTTP
 *   (15-min window, waiting_for, live cost, can_reuse after a failed ranking), schema upgrade 2 -> 3, old runs without steps.
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
    if ((!str_contains($url, 'api.anthropic.com') && !str_contains($url, '/fake_claude.php')) || !$list) return [0, '', 'network disabled in tests'];
    $next = array_shift($list);
    file_put_contents($q, json_encode($list));
    return [(int)$next[0], is_string($next[1]) ? $next[1] : json_encode($next[1]), ''];
}
PHP, $stub, 1);
// st_ai streams (SSE, curl) to ST_API_URL = local fake Claude API (php -S, same canned queue). Small timeouts keep the stream tests fast.
$FP = 20000 + getmypid() % 1000;   // app views server below: 18000 + pid % 1000 + 7
$stub = str_replace("const MFC_ROOT = __DIR__ . '/..';", "const MFC_ROOT = __DIR__ . '/..';\nconst ST_API_URL = 'http://127.0.0.1:$FP/fake_claude.php';\n"
    . "const ST_STREAM_MAX_SEC = 30, ST_STREAM_IDLE_SEC = 2, ST_STREAM_CHECK_SEC = 1, ST_RETRY_WAIT = 0, ST_LIVE_SEC = 0.5;", $stub);
file_put_contents("$M/lib/common.php", $stub);
foreach (['lib/cost.php', 'lib/health.php', 'lib/studio.php', 'api/studio.php'] as $f) copy("$src/$f", "$M/$f");
$Q = "$T/fake_q.json";
putenv("STUDIO_FAKE_Q=$Q");
@mkdir("$T/fake", 0777, true);
file_put_contents("$T/fake/sse_gen.php", <<<'PHP'
<?php
/** Canned Claude message -> realistic SSE events (tests only): message_start (input usage), ping, per block start + deltas (cut mid-word) + stop, message_delta (stop_reason + output usage), message_stop. */
function fake_sse_events(array $m): array
{
    $e = fn(string $t, array $d): string => "event: $t\ndata: " . json_encode(['type' => $t] + $d, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES) . "\n\n";
    $u = (array)($m['usage'] ?? []);
    $out = [$e('message_start', ['message' => ['id' => $m['id'] ?? 'msg_fake', 'type' => 'message', 'role' => 'assistant', 'model' => $m['model'] ?? 'claude-opus-5-5', 'content' => [],
        'stop_reason' => null, 'stop_sequence' => null, 'usage' => array_diff_key($u, ['output_tokens' => 1, 'server_tool_use' => 1]) + ['output_tokens' => 1]]]), $e('ping', [])];
    foreach (array_values((array)($m['content'] ?? [])) as $i => $b) {
        $t = (string)($b['type'] ?? '');
        $d = fn(array $x): string => $e('content_block_delta', ['index' => $i, 'delta' => $x]);
        if ($t === 'text') {
            $out[] = $e('content_block_start', ['index' => $i, 'content_block' => ['type' => 'text', 'text' => '']]);
            foreach (mb_str_split((string)$b['text'], 7) as $p) $out[] = $d(['type' => 'text_delta', 'text' => $p]);
            foreach ((array)($b['citations'] ?? []) as $c) $out[] = $d(['type' => 'citations_delta', 'citation' => $c]);
        } elseif ($t === 'thinking') {
            $out[] = $e('content_block_start', ['index' => $i, 'content_block' => ['type' => 'thinking', 'thinking' => '', 'signature' => '']]);
            foreach (mb_str_split((string)$b['thinking'], 9) as $p) $out[] = $d(['type' => 'thinking_delta', 'thinking' => $p]);
            $out[] = $d(['type' => 'signature_delta', 'signature' => (string)$b['signature']]);
        } elseif ($t === 'tool_use' || $t === 'server_tool_use') {
            $out[] = $e('content_block_start', ['index' => $i, 'content_block' => array_merge($b, ['input' => new stdClass()])]);
            foreach (str_split(empty($b['input']) ? '' : json_encode($b['input']), 5) as $p) $out[] = $d(['type' => 'input_json_delta', 'partial_json' => $p]);
        } else $out[] = $e('content_block_start', ['index' => $i, 'content_block' => $b]);   // web_search_tool_result, fallback, redacted_thinking, unknown: whole block in start
        if ($i === 0) $out[] = $e('ping', []);
        $out[] = $e('content_block_stop', ['index' => $i]);
    }
    $out[] = $e('message_delta', ['delta' => ['stop_reason' => $m['stop_reason'] ?? 'end_turn', 'stop_sequence' => null], 'usage' => array_intersect_key($u, ['output_tokens' => 1, 'server_tool_use' => 1])]);
    $out[] = $e('message_stop', []);
    return $out;
}
PHP);
file_put_contents("$T/fake/fake_claude.php", <<<'PHP'
<?php
/**
 * FAKE Claude Messages API (tests only, SIMULATED): pops [code, body, options] from the STUDIO_FAKE_Q queue and logs the request like the http_json stub.
 * 200 -> realistic SSE stream (each event written in 2 flushed pieces); other codes -> JSON error body with that status; empty queue -> 404.
 * options: error => k (overloaded_error event after k events), cut => k (connection closed after k events, no message_stop), stall => [k, sec] (no bytes for sec
 *   after k events; k = 0: not even headers), delay => sec (between events), cancel => k (after k events the RUNNING run is set to CANCELLING, like the app's Cancel),
 *   probe => [k, ...] (before event k: wait 1.6 s, then record the RUNNING run's live columns to "<queue>.probe", one JSON line each: what the app would see mid-stream)
 */
declare(strict_types=1);
require __DIR__ . '/sse_gen.php';
$q = (string)getenv('STUDIO_FAKE_Q');
@file_put_contents("$q.log", 'http://' . ($_SERVER['HTTP_HOST'] ?? '') . ($_SERVER['REQUEST_URI'] ?? '') . ' ' . substr((string)file_get_contents('php://input'), 0, 2000000) . "\n", FILE_APPEND);
$list = is_file($q) ? (array)json_decode((string)file_get_contents($q), true) : [];
$next = array_shift($list);
file_put_contents($q, json_encode($list));
if (!is_array($next)) { http_response_code(404); header('Content-Type: application/json'); echo json_encode(['type' => 'error', 'error' => ['type' => 'not_found_error', 'message' => 'no canned response (test queue empty)']]); exit; }
$code = (int)$next[0]; $body = $next[1] ?? null; $opt = (array)($next[2] ?? []);
if ($code !== 200) { http_response_code($code); header('Content-Type: application/json'); echo is_string($body) ? $body : json_encode($body); exit; }
$ev = fake_sse_events(is_string($body) ? (array)json_decode($body, true) : (array)$body);
$stall = array_map('intval', (array)($opt['stall'] ?? []));
if ($stall && $stall[0] === 0) sleep($stall[1]);
header('Content-Type: text/event-stream');
header('Cache-Control: no-cache');
while (ob_get_level()) ob_end_flush();
foreach ($ev as $k => $e) {
    if (isset($opt['error']) && $k === (int)$opt['error']) { echo "event: error\ndata: " . json_encode(['type' => 'error', 'error' => ['type' => 'overloaded_error', 'message' => 'Overloaded']]) . "\n\n"; flush(); exit; }
    if (isset($opt['cut']) && $k === (int)$opt['cut']) exit;
    if (isset($opt['cancel']) && $k === (int)$opt['cancel'])
        (new PDO('mysql:host=' . getenv('TEST_DB_HOST') . ';dbname=' . getenv('TEST_DB_NAME'), (string)getenv('TEST_DB_USER'), (string)getenv('TEST_DB_PASS')))->exec("UPDATE st_runs SET status = 'CANCELLING' WHERE status = 'RUNNING'");
    if ($stall && $k > 0 && $k === $stall[0]) sleep($stall[1]);
    if (in_array($k, array_map('intval', (array)($opt['probe'] ?? [])), true)) {
        usleep(1600000);   // client ticks meanwhile (idle limit 2 s): its throttled live write lands before the probe
        $row = (new PDO('mysql:host=' . getenv('TEST_DB_HOST') . ';dbname=' . getenv('TEST_DB_NAME'), (string)getenv('TEST_DB_USER'), (string)getenv('TEST_DB_PASS')))
            ->query("SELECT id, frac, detail, beat_at, steps_json, steps_done, steps_total, UTC_TIMESTAMP() db_now FROM st_runs WHERE status = 'RUNNING' ORDER BY id DESC LIMIT 1")->fetch(PDO::FETCH_ASSOC);
        file_put_contents("$q.probe", json_encode(['k' => $k, 'row' => $row]) . "\n", FILE_APPEND);
    }
    $h = intdiv(strlen($e), 2);   // event in 2 pieces: the client sees lines (and sometimes UTF-8 chars) cut in the middle
    echo substr($e, 0, $h); flush();
    echo substr($e, $h); flush();
    if (!empty($opt['delay'])) usleep((int)((float)$opt['delay'] * 1e6));
}
PHP);
require "$T/fake/sse_gen.php";
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
// daily discovery: findings schema + server-side cleaning of the AI output
$ds = st_disc_schema()['properties']['findings']['items'];
ok($uc['st_disc_schema'] === 0 && $ds['required'] === array_keys($ds['properties']) && $ds['additionalProperties'] === false && $ds['properties']['type']['enum'] === ST_DISC_TYPES
    && $ds['properties']['basis']['enum'] === ['SAID', 'GUESS'] && !str_contains(json_encode(st_disc_schema()), 'null'), 'discovery schema: 0 union/nullable types, every property required, no extra properties');
$dRaw = [
    ['type' => 'NEW_APP', 'title' => "  Ghost detector\n  apps rising ", 'detail' => str_repeat('word ', 50), 'basis' => 'SAID', 'sources' => ['S1', 's2', 'S9', 'S1'], 'category' => ' Entertainment ',
        'countries' => ['us', 'USA', ' in', 'us', 7, 'X1']],
    ['type' => 'POLICY', 'title' => 'Policy only invented', 'detail' => 'd', 'basis' => 'SAID', 'sources' => ['S9'], 'category' => '', 'countries' => []],
    ['type' => 'NEW_APP', 'title' => '   ', 'detail' => 'empty title', 'basis' => 'GUESS', 'sources' => ['S1'], 'category' => '', 'countries' => []],
    ['type' => 'HACK', 'title' => 'Odd one', 'detail' => ['x'], 'basis' => 'MAYBE', 'sources' => 'S2', 'category' => ['y'], 'countries' => 'in'],
    'junk', ['title' => str_repeat('T', 90)]];
$dc = st_disc_clean($dRaw, ['S1' => 11, 'S2' => 12]);
ok(count($dc) === 4 && $dc[0]['title'] === 'Ghost detector apps rising' && mb_strlen($dc[0]['detail']) === ST_DISC_DETAIL && ST_DISC_DETAIL === 140 && str_ends_with($dc[0]['detail'], 'word…') && $dc[0]['sources'] === [11, 12]
    && $dc[0]['countries'] === ['US', 'IN'] && $dc[0]['category'] === 'Entertainment' && $dc[0]['basis'] === 'SAID',
    'discovery clean: title trimmed, detail cut to 140 with …, invented S9 + duplicate dropped (s2 accepted), countries ISO2 uppercase, AI order');
ok($dc[1]['sources'] === [] && $dc[1]['basis'] === 'GUESS' && $dc[2]['type'] === 'OTHER' && $dc[2]['basis'] === 'GUESS' && $dc[2]['detail'] === '' && $dc[2]['category'] === '' && $dc[2]['sources'] === [12]
    && $dc[2]['countries'] === ['IN'] && $dc[3]['type'] === 'OTHER' && mb_strlen($dc[3]['title']) === ST_DISC_TITLE && ST_DISC_TITLE === 60 && $dc[3]['sources'] === [] && $dc[3]['countries'] === [],
    'discovery clean: SAID without a real source -> GUESS, unknown type -> OTHER, empty title / non-array dropped, title cut to 60, missing fields safe');
$many = array_map(fn($i) => ['type' => 'ADS', 'title' => "F$i", 'detail' => 'd', 'basis' => 'GUESS', 'sources' => [], 'category' => '', 'countries' => []], range(1, 12));
ok(array_column(st_disc_clean($many, []), 'title') === ['F1', 'F2', 'F3', 'F4', 'F5', 'F6', 'F7', 'F8'], 'discovery clean: at most 8 findings, first 8 kept');
// streaming (SSE) accumulator: owner's weekly ranking failed at step 4/6 (non-streaming call, 300 s timeout / 32k max_tokens)
$norm = function ($v) use (&$norm) { if (!is_array($v)) return $v; $v = array_map($norm, $v); if (!array_is_list($v)) ksort($v); return $v; };
$feed = function (string $sse, int $size): array { $st = st_sse_new(); foreach (str_split($sse, max(1, $size)) as $c) st_sse_feed($st, $c); return $st; };
$msgU = ['id' => 'msg_u1', 'type' => 'message', 'role' => 'assistant', 'model' => 'claude-opus-5-5', 'content' => [
    ['type' => 'thinking', 'thinking' => 'सोच रहा हूँ… plan 🔦 next', 'signature' => 'sig-abc=='],
    ['type' => 'server_tool_use', 'id' => 'srvtoolu_1', 'name' => 'web_search', 'input' => ['query' => 'टॉर्च ऐप "best" \\ 2026']],
    ['type' => 'web_search_tool_result', 'tool_use_id' => 'srvtoolu_1', 'content' => [['type' => 'web_search_result', 'url' => 'https://ex.example/a', 'title' => 'A', 'page_age' => '1 day', 'encrypted_content' => 'xx']]],
    ['type' => 'text', 'text' => 'खेल ऐप बढ़ रहे हैं 🎮 — growth in "Tools" {[ ]} data: x', 'citations' => [['type' => 'web_search_result_location', 'url' => 'https://ex.example/a', 'title' => 'A', 'cited_text' => 'x', 'encrypted_index' => 'e1']]],
    ['type' => 'redacted_thinking', 'data' => 'ENC=='], ['type' => 'mystery_block', 'x' => 1]],
    'stop_reason' => 'end_turn', 'stop_sequence' => null, 'usage' => ['input_tokens' => 1234, 'cache_creation_input_tokens' => 10, 'cache_read_input_tokens' => 20, 'output_tokens' => 567, 'server_tool_use' => ['web_search_requests' => 1]]];
$sse = implode('', fake_sse_events($msgU));
$same = true;
foreach ([1, 7, 64, strlen($sse)] as $sz) { $st = $feed($sse, $sz); $same = $same && $st['done'] && $st['error'] === null && $st['buf'] === '' && $norm(st_sse_message($st)) === $norm($msgU); }
$utf8Cut = (bool)array_filter(str_split($sse, 7), fn($c) => !mb_check_encoding($c, 'UTF-8'));
ok($same && $utf8Cut, 'SSE: same message as the non-streaming JSON for chunk sizes 1, 7, 64 and whole (Hindi/emoji UTF-8 cut between chunks, thinking + signature, server_tool_use input from partial_json, web_search_tool_result, citations_delta, unknown block)');
$same = true;
foreach ([1, 5, 64] as $sz) { $st = $feed(str_replace("\n", "\r\n", $sse), $sz); $same = $same && $st['done'] && $norm(st_sse_message($st)) === $norm($msgU); }
ok($same, 'SSE: \r\n line endings (\r and \n arriving in different chunks)');
$evs = fake_sse_events($msgU);
$k3 = (int)array_key_first(array_filter($evs, fn($e) => str_contains($e, '"type":"content_block_stop","index":3')));
array_splice($evs, $k3, 0, ["event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":3,\ndata:\"delta\":{\"type\":\"text_delta\",\"text\":\" ✓\"}}\n\n"]);   // one event, 2 data lines
array_splice($evs, 1, 0, [": keep-alive comment\n\nevent: future_event\ndata: {\"type\":\"future_event\",\"x\":1}\n\nid: 7\nretry: 1000\ndata: {\"type\":\"ping\"}\n\nevent: ping\ndata: not json\n\n"]);
$exp = $msgU; $exp['content'][3]['text'] .= ' ✓';
$st = $feed(implode('', $evs), 3);
ok($st['done'] && $norm(st_sse_message($st)) === $norm($exp), 'SSE: comments, unknown events / fields, data-only event, bad JSON ignored; multi-line data joined');
$um = "event: message_start\ndata: " . json_encode(['type' => 'message_start', 'message' => ['id' => 'm', 'model' => 'claude-opus-5-5', 'content' => [], 'stop_reason' => null, 'usage' => ['input_tokens' => 100, 'output_tokens' => 1]]])
    . "\n\nevent: message_delta\ndata: " . json_encode(['type' => 'message_delta', 'delta' => ['stop_reason' => 'max_tokens', 'stop_sequence' => null], 'usage' => ['input_tokens' => 150, 'cache_read_input_tokens' => 50, 'output_tokens' => 300,
        'server_tool_use' => ['web_search_requests' => 2]]]) . "\n\nevent: message_stop\ndata: {\"type\":\"message_stop\"}\n\n";
$st = $feed($um, 3); $mm = st_sse_message($st);
ok($mm['usage'] === ['input_tokens' => 150, 'output_tokens' => 300, 'cache_read_input_tokens' => 50, 'server_tool_use' => ['web_search_requests' => 2]] && $mm['stop_reason'] === 'max_tokens' && $mm['content'] === []
    && st_sse_usage($st) === $mm['usage'], 'SSE: message_delta usage is cumulative (merged, later values win), stop_reason from the delta');
$evs = fake_sse_events(['usage' => ['input_tokens' => 4321, 'output_tokens' => 9], 'content' => [['type' => 'server_tool_use', 'id' => 's9', 'name' => 'web_search', 'input' => ['query' => 'abc def']],
    ['type' => 'text', 'text' => 'partial answer here']]]);
$st = $feed(implode('', array_slice($evs, 0, 12)) . "event: content_block_delta\ndata: {\"type\":\"content_bl", 4); $mm = st_sse_message($st);
$st2 = $feed(implode('', array_slice($evs, 0, 5)), 64);
$st3 = $feed(implode('', array_slice($evs, 0, 2)) . "event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}\n\n", 7);
$st4 = $feed(implode('', fake_sse_events(['content' => [['type' => 'tool_use', 'id' => 't1', 'name' => 'noop', 'input' => []]]])), 9);
ok(!$st['done'] && ($mm['content'][1]['text'] ?? '') === 'partial answer' && ($mm['content'][0]['input'] ?? null) === ['query' => 'abc def'] && st_sse_usage($st) === ['input_tokens' => 4321, 'output_tokens' => 9]
    && json_encode(st_sse_message($st2)['content'][0]['input']) === '{}' && st_sse_usage($st2)['output_tokens'] === 3 && $st3['error'] === ['type' => 'overloaded_error', 'message' => 'Overloaded'] && !$st3['done']
    && st_sse_message($st3)['content'] === [] && json_encode(st_sse_message($st4)['content'][0]['input']) === '{}' && st_sse_message(st_sse_new()) === null,
    'SSE: stream cut mid-way keeps what arrived (half tool input -> {}), output tokens ~ streamed bytes / 4 without message_delta, error event recorded, empty tool input -> {} object');
// tool input given whole in content_block_start (no input_json_delta) is kept, not overwritten with {}
$si = "event: message_start\ndata: " . json_encode(['type' => 'message_start', 'message' => ['id' => 'm', 'model' => 'claude-opus-5-5', 'content' => [], 'usage' => ['input_tokens' => 1]]]) . "\n\n"
    . "event: content_block_start\ndata: " . json_encode(['type' => 'content_block_start', 'index' => 0, 'content_block' => ['type' => 'server_tool_use', 'id' => 's1', 'name' => 'web_search',
        'input' => ['query' => 'android prank apps'], 'caller' => ['type' => 'direct']]]) . "\n\nevent: content_block_stop\ndata: {\"type\":\"content_block_stop\",\"index\":0}\n\n"
    . "event: content_block_start\ndata: " . json_encode(['type' => 'content_block_start', 'index' => 1, 'content_block' => ['type' => 'tool_use', 'id' => 't2', 'name' => 'x', 'input' => ['a' => 1]]]) . "\n\n"
    . "event: content_block_delta\ndata: " . json_encode(['type' => 'content_block_delta', 'index' => 1, 'delta' => ['type' => 'input_json_delta', 'partial_json' => '{"b":2}']]) . "\n\n"
    . "event: content_block_stop\ndata: {\"type\":\"content_block_stop\",\"index\":1}\n\n";
$sim = st_sse_message($feed($si, 5));
ok(($sim['content'][0]['input'] ?? null) === ['query' => 'android prank apps'] && ($sim['content'][0]['caller'] ?? null) === ['type' => 'direct'] && ($sim['content'][1]['input'] ?? null) === ['b' => 2],
    'SSE: tool input given whole in content_block_start kept when no delta follows; deltas (when sent) win');
$fr = fn(array $x): array => $x + ['code' => 0, 'raw' => '', 'st' => st_sse_new(), 'idle' => false, 'cancel' => false, 'dberr' => null, 'errno' => 0, 'err' => '', 'secs' => 0.0];
$stOut = $feed(implode('', array_slice($evs, 0, 12)), 64);
$sf = [st_stream_fail($fr(['code' => 529, 'raw' => '{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}'])),
    st_stream_fail($fr(['code' => 400, 'raw' => '{"type":"error","error":{"type":"invalid_request_error","message":"bad schema"}}'])),
    st_stream_fail($fr(['errno' => 7, 'err' => 'Failed to connect to api.anthropic.com port 443', 'secs' => 0.2])),
    st_stream_fail($fr(['errno' => 28, 'err' => 'Operation timed out after 1500000 milliseconds', 'secs' => 1500.4, 'code' => 200, 'st' => $stOut])),
    st_stream_fail($fr(['errno' => 42, 'err' => 'Callback aborted', 'idle' => true, 'secs' => 3.2])),
    st_stream_fail($fr(['code' => 200, 'st' => $stOut])), st_stream_fail($fr(['code' => 200, 'st' => $st3])),
    st_stream_fail($fr(['code' => 200, 'st' => ['error' => ['type' => 'api_error', 'message' => 'Internal']] + $stOut]))];
ok($sf === [[true, 'Claude API HTTP 529 Overloaded'], [false, 'Claude API HTTP 400 bad schema'], [true, 'Claude API connection failed: Failed to connect to api.anthropic.com port 443'],
    [false, 'Claude API did not answer in time (1500 s): Operation timed out after 1500000 milliseconds'], [true, 'Claude API did not answer in time (3 s): no data for 2 s'],
    [false, 'Claude API answer stopped early (connection closed)'], [true, 'Claude API error during answer: overloaded_error: Overloaded'], [false, 'Claude API error during answer: api_error: Internal']],
    'stream failures: clear texts; retry only for 429/529/5xx, overloaded/api_error before any output, and nothing received; never after output started');
$fbB = ['type' => 'fallback', 'from' => ['model' => 'claude-opus-5-5'], 'to' => ['model' => 'claude-opus-5']];
$fbC = [['type' => 'thinking', 'thinking' => '', 'signature' => 'sigA'], ['type' => 'server_tool_use', 'id' => 'a', 'name' => 'web_search', 'input' => ['query' => 'q']],
    ['type' => 'web_search_tool_result', 'tool_use_id' => 'a', 'content' => []], ['type' => 'server_tool_use', 'id' => 'b', 'name' => 'web_search', 'input' => ['query' => 'q2']],
    ['type' => 'tool_use', 'id' => 'c', 'name' => 'x', 'input' => []], ['type' => 'redacted_thinking', 'data' => 'R'], ['type' => 'mystery', 'x' => 1], ['type' => 'text', 'text' => '{"dead":'], $fbB,
    ['type' => 'thinking', 'thinking' => '', 'signature' => 'sigB'], ['type' => 'text', 'text' => '{"ok":'], ['type' => 'text', 'text' => '1}']];
ok(st_ai_text($fbC) === '{"ok":1}' && st_ai_text([['type' => 'text', 'text' => 'a'], ['type' => 'thinking', 'thinking' => 'z'], ['type' => 'text', 'text' => 'b']]) === 'ab' && st_ai_text([$fbB]) === '',
    'non-streaming answer text = text blocks after the last fallback block only (the API already dropped the declined partial)');
ok(st_ai_text($fbC, true) === '{"dead":{"ok":1}' && st_ai_text([['type' => 'text', 'text' => '{"opportunities":[{"title":"A"},{"ti'], $fbB, ['type' => 'text', 'text' => 'tle":"B"}]}']], true) === '{"opportunities":[{"title":"A"},{"title":"B"}]}'
    && st_ai_text([$fbB], true) === '', 'streaming answer text = every text block (the fallback model continues the declined partial)');
$echo = st_ai_echo($fbC);
$fbD = [['type' => 'text', 'text' => 'p1'], $fbB, ['type' => 'thinking', 'thinking' => '', 'signature' => 'sigM'], ['type' => 'text', 'text' => 'p2'], $fbB + ['n' => 2], ['type' => 'text', 'text' => 'p3']];
ok(array_column($echo, 'type') === ['server_tool_use', 'web_search_tool_result', 'text', 'fallback', 'thinking', 'text', 'text'] && $echo[0]['id'] === 'a' && $echo[3] === $fbB && $echo[4]['signature'] === 'sigB'
    && st_ai_echo(array_slice($fbC, 0, 8)) === array_slice($fbC, 0, 8) && array_column(st_ai_echo($fbD), 'type') === ['text', 'fallback', 'text', 'fallback', 'text'],
    'pause_turn echo after a mid-stream fallback: thinking / redacted / tool_use / unpaired server_tool_use / unknown before the last boundary dropped, '
    . 'text + paired search kept, fallback blocks kept in place; without a fallback block unchanged');
// truncated JSON salvage (max_tokens): only complete items of the top-level list
$sv = [['title' => 'A "quoted" [x] {y}', 'deep' => ['k' => [1, 2, ['z' => 'q\\']]]], ['title' => "B ]} line\nbreak", 'n' => ['m' => ['o' => -1.5]]], ['title' => 'C ✓ खेल']];
$full = json_encode(['meta' => ['opportunities' => [9]], 'opportunities' => $sv, 'tail' => true], JSON_UNESCAPED_UNICODE);
$at = fn(string $needle, string $plus = '') => substr($full, 0, (int)strpos($full, $needle)) . $plus;
ok(st_json_salvage_list($full, 'opportunities') === $sv && st_json_salvage_list(json_encode(['opportunities' => $sv], JSON_PRETTY_PRINT), 'opportunities') === $sv
    && st_json_salvage_list('Sure: {"opportunities":[{"a":1},{"b":2}]', 'opportunities') === [['a' => 1], ['b' => 2]], 'salvage: complete JSON (also pretty-printed, text before it) -> every item; same key nested deeper ignored');
ok(st_json_salvage_list($at('C ✓', 'C'), 'opportunities') === array_slice($sv, 0, 2) && st_json_salvage_list($at('"o"', '"o":'), 'opportunities') === [$sv[0]]
    && st_json_salvage_list($at('"z"', '"z":"q\\'), 'opportunities') === [] && st_json_salvage_list($at('{"title":"A'), 'opportunities') === [] && st_json_salvage_list('{"opportunities":[{"a":1},', 'opportunities') === [['a' => 1]],
    'salvage: cut mid-string, mid nested object, inside an escape, right after "[" or a comma -> only the complete items');
ok(st_json_salvage_list('{"opportunities":["a\\"b]", 3, true, null, "c', 'opportunities') === ['a"b]', 3, true, null] && st_json_salvage_list('not json [1, 2]', 'opportunities') === []
    && st_json_salvage_list('', 'opportunities') === [] && st_json_salvage_list('{"opportunities":"x","other":[{"a":1}]}', 'opportunities') === [] && st_json_salvage_list('{"opportunities":{"a":[1]}}', 'opportunities') === [],
    'salvage: scalar items, garbage / empty / key not a list -> []');
$bigOpps = array_map(fn($i) => ['title' => "Idea $i", 'note' => str_repeat('x "y" \\ [z] ', 400)], range(1, 60));
$bigCut = substr(json_encode(['opportunities' => $bigOpps]), 0, -500);
$t0 = microtime(true); $bigN = count(st_json_salvage_list($bigCut, 'opportunities')); $bigT = microtime(true) - $t0;
ok($bigN === 59 && $bigT < 1.0, sprintf('salvage: %d KB cut answer scanned in %.0f ms (59 of 60 ideas complete)', strlen($bigCut) / 1024, $bigT * 1000));

// live progress (pure): honest frac / detail from the stream state, steps for the app, pct
$blk = fn(string $t, array $x = []): array => ['type' => $t] + $x;
$LI = st_live_new(['schema' => ['type' => 'object'], 'salvage' => 'opportunities', 'expect' => 10]);
$LS = st_live_new(['search' => 5]);
$LW = st_live_new(['schema' => ['type' => 'object']]);
ok($LI['mode'] === 'items' && $LI['expect'] === 10 && $LS['mode'] === 'search' && $LS['max'] === 5 && $LW['mode'] === 'write' && $LW['span'] === [0.0, 1.0]
    && st_live_new(['span' => [0.45, 1]])['span'] === [0.45, 1.0], 'live: kind of call from the options (salvage list -> items, web search -> search, else write) + span inside the step');
$thinkSt = ['blocks' => [$blk('thinking', ['thinking' => 'hmm'])]];
$th = array_map(fn($t) => st_live_calc($LI, $thinkSt, $t), [0, 30, 300, 3000]);
ok($th[0] === [0.0, 'AI is thinking', ''] && $th[1][0] > 0.05 && $th[1][0] < $th[2][0] && $th[2][0] < $th[3][0] && $th[3][0] < 0.25,
    'live: only thinking so far -> "AI is thinking", frac creeps with time (' . implode(', ', array_column($th, 0)) . '), always below 0.25');
$txtSt = ['blocks' => [$blk('thinking', ['thinking' => 'x']), $blk('text', ['text' => '{"opportunities":[{"a":1},{"b":2}'])]];
ok(st_live_calc(['items' => 4] + $LI, $txtSt, 500) === [0.53, '4 of ~10 ideas written', '4 of ~10'] && st_live_calc($LI, $txtSt, 5) === [0.25, '0 of ~10 ideas written', '0 of ~10']
    && st_live_calc(['items' => 14] + $LI, $txtSt, 5)[0] === 0.95, 'live: writing ideas -> 0.25 + 0.7 x complete / expected ("4 of ~10 ideas written", note "4 of ~10"), more than expected stays 0.95');
$sb = fn(string $id) => $blk('server_tool_use', ['id' => $id, 'name' => 'web_search', 'input' => []]);
$sr = fn(string $id) => $blk('web_search_tool_result', ['tool_use_id' => $id, 'content' => []]);
$s2 = ['blocks' => [$blk('text', ['text' => 'Let me search.']), $sb('a'), $sr('a'), $blk('text', ['text' => 'Now the next one.']), $sb('b'), $sr('b')]];
$s2w = ['blocks' => [...$s2['blocks'], $blk('text', ['text' => str_repeat('n', 3000)])]];
$cw = st_live_calc($LS, $s2w, 10);
ok(st_live_calc($LS, $s2, 10) === [0.24, '2 web searches', '2 searches'] && $cw[1] === 'writing notes' && $cw[0] > 0.6 && $cw[0] < 0.97 && $cw[2] === '2 searches'
    && st_live_calc(['base_n' => 3] + $LS, ['blocks' => [$sb('c')]], 10)[1] === '4 web searches' && st_live_calc($LS, ['blocks' => [$sb('c')]], 10) === [0.12, '1 web search', '1 search']
    && st_live_calc($LS, ['blocks' => []], 10)[1] === 'AI is thinking' && st_live_calc($LS, ['blocks' => [$blk('text', ['text' => 'Searching'])]], 10)[1] === 'starting web search'
    && st_live_calc(['base_n' => 9] + $LS, ['blocks' => []], 10)[0] === 0.6,
    'live: web search call -> "N web searches" (0.6 x searches / max_uses, earlier pause_turn turns counted), then "writing notes" (text after the last search grows the bar)');
$w1 = st_live_calc($LW, ['blocks' => [$blk('text', ['text' => str_repeat('x', 20000)])]], 60);
ok(st_live_calc($LW, $thinkSt, 60)[1] === 'AI is thinking' && $w1 === [0.61, 'AI is writing', ''] && st_live_calc($LW, ['blocks' => [$blk('text', ['text' => str_repeat('x', 900000)])]], 60)[0] < 0.97,
    'live: other structured calls -> "AI is thinking", then "AI is writing" (bar grows with the text, never 100%)');
$sj = json_encode([['l' => 'Web research: Tools', 'n' => '9 sources', 's' => 'd'], ['l' => 'AI ranking', 'n' => '4 of ~10', 's' => 'w'], ['l' => 'Save + notify you', 'n' => '', 's' => 'w']]);
$svs = fn(?string $j, string $st): array => array_column(st_steps_view($j, $st), 'state');
$sje = str_replace('"n":"4 of ~10","s":"w"', '"n":"4 of ~10","s":"e"', $sj);
ok($svs($sj, 'RUNNING') === ['done', 'on', 'wait'] && $svs($sj, 'CANCELLING') === ['done', 'on', 'wait'] && $svs($sj, 'FAILED') === ['done', 'fail', 'wait'] && $svs($sj, 'CANCELLED') === ['done', 'fail', 'wait']
    && $svs($sj, 'DONE') === ['done', 'done', 'done'] && $svs($sj, 'QUEUED') === ['done', 'wait', 'wait'] && $svs($sje, 'QUEUED') === ['done', 'fail', 'wait'] && $svs($sje, 'RUNNING') === ['done', 'on', 'wait']
    && st_steps_view($sj, 'RUNNING')[1] === ['label' => 'AI ranking', 'state' => 'on', 'note' => '4 of ~10'] && st_steps_view(null, 'DONE') === [] && st_steps_view('{"l":1}', 'DONE') === []
    && st_steps_view('not json', 'RUNNING') === [] && st_steps_view('[1,2]', 'RUNNING') === [],
    'steps for the app: first open step "on" while running, "fail" where a run stopped (also a crash-recovered one), all done when DONE; old / bad JSON -> []');
$frow = fn(array $x): array => $x + ['id' => 1, 'run_uid' => 'ST-x', 'job' => 'rank', 'ref_id' => null, 'trigger_type' => 'manual', 'triggered_by' => '', 'status' => 'RUNNING', 'stage' => 'AI ranking (structured)',
    'steps_done' => 4, 'steps_total' => 6, 'errors' => 0, 'error_text' => '', 'ai_calls' => 0, 'searches' => 0, 'cost_usd' => '0.0000', 'summary' => '', 'attempts' => 1, 'queued_at' => '2026-10-23 10:00:00',
    'started_at' => null, 'finished_at' => null, 'frac' => '0.9800'];
$old = st_run_row($frow([]));
ok($old['pct'] === 83 && st_run_row($frow(['status' => 'DONE']))['pct'] === 100 && st_run_row($frow(['steps_done' => 6, 'frac' => '0.0000']))['pct'] === 99
    && st_run_row($frow(['status' => 'QUEUED', 'steps_done' => 0, 'steps_total' => 0, 'frac' => '0']))['pct'] === 0 && st_run_row($frow(['steps_done' => 1, 'steps_total' => 3, 'frac' => '0']))['pct'] === 33
    && $old['frac'] === 0.98 && $old['steps'] === [] && $old['detail'] === '' && $old['beat_at'] === null && $old['error_text'] === null && $old['summary'] === null && $old['waiting_for'] === null && $old['can_reuse'] === false,
    'run row: pct = floor((steps done + frac) / total) (4 + 0.98 of 6 -> 83), max 99 until DONE (100); row without the new columns -> steps [], frac/beat defaults; empty error/summary -> null');

if (!getenv('TEST_DB_HOST')) { echo "\nTEST_DB_HOST not set: DB part skipped\n$n checks, $fail failed\n"; exit($fail > 0 ? 1 : 0); }

// ================================================================ Part 2: MySQL + simulated data
// fake Claude API server (fake_claude.php): started before the first st_ai call; workers so a stalled stream never blocks the next request
$env = 'STUDIO_FAKE_Q=' . escapeshellarg($Q) . ' TEST_DB_HOST=' . escapeshellarg((string)getenv('TEST_DB_HOST')) . ' TEST_DB_NAME=' . escapeshellarg((string)getenv('TEST_DB_NAME'))
    . ' TEST_DB_USER=' . escapeshellarg((string)getenv('TEST_DB_USER')) . ' TEST_DB_PASS=' . escapeshellarg((string)getenv('TEST_DB_PASS'));
$fsrv = proc_open("PHP_CLI_SERVER_WORKERS=4 $env exec php -S 127.0.0.1:$FP -t " . escapeshellarg("$T/fake"), [1 => ['file', "$T/fake.log", 'a'], 2 => ['file', "$T/fake.log", 'a']], $fpipes);
register_shutdown_function(function () use ($fsrv) { proc_terminate($fsrv); });
for ($i = 0; $i < 50 && !($fs = @fsockopen('127.0.0.1', $FP, $en, $es, 0.2)); $i++) usleep(100000);
if (!empty($fs)) fclose($fs);
function q(string $sql, array $p = []): array { return h_q(db(), $sql, $p); }
function q1(string $sql, array $p = []) { return h_q1(db(), $sql, $p); }
$fake = function (array $responses) use ($Q) { file_put_contents($Q, json_encode($responses)); };
$left = function () use ($Q) { return count((array)json_decode((string)@file_get_contents($Q), true)); };
$pdo = db();
foreach (['kv', 'app_alerts', 'health_events', 'health_emails', 'day_install', 'day_user', 'imps', 'devices', 'gads_campaign_day', 'events', 'ga_geo_day', 'ga_camp', 'ga_accounts',
    'zz_imps', 'zz_events', 'zz_day_install', 'zz_devices', 'zz_gads_campaign_day',
    'st_runs', 'st_events', 'st_evidence', 'st_snapshots', 'st_econ', 'st_app_meta', 'st_opps', 'st_projects', 'st_artifacts', 'st_approvals', 'st_usage', 'st_sources', 'st_notified', 'st_port',
    'cost_events', 'cost_billed', 'cost_paused'] as $t) $pdo->exec("DROP TABLE IF EXISTS $t");
$pdo->exec("CREATE TABLE kv (k VARCHAR(64) NOT NULL PRIMARY KEY, v TEXT NOT NULL, updated_at DATETIME NOT NULL)");
// AI Cost Manager (lib/cost.php): the suite makes more than the research module's default 60 AI calls a day -> daily cap off (0 = no cap); cost_events fresh every run
kv_set('cost_settings', json_encode(['modules' => ['research' => ['cap' => 0]]]));
if (function_exists('cost_settings')) cost_settings(true);
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
// result group-wise (row order / summation order MySQL 8 par alag ho sakta hai), float tolerance ke saath
$kp = $byKey(st_econ_cohorts($np, $eFrom, $eTo));
$sameP = array_keys($kp) === array_keys($ko);
foreach ($ko as $gk => $v) $sameP = $sameP && $v['i'] === $kp[$gk]['i'] && $v['r1'] === $kp[$gk]['r1'] && $v['r7'] === $kp[$gk]['r7'] && abs($v['rev'] - $kp[$gk]['rev']) < 1e-9;
$wp = (int)q1("SELECT COUNT(*) FROM st_events WHERE level = 'warn' AND msg LIKE '%fast cohort SQL failed%Access denied%'");
ok($sameP && $wp === 1, "no CREATE TEMPORARY TABLES privilege: warning logged, old query used, same result (same: " . ($sameP ? 'yes' : 'no') . ", warns: $wp)");
$np = null;
$rid = st_run_new($pdo, 'economics', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
$er = $pdo->query("SELECT * FROM st_runs WHERE id = $rid")->fetch();
$ec = []; foreach (q('SELECT * FROM st_econ') as $r) $ec[$r['category'] . '|' . $r['cc'] . '|' . $r['users_type']] = $r;
ok($er['status'] === 'DONE' && (int)$er['steps_done'] === 4 && (int)$er['steps_total'] === 4, "economics with cohorts in cc '' / NULL / 'UNK' / 'in' + 'IN': run DONE, no duplicate key" . ($er['status'] !== 'DONE' ? ' · ' . $er['error_text'] : ''));
$es = st_run_row($er)['steps'];
ok(array_map(fn($x) => [$x['label'], $x['state']], $es) === [['Install cohorts', 'done'], ['Retention + D7 revenue', 'done'], ['Cost per install', 'done'], ['Save economics', 'done']]
    && preg_match('/^[\d,]+ installs$/', $es[0]['note']) && $es[1]['note'] === '' && preg_match('/^\d+ apps?$/', $es[2]['note']) && preg_match('/^\d+ rows?$/', $es[3]['note']),
    'economics steps for the app: 4 labels (cohort SQL fills 2 at once), notes ' . json_encode(array_column($es, 'note')));
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
ok(str_contains($log, 'web_search_20260209') && str_contains($log, 'json_schema') && str_contains($log, 'UNTRUSTED DATA') && substr_count($log, '"stream":true') === 3 && str_contains($log, '/fake_claude.php '),
    'requests use web search tool, structured output and the untrusted-content rule; all 3 streamed (SSE)');
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
$s1 = st_run_row($run)['steps'];
ok(array_column($s1, 'label') === ['Your apps: earnings + installs', 'Google Ads spend', 'App, country, category totals', 'Save + insights', 'Web research: Tools', 'Your apps + daily discovery', 'AI ranking', 'Save + notify you']
    && array_unique(array_column($s1, 'state')) === ['done'] && (int)$run['steps_total'] === 8 && (int)$run['steps_done'] === 8 && $s1[4]['note'] === '2 sources' && $s1[6]['note'] === '2 ideas'
    && $s1[5]['note'] === st_n(st_port_apps($pdo), 'app') && st_port_apps($pdo) > 0 && preg_match('/^\d+ apps?$/', $s1[0]['note']) && st_run_row($run)['pct'] === 100,
    'rank run steps: the portfolio that ran inline first keeps its own 4 steps in place, then web research per category ("2 sources" over a pause_turn), apps + discovery ("N apps"), AI ranking ("2 ideas"), save');

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

// ---------------------------------------------------------------- daily discovery: web notes -> structured findings (2nd AI call, no search) -> weekly ranking
@unlink("$Q.log");
$dF = fn(string $type, string $title, string $detail, string $basis, array $src, string $cat = '', array $ccs = []) => ['type' => $type, 'title' => $title, 'detail' => $detail,
    'basis' => $basis, 'sources' => $src, 'category' => $cat, 'countries' => $ccs];
$fake([$webResp(['https://news.example/ghost', 'https://news.example/top50', 'https://policy.example/calllog'], '- Ghost detector apps rising in US [SOURCE SAYS] (https://news.example/ghost)'),
    $jsonResp(['findings' => [
        $dF('NEW_APP', 'Ghost detector apps rising in US', '2 new apps entered the top 50 in Entertainment this week.', 'SAID', ['S1', 'S2'], 'Entertainment', ['us']),
        $dF('COMPETITOR', 'New rival for Clap to Find', 'Clap Finder Pro launched: no ads, 4.6 stars, 100K+ installs.', 'SAID', ['S7']),   // only an invented id
        $dF('POLICY', 'Call log permission rule update', 'Play asks call history apps to explain why they need call log access.', 'SAID', ['S3'], 'Tools'),
        $dF('ADS', 'Prank apps cut ads in India', 'Fewer new ad creatives for prank apps in IN since 10 Oct.', 'GUESS', ['S2'], 'Prank apps', ['IN']),
        $dF('OTHER', ' ', 'no title', 'GUESS', [])]])]);
$rid1 = st_run_new($pdo, 'discover', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
$dr = $pdo->query("SELECT * FROM st_runs WHERE id = $rid1")->fetch();
$d1 = $pdo->query("SELECT * FROM st_artifacts WHERE run_id = $rid1 AND kind = 'daily_notes'")->fetch();
$dc1 = $d1 ? (array)json_decode($d1['content'], true) : [];
$dEv = array_map('intval', (array)($dc1['sources'] ?? []));
ok($dr['status'] === 'DONE' && $left() === 0 && (int)$dr['steps_done'] === 2 && (int)$dr['steps_total'] === 2 && (int)$dr['ai_calls'] === 2 && (int)$dr['searches'] === 2
    && $dr['summary'] === '4 findings · 3 sources', 'discover: web notes + structuring call, run DONE 2/2 steps, summary "' . $dr['summary'] . '"' . ($dr['status'] !== 'DONE' ? ' · ' . $dr['error_text'] : ''));
ok(array_map(fn($x) => array_values($x), st_run_row($dr)['steps']) === [['Web scan', 'done', '3 sources'], ['Findings', 'done', '4 findings']], 'discover steps: Web scan "3 sources", Findings "4 findings"');
$evUrl = fn(int $id) => (string)q1('SELECT url FROM st_evidence WHERE id = ?', [$id]);
ok(($dc1['structured'] ?? null) === true && array_keys($dEv) === ['S1', 'S2', 'S3'] && $evUrl($dEv['S3']) === 'https://policy.example/calllog' && str_contains($dc1['notes'] ?? '', 'S3: T https://policy.example/calllog')
    && ($dc1['countries'] ?? null) === ['IN', 'US'] && array_column($dc1['findings'] ?? [], 'type') === ['NEW_APP', 'COMPETITOR', 'POLICY', 'ADS'],
    'daily_notes artifact: notes + S map (evidence ids) + countries + 4 structured findings in AI order (empty title dropped)');
$f = $dc1['findings'] ?? [[], [], [], []];
ok(($f[0]['sources'] ?? null) === [$dEv['S1'], $dEv['S2']] && $f[0]['countries'] === ['US'] && $f[0]['basis'] === 'SAID' && $f[1]['sources'] === [] && $f[1]['basis'] === 'GUESS'
    && $f[2]['sources'] === [$dEv['S3']] && $f[3]['sources'] === [$dEv['S2']] && $f[3]['basis'] === 'GUESS',
    'findings saved with evidence ids; invented S7 dropped (finding left without a source -> GUESS)');
$lines = array_values(array_filter(explode("\n", (string)@file_get_contents("$Q.log"))));
$jr = array_map(fn($l) => json_decode(substr($l, (int)strpos($l, ' ') + 1), true), $lines);
$um = (string)($jr[1]['messages'][0]['content'] ?? '');
ok(count($jr) === 2 && isset($jr[0]['tools']) && !isset($jr[1]['tools']) && ($jr[1]['output_config']['effort'] ?? '') === 'low' && ($jr[1]['max_tokens'] ?? 0) === 6000
    && $unions($jr[1]['output_config']['format']['schema'] ?? ['x' => ['anyOf' => []]]) === 0 && isset($jr[1]['output_config']['format']['schema']['properties']['findings'])
    && str_contains($um, 'never invent S ids') && str_contains($um, 'if nothing meaningful happened return an empty list') && str_contains($um, "=== Source ids you may use ===\nS1, S2, S3")
    && str_contains($um, 'Ghost detector apps rising in US [SOURCE SAYS]') && str_contains($um, 'S2: T https://news.example/top50') && str_contains($um, 'title: short English, at most 45 characters')
    && str_contains($um, 'at most 100 characters'),
    'structuring request: no web search tool, effort low, 6000 tokens, findings schema (0 union types), notes + S list + rules in the prompt');
// structuring call fails (not budget/cancel): error recorded, run PARTIAL (no retry), notes still saved
$fake([$webResp(['https://news.example/quiet'], 'Quiet day: nothing meaningful [MY INFERENCE]'), [400, ['type' => 'error', 'error' => ['type' => 'invalid_request_error', 'message' => 'schema too complex']]]]);
$rid2 = st_run_new($pdo, 'discover', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
$dr2 = $pdo->query("SELECT * FROM st_runs WHERE id = $rid2")->fetch();
$d2 = $pdo->query("SELECT * FROM st_artifacts WHERE run_id = $rid2 AND kind = 'daily_notes'")->fetch();
$dc2 = $d2 ? (array)json_decode($d2['content'], true) : [];
ok($dr2['status'] === 'PARTIAL' && (int)$dr2['attempts'] === 1 && (int)$dr2['errors'] === 1 && str_contains($dr2['error_text'], 'structuring findings: Claude API HTTP 400 schema too complex')
    && $dr2['summary'] === 'notes only · 1 source' && $left() === 0, 'structuring failure: error recorded, run PARTIAL (not retried), summary "' . $dr2['summary'] . '"');
ok(($dc2['structured'] ?? null) === false && ($dc2['findings'] ?? null) === [] && str_contains($dc2['notes'] ?? '', 'Quiet day') && count($dc2['sources'] ?? []) === 1,
    'structuring failure: notes + sources still saved (structured false, no findings)');
ok(array_map(fn($x) => array_values($x), st_run_row($dr2)['steps']) === [['Web scan', 'done', '1 source'], ['Findings', 'fail', 'notes only']] && st_run_row($dr2)['pct'] === 99,
    'discover steps after a structuring failure: Findings "fail" ("notes only"), run PARTIAL at 99%');
// budget runs out after the web call: run BLOCKED (as before), but the paid-for notes are kept
kv_set('st_ai_budget_usd', (string)(st_month_spend($pdo) + 0.01));
$fake([$webResp(['https://news.example/budget'], 'budget day notes')]);
$ridB = st_run_new($pdo, 'discover', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
kv_set('st_ai_budget_usd', '40');
$drB = $pdo->query("SELECT * FROM st_runs WHERE id = $ridB")->fetch();
$dB = $pdo->query("SELECT * FROM st_artifacts WHERE run_id = $ridB AND kind = 'daily_notes'")->fetch();
$dcB = $dB ? (array)json_decode($dB['content'], true) : [];
ok($drB['status'] === 'BLOCKED' && str_contains($drB['error_text'], 'budget reached') && (int)$drB['ai_calls'] === 1 && $left() === 0 && ($dcB['structured'] ?? null) === false
    && str_contains($dcB['notes'] ?? '', 'budget day notes') && $drB['summary'] === 'notes only · 1 source', 'budget reached before the structuring call: run BLOCKED, web notes still saved (structured false)');
if ($dB) h_x($pdo, 'DELETE FROM st_artifacts WHERE id = ?', [(int)$dB['id']]);
// newer scan repeats a title (other case): the ranking keeps only the newest one
$fake([$webResp(['https://news.example/ghost2'], 'ghost update'), $jsonResp(['findings' => [$dF('NEW_APP', 'GHOST DETECTOR APPS RISING IN US', 'Now 3 new ghost apps in the top 50.', 'SAID', ['S1'], 'Entertainment', ['US'])]])]);
$rid3 = st_run_new($pdo, 'discover', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
$d3 = $pdo->query("SELECT * FROM st_artifacts WHERE run_id = $rid3 AND kind = 'daily_notes'")->fetch();
$d3Ev = (int)(json_decode((string)($d3['content'] ?? '{}'), true)['sources']['S1'] ?? 0);
$tAgo = fn(int $h) => gmdate('Y-m-d H:i:s', time() - $h * 3600);
$dOld = st_artifact_save($pdo, null, null, 'daily_notes', ['notes' => 'old', 'sources' => ['S1' => $dEv['S1']], 'countries' => ['IN'], 'findings' => [$dF('OTHER', 'Nine day old finding', 'too old', 'GUESS', [$dEv['S1']])],
    'structured' => true], 'test: 9 days old', 'test');
h_x($pdo, 'UPDATE st_artifacts SET created_at = ? WHERE id = ?', [$tAgo(216), $dOld]);
// busy week: 9 scans x 8 findings = 72 > cap 64 -> the newest 8 scans are read whole (all 64 titles), the oldest not at all; used_in_ranking follows what was read
$pdo->exec("UPDATE st_artifacts SET kind = 'daily_off' WHERE kind = 'daily_notes'");
$busy = [];
for ($i = 0; $i < 9; $i++) {
    $busy[$i] = st_artifact_save($pdo, null, null, 'daily_notes', ['notes' => "busy $i", 'sources' => [], 'countries' => ['IN'], 'structured' => true,
        'findings' => array_map(fn($j) => $dF('OTHER', "Busy scan $i finding $j", 'd', 'GUESS', []), range(1, ST_DISC_MAX))], "test: busy $i", 'test');
    h_x($pdo, 'UPDATE st_artifacts SET created_at = ? WHERE id = ?', [$tAgo(1 + $i * 18), $busy[$i]]);   // 1 hour .. 6 days ago
}
@unlink("$Q.log");
$fake([$webResp(['https://example.com/busy'], 'tools notes'), $jsonResp(['opportunities' => []])]);
$brid = st_run_new($pdo, 'rank', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
$br = $pdo->query("SELECT * FROM st_runs WHERE id = $brid")->fetch();
$bp = '';
foreach (explode("\n", (string)@file_get_contents("$Q.log")) as $line) { $j = json_decode(substr($line, (int)strpos($line, ' ') + 1), true); if (isset($j['output_config']['format']['schema']['properties']['opportunities'])) $bp = (string)($j['messages'][0]['content'] ?? ''); }
$bIn = array_map(fn($i) => count(array_filter(range(1, ST_DISC_MAX), fn($j) => str_contains($bp, "- [OTHER] Busy scan $i finding $j: d [MY INFERENCE]"))), array_keys($busy));
$bUsed = array_map(fn($id) => st_disc_item($pdo, $pdo->query("SELECT * FROM st_artifacts WHERE id = $id")->fetch())['used_in_ranking'], $busy);
$bArt = json_decode((string)q1("SELECT content FROM st_artifacts WHERE run_id = ? AND kind = 'ranking'", [$brid]), true);
ok($br['status'] === 'DONE' && $left() === 0 && ST_DISC_RANK_MAX === 64 && $bIn === [8, 8, 8, 8, 8, 8, 8, 8, 0] && ($bArt['discovery_ids'] ?? null) === array_slice($busy, 0, 8)
    && $bUsed === [true, true, true, true, true, true, true, true, false],
    'busy week (9 scans x 8): rank prompt has all 64 findings of the 8 newest scans, ranking artifact lists them, the 9th scan is not "used in ranking" ('
    . json_encode($bIn) . ')' . ($br['status'] !== 'DONE' ? ' · ' . $br['error_text'] : ''));
foreach ($busy as $id) h_x($pdo, 'DELETE FROM st_artifacts WHERE id = ?', [$id]);
$pdo->exec("UPDATE st_artifacts SET kind = 'daily_notes' WHERE kind = 'daily_off'");
// weekly ranking, 2 categories: Tools 1 source (S1), Utilities 3 (S2..S4) -> discovery continues at S5
kv_set('st_categories', json_encode(['Tools', 'Utilities']));
@unlink("$Q.log");
$oppD = ['title' => 'Call Log Explainer', 'concept' => 'explains call log permission', 'category' => 'Tools', 'countries' => ['IN'], 'competitors' => [], 'monetization' => ['banner'], 'risks' => [],
    'why_build' => 'policy change', 'why_avoid' => 'small', 'complexity' => 'Low', 'recommendation' => 'TEST FIRST', 'decision_card' => $card,
    'factors' => $fx(['policy_risk' => $I(6, ['S6']), 'demand' => $I(5, ['S2']), 'competition' => $I(4, ['S5', 'S99'])])];
$fake([$webResp(['https://example.com/t1'], 'tools notes'), $webResp(['https://example.com/u1', 'https://example.com/u2', 'https://example.com/u3'], 'utilities notes'), $jsonResp(['opportunities' => [$oppD]])]);
$rrid = st_run_new($pdo, 'rank', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
kv_set('st_categories', json_encode(['Tools']));
$rr = $pdo->query("SELECT * FROM st_runs WHERE id = $rrid")->fetch();
$rq = [];
foreach (explode("\n", (string)@file_get_contents("$Q.log")) as $line) { $j = json_decode(substr($line, (int)strpos($line, ' ') + 1), true); if (isset($j['output_config']['format']['schema']['properties']['opportunities'])) $rq = $j; }
$rp = (string)($rq['messages'][0]['content'] ?? '');
$secExp = "## Daily discovery (last 7 days)\nFindings from the daily web scans, newest first.\n"
    . "- [NEW_APP] GHOST DETECTOR APPS RISING IN US: Now 3 new ghost apps in the top 50. (S5) [SOURCE SAYS]\n"
    . "- [COMPETITOR] New rival for Clap to Find: Clap Finder Pro launched: no ads, 4.6 stars, 100K+ installs. [MY INFERENCE]\n"
    . "- [POLICY] Call log permission rule update: Play asks call history apps to explain why they need call log access. (S6) [SOURCE SAYS]\n"
    . "- [ADS] Prank apps cut ads in India: Fewer new ad creatives for prank apps in IN since 10 Oct. (S7) [MY INFERENCE]\n"
    . "\nSources:\nS5: T https://news.example/ghost2 <https://news.example/ghost2> (3 days ago)\nS6: T https://policy.example/calllog <https://policy.example/calllog> (3 days ago)\n"
    . "S7: T https://news.example/top50 <https://news.example/top50> (3 days ago)\n";
ok($rr['status'] === 'DONE' && $left() === 0 && str_contains($rp, "=== Web research notes (untrusted) ===\n") && str_contains($rp, $secExp) && !str_contains($rp, 'Nine day old') && !str_contains($rp, 'Quiet day')
    && substr_count(strtolower($rp), 'ghost detector apps rising in us') === 1,
    'rank prompt: "Daily discovery (last 7 days)" section, newest duplicate title wins, S numbers continue after category sources (S5..S7), [SOURCE SAYS]/[MY INFERENCE], 9-day-old + unstructured scans left out'
    . ($rr['status'] !== 'DONE' ? ' · ' . $rr['error_text'] : ''));
ok(str_contains($rp, "S1: T https://example.com/t1 <") && str_contains($rp, "S2: T https://example.com/u1 <") && str_contains($rp, "S3: T https://example.com/u2 <") && str_contains($rp, "S4: T https://example.com/u3 <"),
    'rank prompt: second category renumbered S2..S4 in one pass (S1 -> S2 no longer collides with the old S2 line)');
ok(array_map(fn($x) => array_values($x), st_run_row($rr)['steps']) === [['Web research: Tools', 'done', '1 source'], ['Web research: Utilities', 'done', '3 sources'],
    ['Your apps + daily discovery', 'done', st_n(st_port_apps($pdo), 'app')], ['AI ranking', 'done', '1 idea'], ['Save + notify you', 'done', '']] && st_run_row($rr)['pct'] === 100 && st_run_row($rr)['frac'] === 0.0,
    'weekly ranking steps (2 categories): one web step each with its source count, apps + discovery, AI ranking "1 idea", save; DONE = 100%, frac reset');
$OD = $pdo->query("SELECT * FROM st_opps WHERE title = 'Call Log Explainer'")->fetch();
$fd = $OD ? json_decode($OD['factors'], true) : [];
$u1 = (int)q1("SELECT id FROM st_evidence WHERE url = 'https://example.com/u1' AND run_id = ?", [$rrid]);
ok(($fd['policy_risk']['evidence'] ?? null) === [$dEv['S3']] && ($fd['competition']['evidence'] ?? null) === [$d3Ev] && $d3Ev > 0 && ($fd['demand']['evidence'] ?? null) === [$u1] && $u1 > 0,
    'ranking cites discovery S ids as evidence (S6 -> call log policy page, S5 -> newest ghost source), S2 -> first Utilities source, unknown S99 dropped');
$rrArt = json_decode((string)q1("SELECT content FROM st_artifacts WHERE run_id = ? AND kind = 'ranking'", [$rrid]), true);
ok(($rrArt['discovery_ids'] ?? null) === [(int)$d3['id'], (int)$d1['id']],
    'ranking artifact: discovery_ids = scans it read, newest first (unstructured + 9-day-old scans not listed)');
// timeline for used_in_ranking: older rank runs 10 days ago, scans 3 / 3 / 2 days ago, this ranking 1 day ago (old scan: 9 days ago = 8 days before it)
$rankAt = $tAgo(24);
h_x($pdo, "UPDATE st_runs SET started_at = ? WHERE job = 'rank' AND id <> ?", [$tAgo(240), $rrid]);
h_x($pdo, 'UPDATE st_runs SET started_at = ? WHERE id = ?', [$rankAt, $rrid]);
$dAt = [(int)$d1['id'] => $tAgo(72), (int)$d2['id'] => $tAgo(72), (int)$d3['id'] => $tAgo(48)];
foreach ($dAt as $aid => $t) h_x($pdo, 'UPDATE st_artifacts SET created_at = ? WHERE id = ?', [$t, $aid]);

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
// failed idea evaluation / more research (final, after the retry) -> one notification each: the live card promises one when the run ends (before: only success notified)
$eo = (int)q1('SELECT id FROM st_opps ORDER BY id LIMIT 1');
$alertOf = fn(string $key) => q('SELECT level, title, body, metrics FROM app_alerts WHERE akey = ?', ['st|' . $key]);
$eoSt = (string)q1('SELECT status FROM st_opps WHERE id = ?', [$eo]);
h_x($pdo, "UPDATE st_opps SET status = 'EVALUATING' WHERE id = ?", [$eo]);   // as st_opp_decide MORE_RESEARCH / st_idea_add leave it
$fake([]);   // empty queue: the fake API answers 404 to every attempt
$fe1 = st_run_new($pdo, 'evaluate_idea', ['ref' => $eo, 'trigger' => 'manual', 'by' => 'test']);
$fe2 = st_run_new($pdo, 'more_research', ['ref' => $eo, 'trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
$fr1 = $pdo->query("SELECT * FROM st_runs WHERE id = $fe1")->fetch();
$fn1 = $alertOf("runfail|$fe1"); $fn2 = $alertOf("runfail|$fe2");
ok($eo > 0 && $fr1['status'] === 'FAILED' && (int)$fr1['attempts'] === 2 && (string)$fr1['error_text'] !== '' && q1('SELECT status FROM st_runs WHERE id = ?', [$fe2]) === 'FAILED'
    && count($fn1) === 1 && $fn1[0]['title'] === 'Idea evaluation stopped' && (int)$fn1[0]['level'] === 1 && (json_decode($fn1[0]['metrics'], true)['route'] ?? '') === "studio/opp/$eo"
    && str_starts_with($fn1[0]['body'], rtrim(st_cut((string)$fr1['error_text'], 300), '. ') . ". Open the idea to try again.\nWhen: ")
    && count($fn2) === 1 && $fn2[0]['title'] === 'More research stopped' && (json_decode($fn2[0]['metrics'], true)['route'] ?? '') === "studio/opp/$eo"
    && (int)q1('SELECT COUNT(*) FROM st_notified WHERE nkey IN (?, ?)', ["eval|$eo|$fe1", "eval|$eo|$fe2"]) === 0,
    'failed idea evaluation / more research (after the retry) -> one notification each: reason + "Open the idea to try again", route to the idea; no "evaluated" notification');
ok(q1('SELECT status FROM st_opps WHERE id = ?', [$eo]) === 'NEW', 'failed evaluation puts the idea back from EVALUATING to NEW (More research / Approve / Reject show again, so "try again" works)');
h_x($pdo, 'UPDATE st_opps SET status = ? WHERE id = ?', [$eoSt, $eo]);
// crash recovery: the last attempt killed too (timeout / restart) -> one notification with st_run_exec's key (rankfail / runfail), never twice; earlier attempt killed -> requeued, no notification
$ce = st_run_new($pdo, 'evaluate_idea', ['ref' => $eo, 'trigger' => 'manual', 'by' => 'test']);
$cr = st_run_new($pdo, 'rank', ['trigger' => 'manual', 'by' => 'test']);
$cq = st_run_new($pdo, 'more_research', ['ref' => $eo, 'trigger' => 'manual', 'by' => 'test']);
foreach ([$ce => ST_MAX_ATTEMPTS, $cr => ST_MAX_ATTEMPTS, $cq => 1] as $cid => $cat)
    h_x($pdo, "UPDATE st_runs SET status = 'RUNNING', attempts = ?, started_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 40 MINUTE) WHERE id = ?", [$cat, $cid]);
$fake([]);
st_work($pdo, 60);
st_work($pdo, 60);
st_run_end_notify($pdo, $ce, 'evaluate_idea', $eo, 'FAILED', ['a later error']);   // same run ending again (st_run_exec path): deduped
$cn = [$alertOf("runfail|$ce"), $alertOf("rankfail|$cr"), $alertOf("runfail|$cq")];
$crow = fn(int $id) => $pdo->query("SELECT status, attempts, error_text FROM st_runs WHERE id = $id")->fetch();
ok($crow($ce)['status'] === 'FAILED' && $crow($ce)['error_text'] === 'run interrupted (server timeout/restart)' && $crow($cr)['status'] === 'FAILED'
    && count($cn[0]) === 1 && $cn[0][0]['title'] === 'Idea evaluation stopped' && str_starts_with($cn[0][0]['body'], "Run interrupted (server timeout/restart). Open the idea to try again.\nWhen: ")
    && (json_decode($cn[0][0]['metrics'], true)['route'] ?? '') === "studio/opp/$eo"
    && count($cn[1]) === 1 && $cn[1][0]['title'] === 'Weekly ranking stopped' && str_starts_with($cn[1][0]['body'], 'Run interrupted (server timeout/restart). ')
    && (json_decode($cn[1][0]['metrics'], true)['route'] ?? '') === 'studio/ranking'
    && (int)q1('SELECT COUNT(*) FROM st_notified WHERE nkey IN (?, ?)', ["runfail|$ce", "rankfail|$cr"]) === 2,
    'crash recovery, last attempt killed: evaluate_idea / rank FAILED -> one "Run interrupted (server timeout/restart)" notification each, same key as a normal failure (never twice)');
ok($crow($cq)['status'] === 'FAILED' && (int)$crow($cq)['attempts'] === 2 && count($cn[2]) === 1 && $cn[2][0]['title'] === 'More research stopped'
    && !str_starts_with($cn[2][0]['body'], 'Run interrupted'),
    'crash recovery, first attempt killed: requeued without a notification; its final failure (attempt 2) notifies once with the real reason');
ok((int)q1("SELECT COUNT(*) FROM st_events WHERE kind = 'decision' AND actor = 'sagar'") >= 4, 'decisions in searchable activity history');

// ---------------------------------------------------------------- streaming Claude calls: owner's weekly ranking failed at step 4/6 (HTTP 0 after 300 s, answer cut at 32000 tokens, cost not recorded)
// every call below goes through the fake streaming API (php -S, fake_claude.php): real SSE over curl, chunks cut mid-line, errors / cuts / stalls on demand
$reqs = function () use ($Q) { $o = []; foreach (array_filter(explode("\n", (string)@file_get_contents("$Q.log"))) as $l) $o[] = (array)json_decode(substr($l, (int)strpos($l, ' ') + 1), true); return $o; };
$rankRun = function (array $queue) use ($pdo, $fake, $Q) {
    @unlink("$Q.log"); $fake($queue);
    $id = st_run_new($pdo, 'rank', ['trigger' => 'manual', 'by' => 'test']);
    $t0 = microtime(true); st_work($pdo, 60);
    return $pdo->query("SELECT * FROM st_runs WHERE id = $id")->fetch() + ['secs' => microtime(true) - $t0];
};
$usageOf = fn(int $id) => q('SELECT ok, in_tokens, out_tokens, cost_usd FROM st_usage WHERE run_id = ? ORDER BY id', [$id]);
$oppS = fn(string $t) => ['title' => $t, 'concept' => 'c', 'category' => 'Tools', 'countries' => ['IN'], 'competitors' => [], 'monetization' => ['banner'], 'risks' => [], 'why_build' => 'b',
    'why_avoid' => 'a', 'complexity' => 'Low', 'recommendation' => 'TEST FIRST', 'decision_card' => $card, 'factors' => $fx(['demand' => ['rating' => 6, 'basis' => 'INFERENCE', 'evidence' => ['S1'], 'note' => 'n']])];
// max_tokens answer: $total ideas, cut inside the title of idea $keep + 1
$cutResp = function (string $p, int $keep, int $total = 5) use ($oppS) {
    $j = json_encode(['opportunities' => array_map(fn($i) => $oppS("$p $i"), range(1, $total))]);
    return [200, ['model' => 'claude-opus-5-5', 'stop_reason' => 'max_tokens', 'usage' => ['input_tokens' => 9000, 'output_tokens' => 64000], 'content' => [['type' => 'text', 'text' => substr($j, 0, (int)strpos($j, "\"$p " . ($keep + 1)) + 4)]]]];
};
$slowMsg = [200, ['model' => 'claude-opus-5-5', 'stop_reason' => 'end_turn', 'usage' => ['input_tokens' => 7777, 'output_tokens' => 999], 'content' => [['type' => 'text', 'text' => str_repeat('slow answer text ', 15)]]]];
$errOf = fn(array $r, string $want = 'DONE') => $r['status'] !== $want ? ' · ' . $r['status'] . ' ' . $r['error_text'] : '';
// T1: answer cut at max_tokens after 4 complete ideas -> kept, run PARTIAL, ranking + Gate 1 still happen
$t1 = $rankRun([$webResp(['https://example.com/t1cut'], 'T1 web notes'), $cutResp('Cut idea', 4)]);
$t1Arts = q('SELECT id, kind, content FROM st_artifacts WHERE run_id = ? ORDER BY id', [(int)$t1['id']]);
$t1Rank = json_decode((string)($t1Arts[1]['content'] ?? '{}'), true);
ok($t1['status'] === 'PARTIAL' && str_contains($t1['error_text'], 'AI answer was cut off (max_tokens 64000); kept 4 ideas') && (int)$t1['errors'] === 1 && (int)$t1['attempts'] === 1
    && (int)q1("SELECT COUNT(*) FROM st_opps WHERE title LIKE 'Cut idea %'") === 4 && array_column($t1Arts, 'kind') === ['rank_notes', 'ranking'] && ($t1Rank['truncated'] ?? null) === true
    && array_key_exists('reused_from', $t1Rank) && $t1Rank['reused_from'] === null && count($t1Rank['opportunity_ids'] ?? []) === 4
    && (int)q1('SELECT COUNT(*) FROM st_notified WHERE nkey = ?', ['rank|' . $t1['id']]) === 1 && str_starts_with((string)$t1['summary'], '4 opportunities · ranking artifact #'),
    'answer cut at max_tokens with 4 complete ideas: 4 stored, run PARTIAL ("kept 4 ideas"), ranking artifact (truncated) + Gate 1 notification' . $errOf($t1, 'PARTIAL'));
ok(array_map(fn($x) => [$x['label'], $x['state'], $x['note']], st_run_row($t1)['steps']) === [['Web research: Tools', 'done', '1 source'], ['Your apps + daily discovery', 'done', st_n(st_port_apps($pdo), 'app')],
    ['AI ranking', 'fail', '4 ideas'], ['Save + notify you', 'done', '']], 'cut-off ranking (PARTIAL): AI ranking step "fail" with "4 ideas", save still done');
$rq = $reqs();
ok(count($rq) === 2 && ($rq[0]['stream'] ?? null) === true && ($rq[1]['stream'] ?? null) === true && ($rq[1]['max_tokens'] ?? 0) === 64000 && ($rq[1]['output_config']['effort'] ?? '') === 'high'
    && str_contains($rq[1]['system'] ?? '', 'turn research notes into 8-12 app opportunities') && str_contains($rq[1]['system'], 'Be brief: concept at most 30 words; each factor note at most 15 words')
    && str_contains($rq[1]['system'], 'at most 3 competitors, 4 risks and 4 monetization items'), 'ranking request: streamed, max_tokens 64000, effort high, 8-12 ideas with brevity limits');
$n1 = json_decode((string)($t1Arts[0]['content'] ?? '{}'), true);
$e1 = (int)q1('SELECT id FROM st_evidence WHERE url = ? AND run_id = ?', ['https://example.com/t1cut', (int)$t1['id']]);
ok(str_contains($n1['notes'] ?? '', "## Notes: Tools\nT1 web notes") && !str_contains($n1['notes'], 'Daily discovery') && ($n1['smap'] ?? null) === ['S1' => $e1] && $e1 > 0 && ($n1['n'] ?? 0) === 1
    && ($n1['ads'] ?? null) === '' && ($n1['countries'] ?? null) === ['IN', 'US'] && ($n1['categories'] ?? null) === ['Tools'] && ($n1['done'] ?? null) === ['Tools'] && ($n1['ads_ok'] ?? null) === false
    && ($n1['runs'] ?? null) === [$t1['run_uid']]
    && q1("SELECT kind FROM st_events WHERE run_id = ? AND msg LIKE '%rank_notes v%'", [(int)$t1['id']]) === 'log' && q1("SELECT kind FROM st_events WHERE run_id = ? AND msg LIKE '%ranking v%'", [(int)$t1['id']]) === 'artifact',
    'rank_notes artifact: web notes (before discovery), S map, n, ads, countries, categories, done categories, ads_ok (Ads not configured), runs; logged as kind log (not in the app activity feed)');
// T2: only 2 complete ideas -> clear error, nothing stored, run FAILED (no retry: research steps done); its notes stay reusable
$t2 = $rankRun([$webResp(['https://example.com/t2'], 'T2 web notes'), $cutResp('Cut two', 2)]);
$N2 = (int)q1("SELECT id FROM st_artifacts WHERE run_id = ? AND kind = 'rank_notes'", [(int)$t2['id']]);
$e2 = (int)q1('SELECT id FROM st_evidence WHERE url = ? AND run_id = ?', ['https://example.com/t2', (int)$t2['id']]);
ok($t2['status'] === 'FAILED' && str_contains($t2['error_text'], "Claude's answer was cut off (max_tokens 64000): only 2 complete ideas") && (int)$t2['attempts'] === 1
    && (int)q1("SELECT COUNT(*) FROM st_opps WHERE title LIKE 'Cut two %'") === 0 && (int)q1("SELECT COUNT(*) FROM st_artifacts WHERE run_id = ? AND kind = 'ranking'", [(int)$t2['id']]) === 0 && $N2 > 0,
    'answer cut with only 2 complete ideas: run FAILED with a clear error, no ideas / ranking stored, research notes saved' . $errOf($t2, 'FAILED'));
$t2n = q("SELECT level, title, body, metrics FROM app_alerts WHERE rule = 'studio_run' AND akey = ?", ['st|rankfail|' . (int)$t2['id']]);
ok(count($t2n) === 1 && $t2n[0]['title'] === 'Weekly ranking stopped' && (int)$t2n[0]['level'] === 1 && (json_decode($t2n[0]['metrics'], true)['route'] ?? '') === 'studio/ranking'
    && str_starts_with($t2n[0]['body'], "Claude's answer was cut off (max_tokens 64000): only 2 complete ideas. Research is saved: Run again within 24 h, no new web search cost."),
    'failed weekly ranking (final, no retry) -> one notification: the reason + research saved (the live card promises a notification when it ends)');
$t2r = st_run_row($t2);
ok(array_map(fn($x) => [$x['label'], $x['state']], $t2r['steps']) === [['Web research: Tools', 'done'], ['Your apps + daily discovery', 'done'], ['AI ranking', 'fail'], ['Save + notify you', 'wait']]
    && $t2r['steps'][0]['note'] === '1 source' && $t2r['pct'] >= 50 && $t2r['pct'] <= 74 && $t2r['steps_done'] === 2 && str_contains((string)$t2r['error_text'], 'only 2 complete ideas'),
    "failed ranking: AI ranking step \"fail\", save waiting, {$t2r['pct']}% (stopped inside step 3 of 4), error text for the app");
$re = st_rank_notes_reuse($pdo, 0, ['Tools'], ['US', 'IN']);
ok(($re['id'] ?? 0) === $N2 && $re['uid'] === $t2['run_uid'] && $re['smap'] === ['S1' => $e2] && $re['n'] === 1 && str_contains($re['notes'], 'T2 web notes') && $re['ads'] === ''
    && $re['done'] === ['Tools'] && $re['ads_ok'] === false && $re['runs'] === [$t2['run_uid']],
    'reuse: notes of the failed run found (countries in any order, done categories, ads_ok, runs); PARTIAL run with a ranking (T1) not offered');
$noRe = [st_rank_notes_reuse($pdo, 0, ['Tools', 'Utilities'], ['IN', 'US']), st_rank_notes_reuse($pdo, 0, ['Tools'], ['IN'])];
h_x($pdo, 'UPDATE st_artifacts SET created_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR) WHERE id = ?', [$N2]);
$noRe[] = st_rank_notes_reuse($pdo, 0, ['Tools'], ['IN', 'US']);
h_x($pdo, 'UPDATE st_artifacts SET created_at = UTC_TIMESTAMP() WHERE id = ?', [$N2]);
$reSt = [];
foreach (['DONE', 'RUNNING', 'QUEUED', 'BLOCKED', 'CANCELLED', 'FAILED'] as $s) {
    h_x($pdo, 'UPDATE st_runs SET status = ? WHERE id = ?', [$s, (int)$t2['id']]);
    $reSt[$s] = [(st_rank_notes_reuse($pdo, 0, ['Tools'], ['IN', 'US'])['id'] ?? 0) === $N2, (st_rank_notes_reuse($pdo, (int)$t2['id'], ['Tools'], ['IN', 'US'])['id'] ?? 0) === $N2];
}
$evRow = $pdo->query("SELECT * FROM st_evidence WHERE id = $e2")->fetch();
h_x($pdo, 'DELETE FROM st_evidence WHERE id = ?', [$e2]);
$noRe[] = st_rank_notes_reuse($pdo, 0, ['Tools'], ['IN', 'US']);
h_x($pdo, 'INSERT INTO st_evidence (`' . implode('`,`', array_keys($evRow)) . '`) VALUES (' . implode(',', array_fill(0, count($evRow), '?')) . ')', array_values($evRow));
ok($noRe === [null, null, null, null] && (st_rank_notes_reuse($pdo, 0, ['Tools'], ['IN', 'US'])['id'] ?? 0) === $N2
    && $reSt === ['DONE' => [false, true], 'RUNNING' => [false, true], 'QUEUED' => [false, true], 'BLOCKED' => [true, true], 'CANCELLED' => [true, true], 'FAILED' => [true, true]],
    'reuse refused: other categories / countries, older than 24 h, an evidence row gone (back -> reusable again), run DONE / RUNNING / QUEUED; '
    . 'offered: FAILED / PARTIAL / BLOCKED (budget) / CANCELLED, and to the same run (crash-requeued attempt 2) in any status · ' . json_encode($reSt));
// T3: next rank run reuses T2's notes: no web search, one AI call, steps as usual, evidence still citable
$t3 = $rankRun([$jsonResp(['opportunities' => [$oppS('Reuse idea')]])]);
$rq = $reqs();
$t3Rank = json_decode((string)q1("SELECT content FROM st_artifacts WHERE run_id = ? AND kind = 'ranking'", [(int)$t3['id']]), true);
$OR = $pdo->query("SELECT * FROM st_opps WHERE title = 'Reuse idea'")->fetch();
ok($t3['status'] === 'DONE' && count($rq) === 1 && !isset($rq[0]['tools']) && str_contains($rq[0]['messages'][0]['content'] ?? '', "## Notes: Tools\nT2 web notes") && (int)$t3['steps_done'] === 4
    && (int)$t3['steps_total'] === 4 && (int)$t3['ai_calls'] === 1 && (int)$t3['searches'] === 0 && ($t3Rank['reused_from'] ?? null) === ['artifact_id' => $N2, 'run_uid' => $t2['run_uid'], 'runs' => [$t2['run_uid']]]
    && str_ends_with((string)$t3['summary'], ' · research notes reused from ' . $t2['run_uid'])
    && (int)q1('SELECT COUNT(*) FROM st_events WHERE run_id = ? AND msg LIKE ?', [(int)$t3['id'], '%reusing research notes from ' . $t2['run_uid'] . ' (no new web search)%']) === 1
    && $OR && (json_decode($OR['factors'], true)['demand']['evidence'] ?? null) === [$e2] && (int)q1("SELECT COUNT(*) FROM st_artifacts WHERE run_id = ? AND kind = 'rank_notes'", [(int)$t3['id']]) === 0
    && st_rank_notes_reuse($pdo, 0, ['Tools'], ['IN', 'US']) === null,
    'rank run after a failed one: notes reused (no web search, 1 AI call, 4/4 steps), reused_from + summary + stage log, S1 still cites the evidence, notes used up' . $errOf($t3));
ok(array_map(fn($x) => [$x['label'], $x['state'], $x['note']], array_slice(st_run_row($t3)['steps'], 0, 1)) === [['Web research: Tools', 'done', 'reused']] && st_run_row($t3)['steps'][2]['note'] === '1 idea',
    'reused research shows as a done step with note "reused"');
// F1: overloaded error event AFTER output started -> no retry (would bill twice), FAILED; the partial call is still recorded (ok 0)
$f1 = $rankRun([$webResp(['https://example.com/f1'], 'F1 web notes'), $slowMsg + [2 => ['error' => 4]], $jsonResp(['opportunities' => [$oppS('Never used')]])]);
$u = $usageOf((int)$f1['id']); $ul = end($u);
ok($f1['status'] === 'FAILED' && str_contains($f1['error_text'], 'Claude API error during answer: overloaded_error: Overloaded') && $left() === 1 && count($reqs()) === 2
    && count($u) === 2 && (int)$ul['ok'] === 0 && (int)$ul['in_tokens'] === 7777 && (float)$ul['cost_usd'] > 0 && (int)$f1['ai_calls'] === 2
    && str_contains((string)kv_get('ai_last_error'), 'overloaded_error') && str_ends_with((string)kv_get('ai_last_error'), '(studio)'),
    'stream error after output: not retried, run FAILED with a clear error, cost of the failed call recorded (ok 0, input tokens from message_start)' . $errOf($f1, 'FAILED'));
// F2: connection closed mid-answer (no message_stop) -> FAILED, no retry; reuses F1's notes
$f2 = $rankRun([$slowMsg + [2 => ['cut' => 6]], $jsonResp(['opportunities' => [$oppS('Never used')]])]);
$u = $usageOf((int)$f2['id']);
ok($f2['status'] === 'FAILED' && str_contains($f2['error_text'], 'Claude API answer stopped early (connection closed)') && count($reqs()) === 1 && $left() === 1
    && count($u) === 1 && (int)$u[0]['ok'] === 0 && (int)$u[0]['in_tokens'] === 7777 && (int)$u[0]['out_tokens'] >= 1
    && (int)q1('SELECT COUNT(*) FROM st_events WHERE run_id = ? AND msg LIKE ?', [(int)$f2['id'], '%reusing research notes from ' . $f1['run_uid'] . '%']) === 1,
    'stream cut mid-answer: run FAILED ("stopped early"), not retried, usage ok 0; research notes of the failed run reused' . $errOf($f2, 'FAILED'));
// F3: stream stalls (no bytes, no ping) -> idle abort after ST_STREAM_IDLE_SEC, not after the full timeout
$f3 = $rankRun([$slowMsg + [2 => ['stall' => [5, 6]]], $jsonResp(['opportunities' => [$oppS('Never used')]])]);
ok($f3['status'] === 'FAILED' && preg_match('/Claude API did not answer in time \(\d+ s\): no data for 2 s/', $f3['error_text']) && $f3['secs'] < 6 && count($reqs()) === 1 && $left() === 1,
    sprintf('stalled stream: idle abort in %.1f s ("did not answer in time ... no data for 2 s"), not retried after output', $f3['secs']) . $errOf($f3, 'FAILED'));
// C1: Cancel pressed while a slow answer streams -> stream aborted within ~ST_STREAM_CHECK_SEC, run CANCELLED, partial usage recorded
$c1 = $rankRun([$slowMsg + [2 => ['delay' => 0.25, 'cancel' => 3]]]);
$u = $usageOf((int)$c1['id']);
ok($c1['status'] === 'CANCELLED' && $c1['secs'] < 6 && count($u) === 1 && (int)$u[0]['ok'] === 0 && (int)$u[0]['in_tokens'] === 7777,
    sprintf('cancel during a slow stream: run CANCELLED after %.1f s (stream needs ~10 s), usage ok 0 recorded', $c1['secs']) . $errOf($c1, 'CANCELLED'));
ok(array_column(st_run_row($c1)['steps'], 'state') === ['done', 'done', 'fail', 'wait'] && st_run_row($c1)['steps'][0]['note'] === 'reused', 'cancelled run: the step where it stopped (AI ranking) "fail", the rest waiting');
// D1: nothing received at all (not even headers) -> idle abort, retried once (no bill), then DONE
$d1r = $rankRun([$slowMsg + [2 => ['stall' => [0, 5]]], $jsonResp(['opportunities' => [$oppS('Stall retry idea')]])]);
ok($d1r['status'] === 'DONE' && count($reqs()) === 2 && (int)$d1r['ai_calls'] === 1 && $left() === 0 && (int)q1("SELECT COUNT(*) FROM st_opps WHERE title = 'Stall retry idea'") === 1
    && st_rank_notes_reuse($pdo, 0, ['Tools'], ['IN', 'US']) === null, 'no data at all: retried once, run DONE, only the answered call billed; reused notes used up' . $errOf($d1r));
// R1: HTTP 529 -> retried; overloaded error event BEFORE any output -> retried; both calls DONE
$r1 = $rankRun([[529, ['type' => 'error', 'error' => ['type' => 'overloaded_error', 'message' => 'Overloaded']]], $webResp(['https://example.com/r1'], 'R1 web notes'),
    $slowMsg + [2 => ['error' => 2]], $jsonResp(['opportunities' => [$oppS('Retry idea')]])]);
ok($r1['status'] === 'DONE' && count($reqs()) === 4 && $left() === 0 && array_map('intval', array_column($usageOf((int)$r1['id']), 'ok')) === [1, 0, 1]
    && (int)q1("SELECT COUNT(*) FROM st_opps WHERE title = 'Retry idea'") === 1, 'HTTP 529 and an overloaded error event before output: each retried once, run DONE (failed attempt recorded ok 0)' . $errOf($r1));
// K1: ranking stream cut (connection closed / timeout) after 4 complete ideas -> salvaged like max_tokens: 4 kept, run PARTIAL, not retried, cut call billed ok 0
$kj = json_encode(['opportunities' => array_map(fn($i) => $oppS("Kept cut $i"), range(1, 5))]);
$k1 = $rankRun([$webResp(['https://example.com/k1'], 'K1 web notes'), [200, ['model' => 'claude-opus-5-5', 'stop_reason' => 'end_turn', 'usage' => ['input_tokens' => 9100, 'output_tokens' => 40000],
    'content' => [['type' => 'text', 'text' => $kj]]], ['cut' => 3 + intdiv((int)strpos($kj, '"Kept cut 5') + 7, 7)]]]);   // text deltas are 7 chars from event 3: cut inside idea 5's title
$u = $usageOf((int)$k1['id']);
ok($k1['status'] === 'PARTIAL' && str_contains($k1['error_text'], 'AI answer was cut off (Claude API answer stopped early (connection closed)); kept 4 ideas') && count($reqs()) === 2
    && (int)q1("SELECT COUNT(*) FROM st_opps WHERE title LIKE 'Kept cut %'") === 4 && array_map('intval', array_column($u, 'ok')) === [1, 0] && (int)$u[1]['in_tokens'] === 9100 && (int)$u[1]['out_tokens'] > 100
    && (json_decode((string)q1("SELECT content FROM st_artifacts WHERE run_id = ? AND kind = 'ranking'", [(int)$k1['id']]), true)['truncated'] ?? null) === true,
    'ranking stream cut after 4 complete ideas: kept (PARTIAL, "cut off (... stopped early ...); kept 4 ideas"), ranking truncated, not retried, cut call billed ok 0' . $errOf($k1, 'PARTIAL'));
// FB3: answer cut at max_tokens after a mid-stream fallback: the fallback model continued the partial JSON -> salvage on the whole text (4 complete ideas, not 0)
$c5 = $cutResp('Fb idea', 4)[1]; $tx = $c5['content'][0]['text']; $sp = (int)strpos($tx, '"Fb idea 3') + 5;
$c5['content'] = [['type' => 'text', 'text' => substr($tx, 0, $sp)], $fbB, ['type' => 'text', 'text' => substr($tx, $sp)]];
$fb3 = $rankRun([$webResp(['https://example.com/fb3'], 'FB3 web notes'), [200, $c5]]);
ok($fb3['status'] === 'PARTIAL' && str_contains($fb3['error_text'], 'AI answer was cut off (max_tokens 64000); kept 4 ideas') && (int)q1("SELECT COUNT(*) FROM st_opps WHERE title LIKE 'Fb idea %'") === 4,
    'max_tokens after a mid-stream fallback (JSON continued across the marker): 4 complete ideas salvaged from partial + continuation' . $errOf($fb3, 'PARTIAL'));
// HS: healthy but slow answer: bytes keep coming for longer than ST_STREAM_IDLE_SEC (2 s) and many ST_STREAM_CHECK_SEC (1 s), on a connection with a 2 s MySQL wait_timeout:
// not aborted (idle timer reset by data), and the in-stream DB ping keeps the connection alive for the usage row written after the call
$hp = new PDO('mysql:host=' . getenv('TEST_DB_HOST') . ';dbname=' . getenv('TEST_DB_NAME') . ';charset=utf8mb4', (string)getenv('TEST_DB_USER'), (string)getenv('TEST_DB_PASS'),
    [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC, PDO::ATTR_EMULATE_PREPARES => true]);
$hp->exec("SET time_zone = '+00:00'"); $hp->exec('SET SESSION wait_timeout = 2');
@unlink("$Q.log"); $fake([$webResp(['https://example.com/hs'], 'HS web notes'), $jsonResp(['opportunities' => [$oppS('Healthy slow idea')]]) + [2 => ['delay' => 0.02]]]);
$hid = st_run_new($pdo, 'rank', ['trigger' => 'manual', 'by' => 'test']);
$t0 = microtime(true); $hsErr = '';
try { st_run_exec($hp, $hid); } catch (Throwable $e) { $hsErr = ' · ' . $e->getMessage(); }
$hsSecs = microtime(true) - $t0; $hp = null;
$hs = $pdo->query("SELECT * FROM st_runs WHERE id = $hid")->fetch();
ok($hs['status'] === 'DONE' && $hsSecs > 3.5 && count($reqs()) === 2 && array_map('intval', array_column($usageOf($hid), 'ok')) === [1, 1] && (int)q1("SELECT COUNT(*) FROM st_opps WHERE title = 'Healthy slow idea'") === 1,
    sprintf('healthy slow stream (%.1f s, idle limit 2 s, check every 1 s, MySQL wait_timeout 2 s): not aborted, DONE, both calls billed ok 1', $hsSecs) . $hsErr . $errOf($hs));
// BL: budget runs out after the first of 2 categories -> BLOCKED, the paid-for notes of the done category saved; after the limit is raised the next run researches only the missing one
kv_set('st_categories', json_encode(['Tools', 'Utilities']));
kv_set('st_ai_budget_usd', (string)(st_month_spend($pdo) + 0.001));
$bl = $rankRun([$webResp(['https://example.com/bl-tools'], 'BL tools notes'), $webResp(['https://example.com/never'], 'never')]);
$blN = (array)json_decode((string)q1("SELECT content FROM st_artifacts WHERE run_id = ? AND kind = 'rank_notes'", [(int)$bl['id']]), true);
ok($bl['status'] === 'BLOCKED' && $left() === 1 && str_contains($blN['notes'] ?? '', "## Notes: Tools\nBL tools notes") && ($blN['done'] ?? null) === ['Tools'] && ($blN['categories'] ?? null) === ['Tools', 'Utilities'],
    'budget reached between categories: run BLOCKED, notes of the researched category saved (done = Tools only)' . $errOf($bl, 'BLOCKED'));
ok(st_rank_notes_reuse($pdo, 0, ['Tools', 'Utilities'], ['IN', 'US']) !== null && st_run_rows($pdo, [$bl])[0]['can_reuse'] === false,
    'only part of the research saved (Tools, not Utilities): Run again still reuses it, but can_reuse false (the app must not promise "no new web search cost")');
kv_set('st_ai_budget_usd', '40');
$bl2 = $rankRun([$webResp(['https://example.com/bl-util'], 'BL utilities notes'), $jsonResp(['opportunities' => [$oppS('Budget resume idea')]])]);
$rq = $reqs();
$bl2N = (array)json_decode((string)q1("SELECT content FROM st_artifacts WHERE run_id = ? AND kind = 'rank_notes'", [(int)$bl2['id']]), true);
$bl2R = (array)json_decode((string)q1("SELECT content FROM st_artifacts WHERE run_id = ? AND kind = 'ranking'", [(int)$bl2['id']]), true);
$eBt = (int)q1('SELECT id FROM st_evidence WHERE url = ?', ['https://example.com/bl-tools']); $eBu = (int)q1('SELECT id FROM st_evidence WHERE url = ?', ['https://example.com/bl-util']);
ok($bl2['status'] === 'DONE' && count($rq) === 2 && isset($rq[0]['tools']) && str_contains($rq[0]['messages'][0]['content'] ?? '', 'Research theme: Utilities')
    && str_contains($rq[1]['messages'][0]['content'] ?? '', "## Notes: Tools\nBL tools notes") && str_contains($rq[1]['messages'][0]['content'], "## Notes: Utilities\nBL utilities notes")
    && (int)$bl2['steps_done'] === 5 && (int)$bl2['steps_total'] === 5 && ($bl2N['done'] ?? null) === ['Tools', 'Utilities'] && ($bl2N['smap'] ?? null) === ['S1' => $eBt, 'S2' => $eBu]
    && ($bl2N['runs'] ?? null) === [$bl2['run_uid'], $bl['run_uid']] && ($bl2R['reused_from']['runs'] ?? null) === [$bl['run_uid']]
    && (int)q1('SELECT COUNT(*) FROM st_events WHERE run_id = ? AND msg LIKE ?', [(int)$bl2['id'], '%reusing research notes from ' . $bl['run_uid'] . ' (new research only for: Utilities)%']) === 1
    && st_rank_notes_reuse($pdo, 0, ['Tools', 'Utilities'], ['IN', 'US']) === null,
    'after the budget block: notes reused, only the missing category researched (1 web call), S numbers continue, 5/5 steps, notes chain used up' . $errOf($bl2));
ok(array_map(fn($x) => [$x['label'], $x['note']], array_slice(st_run_row($bl2)['steps'], 0, 2)) === [['Web research: Tools', 'reused'], ['Web research: Utilities', '1 source']]
    && array_column(st_run_row($bl)['steps'], 'state') === ['done', 'fail', 'wait', 'wait', 'wait'],
    'budget block: Utilities step "fail" (BLOCKED there); next run: Tools "reused", Utilities "1 source"');
kv_set('st_categories', json_encode(['Tools']));
// NS1: ST_NO_STREAM (or no curl) -> old non-streaming http_json path still works; network failure text
putenv('ST_NO_STREAM=1');
$ns = $rankRun([$webResp(['https://example.com/ns'], 'NS web notes'), $jsonResp(['opportunities' => [$oppS('No stream idea')]])]);
$rq = $reqs();
@unlink("$Q.log"); $fake([]);
$nd = st_run_new($pdo, 'discover', ['trigger' => 'manual', 'by' => 'test']);
st_work($pdo, 60);
$ndr = $pdo->query("SELECT * FROM st_runs WHERE id = $nd")->fetch();
putenv('ST_NO_STREAM');
ok($ns['status'] === 'DONE' && count($rq) === 2 && !array_filter($rq, fn($b) => array_key_exists('stream', $b)) && (int)q1("SELECT COUNT(*) FROM st_opps WHERE title = 'No stream idea'") === 1
    && $ndr['status'] === 'FAILED' && str_contains($ndr['error_text'], 'Claude API connection failed: network disabled in tests'),
    'ST_NO_STREAM: non-streaming path (no "stream" in the request) still ranks; network error -> "connection failed"' . $errOf($ns));
// NS2: streaming cannot connect on the owner's host (DNS / SSL / CA / refused, nothing received) -> same call through the owner's http_json; streaming off for 6 h
st_stream_url('http://127.0.0.1:1/fake_claude.php');
$ns2 = $rankRun([$webResp(['https://example.com/ns2'], 'NS2 web notes'), $jsonResp(['opportunities' => [$oppS('Connect fallback idea')]])]);
$rq = $reqs();
$until = (int)(kv_get('st_nostream_until') ?? 0);
st_stream_url('');
kv_set('st_nostream_until', '0');
ok($ns2['status'] === 'DONE' && count($rq) === 2 && !array_filter($rq, fn($b) => array_key_exists('stream', $b)) && (int)q1("SELECT COUNT(*) FROM st_opps WHERE title = 'Connect fallback idea'") === 1
    && $until > time() + 5 * 3600 && (int)q1("SELECT COUNT(*) FROM st_events WHERE run_id = ? AND msg LIKE 'Claude streaming could not connect%'", [(int)$ns2['id']]) === 1
    && (int)q1('SELECT COUNT(*) FROM st_usage WHERE run_id = ? AND ok = 0', [(int)$ns2['id']]) === 0,
    'streaming cannot connect (refused, nothing received): the same call goes through http_json, streaming off for 6 h (ranking call skips it), logged, no failed usage row' . $errOf($ns2));
// FB1: mid-stream server-side fallback on a pause_turn web call (streaming: the fallback model continues the partial text) + on the structuring call (fallback model restarted the JSON: text after the marker parsed), echo follows the API rule
$discRun = function (array $queue) use ($pdo, $fake, $Q) {
    @unlink("$Q.log"); $fake($queue);
    $id = st_run_new($pdo, 'discover', ['trigger' => 'manual', 'by' => 'test']); st_work($pdo, 60);
    return [$pdo->query("SELECT * FROM st_runs WHERE id = $id")->fetch(), (array)json_decode((string)q1("SELECT content FROM st_artifacts WHERE run_id = ? AND kind = 'daily_notes'", [$id]), true)];
};
$wr = fn(string $id, string $url) => ['type' => 'web_search_tool_result', 'tool_use_id' => $id, 'content' => [['type' => 'web_search_result', 'url' => $url, 'title' => "T $url", 'page_age' => '1 day ago']]];
$su = fn(string $id) => ['type' => 'server_tool_use', 'id' => $id, 'name' => 'web_search', 'input' => ['query' => "q $id"]];
$msgOf = fn(string $stop, array $content) => [200, ['model' => 'claude-opus-5-5', 'stop_reason' => $stop, 'usage' => $usage, 'content' => $content]];
$fbFind = $dF('NEW_APP', 'After fallback finding', 'd', 'SAID', ['S1'], 'Tools', ['IN']);
[$fr1, $fc1] = $discRun([$msgOf('pause_turn', [['type' => 'thinking', 'thinking' => 'pre', 'signature' => 'sigPre'], $su('f1'), $wr('f1', 'https://fb.example/pre'), $su('f2'),
    ['type' => 'text', 'text' => 'partial notes + '], $fbB, ['type' => 'thinking', 'thinking' => 'post', 'signature' => 'sigPost'], $su('f3'), $wr('f3', 'https://fb.example/post'),
    ['type' => 'text', 'text' => 'notes after fallback']]), $msgOf('end_turn', [['type' => 'text', 'text' => ' + more notes']]),
    $msgOf('end_turn', [['type' => 'text', 'text' => '{"findings":[{"type":"NEW_APP","title":"Dead'], $fbB, ['type' => 'text', 'text' => json_encode(['findings' => [$fbFind]])]])]);
$rq = $reqs();
$ec = (array)($rq[1]['messages'][1]['content'] ?? []);
ok($fr1['status'] === 'DONE' && count($rq) === 3 && array_column($fc1['findings'] ?? [], 'title') === ['After fallback finding']
    && str_contains($fc1['notes'] ?? '', "partial notes + notes after fallback + more notes") && count($fc1['sources'] ?? []) === 2
    && array_column($ec, 'type') === ['server_tool_use', 'web_search_tool_result', 'text', 'fallback', 'thinking', 'server_tool_use', 'web_search_tool_result', 'text']
    && ($ec[0]['input'] ?? null) === ['query' => 'q f1'] && ($ec[3] ?? null) === $fbB && ($ec[4]['signature'] ?? '') === 'sigPost' && ($ec[2]['text'] ?? '') === 'partial notes + ',
    'mid-stream fallback (streaming): notes = declined partial + the fallback model\'s continuation; structured answer that restarted after the marker still parsed; '
    . 'pause_turn echo drops pre-fallback thinking + unpaired search, keeps paired search + text + the fallback block in place' . $errOf($fr1));
// FB2: fallback model continued the declined partial (JSON split across the marker) -> whole text parsed
[$fr2, $fc2] = $discRun([$webResp(['https://fb.example/two'], 'fb2 notes'), $msgOf('end_turn', [['type' => 'text', 'text' => '{"findings":[{"type":"OTHER","title":"Contin'], $fbB,
    ['type' => 'text', 'text' => 'ued finding","detail":"d","basis":"GUESS","sources":[],"category":"","countries":[]}]}']])]);
ok($fr2['status'] === 'DONE' && array_column($fc2['findings'] ?? [], 'title') === ['Continued finding'], 'fallback continuation: JSON split across the fallback block still parsed' . $errOf($fr2));
h_x($pdo, "DELETE FROM st_artifacts WHERE kind = 'daily_notes' AND run_id IN (?, ?)", [(int)$fr1['id'], (int)$fr2['id']]);   // discovery views below expect their own scans only
// WT: st_work raises a short MySQL wait_timeout (shared hosting) for the long stream, never lowers a bigger one
$pdo->exec('SET SESSION wait_timeout = 60'); st_work($pdo, 1); $wt1 = (int)q1('SELECT @@session.wait_timeout');
$pdo->exec('SET SESSION wait_timeout = 30000'); st_work($pdo, 1); $wt2 = (int)q1('SELECT @@session.wait_timeout');
ok($wt1 === 7200 && $wt2 === 30000, "st_work: session wait_timeout 60 -> $wt1, 30000 -> $wt2");
// ---------------------------------------------------------------- live progress (view=live): what the app sees WHILE a run streams
// LV: slow weekly ranking; the fake API waits 1.6 s before chosen events and reads the run's live columns from the DB (like the app polling view=live)
$evAt = function (array $evs, callable $hit): int {   // probe index: the event right after the first one where $hit(text so far, thinking deltas so far, event) is true
    $acc = ''; $th = 0;
    foreach ($evs as $k => $e) {
        $d = json_decode(substr($e, (int)strpos($e, 'data: ') + 6), true) ?: [];
        if (($d['delta']['type'] ?? '') === 'text_delta') $acc .= $d['delta']['text'];
        if (($d['delta']['type'] ?? '') === 'thinking_delta') $th++;
        if ($hit($acc, $th, $d)) return $k + 1;
    }
    return -1;
};
$lvSrc = fn(string $id, string $u) => [['type' => 'server_tool_use', 'id' => $id, 'name' => 'web_search', 'input' => ['query' => "q $id"]],
    ['type' => 'web_search_tool_result', 'tool_use_id' => $id, 'content' => [['type' => 'web_search_result', 'url' => $u, 'title' => "T $u", 'page_age' => '1 day ago']]]];
$lvWeb = ['model' => 'claude-opus-5-5', 'stop_reason' => 'end_turn', 'usage' => $usage, 'content' => [...$lvSrc('lv1', 'https://lv.example/1'), ...$lvSrc('lv2', 'https://lv.example/2'),
    ['type' => 'text', 'text' => str_repeat('Live notes line [SOURCE SAYS]. ', 40)]]];
$lvRank = ['model' => 'claude-opus-5-5', 'stop_reason' => 'end_turn', 'usage' => ['input_tokens' => 5000, 'output_tokens' => 3000], 'content' => [
    ['type' => 'thinking', 'thinking' => str_repeat('weighing the notes ', 30), 'signature' => 'sigLv'], ['type' => 'text', 'text' => json_encode(['opportunities' => array_map(fn($i) => $oppS("Live idea $i"), [1, 2, 3])])]]];
$we = fake_sse_events($lvWeb); $re = fake_sse_events($lvRank);
$pW = [$evAt($we, fn($a, $t, $d) => ($d['type'] ?? '') === 'content_block_stop' && ($d['index'] ?? -1) === 2), $evAt($we, fn($a) => strlen($a) > 700)];   // 2nd search sent (no result yet); notes text flowing
$pR = [$evAt($re, fn($a, $t) => $t === 20), $evAt($re, fn($a) => count(st_json_salvage_list($a, 'opportunities')) === 1) + 3, $evAt($re, fn($a) => count(st_json_salvage_list($a, 'opportunities')) === 2) + 3];
@unlink("$Q.probe");
$lv = $rankRun([[200, $lvWeb, ['probe' => $pW]], [200, $lvRank, ['probe' => $pR]]]);
$pr = array_map(fn($l) => json_decode($l, true), array_values(array_filter(explode("\n", (string)@file_get_contents("$Q.probe")))));
$pv = function (int $i) use ($pr): array {
    $r = (array)($pr[$i]['row'] ?? []);
    return ['detail' => $r['detail'] ?? null, 'frac' => (float)($r['frac'] ?? -1), 'done' => (int)($r['steps_done'] ?? -1), 'id' => (int)($r['id'] ?? 0),
        'beat' => isset($r['beat_at']) ? strtotime($r['db_now'] . ' UTC') - strtotime($r['beat_at'] . ' UTC') : 99,
        'steps' => array_map(fn($x) => [$x['label'], $x['state'], $x['note']], st_steps_view($r['steps_json'] ?? null, 'RUNNING'))];
};
$P = array_map($pv, array_keys($pr));
$lvSum = json_encode(array_map(fn($x) => [$x['detail'], $x['frac'], $x['beat']], $P));
ok($lv['status'] === 'DONE' && count($P) === 5 && count(array_unique(array_column($P, 'id'))) === 1 && $P[0]['id'] === (int)$lv['id'] && max(array_column($P, 'beat')) <= 2,
    "live: 5 mid-stream DB probes of the same RUNNING run, heartbeat (beat_at) never older than 2 s while streaming · $lvSum" . $errOf($lv));
ok($P[0]['detail'] === '2 web searches' && $P[0]['frac'] === 0.24 && $P[0]['steps'][0] === ['Web research: Tools', 'on', '2 searches'] && $P[0]['steps'][2] === ['AI ranking', 'wait', '']
    && $P[1]['detail'] === 'writing notes' && $P[1]['frac'] > 0.6 && $P[1]['frac'] < 0.97 && $P[1]['done'] === 0,
    'live web research step: "2 web searches" (0.6 x 2 / 5 = 0.24, step note "2 searches"), then "writing notes" (bar past 0.6)');
ok($P[2]['detail'] === 'AI is thinking' && $P[2]['frac'] > 0 && $P[2]['frac'] < 0.25 && $P[2]['done'] === 2
    && array_slice($P[2]['steps'], 0, 3) === [['Web research: Tools', 'done', '2 sources'], ['Your apps + daily discovery', 'done', st_n(st_port_apps($pdo), 'app')], ['AI ranking', 'on', '']],
    'live ranking step while Claude only thinks: "AI is thinking", bar creeping (> 0, < 0.25); web step done with "2 sources"');
ok($P[3]['detail'] === '1 of ~10 ideas written' && $P[3]['frac'] === 0.32 && $P[3]['steps'][2] === ['AI ranking', 'on', '1 of ~10'] && $P[4]['detail'] === '2 of ~10 ideas written' && $P[4]['frac'] === 0.39
    && $P[4]['steps'][2] === ['AI ranking', 'on', '2 of ~10'] && $P[1]['frac'] > $P[0]['frac'] && $P[3]['frac'] > $P[2]['frac'] && $P[4]['frac'] > $P[3]['frac'],
    'live ranking step while ideas stream: complete ideas counted from the partial JSON ("1 of ~10 ideas written" -> 0.32, "2 of ~10" -> 0.39), frac only moves forward');
$lvRow = st_run_row($pdo->query('SELECT * FROM st_runs WHERE id = ' . (int)$lv['id'])->fetch());
ok($lvRow['pct'] === 100 && $lvRow['frac'] === 0.0 && $lvRow['detail'] === '3 ideas' && $lvRow['steps'][2] === ['label' => 'AI ranking', 'state' => 'done', 'note' => '3 ideas'] && $lvRow['beat_at'] !== null
    && (int)q1("SELECT COUNT(*) FROM st_opps WHERE title LIKE 'Live idea %'") === 3, 'after the stream: run DONE 100%, live text replaced by the result "3 ideas" (finished card), AI ranking note "3 ideas"');
@unlink("$Q.probe");

// StRun by hand (as a job drives it): labels, notes, states, frac forward-only, reset on step, throttled writes, heartbeat, a failing live write never breaks the job
$mk = function () use ($pdo): StRun {
    $id = st_run_new($pdo, 'source_check', ['trigger' => 'manual', 'by' => 'test']);
    h_x($pdo, "UPDATE st_runs SET status = 'RUNNING', attempts = 1, started_at = UTC_TIMESTAMP() WHERE id = ?", [$id]);
    return new StRun($pdo, $pdo->query("SELECT * FROM st_runs WHERE id = $id")->fetch());
};
$rowOf = fn(StRun $R): array => st_run_row($pdo->query("SELECT * FROM st_runs WHERE id = {$R->id}")->fetch());
$trip = fn(array $row): array => array_map(fn($x) => [$x['label'], $x['state'], $x['note']], $row['steps']);
$R = $mk(); $hand = [$R->id];
$b0 = $R->plan(['Web research: Tools', 'Web research: Maps', 'Your apps + daily discovery', 'AI ranking', 'Save + notify you']);
$R->note('9 sources'); $R->step();
$R->mark(1, 'reused');
$R->note('23 apps'); $R->step();
$R->progress(0.5, '4 of ~10 ideas written', '4 of ~10', true);
$x1 = $rowOf($R);
ok($b0 === 0 && $trip($x1) === [['Web research: Tools', 'done', '9 sources'], ['Web research: Maps', 'done', 'reused'], ['Your apps + daily discovery', 'done', '23 apps'], ['AI ranking', 'on', '4 of ~10'], ['Save + notify you', 'wait', '']]
    && $x1['steps_done'] === 3 && $x1['steps_total'] === 5 && $x1['frac'] === 0.5 && $x1['pct'] === 70 && $x1['detail'] === '4 of ~10 ideas written',
    'StRun: labelled plan, notes, reused step marked done, live note on the running step, pct 70 (3 + 0.5 of 5)');
$R->progress(0.2, 'still'); $fa = $R->frac;
$R->progress(1.7, 'over'); $fb = $R->frac;
$R->error('AI ranking: boom');
$x2 = $rowOf($R);
$R->step();
$x3 = $rowOf($R);
h_x($pdo, "UPDATE st_runs SET status = 'FAILED' WHERE id = ?", [$R->id]);
$x4 = $rowOf($R);
ok($fa === 0.5 && $fb === 0.97 && $trip($x2)[3] === ['AI ranking', 'on', '4 of ~10'] && $x2['errors'] === 1 && $x2['error_text'] === 'AI ranking: boom' && $x2['frac'] === 0.97
    && array_column($x3['steps'], 'state') === ['done', 'done', 'done', 'fail', 'on'] && $x3['steps'][3]['note'] === '' && $x3['frac'] === 0.0 && $x3['detail'] === '' && $x3['pct'] === 80
    && array_column($x4['steps'], 'state') === ['done', 'done', 'done', 'fail', 'fail'],
    'StRun: frac never goes back (0.5 stays after 0.2) and stops at 0.97; error marks the step, step() closes it as "fail" and resets frac / detail / live note; run FAILED -> its current step "fail"');
$N = $mk(); $hand[] = $N->id;
$N->plan(['Check your dashboard data']);
$nAt = $N->plan(['Install cohorts', 'Retention + D7 revenue', 'Cost per install', 'Save economics']);   // a job run inside another one (source test -> economics)
$N->step(2); $N->step(); $N->step(); $N->step();
$C = $mk(); $hand[] = $C->id;
$C->plan(['Your apps: earnings + installs', 'Google Ads spend']); $C->step(); $C->error('portfolio: x'); $C->closeOpen();
$cAt = $C->plan(['Web research: Tools', 'AI ranking']); $C->note('5 sources'); $C->step();
ok($nAt === 0 && array_column($rowOf($N)['steps'], 'label') === ['Install cohorts', 'Retention + D7 revenue', 'Cost per install', 'Save economics', 'Check your dashboard data']
    && array_unique(array_column($rowOf($N)['steps'], 'state')) === ['done'] && $rowOf($N)['steps_done'] === 5 && $rowOf($N)['steps_total'] === 5
    && $cAt === 2 && $trip($rowOf($C)) === [['Your apps: earnings + installs', 'done', ''], ['Google Ads spend', 'fail', ''], ['Web research: Tools', 'done', '5 sources'], ['AI ranking', 'on', '']],
    'StRun: an inner job\'s steps go in before the outer step that is still waiting (economics inside the source test); a failed inner job closes its open steps ("fail") so the outer steps stay in line');
ok($rowOf($C)['steps_done'] === 3 && $rowOf($C)['steps_total'] === 4,
    'closeOpen counts the steps it closes (like step() counts a failed step): steps_done 3 of 4 matches the list (2 closed + 1 done), so "Step N of M" and pct agree with it');
// rank plans first: economics + portfolio steps reserved up front (whole plan from the start, % never jumps back), the inner job adopts them; a failed inner job closes only its own range
$V = $mk(); $hand[] = $V->id;
$V->plan([...ST_ECON_STEPS, ...ST_PORT_STEPS, 'Web research: Tools', 'AI ranking'], 8);
$v0 = $rowOf($V);
$vAt = $V->plan(ST_ECON_STEPS);   // economics inside rank
$V->step(2); $V->progress(0.5, 'x', null, true);
$v1 = $rowOf($V);
$V->error('economics: cpi boom'); $V->closeOpen(0, 4);
$v2 = $rowOf($V);
$pAt = $V->plan(ST_PORT_STEPS); $V->step(4);
$vOther = $V->plan(['Something else']);   // not reserved: inserted as before
ok($v0['steps_total'] === 10 && count($v0['steps']) === 10 && $v0['pct'] === 0 && $vAt === 0 && $v1['steps_total'] === 10 && $v1['steps_done'] === 2 && $v1['pct'] === 25
    && array_column($v2['steps'], 'state') === ['done', 'done', 'fail', 'fail', 'on', 'wait', 'wait', 'wait', 'wait', 'wait'] && $v2['steps_done'] === 4 && $v2['steps_total'] === 10
    && $pAt === 4 && $rowOf($V)['steps_done'] === 8 && $rowOf($V)['steps'][8] === ['label' => 'Something else', 'state' => 'on', 'note' => ''] && $vOther === 8 && $rowOf($V)['steps_total'] === 11,
    'reserved steps: rank shows all 10 steps from the start; economics / portfolio adopt their reserved steps (total stays 10, 2 + 0.5 of 10 = 25%); economics fails -> only its 2 open steps "fail" and counted (4 of 10), rank steps still wait; a plan that matches no reserved steps is inserted as usual');
$X = $mk(); $hand[] = $X->id;
$X->plan(['A', 'B']);
foreach (range(1, 6) as $i) $X->error("minor $i");
$X->error('the fatal one');
$xe = $rowOf($X)['error_text'];
ok($xe === 'minor 1 | minor 2 | minor 3 | minor 4 | the fatal one' && $rowOf($X)['errors'] === 7,
    'error_text with more than 5 errors: first 4 + the last one (the error the run stopped on, shown as the reason in the app) · ' . $xe);
$alterOk = true;
try { st_alter_add($pdo, 'st_runs', "ADD COLUMN detail VARCHAR(160) NOT NULL DEFAULT ''", fn() => false); st_alter_add($pdo, 'st_usage', 'ADD KEY k_run (run_id)', fn() => false); } catch (Throwable $e) { $alterOk = $e->getMessage(); }
$alterBad = false;
try { st_alter_add($pdo, 'st_runs', 'ADD COLUMN zz_bad NOT_A_TYPE', fn() => false); } catch (PDOException $e) { $alterBad = true; }
ok($alterOk === true && $alterBad, 'schema upgrade race: another request already added the column / index (1060 / 1061) -> no error (no HTTP 500 right after the upload); any other ALTER error still throws');
$sc = st_run_new($pdo, 'source_check', ['trigger' => 'manual', 'by' => 'test', 'params' => ['source' => 'claude_web']]); $hand[] = $sc;
ok(st_run_row($pdo->query("SELECT * FROM st_runs WHERE id = $sc")->fetch())['source'] === 'claude_web' && $rowOf($V)['source'] === null
    && st_run_row($pdo->query("SELECT * FROM st_runs WHERE job <> 'source_check' LIMIT 1")->fetch())['source'] === null,
    'run row: source test carries its source (the app\'s "Run again" sends {job: source_check, source}); no source -> null; other jobs null');
$T2 = $mk(); $hand[] = $T2->id;
$T2->plan(['A']);
$T2->wrote = microtime(true);
$T2->progress(0.3, 'thr');
$thr1 = q1('SELECT detail FROM st_runs WHERE id = ?', [$T2->id]);
$T2->wrote -= ST_LIVE_SEC + 0.1;
$T2->progress(0.3, 'thr');   // nothing new, but the pending change is due now
$thr2 = $pdo->query("SELECT detail, frac FROM st_runs WHERE id = {$T2->id}")->fetch();
h_x($pdo, 'UPDATE st_runs SET beat_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 10 MINUTE) WHERE id = ?', [$T2->id]);
$T2->wrote -= ST_LIVE_SEC + 0.1;
$T2->progress(0.3, 'thr');   // unchanged + already written: no write
$beatOld = (int)q1('SELECT TIMESTAMPDIFF(SECOND, beat_at, UTC_TIMESTAMP()) FROM st_runs WHERE id = ?', [$T2->id]);
$T2->beat();
$beatNew = (int)q1('SELECT TIMESTAMPDIFF(SECOND, beat_at, UTC_TIMESTAMP()) FROM st_runs WHERE id = ?', [$T2->id]);
ok($thr1 === '' && $thr2['detail'] === 'thr' && (float)$thr2['frac'] === 0.3 && $beatOld >= 590 && $beatNew <= 1,
    'StRun: live writes throttled (change within ' . ST_LIVE_SEC . ' s waits, then written), nothing new = no write; beat() writes the heartbeat now');
final class StNoLivePdo extends PDO   // DB without the new columns (or a write that fails): progress must never break the job
{
    public function prepare(string $query, array $options = []): PDOStatement|false
    {
        if (str_contains($query, 'beat_at')) throw new PDOException("SQLSTATE[42S22]: Column not found: 1054 Unknown column 'beat_at' in 'field list'");
        return parent::prepare($query, $options);
    }
}
$E = $mk(); $hand[] = $E->id;
$xp = new StNoLivePdo($dsn, (string)getenv('TEST_DB_USER'), (string)getenv('TEST_DB_PASS'), [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC, PDO::ATTR_EMULATE_PREPARES => true]);
$xp->exec("SET time_zone = '+00:00'");
$E2 = new StRun($xp, $pdo->query("SELECT * FROM st_runs WHERE id = {$E->id}")->fetch());
$elog = ini_set('error_log', "$T/live_error.log");   // the expected "live progress" log lines stay out of the test output
try { $E2->plan(['A', 'B']); $E2->stage('stage still saved'); $E2->progress(0.5, 'x', null, true); $E2->beat(); $E2->step(); $E2->error('later error'); $eOk = ''; } catch (Throwable $e) { $eOk = $e->getMessage(); }
ini_set('error_log', (string)$elog);
$er2 = $pdo->query("SELECT * FROM st_runs WHERE id = {$E->id}")->fetch();
ok($eOk === '' && (int)$er2['steps_done'] === 1 && (int)$er2['steps_total'] === 2 && $er2['stage'] === 'stage still saved' && (int)$er2['errors'] === 1 && $er2['steps_json'] === null,
    'live columns cannot be written: plan / stage / step / error still saved the old way, progress + heartbeat fail silently (job keeps running)' . ($eOk !== '' ? " · $eOk" : ''));
putenv('ST_NO_STREAM=1'); @unlink("$Q.log"); $fake([]);
$W = $mk(); $hand[] = $W->id;
$W->plan(['Test web search']);
try { st_ai($pdo, $W, ['system' => 's', 'user' => 'u', 'max_tokens' => 100]); } catch (Throwable $e) {}
putenv('ST_NO_STREAM');
$wr = $pdo->query("SELECT detail, beat_at FROM st_runs WHERE id = {$W->id}")->fetch();
ok($wr['detail'] === 'waiting for Claude' && $wr['beat_at'] !== null, 'non-streaming fallback: detail "waiting for Claude" + heartbeat written before the blocking call');
final class StBeatLogPdo extends PDO   // every live write (beat_at) goes into the request log too, so its order against the http_json calls is visible
{
    public function prepare(string $query, array $options = []): PDOStatement|false
    {
        if (str_contains($query, 'beat_at')) @file_put_contents(getenv('STUDIO_FAKE_Q') . '.log', "BEAT\n", FILE_APPEND);
        return parent::prepare($query, $options);
    }
}
$bp = new StBeatLogPdo($dsn, (string)getenv('TEST_DB_USER'), (string)getenv('TEST_DB_PASS'), [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC, PDO::ATTR_EMULATE_PREPARES => true]);
$bp->exec("SET time_zone = '+00:00'");
$B = $mk(); $hand[] = $B->id;
$B2 = new StRun($bp, $pdo->query("SELECT * FROM st_runs WHERE id = {$B->id}")->fetch());
$B2->plan(['Test web search']);
putenv('ST_NO_STREAM=1'); @unlink("$Q.log"); $fake([[529, ['type' => 'error', 'error' => ['type' => 'overloaded_error', 'message' => 'Overloaded']]], $slowMsg]);
try { st_ai($bp, $B2, ['system' => 's', 'user' => 'u', 'max_tokens' => 100]); $bErr = ''; } catch (Throwable $e) { $bErr = $e->getMessage(); }
putenv('ST_NO_STREAM');
$seq = implode('', array_map(fn($l) => $l === 'BEAT' ? 'B' : 'C', array_filter(explode("\n", (string)@file_get_contents("$Q.log")))));
ok($bErr === '' && $left() === 0 && preg_match('/^B+CB+C(B*)$/', $seq) === 1,
    "non-streaming retry after HTTP 529: heartbeat written again before the second blocking call (B = live write, C = Claude call: $seq)" . ($bErr !== '' ? " · $bErr" : ''));
foreach ($hand as $hid) { h_x($pdo, 'DELETE FROM st_usage WHERE run_id = ?', [$hid]); h_x($pdo, 'DELETE FROM st_runs WHERE id = ?', [$hid]); }
@unlink("$Q.log"); $fake([]);
ok((int)q1("SELECT COUNT(*) FROM st_runs WHERE status IN ('QUEUED','RUNNING','CANCELLING')") === 0, 'streaming tests leave no queued / running runs');

// ---------------------------------------------------------------- HTTP: every view + actions
$port = 18000 + getmypid() % 1000 + 7;
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
// daily discovery views (contract: overview.discovery + view=discovery latest / id / run)
$pdo->exec("UPDATE st_artifacts SET kind = 'daily_off' WHERE kind = 'daily_notes'");
[$c, $j] = $http('GET', 'api/studio.php?view=overview');
[$c2, $j2] = $http('GET', 'api/studio.php?view=discovery');
$pdo->exec("UPDATE st_artifacts SET kind = 'daily_notes' WHERE kind = 'daily_off'");
ok($c === 200 && is_array($j) && array_key_exists('discovery', $j) && $j['discovery'] === null && $c2 === 200 && ($j2['ok'] ?? false) && array_key_exists('item', $j2) && $j2['item'] === null
    && ($j2['days'] ?? null) === [], 'no daily scan yet: overview.discovery null, view=discovery item null and no days');
$d0 = st_artifact_save($pdo, null, null, 'daily_notes', ['notes' => 'old format notes', 'sources' => ['S1' => $dEv['S1']], 'countries' => ['IN']], 'test: old format', 'test');
$d4 = st_artifact_save($pdo, null, null, 'daily_notes', ['notes' => 'fresh', 'sources' => ['S10' => $dEv['S2'], 'S1' => $dEv['S1'], 'S2' => 999999], 'countries' => ['IN', 'US'],
    'findings' => [$dF('POLICY', 'Fresh policy', 'd1', 'SAID', [$dEv['S1'], 999999]), $dF('ADS', 'Fresh ads', 'd2', 'GUESS', []), $dF('NEW_APP', 'Fresh app', 'd3', 'SAID', [$dEv['S2'], $dEv['S1']]),
        $dF('OTHER', 'Fourth', 'd4', 'GUESS', [])], 'structured' => true], 'test: fresh', 'test');
$dv = function (string $q) use ($http) { [$c, $j, $raw] = $http('GET', "api/studio.php?view=discovery$q"); return $c === 200 && ($j['ok'] ?? false) && array_key_exists('item', $j) ? $j : ['err' => substr($raw, 0, 300)]; };
$zero = ['new_app' => 0, 'competitor' => 0, 'policy' => 0, 'ads' => 0, 'other' => 0];
$L = $dv('');
$it = $L['item'] ?? [];
ok(($it['id'] ?? 0) === $d4 && $it['run_id'] === 0 && $it['structured'] === true && $it['used_in_ranking'] === false && $it['ranking_at'] === null && $it['notes'] === '' && $it['countries'] === ['IN', 'US']
    && preg_match('/^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d$/', $it['created_at']) && $it['counts'] === ['new_app' => 1, 'competitor' => 0, 'policy' => 1, 'ads' => 1, 'other' => 1]
    && array_column($it['findings'], 'title') === ['Fresh policy', 'Fresh ads', 'Fresh app', 'Fourth'] && array_column($it['findings'][0]['sources'], 'id') === [$dEv['S1']]
    && array_keys($it['findings'][0]['sources'][0]) === ['id', 'title', 'url', 'page_age'] && array_column($it['findings'][2]['sources'], 'id') === [$dEv['S2'], $dEv['S1']]
    && $it['findings'][1]['sources'] === [] && array_column($it['sources'], 'id') === [$dEv['S1'], $dEv['S2']],
    'view=discovery latest: newest scan, findings in order with resolved sources (missing evidence row skipped), run sources in S order (S1, S10), counts by type, not used in ranking' . (isset($L['err']) ? ' · ' . $L['err'] : ''));
$f0 = array_keys($it['findings'][0] ?? []); sort($f0);
$i0 = array_keys($it); sort($i0);
ok($f0 === ['basis', 'category', 'countries', 'detail', 'sources', 'title', 'type'] && $i0 === ['countries', 'counts', 'created_at', 'findings', 'id', 'notes', 'ranking_at', 'run_id', 'sources', 'structured', 'used_in_ranking']
    && array_column($L['days'] ?? [], 'id') === [$d4, $d0, (int)$d3['id'], (int)$d2['id'], (int)$d1['id'], $dOld] && array_column($L['days'], 'findings') === [4, 0, 1, 0, 4, 1]
    && array_keys($L['days'][0]) === ['id', 'run_id', 'created_at', 'findings'] && $L['days'][4]['run_id'] === $rid1 && $L['days'][4]['created_at'] === $dAt[(int)$d1['id']],
    'view=discovery: item + finding keys as in the contract; days newest first with finding counts');
$it = $dv('&id=' . (int)$d1['id'])['item'] ?? [];
$s0 = $it['findings'][0]['sources'] ?? [];
ok(($it['id'] ?? 0) === (int)$d1['id'] && $it['run_id'] === $rid1 && $it['created_at'] === $dAt[(int)$d1['id']] && $it['structured'] === true && $it['used_in_ranking'] === true && $it['ranking_at'] === $rankAt
    && $it['counts'] === ['new_app' => 1, 'competitor' => 1, 'policy' => 1, 'ads' => 1, 'other' => 0] && $it['notes'] === '' && $it['countries'] === ['IN', 'US']
    && array_column($s0, 'url') === ['https://news.example/ghost', 'https://news.example/top50'] && ($s0[0]['title'] ?? '') === 'T https://news.example/ghost' && ($s0[0]['page_age'] ?? '') === '3 days ago'
    && $it['findings'][0]['detail'] === '2 new apps entered the top 50 in Entertainment this week.' && $it['findings'][0]['category'] === 'Entertainment' && $it['findings'][0]['countries'] === ['US']
    && $it['findings'][0]['basis'] === 'SAID' && $it['findings'][1]['basis'] === 'GUESS' && $it['findings'][1]['sources'] === []
    && array_column($it['sources'], 'url') === ['https://news.example/ghost', 'https://news.example/top50', 'https://policy.example/calllog'],
    'view=discovery&id: scan 3 days ago used by the ranking 1 day ago (ranking_at), sources resolved to title/url/page_age');
$it = $dv("&run=$rid2")['item'] ?? [];
ok(($it['id'] ?? 0) === (int)$d2['id'] && $it['run_id'] === $rid2 && $it['structured'] === false && $it['findings'] === [] && $it['counts'] === $zero && str_contains($it['notes'], 'Quiet day')
    && array_column($it['sources'], 'url') === ['https://news.example/quiet'] && $it['used_in_ranking'] === false && $it['ranking_at'] === null,
    'view=discovery&run: structuring failed -> structured false, raw notes, no findings, not used in ranking');
$it = $dv("&id=$d0")['item'] ?? [];
ok(($it['id'] ?? 0) === $d0 && $it['structured'] === false && $it['findings'] === [] && $it['counts'] === $zero && $it['notes'] === 'old format notes' && array_column($it['sources'], 'id') === [$dEv['S1']]
    && $it['used_in_ranking'] === false && $it['countries'] === ['IN'], 'old-format artifact (no findings): structured false, counts 0, notes text');
$io = $dv("&id=$dOld")['item'] ?? []; $i3 = $dv('&id=' . (int)$d3['id'])['item'] ?? [];
ok(($io['used_in_ranking'] ?? null) === false && count($io['findings']) === 1 && ($i3['used_in_ranking'] ?? null) === true && $i3['ranking_at'] === $rankAt,
    'used_in_ranking: ranking 8 days after a scan -> false; ranking 1 day after -> true');
$rankArt = (int)q1("SELECT id FROM st_artifacts WHERE kind = 'ranking' ORDER BY id DESC LIMIT 1");
$na = $dv("&id=$rankArt"); $nr = $dv('&run=999999');
ok($rankArt > 0 && array_key_exists('item', $na) && $na['item'] === null && array_key_exists('item', $nr) && $nr['item'] === null && count($nr['days'] ?? []) === 6,
    'view=discovery: id of a non-discovery artifact / unknown run -> item null (200)');
[$c, $j] = $http('GET', 'api/studio.php?view=overview');
$od = $j['discovery'] ?? [];
ok(($od['id'] ?? 0) === $d4 && $od['run_id'] === 0 && $od['structured'] === true && $od['total'] === 4 && $od['used_in_ranking'] === false && $od['created_at'] === ($L['item']['created_at'] ?? '')
    && $od['top'] === [['type' => 'POLICY', 'title' => 'Fresh policy', 'sources' => 1], ['type' => 'ADS', 'title' => 'Fresh ads', 'sources' => 0], ['type' => 'NEW_APP', 'title' => 'Fresh app', 'sources' => 2]],
    'overview.discovery: latest scan, structured, total, used_in_ranking, top 3 findings with source counts');
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
ok($c === 200 && (bool)$pdo->query("SHOW TABLES LIKE 'st_port'")->fetch() && kv_get('st_schema_v') === ST_SCHEMA_V, 'schema upgrade: server already at v1 -> current (v' . ST_SCHEMA_V . ') creates st_port');
[$c, $j, $raw] = $http('GET', 'api/studio.php?open=project/1');
ok($c === 200 && str_contains($raw, 'intent://studio/project/1'), 'email deep link page opens the app');
// view=live over HTTP (the app polls it every few seconds): QUEUED / RUNNING / CANCELLING + finished in the last 15 min, newest first, max 20
$pdo->exec('UPDATE st_runs SET finished_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 2 HOUR) WHERE finished_at IS NOT NULL');
$lf = $rankRun([$webResp(['https://example.com/lv-tools'], 'LV tools notes'), $webResp(['https://example.com/lv-prank'], 'LV prank notes'), $cutResp('Live cut', 2)]);   // Tools + Prank apps, IN
$old20 = (int)q1("SELECT id FROM st_runs WHERE job = 'economics' AND status = 'DONE' ORDER BY id LIMIT 1");
$old5 = (int)q1("SELECT id FROM st_runs WHERE job = 'discover' AND status = 'DONE' ORDER BY id LIMIT 1");
h_x($pdo, 'UPDATE st_runs SET finished_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 20 MINUTE) WHERE id = ?', [$old20]);
h_x($pdo, 'UPDATE st_runs SET finished_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 5 MINUTE), steps_json = NULL WHERE id = ?', [$old5]);   // finished before the upgrade: no steps saved
$lr = st_run_new($pdo, 'rank', ['trigger' => 'manual', 'by' => 'test']);
$lrSteps = json_encode([['l' => 'Web research: Tools', 'n' => '9 sources', 's' => 'd'], ['l' => 'Web research: Communication', 'n' => '7 sources', 's' => 'd'], ['l' => 'Web research: Maps & Navigation', 'n' => '8 sources', 's' => 'd'],
    ['l' => 'Your apps + daily discovery', 'n' => '23 apps', 's' => 'd'], ['l' => 'AI ranking', 'n' => '4 of ~10', 's' => 'w'], ['l' => 'Save + notify you', 'n' => '', 's' => 'w']]);   // the approved mockup, mid-run
h_x($pdo, "UPDATE st_runs SET status = 'RUNNING', attempts = 1, started_at = UTC_TIMESTAMP(), beat_at = UTC_TIMESTAMP(), stage = 'AI ranking (structured)', steps_done = 4, steps_total = 6, frac = 0.5,
    detail = '4 of ~10 ideas written', steps_json = ? WHERE id = ?", [$lrSteps, $lr]);
foreach ([[0.25, 2], [0.5, 1]] as [$uc, $us]) h_x($pdo, "INSERT INTO st_usage (run_id, kind, model, in_tokens, out_tokens, searches, cost_usd, ok, created_at) VALUES (?, 'ai+search', 'claude-opus-5-5', 1, 1, ?, ?, 1, UTC_TIMESTAMP())", [$lr, $us, $uc]);
$lq = st_run_new($pdo, 'discover', ['trigger' => 'manual', 'by' => 'test']);
[$c, $j, $raw] = $http('GET', 'api/studio.php?view=live');
$LR = array_column($j['runs'] ?? [], null, 'id');
if (getenv('STUDIO_LIVE_SAMPLE')) file_put_contents((string)getenv('STUDIO_LIVE_SAMPLE'), json_encode($j, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE));
$want = ['id', 'uid', 'job', 'status', 'ref_id', 'trigger', 'steps_done', 'steps_total', 'frac', 'pct', 'stage', 'detail', 'steps', 'queued_at', 'started_at', 'finished_at', 'beat_at', 'cost_usd', 'ai_calls',
    'searches', 'error_text', 'summary', 'waiting_for', 'can_reuse'];
ok($c === 200 && ($j['ok'] ?? false) && ($j['view'] ?? '') === 'live' && preg_match('/^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d$/', (string)($j['server_time'] ?? '')) && array_key_exists('cron_last', $j)
    && array_column($j['runs'] ?? [], 'id') === [$lq, $lr, (int)$lf['id'], $old5] && !array_filter($j['runs'], fn($r) => array_diff($want, array_keys($r))),
    'view=live: queued + running + finished in the last 15 min (5 min ago yes, 20 min ago no), newest first, every contract field' . ($c !== 200 ? ' -> ' . substr($raw, 0, 300) : ''));
$q0 = $LR[$lq] ?? []; $r0 = $LR[$lr] ?? []; $f0 = $LR[(int)$lf['id']] ?? []; $o0 = $LR[$old5] ?? [];
ok(($q0['status'] ?? '') === 'QUEUED' && $q0['waiting_for'] === 'rank' && $q0['pct'] === 0 && $q0['steps'] === [] && $q0['beat_at'] === null && $q0['can_reuse'] === false
    && ($r0['status'] ?? '') === 'RUNNING' && $r0['waiting_for'] === null && $r0['pct'] === 75 && $r0['frac'] === 0.5 && $r0['detail'] === '4 of ~10 ideas written' && $r0['beat_at'] !== null
    && $r0['cost_usd'] === 0.75 && $r0['ai_calls'] === 2 && $r0['searches'] === 3 && (float)q1('SELECT cost_usd FROM st_runs WHERE id = ?', [$lr]) === 0.0
    && array_column($r0['steps'], 'state') === ['done', 'done', 'done', 'done', 'on', 'wait'] && $r0['steps'][4]['note'] === '4 of ~10',
    'view=live: queued run waits behind the running "rank"; running run: pct 75 (4 + 0.5 of 6), detail, heartbeat, live cost $0.75 / 2 AI calls / 3 searches from st_usage (st_runs still 0)');
ok(($f0['status'] ?? '') === 'FAILED' && $f0['can_reuse'] === true && $f0['waiting_for'] === null && str_contains((string)$f0['error_text'], 'only 2 complete ideas') && $f0['cost_usd'] > 0 && $f0['ai_calls'] === 3
    && array_map(fn($x) => [$x['label'], $x['state']], $f0['steps']) === [['Web research: Tools', 'done'], ['Web research: Prank apps', 'done'], ['Your apps + daily discovery', 'done'], ['AI ranking', 'fail'], ['Save + notify you', 'wait']]
    && $f0['pct'] >= 60 && $f0['pct'] <= 79 && ($o0['status'] ?? '') === 'DONE' && $o0['steps'] === [] && $o0['pct'] === 100 && (float)$o0['frac'] === 0.0,   // JSON: 0.0 arrives as 0
    "view=live: failed ranking -> can_reuse true (its research is saved), AI ranking \"fail\", {$f0['pct']}%; old run without steps_json -> steps [] (100%)");
[$c1r, $j1] = $http('GET', 'api/studio.php?view=run&id=' . (int)$lf['id']);
[$c2r, $j2] = $http('GET', 'api/studio.php?view=runs');
[$c3r, $j3] = $http('GET', 'api/studio.php?view=overview');
$rs = array_column($j2['items'] ?? [], null, 'id');
ok($c1r === 200 && ($j1['item']['steps'] ?? null) === $f0['steps'] && ($j1['item']['can_reuse'] ?? null) === true && ($rs[$lq]['waiting_for'] ?? '') === 'rank' && ($rs[$lr]['cost_usd'] ?? 0) === 0.75
    && ($rs[(int)$lf['id']]['pct'] ?? -1) === $f0['pct'] && ($j3['runs'][0]['id'] ?? 0) === $lq && ($j3['runs'][0]['waiting_for'] ?? '') === 'rank' && is_array($j3['runs'][1]['steps'] ?? null),
    'run detail, runs list and overview.runs carry the same live fields (steps, pct, waiting_for, live cost, can_reuse)');
st_cancel($pdo, $lq, 'test');   // else the worker takes the queued (manual) discover first
h_x($pdo, "UPDATE st_runs SET status = 'CANCELLED', finished_at = UTC_TIMESTAMP() WHERE id = ?", [$lr]);
$lf2 = $rankRun([$jsonResp(['opportunities' => [$oppS('Live reuse idea')]])]);   // "Run again": the saved research is used, no web search
[$c, $j] = $http('GET', 'api/studio.php?view=live');
$LR = array_column($j['runs'] ?? [], null, 'id');
ok($lf2['status'] === 'DONE' && count($reqs()) === 1 && ($LR[(int)$lf['id']]['can_reuse'] ?? null) === false && ($LR[(int)$lf2['id']]['can_reuse'] ?? null) === false
    && array_column($LR[(int)$lf2['id']]['steps'] ?? [], 'note') === ['reused', 'reused', '', '1 idea', ''],
    'after "Run again" used the research: can_reuse false; new run shows both web steps "reused"' . $errOf($lf2));
// server still on schema v2 (no live columns): the first request adds them, old runs render without steps
$pdo->exec('ALTER TABLE st_runs DROP COLUMN frac, DROP COLUMN detail, DROP COLUMN beat_at, DROP COLUMN steps_json, DROP KEY k_fin');
$pdo->exec('ALTER TABLE st_usage DROP KEY k_run');
kv_set('st_schema_v', '2');
[$c, $j, $raw] = $http('GET', 'api/studio.php?view=live');
$cols = array_column(q('SHOW COLUMNS FROM st_runs'), 'Field');
ok($c === 200 && kv_get('st_schema_v') === '3' && !array_diff(['frac', 'detail', 'beat_at', 'steps_json'], $cols) && st_has_key($pdo, 'st_runs', 'k_fin') && st_has_key($pdo, 'st_usage', 'k_run')
    && count($j['runs'] ?? []) >= 3 && !array_filter($j['runs'], fn($r) => $r['steps'] !== [] || (float)$r['frac'] !== 0.0 || $r['detail'] !== ''),
    'schema upgrade 2 -> 3: live columns + indexes added on the first request; runs from before render with steps [] and frac 0' . ($c !== 200 ? ' -> ' . substr($raw, 0, 300) : ''));
proc_terminate($srv);
$log = (string)@file_get_contents("$T/server.log");
ok(!preg_match('/PHP (Warning|Fatal|Deprecated|Notice)/', $log), 'no PHP warnings / errors in server log' . (preg_match('/PHP (Warning|Fatal|Deprecated|Notice)[^\n]*/', $log, $mm) ? ': ' . $mm[0] : ''));
$log = (string)@file_get_contents("$T/fake.log");
ok(!preg_match('/PHP (Warning|Fatal|Deprecated|Notice)/', $log), 'no PHP warnings / errors in the fake Claude API log' . (preg_match('/PHP (Warning|Fatal|Deprecated|Notice)[^\n]*/', $log, $mm) ? ': ' . $mm[0] : ''));

echo "\n$n checks, $fail failed\n";
exit($fail > 0 ? 1 : 0);
