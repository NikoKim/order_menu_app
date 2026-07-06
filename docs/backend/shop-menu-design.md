# Spring Boot 后端商家、分类、菜品模块设计

## 1. 设计范围

本文档细化后端中商家、菜品分类、菜品三个模块的职责、接口、权限、数据库映射、图片 URL 规则、后续实现步骤与测试点。模块服务于两个端：

- 顾客端：浏览商家、查看商家详情、查看菜单、查看菜品详情。
- 商家端：维护自己的店铺资料、菜品分类、菜品信息，以及菜品上下架。

本文档不包含订单、支付、用户注册登录、图片上传实现细节，但会约定本模块如何消费登录身份与图片 URL。

## 2. 模块职责和边界

### 2.1 商家模块

职责：

- 管理 `shops` 表中的店铺基础资料。
- 为顾客端提供可见商家列表和商家详情。
- 为商家端提供当前登录商家的店铺资料查询与更新。
- 判断店铺是否可被顾客端展示：`status = 1`。
- 判断店铺营业状态：`business_status = OPEN/CLOSED`。

边界：

- 不负责商家账号注册、登录、密码校验，这些属于用户与认证模块。
- 不直接保存图片文件，只保存图片模块返回的 `cover_image_url`。
- 不负责订单统计，列表中的销量、评分等扩展字段后续可由订单或评价模块聚合。

### 2.2 菜品分类模块

职责：

- 管理 `menu_categories` 表中的分类。
- 分类从属于一个店铺，不能跨店铺复用。
- 顾客端菜单只展示 `status = 1` 的分类。
- 商家端可以新增、修改、删除分类，删除建议采用软删除：`status = 0`。
- 分类排序使用 `sort_order ASC, id ASC`。

边界：

- 分类不负责菜品上下架，只负责组织菜品。
- 删除分类时不物理删除历史数据；若分类下仍有菜品，建议将分类置为隐藏，菜品可保留原 `category_id`。

### 2.3 菜品模块

职责：

- 管理 `dishes` 表中的菜品基础信息、价格、图片、库存、销量、上下架状态。
- 顾客端只展示所属店铺可见且 `status = ON_SALE` 的菜品。
- 商家端可查看自己店铺下全部菜品，包括上架和下架。
- 菜品排序使用 `sort_order ASC, id ASC`。

边界：

- 不负责真实库存扣减事务，订单模块下单时再校验库存。
- 不负责图片上传，只保存图片模块返回的 `image_url`。
- 不物理删除菜品，删除操作建议改为下架或隐藏，避免影响历史订单展示。

## 3. 分层设计建议

### 3.1 Controller

顾客端 Controller：

- `ShopPublicController`
  - `GET /api/shops`
  - `GET /api/shops/{shopId}`
  - `GET /api/shops/{shopId}/menu`
- `DishPublicController`
  - `GET /api/dishes/{dishId}`

商家端 Controller：

- `ShopManageController`
  - `GET /api/shop/me`
  - `PUT /api/shop/me`
- `CategoryManageController`
  - `GET /api/shop/categories`
  - `POST /api/shop/categories`
  - `PUT /api/shop/categories/{categoryId}`
  - `DELETE /api/shop/categories/{categoryId}`
- `DishManageController`
  - `GET /api/shop/dishes`
  - `POST /api/shop/dishes`
  - `PUT /api/shop/dishes/{dishId}`
  - `PUT /api/shop/dishes/{dishId}/status`
  - `DELETE /api/shop/dishes/{dishId}`

### 3.2 Service

- `ShopService`
  - 顾客端商家分页、详情查询。
  - 商家端当前店铺查询与更新。
  - 根据当前用户获取其店铺，并做归属校验。
- `MenuCategoryService`
  - 分类列表、创建、更新、软删除。
  - 校验分类必须属于当前商家的店铺。
- `DishService`
  - 菜品分页、详情、创建、更新、上下架、软删除。
  - 校验菜品必须属于当前商家的店铺。
  - 校验菜品分类必须属于同一店铺。
- `ShopMenuQueryService`
  - 组装顾客端商家菜单：店铺信息、分类、分类下菜品。
  - 只返回顾客可见数据。

### 3.3 Repository

