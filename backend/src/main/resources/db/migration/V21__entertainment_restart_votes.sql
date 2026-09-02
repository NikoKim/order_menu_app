ALTER TABLE game_room_members
  ADD COLUMN restart_session_id BIGINT NULL AFTER ready_round,
  ADD KEY idx_game_room_member_restart (room_id, restart_session_id);
