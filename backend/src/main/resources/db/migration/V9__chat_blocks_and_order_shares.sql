CREATE TABLE chat_blocks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  blocker_user_id BIGINT NOT NULL,
  blocked_user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_chat_block_pair (blocker_user_id, blocked_user_id),
  KEY idx_chat_blocks_blocked (blocked_user_id, blocker_user_id)
);

ALTER TABLE chat_messages
  ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'DELIVERED' AFTER content,
  ADD COLUMN delivery_failure_reason VARCHAR(255) NULL AFTER delivery_status,
  ADD COLUMN order_id BIGINT NULL AFTER dish_sales_count,
  ADD COLUMN order_no VARCHAR(50) NULL AFTER order_id,
  ADD COLUMN order_created_at DATETIME NULL AFTER order_no,
  ADD COLUMN order_status VARCHAR(30) NULL AFTER order_created_at,
  ADD COLUMN order_shop_name VARCHAR(100) NULL AFTER order_status,
  ADD COLUMN order_items_json TEXT NULL AFTER order_shop_name,
  ADD COLUMN order_total_amount DECIMAL(10,2) NULL AFTER order_items_json,
  ADD COLUMN order_remark VARCHAR(500) NULL AFTER order_total_amount;
