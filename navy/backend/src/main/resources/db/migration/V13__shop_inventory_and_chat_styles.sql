CREATE TABLE naval_shop_inventory (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 player_key VARCHAR(100) NOT NULL,
 item_id VARCHAR(32) NOT NULL,
 purchase_request_id CHAR(36) NULL,
 purchased_at BIGINT NOT NULL,
 purchase_day DATE NOT NULL,
 expires_at BIGINT NOT NULL,
 used_at BIGINT NULL,
 KEY idx_shop_owner(player_key,id),
 UNIQUE KEY uk_shop_purchase_request(player_key,purchase_request_id),
 KEY idx_shop_daily(player_key,item_id,purchase_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE naval_shop_equipment (
 player_key VARCHAR(100) NOT NULL PRIMARY KEY,
 inventory_id BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE naval_shop_requests (
 player_key VARCHAR(100) NOT NULL,
 request_id CHAR(36) NOT NULL,
 payload_hash CHAR(64) NOT NULL,
 completed_at BIGINT NULL,
 PRIMARY KEY(player_key,request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE naval_daily_tasks ADD COLUMN task_cycle INT NOT NULL DEFAULT 0;

CREATE TABLE naval_task_reset_rewards (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 player_key VARCHAR(100) NOT NULL,
 task_day DATE NOT NULL,
 task_cycle INT NOT NULL,
 task_id VARCHAR(64) NOT NULL,
 reward_json JSON NOT NULL,
 claimed_at BIGINT NULL,
 UNIQUE KEY uk_reset_task(player_key,task_day,task_cycle,task_id),
 KEY idx_reset_pending(player_key,claimed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE naval_lobby_messages
 ADD COLUMN bubble_style VARCHAR(16) NULL,
 ADD COLUMN bubble_expires_at BIGINT NOT NULL DEFAULT 0;
