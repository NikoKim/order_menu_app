CREATE TABLE naval_page_presence (
  page_token CHAR(36) NOT NULL PRIMARY KEY,
  player_key VARCHAR(80) NOT NULL,
  expires_at BIGINT NOT NULL,
  KEY idx_naval_presence_expiry (expires_at),
  KEY idx_naval_presence_player (player_key, expires_at)
);
