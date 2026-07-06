# 订单、留言建议、图片上传后端设计

## 1. 设计目标

本文档细化 Spring Boot 后端中订单、留言建议、图片上传三个模块的职责、边界、分层结构、接口细节、表映射、权限规则、事务规则和测试点。

当前项目是轻量点餐 App，不接入真实支付和资金结算。订单提交后直接进入待接单状态，由商家接单并完成；顾客可在商家接单前取消订单。

通用接口约定沿用总设计：

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

认证方式：

```text
Authorization: Bearer {token}
```

JWT 中建议包含：

- `userId`
- `role`
- `nickname`

## 2. 模块职责和边界

### 2.1 订单模块

职责：

- 顾客提交订单。
- 计算订单金额并固化订单明细快照。
- 顾客查询我的订单、订单详情。
- 顾客取消待接单订单。
- 商家查询本店订单、订单详情。
- 商家接单、完成订单。
- 维护订单状态流转和权限校验。

边界：

- 不负责真实支付、退款、配送、优惠券。
- 不依赖前端传入金额，金额统一由后端读取菜品价格计算。
- 订单明细保存菜品名称、图片、单价快照，避免菜品后续修改影响历史订单展示。
- 库存扣减可作为后续增强；当前设计预留库存校验点。

### 2.2 留言建议模块

职责：

- 顾客提交系统或店铺留言建议。
- 顾客查询自己的留言建议。
- 商家查询与本店相关的留言建议。
- 商家回复留言，并维护留言状态。

边界：

- 顾客只能查看自己的留言。
- 商家只能查看和回复自己店铺下的留言。
- `shop_id` 为空的留言表示平台级留言，当前不纳入商家端列表，可后续由后台管理端处理。
- 不做多轮对话，仅保留一条回复内容。

### 2.3 图片上传模块

职责：

- 上传顾客头像。
- 上传商家店铺图片。
- 上传菜品图片。
- 校验文件类型、大小、上传权限。
- 将图片保存到服务器文件夹。
- 返回可访问的静态资源 URL。
- 可选记录上传文件信息，便于后续清理。

边界：

- 不负责图片裁剪、压缩和审核。
- 不直接修改业务表图片字段，推荐由前端拿到 URL 后调用对应资料或菜品更新接口保存；也可以在头像上传接口中直接更新当前用户头像，具体实现时保持一致即可。
- 不保存到数据库 BLOB，只保存文件路径和访问 URL。

## 3. 分层与类建议

### 3.1 Controller

建议按用户端和商家端分开路径，避免权限混乱。

- `OrderController`
  - 顾客订单接口：`POST /api/orders`、`GET /api/orders/my`、`GET /api/orders/{orderId}`、`PUT /api/orders/{orderId}/cancel`
- `ShopOrderController`
  - 商家订单接口：`GET /api/shop/orders`、`GET /api/shop/orders/{orderId}`、`PUT /api/shop/orders/{orderId}/accept`、`PUT /api/shop/orders/{orderId}/complete`
- `FeedbackController`
  - 顾客留言接口：`POST /api/feedback`、`GET /api/feedback/my`
- `ShopFeedbackController`
  - 商家留言接口：`GET /api/shop/feedback`、`PUT /api/shop/feedback/{feedbackId}/reply`
- `UploadController`
  - 图片上传接口：`POST /api/upload/avatar`、`POST /api/upload/shop`、`POST /api/upload/menu`

Controller 职责：

- 接收和校验基础请求参数。
- 从认证上下文中获取当前用户。
- 调用 Service。
- 返回统一响应结构。

### 3.2 Service

- `OrderService`
  - `createOrder(currentUserId, request)`
  - `listMyOrders(currentUserId, query)`
  - `getCustomerOrderDetail(currentUserId, orderId)`
  - `cancelOrder(currentUserId, orderId)`
  - `listShopOrders(shopOwnerUserId, query)`
  - `getShopOrderDetail(shopOwnerUserId, orderId)`
  - `acceptOrder(shopOwnerUserId, orderId)`
  - `completeOrder(shopOwnerUserId, orderId)`
- `FeedbackService`
  - `submitFeedback(currentUserId, request)`
  - `listMyFeedback(currentUserId, query)`
  - `listShopFeedback(shopOwnerUserId, query)`
  - `replyFeedback(shopOwnerUserId, feedbackId, request)`
