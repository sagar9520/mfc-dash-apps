<?php
/**
 * GitHub Actions worker (app repo ka .github/workflows/mfc-ai-fix.yml) yahan har kadam report karta hai.
 * Auth: har fix ka apna token = HMAC(hook_secret, "fix|<id>") — server ne dispatch ke waqt diya; repo/verify ke liye "verify|<pkg>".
 * Koi login cookie nahi; token ke bina kuch nahi milta.
 *
 *   GET  ?action=context&fix=ID&token=T        -> issue / task ka poora context (Claude Code prompt ke liye)
 *   POST ?action=step&fix=ID&token=T           JSON {step, status: running|ok|fail, detail, data:{...}, retryable}
 *   POST ?action=upload&fix=ID&token=T         multipart: aab, apk (optional), version_name, version_code, commit, notes
 *   POST ?action=verify&pkg=PKG&token=T        JSON {package_ok, signing_ok, cert_sha256, detail, repo}
 */
declare(strict_types=1);
require_once __DIR__ . '/../lib/health.php';

$pdo = db();
h_schema($pdo);
$action = (string)($_GET['action'] ?? '');
$token = (string)($_GET['token'] ?? ($_SERVER['HTTP_X_MFC_TOKEN'] ?? ''));

if ($action === 'verify') {
    $pkg = (string)($_GET['pkg'] ?? '');
    if ($pkg === '' || !hash_equals(h_sign('verify|' . $pkg), $token)) json_out(['ok' => false, 'error' => 'bad token'], 403);
    $in = json_decode((string)file_get_contents('php://input'), true) ?: [];
    $rc = h_repo_cfg($pkg);
    $cert = strtoupper(str_replace(':', '', (string)($in['cert_sha256'] ?? '')));
    $repoOk = !empty($in['package_ok']);
    $signOk = !empty($in['signing_ok']) && $cert !== '' && ($rc === null || $rc['upload_cert_sha256'] === '' || hash_equals($rc['upload_cert_sha256'], $cert));
    $note = $repoOk ? 'repo ✓ (applicationId matches)' : 'repo ✗ ' . h_cut((string)($in['detail'] ?? 'applicationId mismatch'), 200);
    $note .= ' · ' . ($signOk ? 'signing ✓ ' . substr($cert, 0, 12) . '…' : 'signing ✗ ' . ($cert !== '' && $rc && $rc['upload_cert_sha256'] !== '' && $rc['upload_cert_sha256'] !== $cert ? 'certificate does not match upload_cert_sha256' : h_cut((string)($in['detail'] ?? 'keystore secrets missing'), 200)));
    h_x($pdo, 'UPDATE health_apps SET repo_verified = ?, signing_verified = ?, verify_note = ?, verified_at = UTC_TIMESTAMP(), updated_at = UTC_TIMESTAMP() WHERE pkg = ?',
        [$repoOk ? 1 : 0, $signOk ? 1 : 0, h_cut($note, 500), $pkg]);
    h_log($pdo, "verify $pkg: $note", $repoOk && $signOk ? 'info' : 'warn', ['pkg' => $pkg]);
    h_notify($pdo, 1, 'health_verify', $pkg, [($repoOk && $signOk ? 'Auto-fix ready: ' : 'Verify failed: ') . h_app_name($pdo, $pkg), ($repoOk && $signOk ? 'Auto-fix ready: ' : 'Verify fail: ') . h_app_name($pdo, $pkg)], [$note, $note], 'verify|' . $pkg . '|' . time());
    json_out(['ok' => true, 'repo_verified' => $repoOk, 'signing_verified' => $signOk]);
}

$fixId = (int)($_GET['fix'] ?? 0);
if ($fixId <= 0 || !hash_equals(h_sign('fix|' . $fixId), $token)) json_out(['ok' => false, 'error' => 'bad token'], 403);
$f = $pdo->query('SELECT * FROM health_fixes WHERE id = ' . $fixId)->fetch();
if (!$f) json_out(['ok' => false, 'error' => 'fix not found'], 404);
$i = $pdo->query('SELECT * FROM health_issues WHERE id = ' . (int)$f['issue_id'])->fetch();

