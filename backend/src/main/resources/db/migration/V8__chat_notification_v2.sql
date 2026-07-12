ALTER TABLE conversations
  ADD COLUMN join_approval_required TINYINT(1) NOT NULL DEFAULT 0 AFTER announcement;

ALTER TABLE chat_messages
  ADD COLUMN shop_name VARCHAR(100) NULL AFTER shop_id,
  ADD COLUMN quoted_message_id BIGINT NULL AFTER dish_sales_count,
  ADD COLUMN quoted_type VARCHAR(20) NULL AFTER quoted_message_id,
  ADD COLUMN quoted_content VARCHAR(500) NULL AFTER quoted_type,
  ADD COLUMN quoted_sender_name VARCHAR(100) NULL AFTER quoted_content,
  ADD COLUMN recalled_at DATETIME NULL AFTER quoted_sender_name;

CREATE TABLE chat_message_deletions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  message_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_chat_message_deletion (message_id, user_id),
  KEY idx_chat_message_deletion_user (user_id, message_id)
);