- `UploadService`
  - `uploadAvatar(currentUserId, file)`
  - `uploadShopImage(shopOwnerUserId, file)`
  - `uploadMenuImage(shopOwnerUserId, shopId, file)`

Service 职责：

- 处理核心业务规则。
- 做权限、状态、金额、路径、文件规则校验。
- 控制事务边界。
- 组装响应 DTO。

### 3.3 Repository

可使用 Spring Data JPA，也可使用 MyBatis/MyBatis-Plus。若采用 JPA，建议：

- `OrderRepository`
  - 按顾客分页查询。
  - 按店铺分页查询。
  - 按 ID 查询并配合权限校验。
  - 接单、完成、取消时可使用带状态条件的更新，避免并发重复操作。
- `OrderItemRepository`
  - 按订单 ID 查询明细。
  - 批量保存订单明细。
- `FeedbackRepository`
  - 按用户分页查询。
  - 按店铺分页查询。
  - 按 ID 查询留言。
- `UploadFileRepository`
  - 保存上传记录。
  - 按 `ownerType + ownerId` 查询历史文件。
- 订单创建还需要读取：
  - `ShopRepository`
  - `DishRepository`
  - `UserRepository`

### 3.4 Entity

订单相关：

- `Order`
- `OrderItem`

留言相关：

- `Feedback`

上传相关：

- `UploadFile`

关联读取：

- `User`
- `Shop`
- `Dish`

枚举建议：

- `UserRole`: `CUSTOMER`, `SHOP`
- `OrderStatus`: `SUBMITTED`, `ACCEPTED`, `COMPLETED`, `CANCELLED`
- `FeedbackStatus`: `PENDING`, `REPLIED`, `CLOSED`
- `UploadOwnerType`: `AVATAR`, `SHOP`, `DISH`
- `BusinessStatus`: `OPEN`, `CLOSED`
- `DishStatus`: `ON_SALE`, `OFF_SALE`

### 3.5 DTO

请求 DTO：

- `CreateOrderRequest`
  - `Long shopId`
  - `String remark`
  - `List<CreateOrderItemRequest> items`
- `CreateOrderItemRequest`
  - `Long dishId`
  - `Integer quantity`
- `CancelOrderRequest`
  - 可选：`String reason`
- `SubmitFeedbackRequest`
  - `Long shopId`
  - `String content`
- `ReplyFeedbackRequest`
  - `String reply`
- `OrderPageQuery`
  - `Integer page`
  - `Integer pageSize`
  - `String status`
- `ShopOrderPageQuery`
  - `Integer page`
  - `Integer pageSize`
  - `String status`
  - `LocalDate date`
- `FeedbackPageQuery`
  - `Integer page`
  - `Integer pageSize`
  - `String status`

响应 DTO：

- `CreateOrderResponse`
  - `Long orderId`
  - `String orderNo`
  - `String status`
  - `BigDecimal totalAmount`
- `OrderListItemResponse`
  - `Long orderId`
  - `String orderNo`
  - `Long shopId`
  - `String shopName`
  - `BigDecimal totalAmount`
  - `String status`
  - `LocalDateTime createdAt`
  - `Integer itemCount`
  - `String firstDishName`
  - `String firstDishImageUrl`
- `OrderDetailResponse`
  - 订单基础信息。
  - 顾客信息摘要。
  - 店铺信息摘要。
  - 明细列表。
- `OrderItemResponse`
  - `Long dishId`
  - `String dishName`
  - `String dishImageUrl`
  - `BigDecimal unitPrice`
  - `Integer quantity`
  - `BigDecimal subtotalAmount`
- `FeedbackResponse`
  - `Long id`
  - `Long shopId`
  - `String shopName`
  - `String content`
  - `String reply`
  - `String status`
  - `LocalDateTime createdAt`
  - `LocalDateTime updatedAt`
- `UploadResponse`
  - `String url`
  - `String fileName`
  - `String ownerType`

## 4. 顾客订单接口细化

### 4.1 顾客下单

