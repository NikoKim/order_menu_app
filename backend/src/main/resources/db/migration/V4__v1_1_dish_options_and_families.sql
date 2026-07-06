ALTER TABLE shops
  ADD COLUMN visibility_type VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
  ADD COLUMN family_name VARCHAR(80) NULL,
  ADD COLUMN family_code VARCHAR(6) NULL,
  ADD COLUMN family_join_mode VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  ADD COLUMN family_image_url VARCHAR(255) NULL,
  ADD UNIQUE KEY uk_shops_family_code (family_code),
  ADD KEY idx_shops_visibility_status (visibility_type, status);

CREATE TABLE IF NOT EXISTS dish_option_groups (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dish_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  required TINYINT NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_dish_option_groups_dish_sort (dish_id, sort_order),
  CONSTRAINT fk_dish_option_groups_dish FOREIGN KEY (dish_id) REFERENCES dishes(id),
  CONSTRAINT chk_dish_option_groups_required CHECK (required IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dish_option_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  group_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  price_delta DECIMAL(10,2) NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_dish_option_items_group_sort (group_id, sort_order),
  CONSTRAINT fk_dish_option_items_group FOREIGN KEY (group_id) REFERENCES dish_option_groups(id),
  CONSTRAINT chk_dish_option_items_price_delta CHECK (price_delta >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS family_members (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  shop_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_family_members_shop_user (shop_id, user_id),
  KEY idx_family_members_user_status (user_id, status),
  KEY idx_family_members_shop_status (shop_id, status),
  CONSTRAINT fk_family_members_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
  CONSTRAINT fk_family_members_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT chk_family_members_status CHECK (status IN ('PENDING', 'APPROVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE order_items
  ADD COLUMN selected_options VARCHAR(1000) NULL,
  ADD COLUMN option_amount DECIMAL(10,2) NOT NULL DEFAULT 0;
