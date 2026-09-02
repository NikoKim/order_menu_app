ALTER TABLE game_ai_credentials
    ADD COLUMN game_id BIGINT NULL AFTER default_owner_user_id,
    ADD UNIQUE KEY uk_game_ai_credential_game (game_id),
    ADD CONSTRAINT fk_game_ai_credential_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE;