```http
POST /api/orders
Authorization: Bearer {customerToken}
Content-Type: application/json
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

校验规则：

- 当前用户必须是 `CUSTOMER`。
- `shopId` 必填，店铺必须存在且 `status = 1`。
- 店铺营业状态建议为 `OPEN` 才允许下单。
- `items` 必填且不能为空。
- `quantity` 必须大于 0，建议单项最大值不超过 99。
- 所有菜品必须属于同一个 `shopId`。
- 菜品必须存在且 `status = ON_SALE`。
- 后端按菜品当前价格计算金额，不接受前端传入金额。
- `remark` 最长 500 字符。

处理流程：

1. 读取当前顾客 ID。
2. 校验店铺存在、展示中、营业中。
3. 批量查询请求中的菜品。
4. 校验菜品归属、状态、数量。
5. 计算每个明细小计：`unitPrice * quantity`。
6. 汇总订单金额：所有小计相加。
7. 生成订单号。
8. 保存 `orders`。
9. 保存 `order_items`，固化菜品名称、图片、单价、小计。
10. 返回订单摘要。

订单号建议：

```text
yyyyMMddHHmmss + 6位随机数或递增序列
示例：202607041030000001
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

错误场景：

- `401` 未登录。
- `403` 非顾客角色。
- `404` 店铺或菜品不存在。
- `409` 店铺已打烊、菜品已下架、菜品不属于该店铺。
- `400` 数量非法、明细为空。

事务：

- 下单必须使用事务。
- `orders` 和 `order_items` 必须同时成功或同时失败。

### 4.2 我的订单

```http
GET /api/orders/my?page=1&pageSize=10&status=
Authorization: Bearer {customerToken}
```

查询规则：

- 当前用户必须是 `CUSTOMER`。
- 只返回当前用户自己的订单。
- `status` 可选，为空时查询全部状态。
- 默认按 `created_at DESC` 排序。
- `page` 默认 1，`pageSize` 默认 10，最大建议 50。

返回字段建议：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "orderId": 1,
        "orderNo": "202607041030000001",
        "shopId": 1,
        "shopName": "示例餐厅",
        "totalAmount": 36.00,
        "status": "SUBMITTED",
        "createdAt": "2026-07-04T10:30:00",
        "itemCount": 2,
        "firstDishName": "番茄炒蛋",
        "firstDishImageUrl": "/images/menu_pic/1/tomato-egg.png"
      }
    ],
    "page": 1,
    "pageSize": 10,
    "total": 1
  }
}
```

### 4.3 订单详情

```http
GET /api/orders/{orderId}
Authorization: Bearer {customerToken}
```

查询规则：

- 当前用户必须是 `CUSTOMER`。
- 只能查看自己的订单。
- 订单不存在或不属于当前用户时，建议统一返回 `404`，避免泄露订单存在性。

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "orderId": 1,
    "orderNo": "202607041030000001",
    "status": "SUBMITTED",
    "totalAmount": 36.00,
    "remark": "少放辣",
    "createdAt": "2026-07-04T10:30:00",
    "updatedAt": "2026-07-04T10:30:00",
    "shop": {
      "id": 1,
      "name": "示例餐厅",
      "phone": "13800000000",
      "address": "某某路 1 号"
    },
    "items": [
      {
        "dishId": 1,
        "dishName": "番茄炒蛋",
        "dishImageUrl": "/images/menu_pic/1/tomato-egg.png",
        "unitPrice": 18.00,
        "quantity": 2,
        "subtotalAmount": 36.00
      }
    ]
  }
}
```

### 4.4 取消订单

```http
PUT /api/orders/{orderId}/cancel
Authorization: Bearer {customerToken}
Content-Type: application/json
```

请求可选：

```json
{
  "reason": "临时有事"
}
```

规则：

- 当前用户必须是 `CUSTOMER`。
- 只能取消自己的订单。
- 只有 `SUBMITTED` 状态允许取消。
- `ACCEPTED`、`COMPLETED`、`CANCELLED` 均不允许取消。

处理流程：

