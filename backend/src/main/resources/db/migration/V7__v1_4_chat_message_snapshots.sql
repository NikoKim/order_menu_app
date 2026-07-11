ALTER TABLE chat_messages
  ADD COLUMN dish_description VARCHAR(500) NULL AFTER dish_price,
  ADD COLUMN dish_sales_count INT NULL AFTER dish_description;
