/**
 * MFC Dash · Google Ads AI Health Monitor — READ-ONLY Google Ads Script (v1.0)
 *
 * Har Google Ads account me ALAG se lagao (MCC me nahi). Upar ki 3 lines MFC Dash app se milti hain:
 *   app > Google Ads Health > Settings > account > "Script" (token sirf ek baar dikhta hai).
 * Google Ads > Tools > Bulk actions > Scripts > + > ye code paste > Authorize > Preview > Frequency: Hourly.
 *
 * Ye script SIRF PADHTI hai. Isme sirf do Google Ads calls hain: AdsApp.currentAccount() (account ki ID / naam padhna)
 * aur AdsApp.search() (report query). Koi mutate / pause / enable / remove / create / update / budget / bid / label nahi.
 * Server ka CI check is file me koi aur AdsApp call mile to build fail kar deta hai.
 *
 * Kya bhejta hai: account status, campaigns (status, budget, bid target, learning / budget-limited reasons),
 * daily + hourly campaign metrics, ad groups, countries, ad policy status (disapproved / limited), creatives ke labels.
 * Jo hissa padhne me error aaye wo "errors" me jaata hai: dashboard use missing dikhata hai, zero nahi.
 */
var DASH_URL = 'https://YOUR-DOMAIN/mfc_dash/api/gads_ingest.php';   // <-- app se
var CUSTOMER_ID = '000-000-0000';                                      // <-- is account ka Customer ID
var TOKEN = 'PASTE-TOKEN-HERE';                                        // <-- app se (sirf isi account ke liye)

var VERSION = '1.0';

function main() {
  var acc = AdsApp.currentAccount();
  var cid = String(acc.getCustomerId()).replace(/[^0-9]/g, '');
  if (cid !== CUSTOMER_ID.replace(/[^0-9]/g, '')) {
    throw new Error('This script is configured for CID ' + CUSTOMER_ID + ' but runs in ' + acc.getCustomerId() + '. Use the script made for this account.');
  }
  var tz = acc.getTimeZone();
  var errors = [];
  var info = section('account', errors, function () { return accountInfo(); }) || {};
  info.tz = info.tz || tz;
  if (info.manager) throw new Error('This is a manager (MCC) account. Install the script inside each advertiser account separately.');

  var hello = post({ action: 'hello', account: { manager: !!info.manager } });
  var backfill = Math.min(35, Math.max(3, Number(hello.backfill_days || 3)));
  var hourlyDays = Math.min(29, Math.max(2, Number(hello.hourly_days || 2)));

  var today = day(0, tz);
  var from = day(backfill - 1, tz);
  var hFrom = day(hourlyDays - 1, tz);

  var body = {
    action: 'data', script_version: VERSION, account: info,
    campaigns: section('campaigns', errors, function () { return campaigns(); }),
    days: section('daily', errors, function () { return campaignDays(from, today); }),
    hours: section('hourly', errors, function () { return campaignHours(hFrom, today); }),
    adgroups: section('ad_groups', errors, function () { return adGroups(day(Math.min(backfill, 14) - 1, tz), today); }),
    geo: section('countries', errors, function () { return countries(day(Math.min(backfill, 14) - 1, tz), today); }),
    policy: section('policy', errors, function () { return policy(); }),
    assets: section('assets', errors, function () { return assets(); }),
    errors: errors
  };
  // section fail hua to us key ko hata do: server purana data rakhta hai (missing != zero)
  for (var k in body) if (body[k] === undefined) delete body[k];
  var res = post(body);
  Logger.log('MFC Dash: ' + JSON.stringify(res));
  Logger.log('campaigns=' + (body.campaigns || []).length + ' days=' + (body.days || []).length + ' hours=' + (body.hours || []).length +
    ' adgroups=' + (body.adgroups || []).length + ' geo=' + (body.geo || []).length + ' policy=' + (body.policy || []).length + ' errors=' + errors.length);
}

function post(obj) {
  obj.customer_id = CUSTOMER_ID;
  obj.token = TOKEN;
  var res = UrlFetchApp.fetch(DASH_URL, {
    method: 'post', contentType: 'application/json', headers: { 'X-Token': TOKEN },
    payload: JSON.stringify(obj), muteHttpExceptions: true
  });
  var txt = res.getContentText();
  if (res.getResponseCode() !== 200) throw new Error('Dashboard ' + res.getResponseCode() + ': ' + txt.substring(0, 300));
  return JSON.parse(txt);
}

function section(name, errors, fn) {
  try { return fn(); } catch (e) { errors.push({ section: name, error: String(e).substring(0, 400) }); return undefined; }
}

function day(back, tz) { return Utilities.formatDate(new Date(Date.now() - back * 86400000), tz, 'yyyy-MM-dd'); }
function micros(v) { return v ? Number(v) / 1000000 : 0; }
function n(v) { return v ? Number(v) : 0; }

