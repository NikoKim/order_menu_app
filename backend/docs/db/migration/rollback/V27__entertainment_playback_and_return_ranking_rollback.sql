ALTER TABLE game_sessions
  DROP COLUMN auto_advance,
  DROP COLUMN playback_speed;

UPDATE game_leaderboard_entries
SET score = final_asset
WHERE final_asset IS NOT NULL;
