-- Older Web publishing uploaded images through an immediate follow-up update,
-- which incorrectly marked a newly published moment as edited.
UPDATE moments
SET edited_at = NULL
WHERE status = 'PUBLISHED'
  AND created_at < '2026-08-24 00:00:00'
  AND image_urls IS NOT NULL
  AND image_urls <> ''
  AND edited_at BETWEEN published_at AND DATE_ADD(published_at, INTERVAL 30 SECOND);
