CREATE TABLE IF NOT EXISTS naval_lobby_messages (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 player_key VARCHAR(80) NOT NULL,
 name VARCHAR(100) NOT NULL,
 text VARCHAR(300) NOT NULL,
 created_at BIGINT NOT NULL,
 KEY idx_naval_lobby_sender(player_key,created_at)
);
CREATE TABLE IF NOT EXISTS naval_reviews (
 user_id BIGINT NOT NULL PRIMARY KEY,
 rating INT NOT NULL,
 content VARCHAR(1000) NOT NULL,
 updated_at BIGINT NOT NULL
);
