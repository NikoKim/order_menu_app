ALTER TABLE moments
  ADD COLUMN share_count BIGINT NOT NULL DEFAULT 0 AFTER image_urls,
  ADD COLUMN app_share_count BIGINT NOT NULL DEFAULT 0 AFTER share_count,
  ADD COLUMN external_share_count BIGINT NOT NULL DEFAULT 0 AFTER app_share_count;
