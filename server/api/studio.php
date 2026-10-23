<?php
/**
 * MFC Dash app — "AI App Research & Development Studio" module ki API (sirf app ke liye; web page nahi).
 * Upload: mfc_dash/api/studio.php + lib/studio.php (+ optional mfc_dash/studio_config.php). Zaroori: lib/health.php (AI App Health module).
 *
 * Cron (har 5 min):  php mfc_dash/api/studio.php cron   ya URL  api/studio.php?cron=1&key=CRON_KEY
 *
 * App (login cookie):
 *   GET ?view=overview | research&engine=organic|paid|combined&cc=&category=&status= | opp&id= | compare&ids=1,2,3 | evidence&ids= | projects
 *            | project&id= | approvals | history&q=&kind=&page= | runs | run&id= | sources | settings | mine | discovery | live
 *   GET ?view=live (app har kuch sec poll karta hai; halka: runs ki ek query + chalti runs ke kharche ki ek grouped query) -> {ok, view, server_time (UTC), cron_last,
 *              runs[]: har QUEUED / RUNNING / CANCELLING run + pichhle 15 min me khatam, newest first, max 20}
 *       run (runs, overview.runs, run, project.runs, research.last_ranking me bhi yahi): {id, uid, job, status, ref_id|null, trigger, steps_done, steps_total,
 *              frac (0..1, chalu step ke andar), pct (DONE = 100, warna floor(100 x (steps_done + frac) / max(1, steps_total)), max 99), stage, detail ("4 of ~10 ideas written", '' = kuch nahi),
 *              steps[]:{label, state done|on|wait|fail, note} ([] = purani run), queued_at, started_at|null, finished_at|null, beat_at|null (aakhri heartbeat UTC),
 *              cost_usd (RUNNING: st_usage ka live jod), ai_calls, searches, error_text|null, summary|null, waiting_for (QUEUED: chalti run ka job, warna null),
 *              can_reuse (rank FAILED/PARTIAL/BLOCKED/CANCELLED jiski poori research (har category) agli rank run abhi le legi: dobara web search ka kharcha nahi),
 *              source (source_check: kis source ka test, Run again isse; warna null), by, errors, attempts}. DONE rank: detail = "11 ideas".
 *       overview.mine = null (portfolio kabhi nahi chala) ya {computed_at, profit_inr, roas, ecpm_usd, apps, apps_ads_on, best:{cc, profit_inr}|null, loss:{cc, profit_inr}|null}
 *       mine = aapke apne apps, pichhle 30 local din from..to (aaj samet; pure SQL, AI cost nahi), roz ek baar 'portfolio' job:
 *              {empty (job kabhi nahi chala), computed_at (UTC), from, to, days, rate (INR/USD), spend_by_cc: exact|estimate|mixed|none, running (portfolio run QUEUED/RUNNING),
 *              totals:{earn_inr, ad_rev_inr, pay_inr, spend_inr, profit_inr, roas|null, ecpm_usd|null, imps, installs, apps, apps_ads_on, evidence_id},
 *              apps[]:{pkg, name, category ('' = nahi), earn_inr, ad_rev_inr, pay_inr, spend_inr, spend7_inr, profit_inr, roas|null, ecpm_usd|null, imps, installs, paid_installs, ads_on, evidence_id},
 *              countries[]:{cc ('' = unknown), installs, paid_installs, earn_inr, ad_rev_inr, pay_inr, spend_inr, spend_est, profit_inr, roas|null, ecpm_usd|null, imps, apps, evidence_id},
 *              categories[]:{category, apps, earn_inr, ad_rev_inr, pay_inr, spend_inr, profit_inr, roas|null, ecpm_usd|null, imps, installs, d7|null, evidence_id} — teeno profit desc,
 *              insights[] (sirf type + numbers, text app banata hai; jo shart poori na ho wo type aata hi nahi):
 *                focus {ccs[], ecpm_min_usd, profit_inr} | avoid_paid {ccs[], roas_min, roas_max, loss_inr, est} | strong {category, roas|null, profit_inr, d7|null, ecpm_usd|null}
 *                | weak {category, ecpm_usd|null, profit_inr, roas|null} | app_loss {pkgs[], names[], loss_inr}   — loss_inr = ghate ki rakam, POSITIVE (₹ jo gaye)}
 *              earn = ads + payments, profit = earn - spend (ghata = negative), roas = earn / spend (spend 0 = null), ecpm_usd = ad revenue USD / imps x 1000; paise 2 decimal, roas/eCPM 4
 *       overview.discovery = null (daily_notes abhi nahi) ya {id, run_id, created_at, structured, total, used_in_ranking, top[]:{type, title, sources (count)}} — pehli 3 findings
 *   GET ?view=discovery [&id=ARTIFACT_ID | &run=RUN_ID] -> {item: null | {id, run_id, created_at (UTC), structured, countries[], used_in_ranking, ranking_at|null,
 *              counts:{new_app, competitor, policy, ads, other}, findings[]:{type NEW_APP|COMPETITOR|POLICY|ADS|OTHER, title, detail, basis SAID|GUESS, category, countries[],
 *              sources[]:{id, title, url, page_age}}, sources[] (run ke saare, S order), notes ('' jab structured)}, days[]:{id, run_id, created_at, findings} (newest first, max 30)}
 *       structured false = purana artifact ya structuring fail (app raw notes dikhata hai). used_in_ranking = rank run (DONE/PARTIAL) iske baad 7 din me shuru hui
 *              aur ise sach me padha (ranking artifact ki discovery_ids); bina findings ke hamesha false
 *       opp rows me 'fit' = own_fit ka saar {focus_ccs[], avoid_paid_ccs[], category_roas|null, category_profit_inr|null, rating, basis: VERIFIED|ESTIMATE} ya null
 *   POST ?action=gate1 {select:[ids], note} | opp {id, decision: REJECT|WATCH|MORE_RESEARCH|RESTORE, note} | idea {title, concept, category, countries[]}
 *        | gate {project_id, gate, artifact_id, hash, decision: APPROVE|REQUEST_CHANGES|REJECT, feedback}
 *        | run {job: economics|portfolio|discover|rank|deep|source_check, source?} | cancel {id}
 *        | setting {key, value} | countries {list:[{cc,name,tier,on}]} | categories {list:[..]} | weights {weights:{key:w}} | app_category {pkg, category}
 *        | project_cancel {id, reason}
 *   GET ?open=project/12 -> chhota page jo app kholta hai (email ke link)
 */
