ALTER TABLE guess_stock_ai_tasks DROP COLUMN error_message, DROP COLUMN decision_reason;
ALTER TABLE guess_stock_rounds DROP COLUMN event_config_index;
ALTER TABLE game_room_members DROP FOREIGN KEY fk_game_room_member_participant;
ALTER TABLE guess_stock_participants
  DROP FOREIGN KEY fk_guess_stock_participant_room_member,
  DROP INDEX idx_guess_stock_participant_identity,
  DROP COLUMN room_member_id,
  DROP COLUMN player_identity_key;
ALTER TABLE game_sessions
  DROP FOREIGN KEY fk_game_session_room,
  DROP INDEX idx_game_session_room,
  DROP COLUMN progression_mode,
  DROP COLUMN room_id;
DROP TABLE IF EXISTS game_ai_credentials;
DROP TABLE IF EXISTS game_room_members;
DROP TABLE IF EXISTS game_rooms;
