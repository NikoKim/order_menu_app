ALTER TABLE guess_stock_ai_tasks
  ADD COLUMN snapshot_version VARCHAR(64) NULL AFTER market_snapshot_json,
  ADD COLUMN prompt_version VARCHAR(32) NULL AFTER snapshot_version,
  ADD COLUMN model_route VARCHAR(16) NULL AFTER prompt_version,
  ADD COLUMN decision_cache_key VARCHAR(255) NULL AFTER model_route,
  ADD KEY idx_guess_stock_ai_decision_cache (decision_cache_key, decision_status);
