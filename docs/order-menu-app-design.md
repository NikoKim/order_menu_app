# 点餐 App 设计文档

## 1. 项目目标

本项目是一个给少量用户使用的轻量点餐 App，不涉及真实充值、支付和资金结算。系统分为顾客端、商家端和后端服务：

- `customer_frontend`：顾客端 Flutter 应用
- `shop_frontend`：商家端 Flutter 应用
- `backend`：Spring Boot 后端，顾客端和商家端共用
- `Mysql`：业务数据库，本地端口 `3307`
- `image/avatar`：用户头像
- `image/shop_pic`：商家图片
- `image/menu_pic/{shopId}`：每个商家一个文件夹，存储该商家的菜品图片

系统采用账号登录，不做第三方登录。顾客注册只需要昵称和密码。商家账号由后台初始化或后续由商家端创建。

## 2. 运行环境

### 2.1 本地开发环境

- MySQL 地址：`127.0.0.1:3307`
- root 用户：`root`
- root 密码：`1714672038qwe`
- App 数据库用户：`order_menu_admin`
- App 数据库用户密码：`1714672038qwe`
- 数据库名：`order_menu`
- 后端建议端口：`8080`
- 静态图片访问前缀：`http://localhost:8080/images`

### 2.2 部署环境

部署环境使用宝塔 Linux 面板：

- 后端 Spring Boot 作为独立服务运行
- MySQL 使用宝塔面板管理
- 图片资源存储在服务器文件夹中
- 通过 Nginx 或 Spring Boot 静态资源映射暴露图片访问地址

建议部署路径：

```text
/www/wwwroot/order_menu_app/
  backend/
  image/
    avatar/
    shop_pic/
    menu_pic/
```

部署环境通过配置文件区分本地和线上，例如：

- `application-dev.yml`：本地开发环境
- `application-prod.yml`：宝塔部署环境

## 3. 数据库初始化

```sql
CREATE DATABASE IF NOT EXISTS order_menu
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'order_menu_admin'@'localhost'
  IDENTIFIED BY '1714672038qwe';

GRANT ALL PRIVILEGES ON order_menu.* TO 'order_menu_admin'@'localhost';
FLUSH PRIVILEGES;
```

## 4. 角色与页面结构

### 4.1 顾客端

已登录时：

| 一级菜单 | 二级菜单 | 三级菜单 |
| --- | --- | --- |
| 首页（商家） | 商家点餐页 | 下单页 |
| 首页（商家） | 商家点餐页 | 菜品详情页 |
| 我的 | 订单 | 每个订单的详情页 |
| 我的 | 修改个人信息 |  |
| 我的 | 留言建议 |  |

未登录时：

| 一级菜单 | 二级菜单 |
| --- | --- |
| 登录 | 登录页 |
| 登录 | 注册页 |

### 4.2 商家端

商家端建议包含以下能力，供后续开发：

- 商家登录
- 商家信息管理
- 菜品分类管理
- 菜品管理
- 订单管理
- 留言建议查看

## 5. 数据库设计

### 5.1 users 用户表

存储顾客和商家账号。顾客注册只需要昵称和密码，头像和手机号等资料后续可补充。

