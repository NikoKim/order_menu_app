SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS guess_stock_ai_tasks;
DROP TABLE IF EXISTS guess_stock_trades;
DROP TABLE IF EXISTS guess_stock_orders;
DROP TABLE IF EXISTS guess_stock_positions;
DROP TABLE IF EXISTS guess_stock_participants;
DROP TABLE IF EXISTS guess_stock_rounds;
DROP TABLE IF EXISTS game_leaderboard_entries;
DROP TABLE IF EXISTS game_session_actions;
DROP TABLE IF EXISTS game_sessions;
ALTER TABLE games DROP FOREIGN KEY fk_games_current_version;
DROP TABLE IF EXISTS game_versions;
DROP TABLE IF EXISTS games;
DROP TABLE IF EXISTS entertainment_guests;

SET FOREIGN_KEY_CHECKS = 1;