- `ShopRepository`
  - `findByIdAndStatus(Long id, Integer status)`
  - `findByOwnerUserId(Long ownerUserId)`
  - `findByStatus(Integer status, Pageable pageable)`
  - 支持按 `name` 模糊搜索。
- `MenuCategoryRepository`
  - `findByShopIdAndStatusOrderBySortOrderAscIdAsc(Long shopId, Integer status)`
  - `findByShopIdOrderBySortOrderAscIdAsc(Long shopId)`
  - `existsByIdAndShopId(Long id, Long shopId)`
- `DishRepository`
  - `findByShopIdAndStatusOrderBySortOrderAscIdAsc(Long shopId, String status)`
  - `findByShopIdAndCategoryIdAndStatusOrderBySortOrderAscIdAsc(...)`
  - `findByIdAndShopId(Long id, Long shopId)`
  - 支持商家端按 `categoryId`、`keyword`、`status` 组合分页查询。

### 3.4 Entity

- `Shop`
  - 映射 `shops` 表。
  - 关键字段：`id`、`ownerUserId`、`name`、`description`、`coverImageUrl`、`address`、`phone`、`businessStatus`、`status`、`createdAt`、`updatedAt`。
- `MenuCategory`
  - 映射 `menu_categories` 表。
  - 关键字段：`id`、`shopId`、`name`、`sortOrder`、`status`、`createdAt`、`updatedAt`。
- `Dish`
  - 映射 `dishes` 表。
  - 关键字段：`id`、`shopId`、`categoryId`、`name`、`description`、`price`、`imageUrl`、`stock`、`salesCount`、`status`、`sortOrder`、`createdAt`、`updatedAt`。

建议枚举：

- `BusinessStatus`：`OPEN`、`CLOSED`
- `DishStatus`：`ON_SALE`、`OFF_SALE`
- 用户角色沿用认证模块：`CUSTOMER`、`SHOP`

### 3.5 DTO

请求 DTO：

- `ShopUpdateRequest`
- `CategoryCreateRequest`
- `CategoryUpdateRequest`
- `DishCreateRequest`
- `DishUpdateRequest`
- `DishStatusUpdateRequest`
- `ShopQueryRequest`
- `DishManageQueryRequest`

响应 DTO：

- `ShopListResponse`
- `ShopDetailResponse`
- `ShopManageResponse`
- `CategoryResponse`
- `DishListResponse`
- `DishDetailResponse`
- `ShopMenuResponse`
- `ShopMenuCategoryResponse`
- `ShopMenuDishResponse`

## 4. 顾客端接口细化

### 4.1 商家列表

```http
GET /api/shops?page=1&pageSize=10&keyword=
```

用途：顾客端首页展示可见商家。

权限：

- 允许未登录或顾客登录后访问。
- 若项目统一要求登录，也可要求 `CUSTOMER` token，但不应允许商家端 token 用作顾客身份下单。

查询规则：

- 只查询 `shops.status = 1`。
- `keyword` 非空时按店铺名称模糊匹配。
- 默认排序：`id DESC` 或后续增加 `sort_order` 后改为运营排序。

返回字段：

```json
{
  "id": 1,
  "name": "示例餐厅",
  "description": "家常菜",
  "coverImageUrl": "/images/shop_pic/1.png",
  "address": "某某路 1 号",
  "businessStatus": "OPEN"
}
```

### 4.2 商家详情

```http
GET /api/shops/{shopId}
```

用途：进入商家点餐页前查看店铺资料。

查询规则：

- 店铺不存在或 `status != 1` 返回 `404`。
- 返回营业状态，但即使 `businessStatus = CLOSED` 也可以查看详情和菜单；下单由订单模块限制。

返回字段：

```json
{
  "id": 1,
  "name": "示例餐厅",
  "description": "家常菜",
  "coverImageUrl": "/images/shop_pic/1.png",
  "address": "某某路 1 号",
  "phone": "13800000000",
  "businessStatus": "OPEN"
}
```

### 4.3 商家菜单

```http
GET /api/shops/{shopId}/menu
```

用途：顾客端点餐页按分类展示可点菜品。

查询规则：

