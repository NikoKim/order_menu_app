CREATE TABLE guess_stock_event_chain_states (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, session_id BIGINT NOT NULL, chain_key VARCHAR(80) NOT NULL,
  last_step INT NOT NULL DEFAULT 0, depth INT NOT NULL DEFAULT 0, cooldown_until_round INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_guess_stock_chain (session_id, chain_key),
  CONSTRAINT fk_guess_stock_chain_session FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE
);