function rows(q) {
  var out = [];
  var it = AdsApp.search(q);
  while (it.hasNext()) out.push(it.next());
  return out;
}

function accountInfo() {
  var r = rows('SELECT customer.id, customer.descriptive_name, customer.currency_code, customer.time_zone, customer.status, customer.manager FROM customer')[0] || {};
  var c = r.customer || {};
  return { name: c.descriptiveName || '', currency: c.currencyCode || '', tz: c.timeZone || '', status: c.status || '', manager: !!c.manager };
}

function campaigns() {
  var q = 'SELECT campaign.id, campaign.name, campaign.status, campaign.serving_status, campaign.primary_status, campaign.primary_status_reasons, ' +
    'campaign.advertising_channel_type, campaign.bidding_strategy_type, campaign.target_cpa.target_cpa_micros, campaign.target_roas.target_roas, ' +
    'campaign.maximize_conversions.target_cpa_micros, campaign.maximize_conversion_value.target_roas, campaign.start_date, ' +
    'campaign.app_campaign_setting.app_id, campaign_budget.amount_micros, campaign_budget.period ' +
    "FROM campaign WHERE campaign.status != 'REMOVED'";
  return rows(q).map(function (r) {
    var c = r.campaign, b = r.campaignBudget || {};
    var tcpa = (c.targetCpa && c.targetCpa.targetCpaMicros) || (c.maximizeConversions && c.maximizeConversions.targetCpaMicros) || 0;
    var troas = (c.targetRoas && c.targetRoas.targetRoas) || (c.maximizeConversionValue && c.maximizeConversionValue.targetRoas) || 0;
    return {
      campaign_id: String(c.id), name: c.name, status: c.status, serving_status: c.servingStatus || '', primary_status: c.primaryStatus || '',
      primary_reasons: c.primaryStatusReasons || [], channel: c.advertisingChannelType || '', bidding: c.biddingStrategyType || '',
      target_cpa: micros(tcpa), target_roas: n(troas), start_date: c.startDate || '', pkg: (c.appCampaignSetting && c.appCampaignSetting.appId) || '',
      budget: b.amountMicros ? micros(b.amountMicros) : null, budget_period: b.period || ''
    };
  });
}

function campaignDays(from, to) {
  return rows('SELECT segments.date, campaign.id, metrics.cost_micros, metrics.impressions, metrics.clicks, metrics.conversions, metrics.conversions_value ' +
    "FROM campaign WHERE segments.date BETWEEN '" + from + "' AND '" + to + "'").map(function (r) {
    return { day: r.segments.date, campaign_id: String(r.campaign.id), cost: micros(r.metrics.costMicros), impressions: n(r.metrics.impressions),
      clicks: n(r.metrics.clicks), conversions: n(r.metrics.conversions), conv_value: n(r.metrics.conversionsValue) };
  });
}

function campaignHours(from, to) {
  return rows('SELECT segments.date, segments.hour, campaign.id, metrics.cost_micros, metrics.impressions, metrics.clicks, metrics.conversions ' +
    "FROM campaign WHERE segments.date BETWEEN '" + from + "' AND '" + to + "'").map(function (r) {
    return { day: r.segments.date, hour: n(r.segments.hour), campaign_id: String(r.campaign.id), cost: micros(r.metrics.costMicros),
      impressions: n(r.metrics.impressions), clicks: n(r.metrics.clicks), conversions: n(r.metrics.conversions) };
  });
}

function adGroups(from, to) {
  return rows('SELECT segments.date, campaign.id, ad_group.id, ad_group.name, ad_group.status, metrics.cost_micros, metrics.impressions, metrics.clicks, ' +
    "metrics.conversions, metrics.conversions_value FROM ad_group WHERE segments.date BETWEEN '" + from + "' AND '" + to + "'").map(function (r) {
    return { day: r.segments.date, campaign_id: String(r.campaign.id), ad_group_id: String(r.adGroup.id), name: r.adGroup.name, status: r.adGroup.status,
      cost: micros(r.metrics.costMicros), impressions: n(r.metrics.impressions), clicks: n(r.metrics.clicks), conversions: n(r.metrics.conversions),
      conv_value: n(r.metrics.conversionsValue) };
  });
}