- 店铺必须存在且 `shops.status = 1`。
- 分类只返回 `menu_categories.status = 1`。
- 菜品只返回 `dishes.status = ON_SALE`。
- 菜品必须属于该店铺。
- 分类和菜品均按 `sort_order ASC, id ASC` 排序。
- 若存在无分类菜品，可放入一个虚拟分类：`id = null`、`name = "其他"`；首版也可以要求商家创建分类后才展示。

返回字段：

```json
{
  "shop": {
    "id": 1,
    "name": "示例餐厅",
    "businessStatus": "OPEN"
  },
  "categories": [
    {
      "id": 1,
      "name": "热菜",
      "sortOrder": 1,
      "dishes": [
        {
          "id": 1,
          "name": "番茄炒蛋",
          "price": 18.00,
          "imageUrl": "/images/menu_pic/1/tomato-egg.png",
          "salesCount": 0,
          "status": "ON_SALE"
        }
      ]
    }
  ]
}
```

### 4.4 菜品详情

```http
GET /api/dishes/{dishId}
```

用途：顾客端菜品详情页展示描述、价格、图片等。

查询规则：

- 菜品必须存在。
- 所属店铺必须 `shops.status = 1`。
- 顾客端只允许查看 `dishes.status = ON_SALE` 的菜品。
- 若菜品下架，返回 `404` 或 `409` 均可；建议返回 `404`，避免顾客端继续展示不可点菜品。

返回字段：

```json
{
  "id": 1,
  "shopId": 1,
  "categoryId": 1,
  "name": "番茄炒蛋",
  "description": "酸甜口味",
  "price": 18.00,
  "imageUrl": "/images/menu_pic/1/tomato-egg.png",
  "stock": null,
  "salesCount": 0,
  "status": "ON_SALE"
}
```

## 5. 商家端接口细化

商家端接口统一要求：

- 请求头必须包含 `Authorization: Bearer {token}`。
- token 中 `role` 必须为 `SHOP`。
- 所有数据操作都基于当前登录用户对应的店铺，不允许通过请求体传入任意 `shopId` 修改其他店铺。

### 5.1 获取我的店铺

```http
GET /api/shop/me
```

规则：

- 根据当前登录用户 `userId` 查询 `shops.owner_user_id`。
- 未找到店铺返回 `404`，由前端提示联系管理员或完成店铺初始化。

返回字段：

```json
{
  "id": 1,
  "name": "示例餐厅",
  "description": "家常菜",
  "coverImageUrl": "/images/shop_pic/1.png",
  "address": "某某路 1 号",
  "phone": "13800000000",
  "businessStatus": "OPEN",
  "status": 1
}
```

### 5.2 修改我的店铺

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

校验：

- `name` 必填，建议不超过 80 字符。
- `description` 不超过 500 字符。
- `businessStatus` 只能是 `OPEN` 或 `CLOSED`。
- `coverImageUrl` 必须是本系统图片路径，建议以 `/images/shop_pic/` 开头。

### 5.3 分类列表

```http
GET /api/shop/categories
```

规则：

- 只查询当前商家店铺下分类。
- 商家端默认返回 `status = 1` 的分类；如后续需要回收站，可增加 `includeDisabled=true`。

### 5.4 新增分类

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

校验：

- `name` 必填，不超过 50 字符。
- `sortOrder` 为空时默认为 `0`。
- 同一店铺内建议不允许存在同名且 `status = 1` 的分类。

### 5.5 修改分类

```http
PUT /api/shop/categories/{categoryId}
```

规则：

- `categoryId` 必须属于当前商家的店铺。
- 可修改 `name`、`sortOrder`。
- 不允许修改 `shopId`。

### 5.6 删除分类

```http
DELETE /api/shop/categories/{categoryId}
```

规则：

- `categoryId` 必须属于当前商家的店铺。
- 执行软删除：`status = 0`。
- 不建议同步删除菜品；分类下菜品在商家端仍可通过全部菜品列表看到，后续商家可重新分配分类。

### 5.7 菜品列表

```http
GET /api/shop/dishes?page=1&pageSize=10&categoryId=&keyword=&status=
```

规则：

- 只查询当前商家的店铺菜品。
- `categoryId` 非空时必须属于当前店铺。
- `keyword` 按菜品名称模糊匹配。
- `status` 可选：`ON_SALE`、`OFF_SALE`。
- 默认排序：`sort_order ASC, id DESC`。

