ALTER TABLE guess_stock_ai_tasks
  DROP INDEX idx_guess_stock_ai_decision_cache,
  DROP COLUMN decision_cache_key,
  DROP COLUMN model_route,
  DROP COLUMN prompt_version,
  DROP COLUMN snapshot_version;
