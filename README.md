# MFC Dashboard — Android app

Website (`tirangae-online.live/mfc_dash`) ka hi data, phone par: profit, ROAS, ad spend, users, ads, crashes, cross promo.
Login wahi username/password jo site par hai. Server par **sirf ek nayi file** upload karni hai (neeche step 2).

## 1. App banao (Android Studio)
1. `MfcDashApp.zip` kholo, folder `MfcDashApp` ko Android Studio mein **Open** karo (File → Open).
2. Gradle sync hone do (pehli baar 2–5 min, internet chahiye).
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**. APK `app/build/outputs/apk/debug/` mein milega.
   Play Store / signed APK ke liye apni keystore se **Generate Signed Bundle / APK** karo.
4. Phone par install karo, login karo. Bas.

Chahiye: Android Studio Ladybug (2024.2) ya naya, JDK 17 (Studio ke saath aata hai). Phone Android 7.0+ (minSdk 24).

Dashboard ka address `app/build.gradle.kts` mein `DEFAULT_BASE_URL` hai; app ke login screen par "Server address badlo" se bhi badal sakte ho.

## 2. Server par ek file (extra data ke liye)
`server/api/app.php` ko server par **`mfc_dash/api/app.php`** upload karo. Database mein kuch nahi karna: yeh file apni chhoti table `app_notes` (change log) khud bana leti hai.

Is file ke bina app chalegi (site wala saara data aayega), par yeh 5 cheezein "app.php chahiye" dikhayengi:
- 1 ghanta / 24 ghante active, WAU, MAU, DAU/MAU
- Retention D1 / D3 / D7 / D14 (Google Ads vs organic)
- Campaign-wise ROAS cohort D0 / D1 / D3 / D7 (target curve ke saath)
- Campaign ka roz ka spend/conv table
- Change log (notes)

Check: browser mein site par login karke `https://tirangae-online.live/mfc_dash/api/app.php?range=7d` kholo, `{"ok":true,...}` dikhna chahiye.

## 2b. Smart alerts + notifications (naya)
`server/api/alerts.php` ko server par **`mfc_dash/api/alerts.php`** upload karo (app.php ke bagal mein). Table `app_alerts` khud ban jaati hai.

Yeh file poore dashboard par nazar rakhti hai aur 3 level ke alerts banati hai:
- **🔴 Urgent** (awaaz + vibration): kisi app se data aana band / server se sab apps ka data band, ads band (users hain par 0 impressions), Google Ads script ne data bhejna band kiya (account-wise), campaign 0 impressions, paisa ja raha par 0 install, CPI 2.5x, 3 din ka ROAS 60% se neeche (loss), campaign cohort D3 ROAS 40% se neeche, crash spike, revenue 85%+ neeche, dashboard cron band.
- **🟠 Dhyan do** (issue hai, abhi fix zaroori nahi): data/impressions kam, CPI +50%, ROAS <85%, spend 2x tez, naya version crash kar raha, ad fill gira, revenue/installs 60%+ neeche, eCPM aadha, uninstall zyada, payments fail, D1 retention giri.
- **🔵 Info**: roz subah 9 baje kal ka hisaab (+ AI brief), nayi/paused campaign, revenue/installs upar, naya record, "urgent theek ho gaya".

Har alert me likha hota hai **kis app / kis Google Ads account / kis campaign** me kya issue hai. Theek hone par urgent alerts apne aap band ho jaate hain.

Kaise chalta hai:
- App khuli ho: har 20 sec. App band ho: har 15 min (Android ki minimum) — phone Settings > Battery me app ko **No restrictions** do (Xiaomi/Oppo/Vivo zaroor).
- Rules server par app ke har poll par chalte hain (4 min se purane ho to). **Zyada turant** chahiye to Plesk me ek aur cron: `https://tirangae-online.live/mfc_dash/api/alerts.php?cron=1&key=CRON_KEY` har 5 min (`CRON_KEY` = config.php ka `cron_key`).
- Pehli baar app sirf abhi ke urgent alerts ki notification degi, purani history nahi.
- App > Settings > Notifications: teeno level on/off, check interval, **Test notification** button.
- Thresholds badalne hon to `alerts.php` ke upar `AL_*` constants.

Check: browser mein login karke `https://tirangae-online.live/mfc_dash/api/alerts.php` kholo, `{"ok":true,"alerts":[...]}` dikhna chahiye.

## 2c. Claude AI (optional, par recommended)
Rules AI ke bina poore chalte hain. Claude AI upar se do kaam karta hai:
1. Har naye Urgent / Dhyan-do alert par 3 line Hinglish me: **kya hua, sabse likely wajah, abhi kya karo** (alert ke neeche "CLAUDE AI" box me, notification me bhi).
2. Roz subah 9 baje **AI brief**: kal ka poora hisaab (sab apps + campaigns + khule alerts) padh kar verdict + 4-6 observations + aaj ke action items (Alerts screen ke upar).

