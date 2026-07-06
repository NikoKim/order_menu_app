# 认证与用户模块后端设计

## 1. 模块职责和边界

认证与用户模块负责顾客和商家账号的注册、登录、JWT 签发与校验、当前登录用户识别、个人资料维护，以及基于角色的接口访问控制。

模块职责：

- 管理 `users` 表中的账号基础信息，包括昵称、密码摘要、角色、头像、手机号、状态和时间字段。
- 提供顾客注册接口。商家账号不开放普通顾客注册入口，可由后台初始化，或后续由商家端受控创建。
- 提供顾客登录和商家登录能力。两类登录可共用认证服务，但必须校验请求角色。
- 登录成功后签发 JWT，供 Flutter 客户端通过 `Authorization: Bearer {token}` 访问受保护接口。
- 从 JWT 中解析当前用户身份，并在服务端二次校验用户仍存在且启用。
- 提供当前用户查询和个人信息修改接口。
- 为其他业务模块提供当前用户 ID、角色、商家归属校验等基础能力。

模块边界：

- 不负责商家店铺资料维护，店铺信息属于商家模块，认证模块只维护 `users.role = SHOP` 的账号信息。
- 不负责头像文件上传，头像上传属于上传模块；本模块只保存上传后返回的 `avatar_url`。
- 不负责订单、菜品、留言等业务数据权限的完整判断，但提供当前用户身份，供各业务模块继续判断资源归属。
- 不提供第三方登录、短信验证码登录、刷新 token、找回密码等能力；这些可作为后续扩展。

## 2. 包结构建议

建议沿用总设计中的后端分层结构：

```text
backend
  controller
    AuthController
    UserController
  service
    AuthService
    UserService
  repository
    UserRepository
  entity
    User
    UserRole
    UserStatus
  dto
    request
      CustomerRegisterRequest
      LoginRequest
      UpdateMeRequest
    response
      RegisterResponse
      LoginResponse
      CurrentUserResponse
  security
    JwtTokenProvider
    JwtAuthenticationFilter
    CurrentUser
    CurrentUserHolder
  common
    ApiResponse
    BusinessException
    ErrorCode
    GlobalExceptionHandler
```

## 3. 分层设计

### 3.1 Controller

`AuthController`：

- `POST /api/auth/customer/register`：顾客注册。
- `POST /api/auth/login`：顾客或商家登录，根据 `role` 区分。

`UserController`：

- `GET /api/users/me`：获取当前登录用户。
- `PUT /api/users/me`：修改当前用户基础资料。

Controller 只做请求接收、参数校验注解、调用 Service、包装统一返回，不直接写认证逻辑和数据库逻辑。

### 3.2 Service

`AuthService`：

- `registerCustomer(CustomerRegisterRequest request)`：创建顾客账号。
- `login(LoginRequest request)`：校验账号、密码、角色、状态，签发 JWT。
- `buildLoginResponse(User user)`：组装 token 和用户信息。

`UserService`：

- `getCurrentUser(Long userId)`：查询当前用户信息。
- `updateCurrentUser(Long userId, UpdateMeRequest request)`：修改昵称、手机号、头像。
- `checkNicknameAvailable(String nickname, UserRole role, Long excludeUserId)`：同角色下昵称唯一校验。

### 3.3 Repository

`UserRepository` 建议提供：

- `Optional<User> findById(Long id)`
- `Optional<User> findByNicknameAndRole(String nickname, UserRole role)`
- `boolean existsByNicknameAndRole(String nickname, UserRole role)`
- `boolean existsByNicknameAndRoleAndIdNot(String nickname, UserRole role, Long id)`
- `User save(User user)`

如果使用 Spring Data JPA，可直接通过方法名派生查询；如果使用 MyBatis，则在 Mapper 中实现等价 SQL。

### 3.4 Entity

`User` 对应 `users` 表：

- `id: Long`
- `nickname: String`
- `passwordHash: String`
- `role: UserRole`
- `avatarUrl: String`
- `phone: String`
- `status: Integer` 或 `UserStatus`
- `createdAt: LocalDateTime`
- `updatedAt: LocalDateTime`

