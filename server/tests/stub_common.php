<?php
/**
 * TEST STUB of mfc_dash/lib/common.php (development environment only). Real server par asli common.php hota hai.
 * DB: env TEST_DB_HOST / TEST_DB_NAME / TEST_DB_USER / TEST_DB_PASS. Login hamesha "logged in" (sirf test).
 */
declare(strict_types=1);
const MFC_ROOT = __DIR__ . '/..';

function cfg(?string $k = null)
{
    $c = ['timezone' => 'Asia/Kolkata', 'cron_key' => 'test-cron', 'admin_password' => 'test', 'inr_per_usd' => 95, 'gads_secret' => 'test'];
    date_default_timezone_set('UTC');
    return $k === null ? $c : ($c[$k] ?? null);
}
function db(): PDO
{
    static $pdo = null;
    if ($pdo === null) {
        $pdo = new PDO('mysql:host=' . getenv('TEST_DB_HOST') . ';dbname=' . getenv('TEST_DB_NAME') . ';charset=utf8mb4', getenv('TEST_DB_USER'), getenv('TEST_DB_PASS'),
            [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC, PDO::ATTR_EMULATE_PREPARES => true]);
        $pdo->exec("SET time_zone = '+00:00'");
    }
    return $pdo;
}
function ensure_gads_account(PDO $pdo): void
{
    $pdo->exec("CREATE TABLE IF NOT EXISTS gads_account (customer_id VARCHAR(20) NOT NULL PRIMARY KEY, name VARCHAR(255) NOT NULL DEFAULT '', currency CHAR(3) NOT NULL DEFAULT 'INR',
        tz VARCHAR(64) NOT NULL DEFAULT '', updated_at DATETIME NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}
function apps(): array { return []; }
function json_out($data, int $code = 200): void
{
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($data, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}
function is_https(): bool { return false; }
function admin_user(): string { return 'test-user'; }
function is_logged_in(): bool { return true; }
function require_login_json(): void {}
function require_cron(): void {}
function tz(): DateTimeZone { return new DateTimeZone((string)cfg('timezone')); }
function local_today(): string { return (new DateTime('now', tz()))->format('Y-m-d'); }
function tz_offset_str(): string { return '+05:30'; }
function day_bounds_utc(string $day): array { $s = new DateTime("$day 00:00:00", tz()); $e = (clone $s)->modify('+1 day'); $u = new DateTimeZone('UTC'); return [$s->setTimezone($u)->format('Y-m-d H:i:s'), $e->setTimezone($u)->format('Y-m-d H:i:s')]; }
function kv_get(string $k): ?string { $st = db()->prepare('SELECT v FROM kv WHERE k = ?'); $st->execute([$k]); $v = $st->fetchColumn(); return $v === false ? null : (string)$v; }
function kv_set(string $k, string $v): void { db()->prepare('REPLACE INTO kv (k, v, updated_at) VALUES (?, ?, UTC_TIMESTAMP())')->execute([$k, $v]); }
function http_json(string $method, string $url, ?string $body = null, array $headers = [], int $timeout = 30): array { return [0, '', 'network disabled in tests']; }
