ALTER TABLE naval_rooms ADD COLUMN version BIGINT NOT NULL DEFAULT 1,
 ADD COLUMN due_at BIGINT NOT NULL DEFAULT 0,
 ADD KEY idx_room_due(phase,due_at);