1. 查询订单。
2. 校验订单属于当前顾客。
3. 校验当前状态为 `SUBMITTED`。
4. 更新状态为 `CANCELLED`。
5. 返回最新订单状态。

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "orderId": 1,
    "orderNo": "202607041030000001",
    "status": "CANCELLED"
  }
}
```

并发建议：

- 更新时增加状态条件：`WHERE id = ? AND customer_user_id = ? AND status = 'SUBMITTED'`。
- 影响行数为 0 时返回 `409`，提示订单状态已变化。

## 5. 商家订单接口细化

### 5.1 商家订单列表

```http
GET /api/shop/orders?page=1&pageSize=10&status=&date=
Authorization: Bearer {shopToken}
```

查询规则：

- 当前用户必须是 `SHOP`。
- 根据当前商家用户查询自己的店铺。
- 只返回本店订单。
- `status` 可选，支持 `SUBMITTED`、`ACCEPTED`、`COMPLETED`、`CANCELLED`。
- `date` 可选，格式 `yyyy-MM-dd`，按下单日期过滤。
- 默认按 `created_at DESC` 排序。

返回字段建议：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "orderId": 1,
        "orderNo": "202607041030000001",
        "customerUserId": 2,
        "customerNickname": "小明",
        "totalAmount": 36.00,
        "status": "SUBMITTED",
        "createdAt": "2026-07-04T10:30:00",
        "itemCount": 2,
        "firstDishName": "番茄炒蛋"
      }
    ],
    "page": 1,
    "pageSize": 10,
    "total": 1
  }
}
```

### 5.2 商家订单详情

```http
GET /api/shop/orders/{orderId}
Authorization: Bearer {shopToken}
```

查询规则：

- 当前用户必须是 `SHOP`。
- 当前用户必须是订单所属店铺的 owner。
- 不属于本店的订单返回 `404` 或 `403`；推荐返回 `404`，减少信息泄露。

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "orderId": 1,
    "orderNo": "202607041030000001",
    "status": "SUBMITTED",
    "totalAmount": 36.00,
    "remark": "少放辣",
    "createdAt": "2026-07-04T10:30:00",
    "customer": {
      "id": 2,
      "nickname": "小明",
      "phone": "13800000000"
    },
    "items": [
      {
        "dishId": 1,
        "dishName": "番茄炒蛋",
        "dishImageUrl": "/images/menu_pic/1/tomato-egg.png",
        "unitPrice": 18.00,
        "quantity": 2,
        "subtotalAmount": 36.00
      }
    ]
  }
}
```

### 5.3 接单

```http
PUT /api/shop/orders/{orderId}/accept
Authorization: Bearer {shopToken}
```

规则：

- 当前用户必须是 `SHOP`。
- 订单必须属于当前商家的店铺。
- 只有 `SUBMITTED` 状态允许接单。
- 已取消、已接单、已完成的订单不能再次接单。

处理流程：

1. 查询当前用户拥有的店铺。
2. 查询订单并校验 `shop_id`。
3. 校验状态为 `SUBMITTED`。
4. 更新状态为 `ACCEPTED`。
5. 返回最新状态。

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "orderId": 1,
    "orderNo": "202607041030000001",
    "status": "ACCEPTED"
  }
}
```

并发建议：

- 更新时增加状态条件：`WHERE id = ? AND shop_id = ? AND status = 'SUBMITTED'`。
- 若顾客同时取消，只有一个操作能成功。

### 5.4 完成订单

```http
PUT /api/shop/orders/{orderId}/complete
Authorization: Bearer {shopToken}
```

规则：

- 当前用户必须是 `SHOP`。
- 订单必须属于当前商家的店铺。
- 只有 `ACCEPTED` 状态允许完成。
- `SUBMITTED` 不能直接完成。
- `CANCELLED` 和 `COMPLETED` 不能完成。

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "orderId": 1,
    "orderNo": "202607041030000001",
    "status": "COMPLETED"
  }
}
```

## 6. 留言建议接口细化

### 6.1 顾客提交留言建议

```http
POST /api/feedback
Authorization: Bearer {customerToken}
Content-Type: application/json
```

请求：

```json
{
  "shopId": 1,
  "content": "希望增加米饭选项"
}
```

规则：

- 当前用户必须是 `CUSTOMER`。
- `content` 必填，长度建议 1 到 1000。
- `shopId` 可空。
- `shopId` 不为空时，店铺必须存在且 `status = 1`。
- 新留言状态为 `PENDING`。

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "shopId": 1,
    "content": "希望增加米饭选项",
    "reply": null,
    "status": "PENDING",
    "createdAt": "2026-07-04T10:30:00"
  }
}
```