declare(strict_types=1);
require_once __DIR__ . '/../lib/studio.php';

$isCli = PHP_SAPI === 'cli';
$pdo = db();
st_schema($pdo);

// ---------------------------------------------------------------- cron
if (($isCli && in_array('cron', $argv ?? [], true)) || isset($_GET['cron'])) {
    require_cron();
    @set_time_limit(300);
    $log = function (string $m) { echo gmdate('H:i:s') . "  $m\n"; };
    st_cron($pdo, $log);
    $log('done');
    exit;
}

// ---------------------------------------------------------------- deep link page (koi data nahi)
if (isset($_GET['open'])) {
    $route = preg_replace('#[^a-z0-9/_-]#i', '', (string)$_GET['open']);
    $intent = 'intent://studio/' . $route . '#Intent;scheme=mfcdash;package=com.myfuturecode.mfcdash;end';
    header('Content-Type: text/html; charset=utf-8');
    echo '<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>MFC Dash</title>'
        . '<body style="font:16px system-ui;background:#f3f2f8;color:#15132b;display:grid;place-items:center;height:90vh;margin:0">'
        . '<div style="text-align:center;padding:24px"><p>Open this in the MFC Dash app</p>'
        . '<a href="' . htmlspecialchars($intent) . '" style="display:inline-block;padding:14px 22px;border-radius:12px;background:linear-gradient(100deg,#9c02ba,#4a1fc4);color:#fff;text-decoration:none;font-weight:600">Open MFC Dash</a>'
        . '<p style="color:#6b6a80;font-size:13px">' . htmlspecialchars($route) . '</p></div><script>setTimeout(function(){location.href=' . json_encode($intent) . '},200)</script>';
    exit;
}

