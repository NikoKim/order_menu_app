ALTER TABLE users ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- Revoke any legacy session for the reserved administrator before granting its role.
DELETE s FROM account_sessions s JOIN users u ON u.id=s.user_id WHERE LOWER(u.nickname)='nikokim';
INSERT INTO users(nickname,password_hash,is_admin)
VALUES('NikoKim','$2a$10$SP5GK7B5a2q46qnjmU2V3..uuTQDgFzQLnIEp9G9Kc8kRmL3QAZm6',TRUE)
ON DUPLICATE KEY UPDATE nickname=VALUES(nickname),password_hash=VALUES(password_hash),is_admin=TRUE;

-- Existing 30-day logins retain the same issuance date with a 90-day lifetime.
UPDATE account_sessions SET expires_at=expires_at+5184000000
WHERE expires_at>CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED);

CREATE TABLE account_notifications (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 sender_id BIGINT NOT NULL,
 title VARCHAR(120) NOT NULL,
 content VARCHAR(4000) NOT NULL,
 coins INT NOT NULL DEFAULT 0,
 created_at BIGINT NOT NULL,
 read_at BIGINT NULL,
 claimed_at BIGINT NULL,
 KEY idx_notification_recipient(user_id,id),
 KEY idx_notification_unread(user_id,read_at),
 FOREIGN KEY(user_id) REFERENCES users(id),
 FOREIGN KEY(sender_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admin_notification_requests (
 admin_id BIGINT NOT NULL,
 request_id CHAR(36) NOT NULL,
 payload_hash CHAR(64) NOT NULL,
 sent_count INT NULL,
 coins INT NOT NULL,
 created_at BIGINT NOT NULL,
 PRIMARY KEY(admin_id,request_id),
 FOREIGN KEY(admin_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
