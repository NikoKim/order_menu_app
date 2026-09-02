ALTER TABLE game_room_members
  DROP KEY idx_game_room_member_restart,
  DROP COLUMN restart_session_id;
