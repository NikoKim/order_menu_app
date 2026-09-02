ALTER TABLE game_sessions
  ADD COLUMN playback_speed DECIMAL(3,1) NOT NULL DEFAULT 1.0 AFTER progression_mode,
  ADD COLUMN auto_advance TINYINT NOT NULL DEFAULT 0 AFTER playback_speed;

UPDATE game_leaderboard_entries
SET score = CASE
  WHEN final_asset IS NULL OR profit_amount IS NULL OR final_asset - profit_amount = 0 THEN 0
  ELSE ROUND(profit_amount / (final_asset - profit_amount) * 100, 6)
END;
