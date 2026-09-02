ALTER TABLE guess_stock_participants
  ADD COLUMN record_deleted TINYINT NOT NULL DEFAULT 0 AFTER final_rank,
  ADD KEY idx_guess_stock_participant_history (player_identity_key, record_deleted, session_id);
