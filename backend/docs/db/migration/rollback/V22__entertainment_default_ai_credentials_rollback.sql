ALTER TABLE game_ai_credentials
  DROP FOREIGN KEY fk_game_ai_credential_default_owner,
  DROP KEY uk_game_ai_credential_default_owner,
  DROP COLUMN default_owner_user_id;
