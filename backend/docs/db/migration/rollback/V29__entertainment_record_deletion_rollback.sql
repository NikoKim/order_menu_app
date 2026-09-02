ALTER TABLE guess_stock_participants
  DROP INDEX idx_guess_stock_participant_history,
  DROP COLUMN record_deleted;
