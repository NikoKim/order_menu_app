ALTER TABLE game_sessions
  ADD COLUMN scenario_seed BIGINT NULL AFTER progression_mode;

ALTER TABLE guess_stock_rounds
  ADD COLUMN opening_base_change_percent DECIMAL(10,4) NOT NULL DEFAULT 0 AFTER base_change_percent,
  ADD COLUMN opening_event_effective TINYINT NULL AFTER event_change_percent,
  ADD COLUMN opening_event_change_percent DECIMAL(10,4) NOT NULL DEFAULT 0 AFTER opening_event_effective,
  ADD COLUMN closing_event_effective TINYINT NULL AFTER opening_event_change_percent,
  ADD COLUMN closing_event_change_percent DECIMAL(10,4) NOT NULL DEFAULT 0 AFTER closing_event_effective,
  ADD COLUMN mean_reversion_change_percent DECIMAL(10,4) NOT NULL DEFAULT 0 AFTER closing_event_change_percent,
  ADD COLUMN forced_sell_quantity BIGINT NOT NULL DEFAULT 0 AFTER closing_sell_quantity;