if ($action === 'context') {
    $prev = h_q($pdo, 'SELECT attempt, failure_reason, diff FROM health_fixes WHERE issue_id = ? AND id < ? ORDER BY id DESC LIMIT 2', [(int)$f['issue_id'], $fixId]);
    $maxVc = (int)h_q1($pdo, 'SELECT GREATEST(IFNULL(MAX(version_code), 0), 0) FROM health_builds WHERE pkg = ?', [$i['pkg']]);
    $seenVc = (int)h_q1($pdo, 'SELECT IFNULL(MAX(version_code), 0) FROM health_issues WHERE pkg = ?', [$i['pkg']]);
    $isTask = $i['type'] === 'TASK';
    $prompt = $isTask
        ? "Implement this change requested by the app owner in this Android app ({$i['pkg']}).\n\nRequest: {$i['title']}\n\nDetails:\n{$i['stack']}\n\n"
        : "Fix this production issue in this Android app ({$i['pkg']}).\n\nIssue ISS-{$i['id']} ({$i['type']}, {$i['severity']}): {$i['title']}\nLocation: {$i['location']}\nEvidence: {$i['source']}, {$i['users']} users, {$i['events']} events\n\nStack trace:\n" . h_cut((string)$i['stack'], 12000)
          . "\n\nAI root-cause analysis (from evidence only, may be wrong):\n{$i['ai_analysis']}\n\nSuggested fix:\n{$i['ai_fix']}\n\n";
    foreach ($prev as $p) $prompt .= "A previous attempt #{$p['attempt']} failed: {$p['failure_reason']}\nIts diff was:\n" . h_cut((string)$p['diff'], 6000) . "\n\n";
    $prompt .= "Rules:\n- Make the smallest safe change that fixes this. Do not refactor unrelated code.\n- Do NOT change applicationId, package name, signing config, keystore files, or versionCode/versionName (the pipeline bumps the version).\n"
        . "- Do not add new dependencies unless unavoidable.\n- Add or update a unit test that covers the fix when the code is testable.\n- Do not commit; the pipeline commits.\n"
        . "- When done, write a file named MFC_FIX_NOTES.md in the repo root with: a short summary, then one bullet per changed file explaining the change.";
    json_out(['ok' => true, 'issue_id' => (int)$i['id'], 'type' => $i['type'], 'pkg' => $i['pkg'], 'title' => $i['title'], 'prompt' => $prompt,
        'min_version_code' => max($maxVc, $seenVc) + 1, 'attempt' => (int)$f['attempt'], 'branch' => $f['branch']]);
}

if ($f['finished_at'] !== null) json_out(['ok' => false, 'error' => 'fix already finished'], 409);

if ($action === 'step') {
    $in = json_decode((string)file_get_contents('php://input'), true) ?: [];
    $step = preg_replace('/[^a-z_]/', '', (string)($in['step'] ?? ''));
    $status = in_array($in['status'] ?? '', ['running', 'ok', 'fail'], true) ? $in['status'] : 'running';
    $d = (array)($in['data'] ?? []);
    $detail = h_cut((string)($in['detail'] ?? ''), 2000);
    $set = ['updated_at = UTC_TIMESTAMP()']; $p = [];
    $add = function (string $col, $v) use (&$set, &$p) { $set[] = "$col = ?"; $p[] = $v; };
    switch ($step) {
        case 'started':
            if (!empty($d['gh_run_url'])) $add('gh_run_url', h_cut((string)$d['gh_run_url'], 300));
            if (!empty($d['base_sha'])) $add('base_sha', h_cut((string)$d['base_sha'], 64));
            break;
        case 'claude':
            $add('files_inspected', h_json(array_slice(array_values((array)($d['files_inspected'] ?? [])), 0, 200)));
            $add('files_modified', h_json(array_slice(array_values((array)($d['files_modified'] ?? [])), 0, 200)));
            $add('diff', h_cut((string)($d['diff'] ?? ''), 400000));
            $add('explanation', h_cut((string)($d['explanation'] ?? ''), 20000));
            if (!empty($d['commit_sha'])) $add('commit_sha', h_cut((string)$d['commit_sha'], 64));
            if ($status === 'ok') h_issue_status($pdo, (int)$i['id'], 'TESTING', "fix #{$f['attempt']}: code changed");
            break;
        case 'unit_tests':
            $add('unit_tests', h_cut(h_json(['passed' => $d['passed'] ?? null, 'failed' => $d['failed'] ?? null, 'total' => $d['total'] ?? null, 'log' => h_cut((string)($d['log'] ?? ''), 8000)]), 12000));
            if ($status !== 'running') $add('unit_ok', $status === 'ok' ? 1 : 0);
            break;
        case 'build':
            $add('build_log', h_cut((string)($d['log'] ?? $detail), 12000));
            if ($status !== 'running') $add('build_ok', $status === 'ok' ? 1 : 0);
            break;
        case 'device_test':
            $add('device_test', h_cut((string)($d['log'] ?? $detail), 12000));
            if ($status !== 'running') $add('device_ok', $status === 'ok' ? 1 : 0);
            if ($status === 'ok') h_issue_status($pdo, (int)$i['id'], 'AAB_BUILDING', 'all tests passed, building signed AAB');
            break;
    }
    $p[] = $fixId;
    h_x($pdo, 'UPDATE health_fixes SET ' . implode(', ', $set) . ' WHERE id = ?', $p);
    h_fix_step($pdo, $fixId, $step ?: 'step', $status, $detail);
    if ($status === 'fail' || $step === 'failed') {
        h_fix_finish($pdo, $fixId, false, $detail !== '' ? "$step: $detail" : "$step failed", !empty($in['retryable']));
    }
    json_out(['ok' => true]);
}

