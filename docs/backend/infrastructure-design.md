# Spring Boot 后端基础设施与部署配置设计

## 1. 设计目标

本文件用于约定点餐 App 后端工程的基础设施、配置、通用能力和部署方式。后端服务由顾客端 Flutter App 和商家端 Flutter App 共同调用，重点满足：

- 本地开发和宝塔 Linux 部署环境可切换。
- API 返回格式、异常处理、分页、参数校验统一。
- 支持 JWT 登录认证、角色鉴权、跨域访问和图片上传。
- 数据库、静态资源、初始化脚本有清晰的组织方式。

## 2. 推荐技术栈

| 类别 | 推荐方案 | 说明 |
| --- | --- | --- |
| 后端框架 | Spring Boot 3.x | 生态成熟，适合快速搭建 REST API、参数校验、安全、文件上传和部署配置。 |
| Java 版本 | JDK 17 | Spring Boot 3.x 推荐版本，长期支持，部署稳定。 |
| ORM / 数据访问 | MyBatis-Plus | 本项目表结构清晰、接口以增删改查和条件分页查询为主。MyBatis-Plus 对 SQL 可控性更好，分页、条件构造器、通用 Mapper 使用简单，也便于后续手写复杂订单查询 SQL。 |
| 数据库 | MySQL 8.x | 业务数据量较小但关系结构明确，适合用户、店铺、菜品、订单等关系型数据。 |
| 数据库连接池 | HikariCP | Spring Boot 默认连接池，性能稳定。 |
| 安全认证 | Spring Security + JWT | 使用无状态 Token 认证，适合 Flutter 客户端调用。 |
| 密码加密 | BCrypt | 不保存明文密码，注册和初始化账号时保存密码哈希。 |
| 参数校验 | spring-boot-starter-validation | 使用 Bean Validation 统一校验请求体、路径参数和查询参数。 |
| 文件上传 | Spring MultipartFile + 本地磁盘存储 | 项目规模较轻，先使用服务器目录保存头像、店铺图、菜品图，返回 `/images/...` URL。 |
| API 文档 | springdoc-openapi | 后续可提供 Swagger UI，方便前后端联调。 |
| 数据库脚本 | 手动 SQL 脚本，后续可接入 Flyway | 当前阶段保持简单，脚本按版本和用途组织；项目稳定后再引入自动迁移。 |

不推荐本项目优先使用 Spring Data JPA，原因是订单、菜品、店铺等查询后续容易出现多条件筛选、排序、统计和聚合展示，MyBatis-Plus 更容易精确控制 SQL 和返回字段。

## 3. 工程包结构设计

建议后端工程根包为：

```text
com.order.menu
```

推荐结构：

```text
backend/
  src/main/java/com/order/menu/
    OrderMenuApplication.java

    common/
      response/
        ApiResponse.java
        PageResponse.java
      exception/
        BusinessException.java
        GlobalExceptionHandler.java
        ErrorCode.java
      constant/
        RoleConstants.java
        FileConstants.java
      util/
        JwtUtils.java
        FileUtils.java
        OrderNoUtils.java

    config/
      WebMvcConfig.java
      SecurityConfig.java
      MyBatisPlusConfig.java
      CorsConfig.java
      UploadProperties.java
      JwtProperties.java

    security/
      JwtAuthenticationFilter.java
      LoginUser.java
      UserContext.java
      PasswordConfig.java

    module/
      auth/
        controller/
        service/
        dto/
      user/
        controller/
        service/
        mapper/
        entity/
        dto/
      shop/
        controller/
        service/
        mapper/
        entity/
        dto/
      category/
        controller/
        service/
        mapper/
        entity/
        dto/
      dish/
        controller/
        service/
        mapper/
        entity/
        dto/
      order/
        controller/
        service/
        mapper/
        entity/
        dto/
      feedback/
        controller/
        service/
        mapper/
        entity/
        dto/
      upload/
        controller/
        service/
        dto/

  src/main/resources/
    application.yml
    application-dev.yml
    application-prod.yml
    mapper/
      user/
      shop/
      category/
      dish/
      order/
      feedback/
    db/
      init/
      migration/
      seed/
```

