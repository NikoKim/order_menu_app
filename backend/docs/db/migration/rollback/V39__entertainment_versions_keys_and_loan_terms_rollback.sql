DROP TABLE IF EXISTS game_play_key_uses;
DROP TABLE IF EXISTS game_play_keys;
ALTER TABLE guess_stock_loan_contracts
  DROP COLUMN last_extended_round,
  DROP COLUMN total_extended_rounds,
  DROP COLUMN extension_count,
  DROP COLUMN original_maturity_round;
ALTER TABLE game_versions DROP INDEX idx_game_version_label, DROP COLUMN version_label;
ALTER TABLE games DROP COLUMN play_key_required, DROP COLUMN draft_version_label;
