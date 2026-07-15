ALTER TABLE conversations
  ADD COLUMN message_version BIGINT NOT NULL DEFAULT 0 AFTER status;
