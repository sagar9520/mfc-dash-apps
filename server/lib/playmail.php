<?php
/**
 * Play Console emails -> app alerts. Google review reject / policy / Play Console inbox ka koi API nahi deta, isliye:
 *  - Sagar ke Gmail accounts Play Console ki mails hosting ke ek mailbox par forward karte hain; yeh file us mailbox ko IMAP (TLS, 993) se padhti hai.
 *  - PHP imap extension ki zaroorat nahi: chhota IMAP client (stream_socket_client ssl://), literals {n} sahi se, timeout 20s, ek run me max 30 mails.
 *  - Sirf google.com ki mails (seedhi ya forward ke andar wali From) jinme "Google Play" ho. Har mail = app_alerts ki ek row (rule play_mail), duplicate kabhi nahi.
 *  - DKIM: sirf sabse upar wale Authentication-Results (hamare mail server ka; neeche wale nakli ho sakte) par bharosa. Server yeh header na lagaye to 'unknown'.
 *  - Level (conservative): 3 urgent sirf saaf "ho gaya" wale reject / not approved / removed / suspended / terminated par
 *           ("will be removed", "to avoid suspension", "if rejected" jaisi dhamki / shart = 2),
 *           2 watch (violation / not compliant / action required / warning / issue found / declaration / deadline / limited / subject me policy),
 *           1 info (approved / live / published / available / review complete, baaki).
 *  - Config: health_config.php me 'play_mail' => ['host' => .., 'port' => 993, 'user' => .., 'pass' => ..]. Na ho / khali ho to sab band (kuch nahi chalta).
 *  - Health cron (har 5 min) se chalta hai, lock ke saath. Status kv 'pm_status' me: app health.php?action=playmail_status se dekhta hai.
 */
declare(strict_types=1);
require_once __DIR__ . '/health.php';

const PM_MAX_MAILS  = 30;      // ek run me itni mails
const PM_TIMEOUT    = 20;      // har socket read/connect (sec)
const PM_TEXT_BYTES = 20000;   // mail body ke pehle itne bytes
const PM_BUDGET_SEC = 75;      // ek run ka max time (cron ka baaki kaam na ruke)
const PM_EVERY_SEC  = 240;     // cron har minute bhi chale to mailbox har ~5 min hi dekho
const PM_SAME_HOURS = 12;      // same content (do Gmail se forward) itne ghante me = duplicate (asli dobara reject 12h baad phir alert)
const PM_BAD_TRIES  = 3;       // ek mail par itni baar error (DB / parse) -> chhod do (Seen), taaki baaki mails na atkein

// ================================================================ config + status
/** null = feature band (config nahi / adhoora / sample wali values). */
function pm_cfg(): ?array
{
    $c = hcfg('play_mail');
    if (!is_array($c)) return null;
    $host = trim((string)($c['host'] ?? '')); $user = trim((string)($c['user'] ?? '')); $pass = (string)($c['pass'] ?? '');
    if ($host === '' || $user === '' || $pass === '' || preg_match('/example\.com|YOUR-/i', $host . ' ' . $user)) return null;
    if (!preg_match('/^[a-z0-9.-]+$/i', $host)) return null;
    $port = (int)($c['port'] ?? 993);
    $fw = array_values(array_filter(array_map(fn($x) => strtolower(trim((string)$x)), (array)($c['forwarders'] ?? []))));
    return ['host' => $host, 'port' => $port > 0 ? $port : 993, 'user' => $user, 'pass' => $pass, 'forwarders' => $fw];
}

function pm_status(): array
{
    $s = json_decode((string)(kv_get('pm_status') ?? ''), true);
    return is_array($s) ? $s : [];
}

/** App ke liye: {configured, last_check, last_ok, last_error, last_count, last_mails, unseen_left, mailbox}. Times UTC 'Y-m-d H:i:s'. */
function pm_status_out(): array
{
    $c = pm_cfg(); $s = pm_status();
    $mb = $c ? preg_replace('/(?<=.).(?=[^@]*@)/', '•', $c['user']) . ' @ ' . $c['host'] : '';
    return ['configured' => $c !== null, 'last_check' => $s['last_check'] ?? null, 'last_ok' => $s['last_ok'] ?? null, 'last_error' => (string)($s['last_error'] ?? ''),
        'last_count' => (int)($s['last_count'] ?? 0), 'last_mails' => (int)($s['last_mails'] ?? 0), 'unseen_left' => (int)($s['unseen_left'] ?? 0), 'mailbox' => $mb];
}

// ================================================================ run (cron + test button)
/** Cron se: config ho aur ~5 min ho gaye hon tabhi. */
function pm_cron(PDO $pdo, callable $log): void
{
    if (!pm_cfg() || !h_due('pm_last_run', PM_EVERY_SEC)) return;
    kv_set('pm_last_run', (string)time());
    $r = pm_run($pdo, 'cron');
    $log('play mail: ' . ($r['ok'] ? "{$r['mails']} mails, {$r['alerts']} alerts" : 'ERROR ' . $r['error']));
}

