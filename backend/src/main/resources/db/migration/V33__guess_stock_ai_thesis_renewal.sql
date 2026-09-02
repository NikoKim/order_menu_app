ALTER TABLE guess_stock_ai_theses
  ADD COLUMN renewed_round INT NULL AFTER last_validated_round;

UPDATE guess_stock_ai_theses
SET renewed_round = created_round
WHERE renewed_round IS NULL;

ALTER TABLE guess_stock_ai_theses
  MODIFY COLUMN renewed_round INT NOT NULL;
