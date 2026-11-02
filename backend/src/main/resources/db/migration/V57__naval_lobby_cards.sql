ALTER TABLE naval_lobby_messages
    ADD COLUMN sticker_id VARCHAR(24) NULL,
    ADD COLUMN room_id VARCHAR(36) NULL,
    ADD COLUMN room_round INT NULL;
