# Redis 开发与验证

Redis 默认关闭，未部署 Redis 时应用继续使用数据库和 Noop 实现。开发环境按模块逐项开启，避免一次打开全部功能。

## 本地启动

1. 在 `backend` 目录复制 `.env.redis.example` 为 `.env.redis`，并把 `change_me` 替换为本机专用密码。
2. 运行 `docker compose -f docker-compose.redis.yml up -d`。
3. 运行 `powershell -ExecutionPolicy Bypass -File scripts/verify-redis.ps1` 完成容器、连接、迁移结构和 Redis 适配器验证。

`.env.redis` 已被 Git 忽略。不要把 Redis 密码写入 YAML、Java 测试或日志。
本地密码认证使用 `REDIS_USERNAME=default`；生产环境应设置受限 ACL 用户并限制 Key 前缀和高危命令。

## dev 与 prod 配置隔离

| Profile | Redis 环境文件 | Key 环境前缀 |
|---|---|---|
| `dev` | `backend/.env.redis`（同时兼容从项目根目录或 `backend` 目录启动） | `oma:dev:*` |
| `prod` | `/www/server/SpringBoot/.env.redis` | `oma:prod:*` |

- 公共 `application.yml` 不再加载任何具体环境的 Redis 文件。
- `application-dev.yml` 只做可选本地导入，本地没有 Redis 时仍可用 Noop/数据库方式启动。
- `application-prod.yml` 使用必需导入 `file:/www/server/SpringBoot/.env.redis[.properties]`；文件缺失时生产启动直接失败，避免静默使用开发配置或无密码连接。
- 线上启动必须设置 `SPRING_PROFILES_ACTIVE=prod` 或添加 `--spring.profiles.active=prod`。不要依赖默认值，默认 Profile 是 `dev`。
- `ORDER_MENU_REDIS_ENV` 可显式覆盖 Key 前缀；未配置时分别默认 `dev` 和 `prod`，防止两个环境共享 Key。

## 功能开关

至少设置 `ORDER_MENU_REDIS_ENABLED=true`，再独立开启所需模块：

```properties
ORDER_MENU_MENU_CACHE_ENABLED=true
ORDER_MENU_MOMENT_CACHE_ENABLED=true
ORDER_MENU_CHAT_CACHE_ENABLED=true
ORDER_MENU_UNREAD_CACHE_ENABLED=true
ORDER_MENU_IDEMPOTENCY_ENABLED=true
ORDER_MENU_PRESENCE_ENABLED=true
ORDER_MENU_RATE_LIMIT_ENABLED=true
```

生产环境应按 `moment -> menu -> chat -> unread -> idempotency -> presence/rate-limit` 顺序灰度，每次只开启一个模块。Redis 异常时先关闭对应模块开关，不需要删除数据库字段。

## 在线与输入中接口

- `PUT /api/messages/presence/online`：客户端登录后和前台活跃期间每 30-45 秒续期一次。
- `GET /api/messages/presence/users/{userId}`：读取在线状态。
- `PUT /api/messages/conversations/{conversationId}/typing`：请求体 `{"active":true}`；输入期间每 3-5 秒续期，停止输入时传 `false`。
- `GET /api/messages/conversations/{conversationId}/typing`：读取仍在 TTL 内的输入用户 ID。读写均验证当前用户是活跃会话成员。

在线状态默认 TTL 为 90 秒，输入中状态默认 TTL 为 8 秒。状态只用于界面提示，不是业务事实数据。

## 关键接口限流

启用 `ORDER_MENU_RATE_LIMIT_ENABLED=true` 后使用 Redis Lua 原子计数：

| 接口 | 默认额度 |
|---|---:|
| 登录 | 同一来源地址 10 次/10 分钟 |
| 创建订单 | 同一用户 5 次/分钟 |
| 发送文字或图片消息 | 同一用户 60 次/分钟 |
| 发表评论 | 同一用户 30 次/分钟 |

额度可通过 `ORDER_MENU_LOGIN_LIMIT`、`ORDER_MENU_ORDER_CREATE_LIMIT`、`ORDER_MENU_MESSAGE_SEND_LIMIT`、`ORDER_MENU_MOMENT_COMMENT_LIMIT` 及对应的 `*_WINDOW_SECONDS` 调整。Redis 不可用时限流快速降级为放行，数据库唯一索引仍负责订单最终幂等。

## 数据库迁移

在上线包含 Redis 业务代码前，按现有迁移流程依次执行：

- `V16__chat_cursor_indexes.sql`
- `V17__order_idempotency.sql`

迁移前先检查目标库是否已手工创建同名索引或字段，避免重复执行。验证脚本中的 `RedisConnectionTest.shouldHaveRequiredRedisMigrationSchema` 会检查两个字段和三个索引。