枚举建议：

- `UserRole`：`CUSTOMER`、`SHOP`
- `UserStatus`：`ENABLED(1)`、`DISABLED(0)`

### 3.5 DTO

请求 DTO：

- `CustomerRegisterRequest`
  - `nickname`
  - `password`
- `LoginRequest`
  - `nickname`
  - `password`
  - `role`
- `UpdateMeRequest`
  - `nickname`
  - `phone`
  - `avatarUrl`

响应 DTO：

- `RegisterResponse`
  - `userId`
  - `nickname`
- `LoginResponse`
  - `token`
  - `user`
- `CurrentUserResponse`
  - `id`
  - `nickname`
  - `role`
  - `avatarUrl`
  - `phone`

登录响应中的 `user` 建议复用 `CurrentUserResponse` 的轻量字段，不返回 `passwordHash`、`status` 等内部字段。

## 4. 接口细化

所有接口统一使用总设计中的返回格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

### 4.1 顾客注册

```http
POST /api/auth/customer/register
Content-Type: application/json
```

请求：

```json
{
  "nickname": "小明",
  "password": "123456"
}
```

处理流程：

1. 校验 `nickname` 和 `password`。
2. 固定注册角色为 `CUSTOMER`，不允许前端传入角色覆盖。
3. 检查 `users` 表中是否已存在相同 `nickname + CUSTOMER`。
4. 使用 BCrypt 加密密码，写入 `password_hash`。
5. 新用户默认 `status = 1`，`avatar_url = null`，`phone = null`。
6. 返回新用户 ID 和昵称。

响应：

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

错误：

- `400`：昵称或密码格式不合法。
- `409`：同角色下昵称已存在。

### 4.2 商家登录

```http
POST /api/auth/login
Content-Type: application/json
```

请求：

```json
{
  "nickname": "店主",
  "password": "123456",
  "role": "SHOP"
}
```

处理流程：

1. 校验 `nickname`、`password`、`role`。
2. 按 `nickname + SHOP` 查询账号。
3. 账号不存在时返回统一登录失败提示，避免暴露账号是否存在。
4. 使用 BCrypt 校验明文密码和 `password_hash`。
5. 校验 `status = 1`。
6. 签发包含 `userId`、`role`、`nickname` 的 JWT。
7. 返回 token 和商家账号基础信息。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "jwt-token",
    "user": {
      "id": 2,
      "nickname": "店主",
      "role": "SHOP",
      "avatarUrl": null,
      "phone": null
    }
  }
}
```

错误：

- `400`：请求参数不合法。
- `401`：账号或密码错误。
- `403`：账号被禁用。

### 4.3 顾客登录

```http
POST /api/auth/login
Content-Type: application/json
```

请求：

```json
{
  "nickname": "小明",
  "password": "123456",
  "role": "CUSTOMER"
}
```

处理流程与商家登录一致，但按 `nickname + CUSTOMER` 查询，并签发 `role = CUSTOMER` 的 JWT。

响应：

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
      "avatarUrl": null,
      "phone": null
    }
  }
}
```

### 4.4 当前用户

```http
GET /api/users/me
Authorization: Bearer {token}
```

处理流程：

1. JWT 过滤器解析 token，得到 `userId`、`role`、`nickname`。
2. 查询数据库中的用户最新信息。
3. 如果用户不存在或 `status = 0`，返回未登录或无权限。
4. 返回当前用户资料。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "nickname": "小明",
    "role": "CUSTOMER",
    "avatarUrl": "/images/avatar/1.png",
    "phone": "13800000000"
  }
}
```

错误：

- `401`：未携带 token、token 无效、token 过期。
- `403`：用户已被禁用。

### 4.5 修改个人信息

```http
PUT /api/users/me
Authorization: Bearer {token}
Content-Type: application/json
```

请求：

```json
{
  "nickname": "新的昵称",
  "phone": "13800000000",
  "avatarUrl": "/images/avatar/1.png"
}
```

处理流程：

1. 从登录态获取当前 `userId`。
2. 查询用户并确认启用。
3. 校验请求字段。
4. 如果修改昵称，检查同角色下 `nickname` 是否被其他用户占用。
5. 更新 `nickname`、`phone`、`avatar_url`。
6. 返回更新后的当前用户资料。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "nickname": "新的昵称",
    "role": "CUSTOMER",
    "avatarUrl": "/images/avatar/1.png",
    "phone": "13800000000"
  }
}
```

