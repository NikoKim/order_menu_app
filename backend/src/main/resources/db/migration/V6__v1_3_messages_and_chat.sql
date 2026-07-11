ALTER TABLE users
  ADD COLUMN allow_private_chat TINYINT NOT NULL DEFAULT 1,
  ADD COLUMN auto_join_family_chat TINYINT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS conversations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(20) NOT NULL,
  direct_key VARCHAR(50) NULL,
  shop_id BIGINT NULL,
  owner_user_id BIGINT NULL,
  name VARCHAR(100) NULL,
  cover_image_url VARCHAR(255) NULL,
  announcement VARCHAR(1000) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  last_message_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_conversations_direct_key (direct_key),
  UNIQUE KEY uk_conversations_family_shop (shop_id),
  KEY idx_conversations_type_status_updated (type, status, updated_at),
  CONSTRAINT fk_conversations_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
  CONSTRAINT fk_conversations_owner FOREIGN KEY (owner_user_id) REFERENCES users(id),
  CONSTRAINT chk_conversations_type CHECK (type IN ('DIRECT', 'FAMILY_GROUP')),
  CONSTRAINT chk_conversations_status CHECK (status IN ('ACTIVE', 'DISSOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation_members (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
  member_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  muted TINYINT NOT NULL DEFAULT 0,
  last_read_message_id BIGINT NULL,
  joined_at DATETIME NULL,
  left_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_conversation_members_conversation_user (conversation_id, user_id),
  KEY idx_conversation_members_user_status (user_id, member_status),
  CONSTRAINT fk_conversation_members_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id),
  CONSTRAINT fk_conversation_members_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT chk_conversation_members_role CHECK (role IN ('OWNER', 'MEMBER')),
  CONSTRAINT chk_conversation_members_status CHECK (member_status IN ('PENDING', 'ACTIVE', 'LEFT', 'REMOVED')),
  CONSTRAINT chk_conversation_members_muted CHECK (muted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  sender_user_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
  content VARCHAR(2000) NULL,
  dish_id BIGINT NULL,
  shop_id BIGINT NULL,
  dish_name VARCHAR(80) NULL,
  dish_image_url VARCHAR(255) NULL,
  dish_price DECIMAL(10,2) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_chat_messages_conversation_created (conversation_id, created_at, id),
  KEY idx_chat_messages_sender_created (sender_user_id, created_at),
  CONSTRAINT fk_chat_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id),
  CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_user_id) REFERENCES users(id),
  CONSTRAINT fk_chat_messages_dish FOREIGN KEY (dish_id) REFERENCES dishes(id),
  CONSTRAINT fk_chat_messages_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
  CONSTRAINT chk_chat_messages_type CHECK (type IN ('TEXT', 'DISH_SHARE', 'SYSTEM')),
  CONSTRAINT chk_chat_messages_dish_price CHECK (dish_price IS NULL OR dish_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_notifications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  type VARCHAR(50) NOT NULL,
  title VARCHAR(100) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  payload VARCHAR(2000) NULL,
  read_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_notifications_user_read_created (user_id, read_at, created_at),
  CONSTRAINT fk_user_notifications_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
