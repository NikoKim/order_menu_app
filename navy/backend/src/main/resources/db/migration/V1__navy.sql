CREATE TABLE naval_matchmaking_lock (
  id INT NOT NULL PRIMARY KEY
);
INSERT INTO naval_matchmaking_lock (id) VALUES (1);

CREATE TABLE naval_rooms (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  invite_code CHAR(6) NOT NULL,
  mode VARCHAR(16) NOT NULL,
  auto_match BOOLEAN NOT NULL DEFAULT FALSE,
  phase VARCHAR(16) NOT NULL,
  host_key VARCHAR(80) NOT NULL,
  peer_key VARCHAR(80) NULL,
  deadline BIGINT NOT NULL DEFAULT 0,
  state_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_naval_invite (invite_code),
  KEY idx_naval_match (phase, auto_match, mode, created_at),
  KEY idx_naval_host (host_key, phase),
  KEY idx_naval_peer (peer_key, phase),
  KEY idx_naval_deadline (phase, deadline)
);

CREATE TABLE naval_profiles (
  user_id BIGINT NOT NULL PRIMARY KEY,
  formations_json JSON NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE naval_page_presence (
  page_token CHAR(36) NOT NULL PRIMARY KEY,
  player_key VARCHAR(80) NOT NULL,
  expires_at BIGINT NOT NULL,
  KEY idx_naval_presence_expiry (expires_at),
  KEY idx_naval_presence_player (player_key, expires_at)
);

CREATE TABLE naval_round_results (
  game_id VARCHAR(80) NOT NULL PRIMARY KEY,
  room_id VARCHAR(36) NOT NULL,
  round_no INT NOT NULL,
  player0_key VARCHAR(80) NOT NULL,
  player1_key VARCHAR(80) NOT NULL,
  finished_at BIGINT NOT NULL,
  result_json JSON NOT NULL,
  UNIQUE KEY uk_naval_room_round (room_id, round_no),
  KEY idx_naval_result_player0 (player0_key, finished_at),
  KEY idx_naval_result_player1 (player1_key, finished_at)
);

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

ALTER TABLE naval_lobby_messages
    ADD COLUMN sticker_id VARCHAR(24) NULL,
    ADD COLUMN room_id VARCHAR(36) NULL,
    ADD COLUMN room_round INT NULL;

ALTER TABLE naval_lobby_messages ADD COLUMN battle_json JSON NULL;

CREATE TABLE IF NOT EXISTS naval_medal_profiles (
  player_key VARCHAR(100) NOT NULL PRIMARY KEY,
  public_id CHAR(36) NOT NULL UNIQUE,
  classic_current INT NOT NULL DEFAULT 0,
  classic_best INT NOT NULL DEFAULT 0,
  military_current INT NOT NULL DEFAULT 0,
  military_best INT NOT NULL DEFAULT 0,
  equipped VARCHAR(32) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS naval_medal_rounds (
  game_id VARCHAR(100) NOT NULL,
  player_key VARCHAR(100) NOT NULL,
  PRIMARY KEY(game_id,player_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