```sql
CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  nickname VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL COMMENT 'CUSTOMER 或 SHOP',
  avatar_url VARCHAR(255) NULL,
  phone VARCHAR(30) NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_nickname_role (nickname, role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.2 shops 商家表

一个商家账号可以对应一个商家店铺。

```sql
CREATE TABLE shops (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(500) NULL,
  cover_image_url VARCHAR(255) NULL,
  address VARCHAR(255) NULL,
  phone VARCHAR(30) NULL,
  business_status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN 或 CLOSED',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1显示 0隐藏',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_shops_owner FOREIGN KEY (owner_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.3 menu_categories 菜品分类表

```sql
CREATE TABLE menu_categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  shop_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_menu_categories_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
  KEY idx_menu_categories_shop (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.4 dishes 菜品表

```sql
CREATE TABLE dishes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  shop_id BIGINT NOT NULL,
  category_id BIGINT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(500) NULL,
  price DECIMAL(10,2) NOT NULL,
  image_url VARCHAR(255) NULL,
  stock INT NULL COMMENT 'NULL 表示不限库存',
  sales_count INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE' COMMENT 'ON_SALE 或 OFF_SALE',
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_dishes_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
  CONSTRAINT fk_dishes_category FOREIGN KEY (category_id) REFERENCES menu_categories(id),
  KEY idx_dishes_shop (shop_id),
  KEY idx_dishes_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.5 orders 订单表

订单不接入真实支付，提交后直接进入待接单或已下单状态。

```sql
CREATE TABLE orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(40) NOT NULL,
  customer_user_id BIGINT NOT NULL,
  shop_id BIGINT NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  remark VARCHAR(500) NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED'
    COMMENT 'SUBMITTED, ACCEPTED, COMPLETED, CANCELLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_orders_order_no (order_no),
  KEY idx_orders_customer (customer_user_id),
  KEY idx_orders_shop (shop_id),
  CONSTRAINT fk_orders_customer FOREIGN KEY (customer_user_id) REFERENCES users(id),
  CONSTRAINT fk_orders_shop FOREIGN KEY (shop_id) REFERENCES shops(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.6 order_items 订单明细表

```sql
CREATE TABLE order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  dish_id BIGINT NOT NULL,
  dish_name VARCHAR(80) NOT NULL,
  dish_image_url VARCHAR(255) NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL,
  subtotal_amount DECIMAL(10,2) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_order_items_dish FOREIGN KEY (dish_id) REFERENCES dishes(id),
  KEY idx_order_items_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.7 feedback 留言建议表

```sql
CREATE TABLE feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  shop_id BIGINT NULL,
  content VARCHAR(1000) NOT NULL,
  reply VARCHAR(1000) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, REPLIED, CLOSED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_feedback_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
  KEY idx_feedback_user (user_id),
  KEY idx_feedback_shop (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.8 upload_files 上传文件记录表

可选表，用于记录图片上传来源，便于后续清理无用图片。

```sql
CREATE TABLE upload_files (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_type VARCHAR(30) NOT NULL COMMENT 'AVATAR, SHOP, DISH',
  owner_id BIGINT NULL,
  file_path VARCHAR(255) NOT NULL,
  url VARCHAR(255) NOT NULL,
  original_name VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_upload_files_owner (owner_type, owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 6. 接口通用约定

### 6.1 基础路径

```text
本地开发：http://localhost:8080/api
部署环境：https://zsj-unknown/order_menu/api
```

图片资源：

```text
本地开发：http://localhost:8080/images/avatar/xxx.png
部署环境：https://zsj-unknown/order_menu/images/avatar/xxx.png
```

### 6.2 返回格式

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

分页返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "page": 1,
    "pageSize": 10,
    "total": 0
  }
}
```

### 6.3 错误码

| code | 含义 |
| --- | --- |
| 0 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未登录或登录过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 数据冲突，例如昵称已存在 |
| 500 | 服务端错误 |

### 6.4 登录认证

登录成功后返回 `token`。Flutter 请求需要在 Header 中携带：

```text
Authorization: Bearer {token}
```

建议后端使用 JWT。Token 中保存：

- `userId`
- `role`
- `nickname`

## 7. 顾客端接口设计

### 7.1 注册

```http
POST /api/auth/customer/register
```

请求：

```json
{
  "nickname": "小明",
  "password": "123456"
}
```

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": 1,
    "nickname": "小明"
  }
}
```

### 7.2 登录

```http
POST /api/auth/login
```

请求：

```json
{
  "nickname": "小明",
  "password": "123456",
  "role": "CUSTOMER"
}
```

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "jwt-token",
    "user": {
      "id": 1,
      "nickname": "小明",
      "role": "CUSTOMER",
      "avatarUrl": null
    }
  }
}
```

### 7.3 获取当前用户信息

```http
GET /api/users/me
```

### 7.4 修改个人信息

```http
PUT /api/users/me
```

请求：

```json
{
  "nickname": "新的昵称",
  "phone": "13800000000",
  "avatarUrl": "/images/avatar/1.png"
}
```

### 7.5 商家列表

```http
GET /api/shops?page=1&pageSize=10&keyword=
```

返回字段：

```json
{
  "id": 1,
  "name": "示例餐厅",
  "description": "家常菜",
  "coverImageUrl": "/images/shop_pic/1.png",
  "businessStatus": "OPEN"
}
```

### 7.6 商家详情

```http
GET /api/shops/{shopId}
```

### 7.7 商家菜单

```http
GET /api/shops/{shopId}/menu
```

返回：

```json
{
  "shop": {
    "id": 1,
    "name": "示例餐厅"
  },
  "categories": [
    {
      "id": 1,
      "name": "热菜",
      "dishes": [
        {
          "id": 1,
          "name": "番茄炒蛋",
          "price": 18.00,
          "imageUrl": "/images/menu_pic/1/tomato-egg.png",
          "status": "ON_SALE"
        }
      ]
    }
  ]
}
```

### 7.8 菜品详情

```http
GET /api/dishes/{dishId}
```

### 7.9 下单

```http
POST /api/orders
```

请求：

```json
{
  "shopId": 1,
  "remark": "少放辣",
  "items": [
    {
      "dishId": 1,
      "quantity": 2
    }
  ]
}
```

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "orderId": 1,
    "orderNo": "202607041030000001",
    "status": "SUBMITTED",
    "totalAmount": 36.00
  }
}
```

