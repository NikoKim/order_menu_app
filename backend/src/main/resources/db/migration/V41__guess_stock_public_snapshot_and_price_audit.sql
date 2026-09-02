ALTER TABLE guess_stock_rounds
  ADD COLUMN raw_total_change_percent DECIMAL(10,4) NOT NULL DEFAULT 0 AFTER noise_change_percent,
  ADD COLUMN clamp_adjustment_percent DECIMAL(10,4) NOT NULL DEFAULT 0 AFTER raw_total_change_percent;