### 6.2 我的留言建议

```http
GET /api/feedback/my?page=1&pageSize=10
Authorization: Bearer {customerToken}
```

查询规则：

- 当前用户必须是 `CUSTOMER`。
- 只返回当前用户提交的留言。
- 默认按 `created_at DESC` 排序。

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "shopId": 1,
        "shopName": "示例餐厅",
        "content": "希望增加米饭选项",
        "reply": "谢谢建议，我们会考虑增加。",
        "status": "REPLIED",
        "createdAt": "2026-07-04T10:30:00",
        "updatedAt": "2026-07-04T11:00:00"
      }
    ],
    "page": 1,
    "pageSize": 10,
    "total": 1
  }
}
```

### 6.3 商家查看留言建议

```http
GET /api/shop/feedback?page=1&pageSize=10&status=
Authorization: Bearer {shopToken}
```

查询规则：

- 当前用户必须是 `SHOP`。
- 只查询当前商家店铺的留言，即 `feedback.shop_id = currentShop.id`。
- `status` 可选。
- 默认按 `created_at DESC` 排序。
- `shop_id IS NULL` 的平台留言不出现在商家列表。

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "userId": 2,
        "userNickname": "小明",
        "shopId": 1,
        "content": "希望增加米饭选项",
        "reply": null,
        "status": "PENDING",
        "createdAt": "2026-07-04T10:30:00",
        "updatedAt": "2026-07-04T10:30:00"
      }
    ],
    "page": 1,
    "pageSize": 10,
    "total": 1
  }
}
```

### 6.4 商家回复留言建议

```http
PUT /api/shop/feedback/{feedbackId}/reply
Authorization: Bearer {shopToken}
Content-Type: application/json
```

请求：

```json
{
  "reply": "谢谢建议，我们会考虑增加。"
}
```

规则：

- 当前用户必须是 `SHOP`。
- 留言必须属于当前商家店铺。
- `reply` 必填，长度建议 1 到 1000。
- 回复后状态更新为 `REPLIED`。
- 已回复留言允许覆盖回复或禁止重复回复均可。建议允许覆盖，方便商家修正文案。
- 如后续需要关闭留言，可扩展 `PUT /api/shop/feedback/{feedbackId}/close`。

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "reply": "谢谢建议，我们会考虑增加。",
    "status": "REPLIED",
    "updatedAt": "2026-07-04T11:00:00"
  }
}
```

## 7. 图片上传设计

### 7.1 上传头像

```http
POST /api/upload/avatar
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

字段：

```text
file: 图片文件
```

权限：

- 登录用户均可上传自己的头像。

保存路径：

```text
image/avatar/{userId}_{timestamp}.{ext}
```

访问 URL：

```text
/images/avatar/{userId}_{timestamp}.{ext}
```

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "url": "/images/avatar/2_20260704103000.png",
    "fileName": "2_20260704103000.png",
    "ownerType": "AVATAR"
  }
}
```

建议：

- 上传成功后可直接更新 `users.avatar_url`。
- 若选择不直接更新，则由前端调用 `PUT /api/users/me` 保存头像 URL。

### 7.2 上传商家图

```http
POST /api/upload/shop
Authorization: Bearer {shopToken}
Content-Type: multipart/form-data
```

字段：

```text
file: 图片文件
```

权限：

- 当前用户必须是 `SHOP`。
- 当前用户必须已有自己的店铺。

保存路径：

```text
image/shop_pic/{shopId}_{timestamp}.{ext}
```

访问 URL：

```text
/images/shop_pic/{shopId}_{timestamp}.{ext}
```

建议：

- 上传成功后可直接更新 `shops.cover_image_url`。
- 若选择不直接更新，则由前端调用 `PUT /api/shop/me` 保存店铺图 URL。

### 7.3 上传菜品图

```http
POST /api/upload/menu
Authorization: Bearer {shopToken}
Content-Type: multipart/form-data
```

字段：

```text
shopId: 商家 ID
file: 图片文件
```

权限：

- 当前用户必须是 `SHOP`。
- `shopId` 必须是当前用户拥有的店铺 ID。

保存路径：

```text
image/menu_pic/{shopId}/{timestamp}_{safeOriginalName}
```

访问 URL：

```text
/images/menu_pic/{shopId}/{timestamp}_{safeOriginalName}
```

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "url": "/images/menu_pic/1/20260704103000_tomato-egg.png",
    "fileName": "20260704103000_tomato-egg.png",
    "ownerType": "DISH"
  }
}
```

