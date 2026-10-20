<?php
/**
 * Read-only guard: Google Ads monitor ka koi bhi code Google Ads me kuch badal na sake.  php server/tests/gads_readonly_check.php
 *  - setup/gads_health_script.js: sirf AdsApp.currentAccount() aur AdsApp.search() allowed; koi mutate / builder / setter nahi.
 *  - PHP files: Google Ads API (googleads.googleapis.com) ka koi call nahi.
 */
declare(strict_types=1);
$root = dirname(__DIR__);
$fail = 0;
$bad = function (string $m) use (&$fail) { $fail++; echo "FAIL: $m\n"; };

$js = (string)file_get_contents("$root/setup/gads_health_script.js");
$code = preg_replace('#/\*.*?\*/#s', '', $js);                // comments hata kar
$code = preg_replace('#(^|[^:])//[^\n]*#', '$1', $code);
preg_match_all('/\bAdsApp\.(\w+)/', $code, $m);
foreach (array_unique($m[1]) as $call) if (!in_array($call, ['currentAccount', 'search'], true)) $bad("script uses AdsApp.$call (only currentAccount / search allowed)");
preg_match_all('/\b(MccApp|BulkUploads?|AdsManagerApp)\b/', $code, $m2);
foreach (array_unique($m2[1]) as $x) $bad("script uses $x");
foreach (['/\.mutate\w*\s*\(/i', '/\.(pause|enable|remove|applyLabel|removeLabel|setAmount|setBudget|setCpc|setCpa|setTargetCpa|setTargetRoas|setStatus|setName|setEndDate|setStartDate|bidding|newCampaignBuilder|newAdGroupBuilder|newAd|newKeywordBuilder|addLocation|excludeLocation|create\w*|update\w*|upload\w*)\s*\(/i'] as $re)
    if (preg_match_all($re, $code, $mm)) $bad('script has a write-like call: ' . implode(', ', array_unique($mm[0])));
// account methods: sirf getters
preg_match_all('/\bacc\.(\w+)\s*\(/', $code, $m3);
foreach (array_unique($m3[1]) as $x) if (!str_starts_with($x, 'get')) $bad("script calls account method $x()");
// GAQL: sirf SELECT
preg_match_all("/rows\\(\\s*['\"]([A-Z]+)/", $code, $m4);
foreach ($m4[1] as $kw) if ($kw !== 'SELECT') $bad("query starting with $kw");

foreach (['lib/gads_health.php', 'lib/gads_rules.php', 'api/gads_health.php', 'api/gads_ingest.php'] as $f) {
    $s = (string)file_get_contents("$root/$f");
    if (preg_match('/googleads\.googleapis\.com|adwords\.google\.com\/api/i', $s)) $bad("$f calls the Google Ads API");
    if (preg_match('/:mutate|mutateOperations|CampaignService|AdGroupService|BillingSetup/i', $s)) $bad("$f mentions a Google Ads write service");
}
echo $fail ? "\nread-only check FAILED ($fail)\n" : "read-only check passed: script uses only AdsApp.currentAccount() + AdsApp.search(); no Google Ads API calls in PHP\n";
exit($fail ? 1 : 0);
