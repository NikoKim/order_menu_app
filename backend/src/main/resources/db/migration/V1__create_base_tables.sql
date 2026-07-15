USE order_menu;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  nickname VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL,
  avatar_url VARCHAR(255) NULL,
  phone VARCHAR(30) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_nickname (nickname),
  KEY idx_users_role_status (role, status, deleted),
  KEY idx_users_phone (phone),
  CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'SHOP', 'ADMIN')),
  CONSTRAINT chk_users_status CHECK (status IN (0, 1)),
  CONSTRAINT chk_users_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS shops (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(500) NULL,
  cover_image_url VARCHAR(255) NULL,
  address VARCHAR(255) NULL,
  phone VARCHAR(30) NULL,
  business_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  status TINYINT NOT NULL DEFAULT 1,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_shops_owner_user (owner_user_id),
  KEY idx_shops_status_id (status, deleted, id),
  KEY idx_shops_name (name),
  CONSTRAINT fk_shops_owner FOREIGN KEY (owner_user_id) REFERENCES users(id),
  CONSTRAINT chk_shops_business_status CHECK (business_status IN ('OPEN', 'CLOSED')),
  CONSTRAINT chk_shops_status CHECK (status IN (0, 1)),
  CONSTRAINT chk_shops_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS menu_categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  shop_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_menu_categories_shop_name (shop_id, name),
  UNIQUE KEY uk_menu_categories_id_shop (id, shop_id),
  KEY idx_menu_categories_shop_sort (shop_id, status, deleted, sort_order),
  CONSTRAINT fk_menu_categories_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
  CONSTRAINT chk_menu_categories_status CHECK (status IN (0, 1)),
  CONSTRAINT chk_menu_categories_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dishes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  shop_id BIGINT NOT NULL,
  category_id BIGINT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(500) NULL,
  price DECIMAL(10,2) NOT NULL,
  image_url VARCHAR(255) NULL,
  stock INT NULL,
  sales_count INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
  sort_order INT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dishes_shop_name (shop_id, name),
  KEY idx_dishes_shop_status_sort (shop_id, status, deleted, sort_order),
  KEY idx_dishes_shop_category_status_sort (shop_id, category_id, status, deleted, sort_order),
  KEY idx_dishes_category_shop (category_id, shop_id),
  KEY idx_dishes_category (category_id),
  KEY idx_dishes_name (name),
  CONSTRAINT fk_dishes_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
  CONSTRAINT fk_dishes_category FOREIGN KEY (category_id) REFERENCES menu_categories(id),
  CONSTRAINT fk_dishes_category_shop FOREIGN KEY (category_id, shop_id) REFERENCES menu_categories(id, shop_id),
  CONSTRAINT chk_dishes_price CHECK (price >= 0),
  CONSTRAINT chk_dishes_stock CHECK (stock IS NULL OR stock >= 0),
  CONSTRAINT chk_dishes_sales_count CHECK (sales_count >= 0),
  CONSTRAINT chk_dishes_status CHECK (status IN ('ON_SALE', 'OFF_SALE')),
  CONSTRAINT chk_dishes_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(40) NOT NULL,
  customer_user_id BIGINT NOT NULL,
  shop_id BIGINT NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  remark VARCHAR(500) NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_orders_order_no (order_no),
  KEY idx_orders_customer_status_created (customer_user_id, status, deleted, created_at),
  KEY idx_orders_shop_status_created (shop_id, status, deleted, created_at),
  KEY idx_orders_created_at (created_at),
  CONSTRAINT fk_orders_customer FOREIGN KEY (customer_user_id) REFERENCES users(id),
  CONSTRAINT fk_orders_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
  CONSTRAINT chk_orders_total_amount CHECK (total_amount >= 0),
  CONSTRAINT chk_orders_status CHECK (status IN ('SUBMITTED', 'ACCEPTED', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT chk_orders_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  dish_id BIGINT NOT NULL,
  dish_name VARCHAR(80) NOT NULL,
  dish_image_url VARCHAR(255) NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL,
  subtotal_amount DECIMAL(10,2) NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_order_items_order (order_id, deleted),
  KEY idx_order_items_dish (dish_id),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_order_items_dish FOREIGN KEY (dish_id) REFERENCES dishes(id),
  CONSTRAINT chk_order_items_unit_price CHECK (unit_price >= 0),
  CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
  CONSTRAINT chk_order_items_subtotal_amount CHECK (subtotal_amount >= 0),
  CONSTRAINT chk_order_items_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  shop_id BIGINT NULL,
  content VARCHAR(1000) NOT NULL,
  reply VARCHAR(1000) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_feedback_user_created (user_id, deleted, created_at),
  KEY idx_feedback_shop_status_created (shop_id, status, deleted, created_at),
  CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_feedback_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
  CONSTRAINT chk_feedback_status CHECK (status IN ('PENDING', 'REPLIED')),
  CONSTRAINT chk_feedback_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS upload_files (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_type VARCHAR(30) NOT NULL,
  owner_id BIGINT NULL,
  file_path VARCHAR(255) NOT NULL,
  url VARCHAR(255) NOT NULL,
  original_name VARCHAR(255) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_upload_files_file_path (file_path),
  UNIQUE KEY uk_upload_files_url (url),
  KEY idx_upload_files_owner_created (owner_type, owner_id, deleted, created_at),
  CONSTRAINT chk_upload_files_owner_type CHECK (owner_type IN ('AVATAR', 'SHOP', 'DISH')),
  CONSTRAINT chk_upload_files_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
