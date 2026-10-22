package com.myfuturecode.mfcdash.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App language. Source strings are English; `"text".t()` returns the Hinglish version when Lang.code == "hi".
 * Lang.code is Compose state, so every screen re-renders on change. Default: English.
 */
object Lang {
    var code by mutableStateOf("en")
    val hi get() = code == "hi"
}

val LANGS = listOf("en" to "English", "hi" to "Hinglish")

fun String.t(): String = if (Lang.hi) (HI[this] ?: HI_HEALTH[this] ?: HI_GADS[this] ?: HI_STUDIO[this] ?: HI_COST[this]
    ?: HI_UI_KIT[this] ?: HI_UI_HOME[this] ?: HI_UI_MONEY[this] ?: HI_UI_SETTINGS[this] ?: HI_UI_HEALTH[this]
    ?: HI_UI_GADS[this] ?: HI_UI_STUDIO[this] ?: HI_UI_WIDGET[this] ?: HI_UI_SORT[this] ?: this) else this
fun tf(s: String, vararg a: Any?): String = try { s.t().format(*a) } catch (e: Exception) { s.format(*a) }

/** English → Hinglish. Missing key = English shown. */
val HI: Map<String, String> = mapOf(
    // ---- common / periods
    "Filter" to "Filter", "Filters" to "Filters", "Reset" to "Reset", "Done" to "Ho gaya", "Clear" to "Hatao",
    "Today" to "Aaj", "Yesterday" to "Kal", "7 days" to "7 din", "30 days" to "30 din", "Custom" to "Custom", "Apply" to "Lagao", "Cancel" to "Cancel",
    "Pick a period" to "Period chuno", "All" to "Sab", "All apps" to "Sab apps", "Home" to "Home", "Money" to "Paisa", "Users" to "Users", "Ads" to "Ads", "Live" to "Live",
    "No data received" to "Data nahi aaya", "Try again" to "Dobara try karo", "No data" to "Data nahi", "No data for the chart" to "Graph ke liye data nahi",
    "Loading…" to "Load ho raha hai…", "Updated" to "Updated", "data %s min old" to "data %s min purana", "just now" to "abhi", "%s sec ago" to "%s sec pehle",
    "%s min ago" to "%s min pehle", "%s hours ago" to "%s ghante pehle", "Sort" to "Sort", "Show" to "Dikhao", "Rows" to "Rows", "Top 10" to "Top 10", "Kind" to "Kism",
    "Settings" to "Settings", "Alerts" to "Alerts", "Language" to "Bhasha", "More" to "Aur", "on" to "chalu", "off" to "band", "now" to "abhi", "Open" to "Khule",
    // ---- home
    "Active now" to "Abhi active", "live, all apps" to "live, sab apps", "DAU today" to "Aaj DAU", "Earned" to "Kamaya", "Spent" to "Kharcha", "Profit" to "Profit",
    "PROFIT" to "PROFIT", "ad revenue + payments − Google Ads spend" to "ad revenue + payments − Google Ads kharcha", "break-even" to "break-even", "target" to "target",
    "Ad revenue" to "Ad revenue", "Payments" to "Payments", "Ad spend" to "Ad kharcha", "Ad spend ₹" to "Ad kharcha ₹", "Installs" to "Installs", "first open" to "first open",
    "Crashes" to "Crashes", "Crash" to "Crash", "crashes" to "crashes", "all good" to "sab theek", "Uninstalls" to "Uninstalls", "Uninst" to "Uninst", "FCM check" to "FCM check",
    "Promo installs" to "Promo installs", "from cross promo" to "cross promo se", "Apps" to "Apps", "by profit" to "profit ke hisaab se", "In profit" to "Profit me",
    "In loss" to "Loss me", "ads" to "ads", "With ad spend" to "Ad kharch wale", "Organic only" to "Sirf organic", "With spend" to "Kharch wale", "No app matches this filter" to "Is filter me koi app nahi",
    "tap for full data" to "poora data: tap karo", "Best / worst" to "Best / worst", "BEST APP" to "BEST APP", "WORST APP" to "WORST APP", "CHEAPEST CAMPAIGN" to "SABSE SASTI CAMPAIGN",
    "COSTLIEST CAMPAIGN" to "SABSE MEHNGI CAMPAIGN", "Daily: earned vs spent" to "Roz ka: kamaya vs kharcha", "Earned ₹" to "Kamaya ₹", "Daily ROAS" to "Roz ka ROAS",
    "green = on target" to "hara = target par", "Daily summary" to "Roz ka hisaab", "from cron" to "cron se", "Daily summary appears here once the cron has run" to "Cron chalne ke baad roz ka hisaab yahan aayega",
    "Date" to "Date", "Revenue" to "Revenue", "Spend" to "Kharcha", "Summary %s min old" to "Hisaab %s min purana", "summary %s min old (cron?)" to "hisaab %s min purana (cron?)",
    "is cron/run.php running?" to "cron/run.php chal raha hai?", "% vs yesterday" to "% kal se", "Watch" to "Dhyan do", "Urgent" to "Urgent", "URGENT" to "URGENT", "Info" to "Info",
    "All good: no urgent or watch alerts" to "Sab theek: koi urgent ya dhyan-do alert nahi", "Data, ads, Google Ads, crashes, revenue — all normal" to "Data, ads, Google Ads, crash, revenue — sab normal",
    "No alerts: no crashes, loss campaigns or low fill found." to "Koi alert nahi: crash, loss campaign ya kam fill nahi mila.", "%s open · tap for details" to "%s khule · detail ke liye tap",
    "See all %s alerts" to "Sabhi %s alerts dekho", "%s (%s) fill only %s" to "%s (%s) fill sirf %s", "%s below target: ROAS %s" to "%s target se neeche: ROAS %s",
    "%s in loss: ROAS %s" to "%s loss me: ROAS %s", "%s: spend but 0 conv" to "%s: kharcha hai par 0 conv", "%s items" to "%s cheezein", "%s of %s" to "%s / %s",
    "server check" to "server check", "No Google Ads data coming in" to "Google Ads ka data nahi aa raha", "check the account script" to "account ki script check karo", "no script data" to "script data nahi",
    "Per-install economics" to "Per-install hisaab", "Earned / install" to "Kamai / install", "Profit / install" to "Profit / install", "Cost / conv" to "Cost / conv", "0 conv" to "0 conv",
    "Payback" to "Payback", "days" to "din", "Payback = cost/conv ÷ ARPDAU: days until a paid user earns back what they cost." to "Payback = cost/conv ÷ ARPDAU: paid user apna kharcha kitne din me wapas kamata hai.",
    "Organic (no utm) installs" to "Organic (bina utm) installs", "all installs" to "sab installs", "from Google Ads installs" to "Google Ads installs se", "Ads users rev" to "Ads users ki kamai",
    "Campaign-wise ROAS cohort" to "Campaign-wise ROAS cohort", "ROAS cohort: by campaign" to "ROAS cohort: campaign-wise", "Change log" to "Change log", "budget / bid / creative changes" to "budget / bid / creative ke badlav",
    "last %s days" to "pichhle %s din", "spend %s, earned %s" to "kharcha %s, kamaya %s", "update" to "update", "updated" to "updated",
    // ---- money
    "Google Ads accounts" to "Google Ads accounts", "Account" to "Account", "Campaigns" to "Campaigns", "campaigns" to "campaigns", "Campaign" to "Campaign", "Status" to "Status",
    "Enabled" to "Chalu", "Paused" to "Paused", "Cost" to "Cost", "Conv" to "Conv", "Cost/conv" to "Cost/conv", "No campaigns match" to "Koi campaign match nahi", "Google Ads campaigns" to "Google Ads campaigns",
    "spend" to "kharcha", "spent" to "kharcha", "Pay" to "Pay", "pay" to "pay", "Paid" to "Paid", "paid" to "paid", "Failed" to "Fail", "failed" to "fail", "Started" to "Shuru", "App · plan" to "App · plan",
    "Count" to "Count", "Amount" to "Rakam", "No payments" to "Koi payment nahi", "Plan" to "Plan",
    // ---- users
    "active now" to "abhi active", "active" to "active", "1 hour" to "1 ghanta", "24 hours" to "24 ghante", "needs app.php" to "app.php chahiye", "Installed" to "Installed",
    "%s uninstalled (total)" to "%s uninstalled (total)", "active users" to "active users", "Sessions" to "Sessions", "sessions" to "sessions", "Avg session" to "Avg session",
    "%s of installs" to "installs ka %s", "Impr / DAU" to "Impr / DAU", "Retention" to "Retention", "coming back after install" to "install ke baad wapas aana",
    "Retention by day" to "Retention din-wise", "cohort = install day" to "cohort = install ka din", "Daily" to "Roz ka", "Hourly" to "Ghante-wise",
    "installs (line), revenue ₹ (bars)" to "installs (line), revenue ₹ (bars)", "Revenue ₹" to "Revenue ₹", "Install source" to "Install source", "Source" to "Source", "Sources" to "Sources",
    "No installs yet" to "Abhi installs nahi", "Users (ads)" to "Users (ads)", "installs" to "installs", "Countries" to "Desh", "Country" to "Desh", "Impr" to "Impr", "Impressions" to "Impressions",
    "App versions" to "App versions", "users" to "users", "App" to "App", "Version" to "Version", "Versions" to "Versions", "Live countries" to "Live desh", "right now" to "abhi",
    "Nobody live right now" to "Abhi koi live nahi", "Cohort" to "Cohort", "Organic" to "Organic", "Install day" to "Install din", "No installs in this period" to "Is period me installs nahi",
    "Dn = % who came back on day n after install (only cohorts whose day n has passed). Colour vs common utility-app benchmarks D1 35% / D7 12%." to
        "Dn = install ke n-ve din kitne % wapas aaye (sirf un cohorts par jinka n-vaan din aa chuka). Rang: utility apps ke aam benchmark D1 35% / D7 12% ke mukable.",
    // ---- ads
    "Via" to "Via", "Format" to "Format", "Fill" to "Fill", "loaded ÷ requests" to "loaded ÷ requests", "Requests" to "Requests", "clicks" to "clicks", "Clicks" to "Clicks", "Share" to "Hissa",
    "of all ad revenue" to "poori ad revenue ka", "Revenue by network" to "Network-wise revenue", "impressions · eCPM · load time" to "impressions · eCPM · load time", "Network" to "Network",
    "Load ms" to "Load ms", "No impressions yet" to "Abhi impressions nahi", "No impressions" to "Impressions nahi", "Revenue by format" to "Format-wise revenue", "fill = loaded ÷ requests" to "fill = loaded ÷ requests",
    "No ad data" to "Ad data nahi", "Ad units" to "Ad units", "Ad unit" to "Ad unit", "Ad failure reasons" to "Ad fail hone ki wajah", "Reason" to "Wajah", "No ad failures" to "Koi ad failure nahi", "No failures" to "Koi failure nahi",
    "Cross promo" to "Cross promo", "mockup ads of your own apps" to "apne apps ke mockup ads", "No cross promo shown in this period. Tap to open the report." to "Is period me cross promo nahi dikha. Report ke liye tap karo.",
    "Views" to "Views", "Full report: which app promotes which, by ad type and design →" to "Poori report: kis app me kis app ka promo, ad type aur design-wise →",
    "Promo video" to "Promo video", "App · promo" to "App · promo", "Shows" to "Shows", "In-app ad creatives" to "In-app ad creatives", "Creative" to "Creative", "Creatives" to "Creatives",
    "No creative IDs" to "Creative ID nahi", "Actual image/video: AppLovin Ad Review and AdMob Ad Review Center." to "Asli image/video: AppLovin Ad Review aur AdMob Ad Review Center.",
    "Google Ads creatives" to "Google Ads creatives", "7 days · Google label" to "7 din · Google label", "Type" to "Type", "Label" to "Label", "No creatives from the Google Ads script" to "Google Ads script se creatives nahi",
    "AppLovin check" to "AppLovin check", "app vs API" to "app vs API", "Update" to "Update", "App (live)" to "App (live)", "live" to "live", "no key / no cron" to "key nahi / cron nahi",
    "Ads: format" to "Ads: format", "Ads: network" to "Ads: network", "impr" to "impr",
    // ---- live
    "LIVE USERS NOW" to "ABHI LIVE USERS", "refreshes every 20 sec" to "har 20 sec refresh", "in 30 min" to "30 min me", "Live by app" to "App-wise live", "No app list" to "App list nahi",
    "Live feed" to "Live feed", "latest impressions (30 min)" to "taaza impressions (30 min)", "Time" to "Time", "No impressions in 30 min" to "30 min me koi impression nahi",
    "Live feed only for today's period" to "Live feed sirf aaj ke period me", "New installs" to "Naye installs", "last 6 hours" to "pichhle 6 ghante", "Phone" to "Phone",
    "No new install in 6 hours" to "6 ghante me naya install nahi", "Only for today's period" to "Sirf aaj ke period me", "in this period" to "is period me", "Ver" to "Ver", "Error" to "Error", "Where" to "Kahan",
    "No crashes 🎉" to "Koi crash nahi 🎉", "Change the period from the Home/Money tabs. The live feed is always today." to "Period badalne ke liye Home/Paisa tab ke chips use karo. Live feed hamesha aaj ka hai.",
    // ---- cross promo
    "Shown in" to "Kis app me", "Promoted" to "Promote hua", "Promoted app" to "Promote hua app", "Install / click" to "Install / click", "Rewards" to "Rewards", "rewarded" to "rewarded",
    "By ad type" to "Ad type wise", "Ad type" to "Ad type", "Inst / 1000 views" to "Inst / 1000 views", "No promo shown in this period" to "Is period me promo nahi dikha",
    "Which app promotes which" to "Kis app me kis app ka promo", "Inst/click" to "Inst/click", "No cross promo shown in this period" to "Is period me cross promo nahi dikha",
    "By promoted app" to "Promote hue app wise", "who got how many installs" to "kisko kitne installs mile", "By design" to "Design wise", "which mockup gets more clicks" to "kaunsa mockup zyada click laata hai",
    "Design" to "Design", "No design data yet" to "Abhi design data nahi", "App open" to "App open", "Inter image" to "Inter image", "Inter video" to "Inter video", "Rewarded" to "Rewarded",
    "An install counts only when the promoted app is also connected to the dashboard (tracker_url/tracker_key) and runs the new tracker version." to
        "Install tabhi ginta hai jab promote hua app bhi dashboard se juda ho (tracker_url/tracker_key) aur naye tracker wala version ho.",
    "shown in this app / installs of this app" to "is app me dikhe / is app ke installs", "Promos shown in this app" to "Is app me dikhe promos",
    "Where this app's installs came from" to "Is app ke installs kis promo se aaye", "No installs from other apps' promos" to "Dusre apps ke promo se install nahi",
    // ---- app detail
    "this app's campaigns" to "is app ki campaigns", "conv" to "conv", "revenue ₹, spend ₹ (dash), installs" to "revenue ₹, kharcha ₹ (dash), installs", "No campaign on this app" to "Is app par campaign nahi",
    "No installs" to "Installs nahi", "Daily history" to "Roz ki history", "After the cron runs" to "Cron ke baad", "ROAS cohort" to "ROAS cohort",
    // ---- campaign
    "Campaign not found (period changed?)" to "Campaign nahi mili (period badla?)", "SPEND" to "KHARCHA", "COST / CONV" to "COST / CONV", "Conversions" to "Conversions", "installs (Google)" to "installs (Google)",
    "Conv rate" to "Conv rate", "conv ÷ clicks" to "conv ÷ clicks", "Suggestion" to "Raay", "avg cost/conv" to "avg cost/conv",
    "Spending but 0 conversions. Check creatives and the Play listing first, otherwise pause." to "Kharcha ho raha hai par 0 conversions. Pehle creatives aur Play listing check karo, warna pause karo.",
    "Not enough data yet for a suggestion." to "Abhi itna data nahi ki raay di jaaye.",
    "This campaign is %s%% cheaper than average. Worth raising budget 15–20%%, then watch ROAS for 3 days." to "Yeh campaign average se %s%% sasti hai. Budget 15–20%% badhane layak, phir 3 din ROAS dekho.",
    "%s%% costlier than average. Remove low-label creatives or lower bid/target." to "Average se %s%% mehngi. Low-label creatives hatao ya bid/target kam karo.",
    "Around average. Let it run, keep rotating creatives." to "Average ke aas-paas. Jaise chal rahi hai chalne do, creatives rotate karte raho.", "from install day" to "install ke din se",
    "No utm_campaign with this name in the Play referrer. Put utm_campaign=%s in the ad's final URL (same as the Google Ads campaign name) and cohort ROAS will show here." to
        "Is naam ka utm_campaign Play referrer me nahi mila. Ads ke final URL me utm_campaign=%s lagao (Google Ads ke campaign naam jaisa), tab cohort ROAS yahan aayega.",
    "Installs (utm)" to "Installs (utm)", "Ad rev" to "Ad rev", "spend ₹ (bars), conversions (line)" to "kharcha ₹ (bars), conversions (line)", "Spend ₹" to "Kharcha ₹",
    "this campaign · 7 days" to "is campaign ke · 7 din", "No creatives for this campaign from the script" to "Script se is campaign ke creatives nahi aaye",
    "One creative can run in several ads, so creative cost will not match campaign cost." to "Ek creative kai ads me chal sakta hai, isliye creative ka cost campaign ke cost se match nahi karega.",
    "Installs from this campaign" to "Is campaign se installs",
    "No utm_campaign with this name in the Play referrer. Put utm_campaign=%s in the ad's final URL to see installs here and (after app.php) the ROAS cohort." to
        "Play referrer me is naam ka utm_campaign nahi mila. Ads ke final URL me utm_campaign=%s lagao to yahan installs aur (app.php ke baad) ROAS cohort dikhega.",
    "what changed, when" to "kya badla, kab", "Change log / notes" to "Change log / notes",
    // ---- extra sections
    "This data comes from the new server file api/app.php. Upload server/api/app.php from the zip into mfc_dash/api/, that's all." to
        "Yeh data server ke naye api/app.php se aata hai. Zip ke server/api/app.php ko mfc_dash/api/ me upload karo, bas.",
    "No utm_campaign installs or campaign spend in this period" to "Is period me utm_campaign wale installs ya campaign kharcha nahi", "Total" to "Total", "inst" to "inst",
    "Dn = earnings up to n days after install ÷ spend. Green = on target, red = under half. Campaign name and the Play link's utm_campaign must match." to
        "Dn = install ke n din tak ki kamai ÷ kharcha. Hara = target par, laal = aadhe se kam. Campaign ka naam aur Play link ka utm_campaign same hona chahiye.",
    "No notes yet. When you change budget/bid/creative, write it here: later it lines up with ROAS." to "Abhi koi note nahi. Jab budget/bid/creative badlo, yahan likho: baad me ROAS ke saath milaan hota hai.",
    "Remove" to "Hatao", "e.g. raised budget 20%, paused video creative #3" to "Jaise: budget 20% badhaya, video creative #3 roka", "Save note" to "Note save karo",
    "Total ROAS" to "Total ROAS", "profit" to "profit", "earned/install" to "kamai/install", "back" to "wapas",
    // ---- login
    "Revenue, ad spend, ROAS, users and crashes, all in one place." to "Revenue, ad kharcha, ROAS, users aur crashes, sab ek jagah.", "Sign in" to "Sign in",
    "Same username/password as the website." to "Wahi username/password jo website par hai.", "Username" to "Username", "Password" to "Password",
    "Hide server address" to "Server address chhupao", "Change server address" to "Server address badlo", "Dashboard URL" to "Dashboard URL", "e.g. https://tirangae-online.live/mfc_dash/" to "Jaise https://tirangae-online.live/mfc_dash/",
    "Enter both username and password" to "Username aur password dono bharo", "Login failed" to "Login nahi hua",
    "Login is saved encrypted on the phone; if the session expires the app signs in again by itself." to "Login phone me encrypted save rehta hai; session expire ho to app khud dobara login karta hai.",
    // ---- alerts
    "alerts.php is not on the server" to "alerts.php server par nahi hai",
    "See the README step: upload server/api/alerts.php into mfc_dash/api/. After that this screen and notifications start working." to "README ka step dekho: server/api/alerts.php ko mfc_dash/api/ me upload karo. Uske baad yeh screen aur notifications chalu ho jaayenge.",
    "Check again" to "Dobara check karo", "CLAUDE AI · MORNING BRIEF" to "CLAUDE AI · SUBAH KA BRIEF", "on %s data" to "%s ke data par", "Claude AI is off" to "Claude AI abhi band hai",
    "Add the API key in Settings: cause + what to do now for every alert, and a daily 9 AM brief." to "Settings me API key daalo: har alert ki wajah + abhi kya karein, aur roz subah 9 baje ka brief.",
    "All good ✅" to "Sab theek ✅", "%s open alerts" to "%s khule alert", "urgent" to "urgent", "watch" to "dhyan do", "check" to "check", "AI writing…" to "AI likh raha…", "Mark read" to "Sab padha",
    "Check now" to "Abhi check", "History" to "History", "Data" to "Data", "No open alert right now. Rules check the data every few minutes." to "Abhi koi khula alert nahi. Rules har kuch minute me data check karte hain.",
    "Nothing in this filter." to "Is filter me kuch nahi.", "What the rules watch" to "Rules kya dekhte hain",
    "data stopped (app / server), ads stopped, Google Ads script silent, campaign 0 impressions, spend with 0 installs, CPI 2.5x, ROAS loss (<60%), crash spike, revenue 85%+ down, ad unit no-fill, cron down" to
        "data aana band (app / server), ads band, Google Ads script chup, campaign 0 impressions, kharch par 0 install, CPI 2.5x, ROAS loss (<60%), crash spike, revenue 85%+ neeche, ad unit no-fill, cron band",
    "data low, impressions down, CPI +50%, ROAS <85%, campaign D3 ROAS <60%, spend 2x faster, new version crashing, ad fill down, ad unit CTR/eCPM/fill down, revenue/installs 60%+ down, eCPM halved, uninstalls high, payments failing, D1 retention down" to
        "data kam, impressions gire, CPI +50%, ROAS <85%, campaign D3 ROAS <60%, kharcha 2x tez, naya version crash, ad fill gira, ad unit CTR/eCPM/fill gira, revenue/installs 60%+ neeche, eCPM aadha, uninstall zyada, payments fail, D1 retention giri",
    "9 AM summary of yesterday (+AI brief), new/paused campaign, revenue/installs up, new record, urgent issue recovered" to "subah 9 baje kal ka hisaab (+AI brief), nayi/paused campaign, revenue/installs upar, naya record, urgent theek ho gaya",
    "Notification / Claude AI settings →" to "Notification / Claude AI settings →", "Open app" to "App kholo", "✓ Done" to "✓ Done", "closed" to "band",
    // ---- settings
    "Applies to the whole app, alert texts and Claude AI explanations. Default: English." to "Poori app, alert text aur Claude AI explanations par lagta hai. Default: English.",
    "Logout" to "Logout", "24x7 live monitoring" to "24x7 live monitoring", "Always-on monitor" to "Hamesha-chalu monitor",
    "checks the server every minute, even with the app closed; a small fixed notification stays visible" to "app band ho tab bhi har minute server check; ek chhoti fixed notification dikhti rehti hai",
    "Off = check every 15–60 min via Android background work (see below). On = issue → notification within about a minute: which app, which account, which ad unit, what happened, loss estimate and fix." to
        "Band = Android background work se har 15–60 min check (neeche). Chalu = issue aate hi ~1 minute me notification: kis app, kis account, kis ad unit me kya hua, nuksan ka andaza aur fix.",
    "Notifications" to "Notifications", "needs alerts.php" to "alerts.php chahiye",
    "⚠ No notification permission — turn it on in phone Settings > Apps > MFC > Notifications." to "⚠ Notification permission nahi hai — phone Settings > Apps > MFC > Notifications on karo.",
    "sound + vibration: data stopped, crash, loss" to "awaaz + vibration: data band, crash, loss", "there is an issue, no fix needed right now" to "issue hai, abhi fix zaroori nahi",
    "morning brief, records, new campaign" to "subah ka brief, records, nayi campaign", "Background check interval when the monitor is off" to "Monitor band ho tab background check kitni der me",
    "With the app open: every 20 sec. 15 min is Android's minimum; on Xiaomi/Oppo/Vivo set this app to \"No restrictions\" in phone Settings > Battery, otherwise background checks stop." to
        "App khuli ho to har 20 sec. 15 min Android ki minimum hai; Xiaomi/Oppo/Vivo par phone Settings > Battery me is app ko \"No restrictions\" do, warna background check ruk jaata hai.",
    "Test alert sent — a notification should arrive" to "Test alert bheja — notification aani chahiye", "Test notification" to "Test notification", "View alerts" to "Alerts dekho",
    "The rules check all data by themselves. Claude AI adds: for every Urgent/Watch alert, what happened, the cause, the loss/benefit and what to do now, plus a daily 9 AM AI brief." to
        "Rules khud sab data check karte hain. Claude AI upar se: har Urgent/Dhyan-do alert par kya hua, wajah, nuksan/fayda aur abhi kya karo, aur roz subah 9 baje kal ka AI brief.",
    "Key" to "Key", "Last AI reply" to "Aakhri AI jawab", "not yet" to "abhi nahi", "New key (to replace)" to "Nayi key (badalni ho to)", "Hide" to "Chhupao", "Model" to "Model",
    "Opus 5.5 (best)" to "Opus 5.5 (best)", "Sonnet 5.5" to "Sonnet 5.5", "Haiku 5.5 (cheap)" to "Haiku 5.5 (sasta)", "Save model" to "Model save", "Save + test" to "Save + test", "Turn AI off" to "AI band",
    "Key: console.anthropic.com > API Keys. Cost is small: one alert ≈ ₹1, the daily brief ≈ ₹4 (Opus 5.5). The key stays in the server DB (kv table), not on the phone." to
        "Key: console.anthropic.com > API Keys. Kharcha chhota hai: ek alert ≈ ₹1, roz ka brief ≈ ₹4 (Opus 5.5). Key server ke DB (kv table) me rehti hai, phone me nahi.",
    "Claude AI turned off" to "Claude AI band kar diya", "Key works, Claude AI is on ✅" to "Key sahi hai, Claude AI chalu ✅", "Key saved but the test call failed: " to "Key save hui par test call fail: ",
    "Save failed" to "Save nahi hua", "Test failed" to "Test nahi hua", "Check failed" to "Check nahi hua", "%s new alerts" to "%s naye alert", "No new alert — all good" to "Koi naya alert nahi — sab theek",
    "ROAS target" to "ROAS target", "marker on the Home profit bar" to "Home ke profit bar ka nishaan", "Target" to "Target",
    "100% = break-even (ad revenue + payments = ad spend). Per research, scale a campaign only above 150%." to "100% = break-even (ad revenue + payments = ad kharcha). Research ke hisaab se 150% se upar hi campaign scale karo.",
    "Data source" to "Data source", "Auto refresh" to "Auto refresh", "Every 20 sec (when the period includes today)" to "Har 20 sec (jab period me aaj ho)",
    // ---- monitor service
    "Monitoring…" to "Nazar rakh raha hoon…", "Server not reachable" to "Server se sampark nahi", "No internet, or the server could not be found. Check the net and try again." to "Internet nahi hai ya server nahi mila. Net check karke dobara try karo.",
    "Could not connect to the server. Try again in a little while." to "Server se connect nahi ho paya. Thodi der baad dobara try karo.",
    "The server took too long to answer. Try again in a little while." to "Server ne time par jawab nahi diya. Thodi der baad dobara try karo.",
    "Secure connection to the server failed. Check the phone's date/time and the net." to "Server se secure connection nahi bana. Phone ka date/time aur net check karo.",
    "Network problem. Try again." to "Net me dikkat hai. Dobara try karo.", "Server is busy (error %s). Try again in a little while." to "Server abhi busy hai (error %s). Thodi der baad dobara try karo.",
    "All good" to "Sab theek", "checked" to "check", "MFC live monitor" to "MFC live monitor",
)
