CREATE TABLE guess_stock_research_accounts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, session_id BIGINT NOT NULL, participant_id BIGINT NOT NULL,
  total_points INT NOT NULL, remaining_points INT NOT NULL,
  UNIQUE KEY uk_guess_stock_research_account (session_id, participant_id),
  CONSTRAINT fk_guess_stock_research_session FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE
);
CREATE TABLE guess_stock_research_actions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, session_id BIGINT NOT NULL, participant_id BIGINT NOT NULL,
  round_no INT NOT NULL, stage VARCHAR(16) NOT NULL, action_type VARCHAR(32) NOT NULL,
  result_json TEXT NOT NULL, request_id VARCHAR(100) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_guess_stock_research_request (session_id, participant_id, request_id),
  CONSTRAINT fk_guess_stock_research_action_session FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE
);