/** Ek check: login -> INBOX -> UNSEEN (max 30) -> har mail ka alert -> \Seen. Lock: do run ek saath nahi. */
function pm_run(PDO $pdo, string $by = 'cron'): array
{
    $cfg = pm_cfg();
    $res = ['ok' => false, 'configured' => $cfg !== null, 'mails' => 0, 'alerts' => 0, 'duplicates' => 0, 'skipped' => 0, 'unseen' => 0, 'error' => '', 'ms' => 0];
    if (!$cfg) { $res['error'] = 'play_mail not set in health_config.php'; return $res; }
    if ((int)$pdo->query("SELECT GET_LOCK('mfc_playmail', 0)")->fetchColumn() !== 1) { $res['error'] = 'another Play mail check is running, try again in a minute'; return $res; }
    $t0 = microtime(true);
    $im = null;
    try {
        if (!$pdo->query("SHOW TABLES LIKE 'app_alerts'")->fetch()) throw new RuntimeException('app_alerts table missing: upload api/alerts.php and open Alerts in the app once');
        $known = pm_known_apps($pdo);
        $im = new PMImap($cfg['host'], $cfg['port']);
        $im->login($cfg['user'], $cfg['pass']);
        $im->cmd('SELECT INBOX');
        $uids = $im->searchUnseen();
        $res['unseen'] = count($uids);
        foreach (array_slice($uids, 0, PM_MAX_MAILS) as $uid) {
            if (microtime(true) - $t0 > PM_BUDGET_SEC) break;
            try {
                $m = $im->fetch($uid);
                $r = $m === null ? 'skip' : pm_handle($pdo, $m, $known, $cfg);
            } catch (PMImapError $e) { throw $e; }   // connection gaya: poora run fail, mail Unseen hi rahe
            catch (Throwable $e) {
                // DB / parse error: agle run me dobara; PM_BAD_TRIES baar ke baad chhod do (ek kharab mail queue na roke)
                $bad = json_decode((string)(kv_get('pm_bad') ?? ''), true);
                $bad = is_array($bad) ? $bad : [];
                $bad[(string)$uid] = (int)($bad[(string)$uid] ?? 0) + 1;
                kv_set('pm_bad', h_json(array_slice($bad, -50, null, true)));
                if ($bad[(string)$uid] < PM_BAD_TRIES) throw $e;
                h_log($pdo, "Play mail UID $uid skipped after " . PM_BAD_TRIES . ' failures: ' . h_cut($e->getMessage(), 200), 'error');
                $r = 'skip';
            }
            $res['mails']++;
            if ($r === 'alert') $res['alerts']++; elseif ($r === 'dup') $res['duplicates']++; else $res['skipped']++;
            $im->seen($uid);   // alert DB me likhne ke BAAD hi: DB error par mail agle run me dobara aayegi
        }
        $im->logout();
        $res['ok'] = true;
    } catch (Throwable $e) {
        $res['error'] = h_cut($e->getMessage(), 300);
        h_log($pdo, 'Play mail check failed (' . $by . '): ' . $res['error'], 'error');
    } finally {
        if ($im) $im->close();
        $pdo->query("SELECT RELEASE_LOCK('mfc_playmail')");
    }
    $res['ms'] = (int)round((microtime(true) - $t0) * 1000);
    $prev = pm_status();
    kv_set('pm_status', h_json(['last_check' => h_now(), 'last_ok' => $res['ok'] ? h_now() : ($prev['last_ok'] ?? null), 'last_error' => $res['error'],
        'last_count' => $res['alerts'], 'last_mails' => $res['mails'], 'unseen_left' => max(0, $res['unseen'] - $res['mails']), 'by' => h_cut($by, 60)]));
    if ($res['alerts'] > 0) h_log($pdo, "Play mail: {$res['alerts']} new alerts from {$res['mails']} mails ($by)");
    return $res;
}

/** Ek mail -> 'alert' | 'dup' | 'skip'. Insert fail (DB) = exception (mail Seen nahi hogi). */
function pm_handle(PDO $pdo, array $m, array $known, array $cfg): string
{
    $mail = pm_parse($m['header'], $m['text']);
    $from = $mail['from_addr'];
    $direct = pm_is_google($from);
    $via = 'direct';
    if (!$direct) {
        // forward kiya hua: andar wale header block / attached mail ka From google.com ho
        if (!pm_is_google($mail['fwd_from_addr'])) return 'skip';
        if ($cfg['forwarders'] && !in_array($from, $cfg['forwarders'], true)) return 'skip';   // (optional) sirf Sagar ke Gmail se forward
        $via = $mail['fwd_attached'] ? 'attached' : 'forward';
    }
    // From ke domain (direct = google.com, forward = forward karne wale ka domain) ka DKIM fail = nakli mail: skip, par log me dikhe
    $dkim = pm_dkim($mail['ar'], $direct ? 'google.com' : (string)substr((string)strrchr($from, '@'), 1));
    if ($dkim === 'fail') {
        h_log($pdo, 'Play mail skipped (DKIM fail, possible fake): ' . h_cut($from . ' · ' . $mail['subject'], 200), 'warn');
        return 'skip';
    }
    $subject = pm_clean_subject($via === 'direct' || $mail['fwd_subject'] === '' ? $mail['subject'] : $mail['fwd_subject']);
    $orig = $direct ? $from : $mail['fwd_from_addr'];
    $isFwdConfirm = $direct && $from === 'forwarding-noreply@google.com' && preg_match('/forwarding confirmation/i', $subject) === 1;
    // Play wali mail: bhejne wala googleplay / play-console, ya subject / text me "Google Play" / "Play Console" (sirf Play Store receipt nahi)
    $full = mb_strtolower($subject . "\n" . $mail['text']);
    $isPlay = preg_match('/play/', (string)strstr($orig, '@', true)) === 1 || str_contains($full, 'google play') || str_contains($full, 'play console');
    if (!$isFwdConfirm && (!$isPlay || preg_match('/\border\s+receipt\b/i', $subject))) return 'skip';
    $body = pm_meaningful($mail['text']);

    // Message-ID (attached forward me asli mail ka) = ek mail ek hi alert
    $mid = trim($via === 'attached' && $mail['fwd_message_id'] !== '' ? $mail['fwd_message_id'] : $mail['message_id'], " <>\t");
    $akey = 'mail|' . sha1($mid !== '' ? strtolower($mid) : ($from . '|' . $mail['date'] . '|' . $mail['subject']));
    if ((int)h_q1($pdo, 'SELECT COUNT(*) FROM app_alerts WHERE akey = ?', [$akey]) > 0) return 'dup';
    // wahi mail do Gmail se (alag Message-ID): subject + text same, PM_SAME_HOURS me
    $chash = substr(sha1(mb_strtolower($subject) . '|' . mb_substr(preg_replace('/\s+/u', ' ', mb_strtolower($body)) ?? '', 0, 400)), 0, 16);
    if ((int)h_q1($pdo, "SELECT COUNT(*) FROM app_alerts WHERE rule = 'play_mail' AND created_at >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL " . PM_SAME_HOURS . " HOUR) AND metrics LIKE ?",
        ['%"chash":"' . $chash . '"%']) > 0) return 'dup';

    if ($isFwdConfirm) {
        // Gmail forwarding setup ki confirmation mail: link / code app me dikhao (warna webmail kholna padta)
        $link = preg_match('#https://mail(?:-settings)?\.google\.com/[^\s<>"\'()]+#', $mail['text'], $mm) ? rtrim($mm[0], '.,)>') : '';
        $code = preg_match('/\b(?:confirmation code|code)\s*:?\s*(\d{6,12})\b/i', $mail['text'], $mc) ? $mc[1] : '';
        $t = ['Gmail forwarding confirmation', 'Gmail forwarding confirm karo'];
        $b = 'Open this link to allow the forwarding: ' . ($link ?: '(see the mail)') . ($code !== '' ? " · code $code" : '');
        pm_alert_insert($pdo, $akey, 1, '', $t, [$b, $b], ['from' => $from, 'date' => $mail['date'], 'level_word' => 'info', 'match' => 'forwarding confirmation', 'via' => $via,
            'forwarded_by' => '', 'dkim' => $dkim, 'chash' => $chash]);
        return 'alert';
    }

    $app = pm_find_app($subject, $body . "\n" . $mail['text'], $known);
    // app ka naam / package classify se pehle 'appname' bana do ("Live Location" jaise naam se 'live' na lage; "<naam> has been removed" pakda jaaye)
    [$level, $word, $hiPrefix] = pm_level(pm_strip_app($subject, $app), pm_strip_app(pm_meaningful($mail['text'], 2000), $app));
    $name = $app['name'] ?? '';
    $title = $subject !== '' ? $subject : 'Google Play Console email';
    $titleHi = $hiPrefix . ' · ' . ($name !== '' ? $name : mb_substr($title, 0, 80));
    $bodyTxt = $body !== '' ? $body : '(no text)';
    pm_alert_insert($pdo, $akey, $level, (string)($app['pkg'] ?? ''), [$title, $titleHi], [$bodyTxt, $bodyTxt], [
        'from' => $orig, 'date' => $mail['fwd_date'] !== '' && !$direct ? $mail['fwd_date'] : $mail['date'],
        'level_word' => $level === 3 ? 'urgent' : ($level === 2 ? 'watch' : 'info'), 'match' => $word, 'via' => $via, 'forwarded_by' => $direct ? '' : $from,
        'dkim' => $dkim, 'chash' => $chash]);
    return 'alert';
}

