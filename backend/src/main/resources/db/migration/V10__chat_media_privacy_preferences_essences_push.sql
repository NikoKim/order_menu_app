ALTER TABLE users
  ADD COLUMN show_joined_families TINYINT(1) NOT NULL DEFAULT 1 AFTER auto_join_family_chat,
  ADD COLUMN show_phone TINYINT(1) NOT NULL DEFAULT 1 AFTER show_joined_families;

ALTER TABLE conversation_members
  ADD COLUMN hidden_at DATETIME NULL AFTER muted,
  ADD COLUMN pinned_at DATETIME(6) NULL AFTER hidden_at,
  ADD COLUMN last_essence_read_at DATETIME NULL AFTER pinned_at;

ALTER TABLE chat_messages
  ADD COLUMN image_url VARCHAR(500) NULL AFTER order_remark,
  ADD COLUMN image_storage_path VARCHAR(1000) NULL AFTER image_url,
  ADD COLUMN image_expires_at DATETIME NULL AFTER image_storage_path,
  ADD COLUMN shared_shop_id BIGINT NULL AFTER image_expires_at,
  ADD COLUMN shared_shop_name VARCHAR(100) NULL AFTER shared_shop_id,
  ADD COLUMN shared_shop_logo_url VARCHAR(500) NULL AFTER shared_shop_name,
  ADD COLUMN shared_shop_description VARCHAR(1000) NULL AFTER shared_shop_logo_url;

ALTER TABLE chat_messages
  DROP CHECK chk_chat_messages_type,
  ADD CONSTRAINT chk_chat_messages_type
    CHECK (type IN ('TEXT', 'DISH_SHARE', 'ORDER_SHARE', 'IMAGE_SHARE', 'SHOP_SHARE', 'SYSTEM'));

CREATE TABLE message_essences (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  message_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  pinned_at DATETIME(6) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_message_essence_message (message_id),
  KEY idx_message_essence_conversation (conversation_id, pinned_at, created_at),
  CONSTRAINT fk_message_essence_conversation FOREIGN KEY (conversation_id)
    REFERENCES conversations(id) ON DELETE CASCADE,
  CONSTRAINT fk_message_essence_message FOREIGN KEY (message_id)
    REFERENCES chat_messages(id) ON DELETE CASCADE,
  CONSTRAINT fk_message_essence_creator FOREIGN KEY (created_by_user_id)
    REFERENCES users(id)
);

CREATE TABLE message_essence_likes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  essence_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_message_essence_like (essence_id, user_id),
  KEY idx_message_essence_like_user (user_id),
  CONSTRAINT fk_message_essence_like_essence FOREIGN KEY (essence_id)
    REFERENCES message_essences(id) ON DELETE CASCADE,
  CONSTRAINT fk_message_essence_like_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE push_devices (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token VARCHAR(512) NOT NULL,
  platform VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_push_device_token (token),
  KEY idx_push_device_user (user_id),
  CONSTRAINT fk_push_device_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON DELETE CASCADE
);

ALTER TABLE conversations
    ADD COLUMN message_version BIGINT NOT NULL DEFAULT 0 AFTER status;