if ($action === 'upload') {
    // AAB sirf tab jab teeno validation records pass hon (worker ka kehna kaafi nahi)
    $f = $pdo->query('SELECT * FROM health_fixes WHERE id = ' . $fixId)->fetch();
    if ((int)$f['unit_ok'] !== 1 || (int)$f['build_ok'] !== 1 || (int)$f['device_ok'] !== 1)
        json_out(['ok' => false, 'error' => 'validation incomplete: unit=' . var_export($f['unit_ok'], true) . ' build=' . var_export($f['build_ok'], true) . ' device=' . var_export($f['device_ok'], true)], 409);
    $aab = $_FILES['aab'] ?? null;
    if (!$aab || ($aab['error'] ?? 1) !== UPLOAD_ERR_OK) json_out(['ok' => false, 'error' => 'aab upload failed (check upload_max_filesize / post_max_size)'], 400);
    $dir = h_aab_dir();
    $vn = preg_replace('/[^A-Za-z0-9._-]/', '', (string)($_POST['version_name'] ?? ''));
    $vc = (int)($_POST['version_code'] ?? 0);
    $base = preg_replace('/[^A-Za-z0-9._-]/', '_', $i['pkg']) . '-' . $vc . '-' . bin2hex(random_bytes(6));
    if (!move_uploaded_file($aab['tmp_name'], "$dir/$base.aab")) json_out(['ok' => false, 'error' => 'cannot store aab'], 500);
    $apkName = ''; $apkSize = 0; $apkSha = '';
    $apk = $_FILES['apk'] ?? null;
    if ($apk && ($apk['error'] ?? 1) === UPLOAD_ERR_OK && move_uploaded_file($apk['tmp_name'], "$dir/$base.apk")) {
        $apkName = "$base.apk"; $apkSize = (int)filesize("$dir/$base.apk"); $apkSha = (string)hash_file('sha256', "$dir/$base.apk");
    }
    $sha = (string)hash_file('sha256', "$dir/$base.aab");
    $claimed = strtolower((string)($_POST['sha256'] ?? ''));
    if ($claimed !== '' && !hash_equals($sha, $claimed)) { @unlink("$dir/$base.aab"); json_out(['ok' => false, 'error' => 'checksum mismatch'], 400); }
    $unit = json_decode((string)$f['unit_tests'], true) ?: [];
    $tests = ['unit' => ['passed' => $unit['passed'] ?? null, 'failed' => $unit['failed'] ?? null, 'total' => $unit['total'] ?? null], 'build' => true, 'device' => true];
    h_x($pdo, 'INSERT INTO health_builds (fix_id, issue_id, pkg, version_name, version_code, commit_sha, file, size, sha256, release_notes, tests, apk_file, apk_size, apk_sha256, status, created_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,\'READY_FOR_REVIEW\',UTC_TIMESTAMP())',
        [$fixId, (int)$i['id'], $i['pkg'], h_cut($vn, 40), $vc, h_cut((string)($_POST['commit'] ?? $f['commit_sha']), 64), "$base.aab", (int)filesize("$dir/$base.aab"), $sha,
            h_cut((string)($_POST['notes'] ?? ''), 4000), h_json($tests), $apkName, $apkSize, $apkSha]);
    $bid = (int)$pdo->lastInsertId();
    h_fix_step($pdo, $fixId, 'aab', 'ok', "$vn ($vc) · " . round(filesize("$dir/$base.aab") / 1048576, 1) . " MB · sha256 " . substr($sha, 0, 12) . '…' . ($apkName !== '' ? ' · test APK attached' : ''));
    h_fix_finish($pdo, $fixId, true, '', false);
    $name = h_app_name($pdo, $i['pkg']);
    h_notify($pdo, 2, 'health_aab', $i['pkg'], ["Tested build ready: $name $vn", "Tested build ready: $name $vn"],
        [($i['type'] === 'TASK' ? 'Your task: ' : 'Fix for ISS-' . $i['id'] . ': ') . h_cut($i['title'], 150) . ' · download the test APK, check it, then Approve & Live.',
         ($i['type'] === 'TASK' ? 'Aapka kaam: ' : 'ISS-' . $i['id'] . ' ka fix: ') . h_cut($i['title'], 150) . ' · test APK download karke check karo, phir Approve & Live.'], 'aab|' . $bid, ['build_id' => $bid, 'issue_id' => (int)$i['id']]);
    if (kv_get('h_mail_aab') !== '0') h_email($pdo, 'aab|' . $bid, "Tested AAB ready: $name $vn ($vc)", "Issue: ISS-{$i['id']} {$i['title']}\nCommit: " . ($_POST['commit'] ?? '') . "\nSHA-256: $sha\nTests: unit {$tests['unit']['passed']}/{$tests['unit']['total']} passed, release build OK, emulator test OK\n\nNot published. Approve it in the app to send it live.", 'aab/' . $bid);
    json_out(['ok' => true, 'build_id' => $bid, 'sha256' => $sha]);
}

json_out(['ok' => false, 'error' => 'unknown action'], 400);
