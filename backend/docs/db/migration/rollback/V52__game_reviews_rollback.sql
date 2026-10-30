DROP TABLE IF EXISTS game_reviews;
ALTER TABLE game_sessions DROP INDEX idx_game_session_review_player;
ALTER TABLE guess_stock_participants DROP INDEX idx_game_participant_review_user;
