ALTER TABLE game_ai_credentials
  ADD COLUMN default_owner_user_id BIGINT NULL AFTER session_id,
  ADD UNIQUE KEY uk_game_ai_credential_default_owner (default_owner_user_id),
  ADD CONSTRAINT fk_game_ai_credential_default_owner
    FOREIGN KEY (default_owner_user_id) REFERENCES users(id) ON DELETE CASCADE;
