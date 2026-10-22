# Play signals: per-version vitals, "new version live" alert, Play Console emails as alerts

## What changed

1. **Per-version vitals** (`lib/health.php`, `h_job_vitals`)
   - Every vitals run now also asks Google for the same latest day split by `versionCode` (crash set and ANR set). It keeps the top 5 versions by distinct users and saves them in the new column `health_apps.vitals_ver` (JSON).
   - If the per-version query fails, the main vitals job keeps going. The failure is only logged as a `warn` in the Health logs.
   - The column is added automatically on the first request (safe `ALTER`, runs once; kv `h_schema_m = 2`). If two requests add it at the same time, the duplicate-column error is ignored. If the DB user may not `ALTER`, the module keeps working without `vitals_ver`, and the next request tries again.
   - Google's data is still 1-2 days late. `vitals_ver.day` tells you which day the numbers are for.
2. **New alert rule `new_version`** (`api/alerts.php`, `al_rule_new_version`)
   - It fires once per version when MFC tracker data (`day_ver`, the same table `ver_crash` uses) shows a version that:
     - is higher than the highest one seen before for that app, and
     - is on at least 5 distinct devices (`AL_LIVE_MIN_USERS`), counted over today + yesterday (local days). So it looks back 24-48 h, and test installs don't fire it.
   - Level 1 (info). akey `live|<pkg>|<ver>`.
   - It never fires again for that version, even after the alert is auto-resolved. Two checks make sure of this: the akey is looked up in `app_alerts` in any state, and the highest live version per app is kept in kv `al_live_ver`.
   - First run after deploy: the starting point is the highest version from the last 30 days of history. This avoids an alert flood for old versions. An app with no history gets a silent starting point.
