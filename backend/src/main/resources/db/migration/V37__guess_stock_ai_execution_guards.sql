ALTER TABLE guess_stock_ai_decisions
  ADD COLUMN requested_target_position_pct DECIMAL(8,4) NULL AFTER target_position_pct,
  ADD COLUMN step_target_position_pct DECIMAL(8,4) NULL AFTER requested_target_position_pct,
  ADD COLUMN resulting_position_pct DECIMAL(8,4) NULL AFTER step_target_position_pct;
