ALTER TABLE guess_stock_rounds
  ADD COLUMN opening_event_occurred TINYINT NULL AFTER event_change_percent,
  ADD COLUMN opening_event_direction VARCHAR(20) NULL AFTER opening_event_occurred,
  ADD COLUMN closing_event_occurred TINYINT NULL AFTER opening_event_change_percent,
  ADD COLUMN closing_event_direction VARCHAR(20) NULL AFTER closing_event_occurred;

UPDATE guess_stock_rounds
SET opening_event_occurred = CASE WHEN event_stage = 'OPENING' AND event_config_index IS NOT NULL THEN 1 ELSE 0 END,
    opening_event_direction = CASE WHEN event_stage = 'OPENING' THEN event_direction ELSE NULL END,
    closing_event_occurred = CASE
      WHEN event_config_index IS NOT NULL AND (event_stage = 'CLOSING' OR closing_event_effective IS NOT NULL) THEN 1
      ELSE 0
    END,
    closing_event_direction = CASE
      WHEN event_config_index IS NOT NULL AND (event_stage = 'CLOSING' OR closing_event_effective IS NOT NULL) THEN event_direction
      ELSE NULL
    END;
