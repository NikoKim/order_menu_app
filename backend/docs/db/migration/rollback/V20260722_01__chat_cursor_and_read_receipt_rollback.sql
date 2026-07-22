DROP INDEX idx_chat_messages_conversation_id_id ON chat_messages;
DROP INDEX uk_chat_message_deletion_user_message ON chat_message_deletions;

ALTER TABLE conversation_members
    DROP COLUMN last_read_message_id;
