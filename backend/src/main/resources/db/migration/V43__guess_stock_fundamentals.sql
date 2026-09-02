ALTER TABLE guess_stock_rounds
  ADD COLUMN fundamental_change_percent DECIMAL(10,4) NOT NULL DEFAULT 0 AFTER clamp_adjustment_percent,
  ADD COLUMN report_surprise_percent DECIMAL(10,4) NOT NULL DEFAULT 0 AFTER fundamental_change_percent,
  ADD COLUMN demand_index DECIMAL(10,4) NULL AFTER report_surprise_percent,
  ADD COLUMN order_index DECIMAL(10,4) NULL AFTER demand_index,
  ADD COLUMN inventory_index DECIMAL(10,4) NULL AFTER order_index,
  ADD COLUMN margin_index DECIMAL(10,4) NULL AFTER inventory_index,
  ADD COLUMN cash_flow_index DECIMAL(10,4) NULL AFTER margin_index,
  ADD COLUMN fundamental_score DECIMAL(10,4) NULL AFTER cash_flow_index,
  ADD COLUMN fair_value_index DECIMAL(10,4) NULL AFTER fundamental_score,
  ADD COLUMN financial_risk DECIMAL(10,4) NULL AFTER fair_value_index,
  ADD COLUMN financial_report_published TINYINT NOT NULL DEFAULT 0 AFTER financial_risk;

CREATE TABLE guess_stock_financial_reports (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, session_id BIGINT NOT NULL, round_no INT NOT NULL,
  fundamental_score DECIMAL(10,4) NOT NULL, expected_score DECIMAL(10,4) NOT NULL,
  surprise_percent DECIMAL(10,4) NOT NULL, report_json TEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_guess_stock_report (session_id, round_no),
  CONSTRAINT fk_guess_stock_report_session FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE
);