// ---------------------------------------------------------------- app (login)
require_login_json();
$me = admin_user();
if (session_status() === PHP_SESSION_ACTIVE) session_write_close();   // session lock chhodo: warna scan/AI ka lamba kaam chalte waqt app ki baaki requests atak kar timeout hoti hain
$action = (string)($_GET['action'] ?? '');

if (($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
    $in = json_decode((string)file_get_contents('php://input'), true);
    if (!is_array($in)) $in = $_POST;
    $id = (int)($in['id'] ?? 0);
    try {
        switch ($action) {
            case 'gate1': $resp = st_gate1($pdo, (array)($in['select'] ?? []), $me, (string)($in['note'] ?? '')); break;
            case 'opp': $resp = st_opp_decide($pdo, $id, (string)($in['decision'] ?? ''), (string)($in['note'] ?? ''), $me); break;
            case 'idea': $resp = st_idea_add($pdo, $in, $me); break;
            case 'gate':
                $resp = st_gate_decide($pdo, (int)($in['project_id'] ?? 0), (int)($in['gate'] ?? 0), (int)($in['artifact_id'] ?? 0), (string)($in['hash'] ?? ''),
                    (string)($in['decision'] ?? ''), (string)($in['feedback'] ?? ''), $me);
                break;
            case 'run':
                $job = (string)($in['job'] ?? '');
                if (!in_array($job, ['economics', 'portfolio', 'discover', 'rank', 'deep', 'source_check'], true)) throw new InvalidArgumentException('bad job');
                if ((int)h_q1($pdo, "SELECT COUNT(*) FROM st_runs WHERE job = ? AND status IN ('QUEUED','RUNNING')", [$job]) > 0 && $job !== 'source_check') throw new StConflict("$job is already queued or running");
                $rid = st_run_new($pdo, $job, ['trigger' => 'manual', 'by' => $me, 'params' => ['source' => (string)($in['source'] ?? '')]]);
                $resp = ['ok' => true, 'run_id' => $rid, 'note' => 'Queued. The server cron (every 5 min) runs it; the app shows real progress.'];
                break;
            case 'cancel': $resp = ['ok' => st_cancel($pdo, $id, $me)]; break;
            case 'project_cancel':
                $p = $pdo->query('SELECT * FROM st_projects WHERE id = ' . $id)->fetch();
                if (!$p) throw new InvalidArgumentException('project not found');
                if (trim((string)($in['reason'] ?? '')) === '') throw new InvalidArgumentException('Please give a reason');
                h_x($pdo, "UPDATE st_projects SET stage = 'CANCELLED', next_gate = NULL, blocker = ?, updated_at = UTC_TIMESTAMP() WHERE id = ?", [st_cut('Cancelled: ' . $in['reason'], 500), $id]);
                st_log($pdo, "{$p['puid']} {$p['name']} cancelled · " . st_cut((string)$in['reason'], 300), 'info', ['project' => $id, 'opp' => (int)$p['opp_id']], 'decision', $me);
                $resp = ['ok' => true];
                break;
            case 'setting':
                $k = (string)($in['key'] ?? '');
                if (!array_key_exists($k, ST_SETTINGS)) throw new InvalidArgumentException('bad key');
                $v = trim((string)($in['value'] ?? ''));
                if (in_array($k, ['st_ltv_mult', 'st_dev_cost'], true)) { if (!is_array(json_decode($v, true))) throw new InvalidArgumentException('JSON expected'); }
                elseif (!preg_match('/^\d+(\.\d+)?$/', $v)) throw new InvalidArgumentException('number expected');
                kv_set($k, $v);
                st_log($pdo, "setting $k = $v", 'info', [], 'action', $me);
                $resp = ['ok' => true];
                break;
            case 'countries':
                $list = [];
                foreach ((array)($in['list'] ?? []) as $c) {
                    $cc = strtoupper(trim((string)($c['cc'] ?? '')));
                    if (!preg_match('/^[A-Z]{2}$/', $cc)) continue;
                    $list[] = ['cc' => $cc, 'name' => st_cut((string)($c['name'] ?? $cc), 60), 'tier' => max(1, min(3, (int)($c['tier'] ?? 3))), 'on' => !empty($c['on'])];
                }
                if (!$list) throw new InvalidArgumentException('empty list');
                kv_set('st_countries', st_json($list));
                st_log($pdo, 'countries: ' . implode(', ', array_map(fn($c) => $c['cc'] . 'T' . $c['tier'] . ($c['on'] ? '' : '(off)'), $list)), 'info', [], 'action', $me);
                $resp = ['ok' => true];
                break;
            case 'categories':
                $list = array_values(array_unique(array_filter(array_map(fn($c) => st_cut(trim((string)$c), 60), (array)($in['list'] ?? [])))));
                if (!$list) throw new InvalidArgumentException('empty list');
                kv_set('st_categories', st_json($list));
                st_log($pdo, 'categories: ' . implode(', ', $list), 'info', [], 'action', $me);
                $resp = ['ok' => true];
                break;
            case 'weights':
                $w = [];
                foreach ((array)($in['weights'] ?? []) as $k => $v) if (isset(ST_FACTORS[$k]) && is_numeric($v)) $w[$k] = max(0, min(50, (float)$v));
                kv_set('st_weights', st_json($w + array_map(fn($f) => $f[3], ST_FACTORS)));
                st_log($pdo, 'score weights changed', 'info', [], 'action', $me);
                $resp = ['ok' => true, 'note' => 'New weights apply to the next ranking run.'];
                break;
            case 'app_category':
                $pkg = (string)($in['pkg'] ?? '');
                if (!preg_match('/^[a-zA-Z][\w]*(\.[\w]+)+$/', $pkg)) throw new InvalidArgumentException('bad package');
                h_x($pdo, 'REPLACE INTO st_app_meta (pkg, category, set_by, updated_at) VALUES (?,?,?,UTC_TIMESTAMP())', [$pkg, st_cut(trim((string)($in['category'] ?? '')), 60), st_cut($me, 60)]);
                $resp = ['ok' => true];
                break;
            default: throw new InvalidArgumentException('unknown action');
        }
        json_out($resp);
    } catch (StConflict $e) {
        json_out(['ok' => false, 'error' => $e->getMessage(), 'conflict' => true], 409);
    } catch (InvalidArgumentException $e) {
        json_out(['ok' => false, 'error' => $e->getMessage()], 400);
    } catch (Throwable $e) {
        error_log('mfc studio api: ' . $e->getMessage());
        json_out(['ok' => false, 'error' => 'server error: ' . st_cut($e->getMessage(), 200)], 500);
    }
}

// ---------------------------------------------------------------- GET views
$view = (string)($_GET['view'] ?? 'overview');
$out = ['ok' => true, 'view' => $view, 'server_time' => st_now(), 'cron_last' => ($c = (int)(kv_get('st_cron_last') ?? 0)) ? gmdate('Y-m-d H:i:s', $c) : null];

switch ($view) {
    case 'overview':
        $out['pending'] = st_pending($pdo);
        $out['counts'] = [
            'ideas_new' => (int)h_q1($pdo, "SELECT COUNT(*) FROM st_opps WHERE status = 'NEW'"),
            'ideas_watch' => (int)h_q1($pdo, "SELECT COUNT(*) FROM st_opps WHERE status = 'WATCH'"),
            'projects_active' => (int)h_q1($pdo, "SELECT COUNT(*) FROM st_projects WHERE stage NOT IN ('REJECTED','CANCELLED','PUBLISHED')"),
            'published' => (int)h_q1($pdo, "SELECT COUNT(*) FROM st_projects WHERE stage = 'PUBLISHED'"),
            'evidence' => (int)h_q1($pdo, 'SELECT COUNT(*) FROM st_evidence')];
        $out['spend'] = ['month_usd' => round(st_month_spend($pdo), 2), 'limit_usd' => (float)st_set('st_ai_budget_usd')];
        $out['sources'] = array_map(fn($s) => ['id' => $s['id'], 'name' => $s['name'], 'status' => $s['status'], 'missing' => $s['missing'], 'paid' => $s['paid']], st_sources($pdo));
        $out['runs'] = st_run_rows($pdo, h_q($pdo, 'SELECT * FROM st_runs ORDER BY id DESC LIMIT 6'));
        $out['activity'] = h_q($pdo, "SELECT id, kind, level, actor, msg, created_at, project_id, opp_id FROM st_events WHERE kind <> 'log' ORDER BY id DESC LIMIT 8");
        $out['top'] = array_map(fn($o) => st_opp_row($o), h_q($pdo, "SELECT * FROM st_opps WHERE status IN ('NEW','WATCH') ORDER BY GREATEST(COALESCE(organic_score,0), COALESCE(paid_score,0)) DESC LIMIT 3"));
        $out['schedule'] = ['daily' => st_set('st_daily') === '1', 'weekly' => st_set('st_weekly') === '1', 'monthly' => st_set('st_monthly') === '1',
            'weekly_dow' => (int)st_set('st_weekly_dow'), 'last_daily' => kv_get('st_daily_day'), 'last_weekly' => kv_get('st_weekly_wk'), 'last_monthly' => kv_get('st_monthly_m')];
        $out['mine'] = st_port_brief($pdo);
        $out['discovery'] = st_disc_brief($pdo);
        break;

    case 'discovery':   // daily discovery: latest, ya &id= (artifact), ya &run= (us run ka artifact)
        $out += st_disc_view($pdo, max(0, (int)($_GET['id'] ?? 0)), max(0, (int)($_GET['run'] ?? 0)));
        break;

    case 'mine':   // aapke apne apps: eCPM, kamai, spend, ROAS, country-wise profit/loss (last 30 days)
        $out += st_port_view($pdo);
        $out['running'] = (int)h_q1($pdo, "SELECT COUNT(*) FROM st_runs WHERE job = 'portfolio' AND status IN ('QUEUED','RUNNING')") > 0;
        // aakhri portfolio run (fail hua ho to app wajah dikhata hai)
        $lr = h_q($pdo, "SELECT status, error_text, finished_at FROM st_runs WHERE job = 'portfolio' ORDER BY id DESC LIMIT 1");
        $out['last_run'] = $lr ? ['status' => (string)$lr[0]['status'], 'error_text' => $lr[0]['error_text'], 'finished_at' => $lr[0]['finished_at']] : null;
        break;

    case 'research':
        $engine = in_array($_GET['engine'] ?? '', ['organic', 'paid', 'combined'], true) ? $_GET['engine'] : 'organic';
        $col = ['organic' => 'organic_score', 'paid' => 'paid_score', 'combined' => 'combined_score'][$engine];
        $w = []; $p = [];
        $status = (string)($_GET['status'] ?? 'active');
        if ($status === 'active') $w[] = "status IN ('NEW','WATCH','SELECTED','EVALUATING')";
        elseif (in_array($status, ['NEW', 'WATCH', 'SELECTED', 'REJECTED', 'EVALUATING'], true)) { $w[] = 'status = ?'; $p[] = $status; }
        if (preg_match('/^[A-Z]{2}$/', (string)($_GET['cc'] ?? ''))) { $w[] = 'FIND_IN_SET(?, countries)'; $p[] = $_GET['cc']; }
        if (($_GET['category'] ?? '') !== '') { $w[] = 'category = ?'; $p[] = (string)$_GET['category']; }
        if (($_GET['tier'] ?? '') !== '') {
            $tcc = array_values(array_map(fn($c) => $c['cc'], array_filter(st_countries(), fn($c) => (string)$c['tier'] === (string)$_GET['tier'])));
            if ($tcc) $w[] = '(' . implode(' OR ', array_map(fn($c) => 'FIND_IN_SET(' . $pdo->quote($c) . ', countries)', $tcc)) . ')';
        }
        $out['engine'] = $engine;
        $out['items'] = array_map(fn($o) => st_opp_row($o), h_q($pdo, 'SELECT * FROM st_opps' . ($w ? ' WHERE ' . implode(' AND ', $w) : '') . " ORDER BY $col IS NULL, $col DESC, coverage DESC LIMIT 60", $p));
        $out['countries'] = st_countries();
        $out['categories'] = array_values(array_unique(array_merge(st_categories(), array_column(h_q($pdo, "SELECT DISTINCT category FROM st_opps WHERE category <> ''"), 'category'))));
        $last = $pdo->query("SELECT r.*, a.version FROM st_artifacts a JOIN st_runs r ON r.id = a.run_id WHERE a.project_id IS NULL AND a.kind = 'ranking' ORDER BY a.id DESC LIMIT 1")->fetch();
        $out['last_ranking'] = $last ? st_run_rows($pdo, [$last])[0] + ['version' => (int)$last['version']] : null;
        $out['weights'] = st_weights();
        break;

    case 'opp':
        $o = $pdo->query('SELECT * FROM st_opps WHERE id = ' . (int)($_GET['id'] ?? 0))->fetch();
        if (!$o) json_out(['ok' => false, 'error' => 'not found'], 404);
        $out['item'] = st_opp_row($o, true);
        $ids = array_filter(explode(',', (string)$o['evidence_ids']));
        foreach ((array)(st_dec($o['profit'], [])['evidence_ids'] ?? []) as $e) $ids[] = $e;
        $out['evidence'] = st_evidence_rows($pdo, $ids);
        $out['history'] = h_q($pdo, 'SELECT kind, level, actor, msg, created_at FROM st_events WHERE opp_id = ? ORDER BY id DESC LIMIT 30', [(int)$o['id']]);
        $out['decisions'] = h_q($pdo, 'SELECT gate, decision, feedback, user, created_at FROM st_approvals WHERE opp_id = ? ORDER BY id DESC', [(int)$o['id']]);
        $out['project_id'] = ($pid = h_q1($pdo, 'SELECT id FROM st_projects WHERE opp_id = ? ORDER BY id DESC LIMIT 1', [(int)$o['id']])) !== null ? (int)$pid : null;
        break;

    case 'compare':
        $ids = array_slice(array_values(array_filter(array_map('intval', explode(',', (string)($_GET['ids'] ?? ''))))), 0, 5);
        $out['items'] = $ids ? array_map(fn($o) => st_opp_row($o, true), h_q($pdo, 'SELECT * FROM st_opps WHERE id IN (' . implode(',', $ids) . ')')) : [];
        break;

    case 'evidence':
        $out['items'] = st_evidence_rows($pdo, explode(',', (string)($_GET['ids'] ?? '')));
        break;

    case 'projects':
        $out['items'] = array_map(fn($p) => st_project_row($pdo, $p), h_q($pdo, 'SELECT * FROM st_projects ORDER BY (next_gate IS NULL), updated_at DESC LIMIT 50'));
        break;

    case 'project':
        $p = $pdo->query('SELECT * FROM st_projects WHERE id = ' . (int)($_GET['id'] ?? 0))->fetch();
        if (!$p) json_out(['ok' => false, 'error' => 'not found'], 404);
        $out['item'] = st_project_row($pdo, $p, true);
        $out['history'] = h_q($pdo, 'SELECT kind, level, actor, msg, created_at FROM st_events WHERE project_id = ? ORDER BY id DESC LIMIT 40', [(int)$p['id']]);
        break;

    case 'approvals':
        $out['pending'] = st_pending($pdo);
        $out['recent'] = h_q($pdo, 'SELECT a.gate, a.decision, a.feedback, a.user, a.created_at, a.artifact_version, a.project_id, a.opp_id, COALESCE(p.name, o.title) name
            FROM st_approvals a LEFT JOIN st_projects p ON p.id = a.project_id LEFT JOIN st_opps o ON o.id = a.opp_id ORDER BY a.id DESC LIMIT 40');
        break;

    case 'history':
        $w = []; $p = [];
        if (($q = trim((string)($_GET['q'] ?? ''))) !== '') { $w[] = 'msg LIKE ?'; $p[] = '%' . str_replace(['%', '_'], ['\%', '\_'], $q) . '%'; }
        if (in_array($_GET['kind'] ?? '', ['log', 'decision', 'artifact', 'action', 'notify'], true)) { $w[] = 'kind = ?'; $p[] = $_GET['kind']; }
        $page = max(0, (int)($_GET['page'] ?? 0));
        $out['items'] = h_q($pdo, 'SELECT id, run_id, project_id, opp_id, kind, level, actor, msg, created_at FROM st_events' . ($w ? ' WHERE ' . implode(' AND ', $w) : '') . ' ORDER BY id DESC LIMIT 60 OFFSET ' . ($page * 60), $p);
        $out['page'] = $page;
        break;

    case 'runs':
        $out['items'] = st_run_rows($pdo, h_q($pdo, 'SELECT * FROM st_runs ORDER BY id DESC LIMIT 50'));
        break;

    case 'live':   // chalti / queued / abhi khatam runs: har screen ka live card isi se
        $out['runs'] = st_live_runs($pdo);
        break;

    case 'run':
        $r = $pdo->query('SELECT * FROM st_runs WHERE id = ' . (int)($_GET['id'] ?? 0))->fetch();
        if (!$r) json_out(['ok' => false, 'error' => 'not found'], 404);
        $out['item'] = st_run_rows($pdo, [$r])[0];
        $out['log'] = h_q($pdo, 'SELECT level, msg, created_at FROM st_events WHERE run_id = ? ORDER BY id LIMIT 300', [(int)$r['id']]);
        $out['usage'] = h_q($pdo, 'SELECT kind, model, in_tokens, out_tokens, searches, cost_usd, ok, created_at FROM st_usage WHERE run_id = ? ORDER BY id', [(int)$r['id']]);
        break;

    case 'sources':
        $out['items'] = st_sources($pdo);
        break;

    case 'settings':
        $s = [];
        foreach (ST_SETTINGS as $k => $d) $s[$k] = st_set($k);
        $out['settings'] = $s;
        $out['countries'] = st_countries();
        $out['categories'] = st_categories();
        $out['weights'] = st_weights();
        $out['factors'] = array_map(fn($k, $f) => ['key' => $k, 'label' => $f[0], 'engine' => $f[1], 'higher_is' => $f[2] > 0 ? 'better' : 'worse', 'default' => $f[3]], array_keys(ST_FACTORS), ST_FACTORS);
        $cats = st_app_category($pdo);
        $out['apps'] = array_map(fn($pkg, $a) => ['pkg' => $pkg, 'name' => (string)($a['name'] ?? $pkg), 'category' => $cats[$pkg] ?? ''], array_keys(apps()), apps());
        $out['spend'] = ['month_usd' => round(st_month_spend($pdo), 2)];
        $out['ai_key'] = h_ai_key() !== '';
        $out['email'] = h_email_on();
        break;

    default:
        json_out(['ok' => false, 'error' => 'unknown view'], 400);
}
json_out($out);
