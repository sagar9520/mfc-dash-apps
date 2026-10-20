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
];
