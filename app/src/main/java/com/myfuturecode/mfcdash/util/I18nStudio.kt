package com.myfuturecode.mfcdash.util

/** AI App Research Studio ke strings: English → Hinglish. (HI, HI_HEALTH, HI_GADS ke baad dekha jaata hai.) */
val HI_STUDIO: Map<String, String> = mapOf(
    // ---- module / home / tabs
    "AI App Research Studio" to "AI App Research Studio", "Studio" to "Studio", "Open in Studio" to "Studio me kholo",
    "%s Studio updates waiting for you" to "%s Studio updates aapka intezaar kar rahe hain",
    "App ideas, competitors, profit, approvals · never auto-publish" to "App ideas, competitors, profit, approvals · khud kabhi publish nahi",
    "Overview" to "Overview", "Research & Ranking" to "Research & Ranking", "Projects" to "Projects", "Approvals" to "Approvals", "History" to "History",
    "Runs" to "Runs", "Data sources" to "Data sources", "Settings" to "Settings",
    "Research Studio is not on the server yet" to "Research Studio abhi server par nahi hai",
    "Upload lib/studio.php and api/studio.php to mfc_dash and add the cron line (see the setup guide), then tap Try again." to
        "lib/studio.php aur api/studio.php mfc_dash me upload karo aur cron line jodo (setup guide dekho), phir Try again dabao.",
    "Studio never publishes an app by itself. Every gate needs your explicit approval; silence is never approval." to
        "Studio kabhi khud app publish nahi karta. Har gate par aapki saaf approval chahiye; chup rehna approval nahi hai.",
    // ---- overview
    "RESEARCH STUDIO" to "RESEARCH STUDIO", "Research, rank, approve. Never auto-publish." to "Research, ranking, approval. Khud publish kabhi nahi.",
    "New ideas" to "Naye ideas", "Watching" to "Nazar me", "Evidence" to "Saboot", "Waiting for you" to "Aapka intezaar", "Nothing waiting for your approval." to "Aapki approval ke liye kuch pending nahi.",
    "AI spend this month" to "Is mahine AI kharcha", "Spent" to "Kharcha", "Research stops (BLOCKED) at the limit. Change it in Settings." to "Limit par research ruk jaati hai (BLOCKED). Settings me badlo.",
    "Top ideas" to "Top ideas", "from the last ranking" to "pichhli ranking se", "See all ideas" to "Sab ideas dekho", "Schedule" to "Schedule",
    "cron not running yet" to "cron abhi nahi chal raha", "Daily discovery" to "Roz ki khoj", "Weekly ranking" to "Hafte ki ranking", "Monthly deep research" to "Mahine ki gehri research",
    "on" to "on", "off" to "off", "last" to "aakhri", "Add the server cron line from the setup guide, otherwise nothing runs." to "Setup guide se server cron line jodo, warna kuch nahi chalega.",
    "Recent runs" to "Haal ke runs", "Activity" to "Activity", "Open" to "Kholo",
    // ---- research
    "Engine" to "Engine", "Organic" to "Organic", "Paid" to "Paid", "Combined" to "Dono", "Tier" to "Tier", "Active" to "Active", "New" to "Naye", "Selected" to "Chune gaye", "Rejected" to "Reject", "Evaluating" to "Jaanch chal rahi",
    "Organic engine: ideas that can grow without paid ads" to "Organic engine: bina paid ads ke badh sakne wale ideas",
    "Paid engine: ideas that can be profitable with paid user acquisition" to "Paid engine: paid users laakar munafa de sakne wale ideas",
    "Combined: average of organic and paid scores" to "Dono: organic aur paid score ka average",
    "Ranking v%s · %s · %s" to "Ranking v%s · %s · %s", "No ranking run yet." to "Abhi koi ranking nahi chali.",
    "Score = weighted average over factors that have data only. Missing data is never counted as zero; it lowers confidence instead." to
        "Score = sirf jin factors ka data hai unka weighted average. Missing data ko zero nahi maanta; confidence kam hota hai.",
    "Run ranking now" to "Abhi ranking chalao", "Ranking queued. It starts within 5 minutes." to "Ranking queue me. 5 minute ke andar shuru hogi.",
    "No ideas match these filters." to "In filters me koi idea nahi.", "%s ideas selected" to "%s ideas chune",
    "Gate 1: approving creates a project per idea and starts competitor analysis (uses AI budget)." to "Gate 1: approve karne par har idea ka project banega aur competitor analysis shuru hoga (AI budget lagega).",
    "Approve (Gate 1)" to "Approve (Gate 1)", "Compare" to "Compare", "Clear" to "Hatao", "Select at most 5 ideas at once." to "Ek baar me zyada se zyada 5 ideas chuno.",
    "Approve Gate 1?" to "Gate 1 approve karein?",
    "%s ideas will become projects. Competitor research (top 3 per idea) and a feature spec will be prepared, then Gate 2 waits for you. Nothing is built or published." to
        "%s ideas projects banenge. Competitor research (har idea ke top 3) aur feature spec banega, phir Gate 2 aapka intezaar karega. Kuch build ya publish nahi hoga.",
    "Note (optional)" to "Note (zaroori nahi)", "Approve" to "Approve", "Approved. Competitor analysis queued." to "Approve ho gaya. Competitor analysis queue me.",
    "your idea" to "aapka idea", "conf." to "conf.", "Org" to "Org", "data" to "data",
    // ---- labels
    "VERIFIED" to "VERIFIED", "THIRD-PARTY ESTIMATE" to "THIRD-PARTY ESTIMATE", "AI INFERENCE" to "AI INFERENCE", "UNKNOWN" to "UNKNOWN",
    // ---- projects / approvals / history / runs
    "No projects yet. Approve ideas at Gate 1 (Research & Ranking) to start one." to "Abhi koi project nahi. Research & Ranking me Gate 1 par ideas approve karo.",
    "Gate" to "Gate", "Waiting for your decision" to "Aapke faisle ka intezaar", "Nothing waiting." to "Kuch pending nahi.", "Recent decisions" to "Haal ke faisle", "No decisions yet." to "Abhi koi faisla nahi.",
    "Search history" to "History me khojo", "Search" to "Khojo", "Decisions" to "Faisle", "Versions" to "Versions", "Actions" to "Actions", "Notifications" to "Notifications", "Logs" to "Logs",
    "Nothing found." to "Kuch nahi mila.", "Newer" to "Naye", "Older" to "Purane",
    "Start a run now" to "Abhi run chalao", "Runs are queued and the server cron picks them up within 5 minutes. AI runs use the monthly budget." to
        "Runs queue me jaate hain, server cron 5 minute me uthata hai. AI runs mahine ka budget use karte hain.",
    "Internal economics (no AI cost)" to "Apna economics (AI kharcha nahi)", "Discovery (AI + web)" to "Khoj (AI + web)", "Ranking (AI + web)" to "Ranking (AI + web)", "Deep research (AI + web)" to "Gehri research (AI + web)",
    "Queued" to "Queue me", "No runs yet." to "Abhi koi run nahi.",
    // ---- sources
    "Only legitimate, licensed sources. Each value keeps its label: VERIFIED, THIRD-PARTY ESTIMATE, AI INFERENCE or UNKNOWN." to
        "Sirf jaayaz, licensed sources. Har value ka label saath rehta hai: VERIFIED, THIRD-PARTY ESTIMATE, AI INFERENCE ya UNKNOWN.",
    "Provides" to "Kya deta hai", "Method" to "Tareeka", "Missing" to "Kya chahiye", "Last OK" to "Aakhri OK", "items" to "items", "Last error" to "Aakhri error",
    "Test" to "Test", "Docs" to "Docs", "Test queued. See Runs." to "Test queue me. Runs me dekho.", "paid" to "paid", "free" to "free",
    // ---- settings
    "Claude API key is not set: AI research cannot run. Add it in Dash Settings > AI." to "Claude API key set nahi: AI research nahi chalegi. Dash Settings > AI me daalo.",
    "Weekly ranking + economics" to "Hafte ki ranking + economics", "Push notifications" to "Push notifications", "Email notifications" to "Email notifications",
    "Daily run hour (server time, 0-23)" to "Roz ka run ka ghanta (server time, 0-23)", "Weekly run day (1 = Monday … 7 = Sunday)" to "Hafte ka din (1 = Somvar … 7 = Ravivar)",
    "Budget & limits" to "Budget & limits", "spent" to "kharcha", "Monthly AI budget (USD)" to "Mahine ka AI budget (USD)", "Web search price per 1,000 searches (USD)" to "1,000 web search ka daam (USD)",
    "Parallel projects limit" to "Ek saath kitne projects", "Minimum installs to judge economics" to "Economics ke liye kam se kam installs",
    "Profit assumptions" to "Profit ki maanyataayein", "These are YOUR assumptions, shown with every profit estimate." to "Yeh AAPKI maanyataayein hain, har profit estimate ke saath dikhti hain.",
    "LTV multiplier on D7 revenue [conservative, base, optimistic]" to "D7 revenue par LTV multiplier [conservative, base, optimistic]",
    "Development cost per app by complexity (USD)" to "Complexity ke hisaab se ek app ka development kharcha (USD)",
    "Countries & tiers" to "Desh & tiers", "Research runs country by country for the countries switched on. Tier groups markets by value." to "Research har chalu desh ke liye alag chalti hai. Tier markets ko value se group karta hai.",
    "Add country code (e.g. BR)" to "Desh code jodo (jaise BR)", "Add" to "Jodo", "Save countries" to "Desh save karo", "Categories" to "Categories", "Add category" to "Category jodo", "Save categories" to "Categories save karo",
    "Score weights" to "Score weights", "Higher weight = the factor counts more. Changes apply from the next ranking run." to "Zyada weight = factor zyada gina jaata hai. Badlav agli ranking se lagenge.",
    "higher is better" to "zyada = behtar", "higher is worse" to "zyada = bura", "default" to "default", "Save weights" to "Weights save karo", "Saved. Applies to the next ranking." to "Save ho gaya. Agli ranking se lagega.",
    "Your apps' categories" to "Aapke apps ki categories", "Used to compare a new idea with your own apps in the same category (installs, retention, revenue, CPI)." to
        "Naye idea ko aapke usi category ke apps se milane ke liye (installs, retention, revenue, CPI).", "Category" to "Category", "Save" to "Save", "Saved" to "Save ho gaya",
    // ---- own idea
    "Add your own idea" to "Apna idea jodo", "Studio evaluates it critically with the same rules and evidence labels. It may recommend REJECT." to
        "Studio isko bhi wahi rules aur labels se kadai se jaanchega. REJECT bhi keh sakta hai.",
    "Title" to "Naam", "What the app does" to "App kya karta hai", "Countries (e.g. IN, US)" to "Desh (jaise IN, US)", "Evaluate" to "Jaancho", "Idea queued for evaluation." to "Idea jaanch ke liye queue me.",
    // ---- idea detail
    "Data coverage" to "Data coverage", "confidence" to "confidence", "Recommendation" to "Salah", "AI said" to "AI ne kaha", "rules say" to "rules kehte", "the more cautious one is shown" to "jo zyada saavdhan hai woh dikhaya",
    "Build complexity" to "Banane ki mushkil", "Your note" to "Aapka note", "Open project" to "Project kholo", "Reject" to "Reject", "Watch" to "Nazar rakho", "More research" to "Aur research",
    "Restore idea" to "Idea wapas lao", "Research is running for this idea. Results appear here when the run finishes." to "Is idea par research chal rahi hai. Run khatam hone par natije yahan aayenge.",
    "Concept" to "Concept", "Why build it" to "Kyun banayein", "Why avoid it" to "Kyun na banayein", "Decision card" to "Decision card", "Score factors" to "Score factors", "weight · rating 0-10 · label" to "weight · rating 0-10 · label",
    "Competitors seen" to "Dikhe competitors", "Monetization" to "Kamaai", "Risks" to "Khatre", "No evidence stored." to "Koi saboot save nahi.", "Your decisions" to "Aapke faisle",
    "Approve (Gate 1)?" to "Approve (Gate 1)?", "Reject this idea?" to "Yeh idea reject karein?", "Watch this market?" to "Is market par nazar rakhein?", "Research more?" to "Aur research karein?", "Restore idea?" to "Idea wapas laayein?",
    "Creates a project, then competitor analysis + feature spec are prepared for Gate 2. Nothing is built or published." to "Project banega, phir Gate 2 ke liye competitor analysis + feature spec taiyaar hoga. Kuch build ya publish nahi hoga.",
    "The idea stays in history with your reason. You can restore it later." to "Idea aapki wajah ke saath history me rahega. Baad me wapas la sakte ho.",
    "Runs another AI + web research pass for this idea (uses AI budget)." to "Is idea par ek aur AI + web research chalegi (AI budget lagega).",
    "Reason (required)" to "Wajah (zaroori)", "Cancel" to "Cancel",
    "Profitability" to "Munafa", "portfolio average" to "portfolio average", "Not enough data to judge profit" to "Profit judge karne ke liye data kam hai",
    "No apps of yours in this category yet: numbers are your portfolio average, a weak proxy." to "Is category me aapka koi app nahi: yeh aapke portfolio ka average hai, kamzor andaza.",
    "Conservative" to "Saavdhan", "Base" to "Base", "Optimistic" to "Ummeed wala", "LTV / install" to "LTV / install", "Margin / install" to "Margin / install", "Installs to pay dev cost" to "Dev kharcha nikalne ko installs",
    "Paid users" to "Paid users", "installs" to "installs", "too few" to "bahut kam", "Assumptions" to "Maanyataayein", "dev cost" to "dev kharcha",
    "Label: VERIFIED internal data (your apps) + YOUR assumptions. Not a forecast." to "Label: VERIFIED apna data (aapke apps) + AAPKI maanyataayein. Yeh bhavishyavaani nahi.",
    "Compare ideas" to "Ideas compare karo", "Confidence" to "Confidence", "Complexity" to "Complexity", "Base margin / install" to "Base margin / install", "Verified factors" to "Verified factors", "Unknown factors" to "Unknown factors",
    // ---- project / gate 2
    "Project" to "Project", "since" to "se", "Open the original idea" to "Asli idea kholo", "Approval gates" to "Approval gates", "Coming in a later release" to "Agle release me aayega",
    "Publishing (Gate 8) always needs its own separate approval. Before any build you will be asked which Play Console account to use." to
        "Publish (Gate 8) ke liye hamesha alag approval chahiye. Build se pehle aapse poocha jayega kaunsa Play Console account use karna hai.",
    "Competitor analysis is being prepared" to "Competitor analysis taiyaar ho raha hai", "You get a notification when the feature spec is ready for Gate 2." to "Feature spec Gate 2 ke liye taiyaar hone par notification aayegi.",
    "Gate 2: feature scope" to "Gate 2: feature scope", "What changed in this version" to "Is version me kya badla", "Your decision on spec v%s" to "Spec v%s par aapka faisla",
    "Approval is tied to this exact version. If the spec changes, you approve again." to "Approval isi version se judi hai. Spec badla to dobara approve karna hoga.",
    "Request changes" to "Badlav maango", "Reject project" to "Project reject karo", "Strategy" to "Strategy", "Effort" to "Mehnat", "Top 3 competitors" to "Top 3 competitors", "Comparison" to "Tulna",
    "All do well" to "Sab achha karte hain", "Users need" to "Users ko chahiye", "Repeated complaints" to "Baar baar shikayatein", "Too complex" to "Bahut complex", "Missing in all" to "Sab me missing",
    "We can simplify" to "Hum aasaan kar sakte hain", "Technical traps to avoid" to "Technical jaal jinse bachna hai", "Our differentiators" to "Hamari khaasiyat",
    "Features" to "Features", "Do not build" to "Mat banao", "Permissions" to "Permissions", "Technical limits" to "Technical limits", "Monetization options" to "Kamaai ke tareeke",
    "Approvals" to "Approvals", "Cancel project" to "Project cancel karo", "Approve spec v%s?" to "Spec v%s approve karein?", "Reject project?" to "Project reject karein?", "Cancel project?" to "Project cancel karein?",
    "Gate 2 approved for this exact version. Design (Gate 3) is not in this release, so nothing more starts automatically." to
        "Isi version ke liye Gate 2 approve. Design (Gate 3) is release me nahi hai, isliye aage kuch apne aap shuru nahi hoga.",
    "Write what to change. A new version is prepared and comes back to you for approval." to "Likho kya badalna hai. Naya version banega aur approval ke liye aapke paas aayega.",
    "The project stops. Reason is saved in history." to "Project ruk jayega. Wajah history me save hogi.", "Reason / feedback (required)" to "Wajah / feedback (zaroori)", "Send" to "Bhejo",
    "Project cancelled" to "Project cancel ho gaya", "Tap for full analysis" to "Poora analysis dekhne ke liye tap karo", "Complaints" to "Shikayatein",
    "Complaints are what users or sources report. Nothing was reproduced or confirmed by testing." to "Shikayatein users ya sources ki batayi hui hain. Kuch bhi test karke confirm nahi kiya gaya.",
    "Open on Google Play" to "Google Play par kholo", "Not possible with public Android APIs: rejected" to "Public Android APIs se mumkin nahi: reject", "days" to "din",
    "Problem it solves" to "Kaunsi dikkat suljhata hai", "Why add" to "Kyun jodein", "Why not" to "Kyun nahi", "User benefit" to "User ka fayda", "Business benefit" to "Business ka fayda",
    // ---- run
    "Run" to "Run", "Steps" to "Steps", "AI calls" to "AI calls", "searches" to "searches", "Cost" to "Kharcha", "attempt %s" to "koshish %s", "Queued" to "Queue me", "Started" to "Shuru", "Finished" to "Khatam",
    "Summary" to "Summary", "Errors" to "Errors", "Cancel run" to "Run cancel karo", "Cancel requested" to "Cancel maanga gaya", "AI usage" to "AI usage", "Log" to "Log", "No log lines yet." to "Abhi koi log nahi.",
    "Internal economics" to "Apna economics", "Competitor analysis" to "Competitor analysis", "Revise feature spec" to "Feature spec sudhaar", "Evaluate idea" to "Idea jaanch", "Source test" to "Source test",
)
