ALTER TABLE conversation_members
    ADD COLUMN last_read_message_id BIGINT NULL
    COMMENT '用户在该会话中最后确认已读的消息ID';

CREATE INDEX idx_chat_messages_conversation_id_id
    ON chat_messages (conversation_id, id);

CREATE UNIQUE INDEX uk_chat_message_deletion_user_message
    ON chat_message_deletions (user_id, message_id);
