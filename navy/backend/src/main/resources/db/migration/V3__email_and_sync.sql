ALTER TABLE users ADD COLUMN email VARCHAR(254) NULL, ADD UNIQUE KEY uk_user_email(email);
ALTER TABLE naval_rooms ADD COLUMN version BIGINT NOT NULL DEFAULT 1,
 ADD COLUMN due_at BIGINT NOT NULL DEFAULT 0, ADD KEY idx_room_due(phase,due_at);
