-- Seeds :links active short links named l000001 .. (matches linkCode() in redirect.js).
-- Inserted directly so seeding is fast and does not go through the create-rate-limiter.
INSERT INTO url_mappings (short_code, original_url, created_at, expires_at, is_active, click_count)
SELECT 'l' || lpad(i::text, 6, '0'),
       'https://example.com/articles/' || i || '?utm_source=loadtest',
       now(),
       now() + interval '90 days',
       true,
       0
FROM generate_series(1, :links) AS i;