建议：

- 菜品图上传接口只返回 URL，不直接绑定菜品。
- 新增或修改菜品时，将该 URL 写入 `dishes.image_url`。

### 7.4 文件校验

统一规则：

- 仅允许图片类型：`jpg`、`jpeg`、`png`、`webp`。
- MIME 类型建议限制：`image/jpeg`、`image/png`、`image/webp`。
- 单文件大小建议不超过 5MB。
- 原始文件名必须清洗，去除路径分隔符和特殊字符。
- 最终文件名必须避免覆盖，使用时间戳、随机数或 UUID。
- 上传目录不存在时自动创建。

建议不要只依赖文件扩展名，至少同时校验 MIME 类型。若后续安全要求更高，可读取文件头魔数做二次判断。

### 7.5 静态资源映射

本地开发建议：

```yaml
app:
  upload:
    root-path: E:/order_menu_app/image
    url-prefix: /images
```

Spring Boot 静态资源映射建议：

```text
/images/** -> file:E:/order_menu_app/image/
```

部署环境建议：

```yaml
app:
  upload:
    root-path: /www/wwwroot/order_menu_app/image
    url-prefix: /images
```

部署目录：

```text
/www/wwwroot/order_menu_app/
  backend/
  image/
    avatar/
    shop_pic/
    menu_pic/
      {shopId}/
```

生产访问方式二选一：

- Spring Boot 暴露 `/images/**` 静态资源。
- Nginx 暴露 `/order_menu/images/**` 并转发 API 到 Spring Boot。

若部署在子路径 `/order_menu` 下，前端拼接图片地址时建议使用后端返回的相对 URL，再由 `ApiClient` 统一拼接环境域名和上下文路径。

### 7.6 upload_files 记录规则

上传成功后建议写入 `upload_files`：

- 头像：`owner_type = AVATAR`，`owner_id = userId`
- 商家图：`owner_type = SHOP`，`owner_id = shopId`
- 菜品图：`owner_type = DISH`，`owner_id` 可先为空，待菜品保存后可选回填 `dishId`

记录字段：

- `file_path`: 服务器绝对或相对存储路径，建议相对 `root-path` 保存。
- `url`: 前端访问 URL。
- `original_name`: 原始文件名。

## 8. 事务、金额计算、状态流转、权限规则

### 8.1 事务规则

必须加事务：

- 顾客下单：保存订单和明细必须原子提交。
- 顾客取消订单：状态校验和状态更新需要保持一致。
- 商家接单：状态校验和状态更新需要保持一致。
- 商家完成订单：状态校验和状态更新需要保持一致。
- 商家回复留言：回复内容和状态更新需要保持一致。
- 上传文件：文件保存和上传记录写库之间可能不完全原子，建议文件先落盘，再写库；写库失败时尝试删除已保存文件。

只读事务建议：

- 订单列表。
- 订单详情。
- 留言列表。

### 8.2 订单金额计算

金额字段统一使用 `BigDecimal`，数据库使用 `DECIMAL(10,2)`。

计算规则：

```text
order_items.unit_price = dishes.price
order_items.subtotal_amount = unit_price * quantity
orders.total_amount = SUM(order_items.subtotal_amount)
```

注意事项：

- 不使用 `double` 或 `float`。
- 前端不传金额，传了也忽略。
- 下单时固化菜品快照：`dish_name`、`dish_image_url`、`unit_price`。
- 菜品后续改价不影响历史订单。

### 8.3 订单状态流转

状态：

| 状态 | 含义 | 操作方 |
| --- | --- | --- |
| `SUBMITTED` | 顾客已下单，等待商家处理 | 顾客创建 |
| `ACCEPTED` | 商家已接单 | 商家 |
| `COMPLETED` | 商家已完成 | 商家 |
| `CANCELLED` | 顾客已取消 | 顾客 |

允许流转：

```text
SUBMITTED -> ACCEPTED -> COMPLETED
SUBMITTED -> CANCELLED
```

