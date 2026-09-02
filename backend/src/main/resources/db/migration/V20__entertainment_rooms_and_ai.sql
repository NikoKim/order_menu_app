CREATE TABLE game_rooms (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  game_id BIGINT NOT NULL,
  game_version_id BIGINT NOT NULL,
  session_id BIGINT NULL,
  invite_code CHAR(6) NOT NULL,
  host_identity_key VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'LOBBY',
  max_players INT NOT NULL DEFAULT 8,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at DATETIME NULL,
  completed_at DATETIME NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_game_room_invite (invite_code),
  KEY idx_game_room_game (game_id, status, created_at),
  CONSTRAINT fk_game_room_game FOREIGN KEY (game_id) REFERENCES games(id),
  CONSTRAINT fk_game_room_version FOREIGN KEY (game_version_id) REFERENCES game_versions(id),
  CONSTRAINT fk_game_room_session FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE game_room_members (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id BIGINT NOT NULL,
  participant_id BIGINT NULL,
  player_type VARCHAR(20) NOT NULL,
  player_user_id BIGINT NULL,
  guest_id BIGINT NULL,
  player_identity_key VARCHAR(100) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  member_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  event_ack_round INT NULL,
  ready_round INT NULL,
  joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  left_at DATETIME NULL,
  UNIQUE KEY uk_game_room_member_identity (room_id, player_identity_key),
  KEY idx_game_room_member_active (room_id, status, joined_at),
  CONSTRAINT fk_game_room_member_room FOREIGN KEY (room_id) REFERENCES game_rooms(id) ON DELETE CASCADE,
  CONSTRAINT fk_game_room_member_user FOREIGN KEY (player_user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_game_room_member_guest FOREIGN KEY (guest_id) REFERENCES entertainment_guests(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE game_ai_credentials (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id BIGINT NULL,
  session_id BIGINT NULL,
  player_identity_key VARCHAR(100) NOT NULL,
  base_url VARCHAR(500) NOT NULL,
  model_name VARCHAR(120) NOT NULL,
  api_key_ciphertext TEXT NOT NULL,
  api_key_iv VARCHAR(80) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_game_ai_credential_room_player (room_id, player_identity_key),
  UNIQUE KEY uk_game_ai_credential_session_player (session_id, player_identity_key),
  KEY idx_game_ai_credential_room (room_id, status),
  CONSTRAINT fk_game_ai_credential_room FOREIGN KEY (room_id) REFERENCES game_rooms(id) ON DELETE CASCADE,
  CONSTRAINT fk_game_ai_credential_session FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE game_sessions
  ADD COLUMN room_id BIGINT NULL AFTER game_version_id,
  ADD COLUMN progression_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO' AFTER phase_remaining_ms,
  ADD KEY idx_game_session_room (room_id),
  ADD CONSTRAINT fk_game_session_room FOREIGN KEY (room_id) REFERENCES game_rooms(id) ON DELETE SET NULL;

ALTER TABLE guess_stock_participants
  ADD COLUMN player_identity_key VARCHAR(100) NULL AFTER guest_id,
  ADD COLUMN room_member_id BIGINT NULL AFTER player_identity_key,
  ADD KEY idx_guess_stock_participant_identity (session_id, player_identity_key),
  ADD CONSTRAINT fk_guess_stock_participant_room_member FOREIGN KEY (room_member_id) REFERENCES game_room_members(id) ON DELETE SET NULL;

ALTER TABLE game_room_members
  ADD CONSTRAINT fk_game_room_member_participant FOREIGN KEY (participant_id) REFERENCES guess_stock_participants(id) ON DELETE SET NULL;

ALTER TABLE guess_stock_rounds
  ADD COLUMN event_config_index INT NULL AFTER round_no;

ALTER TABLE guess_stock_ai_tasks
  ADD COLUMN decision_reason VARCHAR(500) NULL AFTER decision_status,
  ADD COLUMN error_message VARCHAR(500) NULL AFTER decision_reason;