### 7.10 我的订单列表

```http
GET /api/orders/my?page=1&pageSize=10&status=
```

### 7.11 订单详情

```http
GET /api/orders/{orderId}
```

### 7.12 取消订单

```http
PUT /api/orders/{orderId}/cancel
```

顾客只能取消自己的订单。建议只有 `SUBMITTED` 状态允许取消。

### 7.13 提交留言建议

```http
POST /api/feedback
```

请求：

```json
{
  "shopId": 1,
  "content": "希望增加米饭选项"
}
```

### 7.14 我的留言建议

```http
GET /api/feedback/my?page=1&pageSize=10
```

## 8. 商家端接口设计

### 8.1 商家登录

```http
POST /api/auth/login
```

请求：

```json
{
  "nickname": "店主",
  "password": "123456",
  "role": "SHOP"
}
```

### 8.2 获取我的店铺

```http
GET /api/shop/me
```

### 8.3 修改我的店铺

```http
PUT /api/shop/me
```

请求：

```json
{
  "name": "示例餐厅",
  "description": "家常菜",
  "coverImageUrl": "/images/shop_pic/1.png",
  "address": "某某路 1 号",
  "phone": "13800000000",
  "businessStatus": "OPEN"
}
```

### 8.4 分类列表

```http
GET /api/shop/categories
```

### 8.5 新增分类

```http
POST /api/shop/categories
```

请求：

```json
{
  "name": "热菜",
  "sortOrder": 1
}
```

### 8.6 修改分类

```http
PUT /api/shop/categories/{categoryId}
```

### 8.7 删除分类

```http
DELETE /api/shop/categories/{categoryId}
```

建议使用软删除或设置 `status = 0`，避免历史菜品和订单丢失关联。

### 8.8 菜品列表

```http
GET /api/shop/dishes?page=1&pageSize=10&categoryId=&keyword=&status=
```

### 8.9 新增菜品

```http
POST /api/shop/dishes
```

请求：

```json
{
  "categoryId": 1,
  "name": "番茄炒蛋",
  "description": "酸甜口",
  "price": 18.00,
  "imageUrl": "/images/menu_pic/1/tomato-egg.png",
  "stock": null,
  "status": "ON_SALE",
  "sortOrder": 1
}
```

