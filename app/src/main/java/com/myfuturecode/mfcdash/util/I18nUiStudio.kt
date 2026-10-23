package com.myfuturecode.mfcdash.util

/** English -> Hinglish (redesign: Research Studio). Missing key = English. */
val HI_UI_STUDIO: Map<String, String> = mapOf(
    // overview: hero, OK chahiye, budget, aur
    "Waiting for your OK" to "Aapke OK ka wait", "Give OK" to "OK do", "See all" to "Sab dekho", "Select" to "Chuno",
    "for %s days" to "%s din se", "for 1 day" to "1 din se", "New idea" to "naya",
    "%s ideas" to "%s ideas", "%s new" to "%s naye", "%s waiting" to "%s wait me",
    "%s active" to "%s chalu", "%s published" to "%s publish", "%s evidence" to "%s saboot", "%s error" to "%s error",
    "Nothing waiting" to "Kuch pending nahi", "Budget" to "Budget", "Budget full" to "Budget poora", "No budget" to "Budget nahi",
    "Cron" to "Cron", "Cron off" to "Cron band", "Cron late" to "Cron der se", "No AI key" to "AI key nahi", "Never auto-publish" to "Khud publish kabhi nahi",
    // research + ranking
    "Run now" to "Abhi chalao", "Ranking v%s" to "Ranking v%s", "No ranking yet" to "Abhi ranking nahi", "Max 5 ideas" to "Max 5 ideas",
    "No ideas" to "Koi idea nahi", "No ideas yet" to "Abhi koi idea nahi", "Nothing found" to "Kuch nahi mila", "Both" to "Dono",
    "Strongly consider" to "Zaroor socho", "Test first" to "Pehle test karo", "Watch market" to "Market dekho", "Keep watching" to "Nazar rakho",
    "Restored" to "Wapas laaya", "Changes requested" to "Badlav maange", "score" to "score", "Score" to "Score",
    "as of" to "tak", "collected" to "jama kiya", "decision" to "faisla", "error" to "error",
    // projects, approvals, runs, settings
    "No projects yet" to "Abhi koi project nahi", "No decisions yet" to "Abhi koi faisla nahi", "Start" to "Shuru karo",
    "Country code" to "Country code", "Gates approved" to "Gates approved", "Original idea" to "Asli idea", "No log yet" to "Abhi log nahi",
    "Research running" to "Research chal rahi", "steps" to "steps", "Attempts" to "Koshish", "Searches" to "Search",
    "Call" to "Call", "In" to "In", "Out" to "Out", "Updates" to "Updates", "What changed" to "Kya badla",
    "No AI cost" to "AI kharcha nahi", "AI + web" to "AI + web", "Daily hour" to "Roz ka ghanta", "Weekly day" to "Hafte ka din",
    "AI budget / month" to "AI budget / mahina", "Search $ / 1K" to "Search $ / 1K", "Max projects" to "Max projects",
    "Min installs" to "Kam se kam installs", "LTV × D7 revenue" to "LTV × D7 kamai", "Dev cost $" to "Dev kharcha $",
    // decision card
    "What" to "Kya", "Why" to "Kyun", "Benefit" to "Fayda", "Risk" to "Risk", "Alternatives" to "Vikalp",
    // opportunity detail
    "Rules say" to "Rules kehte", "Your data + your assumptions" to "Aapka data + aapke andaze", "weight %s" to "weight %s",
    "Weak points" to "Kamzoriyan", "Weak proxy" to "Kamzor andaza", "Reported, not tested" to "Bataya gaya, test nahi",
    "No evidence" to "Saboot nahi", "Competitor evidence" to "Competitor saboot",
    // spec: features + competitors
    "Core features" to "Mukhya features", "Secondary features" to "Dusre features", "Later release" to "Baad ki release",
    "Not possible on Android" to "Android par mumkin nahi", "Must have" to "Zaroori", "Should have" to "Hona chahiye", "Optional" to "Optional",
    "Implementation" to "Kaise banega", "Android APIs" to "Android APIs", "Privacy" to "Privacy", "Battery / performance" to "Battery / speed",
    "Navigation / UI" to "Navigation / UI", "Monetization impact" to "Kamai par asar", "Premium" to "Premium", "Ads experience" to "Ads ka anubhav",
    "User satisfaction" to "User khushi", "Risks / alternatives" to "Risk / vikalp",
    // daily discovery (overview card + tab + run detail)
    "Latest discovery" to "Nayi khoj", "Today's scan" to "Aaj ki khoj", "Yesterday's scan" to "Kal ki khoj", "%s scan" to "%s ki khoj",
    "Notes only" to "Sirf notes", "Used in ranking" to "Ranking me liya", "For next ranking" to "Agli ranking ke liye",
    "finding" to "khoj", "findings" to "khoj", "1 finding" to "1 khoj", "%s findings" to "%s khoj",
    "1 source" to "1 source", "%s sources" to "%s sources", "Sources %s" to "Sources %s", "Findings" to "Khoj", "No findings" to "Koi khoj nahi",
    "No sources" to "Koi source nahi", "New apps" to "Naye apps", "New app" to "Naya app", "Rivals" to "Rivals", "Competitor" to "Competitor",
    "Source says" to "Source kehta", "AI guess" to "AI andaza", "Yesterday %s" to "Kal %s", "Pick a day" to "Din chuno", "See findings" to "Khoj dekho",
    "No discovery yet" to "Abhi koi khoj nahi", "Daily discovery runs every day when it is on in Settings." to "Settings me chalu ho to roz ki khoj har din chalti hai.",
    // live runs (chalta job ka card, Running now, run detail)
    "1 job running" to "1 kaam chal raha", "%s jobs running" to "%s kaam chal rahe", "Ranking running" to "Ranking chal rahi",
    "Step %s of %s" to "Step %s / %s", "At step %s of %s" to "Step %s / %s par", "%s ready" to "%s taiyaar", "%s stopped" to "%s ruk gaya",
    "%s partly done" to "%s adhoora", "Stopping…" to "Ruk raha hai…", "No update for %s min" to "%s min se koi update nahi",
    "Still working. If it is stuck, the server restarts it by itself." to "Abhi bhi chal raha. Atka ho to server khud dobara chalayega.",
    "Still working. If it is stuck, the server stops it by itself." to "Abhi bhi chal raha. Atka ho to server khud rok dega.",
    "Server cron is not running" to "Server ka cron nahi chal raha", "You cancelled it" to "Aapne rok diya",
    "%s new ideas" to "%s naye ideas", "1 new idea" to "1 naya idea",
    "Starts after the %s" to "%s ke baad shuru hoga", "Starts within 5 minutes" to "5 minute me shuru hoga",
    "List updated %s" to "List update hui: %s", "Updated %s" to "Update hua: %s", "Pick 3-5 ideas" to "3-5 ideas chuno",
    "Run again" to "Phir chalao", "Research is saved. Run again within 24 h: no new web search cost." to "Research save hai. 24 ghante me dobara chalao: web search ka naya kharcha nahi.",
    "Updates every few seconds. You can close the app; you get a notification when it ends." to "Har kuch second me update hota hai. App band kar sakte ho; khatam hone par notification aayega.",
    "Updates every few seconds." to "Har kuch second me update hota hai.",
    "Ideas appear here" to "Ideas yahan aayenge", "When the ranking ends, the list fills by itself. No need to refresh." to "Ranking khatam hote hi list apne aap bhar jayegi. Refresh ki zaroorat nahi.",
    "Tap Run again. Ideas appear here when it ends." to "Phir chalao dabao. Khatam hone par ideas yahan aayenge.",
    "%s sec" to "%s sec", "%s h %s min" to "%s ghante %s min",
    "Ranking" to "Ranking", "Deep research" to "Gehri research", "Discovery" to "Khoj", "Apps analysis" to "Apps ka analysis", "Economics" to "Kamai ka hisaab",
    "Spec update" to "Spec badlav", "Idea check" to "Idea jaanch",
)
