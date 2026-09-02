ALTER TABLE guess_stock_ai_tasks
  ADD COLUMN decision_json LONGTEXT NULL AFTER decision_reason;
