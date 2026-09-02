ALTER TABLE guess_stock_rounds
  DROP COLUMN forced_sell_quantity,
  DROP COLUMN mean_reversion_change_percent,
  DROP COLUMN closing_event_change_percent,
  DROP COLUMN closing_event_effective,
  DROP COLUMN opening_event_change_percent,
  DROP COLUMN opening_event_effective,
  DROP COLUMN opening_base_change_percent;

ALTER TABLE game_sessions DROP COLUMN scenario_seed;
