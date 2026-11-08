ALTER TABLE account_notifications
 ADD COLUMN items_json JSON NULL,
 ADD COLUMN claim_expires_at BIGINT NULL;

UPDATE account_notifications
SET claim_expires_at=created_at+86400000
WHERE coins>0;

ALTER TABLE naval_shop_inventory
 MODIFY COLUMN purchase_day DATE NULL,
 ADD COLUMN acquisition_source VARCHAR(16) NOT NULL DEFAULT 'PURCHASE',
 ADD COLUMN source_notification_id BIGINT NULL,
 ADD COLUMN source_item_index INT NULL,
 ADD UNIQUE KEY uk_shop_notification_item(source_notification_id,source_item_index);