包结构约定：

- `controller` 只处理 HTTP 入参、登录上下文和响应包装。
- `service` 放业务规则，例如下单金额计算、订单状态流转、商家权限校验。
- `mapper` 放 MyBatis-Plus Mapper 和复杂查询 SQL。
- `entity` 对应数据库表结构。
- `dto` 存放请求和响应对象，可继续分为 `request`、`response`。
- `common` 存放全局通用能力，不放具体业务逻辑。
- `config` 存放 Spring 配置、属性映射、拦截器、静态资源映射。
- `security` 存放 JWT 过滤器、当前用户上下文、权限相关对象。

## 4. 配置文件设计

### 4.1 application.yml

公共配置只放各环境通用项，开发和生产差异放到独立 profile。

```yaml
server:
  port: 8080
  servlet:
    context-path: /

spring:
  application:
    name: order-menu-backend
  profiles:
    active: dev
  servlet:
    multipart:
      enabled: true
      max-file-size: 5MB
      max-request-size: 10MB

mybatis-plus:
  mapper-locations: classpath:/mapper/**/*.xml
  type-aliases-package: com.order.menu.module.*.entity
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto

app:
  jwt:
    header: Authorization
    token-prefix: Bearer
    expire-seconds: 604800
  upload:
    allowed-extensions: jpg,jpeg,png,webp
    image-url-prefix: /images
  cors:
    allowed-methods: GET,POST,PUT,DELETE,OPTIONS
```

### 4.2 application-dev.yml

本地开发环境使用 MySQL `3307`，账号 `order_menu_admin`。

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3307/order_menu?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: order_menu_admin
    password: 1714672038qwe

logging:
  level:
    com.order.menu: debug
    com.order.menu.module: debug

app:
  jwt:
    secret: dev-change-me-to-a-long-random-secret
  upload:
    base-path: E:/order_menu_app/image
    image-url-prefix: /images
  cors:
    allowed-origins:
      - http://localhost:3000
      - http://localhost:5173
      - http://localhost:8080
```

本地图片访问示例：

```text
http://localhost:8080/images/avatar/xxx.png
http://localhost:8080/images/shop_pic/xxx.png
http://localhost:8080/images/menu_pic/1/xxx.png
```

### 4.3 application-prod.yml

生产环境运行在宝塔 Linux，建议通过环境变量注入敏感信息，避免把生产密码和 JWT 密钥写死到仓库。

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3307/order_menu?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: ${ORDER_MENU_DB_USERNAME:order_menu_admin}
    password: ${ORDER_MENU_DB_PASSWORD}

logging:
  file:
    name: /www/wwwroot/order_menu_app/backend/logs/order-menu.log
  level:
    com.order.menu: info

app:
  jwt:
    secret: ${ORDER_MENU_JWT_SECRET}
    expire-seconds: 604800
  upload:
    base-path: /www/wwwroot/order_menu_app/image
    image-url-prefix: /images
  cors:
    allowed-origins:
      - https://zsj-unknown
```

生产环境启动建议：

```text
java -jar order-menu-backend.jar --spring.profiles.active=prod
```

生产环境必须配置：

```text
ORDER_MENU_DB_PASSWORD=数据库密码
ORDER_MENU_JWT_SECRET=至少 32 位随机字符串
```

## 5. MySQL 本地配置

本地开发约定：

```text
host: 127.0.0.1
port: 3307
database: order_menu
username: order_menu_admin
password: 1714672038qwe
```

初始化数据库和账号：

```sql
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
```

说明：

- 应用连接地址使用 `127.0.0.1:3307`，因此需要保证 `'order_menu_admin'@'127.0.0.1'` 有权限。
- 生产环境可以继续使用同名账号，但密码应在宝塔 MySQL 中单独设置，不复用本地密码。

## 6. 统一响应设计

所有接口统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

约定：

- `code = 0` 表示成功。
- `message` 成功时为 `success`，失败时为可展示的错误信息。
- `data` 成功时返回对象、数组或分页对象；无数据可返回 `null`。

错误码建议：

