/**
 * Read-only Ads Script (setup/gads_health_script.js) ko fake AdsApp ke saath chalata hai (SIMULATED data, koi network nahi).
 *   node server/tests/gads_script_mock_test.js        (sirf node, koi npm package nahi)
 * Check: Google Ads API ke field naam badalne par (campaign.start_date -> campaign.start_date_time) campaigns section
 * fallback se chalta rahe aur budget aaye; koi aur error aaye to section errors[] me jaaye aur campaigns key na bheje.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var vm = require('vm');

var SRC = fs.readFileSync(path.join(__dirname, '..', 'setup', 'gads_health_script.js'), 'utf8');
var CID = '123-456-7890', TOKEN = 'test-token-not-secret', URL = 'https://dash.test/mfc_dash/api/gads_ingest.php';
// server (gv_script) jaise placeholder bharo; token app bharta hai
var code = SRC.replace("'https://YOUR-DOMAIN/mfc_dash/api/gads_ingest.php'", "'" + URL + "'")
  .replace("'000-000-0000'", "'" + CID + "'").replace("'PASTE-TOKEN-HERE'", "'" + TOKEN + "'");

var OLD_DATE = /campaign\.start_date(?!_)/, NEW_DATE = /campaign\.start_date_time/;
function unrecognized(f) { return new Error("UserError: QueryError.UNRECOGNIZED_FIELD: Unrecognized field in the query: '" + f + "'."); }

function campaignRows(q) {
  var withNew = NEW_DATE.test(q), withOld = OLD_DATE.test(q);
  function camp(id, name, budget, extra) {
    var c = { id: id, name: name, status: 'ENABLED', servingStatus: 'SERVING', primaryStatus: 'ELIGIBLE', primaryStatusReasons: [],
      advertisingChannelType: 'MULTI_CHANNEL', biddingStrategyType: 'TARGET_CPA', appCampaignSetting: { appId: 'com.example.app' } };
    for (var k in extra) c[k] = extra[k];
    if (withNew) c.startDateTime = '2025-03-01 00:00:00';
    if (withOld) c.startDate = '2025-03-01';
    return { campaign: c, campaignBudget: budget };
  }
  return [
    camp('111', 'Install IN', { amountMicros: '500000000', period: 'DAILY' }, { targetCpa: { targetCpaMicros: '25000000' } }),
    camp('222', 'Total budget', { period: 'CUSTOM_PERIOD' }, { biddingStrategyType: 'MAXIMIZE_CONVERSION_VALUE', maximizeConversionValue: { targetRoas: 1.5 } })
  ];
}

// api: { campaigns(q) -> throw ya null }  (null = normal rows)
function fakeRows(q, api, log) {
  var from = (/\bFROM\s+(\w+)/.exec(q) || [])[1];
  var d = '2026-10-23', m = { costMicros: '1000000', impressions: '100', clicks: '10', conversions: 2, conversionsValue: 5 };
  if (from === 'customer') return [{ customer: { id: '1234567890', descriptiveName: 'Mock acct', currencyCode: 'INR', timeZone: 'Asia/Calcutta', status: 'ENABLED', manager: false } }];
  if (from === 'campaign' && /segments\.hour/.test(q)) return [{ segments: { date: d, hour: 5 }, campaign: { id: '111' }, metrics: m }];
  if (from === 'campaign' && /segments\.date/.test(q)) return [{ segments: { date: d }, campaign: { id: '111' }, metrics: m }];
  if (from === 'campaign') { log.campaignQueries.push(q); api.campaigns(q); return campaignRows(q); }
  if (from === 'ad_group') return [{ segments: { date: d }, campaign: { id: '111' }, adGroup: { id: '9', name: 'AG', status: 'ENABLED' }, metrics: m }];
  if (from === 'geographic_view') return [{ segments: { date: d }, campaign: { id: '111' }, geographicView: { countryCriterionId: '2356' }, metrics: m }];
  if (from === 'geo_target_constant') return [{ geoTargetConstant: { id: '2356', countryCode: 'IN' } }];
  if (from === 'ad_group_ad') return [];
  if (from === 'ad_group_ad_asset_view') return [];
  throw new Error('mock: unexpected query ' + q);
}

function run(api) {
  var log = { posts: [], logs: [], campaignQueries: [] };
  var ctx = {
    AdsApp: {
      currentAccount: function () { return { getCustomerId: function () { return CID; }, getTimeZone: function () { return 'Asia/Calcutta'; } }; },
      search: function (q) {
        var list = fakeRows(q, api, log), i = 0;
        return { hasNext: function () { return i < list.length; }, next: function () { return list[i++]; } };
      }
    },
    UrlFetchApp: {
      fetch: function (url, opts) {
        log.posts.push({ url: url, headers: opts.headers, body: JSON.parse(opts.payload) });
        return { getResponseCode: function () { return 200; }, getContentText: function () { return '{"ok":true,"backfill_days":3,"hourly_days":2}'; } };
      }
    },
    Utilities: { formatDate: function (dt) { return new Date(dt).toISOString().substring(0, 10); } },
    Logger: { log: function (s) { log.logs.push(String(s)); } }
  };
  vm.createContext(ctx);
  vm.runInContext(code, ctx, { filename: 'gads_health_script.js' });
  vm.runInContext('main()', ctx);
  log.data = (log.posts.filter(function (p) { return p.body.action === 'data'; })[0] || {}).body;
  return log;
}

var n = 0, fail = 0;
function ok(c, m) { n++; if (!c) { fail++; console.log('FAIL ' + m); } }
function camp(data, id) { return (data.campaigns || []).filter(function (c) { return c.campaign_id === id; })[0] || {}; }

function common(name, r) {
  ok(r.posts.length === 2 && r.posts[0].body.action === 'hello', name + ': hello then data POST');
  ok(!!r.data, name + ': data POST sent');
  ok(r.posts.every(function (p) { return p.url === URL && p.headers['X-Token'] === TOKEN && p.body.customer_id === CID; }), name + ': URL, token header, customer id');
  ok(r.data.script_version === '1.1', name + ': script_version 1.1');
  ok(Array.isArray(r.data.days) && r.data.days.length === 1 && Array.isArray(r.data.hours) && r.data.hours.length === 1, name + ': daily + hourly sections still sent');
  ok(Array.isArray(r.data.geo) && r.data.geo.length === 1 && r.data.geo[0].country === 'IN', name + ': countries section still sent');
}
function campaignsOk(name, r, start, nErr) {
  ok(Array.isArray(r.data.campaigns) && r.data.campaigns.length === 2, name + ': campaigns present (2)');
  var a = camp(r.data, '111'), b = camp(r.data, '222');
  ok(a.start_date === start && b.start_date === start, name + ": start_date '" + start + "' (got '" + a.start_date + "')");
  ok(a.budget === 500 && a.budget_period === 'DAILY', name + ': daily budget 500 from amount_micros');
  ok(b.budget === null && b.budget_period === 'CUSTOM_PERIOD', name + ': CUSTOM_PERIOD budget stays null (missing, not zero)');
  ok(a.target_cpa === 25 && b.target_roas === 1.5 && a.pkg === 'com.example.app', name + ': target CPA / ROAS / package mapped');
  ok(Array.isArray(r.data.errors) && r.data.errors.length === (nErr || 0), name + ': errors count ' + (nErr || 0) + ' (' + JSON.stringify(r.data.errors) + ')');
}

// (a) aaj ka API: start_date reject, start_date_time chalta hai
var a = run({ campaigns: function (q) { if (OLD_DATE.test(q)) throw unrecognized('campaign.start_date'); } });
common('a', a); campaignsOk('a', a, '2025-03-01');
ok(a.campaignQueries.length === 1 && NEW_DATE.test(a.campaignQueries[0]), 'a: one campaigns query, with start_date_time');

// (b) purana API: start_date_time reject, start_date chalta hai
var b = run({ campaigns: function (q) { if (NEW_DATE.test(q)) throw unrecognized('campaign.start_date_time'); } });
common('b', b); campaignsOk('b', b, '2025-03-01');
ok(b.campaignQueries.length === 2 && OLD_DATE.test(b.campaignQueries[1]) && !NEW_DATE.test(b.campaignQueries[1]), 'b: retried with campaign.start_date');
ok(b.logs.some(function (s) { return /start_date_time nahi chala/.test(s); }), 'b: fallback logged');

// (c) dono reject: bina start date ke, budget fir bhi
var c = run({ campaigns: function (q) {
  if (NEW_DATE.test(q)) throw unrecognized('campaign.start_date_time');
  if (OLD_DATE.test(q)) throw unrecognized('campaign.start_date');
} });
common('c', c); campaignsOk('c', c, '', 1);
ok(c.campaignQueries.length === 3 && !/start_date/.test(c.campaignQueries[2]) && /campaign_budget\.amount_micros/.test(c.campaignQueries[2]), 'c: third query has no start date, keeps budget');
ok(c.data.errors.length === 1 && c.data.errors[0].section === 'campaign_start_date', 'c: missing start date reported (campaign_start_date), campaigns still sent');

// (d) koi aur error: retry nahi, section errors me, campaigns key nahi
var d = run({ campaigns: function () { throw new Error('UserError: AuthorizationError.USER_PERMISSION_DENIED: PERMISSION_DENIED for this customer.'); } });
common('d', d);
ok(!('campaigns' in d.data), 'd: campaigns key absent (server keeps old data)');
ok(d.data.errors.length === 1 && d.data.errors[0].section === 'campaigns' && /PERMISSION_DENIED/.test(d.data.errors[0].error), 'd: errors has section campaigns');
ok(d.campaignQueries.length === 1, 'd: unrelated error not retried');

// (e) kisi DUSRE field ka UNRECOGNIZED_FIELD: chhupna nahi chahiye, retry nahi
var e = run({ campaigns: function (q) { if (/primary_status_reasons/.test(q)) throw unrecognized('campaign.primary_status_reasons'); } });
common('e', e);
ok(!('campaigns' in e.data), 'e: campaigns key absent');
ok(e.data.errors.length === 1 && e.data.errors[0].section === 'campaigns' && /primary_status_reasons/.test(e.data.errors[0].error), 'e: other field error reported under campaigns');
ok(e.campaignQueries.length === 1, 'e: other field error not retried');

// (f) koi aur error jiske text me query (start_date_time) bhi hai: retry nahi
var f = run({ campaigns: function (q) { throw new Error('InternalError: INTERNAL_ERROR: please retry. query=' + q); } });
common('f', f);
ok(!('campaigns' in f.data) && f.data.errors.length === 1 && /INTERNAL_ERROR/.test(f.data.errors[0].error), 'f: internal error reported under campaigns');
ok(f.campaignQueries.length === 1, 'f: error echoing the query not retried');

// (g) purana error format bhi pehchana jaye
var g = run({ campaigns: function (q) { if (NEW_DATE.test(q)) throw new Error("Exception: Call to GoogleAdsService.Search failed: Unrecognized field in the query: 'campaign.start_date_time'."); } });
common('g', g); campaignsOk('g', g, '2025-03-01');
ok(g.campaignQueries.length === 2, 'g: older message shape falls back to start_date');

// script VERSION aur server GA_SCRIPT_VER ek hi hon
var lib = fs.readFileSync(path.join(__dirname, '..', 'lib', 'gads_health.php'), 'utf8');
var sv = (/GA_SCRIPT_VER\s*=\s*'([^']+)'/.exec(lib) || [])[1];
ok(sv === a.data.script_version, 'script VERSION (' + a.data.script_version + ') = GA_SCRIPT_VER (' + sv + ')');

if (fail) { console.log('gads script mock test FAILED: ' + fail + ' of ' + n + ' checks'); process.exit(1); }
console.log('gads script mock test: ' + n + ' checks passed');
