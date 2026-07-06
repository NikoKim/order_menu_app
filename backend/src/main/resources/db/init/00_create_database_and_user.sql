-- MySQL 8 bootstrap script.
-- Local MySQL listens on 127.0.0.1:3307; pass -P 3307 when running mysql.
CREATE DATABASE IF NOT EXISTS order_menu
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'order_menu_admin'@'localhost'
  IDENTIFIED BY '1714672038qwe';

CREATE USER IF NOT EXISTS 'order_menu_admin'@'127.0.0.1'
  IDENTIFIED BY '1714672038qwe';

GRANT ALL PRIVILEGES ON order_menu.* TO 'order_menu_admin'@'localhost';
GRANT ALL PRIVILEGES ON order_menu.* TO 'order_menu_admin'@'127.0.0.1';

FLUSH PRIVILEGES;
