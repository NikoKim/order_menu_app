CREATE TABLE guess_stock_challenges (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, challenge_date DATE NOT NULL, game_version_id BIGINT NOT NULL,
  rules_version VARCHAR(32) NOT NULL, scenario_seed BIGINT NOT NULL, immutable_key VARCHAR(120) NOT NULL,
  config_json TEXT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_guess_stock_challenge_day (challenge_date), UNIQUE KEY uk_guess_stock_challenge_key (immutable_key)
);
CREATE TABLE guess_stock_challenge_entries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, challenge_id BIGINT NOT NULL, participant_identity_key VARCHAR(120) NOT NULL,
  session_id BIGINT NOT NULL, return_percent DECIMAL(12,4) NOT NULL, max_drawdown_percent DECIMAL(12,4) NOT NULL,
  liquidation_count INT NOT NULL DEFAULT 0, interest_cost DECIMAL(18,4) NOT NULL DEFAULT 0,
  accurate_research_count INT NOT NULL DEFAULT 0, submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_guess_stock_challenge_entry (challenge_id, participant_identity_key),
  CONSTRAINT fk_guess_stock_challenge_entry FOREIGN KEY (challenge_id) REFERENCES guess_stock_challenges(id) ON DELETE CASCADE
);
