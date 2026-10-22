<?php
/**
 * AI App Health ka config. Is file ko copy karke mfc_dash/health_config.php banao aur apni values bharo.
 * Yeh file secrets rakhti hai: GitHub par kabhi mat daalna. Service account JSON files mfc_dash/secrets/ me (wahan .htaccess URL rokta hai).
 */
return [
    // Har Play Console ek entry. sa = mfc_dash/secrets/ ke andar us console ke service account ki JSON file.
    // Service account ko us console me invite karo: "View app information" + "View app quality information (vitals)".
    // "Approve & Live" (Play par release) chahiye to "Release to production / testing tracks" bhi do.
    'consoles' => [
        ['id' => 'c1', 'name' => 'Console 1 (MyFutureCode)', 'sa' => 'play_c1.json'],
        ['id' => 'c2', 'name' => 'Console 2', 'sa' => 'play_c2.json'],
        // ['id' => 'c3', 'name' => 'Console 3', 'sa' => 'play_c3.json'],
        // ['id' => 'c4', 'name' => 'Console 4', 'sa' => 'play_c4.json'],
        // ['id' => 'c5', 'name' => 'Console 5', 'sa' => 'play_c5.json'],
    ],

    // (optional) app khud discover na ho to haath se console batao: package => console id
    'app_console' => [
        // 'com.myfuturecode.phonetrackerlivelocation' => 'c1',
    ],

    // Auto-fix / tasks: app ka GitHub repo (us repo me server/worker/mfc-ai-fix.yml copy karna hai)
    // upload_cert_sha256 (optional): Play Console > Setup > App signing > "Upload key certificate" ka SHA-256; diya ho to verify me match hota hai.
    'repos' => [
        // 'com.myfuturecode.calculator' => ['repo' => 'sagar9520/hide-calculator', 'branch' => 'main', 'app_dir' => 'app', 'upload_cert_sha256' => ''],
    ],

    // GitHub fine-grained token: sirf upar wale repos, permission "Contents: Read and write" (repository_dispatch ke liye)
    'github_token' => '',

    // Worker ke callbacks ka secret: koi bhi lamba random text (32+ characters). Badloge to chal rahe fix fail ho jaayenge.
    'hook_secret' => '',

    // Email
    'email_to'   => 'YOUR-EMAIL@example.com',
    'email_from' => 'alerts@YOUR-DOMAIN',
    // SMTP: host khali chhodo to server ka PHP mail() use hoga
    'smtp' => ['host' => '', 'port' => 465, 'secure' => 'ssl', 'user' => '', 'pass' => ''],

    // Email ke links aur worker callbacks ke liye dashboard ka public address (aakhri slash ke bina)
    'public_url' => 'https://tirangae-online.live/mfc_dash',

    // AAB / APK kahan rakhne hain (mfc_dash ke andar relative; folder me .htaccess apne aap ban jaata hai)
    'aab_dir' => 'secrets/aab',

    // Ek issue par AI fix ki max koshish (phir NEEDS_HUMAN_REVIEW)
    'max_fix_attempts' => 2,

    // (optional) Play Console ki emails -> app alerts (review reject, policy warning, "update live" ...). Google iska API nahi deta, isliye mail padhte hain.
    // 1) Plesk me sirf isi kaam ka naya mailbox banao (jaise playmail@YOUR-DOMAIN). Yeh har nayi mail ko "read" (\Seen) kar deta hai: is mailbox me apni normal mail mat rakhna.
    // 2) Har Gmail (jisme Play Console ki mails aati hain): Settings > Forwarding and POP/IMAP > "Add a forwarding address" = yeh mailbox.
    //    Google ki confirmation mail ka link app ke Alerts me aa jaayega (ya webmail me kholo). Phir Gmail filter: From "google.com" + "Google Play" -> Forward to is mailbox.
    // 3) host = mail server ka naam jiska SSL certificate sahi ho (Plesk > Mail > "Secure mail with Let's Encrypt"), port 993 (IMAP SSL). Password sirf yahin.
    // 4) forwarders (optional, par rakhna behtar): haath se forward ki gayi mails sirf in Gmail addresses se maani jaayengi. Khali = koi bhi (seedhi google.com wali mails hamesha).
    // 5) Nakli "google.com" mail se bachne ke liye incoming mail ka DKIM check on rakho (Plesk: Tools & Settings > Mail Server Settings > DKIM spam protection, incoming verify):
    //    server ka Authentication-Results header dkim=fail kahe to mail skip (Health logs me warn). Header hi na ho to check nahi ho paata.
    // host / user / pass khali = feature band. Health cron (har 5 min) mailbox check karta hai; app me status: health.php?action=playmail_status
    'play_mail' => ['host' => '', 'port' => 993, 'user' => '', 'pass' => '', 'forwarders' => []],
    // example: 'play_mail' => ['host' => 'mail.example.com', 'port' => 993, 'user' => 'playmail@example.com', 'pass' => '...', 'forwarders' => ['you@gmail.com']],
];