禁止流转：

- `ACCEPTED -> CANCELLED`
- `SUBMITTED -> COMPLETED`
- `COMPLETED -> 任意状态`
- `CANCELLED -> 任意状态`

并发控制：

- 状态更新时使用状态条件更新，避免重复接单、重复完成或取消与接单同时成功。
- 影响行数为 0 时返回 `409`，提示订单状态已变化。

### 8.4 权限规则

顾客：

- 只能创建自己的订单。
- 只能查看自己的订单。
- 只能取消自己的 `SUBMITTED` 订单。
- 只能查看自己的留言。
- 只能提交自己的留言。

商家：

- 只能查看自己店铺的订单。
- 只能操作自己店铺的订单。
- 只能查看自己店铺的留言。
- 只能回复自己店铺的留言。
- 只能上传自己店铺的商家图和菜品图。

通用：

- 未登录返回 `401`。
- 角色不匹配返回 `403`。
- 资源不存在返回 `404`。
- 资源存在但不属于当前用户时，详情类接口建议返回 `404`，操作类接口可返回 `403` 或 `404`，项目内保持统一即可。

## 9. 数据表映射

### 9.1 orders

实体：`Order`

字段映射：

| 表字段 | Java 字段 | 类型建议 | 说明 |
| --- | --- | --- | --- |
| `id` | `id` | `Long` | 主键 |
| `order_no` | `orderNo` | `String` | 订单号，唯一 |
| `customer_user_id` | `customerUserId` | `Long` | 顾客用户 ID |
| `shop_id` | `shopId` | `Long` | 店铺 ID |
| `total_amount` | `totalAmount` | `BigDecimal` | 订单总金额 |
| `remark` | `remark` | `String` | 订单备注 |
| `status` | `status` | `OrderStatus` 或 `String` | 订单状态 |
| `created_at` | `createdAt` | `LocalDateTime` | 创建时间 |
| `updated_at` | `updatedAt` | `LocalDateTime` | 更新时间 |

索引：

- `uk_orders_order_no(order_no)`
- `idx_orders_customer(customer_user_id)`
- `idx_orders_shop(shop_id)`
- 建议补充组合索引：`idx_orders_shop_status_created(shop_id, status, created_at)`
- 建议补充组合索引：`idx_orders_customer_status_created(customer_user_id, status, created_at)`

### 9.2 order_items

实体：`OrderItem`

字段映射：

| 表字段 | Java 字段 | 类型建议 | 说明 |
| --- | --- | --- | --- |
| `id` | `id` | `Long` | 主键 |
| `order_id` | `orderId` | `Long` | 订单 ID |
| `dish_id` | `dishId` | `Long` | 菜品 ID |
| `dish_name` | `dishName` | `String` | 菜品名称快照 |
| `dish_image_url` | `dishImageUrl` | `String` | 菜品图片快照 |
| `unit_price` | `unitPrice` | `BigDecimal` | 下单时单价 |
| `quantity` | `quantity` | `Integer` | 数量 |
| `subtotal_amount` | `subtotalAmount` | `BigDecimal` | 小计 |
| `created_at` | `createdAt` | `LocalDateTime` | 创建时间 |

索引：

- `idx_order_items_order(order_id)`

### 9.3 feedback

实体：`Feedback`

字段映射：

| 表字段 | Java 字段 | 类型建议 | 说明 |
| --- | --- | --- | --- |
| `id` | `id` | `Long` | 主键 |
| `user_id` | `userId` | `Long` | 提交留言的用户 ID |
| `shop_id` | `shopId` | `Long` | 店铺 ID，可空 |
| `content` | `content` | `String` | 留言内容 |
| `reply` | `reply` | `String` | 商家回复 |
| `status` | `status` | `FeedbackStatus` 或 `String` | 留言状态 |
| `created_at` | `createdAt` | `LocalDateTime` | 创建时间 |
| `updated_at` | `updatedAt` | `LocalDateTime` | 更新时间 |

索引：

- `idx_feedback_user(user_id)`
- `idx_feedback_shop(shop_id)`
- 建议补充组合索引：`idx_feedback_shop_status_created(shop_id, status, created_at)`

### 9.4 upload_files

实体：`UploadFile`

字段映射：