/** app_alerts row (wahi table jo smart alerts / health_* ki): app ka live monitor ise uthakar turant notification deta hai. */
function pm_alert_insert(PDO $pdo, string $akey, int $level, string $pkg, array $title, array $body, array $metrics): void
{
    // raw header (Date / Message-ID) me galat UTF-8 ho to json_encode false deta (TypeError): pehle saaf karo
    $metrics = array_map(fn($v) => is_string($v) ? h_cut(pm_utf8($v, ''), 300) : $v, $metrics);
    h_x($pdo, 'INSERT INTO app_alerts (akey, level, rule, pkg, account, campaign, title, body, title_hi, body_hi, ai, metrics, fired, created_at, updated_at)
        VALUES (?,?,\'play_mail\',?,\'\',\'\',?,?,?,?,\'\',?,1,UTC_TIMESTAMP(),UTC_TIMESTAMP())',
        [h_cut($akey, 190), max(1, min(3, $level)), h_cut($pkg, 100), h_cut($title[0], 255), h_cut($body[0], 2000), h_cut($title[1] ?? $title[0], 255), h_cut($body[1] ?? $body[0], 2000), h_json($metrics)]);
}

// ================================================================ classify / app match
/** Mail ka [level, word, Hinglish prefix]. Subject pehle; body tab jab subject kuch na bataye, ya jab body me pakka urgent
 *  ("your app ... has been removed") ho aur subject usse kam bataye. Kuch na mile = info 'other'. */
function pm_level(string $subject, string $body): array
{
    $cs = pm_classify(pm_cls_prep($subject), true);
    $cb = pm_classify(pm_cls_prep($body), false);
    if ($cb !== null && $cb[0] === 3 && ($cs === null || $cs[0] < 3)) return $cb;
    return $cs ?? $cb ?? [1, 'other', 'Play: mail'];
}

/** Classify ke liye text: lowercase; jo abhi hua nahi (dhamki / shart: "will be removed", "to avoid suspension", "if rejected") = 'removalrisk',
 *  user / tester / permission hatna = 'infochange' (app ka kuch nahi bigda). */
function pm_cls_prep(string $t): string
{
    $t = mb_strtolower(str_replace(["\u{2019}", "\u{2018}"], "'", $t));
    $t = preg_replace('/\s+/u', ' ', $t) ?? $t;
    $x = '(?:rejected|removed|suspended|terminated|taken down|unpublished|rejection|removal|suspension|termination|not (?:be )?approved)';
    $t = preg_replace([
        '/\b(?:will|may|might|could|can|would|shall)\s+(?:not\s+)?(?:also\s+|soon\s+|then\s+)?(?:be\s+|get\s+|result\s+in\s+|lead\s+to\s+|face\s+)?(?:[a-z\'-]+\s+){0,2}?' . $x . '\b/u',
        '/\b(?:avoid(?:ing)?|prevent(?:ing)?|risk(?:s|ing)?|if|unless|otherwise|before|in case)\b[^.!?;\n]{0,80}?' . $x . '\b/u',
    ], ' removalrisk ', $t) ?? $t;
    $t = preg_replace('/\b(?:users?|testers?|members?|permissions?|access|e-?mail address(?:es)?|contacts?|payment methods?|cards?)\b[^.!?;\n]{0,40}?\b(?:has|have|was|were)\s+(?:been\s+)?removed\b/u',
        ' infochange ', $t) ?? $t;
    return trim($t);
}

/** [level, word, Hinglish prefix] ya null ($t = pm_cls_prep wala). Pehle urgent, phir watch, phir info: pehla match jeetta hai.
 *  Urgent: subject me seedha shabd (rejected / removed / suspended ...); body me sirf "your app / update / account ... has been X" ya "removed your app". */
function pm_classify(string $t, bool $subject = false): ?array
{
    if ($t === '') return null;
    static $V = ['rejected' => ['rejected', 'Play: reject'], 'rejection' => ['rejected', 'Play: reject'], 'not approved' => ['not approved', 'Play: reject'], 'not been approved' => ['not approved', 'Play: reject'],
        'removed' => ['removed', 'Play: app hataya'], 'removal' => ['removed', 'Play: app hataya'], 'taken down' => ['removed', 'Play: app hataya'], 'unpublished' => ['removed', 'Play: app hataya'],
        'suspended' => ['suspended', 'Play: suspend'], 'suspension' => ['suspended', 'Play: suspend'], 'terminated' => ['terminated', 'Play: account band'], 'termination' => ['terminated', 'Play: account band']];
    static $rules = [
        [2, '/\bremovalrisk\b/u', 'removal warning', 'Play: hataane ki warning', false],
        [2, '/\bviolat(?:e|es|ed|ing|ion|ions)\b/u', 'violation', 'Play: policy violation', false],
        [2, '/\bnot\s+(?:in\s+)?complian(?:t|ce)\b|\bnon-?complian(?:t|ce)\b/u', 'not compliant', 'Play: policy compliance', false],
        [2, '/\baction\s+(?:is\s+)?(?:required|needed)\b|\brequires?\s+(?:your\s+)?(?:immediate\s+)?action\b/u', 'action required', 'Play: action zaroori', false],
        [2, '/\bwarning\b/u', 'warning', 'Play: warning', false],
        [2, '/\bissues?\s+(?:were\s+|was\s+|have\s+been\s+|has\s+been\s+)?(?:found|detected)\b/u', 'issue found', 'Play: issue mila', false],
        [2, '/\bdeclarations?\b/u', 'declaration', 'Play: declaration bharo', false],
        [2, '/\bdeadline\b/u', 'deadline', 'Play: deadline', false],
        [2, '/\b(?:visibility|distribution|reach|availability|discoverability)\b[^.!?;\n]{0,30}?\b(?:limited|restricted|reduced)\b|\b(?:limited|restricted|reduced)\s+(?:visibility|distribution|reach|availability|discoverability)\b/u',
            'limited', 'Play: limited', false],
        [2, '/\bpolic(?:y|ies)\b/u', 'policy', 'Play: policy', true],   // sirf subject: body me lagbhag har Play mail ke link / footer me hota hai
        [1, '/\breview\s+(?:is\s+|was\s+|has\s+been\s+)?complete/u', 'review complete', 'Play: review done', false],
        [1, '/\bapproved\b/u', 'approved', 'Play: approve', false],
        [1, '/\blive\b/u', 'live', 'Play: live', false],
        [1, '/\bpublished\b/u', 'published', 'Play: live', false],
        [1, '/\bavailable\b/u', 'available', 'Play: live', false],
    ];
    $verb = '(rejected|not approved|not been approved|removed|taken down|unpublished|suspended|terminated)';
    $ctx = '(?:\b(?:your|the)\s+(?:apps?|application|update|game|release|submission|developer account|account|store listing|listing)\b|\bappname\b)';
    $urgent = $subject
        ? ['/\b' . $verb . '\b/u', '/\b(?:app|update|account|submission|release)\s+(rejection|removal|suspension|termination)\b/u', '/\b(rejection|removal|suspension|termination)\s+notice\b/u']
        : ['/' . $ctx . '[^.!?;\n]{0,80}?\b(?:has|have|had|was|were|is|are|got)\s+(?:now\s+)?(?:been\s+)?' . $verb . '\b/u',
           '/\b(?:has|have|had|we\'ve|we|google play|google)\s+(?:now\s+)?(rejected|removed|suspended|terminated|unpublished)\s+' . $ctx . '/u'];
    foreach ($urgent as $re) if (preg_match($re, $t, $m)) return [3, ...$V[$m[1]]];
    foreach ($rules as [$lvl, $re, $word, $hi, $subjOnly]) if ((!$subjOnly || $subject) && preg_match($re, $t)) return [$lvl, $word, $hi];
    return null;
}

/** health_apps (+ dashboard apps) ke package aur naam. */
function pm_known_apps(PDO $pdo): array
{
    $names = [];
    foreach (apps() as $pkg => $a) $names[(string)$pkg] = (string)($a['name'] ?? '');
    try { foreach (h_q($pdo, 'SELECT pkg, name FROM health_apps') as $r) if (($names[$r['pkg']] ?? '') === '' || $r['name'] !== '') $names[(string)$r['pkg']] = (string)$r['name']; }
    catch (Throwable $e) { }
    $pk = [];
    foreach ($names as $p => $n) $pk[strtolower($p)] = $p;
    return ['pkgs' => $pk, 'names' => $names];
}

/** Pehle package name (com.x.y), phir app ka naam (subject me pehle, phir text me; sabse lamba naam jeetta hai). ['pkg','name'] ya null. */
function pm_find_app(string $subject, string $text, array $known): ?array
{
    $all = strtolower($subject . "\n" . $text);
    if (preg_match_all('/(?<![a-z0-9_.])[a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+/', $all, $mm)) {
        foreach ($mm[0] as $cand) {
            $cand = rtrim($cand, '.');
            if (isset($known['pkgs'][$cand])) { $p = $known['pkgs'][$cand]; return ['pkg' => $p, 'name' => $known['names'][$p] !== '' ? $known['names'][$p] : $p]; }
        }
    }
    foreach ([$subject, $text] as $hay) {
        $best = null;
        foreach ($known['names'] as $p => $n) {
            if (mb_strlen($n) < 4) continue;
            if (preg_match('/(?<![\p{L}\p{N}])' . preg_quote($n, '/') . '(?![\p{L}\p{N}])/iu', $hay) && ($best === null || mb_strlen($n) > mb_strlen($best['name']))) $best = ['pkg' => (string)$p, 'name' => $n];
        }
        if ($best) return $best;
    }
    return null;
}

/** App ka naam / package -> ' appname ' (classify me naam ke shabd na gine jaayen; "<naam> has been removed" app ke baare me maana jaaye). */
function pm_strip_app(string $t, ?array $app): string
{
    if (!$app) return $t;
    foreach ([$app['name'], $app['pkg']] as $x) if ($x !== '') $t = (string)(preg_replace('/' . preg_quote($x, '/') . '/iu', ' appname ', $t) ?? $t);
    return $t;
}

function pm_is_google(string $addr): bool { return $addr !== '' && preg_match('/@(?:[a-z0-9-]+\.)*google\.com$/i', $addr) === 1; }

// ================================================================ MIME parse (quoted-printable / base64 / multipart / RFC2047)
/** Raw header + body text -> mail ki zaroori cheezen (forwarded block bhi). */
function pm_parse(string $rawHeader, string $rawText): array
{
    $h = pm_headers($rawHeader);
    $parts = pm_mime($h['content-type'] ?? 'text/plain', $h['content-transfer-encoding'] ?? '', $rawText);
    $text = $parts['plain'] !== '' ? $parts['plain'] : pm_html_text($parts['html']);
    $text = str_replace(["\r\n", "\r"], "\n", $text);
    $out = ['from' => pm_words($h['from'] ?? ''), 'from_addr' => pm_addr(pm_words($h['from'] ?? '')), 'subject' => trim(pm_words($h['subject'] ?? '')),
        'date' => trim(pm_utf8($h['date'] ?? '', '')), 'message_id' => trim(pm_utf8($h['message-id'] ?? '', '')), 'text' => $text, 'ar' => $h['authentication-results'] ?? '',
        'fwd_from_addr' => '', 'fwd_subject' => '', 'fwd_date' => '', 'fwd_message_id' => '', 'fwd_attached' => false];
    $out['dkim'] = pm_dkim($out['ar'], 'google.com');
    if ($parts['fwd'] !== null) {   // message/rfc822 attachment ke roop me forward
        $out['fwd_from_addr'] = pm_addr($parts['fwd']['from']); $out['fwd_subject'] = trim($parts['fwd']['subject']); $out['fwd_date'] = trim($parts['fwd']['date']); $out['fwd_attached'] = true;
        $out['fwd_message_id'] = trim($parts['fwd']['message_id']);
        if (trim($parts['fwd']['text']) !== '') $out['text'] = str_replace(["\r\n", "\r"], "\n", $parts['fwd']['text']);
    } elseif (($f = pm_fwd_block($text)) !== null) {
        $out['fwd_from_addr'] = pm_addr($f['from']); $out['fwd_subject'] = trim($f['subject']); $out['fwd_date'] = trim($f['date']);
    }
    return $out;
}

/** Header block -> [lowercase name => value] (folded lines jodkar). Authentication-Results: sab, upar se neeche, "\n" se jude (pm_dkim sirf upar wale server ke maanta hai). */
function pm_headers(string $raw): array
{
    $raw = preg_replace('/\r?\n[ \t]+/', ' ', str_replace("\r\n", "\n", $raw)) ?? $raw;
    $h = [];
    foreach (explode("\n", $raw) as $ln) {
        if (!preg_match('/^([A-Za-z0-9-]+):\s*(.*)$/', $ln, $m)) continue;
        $k = strtolower($m[1]);
        if (!isset($h[$k])) $h[$k] = trim($m[2]);
        elseif ($k === 'authentication-results') $h[$k] .= "\n" . trim($m[2]);
    }
    return $h;
}

/** RFC2047 encoded words (=?UTF-8?B?..?= / ?Q?) -> UTF-8. Lagaataar words ke beech ki khali jagah hatti hai, aur ek charset ke words ke bytes
 *  jodkar ek saath convert hote hain (kuch mailer ek UTF-8 akshar ko do words me kaat dete hain). */
function pm_words(string $s): string
{
    if (!preg_match_all('/=\?([^?\s]+)\?([BbQq])\?([^?\s]*)\?=/', $s, $mm, PREG_SET_ORDER | PREG_OFFSET_CAPTURE)) return pm_utf8($s, '');
    $out = ''; $buf = ''; $bcs = ''; $pos = 0; $prev = false;
    foreach ($mm as $m) {
        $gap = substr($s, $pos, $m[0][1] - $pos);
        if (!($prev && trim($gap) === '')) {   // encoded words ke beech sirf space = hatao; baaki text jaisa hai
            if ($buf !== '') $out .= pm_utf8($buf, $bcs);
            $buf = '';
            $out .= pm_utf8($gap, '');
        }
        $cs = preg_replace('/\*.*$/', '', $m[1][0]) ?? $m[1][0];   // RFC2231 language (UTF-8*en)
        $raw = strtoupper($m[2][0]) === 'B' ? (string)base64_decode($m[3][0]) : quoted_printable_decode(str_replace('_', ' ', $m[3][0]));
        if ($buf !== '' && strcasecmp($cs, $bcs) !== 0) { $out .= pm_utf8($buf, $bcs); $buf = ''; }
        $buf .= $raw; $bcs = $cs;
        $pos = $m[0][1] + strlen($m[0][0]); $prev = true;
    }
    if ($buf !== '') $out .= pm_utf8($buf, $bcs);
    return $out . pm_utf8(substr($s, $pos), '');
}

/** Kisi bhi charset -> sahi UTF-8. UTF-8 bola gaya par galat bytes (20 KB par kata akshar, kharab mailer): aakhri adhoora akshar hatao,
 *  phir bhi galat ho to sirf galat bytes '?' (poora text Latin-1 nahi). Charset na bataya ho aur UTF-8 na ho = Windows-1252. */
function pm_utf8(string $s, string $cs): string
{
    $cs = strtoupper(trim($cs, " \t\"'"));
    if ($cs === 'UTF8') $cs = 'UTF-8';
    if ($cs !== '' && $cs !== 'UTF-8' && $cs !== 'US-ASCII' && $cs !== 'ASCII') {
        try { $r = @mb_convert_encoding($s, 'UTF-8', $cs); if (is_string($r)) return mb_check_encoding($r, 'UTF-8') ? $r : mb_scrub($r, 'UTF-8'); }
        catch (Throwable $e) { }   // anjaan charset: PHP 8 ValueError -> neeche wala andaza
    }
    if (mb_check_encoding($s, 'UTF-8')) return $s;
    $t = preg_replace('/(?:[\xC2-\xDF]|[\xE0-\xEF][\x80-\xBF]?|[\xF0-\xF4][\x80-\xBF]{0,2})$/', '', $s) ?? $s;   // kata hua aakhri akshar
    // UTF-8 bola gaya, ya baaki text me UTF-8 akshar hain = UTF-8 hi hai (sirf kharab bytes '?'); warna purana 8-bit (Windows-1252)
    if ($cs === 'UTF-8' || preg_match('/[\xC2-\xF4][\x80-\xBF]/', $t)) return mb_check_encoding($t, 'UTF-8') ? $t : mb_scrub($t, 'UTF-8');
    return mb_convert_encoding($s, 'UTF-8', 'Windows-1252');
}

/** "Name <a@b.com>" -> a@b.com (lowercase). */
function pm_addr(string $s): string
{
    if (preg_match('/<\s*([^<>\s]+@[^<>\s]+)\s*>/', $s, $m)) return strtolower($m[1]);
    if (preg_match('/([A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,})/', $s, $m)) return strtolower($m[1]);
    return '';
}

/** Authentication-Results -> $domain (aur uske subdomain) ka DKIM: pass / fail / unknown.
 *  Sirf sabse upar wale header ke server (authserv-id) ki lines maani jaati hain: wahi hamara receiving server hai; neeche wali lines bhejne wala
 *  khud likh sakta hai (nakli "dkim=pass"). Server yeh header lagata hi na ho to sabse upar wala bhi nakli ho sakta hai: tab 'forwarders' config rakho.
 *  pass = us domain ka koi signature sahi; fail = us domain ka signature galat / DMARC fail (aur koi pass nahi). */
function pm_dkim(string $ar, string $domain = 'google.com'): string
{
    $list = array_values(array_filter(array_map('trim', explode("\n", strtolower($ar))), fn($x) => $x !== ''));
    $dom = strtolower(trim($domain, " .\t"));
    if (!$list || $dom === '') return 'unknown';
    $id = trim(explode(';', $list[0])[0]);
    $same = fn(string $d) => $d === $dom || str_ends_with($dom, '.' . $d) || str_ends_with($d, '.' . $dom);
    $pass = false; $fail = false;
    foreach ($list as $one) {
        $cls = explode(';', $one);
        if (trim($cls[0]) !== $id) continue;
        foreach (array_slice($cls, 1) as $cl) {
            if (preg_match('/^\s*dmarc=(\w+)/', $cl, $m)) {
                if ($m[1] === 'fail' && preg_match('/header\.from=([a-z0-9.-]+)/', $cl, $f) && $same($f[1])) $fail = true;
                continue;
            }
            if (!preg_match('/^\s*dkim=(\w+)/', $cl, $m)) continue;
            $d = preg_match('/header\.d=([a-z0-9.-]+)/', $cl, $x) ? $x[1] : (preg_match('/header\.i=[^@\s;]*@([a-z0-9.-]+)/', $cl, $x) ? $x[1] : '');
            if ($d !== '' && !$same($d)) continue;   // kisi aur domain ka signature
            if ($m[1] === 'pass' && $d !== '') $pass = true;
            elseif ($m[1] === 'fail' || $m[1] === 'permerror') $fail = true;
        }
    }
    return $pass ? 'pass' : ($fail ? 'fail' : 'unknown');
}

/** Content-Type ka parameter (boundary / charset). */
function pm_param(string $ct, string $name): string
{
    if (preg_match('/[;\s]' . preg_quote($name, '/') . '\s*=\s*(?:"([^"]*)"|([^;\s]+))/i', ' ' . $ct, $m)) return $m[1] !== '' ? $m[1] : ($m[2] ?? '');
    return '';
}

/** MIME body -> [plain, html, fwd]. Body 20 KB par kata ho sakta hai: aakhri part adhoora bhi chalega. fwd = attached mail (message/rfc822) ke From/Subject/Date. */
function pm_mime(string $ctype, string $cte, string $body, int $depth = 0): array
{
    $out = ['plain' => '', 'html' => '', 'fwd' => null];
    $ct = strtolower(trim(explode(';', $ctype)[0]));
    if ($depth < 6 && str_starts_with($ct, 'multipart/')) {
        $b = pm_param($ctype, 'boundary');
        if ($b === '' && preg_match('/^\s*--([^\r\n]{1,200})\r?\n/', $body, $m)) $b = trim($m[1]);
        if ($b === '') return $out;
        $parts = []; $cur = null;
        foreach (preg_split('/\r?\n/', $body) ?: [] as $ln) {
            $t = rtrim($ln);
            if ($t === '--' . $b) { if ($cur !== null) $parts[] = $cur; $cur = []; continue; }
            if ($t === '--' . $b . '--') { if ($cur !== null) $parts[] = $cur; $cur = null; break; }
            if ($cur !== null) $cur[] = $ln;
        }
        if ($cur !== null) $parts[] = $cur;   // kata hua aakhri part
        foreach ($parts as $lines) {
            if ($lines && trim($lines[0]) === '') $sp = ['', implode("\n", array_slice($lines, 1))];   // part bina headers ke (default text/plain)
            else { $raw = implode("\n", $lines); $sp = preg_split('/\n\s*\n/', $raw, 2) ?: [$raw]; }
            $ph = pm_headers($sp[0]);
            if (str_starts_with(strtolower($ph['content-disposition'] ?? ''), 'attachment') && !str_starts_with(strtolower($ph['content-type'] ?? ''), 'message/rfc822')) continue;
            $sub = pm_mime($ph['content-type'] ?? 'text/plain', $ph['content-transfer-encoding'] ?? '', $sp[1] ?? '', $depth + 1);
            if ($out['plain'] === '' && $sub['plain'] !== '') $out['plain'] = $sub['plain'];
            if ($out['html'] === '' && $sub['html'] !== '') $out['html'] = $sub['html'];
            if ($out['fwd'] === null && $sub['fwd'] !== null) $out['fwd'] = $sub['fwd'];
        }
        return $out;
    }
    if ($depth < 6 && $ct === 'message/rfc822') {
        $data = pm_cte($body, $cte);
        $sp = preg_split('/\r?\n\s*\r?\n/', $data, 2) ?: [$data];
        $ih = pm_headers($sp[0]);
        $sub = pm_mime($ih['content-type'] ?? 'text/plain', $ih['content-transfer-encoding'] ?? '', $sp[1] ?? '', $depth + 1);
        // attached mail ka text alag (bahar wala "see attached" jaisa note asli mail nahi)
        return ['plain' => '', 'html' => '', 'fwd' => ['from' => pm_words($ih['from'] ?? ''), 'subject' => pm_words($ih['subject'] ?? ''), 'date' => pm_utf8((string)($ih['date'] ?? ''), ''),
            'message_id' => pm_utf8((string)($ih['message-id'] ?? ''), ''), 'text' => $sub['plain'] !== '' ? $sub['plain'] : pm_html_text($sub['html'])]];
    }
    if ($ct !== 'text/plain' && $ct !== 'text/html' && $ct !== '') return $out;
    $txt = pm_utf8(pm_cte($body, $cte), pm_param($ctype, 'charset') ?: 'UTF-8');
    if ($ct === 'text/html') $out['html'] = $txt; else $out['plain'] = $txt;
    return $out;
}

/** Content-Transfer-Encoding decode (kata hua base64 bhi). */
function pm_cte(string $body, string $cte): string
{
    $cte = strtolower(trim($cte));
    if ($cte === 'base64') {
        $b = preg_replace('/[^A-Za-z0-9+\/=]/', '', $body) ?? '';
        $b = substr($b, 0, intdiv(strlen($b), 4) * 4);
        return (string)base64_decode($b);
    }
    if ($cte === 'quoted-printable') return quoted_printable_decode(preg_replace('/=\r?\n/', '', $body) ?? $body);
    return $body;
}

function pm_html_text(string $h): string
{
    if ($h === '') return '';
    $h = preg_replace('#<(style|script|head|title)\b[^>]*>.*?</\1>#is', ' ', $h) ?? $h;
    $h = preg_replace('#<\s*br\s*/?>|</\s*(p|div|tr|li|h[1-6]|table|td)\s*>#i', "\n", $h) ?? $h;
    $h = preg_replace('#<a\b[^>]*href=["\'](https?://[^"\']+)["\'][^>]*>(.*?)</a>#is', '$2 ($1)', $h) ?? $h;   // link ka address bhi bacha rahe (confirmation link)
    $t = html_entity_decode(strip_tags($h), ENT_QUOTES | ENT_HTML5, 'UTF-8');
    $t = str_replace("\xC2\xA0", ' ', $t);
    $t = preg_replace('/[ \t]+/u', ' ', $t) ?? $t;
    return trim(preg_replace('/\n\s*\n\s*(\n\s*)+/', "\n\n", $t) ?? $t);
}

/** Text me forward wala header block (Gmail / Outlook / Apple): From / Subject / Date. */
function pm_fwd_block(string $text): ?array
{
    if (!preg_match('/^[> \t]*(?:-{3,}\s*Forwarded message\s*-{3,}|-{3,}\s*Original Message\s*-{3,}|Begin forwarded message:)[^\n]*\n((?:[> \t]*[^\n]*\n){1,14})/mi', $text . "\n", $m)) return null;
    $blk = preg_replace('/^[> \t]+/m', '', $m[1]) ?? $m[1];
    $get = fn(string $k) => preg_match('/^' . $k . ':\s*(.+)$/mi', $blk, $x) ? trim(pm_words($x[1])) : '';
    $f = ['from' => $get('From'), 'subject' => $get('Subject'), 'date' => $get('Date') ?: $get('Sent')];
    return $f['from'] !== '' ? $f : null;
}

function pm_clean_subject(string $s): string
{
    $s = trim(preg_replace('/\s+/u', ' ', $s) ?? $s);
    for ($i = 0; $i < 5; $i++) {
        $n = preg_replace('/^\s*(?:(?:fwd?|fw|wg|tr|rv|aw|re)\s*:\s*|\[(?:ext|external|fwd)[^\]]{0,20}\]\s*)/i', '', $s) ?? $s;
        if ($n === $s) break;
        $s = $n;
    }
    return h_cut(trim($s), 255);
}

/** Mail ka kaam ka text (~$max chars, alert body 600): forward ke header, quotes '>', footer / legal / unsubscribe hata kar; lambe tracking links chhote. */
function pm_meaningful(string $text, int $max = 600): string
{
    $t = str_replace(["\r\n", "\r"], "\n", $text);
    // forward block ke baad wala hissa hi asli mail hai
    if (preg_match('/^[> \t]*(?:-{3,}\s*Forwarded message\s*-{3,}|-{3,}\s*Original Message\s*-{3,}|Begin forwarded message:)[^\n]*\n/mi', $t, $m, PREG_OFFSET_CAPTURE)) {
        $rest = substr($t, $m[0][1] + strlen($m[0][0]));
        $rest = preg_replace('/\A(?:[> \t]*(?:From|Date|Sent|Subject|To|Cc|Reply-To):[^\n]*\n)+/i', '', $rest) ?? $rest;
        $t = $rest;
    }
    $foot = '/^(?:©|\(c\)\s*\d{4}|copyright\b|google (?:llc|ireland|commerce|asia)|1600 amphitheatre|you (?:have )?received this|you\'re receiving this|you are receiving this|this (?:e-?mail|message) was sent|'
        . 'to (?:unsubscribe|stop receiving)|unsubscribe\b|manage (?:your )?(?:email|notification)|privacy policy\s*[|·]|please do not reply|do not reply to this|this is an automated)/i';
    $out = []; $lines = 0;
    foreach (explode("\n", $t) as $ln) {
        $ln = trim(preg_replace('/^[> \t]+/', '', $ln) ?? $ln);
        if (preg_match($foot, $ln)) { if ($lines >= 2) break; continue; }   // footer: asli text ke baad ho tabhi yahin khatam
        if ($ln !== '') $lines++;
        $ln = preg_replace_callback('#https?://[^\s<>()"]+#', fn($u) => strlen($u[0]) > 80 ? (parse_url($u[0], PHP_URL_SCHEME) . '://' . parse_url($u[0], PHP_URL_HOST) . '/…') : $u[0], $ln) ?? $ln;
        $ln = trim(preg_replace('/\s+/u', ' ', $ln) ?? $ln);
        if ($ln === '' && (!$out || end($out) === '')) continue;
        $out[] = $ln;
    }
    $s = trim(implode("\n", $out));
    return mb_strlen($s) > $max ? rtrim(mb_substr($s, 0, $max - 3)) . '…' : $s;
}

// ================================================================ IMAP client (TLS socket, tagged commands, literals)
/** Connection / protocol ki gadbad (timeout, band, greeting): poora run rukta hai. Command ka NO / BAD = RuntimeException (sirf us mail ka). */
final class PMImapError extends RuntimeException {}

final class PMImap
{
    private $fp;
    private int $n = 0;
    private bool $pre = false;   // greeting PREAUTH: login ki zaroorat nahi

    public function __construct(string $host, int $port)
    {
        $ctx = stream_context_create(['ssl' => ['verify_peer' => true, 'verify_peer_name' => true, 'peer_name' => $host, 'SNI_enabled' => true]]);
        $warn = [];   // asli wajah (certificate / DNS) PHP warnings me aati hai: unhe pakdo
        set_error_handler(function (int $no, string $str) use (&$warn) { $warn[] = trim((string)preg_replace(['/^stream_socket_client\(\):\s*/', '/\s+/'], ['', ' '], $str)); return true; });
        try { $fp = stream_socket_client('ssl://' . $host . ':' . $port, $en, $es, PM_TIMEOUT, STREAM_CLIENT_CONNECT, $ctx); }
        finally { restore_error_handler(); }
        if (!$fp) {
            $cert = array_values(array_filter($warn, fn($w) => stripos($w, 'certificate') !== false || stripos($w, 'peer') !== false));
            $why = $cert[0] ?? ($es !== '' && $es !== 'Unknown error' ? $es : ($warn[0] ?? "error $en"));
            throw new PMImapError("IMAP connect $host:$port failed: " . h_cut($why, 220)
                . ($cert ? ' (SSL certificate is naam ka nahi: Plesk > Mail > "Secure mail with Let\'s Encrypt", ya certificate wala server hostname use karo)' : ''));
        }
        stream_set_timeout($fp, PM_TIMEOUT);
        $this->fp = $fp;
        [$g] = $this->unit();
        if (!preg_match('/^\* (OK|PREAUTH)\b/i', $g, $gm)) throw new PMImapError('IMAP bad greeting: ' . h_cut($g, 120));
        $this->pre = strtoupper($gm[1]) === 'PREAUTH';
    }

    public function close(): void { if (is_resource($this->fp)) @fclose($this->fp); }

    private function fail(string $what): PMImapError
    {
        $m = is_resource($this->fp) ? stream_get_meta_data($this->fp) : [];
        return new PMImapError(!empty($m['timed_out']) ? "IMAP timeout ($what, " . PM_TIMEOUT . 's)' : "IMAP connection closed ($what)");
    }

    private function write(string $s): void
    {
        for ($off = 0, $len = strlen($s); $off < $len; ) {
            $w = @fwrite($this->fp, substr($s, $off));
            if ($w === false || $w === 0) throw $this->fail('write');
            $off += $w;
        }
    }

    private function line(): string
    {
        $l = '';
        while (!str_ends_with($l, "\n")) {
            $c = fgets($this->fp, 8192);
            if ($c === false || $c === '') throw $this->fail('read');
            $l .= $c;
            if (strlen($l) > 1048576) throw new PMImapError('IMAP line too long');
        }
        return $l;
    }

    private function bytes(int $n): string
    {
        $s = '';
        while (strlen($s) < $n) {
            $c = fread($this->fp, min(8192, $n - strlen($s)));
            if ($c === false || $c === '') { if (feof($this->fp) || !empty(stream_get_meta_data($this->fp)['timed_out'])) throw $this->fail('literal'); continue; }
            $s .= $c;
        }
        return $s;
    }

    /** Ek poora response, literals {n} ke saath. [text, lits]: text me har literal ki jagah "\x00i\x00". */
    private function unit(): array
    {
        $text = ''; $lits = [];
        while (true) {
            $l = $this->line();
            if (preg_match('/\{(\d+)\}\r?\n$/', $l, $m)) {
                $n = (int)$m[1];
                if ($n > 4194304) throw new PMImapError('IMAP literal too big');
                $text .= substr($l, 0, -strlen($m[0]));
                $lits[] = $this->bytes($n);
                $text .= "\x00" . (count($lits) - 1) . "\x00";
                continue;   // literal ke baad line aage chalti hai
            }
            return [$text . rtrim($l, "\r\n"), $lits];
        }
    }

    /** Tagged command. "{L}" = literal argument (server ka "+" aane ke baad bheja jaata hai). Untagged responses wapas; NO / BAD = exception ($label me, password kabhi nahi). */
    public function cmd(#[\SensitiveParameter] string $c, #[\SensitiveParameter] array $lits = [], string $label = ''): array
    {
        $tag = 'M' . str_pad((string)(++$this->n), 3, '0', STR_PAD_LEFT);
        $label = $label !== '' ? $label : strtok($c, ' ');
        $segs = explode('{L}', $tag . ' ' . $c);
        $this->write($segs[0]);
        for ($i = 1, $k = count($segs); $i < $k; $i++) {
            $lit = (string)($lits[$i - 1] ?? '');
            $this->write('{' . strlen($lit) . "}\r\n");
            while (true) {   // continuation "+" ka intezaar
                [$t] = $this->unit();
                if (str_starts_with($t, '+')) break;
                if (str_starts_with($t, $tag . ' ')) throw new RuntimeException("IMAP $label failed: " . h_cut(substr($t, strlen($tag) + 1), 200));
            }
            $this->write($lit . $segs[$i]);
        }
        $this->write("\r\n");
        $out = [];
        while (true) {
            $u = $this->unit();
            if (str_starts_with($u[0], $tag . ' ')) {
                $rest = substr($u[0], strlen($tag) + 1);
                if (strncasecmp($rest, 'OK', 2) !== 0) throw new RuntimeException("IMAP $label failed: " . h_cut($rest, 200));
                return $out;
            }
            if (str_starts_with($u[0], '+')) continue;
            $out[] = $u;
        }
    }

    public function login(string $user, #[\SensitiveParameter] string $pass): void
    {
        if ($this->pre) return;
        // quoted string: sirf printable ASCII, " aur \ escape (RFC 3501); baaki (non-ASCII / control) literal {n} me
        $q = fn(string $s) => preg_match('/^[\x20-\x7e]*$/', $s) ? '"' . addcslashes($s, "\"\\") . '"' : null;
        $qu = $q($user); $qp = $q($pass);
        if ($qu !== null && $qp !== null) $this->cmd("LOGIN $qu $qp", [], 'LOGIN');
        else $this->cmd('LOGIN {L} {L}', [$user, $pass], 'LOGIN');   // non-ASCII: literals
    }

    /** @return int[] UNSEEN mails ke UID (purani pehle) */
    public function searchUnseen(): array
    {
        $ids = [];
        foreach ($this->cmd('UID SEARCH UNSEEN') as [$t]) {
            if (!preg_match('/^\* SEARCH\b(.*)$/i', $t, $m)) continue;
            foreach (preg_split('/\s+/', trim($m[1])) ?: [] as $x) if (ctype_digit($x)) $ids[] = (int)$x;
        }
        sort($ids);
        return array_values(array_unique($ids));
    }

    /** Header (From / Subject / Date / Message-ID + MIME) aur body ke pehle 20 KB, bina \Seen lagaye (PEEK). */
    public function fetch(int $uid): ?array
    {
        $rows = $this->cmd('UID FETCH ' . $uid . ' (BODY.PEEK[HEADER.FIELDS (FROM SUBJECT DATE MESSAGE-ID CONTENT-TYPE CONTENT-TRANSFER-ENCODING AUTHENTICATION-RESULTS)] BODY.PEEK[TEXT]<0.' . PM_TEXT_BYTES . '>)', [], 'FETCH');
        $hdr = null; $txt = null;
        foreach ($rows as [$t, $lits]) {
            if (!preg_match('/^\* \d+ FETCH \(/i', $t)) continue;
            if (preg_match('/\bUID (\d+)/i', $t, $mu) && (int)$mu[1] !== $uid) continue;
            $hdr = $hdr ?? self::item($t, $lits, '/BODY\[HEADER\.FIELDS[^\]]*\]/i');
            $txt = $txt ?? self::item($t, $lits, '/BODY\[TEXT\](?:<[\d.]+>)?/i');
        }
        if ($hdr === null && $txt === null) return null;
        return ['uid' => $uid, 'header' => (string)$hdr, 'text' => (string)$txt];
    }

    /** FETCH response me kisi item ki value: literal, quoted string ya NIL. */
    private static function item(string $t, array $lits, string $re): ?string
    {
        if (!preg_match($re, $t, $m, PREG_OFFSET_CAPTURE)) return null;
        $rest = ltrim(substr($t, $m[0][1] + strlen($m[0][0])), ' ');   // sirf space: literal marker \x00 bacha rahe
        if (preg_match('/^\x00(\d+)\x00/', $rest, $x)) return $lits[(int)$x[1]] ?? '';
        if (preg_match('/^"((?:[^"\\\\]|\\\\.)*)"/s', $rest, $x)) return preg_replace('/\\\\(["\\\\])/', '$1', $x[1]) ?? $x[1];   // IMAP quoted: sirf \" aur \\
        return '';
    }

    public function seen(int $uid): void { $this->cmd('UID STORE ' . $uid . ' +FLAGS.SILENT (\\Seen)', [], 'STORE'); }

    public function logout(): void { try { $this->cmd('LOGOUT'); } catch (Throwable $e) { } }
}