返回字段：

```json
{
  "id": 1,
  "shopId": 1,
  "categoryId": 1,
  "categoryName": "热菜",
  "name": "番茄炒蛋",
  "description": "酸甜口味",
  "price": 18.00,
  "imageUrl": "/images/menu_pic/1/tomato-egg.png",
  "stock": null,
  "salesCount": 0,
  "status": "ON_SALE",
  "sortOrder": 1
}
```

### 5.8 新增菜品

```http
POST /api/shop/dishes
```

请求：

```json
{
  "categoryId": 1,
  "name": "番茄炒蛋",
  "description": "酸甜口味",
  "price": 18.00,
  "imageUrl": "/images/menu_pic/1/tomato-egg.png",
  "stock": null,
  "status": "ON_SALE",
  "sortOrder": 1
}
```

校验：

- `name` 必填，不超过 80 字符。
- `price` 必填，必须大于等于 `0`，保留两位小数。
- `categoryId` 可为空；非空时必须属于当前店铺且分类 `status = 1`。
- `imageUrl` 非空时必须匹配当前店铺目录：`/images/menu_pic/{当前shopId}/...`。
- `stock` 为空表示不限库存；非空时必须大于等于 `0`。
- `status` 为空时默认为 `ON_SALE`。

### 5.9 修改菜品

```http
PUT /api/shop/dishes/{dishId}
```

规则：

- `dishId` 必须属于当前商家的店铺。
- 可修改分类、名称、描述、价格、图片、库存、状态、排序。
- 不允许修改 `shopId`、`salesCount`、创建时间。
- 若更换 `categoryId`，新分类必须属于当前店铺。
- 若更换 `imageUrl`，必须仍位于 `/images/menu_pic/{当前shopId}/` 下。

### 5.10 上下架菜品

```http
PUT /api/shop/dishes/{dishId}/status
```

请求：

```json
{
  "status": "OFF_SALE"
}
```

规则：

- `dishId` 必须属于当前商家的店铺。
- `status` 只能是 `ON_SALE` 或 `OFF_SALE`。
- 下架后顾客端菜单和菜品详情不再返回该菜品。
- 上架时应校验菜品名称、价格等必填字段完整。

### 5.11 删除菜品

```http
DELETE /api/shop/dishes/{dishId}
```

规则：

- `dishId` 必须属于当前商家的店铺。
- 首版建议等同于下架：将 `status` 改为 `OFF_SALE`。
- 如后续需要“隐藏但保留历史”的状态，可扩展 `DishStatus` 增加 `DELETED`。

## 6. 权限规则

### 6.1 顾客端读取权限

- 顾客可读取公开商家列表、公开商家详情、公开菜单、上架菜品详情。
- 顾客不能访问 `/api/shop/**` 商家管理接口。
- 顾客端只读取可见店铺、可见分类、上架菜品。
- 顾客不能通过接口读取下架菜品或隐藏分类。

### 6.2 商家端管理权限

- 商家必须登录，且 token 角色为 `SHOP`。
- 商家只能管理 `shops.owner_user_id = 当前userId` 的店铺。
- 商家只能管理自己店铺下的分类和菜品。
- 商家端接口中即使请求体传入 `shopId`，后端也应忽略或拒绝，统一使用当前用户绑定的店铺 ID。
- 修改菜品分类时，必须校验目标分类属于同一店铺。
- 修改菜品图片时，必须校验图片 URL 位于当前店铺自己的 `menu_pic/{shopId}` 目录。

### 6.3 推荐的权限校验方式

- 在 Controller 层使用认证注解或拦截器保证角色。
- 在 Service 层做数据归属校验，避免绕过 Controller 时产生越权。
- 将“获取当前商家店铺”封装为公共方法，例如 `getCurrentShopOrThrow(currentUserId)`。
- 对分类和菜品的更新、删除、上下架统一使用 `id + shopId` 查询；查不到即返回 `404`，不要暴露其他店铺数据是否存在。

## 7. 数据表映射

### 7.1 shops

