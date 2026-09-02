ALTER TABLE guess_stock_ai_tasks
  ADD COLUMN provider VARCHAR(120) NULL AFTER model_route,
  ADD COLUMN model VARCHAR(120) NULL AFTER provider,
  ADD COLUMN latency_ms BIGINT NULL AFTER model,
  ADD COLUMN prompt_tokens INT NULL AFTER latency_ms,
  ADD COLUMN completion_tokens INT NULL AFTER prompt_tokens,
  ADD COLUMN cache_hit TINYINT NOT NULL DEFAULT 0 AFTER completion_tokens,
  ADD COLUMN fallback_reason VARCHAR(500) NULL AFTER cache_hit,
  ADD COLUMN http_status INT NULL AFTER fallback_reason,
  ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER http_status,
  ADD KEY idx_guess_stock_ai_telemetry (provider, model, created_at);