错误：

- `400`：字段格式不合法。
- `401`：未登录或登录过期。
- `403`：账号禁用。
- `409`：同角色下昵称已存在。

说明：

- 修改昵称后，旧 token 中的 `nickname` 可能不是最新值。服务端权限判断以 `userId` 和数据库最新记录为准。
- 客户端可在修改成功后刷新本地用户信息。是否强制重新登录可暂不要求。

## 5. JWT 认证流程

### 5.1 登录签发

登录成功后生成 JWT，建议 Claims 包含：

- `userId`：用户 ID。
- `role`：`CUSTOMER` 或 `SHOP`。
- `nickname`：登录时昵称，仅用于客户端展示或日志辅助，不作为唯一身份依据。
- `iat`：签发时间。
- `exp`：过期时间。

建议配置：

- `jwt.secret`：从配置文件或环境变量读取，生产环境不要硬编码。
- `jwt.expiration`：建议 7 天，便于 Flutter App 保持登录。
- 签名算法：`HS256` 可满足当前轻量项目需要。

### 5.2 请求校验

受保护接口处理流程：

1. 客户端在 Header 中携带 `Authorization: Bearer {token}`。
2. `JwtAuthenticationFilter` 读取并校验 token 签名和过期时间。
3. 从 token 中解析 `userId` 和 `role`。
4. 查询 `users` 表确认用户存在且启用。
5. 将当前用户信息放入 Spring Security 上下文，供 Controller 和 Service 使用。
6. 没有 token、token 无效、token 过期统一返回 `401`。

### 5.3 放行接口

无需登录：

- `POST /api/auth/customer/register`
- `POST /api/auth/login`
- 顾客端公开浏览接口，例如商家列表、商家详情、菜单、菜品详情。
- 静态图片资源 `/images/**`

需要登录：

- `GET /api/users/me`
- `PUT /api/users/me`
- 下单、我的订单、取消订单、留言、我的留言。
- 商家端所有 `/api/shop/**` 管理接口。
- 上传接口可根据用途要求登录，例如头像上传需要登录，商家和菜品图片上传需要商家登录。

## 6. 角色与权限规则

角色：

- `CUSTOMER`：顾客。
- `SHOP`：商家账号。

基础规则：

- 顾客注册只能创建 `CUSTOMER` 账号。
- 商家登录必须使用 `role = SHOP`，不能用顾客账号进入商家端。
- 顾客登录必须使用 `role = CUSTOMER`，不能用商家账号进入顾客端。
- 同一个昵称允许在不同角色下分别存在，例如 `小明 + CUSTOMER` 和 `小明 + SHOP` 可以共存。
- 同一个角色下昵称唯一，由数据库唯一索引 `uk_users_nickname_role` 和服务层共同保证。

接口权限建议：

| 接口范围 | 允许角色 | 说明 |
| --- | --- | --- |
| `/api/auth/customer/register` | 匿名 | 固定创建顾客 |
| `/api/auth/login` | 匿名 | 根据请求角色登录 |
| `/api/users/me` | CUSTOMER、SHOP | 返回当前登录账号 |
| `/api/orders/**` 顾客订单接口 | CUSTOMER | 只能操作自己的订单 |
| `/api/feedback/**` 顾客留言接口 | CUSTOMER | 只能查看自己的留言 |
| `/api/shop/**` 商家管理接口 | SHOP | 只能管理自己名下店铺资源 |
| `/api/upload/avatar` | CUSTOMER、SHOP | 只能更新自己的头像 URL |
| `/api/upload/shop`、`/api/upload/menu` | SHOP | 只能上传自己店铺相关图片 |

