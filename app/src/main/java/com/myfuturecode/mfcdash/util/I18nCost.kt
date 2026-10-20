package com.myfuturecode.mfcdash.util

/** AI Cost & Usage Manager ke strings: English → Hinglish. (HI, HI_HEALTH, HI_GADS ke baad dekha jaata hai.) */
val HI_COST: Map<String, String> = mapOf(
    // ---- module / tabs / home
    "AI Cost & Usage" to "AI Cost & Usage", "AI Cost" to "AI kharcha", "Overview" to "Overview", "Modules" to "Modules", "Apps" to "Apps", "Tasks" to "Tasks",
    "Budgets & limits" to "Budget aur limits", "This month" to "Is mahine", "Today" to "Aaj", "7 days" to "7 din",
    "Monthly AI cost per module, app and task · budgets and limits" to "Har module, app aur task ka mahine ka AI kharcha · budget aur limits",
    "This month %s of %s (%s) · forecast %s" to "Is mahine %s / %s (%s) · mahine ke ant tak %s", "%s module(s) paused" to "%s module ruke",
    "AI Cost & Usage is not on the server yet" to "AI Cost & Usage abhi server par nahi hai",
    "Upload api/cost.php and lib/cost.php to mfc_dash (see the setup guide), then tap Try again." to
        "api/cost.php aur lib/cost.php mfc_dash me upload karo (setup guide dekho), phir Dobara try karo dabao.",
    // ---- modules
    "App Analytics" to "App Analytics", "Play Console AI & Auto Fix" to "Play Console AI aur Auto Fix", "Google Ads AI Monitoring" to "Google Ads AI Monitoring",
    "Research & Development Studio" to "Research aur Development Studio",
    // ---- overview
    "Monthly budget" to "Mahine ka budget", "Used" to "Kharch hua", "Remaining" to "Bacha", "Forecast month-end" to "Mahine ke ant tak (andaza)",
    "Fixed bills (hosting etc.)" to "Fixed bill (hosting waghera)",
    "Warnings at 70%%, 90%% and 100%% (app + email). Critical reserve %s%% only for urgent monitoring." to
        "70%%, 90%% aur 100%% par warning (app + email). %s%% critical reserve sirf zaroori monitoring ke liye.",
    "Billed vs estimate" to "Asli bill vs andaza", "this month" to "is mahine", "BILLED" to "BILL", "ESTIMATE" to "ANDAZA", "synced" to "sync",
    "fixed monthly bill (you entered)" to "har mahine ka fixed bill (aapne daala)", "No paid AI usage logged this month yet." to "Is mahine abhi tak koi paid AI use nahi hua.",
    "Estimate = tokens × list price. Add an Anthropic Admin API key in Budgets & limits to also see the billed amount." to
        "Andaza = tokens × list price. Asli bill dekhna ho to Budget aur limits me Anthropic Admin API key daalo.",
    "By module" to "Module ke hisaab se", "spent / budget" to "kharch / budget", "PAUSED" to "RUKA", "Paused jobs" to "Ruke hue kaam", "last 3 days" to "pichhle 3 din",
    "%s× skipped" to "%s baar roka",
    "Critical monitoring (rules, scans, alerts) keeps running. Resume a module only with your OK in Modules." to
        "Zaroori monitoring (rules, scans, alerts) chalti rahti hai. Module dobara sirf aapke OK se chalega (Modules me).",
    "Claude API (server)" to "Claude API (server)", "Claude Code worker" to "Claude Code worker", "GitHub Actions" to "GitHub Actions", "Image generation" to "Image banana",
    "Video generation" to "Video banana", "Research data API" to "Research data API", "Firebase Test Lab" to "Firebase Test Lab", "GitHub (billed)" to "GitHub (bill)", "Other" to "Aur",
    // ---- tasks
    "Alert explanation" to "Alert ki AI samjhaish", "Daily AI brief" to "Roz ka AI brief", "Crash / ANR investigation" to "Crash / ANR jaanch", "AI fix" to "AI fix",
    "New task (Naya kaam)" to "Naya kaam", "Google Ads alert AI" to "Google Ads alert AI", "Google Ads daily review" to "Google Ads roz ka review", "Google Ads AI" to "Google Ads AI",
    "Most expensive first. Every row is an estimate from tokens × list price (Claude Code: its own cost report)." to
        "Sabse mehenga pehle. Har line andaza hai: tokens × list price (Claude Code: uski apni cost report).",
    "%s calls" to "%s calls", "%s tasks · %s calls" to "%s kaam · %s calls", "FAILED" to "FAIL", "No AI tasks in this period." to "Is samay me koi AI kaam nahi.",
    "No app-level AI cost in this period." to "Is samay me kisi app ka AI kharcha nahi.", "Not tied to one app (briefs, Ads reviews, research)" to "Kisi ek app ka nahi (brief, Ads review, research)",
    // ---- modules tab
    "AI calls today" to "Aaj AI calls", "AI fix / task runs per day (max)" to "Roz max AI fix / kaam runs", "Allow retry now" to "Abhi retry hone do", "Retry allowed" to "Retry chalu",
    "Resume for 24 hours (my OK)" to "24 ghante ke liye chalao (mera OK)", "Resumed for 24 hours" to "24 ghante ke liye chalu",
    "Raise the total budget in Budgets & limits to run more this month." to "Is mahine aur chalana hai to Budget aur limits me total budget badhao.",
    "OVER BUDGET · RESUMED" to "BUDGET KE BAAHAR · CHALU", "until %s" to "%s tak", "Stop" to "Roko", "Budget rules back on" to "Budget ke niyam wapas chalu",
    "Always on, free: rule engines, Play / Ads scans, crash and ANR detection, alerts. Only paid AI work pauses." to
        "Hamesha chalu, free: rules, Play / Ads scans, crash aur ANR pakadna, alerts. Sirf paid AI kaam rukta hai.",
    // ---- budgets
    "Total monthly budget" to "Mahine ka total budget", "Total" to "Total", "Critical reserve" to "Critical reserve",
    "= %s per month. Module budgets add up to %s." to "= %s har mahine. Modules ka jod %s hai.",
    "Reserve: after the total is used up, only urgent monitoring AI (urgent alert explanation, first look at a new P0/P1 crash) may use this extra share." to
        "Reserve: total khatam hone ke baad sirf zaroori monitoring AI (urgent alert samjhana, naye P0/P1 crash ki pehli jaanch) ye extra hissa use kar sakta hai.",
    "Budget / month" to "Budget / mahina", "AI calls / day" to "AI calls / din", "AI fix / task runs per day" to "AI fix / kaam runs / din",
    "= %s per month. At 100%%: %s" to "= %s har mahine. 100%% par: %s",
    "daily AI brief and non-urgent explanations pause; rules and alerts keep running." to "roz ka AI brief aur non-urgent samjhaish rukti hai; rules aur alerts chalte rahenge.",
    "AI fix, new tasks and manual Investigate pause; crash / ANR scans and alerts keep running." to "AI fix, naye kaam aur manual Investigate rukte hain; crash / ANR scan aur alerts chalte rahenge.",
    "6-hour deep AI and daily review pause; Ads rules and alerts keep running." to "6 ghante wala deep AI aur roz ka review rukta hai; Ads rules aur alerts chalte rahenge.",
    "research runs, image / video and new project builds pause; cached results stay available." to "research runs, image / video aur naye project builds rukte hain; cached results milte rahenge.",
    "Fixed monthly bills" to "Har mahine ke fixed bill", "Hosting, database or any other fixed bill. Counted as billed." to "Hosting, database ya koi aur fixed bill. Asli bill me gina jaata hai.",
    "Name" to "Naam", "Per month" to "Har mahine", "Add bill" to "Bill jodo",
    "GitHub Actions price per minute (0 = public repos, free)" to "GitHub Actions ka per minute rate (0 = public repo, free)",
    "Email the 70 / 90 / 100% warnings" to "70 / 90 / 100% warnings email par bhi", "Save budgets & limits" to "Budget aur limits save karo", "Budgets saved" to "Budget save ho gaya",
    "Cloud budget alerts (Google Cloud, GitHub) only send emails; they do not stop spending. This server checks the budget before every paid AI call. Nothing paid is switched on from here." to
        "Cloud budget alerts (Google Cloud, GitHub) sirf email bhejte hain, kharcha nahi rokte. Yeh server har paid AI call se pehle budget dekhta hai. Yahan se koi paid service on nahi hoti.",
    // ---- keys
    "Actual billed numbers (optional, read-only)" to "Asli bill ke numbers (optional, sirf padhna)",
    "Anthropic Admin API key (sk-ant-admin…, Claude Console > Settings > Admin keys; organisation accounts only). Reads the cost report only." to
        "Anthropic Admin API key (sk-ant-admin…, Claude Console > Settings > Admin keys; sirf organisation account). Sirf cost report padhti hai.",
    "Admin API key" to "Admin API key", "GitHub fine-grained token with \"Plan\" read access, for Actions billing." to "GitHub fine-grained token (\"Plan\" read access), Actions ke bill ke liye.",
    "GitHub username" to "GitHub username", "GitHub token" to "GitHub token", "Anthropic synced" to "Anthropic sync", "GitHub synced" to "GitHub sync",
    "Save keys" to "Keys save karo", "Sync now" to "Abhi sync karo", "Saved" to "Save ho gaya", "Synced" to "Sync ho gaya", "Remove keys" to "Keys hatao", "Keys removed" to "Keys hata di",
)
