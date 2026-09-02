ALTER TABLE games
  ADD COLUMN draft_version_label VARCHAR(40) NOT NULL DEFAULT '1.0' AFTER draft_config_json,
  ADD COLUMN play_key_required TINYINT NOT NULL DEFAULT 0 AFTER visibility;

ALTER TABLE game_versions
  ADD COLUMN version_label VARCHAR(40) NOT NULL DEFAULT '1.0' AFTER version_no,
  ADD KEY idx_game_version_label (game_id, version_label, published_at);

UPDATE game_versions SET version_label = CONCAT('v', version_no);
UPDATE games g
JOIN game_versions v ON v.id = g.current_version_id
SET g.draft_version_label = v.version_label;

CREATE TABLE game_play_keys (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  game_id BIGINT NOT NULL,
  key_hash CHAR(64) NOT NULL,
  key_hint VARCHAR(24) NOT NULL,
  play_scope VARCHAR(12) NOT NULL DEFAULT 'BOTH',
  usage_mode VARCHAR(12) NOT NULL DEFAULT 'ONCE',
  max_uses INT NULL,
  used_count INT NOT NULL DEFAULT 0,
  status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
  last_used_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_game_play_key_hash (game_id, key_hash),
  KEY idx_game_play_key_status (game_id, status, id),
  CONSTRAINT fk_game_play_key_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE game_play_key_uses (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  play_key_id BIGINT NOT NULL,
  game_id BIGINT NOT NULL,
  player_identity_key VARCHAR(100) NOT NULL,
  play_scope VARCHAR(12) NOT NULL,
  used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_game_play_key_use (play_key_id, used_at),
  CONSTRAINT fk_game_play_key_use_key FOREIGN KEY (play_key_id) REFERENCES game_play_keys(id),
  CONSTRAINT fk_game_play_key_use_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE guess_stock_loan_contracts
  ADD COLUMN original_maturity_round INT NULL AFTER maturity_round,
  ADD COLUMN extension_count INT NOT NULL DEFAULT 0 AFTER original_maturity_round,
  ADD COLUMN total_extended_rounds INT NOT NULL DEFAULT 0 AFTER extension_count,
  ADD COLUMN last_extended_round INT NULL AFTER total_extended_rounds;

UPDATE guess_stock_loan_contracts SET original_maturity_round = maturity_round;