Setup: [console.anthropic.com](https://console.anthropic.com) > API Keys > key banao → App > Settings > Claude AI me paste → **Save + test**. Key server ke DB (`kv` table) me rehti hai; chaaho to `config.php` me `'anthropic_api_key' => 'sk-ant-…'` bhi daal sakte ho.
Model: default `claude-opus-5-5` (sabse accha). Kharcha chhota hai: ek alert ≈ ₹1, roz ka brief ≈ ₹4; mahine ka ₹100-300. Sasta chahiye to Settings me Haiku 5.5 chuno.
Server se `api.anthropic.com` tak cURL jaana chahiye (shared hosting par normally jaata hai); error Settings > Claude AI me dikhega.

## 3. Campaign-wise ROAS sahi aane ke liye (ek baar)
ROAS cohort tab banta hai jab pata ho kaunsa install kis campaign se aaya. Yeh Play Install Referrer ke `utm_campaign` se aata hai.
- Google Ads mein har campaign ke **Final URL** mein lagao: `&referrer=utm_source%3Dgoogle_ads%26utm_medium%3Dcpc%26utm_campaign%3D<CAMPAIGN_NAME>`
  (Google App campaigns mein Google khud `gclid` bhejta hai; utm_campaign tab bhi lagao.)
- `<CAMPAIGN_NAME>` **bilkul wahi** likho jo Google Ads mein campaign ka naam hai (app isi naam se spend jodti hai, case se farak nahi padta).
- Phone Tracker jaise apps jinme naya tracker hai, unka install_referrer pehle se dashboard aa raha hai; purane tracker wale apps mein yeh data nahi aayega.
- `imps` (raw ad data) server par 35 din rehta hai (`config.php` → `raw_days`), isliye cohort revenue isi window mein sahi hai. Zyada lamba chahiye to `raw_days` badhao (DB badi hogi).

## 4. App mein kya kahan hai
- **Home**: profit (ad revenue + payments − Google Ads spend), blended ROAS bar (100% break-even, target Settings se), 6 min / 30 min / 1 ghanta / 24 ghante active, DAU/WAU/MAU, alerts (crash, loss campaign, kam fill, purani summary), best/worst app aur campaign, KPI tiles, har app ka card (Profit/Theek/Loss label). App card tap → us app ka poora data.
- **Paisa**: profit/ROAS/kamaya/kharcha, 14 din ka graph, din-wise ROAS heat, campaign-wise ROAS cohort, ek install ka hisaab (cost/conv, kamai/install, payback din), campaigns (tap → detail), accounts, payments, daily summary, change log.
- **Users**: active (6 min se MAU tak), retention table, din-wise retention, hourly/daily graph, install source, utm, countries, versions, live countries.
- **Ads**: ad revenue/eCPM/ARPDAU/fill/requests, network, format (request → fill → impression → eCPM), ad units, fail reasons, cross promo (tap → poori report), promo video, creatives (MAX + Google Ads), AppLovin API milaan.
- **Live**: abhi active, app-wise, live feed (30 min), naye installs (6 ghante), crashes. Har 20 sec refresh.
- **Campaign detail**: spend, cost/conv, CTR/CPC/conv rate, suggestion (avg se sasti → budget badhao, mehngi → creatives/bid), ROAS D0/D1/D3/D7 vs target, roz ka table, creatives, change log.
- **Settings**: ROAS target, logout.

Period chips (Aaj / Kal / 7 din / 30 din / Custom) site jaise hi hain; "Aaj" par app khud har 20 sec refresh hoti hai.

## 5. Zaroori baatein
- Login phone mein encrypted save hota hai (EncryptedSharedPreferences). Session expire ho to app chup-chaap dobara login karti hai; password badla ho to login screen aayegi.
- Site ka `remember me` cookie 30 din ka hai; app usi ko use karti hai, server par koi naya auth nahi.
- **Security**: jo zip aapne bheja usme `secrets/` folder (Firebase service account keys) aur `config.php` (DB password) the. Maine unhe nahi rakha, par chat mein aa chuke hain, isliye Firebase console se un keys ko **regenerate** kar lena aur DB password badal dena. Aage zip bhejte waqt `secrets/` aur `config.php` hata diya karo.

## 6. Agar kuch na dikhe
- "Login expire ho gaya" baar-baar: username/password check karo (config.php ka `admin_user`/`admin_password`).
- "Jawab JSON nahi tha": URL galat hai ya server login page de raha hai; Settings mein URL `https://.../mfc_dash/` (aakhri slash ke saath) rakho.
- Retention/ROAS khali: `api/app.php` upload hua? Browser mein `api/app.php?range=7d` kholke dekho. Campaign ROAS ke liye step 3.
- Google Ads data nahi: Google Ads script (setup/gads_script.js) account mein chal rahi hai? Site par bhi nahi dikhega to app mein bhi nahi.
- Notification nahi aa rahi: (1) Settings > Notifications > Test notification se check karo, (2) phone ki app notification permission on hai?, (3) Battery > No restrictions, (4) app band hone par 15 min tak lag sakta hai.
- "alerts.php server par nahi hai": step 2b. Claude AI error: Settings > Claude AI me error line dekho (galat key = 401, balance khatam = 400/429).