商家资源归属规则：

- 商家模块应通过 `shops.owner_user_id = 当前 userId` 找到当前商家的店铺。
- 修改菜品、分类、订单、留言回复时，业务模块必须校验资源属于该商家的店铺。
- 认证模块只负责提供当前用户，不替业务模块跳过归属校验。

## 7. 密码加密

密码存储规则：

- 数据库只保存 `password_hash`，禁止保存明文密码。
- 推荐使用 Spring Security `BCryptPasswordEncoder`。
- 注册和创建商家账号时：`passwordHash = passwordEncoder.encode(rawPassword)`。
- 登录时：`passwordEncoder.matches(rawPassword, user.passwordHash)`。

密码策略：

- 当前轻量项目建议最小长度 6 位，最大长度 72 位。
- 前后空白应在入库前裁剪；裁剪后为空则拒绝。
- 不在日志中输出明文密码、密码摘要或完整 token。
- 后续如需要提升安全性，可增加密码复杂度、登录失败次数限制、重置密码流程。

## 8. 异常处理

使用 `BusinessException` 表示可预期业务错误，配合 `GlobalExceptionHandler` 统一转换为 `ApiResponse`。

建议错误码：

| code | 场景 |
| --- | --- |
| 400 | 参数校验失败、角色枚举非法、手机号格式非法 |
| 401 | 未登录、token 无效、token 过期、账号或密码错误 |
| 403 | 账号禁用、角色无权访问接口 |
| 404 | 当前用户不存在 |
| 409 | 昵称冲突 |
| 500 | 未预期服务端错误 |

异常消息建议：

- 登录失败统一提示：`账号或密码错误`。
- token 失败统一提示：`未登录或登录已过期`。
- 角色不符提示：`无权访问该接口`。
- 昵称冲突提示：`昵称已存在`。
- 账号禁用提示：`账号已被禁用`。

全局处理建议：

- `MethodArgumentNotValidException`：返回 `400` 和首个字段错误信息。
- `ConstraintViolationException`：返回 `400`。
- `BusinessException`：返回异常中指定的 code 和 message。
- 其他异常：记录日志，返回 `500`，不要把堆栈返回给前端。

## 9. 校验规则

### 9.1 昵称

- 必填。
- 去除前后空格后长度 1 到 50。
- 建议允许中文、英文、数字、下划线和常见空格，不建议允许控制字符。
- 同角色下唯一。
- 修改昵称时，如果与原昵称相同，可直接通过；如果变化，必须做唯一校验。

### 9.2 密码

- 注册和登录必填。
- 长度 6 到 72。
- 不允许全空白。
- 不返回给前端。

### 9.3 角色

- 登录请求必填。
- 只允许 `CUSTOMER` 或 `SHOP`。
- 顾客注册接口不接收角色字段，即使前端传入也忽略。

### 9.4 手机号

- 允许为空。
- 不为空时建议长度不超过 30，与 `users.phone VARCHAR(30)` 一致。
- 当前项目可使用宽松校验：数字、加号、短横线和空格。
- 如果只面向中国手机号，也可限制为 `^1[3-9]\d{9}$`，但会降低通用性。

### 9.5 头像 URL

- 允许为空。
- 长度不超过 255，与 `users.avatar_url VARCHAR(255)` 一致。
- 建议只接受站内图片路径，如 `/images/avatar/xxx.png`。
- 不建议接受任意外部 URL，避免后续展示和安全问题。

## 10. 与 users 表字段映射

