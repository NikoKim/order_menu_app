CREATE TABLE entertainment_guests (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id_hash CHAR(64) NOT NULL,
  display_suffix CHAR(4) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  first_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_entertainment_guest_hash (public_id_hash),
  KEY idx_entertainment_guest_seen (last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE games (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  creator_user_id BIGINT NOT NULL,
  mode_code VARCHAR(40) NOT NULL,
  title VARCHAR(50) NOT NULL,
  description VARCHAR(500) NULL,
  cover_url VARCHAR(500) NULL,
  visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  draft_config_json JSON NOT NULL,
  latest_version_no INT NOT NULL DEFAULT 0,
  current_version_id BIGINT NULL,
  play_count BIGINT NOT NULL DEFAULT 0,
  completed_count BIGINT NOT NULL DEFAULT 0,
  published_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_games_public (status, visibility, published_at, id),
  KEY idx_games_creator (creator_user_id, updated_at, id),
  CONSTRAINT fk_games_creator FOREIGN KEY (creator_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE game_versions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  game_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  config_schema_version INT NOT NULL,
  config_json JSON NOT NULL,
  scenario_seed BIGINT NOT NULL,
  published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_game_version (game_id, version_no),
  KEY idx_game_version_published (game_id, published_at),
  CONSTRAINT fk_game_version_game FOREIGN KEY (game_id) REFERENCES games(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE games
  ADD CONSTRAINT fk_games_current_version FOREIGN KEY (current_version_id) REFERENCES game_versions(id);

CREATE TABLE game_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  game_id BIGINT NOT NULL,
  game_version_id BIGINT NOT NULL,
  player_type VARCHAR(20) NOT NULL,
  player_user_id BIGINT NULL,
  guest_id BIGINT NULL,
  player_identity_key VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL,
  current_round INT NOT NULL,
  phase VARCHAR(20) NOT NULL,
  phase_ends_at DATETIME(3) NULL,
  phase_remaining_ms BIGINT NULL,
  activity_lease_until DATETIME(3) NULL,
  paused_at DATETIME(3) NULL,
  result_json JSON NULL,
  row_version BIGINT NOT NULL DEFAULT 0,
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_game_session_player (player_identity_key, status, updated_at),
  KEY idx_game_session_game (game_id, game_version_id, status),
  KEY idx_game_session_due (status, phase_ends_at),
  KEY idx_game_session_lease (status, activity_lease_until),
  CONSTRAINT fk_game_session_game FOREIGN KEY (game_id) REFERENCES games(id),
  CONSTRAINT fk_game_session_version FOREIGN KEY (game_version_id) REFERENCES game_versions(id),
  CONSTRAINT fk_game_session_user FOREIGN KEY (player_user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_game_session_guest FOREIGN KEY (guest_id) REFERENCES entertainment_guests(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE game_session_actions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  round_no INT NOT NULL,
  action_type VARCHAR(30) NOT NULL,
  payload_json JSON NULL,
  client_request_id VARCHAR(80) NOT NULL,
  accepted TINYINT NOT NULL DEFAULT 1,
  reject_reason VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_game_session_action_request (session_id, client_request_id),
  KEY idx_game_session_action_round (session_id, round_no, created_at),
  CONSTRAINT fk_game_session_action_session FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE game_leaderboard_entries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  game_id BIGINT NOT NULL,
  game_version_id BIGINT NOT NULL,
  session_id BIGINT NOT NULL,
  player_identity_key VARCHAR(100) NOT NULL,
  player_type VARCHAR(20) NOT NULL,
  player_user_id BIGINT NULL,
  guest_id BIGINT NULL,
  display_name VARCHAR(100) NOT NULL,
  score DECIMAL(18,6) NOT NULL,
  profit_amount DECIMAL(20,4) NOT NULL,
  final_asset DECIMAL(20,4) NOT NULL,
  completed_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_game_leaderboard_identity (game_version_id, player_identity_key),
  KEY idx_game_leaderboard_rank (game_version_id, score DESC, final_asset DESC, completed_at),
  CONSTRAINT fk_game_leaderboard_game FOREIGN KEY (game_id) REFERENCES games(id),
  CONSTRAINT fk_game_leaderboard_version FOREIGN KEY (game_version_id) REFERENCES game_versions(id),
  CONSTRAINT fk_game_leaderboard_session FOREIGN KEY (session_id) REFERENCES game_sessions(id),
  CONSTRAINT fk_game_leaderboard_user FOREIGN KEY (player_user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_game_leaderboard_guest FOREIGN KEY (guest_id) REFERENCES entertainment_guests(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE guess_stock_rounds (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  round_no INT NOT NULL,
  event_name VARCHAR(50) NULL,
  event_description VARCHAR(500) NULL,
  event_direction VARCHAR(20) NULL,
  event_effective TINYINT NOT NULL DEFAULT 0,
  open_price DECIMAL(20,4) NOT NULL,
  close_price DECIMAL(20,4) NULL,
  execution_price DECIMAL(20,4) NULL,
  base_change_percent DECIMAL(10,4) NOT NULL,
  event_change_percent DECIMAL(10,4) NOT NULL DEFAULT 0,
  order_flow_change_percent DECIMAL(10,4) NULL,
  net_order_amount DECIMAL(20,4) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  cleared_at DATETIME NULL,
  UNIQUE KEY uk_guess_stock_round (session_id, round_no),
  CONSTRAINT fk_guess_stock_round_session FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE guess_stock_participants (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  participant_type VARCHAR(20) NOT NULL,
  player_user_id BIGINT NULL,
  guest_id BIGINT NULL,
  ai_profile VARCHAR(30) NULL,
  display_name VARCHAR(100) NOT NULL,
  cash_balance DECIMAL(20,4) NOT NULL,
  reserved_cash DECIMAL(20,4) NOT NULL DEFAULT 0,
  final_asset DECIMAL(20,4) NULL,
  final_rank INT NULL,
  row_version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_guess_stock_participant_session (session_id, participant_type, id),
  CONSTRAINT fk_guess_stock_participant_session FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE guess_stock_positions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  participant_id BIGINT NOT NULL,
  quantity BIGINT NOT NULL DEFAULT 0,
  reserved_quantity BIGINT NOT NULL DEFAULT 0,
  average_cost DECIMAL(20,4) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_guess_stock_position (session_id, participant_id),
  CONSTRAINT fk_guess_stock_position_session FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_guess_stock_position_participant FOREIGN KEY (participant_id) REFERENCES guess_stock_participants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE guess_stock_orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  participant_id BIGINT NOT NULL,
  round_no INT NOT NULL,
  side VARCHAR(10) NOT NULL,
  quantity BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reserved_amount DECIMAL(20,4) NOT NULL DEFAULT 0,
  client_request_id VARCHAR(80) NOT NULL,
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_guess_stock_order_participant_round (session_id, participant_id, round_no),
  UNIQUE KEY uk_guess_stock_order_request (session_id, client_request_id),
  KEY idx_guess_stock_order_round (session_id, round_no, status),
  CONSTRAINT fk_guess_stock_order_session FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_guess_stock_order_participant FOREIGN KEY (participant_id) REFERENCES guess_stock_participants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE guess_stock_trades (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  participant_id BIGINT NOT NULL,
  round_no INT NOT NULL,
  side VARCHAR(10) NOT NULL,
  quantity BIGINT NOT NULL,
  execution_price DECIMAL(20,4) NOT NULL,
  amount DECIMAL(20,4) NOT NULL,
  source VARCHAR(20) NOT NULL,
  traded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_guess_stock_trade_order (order_id),
  KEY idx_guess_stock_trade_session (session_id, round_no, traded_at),
  CONSTRAINT fk_guess_stock_trade_session FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_guess_stock_trade_order FOREIGN KEY (order_id) REFERENCES guess_stock_orders(id),
  CONSTRAINT fk_guess_stock_trade_participant FOREIGN KEY (participant_id) REFERENCES guess_stock_participants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE guess_stock_ai_tasks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  participant_id BIGINT NOT NULL,
  round_no INT NOT NULL,
  execute_at DATETIME(3) NOT NULL,
  decision_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  idempotency_key VARCHAR(100) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  executed_at DATETIME NULL,
  UNIQUE KEY uk_guess_stock_ai_task (idempotency_key),
  KEY idx_guess_stock_ai_task_due (decision_status, execute_at),
  CONSTRAINT fk_guess_stock_ai_task_session FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_guess_stock_ai_task_participant FOREIGN KEY (participant_id) REFERENCES guess_stock_participants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
