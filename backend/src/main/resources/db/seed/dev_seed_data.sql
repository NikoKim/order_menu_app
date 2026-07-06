-- BCrypt hash for password: 123456
INSERT INTO users (id, nickname, password_hash, role, status)
VALUES
  (1, 'demo_customer', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiTvqQYXF5XbxP.WE95do5eH4.U4D6K', 'CUSTOMER', 1),
  (2, 'demo_shop', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiTvqQYXF5XbxP.WE95do5eH4.U4D6K', 'SHOP', 1)
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  status = VALUES(status);

INSERT INTO shops (id, owner_user_id, name, description, address, phone, business_status, status)
VALUES (1, 2, 'Demo Cafe', 'Coffee, rice bowls and fried snacks.', '88 Demo Road, Shanghai', '021-88886666', 'OPEN', 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  description = VALUES(description),
  address = VALUES(address),
  phone = VALUES(phone),
  business_status = VALUES(business_status),
  status = VALUES(status);

INSERT INTO menu_categories (id, shop_id, name, sort_order, status)
VALUES
  (1, 1, 'Rice Bowls', 1, 1),
  (2, 1, 'Snacks', 2, 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  sort_order = VALUES(sort_order),
  status = VALUES(status);

INSERT INTO dishes (id, shop_id, category_id, name, description, price, stock, sales_count, status, sort_order)
VALUES
  (1, 1, 1, 'Signature Beef Rice', 'Tender beef with house sauce and soup.', 26.00, NULL, 128, 'ON_SALE', 1),
  (2, 1, 2, 'Fried Chicken Curry Rice', 'Crispy chicken cutlet with warm curry.', 28.00, NULL, 92, 'ON_SALE', 2)
ON DUPLICATE KEY UPDATE
  category_id = VALUES(category_id),
  name = VALUES(name),
  description = VALUES(description),
  price = VALUES(price),
  stock = VALUES(stock),
  sales_count = VALUES(sales_count),
  status = VALUES(status),
  sort_order = VALUES(sort_order);