function countries(from, to) {
  var raw;
  try {
    raw = rows('SELECT segments.date, campaign.id, geographic_view.country_criterion_id, metrics.cost_micros, metrics.impressions, metrics.clicks, ' +
      "metrics.conversions, metrics.conversions_value FROM geographic_view WHERE segments.date BETWEEN '" + from + "' AND '" + to + "' " +
      "AND geographic_view.location_type = 'LOCATION_OF_PRESENCE'").map(function (r) {
      return { day: r.segments.date, campaign_id: String(r.campaign.id), cc: String(r.geographicView.countryCriterionId), m: r.metrics };
    });
  } catch (e) {
    // App campaigns: user_location_view
    raw = rows('SELECT segments.date, campaign.id, user_location_view.country_criterion_id, metrics.cost_micros, metrics.impressions, metrics.clicks, ' +
      "metrics.conversions, metrics.conversions_value FROM user_location_view WHERE segments.date BETWEEN '" + from + "' AND '" + to + "'").map(function (r) {
      return { day: r.segments.date, campaign_id: String(r.campaign.id), cc: String(r.userLocationView.countryCriterionId), m: r.metrics };
    });
  }
  var ids = {};
  raw.forEach(function (x) { ids[x.cc] = true; });
  var codes = {};
  var list = Object.keys(ids);
  for (var i = 0; i < list.length; i += 200) {
    var part = list.slice(i, i + 200).map(function (id) { return 'geoTargetConstants/' + id; });
    if (!part.length) continue;
    rows("SELECT geo_target_constant.resource_name, geo_target_constant.id, geo_target_constant.country_code FROM geo_target_constant WHERE geo_target_constant.resource_name IN ('" +
      part.join("','") + "')").forEach(function (r) { codes[String(r.geoTargetConstant.id)] = r.geoTargetConstant.countryCode; });
  }
  var agg = {};
  raw.forEach(function (x) {
    var cc = codes[x.cc];
    if (!cc) return;
    var k = x.day + '|' + x.campaign_id + '|' + cc;
    var a = agg[k] || (agg[k] = { day: x.day, campaign_id: x.campaign_id, country: cc, cost: 0, impressions: 0, clicks: 0, conversions: 0, conv_value: 0 });
    a.cost += micros(x.m.costMicros); a.impressions += n(x.m.impressions); a.clicks += n(x.m.clicks);
    a.conversions += n(x.m.conversions); a.conv_value += n(x.m.conversionsValue);
  });
  return Object.keys(agg).map(function (k) { return agg[k]; });
}

function policy() {
  return rows('SELECT campaign.id, campaign.name, ad_group.id, ad_group_ad.ad.id, ad_group_ad.policy_summary.approval_status, ' +
    'ad_group_ad.policy_summary.review_status, ad_group_ad.policy_summary.policy_topic_entries FROM ad_group_ad ' +
    "WHERE ad_group_ad.status != 'REMOVED' AND campaign.status = 'ENABLED' " +
    "AND ad_group_ad.policy_summary.approval_status IN ('DISAPPROVED', 'APPROVED_LIMITED', 'AREA_OF_INTEREST_ONLY')").map(function (r) {
    var p = r.adGroupAd.policySummary || {};
    return { campaign_id: String(r.campaign.id), campaign: r.campaign.name, ad_group_id: String(r.adGroup.id), ad_id: String(r.adGroupAd.ad.id),
      approval: p.approvalStatus || '', review: p.reviewStatus || '',
      topics: (p.policyTopicEntries || []).map(function (t) { return t.topic + (t.type ? ' (' + t.type + ')' : ''); }) };
  });
}

function assets() {
  var map = {};
  rows('SELECT campaign.id, campaign.name, asset.id, asset.name, asset.type, asset.text_asset.text, asset.youtube_video_asset.youtube_video_id, ' +
    'asset.youtube_video_asset.youtube_video_title, asset.image_asset.full_size.url, ad_group_ad_asset_view.field_type, ad_group_ad_asset_view.performance_label, ' +
    'metrics.impressions, metrics.clicks, metrics.cost_micros, metrics.conversions FROM ad_group_ad_asset_view ' +
    "WHERE segments.date DURING LAST_7_DAYS AND campaign.advertising_channel_type = 'MULTI_CHANNEL'").forEach(function (r) {
    var a = r.asset || {}, v = r.adGroupAdAssetView || {};
    var key = r.campaign.id + '|' + a.id + '|' + v.fieldType;
    var name = '', url = '';
    if (a.textAsset && a.textAsset.text) name = a.textAsset.text;
    if (a.youtubeVideoAsset) { name = a.youtubeVideoAsset.youtubeVideoTitle || a.name || 'YouTube video'; if (a.youtubeVideoAsset.youtubeVideoId) url = 'https://youtu.be/' + a.youtubeVideoAsset.youtubeVideoId; }
    if (a.imageAsset && a.imageAsset.fullSize && a.imageAsset.fullSize.url) { url = a.imageAsset.fullSize.url; if (!name) name = a.name || 'Image'; }
    if (!name) name = a.name || String(a.id);
    var x = map[key] || (map[key] = { campaign_id: String(r.campaign.id), campaign: r.campaign.name, asset_id: String(a.id), field_type: v.fieldType || '',
      asset_type: a.type || '', label: v.performanceLabel || '', name: name, url: url, impressions: 0, clicks: 0, cost: 0, conversions: 0 });
    x.impressions += n(r.metrics.impressions); x.clicks += n(r.metrics.clicks); x.cost += micros(r.metrics.costMicros); x.conversions += n(r.metrics.conversions);
  });
  return Object.keys(map).map(function (k) { return map[k]; });
}
