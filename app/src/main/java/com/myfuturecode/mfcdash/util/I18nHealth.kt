package com.myfuturecode.mfcdash.util

/** AI App Health module ke strings: English → Hinglish. (HI ke baad dekha jaata hai.) */
val HI_HEALTH: Map<String, String> = mapOf(
    // ---- counts / chhote labels
    "%s apps" to "%s apps", "%s attempts" to "%s koshish", "%s clean · %s failed" to "%s saaf · %s fail", "%s consoles · %s apps · %s monitored" to "%s consoles · %s apps · %s par nazar",
    "%s days" to "%s din", "%s errors" to "%s errors", "%s events" to "%s events", "%s files changed" to "%s files badli", "%s issues" to "%s issues", "%s lines" to "%s lines",
    "%s open health alerts · consoles, apps, crashes, fixes, AAB" to "%s health alerts khule · consoles, apps, crash, fix, AAB", "%s open" to "%s khule",
    "%s problems" to "%s problem", "%s users" to "%s users", "90 days" to "90 din", "All time" to "Shuru se", "AI" to "AI",
    "Attempt %s" to "Koshish %s", "attempt %s" to "koshish %s", "Last %s days" to "Pichhle %s din", "See all %s open issues" to "Sabhi %s khule issues dekho",
    "Live on Play: %s track" to "Play par live: %s track", "Times in your phone's time zone · server zone %s" to "Time aapke phone ke hisaab se · server zone %s",
    "Play vitals up to %s (Play data is 1–2 days late)" to "Play vitals %s tak (Play ka data 1–2 din late aata hai)",
    // ---- module / tabs
    "AI App Health" to "AI App Health", "AI App Health & Auto Fix" to "AI App Health & Auto Fix", "AI Health" to "AI Health",
    "Consoles, apps, crashes, AI fixes, AAB releases, new tasks" to "Consoles, apps, crash, AI fix, AAB release, naye kaam",
    "Overview" to "Overview", "Consoles & Apps" to "Consoles & Apps", "Live Activity" to "Live Activity", "Issues" to "Issues", "AI Fix History" to "AI Fix History",
    "AAB Releases" to "AAB Releases", "System Logs" to "System Logs",
    "AI App Health is not on the server yet" to "AI App Health abhi server par nahi hai",
    "Upload lib/health.php, api/health.php, api/health_hook.php and health_config.php to mfc_dash (see the setup guide), then tap Try again." to
        "lib/health.php, api/health.php, api/health_hook.php aur health_config.php mfc_dash me upload karo (setup guide dekho), phir Dobara try karo dabao.",
    // ---- overview
    "PORTFOLIO HEALTH" to "PORTFOLIO HEALTH", "Healthy" to "Theek", "Problem" to "Problem", "Open issues" to "Khule issues", "Consoles" to "Consoles", "connected" to "jude hue",
    "Investigating" to "Jaanch", "Fixing" to "Fix ho raha", "AI fix running" to "AI fix chal raha", "Needs you" to "Aapki zarurat", "human review" to "aap dekho",
    "Fixes ✓" to "Fix ✓", "Fixes ✗" to "Fix ✗", "verified · 30 days" to "pakka · 30 din", "failed · 30 days" to "fail · 30 din", "AAB ready" to "AAB taiyaar",
    "for your review" to "aapke check ke liye", "Scans 24h" to "Scan 24 ghante", "Running" to "Chal rahe", "Scan" to "Scan", "next" to "agla",
    "Last cycle" to "Pichhla cycle", "No scan has run yet" to "Abhi tak koi scan nahi chala", "Run Scan Now" to "Abhi scan karo", "Needs attention" to "Dhyan chahiye",
    "No open P0/P1 issue." to "Koi khula P0/P1 issue nahi.", "Integration health" to "Integrations ka haal", "Setup pending" to "Setup baaki hai",
    "Cron is not running: scheduled scans will not happen. Add the cron from the setup guide." to "Cron nahi chal raha: apne aap scan nahi honge. Setup guide se cron lagao.",
    "No Play Console service account yet: Play crashes/ANR/vitals cannot be read." to "Abhi Play Console ka service account nahi: Play ke crash/ANR/vitals nahi padh sakte.",
    // ---- consoles
    "Status" to "Status", "With open issues" to "Khule issues wale", "Monitoring off" to "Monitoring band", "Search app or package" to "App ya package dhoondo",
    "synced" to "sync", "service account missing" to "service account nahi", "Not linked to a console" to "Kisi console se nahi juda",
    "Add these to app_console in health_config.php (or give the console's service account access)." to "Inhe health_config.php ke app_console me daalo (ya console ke service account ko access do).",
    "No apps found in this console yet" to "Is console me abhi koi app nahi mila", "monitoring off" to "monitoring band", "scan" to "scan", "not scanned yet" to "abhi scan nahi hua",
    // ---- live
    "Run a scan" to "Scan chalao", "Full scan" to "Poora scan", "Critical crashes" to "Critical crash", "Play errors" to "Play errors", "Vitals" to "Vitals",
    "App discovery" to "Naye apps dhoondo", "AI investigation" to "AI jaanch", "AI fix" to "AI fix", "Repo verify" to "Repo verify", "Daily audit" to "Roz ka audit",
    "Queued" to "Line me", "Next scan" to "Agla scan", "Done 24h" to "Pure 24 ghante", "Failed 24h" to "Fail 24 ghante",
    "Scan status (did the check run?) is separate from issue status (did it find a problem?)." to "Scan status (check chala ya nahi) aur issue status (problem mili ya nahi) alag hain.",
    "Runs" to "Runs", "auto 5 sec" to "har 5 sec", "No runs yet" to "Abhi koi run nahi", "Activity feed" to "Activity feed", "Nothing yet" to "Abhi kuch nahi", "all apps" to "sab apps",
    "Scan started" to "Scan shuru ho gaya",
    // ---- issues
    "Console" to "Console", "Severity" to "Severity", "Period" to "Period", "Ad issue" to "Ad issue", "Task" to "Kaam", "Fixed" to "Fix hue", "Rejected" to "Reject kiye",
    "Resolved" to "Apne aap theek", "Search title, cause, ISS-id" to "Title, wajah, ISS-id dhoondo", "No open issue. 🎉" to "Koi khula issue nahi. 🎉",
    // ---- fixes
    "Result" to "Natija", "Verified" to "Pakka", "A fix counts as successful only after unit tests, release build and emulator test all pass." to
        "Fix tabhi safal maana jaata hai jab unit tests, release build aur emulator test teeno pass hon.", "No AI fix attempts yet" to "Abhi tak koi AI fix nahi",
    "Unit" to "Unit", "Build" to "Build", "Emulator" to "Emulator", "Release build" to "Release build",
    // ---- builds
    "Waiting for you" to "Aapka intezaar", "Published" to "Live ho gaye", "Discarded" to "Hataye", "Test APK" to "Test APK", "Release notes" to "Release notes",
    "Signed, tested builds. Test APK = install on your phone to check. AAB = what goes to Play." to "Signed, tested builds. Test APK = phone me daal kar check karo. AAB = jo Play par jaata hai.",
    "Play never gets anything automatically: only your Approve & Live tap sends it." to "Play par apne aap kuch nahi jaata: sirf aapka Approve & Live tap bhejta hai.",
    "No builds yet. They appear here after an AI fix or task passes all tests." to "Abhi koi build nahi. AI fix ya kaam ke saare tests pass hone par yahan aayega.",
    "Discard" to "Hatao", "Discard this build?" to "Yeh build hatana hai?", "It stays in history but cannot be published." to "History me rahega par live nahi ho sakega.",
    "Approve & Live" to "Approve & Live", "Nothing goes to Play until you tap Approve & Live and confirm." to "Jab tak aap Approve & Live dabakar confirm nahi karte, Play par kuch nahi jaata.",
    "Make it live on Play?" to "Play par live karna hai?", "Track" to "Track", "Internal test" to "Internal test", "Closed test" to "Closed test", "Open test" to "Open test",
    "Production" to "Production", "Rollout %" to "Rollout %", "Start small (e.g. 10%) and raise it in Play Console." to "Chhote se shuru karo (jaise 10%), phir Play Console me badhao.",
    "I tested this build and approve the release" to "Maine yeh build test kiya hai aur release approve karta hoon", "Sent to Play: " to "Play par bheja: ", "Saved" to "Save ho gaya",
    // ---- logs
    "Level" to "Level", "Warning" to "Warning", "Search logs" to "Logs dhoondo", "No log lines" to "Koi log nahi", "Log" to "Log",
    // ---- settings
    "Per app" to "Har app", "Auto-fix works only after the app's repo and signing are verified. Production auto-publish is always OFF." to
        "Auto-fix tabhi chalega jab app ka repo aur signing verify ho jaaye. Production auto-publish hamesha BAND hai.",
    "no repo linked" to "repo nahi juda", "24/7 monitoring" to "24/7 monitoring", "Automatic safe fix attempts" to "Apne aap safe fix ki koshish", "Email notifications" to "Email notifications",
    "Production auto-publish" to "Production auto-publish", "Always off: you approve every release" to "Hamesha band: har release aap approve karte ho",
    "waiting: repo/signing not verified" to "ruka hai: repo/signing verify nahi", "Verify" to "Verify", "Verification started (GitHub Actions)" to "Verification shuru (GitHub Actions)",
    "Email" to "Email", "not set" to "set nahi", "Set email_to (and SMTP) in health_config.php." to "health_config.php me email_to (aur SMTP) bharo.",
    "Send test email" to "Test email bhejo", "Test email sent" to "Test email chala gaya", "Scheduled monitoring" to "Time-table se monitoring", "cron OK" to "cron OK",
    "cron not running" to "cron nahi chal raha", "Integrations" to "Integrations", "off — add key in Settings" to "band — Settings me key daalo", "token not set" to "token nahi",
    "All email" to "Saari email", "Critical crash detected" to "Critical crash mila", "Important ad issue" to "Zaroori ad issue", "Fix attempt failed" to "Fix ki koshish fail",
    "Tested AAB available" to "Tested AAB taiyaar", "Play Console integration broken" to "Play Console connection toota", "Daily portfolio summary" to "Roz ka portfolio summary",
    "Critical health (tracker crashes)" to "Critical health (tracker crashes)", "Play crash / ANR issues" to "Play crash / ANR issues", "Ad network / ad unit checks" to "Ad network / ad unit check",
    "Play vitals (crash / ANR rate)" to "Play vitals (crash / ANR rate)", "Full portfolio audit" to "Poora portfolio audit", "Email summary" to "Email summary",
    "Max fix attempts per issue" to "Ek issue par max fix koshish", "15 min" to "15 min", "1 hour" to "1 ghanta", "daily" to "roz",
    "6 hours (Google data is 1-2 days late)" to "6 ghante (Google ka data 1-2 din late)", "on new P0/P1 crash / ANR" to "naye P0/P1 crash / ANR par",
    // ---- issue detail
    "no console" to "console nahi", "Users" to "Users", "Events" to "Events", "Version" to "Version", "First seen" to "Pehli baar", "Last seen" to "Aakhri baar", "Source" to "Source",
    "Actions" to "Actions", "AI investigating…" to "AI jaanch kar raha…", "Investigate with AI" to "AI se jaanch karao", "AI investigation started" to "AI jaanch shuru",
    "Auto-fix" to "Auto-fix", "AI fix started: tests + build run on GitHub" to "AI fix shuru: tests + build GitHub par chalenge", "Auto-fix not available: " to "Auto-fix abhi nahi: ",
    "Reject" to "Reject", "Mark fixed" to "Fix ho gaya", "Marked fixed" to "Fix mark kiya", "Reopen" to "Dobara kholo", "Reopened" to "Dobara khola", "Rejected: " to "Reject: ",
    "AI root cause" to "AI: asli wajah", "AI is reading the stack trace and code…" to "AI stack trace aur code padh raha hai…", "Not investigated yet." to "Abhi jaanch nahi hui.",
    "Confidence" to "Bharosa", "SUGGESTED FIX" to "SUJHAYA FIX", "MISSING EVIDENCE" to "JO SABOOT NAHI MILA", "AI says: safe to auto-fix" to "AI: auto-fix safe hai",
    "AI says: needs a human" to "AI: insaan ko dekhna hoga", "Affected devices" to "Kaunse phones", "Stack trace" to "Stack trace", "tap to fold" to "band karne ko tap", "tap to expand" to "kholne ko tap",
    "AI fix attempts" to "AI fix ki koshish", "Builds" to "Builds", "Timeline" to "Timeline", "Reject this issue?" to "Yeh issue reject karna hai?", "Reason (optional)" to "Wajah (optional)",
    "Files inspected" to "Files dekhi", "FILES MODIFIED" to "FILES BADLI", "Hide diff" to "Diff chhupao", "Show diff" to "Diff dikhao", "GitHub run ↗" to "GitHub run ↗", "Open issue" to "Issue kholo",
    // ---- app detail
    "Crash rate" to "Crash rate", "ANR rate" to "ANR rate", "Last scan" to "Aakhri scan", "Scan this app" to "Is app ka scan", "New task" to "Naya kaam", "DAU avg" to "DAU avg",
    "of installs" to "installs ka", "Versions in use" to "Chal rahe versions", "last 3 days" to "pichhle 3 din", "Recent scans" to "Haal ke scan", "No scans yet" to "Abhi scan nahi",
    "Automation" to "Automation", "Repo" to "Repo", "not linked" to "nahi juda",
    "Rejected = issues you rejected here. Play policy rejections are not available from Google's API; see Play Console inbox." to
        "Reject = jo issues aapne yahan reject kiye. Play policy rejection Google API se nahi milte; Play Console inbox dekho.",
    // ---- run
    "Run" to "Run", "Trigger" to "Trigger", "Queued" to "Line me", "Started" to "Shuru", "Finished" to "Khatam", "Checks" to "Checks", "Problems found" to "Problem mili", "Errors" to "Errors",
    // ---- task
    "New task for AI" to "AI ke liye naya kaam", "Write what to change. AI makes the change, runs tests and builds a test APK. You check it, then tap Approve & Live." to
        "Likho kya badalna hai. AI badlav karega, tests chalayega aur test APK banayega. Aap check karo, phir Approve & Live dabao.",
    "Not linked" to "Juda nahi", "No app in this console yet" to "Is console me abhi koi app nahi", "repo ✓ signing ✓" to "repo ✓ signing ✓",
    "repo/signing not verified: task will wait for you" to "repo/signing verify nahi: kaam aapka intezaar karega", "What to do (short)" to "Kya karna hai (chhota)",
    "Details (screens, text, behaviour)" to "Detail (screen, text, kaam kaise kare)", "Send to AI" to "AI ko bhejo", "Task saved: AI is working on it" to "Kaam save: AI us par kaam kar raha hai",
    // ---- status labels (statusText)
    "Healthy" to "Theek", "Watch" to "Dhyan do", "Error" to "Error", "Not synced" to "Sync nahi", "Not set" to "Set nahi", "Ok" to "OK", "Open" to "Khula",
    "Investigating" to "Jaanch", "Fix in progress" to "Fix chal raha", "Testing" to "Testing", "Aab building" to "AAB ban raha", "Ready for review" to "Check ke liye taiyaar",
    "Needs human review" to "Aap dekho", "Failed" to "Fail", "Completed no issues" to "Pura, koi issue nahi", "Issues detected" to "Issues mile", "Success" to "Safal",
    "Approved" to "Approved", "Uploaded by user" to "Aapne upload kiya", "Partial" to "Adhoora", "Unknown" to "Pata nahi",
)
