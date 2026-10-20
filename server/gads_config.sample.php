<?php
/**
 * Google Ads AI Health Monitor: optional settings. Copy to mfc_dash/gads_config.php (git me mat daalo).
 * Sirf email intake (Google Ads ke notification emails padhna) ke liye chahiye. Iske bina baaki monitor chalta hai.
 *
 * Mailbox: wahi inbox jahan Google Ads ke emails aate hain (ya ek alag inbox jisme sab accounts ke emails forward hote hain).
 * Gmail: 2-Step Verification on karo, phir App password banao (myaccount.google.com > Security > App passwords) aur woh yahan daalo.
 * Monitor mailbox ko sirf padhta hai (IMAP EXAMINE): koi email delete / move / read-mark nahi hota.
 */
return [
    'imap' => [
        'host'   => 'imap.gmail.com',
        'port'   => 993,
        'user'   => 'ads-alerts@example.com',
        'pass'   => 'APP-PASSWORD-HERE',
        'folder' => 'INBOX',
    ],
];
