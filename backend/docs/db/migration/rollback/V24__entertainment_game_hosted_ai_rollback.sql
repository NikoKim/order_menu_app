ALTER TABLE game_ai_credentials
    DROP FOREIGN KEY fk_game_ai_credential_game,
    DROP INDEX uk_game_ai_credential_game,
    DROP COLUMN game_id;
