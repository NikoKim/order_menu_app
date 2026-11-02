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