| 表字段 | Java 字段 | 类型建议 | 说明 |
| --- | --- | --- | --- |
| `id` | `id` | `Long` | 主键 |
| `owner_type` | `ownerType` | `UploadOwnerType` 或 `String` | `AVATAR`、`SHOP`、`DISH` |
| `owner_id` | `ownerId` | `Long` | 归属业务 ID，可空 |
| `file_path` | `filePath` | `String` | 文件存储路径 |
| `url` | `url` | `String` | 前端访问 URL |
| `original_name` | `originalName` | `String` | 原始文件名 |
| `created_at` | `createdAt` | `LocalDateTime` | 创建时间 |

索引：

- `idx_upload_files_owner(owner_type, owner_id)`

## 10. 后续实现步骤

建议顺序：

1. 创建订单、订单明细、留言、上传文件实体和枚举。
2. 创建 Repository，并补充基础查询方法。
3. 建立统一认证上下文工具，便于 Service 获取 `userId` 和 `role`。
4. 实现 `OrderService.createOrder`，先完成下单主流程。
5. 实现顾客订单列表、详情、取消。
6. 实现商家订单列表、详情、接单、完成。
7. 实现留言提交、我的留言、商家列表、商家回复。
8. 实现上传配置类和静态资源映射。
9. 实现头像、商家图、菜品图上传接口。
10. 增加统一异常处理：参数错误、未登录、无权限、资源不存在、状态冲突。
11. 使用接口测试工具或集成测试覆盖主要流程。
12. 与 Flutter 前端联调，确认分页字段、图片 URL、状态文案映射一致。

## 11. 测试点

### 11.1 订单测试

下单：

- 顾客正常下单成功。
- 未登录下单返回 `401`。
- 商家角色下单返回 `403`。
- 空明细下单返回 `400`。
- 数量为 0 或负数返回 `400`。
- 菜品不存在返回 `404`。
- 菜品下架返回 `409`。
- 菜品不属于店铺返回 `409`。
- 店铺打烊返回 `409`。
- 前端传入金额时后端忽略，最终金额以后端计算为准。

订单查询：

- 顾客只能看到自己的订单。
- 顾客查看他人订单返回 `404`。
- 商家只能看到本店订单。
- 商家查看其他店铺订单返回 `404`。
- 状态筛选、日期筛选、分页排序正确。

状态流转：

- `SUBMITTED` 可取消。
- `ACCEPTED` 不可取消。
- `SUBMITTED` 可接单。
- `CANCELLED` 不可接单。
- `ACCEPTED` 可完成。
- `SUBMITTED` 不可直接完成。
- 重复取消、重复接单、重复完成返回 `409`。
- 并发取消和接单只有一个成功。

金额：

- 多菜品、多数量金额计算正确。
- 历史订单不受菜品改名、改价、改图影响。

### 11.2 留言测试

- 顾客提交店铺留言成功。
- 顾客提交平台留言成功，即 `shopId = null`。
- 内容为空返回 `400`。
- 内容超过 1000 字返回 `400`。
- 顾客只能查看自己的留言。
- 商家只能查看自己店铺留言。
- 商家不能回复其他店铺留言。
- 商家回复后状态变为 `REPLIED`。
- 再次回复时按项目约定覆盖或返回冲突，行为必须稳定。

### 11.3 图片上传测试

- 头像上传成功并返回 `/images/avatar/...`。
- 商家图上传成功并返回 `/images/shop_pic/...`。
- 菜品图上传成功并返回 `/images/menu_pic/{shopId}/...`。
- 非图片文件上传失败。
- 超过大小限制上传失败。
- 未登录上传返回 `401`。
- 顾客上传商家图或菜品图返回 `403`。
- 商家上传其他店铺菜品图返回 `403`。
- 上传后文件实际存在。
- 返回 URL 可以通过浏览器访问。
- `upload_files` 正确记录上传信息。
- 数据库记录失败时，已保存文件能被清理或至少记录错误日志。

### 11.4 部署测试

- `application-dev.yml` 本地路径可写。
- `application-prod.yml` 生产路径可写。
- Linux 部署目录权限正确。
- Spring Boot 或 Nginx 的 `/images/**` 映射可访问。
- Flutter 使用相对图片 URL 拼接后在本地和生产环境均可展示。
