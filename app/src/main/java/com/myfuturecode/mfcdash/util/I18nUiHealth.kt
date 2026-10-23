package com.myfuturecode.mfcdash.util

/** English -> Hinglish (redesign: App Health). Missing key = English. */
val HI_UI_HEALTH: Map<String, String> = mapOf(
    // hero + tabs
    "Apps fine" to "Apps theek", "Look now" to "Turant dekho", "AI fixes" to "AI fix",
    // Aaj: Turant, AI fix taiyaar, Consoles, Scan, AI kaam
    "Nothing urgent" to "Kuch turant nahi", "All %s open" to "Sab %s khule", "AI fix ready" to "AI fix taiyaar",
    "%s need OK" to "%s OK chahiye", "View" to "Dekho", "fix" to "fix", "No service account" to "Service account nahi",
    "%s consoles" to "%s consoles", "%s/%s OK" to "%s/%s OK", "Server zone" to "Server zone", "AI work" to "AI kaam",
    "Monitored" to "Nazar me", "Scans/24h" to "Scan/24h",
    // khaali haal
    "No consoles" to "Koi console nahi", "No open issue" to "Koi khula issue nahi", "No AI fixes" to "Koi AI fix nahi",
    "No builds yet" to "Abhi koi build nahi", "No app here" to "Yahan koi app nahi",
    // bits: error, switches, builds, task
    "Not on server" to "Server par nahi", "Not verified" to "Verify nahi", "Repo & signing" to "Repo aur signing",
    "Always off" to "Hamesha band", "Issue" to "Issue", "Kept in history · not publishable" to "History me rahega · publish nahi hoga",
    // app + run screen
    "open" to "khule", "problems" to "problem",
    // issue screen
    "Investigate" to "Jaanch karo", "AI reading…" to "AI padh raha…", "Not investigated" to "Jaanch nahi hui",
    "Suggested fix" to "Sujhaya fix", "Missing evidence" to "Saboot nahi mila", "Cause" to "Wajah", "Location" to "Jagah",
    "Fold" to "Chhota karo", "Expand" to "Poora dekho", "Files modified" to "Badli files", "GitHub run" to "GitHub run",
    "events" to "events",
    // app screen: Google ka din + version-wise crash / ANR
    "All versions" to "Sab versions", "Version-wise" to "Version ke hisaab se", "Newest" to "Naya",
    // Export for Claude (Issues tab ka button + sheet, issue page ka Copy)
    "For Claude" to "Claude ke liye", "Export for Claude" to "Claude ke liye export", "Crashes for Claude" to "Claude ke liye crashes",
    "ANRs" to "ANR", "Open only" to "Sirf khule", "Top %s per app" to "Har app ke top %s", "All issues per app" to "Har app ke sab issues",
    "1 crash" to "1 crash", "%s crashes" to "%s crashes", "1 ANR" to "1 ANR", "%s ANRs" to "%s ANR", "app" to "app", "crash" to "crash", "ANR" to "ANR",
    "Share file" to "File share karo", "Copied" to "Copy ho gaya", "Copy failed" to "Copy nahi hua",
    "No crash or ANR in this period" to "Is samay me koi crash ya ANR nahi", "Pick crashes or ANRs" to "Crash ya ANR chuno",
    "Pick at least one app" to "Kam se kam ek app chuno", "Counting issues…" to "Issues gin rahe…",
    "Too big to copy (%s), so it is shared as a file" to "Copy ke liye bahut bada (%s), isliye file share ho rahi hai",
    "Could not copy, so it is shared as a file" to "Copy nahi hua, isliye file share ho rahi hai",
    "Could not share the file. Free some space on the phone and try again." to "File share nahi hui. Phone me thodi jagah khali karke dobara try karo.",
    "Upload the new lib/health.php and api/health.php to mfc_dash, then tap Try again." to
        "Naya lib/health.php aur api/health.php mfc_dash me upload karo, phir Dobara try karo dabao.",
)