| 字段 | Entity 字段 | 说明 |
| --- | --- | --- |
| `id` | `id` | 店铺 ID |
| `owner_user_id` | `ownerUserId` | 店铺所属商家用户 ID |
| `name` | `name` | 店铺名称 |
| `description` | `description` | 店铺介绍 |
| `cover_image_url` | `coverImageUrl` | 店铺封面图 URL |
| `address` | `address` | 地址 |
| `phone` | `phone` | 联系电话 |
| `business_status` | `businessStatus` | `OPEN` 或 `CLOSED` |
| `status` | `status` | `1` 显示，`0` 隐藏 |
| `created_at` | `createdAt` | 创建时间 |
| `updated_at` | `updatedAt` | 更新时间 |

关键索引建议：

- `owner_user_id` 建议唯一或至少建立索引。若业务约定一个商家账号只对应一个店铺，推荐增加唯一索引。
- 顾客端列表常查 `status`，可增加普通索引。

### 7.2 menu_categories

| 字段 | Entity 字段 | 说明 |
| --- | --- | --- |
| `id` | `id` | 分类 ID |
| `shop_id` | `shopId` | 所属店铺 ID |
| `name` | `name` | 分类名称 |
| `sort_order` | `sortOrder` | 排序值 |
| `status` | `status` | `1` 显示，`0` 隐藏 |
| `created_at` | `createdAt` | 创建时间 |
| `updated_at` | `updatedAt` | 更新时间 |

关键索引建议：

- 已有 `idx_menu_categories_shop (shop_id)`。
- 可增加 `(shop_id, status, sort_order)` 优化菜单查询。

### 7.3 dishes

| 字段 | Entity 字段 | 说明 |
| --- | --- | --- |
| `id` | `id` | 菜品 ID |
| `shop_id` | `shopId` | 所属店铺 ID |
| `category_id` | `categoryId` | 所属分类 ID，可为空 |
| `name` | `name` | 菜品名称 |
| `description` | `description` | 菜品描述 |
| `price` | `price` | 价格，`DECIMAL(10,2)` |
| `image_url` | `imageUrl` | 菜品图片 URL |
| `stock` | `stock` | 库存，`NULL` 表示不限库存 |
| `sales_count` | `salesCount` | 销量 |
| `status` | `status` | `ON_SALE` 或 `OFF_SALE` |
| `sort_order` | `sortOrder` | 排序值 |
| `created_at` | `createdAt` | 创建时间 |
| `updated_at` | `updatedAt` | 更新时间 |

关键索引建议：

- 已有 `idx_dishes_shop (shop_id)`。
- 已有 `idx_dishes_category (category_id)`。
- 可增加 `(shop_id, status, sort_order)` 优化顾客端菜单查询。
- 可增加 `(shop_id, category_id, status, sort_order)` 优化分类下菜品查询。

## 8. 图片 URL 存储规则

### 8.1 总体规则

- 数据库只保存可访问 URL，不保存服务器绝对路径。
- URL 统一以 `/images` 开头，前端根据环境拼接域名。
- 商家封面保存到 `shops.cover_image_url`。
- 菜品图片保存到 `dishes.image_url`。
- 图片上传接口负责文件落盘和返回 URL，本模块只校验 URL 是否符合业务归属。

### 8.2 店铺图片

店铺封面 URL 示例：

```text
/images/shop_pic/{shopId}_{timestamp}.{ext}
```

规则：

- 商家只能把自己的店铺封面更新为图片上传接口返回的 URL。
- 后端可校验前缀为 `/images/shop_pic/`。

### 8.3 菜品图片与 menu_pic/{shopId}

菜品图片 URL 示例：

```text
/images/menu_pic/{shopId}/{timestamp}_{originalName}
```

目录关系：

```text
image/
  menu_pic/
    1/
      20260704103000_tomato-egg.png
    2/
      20260704103100_noodle.png
```

规则：

- 每个店铺一个独立菜品图片目录：`menu_pic/{shopId}`。
- `dishes.image_url` 必须保存为 `/images/menu_pic/{shopId}/文件名`。
- 商家新增或修改菜品时，`imageUrl` 中的 `{shopId}` 必须等于当前登录商家自己的店铺 ID。
- 商家不能引用其他店铺目录下的图片，例如当前店铺为 `1` 时，不允许保存 `/images/menu_pic/2/noodle.png`。
- 文件名由上传模块生成，建议避免直接使用原始文件名作为唯一名称，使用时间戳、随机串或 UUID 降低重名风险。