| code | 含义 |
| --- | --- |
| 0 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未登录或登录过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 数据冲突，例如昵称已存在、订单状态不允许操作 |
| 413 | 上传文件过大 |
| 415 | 上传文件类型不支持 |
| 500 | 服务端错误 |

## 7. 统一异常设计

异常分类：

| 类型 | 处理方式 |
| --- | --- |
| `BusinessException` | 业务主动抛出，带错误码和错误信息。 |
| 参数校验异常 | 统一返回 `400`，提示第一个或全部字段错误。 |
| 登录认证异常 | 统一返回 `401`。 |
| 权限不足异常 | 统一返回 `403`。 |
| 数据不存在 | 返回 `404`。 |
| 唯一键冲突、状态冲突 | 返回 `409`。 |
| 上传异常 | 按文件过大、类型不支持、保存失败分别返回。 |
| 未知异常 | 记录日志，返回 `500`，不暴露堆栈。 |

全局异常处理器建议放在：

```text
common/exception/GlobalExceptionHandler
```

日志要求：

- 业务异常记录 `warn` 或不记录堆栈。
- 未知异常记录 `error` 和完整堆栈。
- 登录失败不要打印密码、Token、敏感请求体。

## 8. 分页响应设计

分页请求参数统一：

```text
page: 当前页，从 1 开始，默认 1
pageSize: 每页数量，默认 10，最大 100
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

建议内部分页对象字段：

| 字段 | 说明 |
| --- | --- |
| `records` | 当前页数据 |
| `page` | 当前页 |
| `pageSize` | 每页数量 |
| `total` | 总记录数 |

分页处理建议：

- Controller 层接收 `page`、`pageSize`。
- Service 层限制最大 `pageSize`，避免一次查询过多数据。
- MyBatis-Plus 使用分页插件完成分页查询。

## 9. 参数校验设计

请求 DTO 使用 Bean Validation 注解：

| 场景 | 校验建议 |
| --- | --- |
| 昵称 | 必填，长度 1 到 50。 |
| 密码 | 必填，长度 6 到 64。 |
| 角色 | 必填，只允许 `CUSTOMER`、`SHOP`。 |
| 手机号 | 可选，长度不超过 30。 |
| 店铺名称 | 必填，长度 1 到 80。 |
| 分类名称 | 必填，长度 1 到 50。 |
| 菜品名称 | 必填，长度 1 到 80。 |
| 菜品价格 | 必填，大于等于 0。 |
| 下单数量 | 必填，大于 0。 |
| 留言内容 | 必填，长度 1 到 1000。 |

校验约定：

- Controller 类或方法启用 `@Validated`。
- 请求体使用 `@Valid`。
- 路径 ID 和查询参数也要校验正数。
- 参数校验失败统一由全局异常处理器返回 `400`。

## 10. JWT 与安全设计

### 10.1 Token 内容

JWT Payload 建议包含：

```text
userId
role
nickname
iat
exp
```

### 10.2 Header 约定

Flutter 请求需要携带：

```text
Authorization: Bearer {token}
```

### 10.3 放行接口

无需登录：

```text
POST /api/auth/customer/register
POST /api/auth/login
GET  /api/shops
GET  /api/shops/{shopId}
GET  /api/shops/{shopId}/menu
GET  /api/dishes/{dishId}
GET  /images/**
```

需要顾客登录：

```text
GET  /api/users/me
PUT  /api/users/me
POST /api/orders
GET  /api/orders/my
GET  /api/orders/{orderId}
PUT  /api/orders/{orderId}/cancel
POST /api/feedback
GET  /api/feedback/my
POST /api/upload/avatar
```

需要商家登录：

```text
GET    /api/shop/**
POST   /api/shop/**
PUT    /api/shop/**
DELETE /api/shop/**
POST   /api/upload/shop
POST   /api/upload/menu
```

### 10.4 权限校验

安全层校验是否登录和角色，业务层继续校验数据归属：

- 顾客只能查看和取消自己的订单。
- 商家只能管理自己店铺下的分类、菜品、订单、留言。
- 菜品图片上传时，`shopId` 必须属于当前商家。

## 11. 文件上传设计

### 11.1 存储目录

本地：

```text
E:/order_menu_app/image/
  avatar/
  shop_pic/
  menu_pic/{shopId}/
```

宝塔 Linux：

```text
/www/wwwroot/order_menu_app/image/
  avatar/
  shop_pic/
  menu_pic/{shopId}/
```

### 11.2 URL 映射

统一返回相对 URL：

```text
/images/avatar/{filename}
/images/shop_pic/{filename}
/images/menu_pic/{shopId}/{filename}
```

前端根据环境拼接完整地址：

```text
本地: http://localhost:8080/images/...
线上: https://zsj-unknown/order_menu/images/...
```

### 11.3 上传限制

- 只允许 `jpg`、`jpeg`、`png`、`webp`。
- 单文件最大 5MB。
- 文件名使用业务 ID、时间戳、随机数或安全处理后的原文件名，避免重名覆盖。
- 保存前检查目录是否存在，不存在则创建。
- 不信任原文件名，禁止 `../` 等路径穿越字符。

### 11.4 上传记录

可使用 `upload_files` 表记录：

- `owner_type`：`AVATAR`、`SHOP`、`DISH`
- `owner_id`：用户 ID、店铺 ID 或菜品 ID
- `file_path`：服务器绝对路径或相对存储路径
- `url`：返回给前端的访问 URL
- `original_name`：原文件名

## 12. CORS 设计

开发环境允许本地调试来源：

```text
http://localhost:3000
http://localhost:5173
http://localhost:8080
```

生产环境只允许正式域名：

```text
https://zsj-unknown
```

配置要求：

- 允许方法：`GET`、`POST`、`PUT`、`DELETE`、`OPTIONS`。
- 允许请求头：`Authorization`、`Content-Type`。
- 若后续不使用 Cookie，可不启用 credentials。
- CORS 配置要和 Spring Security 组合生效，避免预检请求被拦截。

## 13. 静态资源映射设计

Spring Boot 静态资源映射：

```text
/images/** -> app.upload.base-path/
```

示例：

```text
请求: /images/avatar/1_20260704103000.png
本地文件: E:/order_menu_app/image/avatar/1_20260704103000.png
生产文件: /www/wwwroot/order_menu_app/image/avatar/1_20260704103000.png
```

建议：

- 开发阶段由 Spring Boot 直接映射 `/images/**`。
- 生产阶段可以先由 Spring Boot 映射，部署稳定后再改为 Nginx 静态资源代理。
- 如果线上 API 统一加 `/order_menu` 前缀，应在 Nginx 中把 `/order_menu/api/` 转发给后端，把 `/order_menu/images/` 映射到图片目录。

## 14. 宝塔 Linux 部署路径

推荐目录：

```text
/www/wwwroot/order_menu_app/
  backend/
    order-menu-backend.jar
    logs/
    config/
  image/
    avatar/
    shop_pic/
    menu_pic/
```

目录权限：

- `backend/logs` 需要后端进程可写。
- `image` 及其子目录需要后端进程可写。
- 不建议给整个 `/www/wwwroot` 过宽权限，只给项目目录授权。

启动方式建议：

```text
cd /www/wwwroot/order_menu_app/backend
java -jar order-menu-backend.jar --spring.profiles.active=prod
```

宝塔中可通过 Java 项目管理器或 Supervisor 管理进程，并配置开机自启。

Nginx 反向代理建议：

```text
/order_menu/api/     -> http://127.0.0.1:8080/api/
/order_menu/images/  -> /www/wwwroot/order_menu_app/image/
```

如果暂不配置 Nginx 静态映射，也可以让 `/order_menu/images/` 转发到 Spring Boot 的 `/images/`。

## 15. 数据库初始化脚本组织方式

建议脚本目录：

```text
src/main/resources/db/
  init/
    00_create_database_and_user.sql
  migration/
    V1__create_base_tables.sql
    V2__add_upload_files.sql
  seed/
    dev_seed_data.sql
    prod_init_shop_account.sql
```

脚本职责：

| 目录 | 用途 |
| --- | --- |
| `init` | 创建数据库、账号、授权。通常由 DBA 或开发者手动执行，不随应用启动自动执行。 |
| `migration` | 建表和表结构变更脚本，按版本编号保存。 |
| `seed` | 初始化数据，例如测试店铺、测试菜品、商家账号。 |

执行顺序：

1. 执行 `init/00_create_database_and_user.sql`。
2. 使用 `order_menu_admin` 连接 `order_menu`。
3. 执行 `migration/V1__create_base_tables.sql`。
4. 如果需要文件记录表，执行 `migration/V2__add_upload_files.sql`。
5. 本地开发执行 `seed/dev_seed_data.sql`。
6. 生产环境只执行必要的 `seed/prod_init_shop_account.sql`，不要导入测试数据。

后续如果接入 Flyway：

- 保留 `migration/V*__*.sql` 命名。
- 应用启动时自动检查并执行未运行的迁移。
- 生产环境迁移前先备份数据库。

## 16. 后续项目搭建步骤

1. 创建 Spring Boot 工程，选择 Java 17、Maven 或 Gradle。
2. 添加依赖：Web、Validation、MySQL Driver、MyBatis-Plus、Spring Security、JWT、springdoc-openapi。
3. 建立推荐包结构和 `application.yml`、`application-dev.yml`、`application-prod.yml`。
4. 在本地 MySQL `3307` 创建 `order_menu` 数据库和 `order_menu_admin` 账号。
5. 编写并执行数据库建表脚本。
6. 配置 MyBatis-Plus 分页插件、Mapper 扫描和 XML 路径。
7. 实现统一响应、分页响应、业务异常和全局异常处理。
8. 实现 JWT 工具、登录过滤器、当前用户上下文和角色鉴权。
9. 实现 CORS 和 `/images/**` 静态资源映射。
10. 实现文件上传服务，先完成头像、店铺图、菜品图上传。
11. 实现认证、用户、店铺、分类、菜品、订单、留言建议等业务模块。
12. 配置生产环境变量、打包 jar，上传到宝塔目录。
13. 配置宝塔 Java 项目或 Supervisor，启动 `prod` profile。
14. 配置 Nginx 反向代理和静态资源访问路径。

## 17. 测试点

### 17.1 配置与启动

- `dev` profile 能连接 `127.0.0.1:3307/order_menu`。
- 使用 `order_menu_admin` 能正常建表、查询和写入。
- `prod` profile 缺少 `ORDER_MENU_DB_PASSWORD` 或 `ORDER_MENU_JWT_SECRET` 时应启动失败或明确报错。
- 日志能写入生产目录 `/www/wwwroot/order_menu_app/backend/logs/`。

### 17.2 通用响应和异常

- 成功接口返回 `code = 0`。
- 参数错误返回 `400`。
- 未登录访问受保护接口返回 `401`。
- 顾客访问商家接口返回 `403`。
- 查询不存在资源返回 `404`。
- 重复昵称注册返回 `409`。
- 未知异常不向前端暴露堆栈。

### 17.3 JWT 与权限

- 登录成功返回 Token。
- Token 过期或伪造时返回 `401`。
- 顾客只能操作自己的订单。
- 商家只能操作自己店铺的数据。
- 登出如果不维护服务端 Token 状态，可由客户端删除本地 Token。

### 17.4 分页和校验

- `page` 小于 1 时返回参数错误或自动修正为 1。
- `pageSize` 超过最大值时限制到 100 或返回参数错误。
- 必填字段为空时返回清晰错误信息。
- 价格、数量等数值字段不能为负数。

### 17.5 文件上传和静态资源

- 头像上传后可通过 `/images/avatar/...` 访问。
- 店铺图片上传后可通过 `/images/shop_pic/...` 访问。
- 菜品图片上传后保存在 `/images/menu_pic/{shopId}/...`。
- 超过 5MB 文件上传失败。
- 非图片扩展名上传失败。
- 文件名包含特殊路径字符时不会造成路径穿越。

### 17.6 部署联调

- 宝塔环境下后端进程可自动重启。
- Nginx 能正确转发 `/order_menu/api/`。
- Nginx 或 Spring Boot 能正确访问 `/order_menu/images/`。
- Flutter 顾客端和商家端能切换本地、线上 baseUrl。
- 上传图片返回的相对 URL 能在两端正确拼接展示。