### 8.10 修改菜品

```http
PUT /api/shop/dishes/{dishId}
```

### 8.11 上架或下架菜品

```http
PUT /api/shop/dishes/{dishId}/status
```

请求：

```json
{
  "status": "OFF_SALE"
}
```

### 8.12 删除菜品

```http
DELETE /api/shop/dishes/{dishId}
```

建议不物理删除，改为下架或隐藏，避免影响历史订单展示。

### 8.13 商家订单列表

```http
GET /api/shop/orders?page=1&pageSize=10&status=&date=
```

### 8.14 商家订单详情

```http
GET /api/shop/orders/{orderId}
```

### 8.15 接单

```http
PUT /api/shop/orders/{orderId}/accept
```

### 8.16 完成订单

```http
PUT /api/shop/orders/{orderId}/complete
```

### 8.17 商家查看留言建议

```http
GET /api/shop/feedback?page=1&pageSize=10&status=
```

### 8.18 回复留言建议

```http
PUT /api/shop/feedback/{feedbackId}/reply
```

请求：

```json
{
  "reply": "谢谢建议，我们会考虑增加。"
}
```

## 9. 图片上传接口

### 9.1 上传头像

```http
POST /api/upload/avatar
Content-Type: multipart/form-data
```

字段：

```text
file: 图片文件
```

保存位置：

```text
image/avatar/{userId}_{timestamp}.{ext}
```

### 9.2 上传商家图片

```http
POST /api/upload/shop
Content-Type: multipart/form-data
```

保存位置：

```text
image/shop_pic/{shopId}_{timestamp}.{ext}
```

### 9.3 上传菜品图片

```http
POST /api/upload/menu
Content-Type: multipart/form-data
```

字段：

```text
shopId: 商家 ID
file: 图片文件
```

保存位置：

```text
image/menu_pic/{shopId}/{timestamp}_{originalName}
```

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "url": "/images/menu_pic/1/20260704103000_tomato-egg.png"
  }
}
```

## 10. 订单状态流转

```text
SUBMITTED -> ACCEPTED -> COMPLETED
SUBMITTED -> CANCELLED
```

状态含义：

| 状态 | 含义 | 操作方 |
| --- | --- | --- |
| SUBMITTED | 顾客已下单 | 顾客 |
| ACCEPTED | 商家已接单 | 商家 |
| COMPLETED | 商家已完成 | 商家 |
| CANCELLED | 顾客取消 | 顾客 |

## 11. 后端模块建议

```text
backend
  controller
  service
  repository
  entity
  dto
    request
    response
  config
  security
  common
    ApiResponse
    PageResponse
    BusinessException
```

核心模块：

- 用户与认证模块
- 商家模块
- 菜品分类模块
- 菜品模块
- 订单模块
- 留言建议模块
- 图片上传模块

## 12. Flutter 前端约定

### 12.1 顾客端状态

顾客端启动时读取本地 token：

- 有 token：进入首页和我的页面
- 无 token：我的区域显示登录页和注册页

### 12.2 商家端状态

商家端启动时读取本地 token：

- 有 SHOP token：进入商家管理页面
- 无 token：进入商家登录页

### 12.3 网络层

两个 Flutter 项目建议都封装：

- `ApiClient`
- token 自动注入
- 统一错误提示
- 图片 URL 拼接
- 本地开发和部署环境 baseUrl 切换

## 13. 后续开发顺序建议

1. 建立 Spring Boot 项目和 MySQL 连接
2. 创建数据库表和基础实体
3. 实现注册、登录、当前用户接口
4. 实现图片上传和静态资源访问
5. 实现商家、分类、菜品接口
6. 实现顾客端商家列表、菜单、下单、订单接口
7. 实现商家端订单处理接口
8. 实现留言建议接口
9. 开始两个 Flutter 前端页面开发

