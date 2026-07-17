CREATE TABLE moment_feed_exposures (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  moment_id BIGINT NOT NULL,
  feed_type VARCHAR(20) NOT NULL,
  exposed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_moment_feed_exposure (user_id, moment_id, feed_type),
  KEY idx_moment_feed_exposure_moment (moment_id),
  CONSTRAINT fk_mfe_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_mfe_moment FOREIGN KEY (moment_id) REFERENCES moments(id) ON DELETE CASCADE,
  CONSTRAINT chk_mfe_type CHECK (feed_type IN ('RECOMMENDED', 'RELATED'))
);

-- Existing posts are historical and must not be consumed one-by-one as newly published pins.
INSERT IGNORE INTO moment_feed_exposures (user_id, moment_id, feed_type, exposed_at)
SELECT author_user_id, id, 'RECOMMENDED', COALESCE(published_at, created_at)
FROM moments WHERE status = 'PUBLISHED';
INSERT IGNORE INTO moment_feed_exposures (user_id, moment_id, feed_type, exposed_at)
SELECT author_user_id, id, 'RELATED', COALESCE(published_at, created_at)
FROM moments WHERE status = 'PUBLISHED';

ALTER TABLE user_notifications
  ADD COLUMN dedupe_key VARCHAR(220) NULL AFTER payload,
  ADD UNIQUE KEY uk_user_notification_dedupe (user_id, dedupe_key);

ALTER TABLE chat_messages
  MODIFY COLUMN shared_moment_content LONGTEXT NULL,
  ADD COLUMN shared_moment_preview LONGTEXT NULL AFTER shared_moment_content,
  ADD COLUMN shared_comment_preview LONGTEXT NULL AFTER shared_moment_preview,
  ADD COLUMN shared_moment_author_avatar VARCHAR(500) NULL AFTER shared_moment_author_name,
  ADD COLUMN shared_card_icon VARCHAR(40) NULL AFTER shared_moment_cover_url;
