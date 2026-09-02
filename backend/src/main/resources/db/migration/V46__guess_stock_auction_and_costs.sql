ALTER TABLE guess_stock_trades
  ADD COLUMN fee_amount DECIMAL(18,4) NOT NULL DEFAULT 0 AFTER amount,
  ADD COLUMN slippage_percent DECIMAL(10,4) NOT NULL DEFAULT 0 AFTER fee_amount;