3. **Play Console emails become app alerts** (new `lib/playmail.php`, run from the health cron)
   - Small IMAP-over-TLS client. It does not need the php-imap extension.
   - It verifies the TLS certificate, uses 20 s timeouts, handles 30 mails per run at most, and holds a lock (`GET_LOCK('mfc_playmail')`) so two runs can't overlap. It handles a PREAUTH greeting and uses `STORE +FLAGS.SILENT`. The password is never put in an error or log message, and is marked `#[\SensitiveParameter]` so it doesn't show in stack traces.
   - It decodes MIME (multipart, quoted-printable, base64, RFC2047 subjects, HTML to text) and reads both kinds of forwarded mail: the Gmail "Forwarded message" block and a mail attached as `message/rfc822`.
   - RFC2047 words in a row that use the same charset are joined as bytes before converting, so a UTF-8 character split across two words still decodes. A body cut at 20 KB in the middle of a character loses only that character, not the whole text. Bad bytes in declared UTF-8 become `?`. Only undeclared 8-bit text is treated as Windows-1252.
   - It only accepts mails from google.com (direct, or the forwarded From) that come from a Play sender (`googleplay` / `play-console` address) or mention Google Play / Play Console anywhere in the subject or text. Google Play order receipts are skipped.
   - DKIM: only the topmost `Authentication-Results` header(s) (the ones your own mail server adds) are trusted, because lower ones can be written by the sender. A DKIM or DMARC fail for the From domain (google.com for direct mail, the forwarder's domain for hand-forwards) means the mail is skipped and a `warn` goes to the Health logs. If the server adds no such header, the result is `unknown` and the mail is accepted. Turn on incoming DKIM checks in Plesk, and set `forwarders`.
   - It writes one `app_alerts` row per mail. Duplicates are skipped by Message-ID (for an attached `.eml` forward, the original mail's Message-ID), and also by the same content within 12 hours (the same mail forwarded from two Gmail accounts). An identical second rejection more than 12 h later alerts again.
   - Level is conservative. **Urgent (3)** is used only when a reject / not approved / removed / suspended / terminated has clearly happened: the word is in the subject, or the body says e.g. "your app … has been removed" or "removed your app". Threats and conditions ("will be removed", "may result in suspension", "to avoid removal", "if your app is rejected") are **watch (2)** with `match: "removal warning"`. "Action required", "violation" and "not compliant" are also watch. A user or tester being removed is not urgent. "Policy" counts only in the subject. App names are masked before classifying, so "Live Location" or "Removed Photos" don't count as keywords.
   - A mail that keeps failing (DB / parse error) is retried on the next 2 runs, then marked `\Seen` and logged as an error, so one bad mail can't block the queue. Connection errors fail the whole run and leave the mail unseen.
   - The Gmail "forwarding confirmation" mail (only from `forwarding-noreply@google.com`, sent directly) becomes an info alert that carries the confirm link and code, so setup can be finished from the app.
   - Every mail it processes is marked `\Seen`, so use a dedicated mailbox.
   - Errors go to the Health logs (`health_events`). Status is kept in kv `pm_status`.
   - Play mail alerts close themselves after 24 h like other event alerts. They do not fire again.
4. **API** (`api/health.php`): `GET ?action=playmail_status`, `POST ?action=playmail_test` (login required). The cron calls `pm_cron()`, and only does anything when config is present.
5. **Config sample**: a `play_mail` block was added to `health_config.sample.php`.
6. **Test**: `tests/play_signals_test.php` (51 checks) uses only simulated data, a local fake TLS IMAP server and canned Play API responses. Since the review it also covers a forged lower `Authentication-Results`, a "will be removed" newsletter (watch), and a mail that keeps failing (skipped after 3 tries).
   - The existing suites still pass (cost 42, gads 84, studio 102).
   - The CI workflow (`.github/`, outside `server/`) only syntax-checks it for now. To run it in CI, add a step like the other MySQL tests.

## New config keys (`mfc_dash/health_config.php`, optional)

```php
'play_mail' => ['host' => 'mail.your-domain', 'port' => 993, 'user' => 'playmail@your-domain', 'pass' => '...', 'forwarders' => ['you@gmail.com']],
```

- Leave `host`, `user` or `pass` empty, or leave out the block, and the feature stays off silently.
- `forwarders` is optional. When it is set, hand-forwarded mails are accepted only from these addresses. Mails that come straight from google.com are always accepted.
- `host` must match the mail server's SSL certificate. In Plesk: Mail > "Secure mail with Let's Encrypt", or use the server hostname shown on the certificate.

Setup: create a dedicated Plesk mailbox. Then in each Gmail account, add it under Settings > Forwarding (the confirm link arrives in the app's Alerts), and add a filter: from google.com + "Google Play" > forward to that mailbox.

## Upload (into `mfc_dash/`)

- `api/health.php`, `api/alerts.php`, `lib/health.php`, `lib/playmail.php` (new).
- Optional: add the `play_mail` block to `health_config.php`.
- Do not upload `tests/` or this file.

## JSON the Android app can read

**App rows**: `view=consoles` → `apps[]`, `view=settings` → `apps[]`, `view=app` → `app`. The existing `crash_rate`, `anr_rate` and `vitals_day` stay as they were. New fields:

```json
"vitals_day": "2026-10-20",
"vitals_ver": {"day": "2026-10-20", "rows": [{"vc": "63", "crash": 0.0123, "anr": 0.003075, "users": 3000}, {"vc": "62", "crash": 0.003, "anr": null, "users": 1200}]},
"tracker_ver": "1.11",
"tracker_vc": 64
```

- `vitals_ver` is `null` until the first vitals run after deploy.
- `rows` holds at most 5 entries, sorted by versionCode, highest first.
- `vc` is a string.
- `crash` and `anr` are fractions (0.0123 = 1.23%) or `null` when Google has no value.
- `users` is an int or `null`.
- `tracker_ver` is the highest version that went live per the MFC tracker. It comes from the `new_version` rule and is `null` if unknown.
- `tracker_vc` is an int, or `null` when the tracker's versionCode is unknown.

**`GET api/health.php?action=playmail_status`**

```json
{"ok": true, "configured": true, "last_check": "2026-10-22 12:45:13", "last_ok": "2026-10-22 12:45:13", "last_error": "",
 "last_count": 5, "last_mails": 30, "unseen_left": 6, "mailbox": "p•••••••@your-domain @ mail.your-domain"}
```

- Times are UTC.
- `last_count` is the number of alerts created in the last run.
- `last_error` is `""` when the last run was OK.

**`POST api/health.php?action=playmail_test`**
- Success: `{"ok": true, "result": {"ok": true, "configured": true, "mails": 6, "alerts": 0, "duplicates": 0, "skipped": 6, "unseen": 6, "error": "", "ms": 412}, "status": {…same fields as playmail_status…}}`
- Failure: HTTP 400 with `{"ok": false, "error": "IMAP LOGIN failed: NO [AUTHENTICATIONFAILED] …"}`.

**Overview** (only when `play_mail` is configured):
- `view=overview` → `integrations[]` gets `{"name": "Play Console emails", "kind": "playmail", "status": "OK|ERROR|NOT_SET", "detail": "…"}`.
- `view=settings` → `schedule[]` gets one extra row.

**Alerts** (`api/alerts.php` list, same row format as all alerts):

- `rule: "new_version"`
  - level 1, akey `live|<pkg>|<ver>`, `pkg` set
  - title `"<App> v<ver> is live on Play"`, title_hi `"<App> v<ver> Play par live"`
  - body has the user count
  - `metrics`: `{"version": "1.10", "version_code": 64|null, "users": 7, "previous_version": "1.9", "previous_version_code": null, "min_users": 5}`
- `rule: "play_mail"`
  - level 3 / 2 / 1 for urgent / watch / info, akey `mail|<sha1>`
  - `pkg` is the matched app, or `""`
  - title is the cleaned mail subject; title_hi is `"Play: reject · <App>"` (other prefixes: `Play: app hataya`, `Play: suspend`, `Play: account band`, `Play: hataane ki warning`, `Play: policy violation`, `Play: policy compliance`, `Play: action zaroori`, `Play: warning`, `Play: live`, `Play: approve`, `Play: policy`, `Play: mail`, …)
  - body is about 600 chars of mail text (footer removed); body_hi is the same text
  - `metrics`: `{"from": "no-reply-googleplay-developer@google.com", "date": "<mail Date header>", "level_word": "urgent|watch|info", "match": "rejected", "via": "direct|forward|attached", "forwarded_by": "", "dkim": "pass|unknown", "chash": "…"}`
    - `match` is one of: urgent `rejected`, `not approved`, `removed`, `suspended`, `terminated`; watch `removal warning`, `violation`, `not compliant`, `action required`, `warning`, `issue found`, `declaration`, `deadline`, `limited`, `policy`; info `review complete`, `approved`, `live`, `published`, `available`, `other`.
    - `dkim` is checked for google.com on direct mail and for the forwarder's domain on forwards. Mails that fail are never stored, so the value is only `pass` or `unknown`.
    - The forwarding-confirmation alert has the same keys (`forwarded_by: ""`).
  - The Gmail forwarding-confirmation alert has the title `"Gmail forwarding confirmation"` and `match: "forwarding confirmation"`.
