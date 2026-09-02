ALTER TABLE guess_stock_orders
  ADD COLUMN target_exposure_percent DECIMAL(10,4) NULL AFTER quantity;
