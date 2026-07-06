# Database setup

This backend uses MySQL 8 and the `order_menu` schema.

## Local connection

- Host: `127.0.0.1`
- Port: `3307`
- Database: `order_menu`
- User: `order_menu_admin`
- Password: `1714672038qwe`
- Upload root: `E:/order_menu_app/image`
- Image folders: `image/avatar`, `image/shop_pic`, `image/menu_pic/{shopId}`

## Script order

Run these commands from the repository root after MySQL is running on port `3307`.

```powershell
mysql -h 127.0.0.1 -P 3307 -u root -p1714672038qwe < backend/src/main/resources/db/init/00_create_database_and_user.sql
mysql -h 127.0.0.1 -P 3307 -u order_menu_admin -p1714672038qwe order_menu < backend/src/main/resources/db/migration/V1__create_base_tables.sql
mysql -h 127.0.0.1 -P 3307 -u order_menu_admin -p1714672038qwe order_menu < backend/src/main/resources/db/seed/dev_seed_data.sql
```

The first script creates the database and grants privileges for both `localhost` and `127.0.0.1`. The application connects to `127.0.0.1:3307` in the dev profile.

## Table coverage

`V1__create_base_tables.sql` creates these tables:

- `users`: customer and shop accounts.
- `shops`: merchant profile and business status.
- `menu_categories`: shop menu categories.
- `dishes`: menu items.
- `orders`: order headers.
- `order_items`: order detail snapshots.
- `feedback`: customer feedback and merchant replies.
- `upload_files`: uploaded avatar, shop and menu image records.

All tables use `utf8mb4`, InnoDB, primary keys, creation time, update time where the row can change, and a reserved `deleted` flag for future soft-delete behavior.

## Production configuration

The production profile reads database values from environment variables:

```text
ORDER_MENU_DB_URL=jdbc:mysql://127.0.0.1:3307/order_menu?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
ORDER_MENU_DB_USERNAME=order_menu_admin
ORDER_MENU_DB_PASSWORD=1714672038qwe
ORDER_MENU_JWT_SECRET=replace-with-at-least-32-random-characters
```

If production MySQL uses a different host or port, change only `ORDER_MENU_DB_URL`.
