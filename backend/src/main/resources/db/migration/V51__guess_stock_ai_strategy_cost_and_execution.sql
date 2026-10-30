ALTER TABLE guess_stock_orders
  ADD COLUMN allowed_side VARCHAR(8) NULL,
  ADD COLUMN execution_max_shares BIGINT NULL,
  ADD COLUMN execution_max_notional DECIMAL(24,4) NULL,
  ADD COLUMN execution_min_notional DECIMAL(24,4) NULL,
  ADD COLUMN execution_min_price DECIMAL(24,4) NULL,
  ADD COLUMN execution_max_price DECIMAL(24,4) NULL;

ALTER TABLE guess_stock_ai_decisions
  ADD COLUMN order_id BIGINT NULL,
  ADD COLUMN execution_status VARCHAR(24) NULL,
  ADD COLUMN planned_direction VARCHAR(8) NULL,
  ADD COLUMN planned_shares BIGINT NULL,
  ADD COLUMN planned_value DECIMAL(24,4) NULL,
  ADD KEY idx_ai_decision_order (order_id, execution_status);

ALTER TABLE guess_stock_ai_tasks
  ADD COLUMN prompt_cache_hit_tokens INT NULL,
  ADD COLUMN prompt_cache_miss_tokens INT NULL,
  ADD COLUMN estimated_cost_yuan DECIMAL(14,8) NULL,
  ADD COLUMN strategy_state_json JSON NULL;

CREATE TABLE guess_stock_ai_budgets (
  session_id BIGINT NOT NULL PRIMARY KEY,
  reserved_yuan DECIMAL(14,8) NOT NULL DEFAULT 0,
  spent_yuan DECIMAL(14,8) NOT NULL DEFAULT 0,
  request_count INT NOT NULL DEFAULT 0,
  prompt_tokens BIGINT NOT NULL DEFAULT 0,
  cache_hit_tokens BIGINT NOT NULL DEFAULT 0,
  completion_tokens BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