## 9. 错误码建议

沿用统一响应格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

常见错误：

| 场景 | code | HTTP 语义 |
| --- | --- | --- |
| 参数缺失、价格非法、状态非法 | `400` | Bad Request |
| 未登录或 token 过期 | `401` | Unauthorized |
| 角色不是商家却访问商家端接口 | `403` | Forbidden |
| 店铺、分类、菜品不存在，或不属于当前商家 | `404` | Not Found |
| 同店铺分类重名等数据冲突 | `409` | Conflict |
| 服务端异常 | `500` | Internal Server Error |

越权访问建议返回 `404` 或 `403`。对于根据 ID 操作其他店铺资源的场景，推荐返回 `404`，避免暴露资源存在性。

## 10. 后续实现步骤

1. 建立基础 Entity、Repository 和枚举：`Shop`、`MenuCategory`、`Dish`、`BusinessStatus`、`DishStatus`。
2. 建立通用分页 DTO、请求 DTO、响应 DTO，统一字段命名为驼峰。
3. 实现 `ShopService`，先完成顾客端商家列表、商家详情、商家端我的店铺查询与更新。
4. 实现 `MenuCategoryService`，完成分类列表、新增、修改、软删除，并加入店铺归属校验。
5. 实现 `DishService`，完成商家端菜品分页、新增、修改、上下架、删除。
6. 实现 `ShopMenuQueryService`，组装顾客端菜单接口，过滤隐藏分类和下架菜品。
7. 补充图片 URL 校验工具，确保菜品图片只能引用当前店铺的 `menu_pic/{shopId}`。
8. 接入认证上下文，从 JWT 获取当前 `userId` 和 `role`。
9. 为 Controller 增加参数校验、统一异常处理和统一响应包装。
10. 最后与 Flutter 顾客端、商家端联调列表、详情、菜单、上下架刷新效果。

## 11. 测试点

### 11.1 顾客端读取测试

- 商家列表只返回 `status = 1` 的店铺。
- `keyword` 可以正确过滤商家名称。
- 商家详情访问隐藏店铺返回 `404`。
- 商家菜单只返回可见分类和上架菜品。
- 下架菜品不出现在菜单中。
- 顾客端菜品详情访问下架菜品返回 `404`。

### 11.2 商家端权限测试

- 未登录访问 `/api/shop/**` 返回 `401`。
- 顾客角色访问 `/api/shop/**` 返回 `403`。
- 商家 A 修改商家 B 的分类或菜品失败。
- 商家 A 查询菜品列表时不会出现商家 B 的菜品。
- 请求体伪造 `shopId` 不会改变实际归属。

### 11.3 分类管理测试

- 新增分类成功后归属当前商家店铺。
- 修改分类时校验分类归属。
- 删除分类后 `status = 0`，顾客端菜单不再展示。
- 同店铺分类排序稳定，按 `sortOrder` 和 `id` 输出。

### 11.4 菜品管理测试

- 新增菜品时分类必须属于当前店铺。
- 新增菜品时价格不能为负数。
- 修改菜品时不能改到其他店铺分类下。
- 上架、下架状态切换后顾客端菜单即时反映。
- 删除菜品后顾客端不可见，历史订单仍可保留菜品快照。

### 11.5 图片规则测试

- 店铺封面 URL 只接受 `/images/shop_pic/` 前缀。
- 菜品图片 URL 必须是 `/images/menu_pic/{当前shopId}/...`。
- 商家不能保存其他店铺 `menu_pic/{shopId}` 目录下的图片 URL。
- 空图片 URL 可接受，前端显示默认图。

### 11.6 数据一致性测试

- 菜品的 `shop_id` 与分类的 `shop_id` 必须一致。
- 分类软删除后，已有菜品数据不丢失。
- 店铺隐藏后，顾客端商家详情、菜单、菜品详情均不可见。
- `stock = NULL` 表示不限库存，`stock = 0` 表示无库存但是否展示由订单模块或前端提示决定。