`users` 表：

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
);
```

字段映射：

| 数据库字段 | Entity 字段 | DTO 字段 | 说明 |
| --- | --- | --- | --- |
| `id` | `id` | `id` / `userId` | 用户主键，注册返回 `userId`，用户详情返回 `id` |
| `nickname` | `nickname` | `nickname` | 昵称，同角色下唯一 |
| `password_hash` | `passwordHash` | 不返回 | BCrypt 密码摘要 |
| `role` | `role` | `role` | `CUSTOMER` 或 `SHOP` |
| `avatar_url` | `avatarUrl` | `avatarUrl` | 头像访问路径 |
| `phone` | `phone` | `phone` | 用户手机号，可空 |
| `status` | `status` | 不返回 | `1` 启用，`0` 禁用 |
| `created_at` | `createdAt` | 通常不返回 | 创建时间 |
| `updated_at` | `updatedAt` | 通常不返回 | 更新时间 |

写入规则：

- 顾客注册写入：`nickname`、`password_hash`、`role = CUSTOMER`、`status = 1`。
- 商家账号初始化写入：`nickname`、`password_hash`、`role = SHOP`、`status = 1`。
- 登录只读取，不更新用户表。
- 修改个人信息只允许更新：`nickname`、`phone`、`avatar_url`。
- `id`、`role`、`status`、`created_at`、`updated_at` 不允许通过个人信息接口直接修改。

## 11. 后续实现步骤

1. 引入 Spring Security、JWT 依赖和校验依赖。
2. 创建 `User` 实体、`UserRole` 枚举、`UserRepository`。
3. 创建统一返回 `ApiResponse`、业务异常 `BusinessException`、错误码定义和全局异常处理。
4. 配置 `PasswordEncoder`，使用 BCrypt。
5. 实现 `AuthService.registerCustomer`，完成顾客注册。
6. 实现 `AuthService.login`，完成顾客和商家共用登录。
7. 实现 `JwtTokenProvider`，负责 token 生成、解析和校验。
8. 实现 `JwtAuthenticationFilter`，把当前用户写入 Security 上下文。
9. 配置接口放行和角色权限规则。
10. 实现 `UserService.getCurrentUser` 和 `UserService.updateCurrentUser`。
11. 实现 `AuthController` 和 `UserController`。
12. 准备至少一个商家账号初始化脚本或后台创建入口，便于商家端登录测试。
13. 与 Flutter 顾客端、商家端联调 token 保存、自动注入和登录失效处理。

## 12. 测试点

### 12.1 顾客注册

- 昵称和密码合法时注册成功。
- 同一昵称重复注册顾客账号返回 `409`。
- 昵称为空、过长、密码过短返回 `400`。
- 注册后数据库 `password_hash` 不是明文密码。
- 注册接口不能创建 `SHOP` 角色。

### 12.2 登录

- 顾客账号使用 `role = CUSTOMER` 登录成功。
- 商家账号使用 `role = SHOP` 登录成功。
- 顾客账号使用 `role = SHOP` 登录失败。
- 商家账号使用 `role = CUSTOMER` 登录失败。
- 密码错误返回 `401`。
- 禁用账号返回 `403`。
- 登录成功返回可用 JWT，且响应不包含 `passwordHash`。

### 12.3 JWT

- 携带有效 token 可访问 `/api/users/me`。
- 不携带 token 返回 `401`。
- token 格式错误返回 `401`。
- token 过期返回 `401`。
- token 中用户已不存在或已禁用时拒绝访问。

### 12.4 当前用户

- 顾客和商家均可调用 `/api/users/me`。
- 返回字段包含 `id`、`nickname`、`role`、`avatarUrl`、`phone`。
- 不返回 `passwordHash` 和内部敏感字段。

### 12.5 修改个人信息

- 修改昵称、手机号、头像成功。
- 修改为同角色其他用户已占用昵称返回 `409`。
- 修改为不同角色已使用的昵称允许通过。
- 手机号超长或格式非法返回 `400`。
- 头像 URL 超长或非站内路径返回 `400`。
- 未登录修改返回 `401`。

### 12.6 权限

- `CUSTOMER` token 不能访问 `/api/shop/**`。
- `SHOP` token 不能访问顾客下单和我的订单接口。
- 商家业务模块能根据当前 `SHOP` 用户校验 `shops.owner_user_id`。
- 静态图片资源和公开浏览接口不需要 token。

