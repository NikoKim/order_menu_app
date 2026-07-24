# order_menu_app Redis 执行测试未通过或未完成细节记录文档

> 创建时间：2026-07-22
> 执行分支：feature/20260722_Redis_Deepseek
> 基础文档：order_menu_app_Redis完整可执行改造步骤.md

---

## 总体状态

| 阶段 | 状态 | 说明 |
|------|------|------|
| 阶段 0：基础准备和 Redis 连接 | ✅ 已完成 | 全部测试通过，基础设施就绪 |
| 阶段 1：聊天分页和独立已读位置 | ✅ 已完成 | 后端及 Vue/Flutter 三端均已完成并验证 |
| 阶段 2：库存原子扣减和状态幂等恢复 | ✅ 已完成 | 全部测试通过 |
| 阶段 3：订单 clientRequestId 数据库幂等 | ✅ 已完成 | 全部测试通过 |
| 阶段 4：店铺详情和菜单缓存 | ✅ 已完成 | ShopCachePort/NoopShopCache/RedisShopCache/MenuCacheInvalidator |
| 阶段 5：动态基础数据缓存 | ✅ 已完成 | MomentBaseCacheDto/RedisMomentCacheAdapter/MomentCacheConfiguration |
| 阶段 6：聊天最近消息缓存 | ✅ 已完成 | ChatCachePort/RedisChatCache/ChatCacheConfiguration (ZSET+STRING) |
| 阶段 7：未读数量缓存 | ✅ 已完成 | UnreadCountCache/RedisUnreadCountCache (delete-on-write) |
| 阶段 8：订单 Redis 幂等加速 | ✅ 已完成 | IdempotencyService (SETNX PENDING/SUCCESS) |
| 阶段 9：多实例 WebSocket Pub/Sub | ✅ 代码完成，默认关闭 | Redis Pub/Sub、实例去重、本地降级及测试已完成；需部署双实例验收 |
| 阶段 10：监控、压测、灰度上线 | 🟡 工程完成，环境验收待执行 | 指标、健康探针和验证脚本已完成；压测、告警及灰度需生产类环境 |

---

## 2026-07-22 完整性复核补充

- 聊天缓存改为保存完整消息实体，修复分享卡片字段丢失、缓存页倒序和错误游标；撤回时同时删除 STRING 和 ZSET 成员。
- 通知未读缓存统一在事务提交后失效；补齐“全部已读”失效逻辑。
- 聊天会话未读数和总未读数已接入缓存；新消息、独立已读和单用户删除消息后提交事务再失效。
- 动态缓存删除改为事务提交后执行，避免并发请求在事务提交前回填旧值。
- 订单 Redis 幂等改为带 token 的 `PENDING`，使用 Lua 原子完成/释放；`PENDING` 且 DB 无订单时返回 `ORDER_CREATING`，成功提交后写 `SUCCESS`，回滚后释放。
- Redis 启用态集成测试覆盖全部条件 Bean、完整聊天消息序列化、未读缓存、幂等状态和数据库迁移结构。

---

## 阶段 0：基础准备和 Redis 连接 ✅ 已完成

### 已完成的步骤
- 0.1 创建独立开发分支：已在 `feature/20260722_Redis_Deepseek` 分支
- 0.2 检查 6379 端口：Redis 容器 `order-menu-redis` 运行正常
- 0.3 创建 Docker Redis 配置：所有文件已创建并通过验证
- 0.4 修改 Maven 依赖：已添加 `spring-boot-starter-data-redis` 和 `commons-pool2`
- 0.5 增加 Spring Boot Redis 配置：已添加到 application.yml
- 0.6 创建 Redis 配置属性类：OrderMenuRedisProperties.java
- 0.7 创建统一 Key 工厂：RedisKeyFactory.java
- 0.8 创建 JSON 编解码器：RedisJsonCodec.java
- 0.9 创建事务提交后执行工具：AfterCommitExecutor.java
- 0.10 增加 Redis 连接测试：RedisConnectionTest 通过

### 验收结果
- [x] Redis 容器健康
- [x] Spring Boot Redis 测试通过
- [x] 所有业务 Redis 开关仍为 false
- [x] 原业务可以正常启动（全部 105 测试通过）
- [x] 项目中没有第二个手动创建的 StringRedisTemplate
- [x] 密码没有提交进 Git（.env.redis 被 gitignore）

### 提交
- commit `88daada`: `chore: add redis infrastructure and local docker config`
- 12 files changed, 287 insertions(+)

---

## 阶段 1：聊天分页和独立已读位置 ✅ 已完成

### 已完成的后端步骤

#### 1.1 数据库迁移脚本
- 文件：`backend/docs/db/migration/V20260722_01__chat_cursor_and_read_receipt.sql` ✅
- 文件：`backend/docs/db/migration/rollback/V20260722_01__chat_cursor_and_read_receipt_rollback.sql` ✅
- 开发数据库已确认 `last_read_message_id`，并已执行三个阶段 1 索引迁移 ✅

#### 1.2 修改 ConversationMember 实体
- 文件：`backend/src/main/java/com/order/menu/module/message/entity/ConversationMember.java`
- `lastReadMessageId` 字段已存在 ✅

#### 1.3 增加 ChatMessageMapper 游标查询
- 文件：`backend/src/main/java/com/order/menu/module/message/mapper/ChatMessageMapper.java`
- 已添加：`selectVisibleLatest()`、`selectVisibleBefore()`、`countMessageInConversation()` ✅

#### 1.4 增加 ConversationMemberMapper 已读更新
- 文件：`backend/src/main/java/com/order/menu/module/message/mapper/ConversationMemberMapper.java`
- 已添加：`updateLastReadMessageId()` ✅

#### 1.5 修改消息 DTO
- 文件：`backend/src/main/java/com/order/menu/module/message/dto/MessageDtos.java`
- `ConversationMessagesResponse` 已添加 `hasMore` 和 `nextBeforeId` 字段 ✅
- 已添加 `ReadConversationRequest` record ✅
- 注：保持 `messages` 字段名称兼容旧前端

#### 1.6 修改 MessageService 消息查询
- 文件：`backend/src/main/java/com/order/menu/module/message/service/MessageService.java`
- `messages()` 方法已改为支持 cursor 分页（beforeId + limit）✅
- 已移除 GET 消息中的自动 `markConversationRead` 调用 ✅
- 已移除旧的私有 `markConversationRead(userId, conversationId, messages)` 方法 ✅

#### 1.7 增加独立已读方法
- 新增公开方法 `markConversationRead(userId, conversationId, lastReadMessageId)` ✅

#### 1.8 修改 MessageController
- 文件：`backend/src/main/java/com/order/menu/module/message/controller/MessageController.java`
- GET `/conversations/{id}/messages` 已支持 `beforeId` 和 `limit` 查询参数 ✅
- 新增 PUT `/conversations/{id}/read` 端点 ✅

### 测试修复记录

#### 阶段 1 早期测试失败（已修复）
以下 4 个测试因方法签名变更最初失败，已全部修复：

| 测试名称 | 原始错误 | 修复方式 |
|----------|----------|----------|
| `shopParticipantUsesShopNameInConversationAndMessage` | `NoSuchMethodError: messages(Long,Long)` | 更新调用为 `messages(1L, 9L, null, 50)`，更新 mock 为 `selectVisibleLatest()` |
| `messagesRejectsUserWhoIsNotAnActiveConversationMember` | `NoSuchMethodError: messages(Long,Long)` | 更新调用为 `messages(3L, 9L, null, 50)` |
| `openingMessagesMarksLatestMessageRead` | `NoSuchMethodError: messages(Long,Long)` | 拆分为两个测试：`openingMessagesDoesNotAutoMarkRead` + `markConversationReadUpdatesLastReadMessageId` |
| `blockedRecipientCannotLoadFailedMessage` | `NoSuchMethodError: messages(Long,Long)` | 更新调用为 `messages(2L, 9L, null, 50)`，添加 `selectVisibleLatest()` mock |

#### Mockito 严格模式问题（已修复）
- `verify(conversationMemberMapper, never()).updateById(any())` → 改为 `any(ConversationMember.class)` 解决 MyBatis-Plus 两个重载 `updateById` 方法导致的歧义
- `any()` 用于 int 原始类型导致 NPE → 移除了相关 verify 调用

### 已完成的步骤 — 前端改造（阶段 1.9）

#### 涉及文件
| 前端 | 文件 | 状态 |
|------|------|------|
| web_frontend | `src/api/messages.js` | API 层已支持 `params`（`getMessages` 函数已接受 `params` 参数，可直接传递 `{beforeId, limit}`） |
| web_frontend | `src/views/chat/ChatDetail.vue` | 已完成 cursor 分页、去重、滚动保持、独立已读和跨会话保护 |
| moments_web_frontend | `src/api/messages.js` | 同 web_frontend，API 层已支持 `params` |
| customer_frontend | API、模型、聊天页及测试 | 已完成 cursor 分页、去重、滚动保持、独立已读和跨会话保护 |
| shop_frontend | API、模型、聊天页及测试 | 已完成 cursor 分页、去重、滚动保持、独立已读和跨会话保护 |

#### 前端已实现的功能（根据文档 1.9 节）
1. 历史消息插入列表顶部
2. 根据 `message.id` 去重
3. 插入历史消息后保持原滚动位置
4. `isLoadingMore=true` 时不重复请求
5. `hasMore=false` 时停止请求
6. WebSocket 新消息追加到底部
7. 页面展示到最新消息后调用 `PUT /conversations/{id}/read`
8. 切换会话时清空旧会话分页状态
9. 请求返回后确认会话 ID 仍是当前会话

#### 验证结果
- `web_frontend`、`moments_web_frontend`：`npm run build` 通过
- `customer_frontend`：聊天/API 相关 34 项测试通过；全量 95 通过、2 个既有动态页面测试失败
- `shop_frontend`：全量 90 项测试通过；最终相关 20 项测试通过
- 三端均实现请求会话校验和并发保护，旧响应不会污染当前会话

### 后端测试结果
- 全部 110 测试通过，0 失败 ✅

---

## 阶段 2：库存原子扣减和状态幂等恢复 ✅ 已完成

### 已完成的步骤

#### 2.1 确认 NULL 库存语义
- `stock = NULL` 表示不限库存 ✅
- OrderService 原有逻辑已使用 `dish.getStock() != null` 检查

#### 2.2 修改 DishMapper
- 文件：`backend/src/main/java/com/order/menu/module/dish/mapper/DishMapper.java`
- 已添加：`deductStock()` — 原子扣减（`SET stock = CASE WHEN stock IS NULL THEN NULL ELSE stock - #{quantity} END WHERE id = #{dishId} AND (stock IS NULL OR stock >= #{quantity})`）
- 已添加：`restoreStock()` — 原子恢复（`SET stock = CASE WHEN stock IS NULL THEN NULL ELSE stock + #{quantity} END WHERE id = #{dishId}`）

#### 2.3 修改创建订单逻辑
- 文件：`backend/src/main/java/com/order/menu/module/order/service/OrderService.java`
- 已删除：Java 层先查后改的库存操作
- 已改为：调用 `dishMapper.deductStock()` 返回影响行数判断
- 已添加：菜品按 `dishId` 排序后扣减，降低死锁概率

#### 2.4 修改取消/拒绝订单逻辑
- 文件：`backend/src/main/java/com/order/menu/module/order/mapper/OrderMapper.java`
- 已添加：`changeToCancelledIfAllowed(orderId, targetStatus)` — 条件更新仅当状态为 SUBMITTED/ACCEPTED
- `cancel()` 和 `reject()` 已改为先条件更新，affected==0 则跳过
- `restoreStock()` 使用原子 SQL，库存恢复项按 dishId 排序

### 提交
- commit `2f31488`（包含阶段 2+3 的合并提交）

---

## 阶段 3：订单 clientRequestId 数据库幂等 ✅ 已完成

### 已完成的步骤

#### 3.1 数据库迁移
- 文件：`backend/docs/db/migration/V20260722_02__order_idempotency.sql` ✅
- 文件：`backend/docs/db/migration/rollback/V20260722_02__order_idempotency_rollback.sql` ✅
- 开发数据库已执行 `client_request_id` 字段和唯一索引迁移 ✅

#### 3.2 修改 Order 实体与 DTO
- 文件：`backend/src/main/java/com/order/menu/module/order/entity/Order.java` — 已添加 `clientRequestId` 字段
- 文件：`backend/src/main/java/com/order/menu/module/order/dto/OrderDtos.java` — `CreateOrderRequest` 已添加 `@NotBlank @Size(max=64) String clientRequestId`
- `OrderService.create()` 已设置 `order.setClientRequestId(request.clientRequestId())`
- 已捕获 `DuplicateKeyException`，按 `(userId, clientRequestId)` 查询已有订单并返回

#### 3.3 修改 OrderMapper
- 已添加：`selectByCustomerAndClientRequestId(userId, clientRequestId)` 查询方法

### 提交
- commit `e41cf51`（包含阶段 2+3 的合并提交）

---

## 阶段 4：店铺详情和菜单缓存 ✅ 已完成

### 新增文件
- `backend/src/main/java/com/order/menu/module/shop/service/cache/ShopCachePort.java` — 缓存接口（getShopDetail, putShopDetail, getMenu, putMenu, evictShopDetail, evictMenu）
- `backend/src/main/java/com/order/menu/module/shop/service/cache/NoopShopCache.java` — 空实现（所有 get 返回 Optional.empty()）
- `backend/src/main/java/com/order/menu/module/shop/service/cache/RedisShopCache.java` — Redis 实现（StringRedisTemplate + RedisKeyFactory + RedisJsonCodec，TTL 带 0-30s 随机抖动，所有 Redis 异常只记录警告并返回缓存未命中）
- `backend/src/main/java/com/order/menu/module/shop/service/cache/ShopCacheConfiguration.java` — 条件 Bean 配置（`@ConditionalOnExpression` 控制 Redis/Noop 切换）
- `backend/src/main/java/com/order/menu/module/shop/service/cache/MenuCacheInvalidator.java` — 统一失效器（使用 AfterCommitExecutor 在事务提交后执行缓存删除）

### 修改文件
- `backend/src/main/java/com/order/menu/module/shop/service/ShopService.java`
  - `detail()` / `myShop()` — 先查 Redis 缓存，未命中则查 MySQL 并写缓存
  - `menu()` — 先查 Redis 缓存，未命中则查 MySQL 并写缓存（提取 `loadMenuFromDatabase()` 私有方法）
  - `updateMyShop()` — 更新后调用 `menuCacheInvalidator.evictShopAfterCommit()` 失效缓存

- `backend/src/main/java/com/order/menu/module/dish/service/DishService.java`
  - 已注入 `MenuCacheInvalidator`
  - 所有修改操作后均调用 `menuCacheInvalidator.evictMenuAfterCommit()`：
    - `createCategory()`, `updateCategory()`, `deleteCategory()`
    - `createDish()`, `updateDish()`, `updateStatus()`, `deleteDish()`
    - `updateShowcaseDishes()`

- `backend/src/main/java/com/order/menu/module/order/service/OrderService.java`
  - 已注入 `MenuCacheInvalidator`
  - `create()` — 订单创建并扣库存后失效菜单缓存
  - `cancel()` — 恢复库存后失效菜单缓存
  - `reject()` — 恢复库存后失效菜单缓存

### 修复的测试
- `ShopServiceTest.java` — 添加 `@Mock ShopCachePort` 和 `@Mock MenuCacheInvalidator`，更新构造函数
- `OrderServiceTest.java` — 添加 `@Mock MessageService` 和 `@Mock MenuCacheInvalidator`，更新构造函数

### 后端测试结果
- 全部 110 测试通过，0 失败 ✅

### 注意
- 所有 Redis 功能开关默认 `false`，当前仍使用 Noop 实现
- 开发数据库已执行并由集成测试验证 `V20260722_01`、`V20260722_02` 所需字段和索引

## 待记录项模板

### 测试失败记录
| 测试名称 | 涉及文件 | 涉及模块 | 错误描述 | 修复状态 |
|----------|----------|----------|----------|----------|
| — | — | — | — | — |

### 未完成步骤记录
| 步骤编号 | 步骤描述 | 涉及文件 | 未完成原因 | 影响范围 |
|----------|----------|----------|------------|----------|
| — | — | — | — | — |

---

## 阶段 5：动态基础数据缓存 ✅ 已完成

### 新增文件
- `backend/src/main/java/com/order/menu/module/moment/dto/MomentBaseCacheDto.java`
- `backend/src/main/java/com/order/menu/module/moment/service/RedisMomentCacheAdapter.java`
- `backend/src/main/java/com/order/menu/module/moment/service/MomentCacheConfiguration.java`

### 修改文件
- `MomentCachePort.java` — 改为强类型接口
- `NoopMomentCache.java` — 移除 @Component，实现新接口
- `MomentService.java` — `detail()` 先查缓存再查 MySQL，新增 `extractBaseDto()`/`buildFromCache()` 方法

### 测试：全部 110 通过 ✅

---

## 阶段 6：聊天最近消息缓存 ✅ 已完成

### 新增文件
- `ChatMessageCacheDto.java` / `ChatCachePort.java` / `NoopChatCache.java` / `RedisChatCache.java` (ZSET+STRING) / `ChatCacheConfiguration.java`

### 修改文件
- `MessageService.java` — `messages()` 最新页缓存；`sendMessage()`/`recallMessage()` 缓存追加/删除

### 测试：全部 110 通过 ✅

---

## 阶段 7：未读数量缓存 ✅ 已完成

### 新增文件
- `UnreadCountCache.java` / `NoopUnreadCountCache.java` / `RedisUnreadCountCache.java` / `UnreadCountCacheConfiguration.java`

### 修改文件
- `MessageService.java` — `unreadCount()` 缓存；`createNotification()`/`markNotificationRead()` 删除缓存

### 测试：全部 110 通过 ✅

---

## 阶段 8：订单 Redis 幂等加速 ✅ 已完成

### 新增文件
- `IdempotencyService.java` (SETNX PENDING/SUCCESS) / `IdempotencyConfiguration.java`

### 修改文件
- `OrderService.java` — `create()` 新增 Redis acquire 流程（EXISTING→返回；PENDING→查DB；ACQUIRED→创建）

### 测试：全部 110 通过 ✅

---

## 阶段 10：监控工程 ✅ 已完成，环境验收待执行

- `pom.xml` — 添加 actuator + prometheus 依赖
- `application.yml` — 添加 management 端点配置

### 测试：全部 110 通过 ✅

> 注：此处“完成”仅指 Actuator/Prometheus 工程接入；压测、告警、灰度和高可用演练状态以文末最终复核为准。

---

## 修改总结（截至阶段 1）

### 新增文件 (Phase 0)
- `backend/docker-compose.redis.yml`
- `backend/.env.redis.example`
- `backend/redis/redis.conf`
- `backend/src/main/java/com/order/menu/config/OrderMenuRedisProperties.java`
- `backend/src/main/java/com/order/menu/config/RedisInfrastructureConfiguration.java`
- `backend/src/main/java/com/order/menu/common/redis/RedisKeyFactory.java`
- `backend/src/main/java/com/order/menu/common/redis/RedisJsonCodec.java`
- `backend/src/main/java/com/order/menu/common/transaction/AfterCommitExecutor.java`
- `backend/src/test/java/com/order/menu/RedisConnectionTest.java`

### 新增文件 (Phase 1)
- `backend/docs/db/migration/V20260722_01__chat_cursor_and_read_receipt.sql`
- `backend/docs/db/migration/rollback/V20260722_01__chat_cursor_and_read_receipt_rollback.sql`

### 修改文件 (Phase 0)
- `backend/pom.xml` — 添加 Redis 和 commons-pool2 依赖
- `backend/src/main/resources/application.yml` — 添加 Redis 配置
- `.gitignore` — 添加 `!.env.redis.example`

### 修改文件 (Phase 1)
- `backend/src/main/java/com/order/menu/module/message/mapper/ChatMessageMapper.java` — 添加游标查询方法
- `backend/src/main/java/com/order/menu/module/message/mapper/ConversationMemberMapper.java` — 添加已读更新方法
- `backend/src/main/java/com/order/menu/module/message/dto/MessageDtos.java` — 添加分页字段和 ReadConversationRequest
- `backend/src/main/java/com/order/menu/module/message/service/MessageService.java` — 实现游标分页和独立已读
- `backend/src/main/java/com/order/menu/module/message/controller/MessageController.java` — 添加分页参数和已读端点
- `backend/src/test/java/com/order/menu/module/message/service/MessageServiceTest.java` — 适配新 API

---

## 2026-07-22《Redis 改造建议与实施方案》最终复核

### 本轮补齐的工程项

- 多实例 WebSocket Redis Pub/Sub：统一事务提交后发布、本实例事件去重、订阅分发和 Redis 异常时本地降级；开关为 `ORDER_MENU_WEBSOCKET_BUS_ENABLED`，开发环境默认关闭。
- 在线状态与正在输入：后端 Redis 短 TTL 状态、鉴权接口，以及 Web、动态 Web、顾客 Flutter、商家 Flutter 的心跳、输入状态上报和轮询展示均已接入。
- 关键接口限流：使用 Redis Lua 原子限流覆盖登录、下单、发消息和评论；返回 HTTP 429。登录同时按来源与账号限流，避免把共享出口 IP 误当成单一账号。
- 订单幂等端到端：Web 和顾客 Flutter 均持久化 `clientRequestId`；相同请求在 30 分钟内或应用重启后复用，请求成功后清理。数据库唯一索引仍是最终兜底。
- 查询性能：菜单选项改为批量查询，消除菜单 N+1；会话预览和未读数改为 SQL 定向查询，避免读取全部历史消息。
- 可观测与降级：缓存命中、未命中、失败、降级、数据库回源和耗时已接入 Micrometer；Redis 故障日志做采样；Redis 健康项不阻断应用 readiness。
- 安全与配置：Redis 密码不再硬编码；支持用户名、密码和独立环境 Key 前缀；本地 `.env.redis` 已启用全部单实例 Redis 能力，应用通过 `spring.config.import` 自动读取该文件。
- 运维资料：新增开发说明、Flyway V16/V17 迁移和 `backend/scripts/verify-redis.ps1` 一键验证脚本。

### 最终验证结果

| 验证项 | 结果 |
|--------|------|
| 后端 Maven 全量测试 | 135/135 通过，0 失败，0 错误 |
| `web_frontend` 生产构建 | 通过 |
| `moments_web_frontend` 生产构建 | 通过 |
| 顾客 Flutter Redis 相关测试 | 24/24 通过 |
| 商家 Flutter Redis 相关测试 | 26/26 通过；全量 93/93 通过 |
| 顾客 Flutter 全量测试 | 100/102 通过；2 个既有 `moment_behavior_test` UI finder 失败，与本次 Redis 改造无关 |
| `git diff --check` | 通过；仅有 Git 行尾转换提示 |
| Redis 容器验证脚本 | 本次未完成运行：Docker Desktop daemon 未启动，脚本在连接 Docker API 时终止 |

### 仍需部署环境执行的验收项

- 启动 Docker Desktop 后执行 `powershell -ExecutionPolicy Bypass -File backend/scripts/verify-redis.ps1`，验证带密码 Redis PING 和 Redis 集成测试。
- 使用至少两个后端实例开启 `ORDER_MENU_WEBSOCKET_BUS_ENABLED=true`，验证跨实例消息只投递一次及实例退出后的恢复行为。
- 在生产类环境完成 Redis ACL/TLS、Sentinel 或托管高可用、Prometheus 告警、容量基线、压测和至少 24 小时灰度观察。这些属于环境交付，不能由本地代码测试替代。
- Redis Streams/outbox 仍是具备可靠异步需求后再实施的条件项；动态 Feed ID、热门话题和热点动态缓存已在后续 P0/P1 阶段完成，详见文末实施记录。

### 当前开发环境说明

- IDEA 的 Redis Data Source 只是查看 Redis 的客户端配置，不会替后端启动 Redis，也不会自动改变业务缓存开关。
- `dev` Profile 自动、可选导入 `backend/.env.redis`；`prod` Profile 只导入线上 `/www/server/SpringBoot/.env.redis`，且该文件缺失时启动失败。Redis 服务仍必须实际运行，否则缓存访问会按设计降级到 MySQL。

---

## 2026-07-22 动态 Redis P0/P1 缓存实施记录

### 已完成的后端缓存

- Feed 列表：按用户、类型、页码和大小缓存动态 ID，使用全局版本号完成发布、编辑和删除后的整体失效；仅缓存前 20 页，避免高基数 Key。
- 动态批量读取：Feed、话题页、个人动态页通过 Redis `MGET` 批量读取 `moment:base`，未命中项查询 MySQL 后回填。
- 动态未读数：按用户缓存相关动态未读数；发布相关动态和标记已读后在事务提交后失效。
- 热门话题：缓存按动态数和参与用户数排序的话题结果，话题及动态关系变化后失效。
- 话题与个人动态列表：缓存权限敏感的 ID 页，Key 包含 viewer；缓存命中后仍执行 `canView` 校验，权限不符时回源 MySQL。
- 评论首屏和热门评论：评论第一页按 pageSize 分 Key，热门评论缓存 ID；评论新增、删除、点赞时清理全部 1 至 10 的首屏 Key。
- 互动统计：点赞、评论、分享和回应采用“MySQL 权威 + Redis JSON 快照”的 cache-aside 策略；事务内写操作返回强制读取数据库，提交后再失效缓存，避免返回旧计数或产生双写漂移。
- 热门动态：新增 `GET /api/moments/hot?limit=10`，按点赞、评论、回应和分享总互动数降序，再按发布时间和 ID 降序；缓存前 1000 个候选 ID，返回前逐条执行权限过滤，最多返回 10 条。
- Redis 故障继续通过 `RedisFailurePolicy` 降级到 MySQL；关闭动态缓存开关时使用 `NoopMomentCache`。

### Web 范围

- 仅修改 `moments_web_frontend`：在“推荐”旁增加“热门”页签，固定展示服务端前 10 条结果，支持加载、空状态、错误重试、请求复用和独立滚动位置。
- 本轮未修改 `web_frontend` 的动态页面，也未修改两个 Flutter 工程。
- 手机端后续接入项和测试清单已记录到根目录 `手机端预开发.txt`。

### 验证结果

| 验证项 | 结果 |
|--------|------|
| 后端 Maven 全量测试 | 143/143 通过，0 失败，0 错误；包含动态 P0/P1 Redis 实际序列化往返测试 |
| 动态 Service/Controller/Adapter/Key 专项测试 | 通过 |
| `moments_web_frontend` 生产构建 | 通过，1887 modules transformed |
| `git diff --check` | 通过；仅有 Git 行尾转换提示 |

### 后续容量优化条件

- 热门话题当前以单个 JSON 值保存完整排序结果；话题规模明显增长后可改为只缓存 Top N 或 Redis ZSET。
- 互动统计没有采用 Redis `INCR` 后异步回写 MySQL，而是选择数据库权威的安全快照方案；只有确认写入吞吐成为瓶颈并具备可靠 outbox/对账机制后，才升级为异步计数。

---

## 2026-07-22 Redis dev/prod 配置隔离

- 公共 `application.yml` 不导入任何具体环境的 Redis 文件，Redis 连接参数和功能开关继续使用环境变量占位符。
- `application-dev.yml` 仅可选导入 `./.env.redis` 和 `./backend/.env.redis`，兼容从 `backend` 目录或项目根目录启动；文件不存在时仍可使用数据库和 Noop 实现。
- `application-prod.yml` 必需导入 `file:/www/server/SpringBoot/.env.redis[.properties]`。生产文件缺失时应用直接启动失败，禁止静默回退到开发配置。
- 开发环境默认 Key 前缀为 `oma:dev:*`；生产环境默认 Key 前缀为 `oma:prod:*`，也可通过 `ORDER_MENU_REDIS_ENV` 显式覆盖。
- 本地默认启用 `dev`。线上启动必须设置 `SPRING_PROFILES_ACTIVE=prod`，或传入 `--spring.profiles.active=prod`。
- 配置结构由 `RedisProfileConfigurationTest` 验证，确保 dev 不引用线上文件、prod 不引用工作区 `.env.redis`。
- 修改后后端 Maven 全量测试 145/145 通过，0 失败，0 错误。

---

## 2026-07-23 动态 Redis P0/P1 补充实施记录

### P0：用户互动状态

- 新增按访问者隔离的动态互动状态 Key：`oma:{env}:moment:viewer-state:v1:{viewerId}:{momentId}`。
- 值只包含 `liked`、`read`、`reactionEmojis` 和结构版本，不缓存完整用户对象、手机号、权限结果或完整动态响应。
- Feed、热门、话题和个人动态整页先对全部动态 ID 执行一次 Redis `MGET`，不再逐条读取 Redis。
- Redis 未命中的动态只执行三次批量 MySQL 查询：一次点赞、一次已读、一次表情回应；每个动态的 `false/空集合` 也会回填，避免没有互动的数据反复穿透数据库。
- 回填改为 Redis pipeline，每个 Key 独立设置默认 180 秒 TTL，并增加 0～30 秒随机抖动，减少网络往返和集中失效。
- 点赞、取消点赞、添加/取消表情、批量标记已读后，精确删除当前 `viewerId + momentId` 的状态；删除通过事务提交后回调执行，事务回滚不会误删缓存。
- 批量标记已读已消除查询 N+1：动态一次批量读取、已有已读记录一次批量读取、权限快照一次读取，再写入缺失记录。
- 评论点赞状态使用独立的 `viewerId + commentId` Key 和批量 `MGET`，同时缓存 true/false，TTL 45～55 秒；点赞变化只精确失效当前用户的当前评论。

### P0：权限、曝光和击穿保护

- `MomentAccessCacheService` 将“不看 Ta/不让 Ta 看”和已建立直聊关系合并为用户权限快照；单个 HTTP 请求内复用一次快照，Redis 失败时回源 MySQL。
- 权限快照 TTL 上限收紧为 15 秒并增加 0～5 秒抖动；偏好变化后双方 Key、双方 Feed 和双方未读缓存均在事务提交后失效。
- 直聊关系建立或撤回导致关系变化时，同时失效双方权限快照、双方 Feed 版本和双方动态未读缓存，不再等待列表 TTL 自然过期。
- Feed 增加访问者级版本号，与全局发布版本共同组成 `moment:feed:v2` Key；关系或偏好变化只失效相关用户，不再为单个用户变化清空所有人的 Feed。
- 推荐首屏曝光使用 Redis ZSET 保存最近 2048 条、TTL 7 天；MySQL 仍是权威记录。Redis 未覆盖的候选只按当前候选 ID 范围回查数据库，新曝光使用批量 `INSERT IGNORE` 后写入 Redis。
- 新增跨实例缓存重建锁：Redis `SET NX` 短租约、UUID token、Lua 原子比对 token 后释放；锁 Key 对 identity 使用 SHA-256 摘要。
- 热门动态候选、热门话题和动态统计已接入“首次 miss → 抢锁 → 二次读 → 单实例回源 → finally 释放”。锁繁忙时最多短暂重读三次；Redis 不可用时直接回源数据库，不阻断请求。
- 公开动态 WebSocket 收件人过滤改为一次批量偏好查询，不再为每个在线用户逐个读取权限缓存或执行关系 SQL。

### P1：辅助短缓存

- 作者公开资料缓存只保存 ID、昵称、头像、角色和商家标识，TTL 60～70 秒；用户资料或状态变化时精确失效，并提升 `@` 搜索版本。
- `@` 用户搜索缓存 TTL 30～40 秒；查询词归一化后使用 SHA-256 摘要，Redis Key 不暴露原始搜索内容。
- 话题详情缓存 TTL 120～130 秒；话题搜索分页缓存 TTL 30～40 秒，并通过版本号整体失效。
- 动态和评论“数据库确实不存在”使用 15～25 秒负缓存；只在数据库返回 null 或删除提交后写入。无权限、未发布、关系变化等结果绝不写入全局负缓存。
- 动态基础缓存命中后的热门评论从两次重复 `selectById` 降为一次；提及作者改为复用批量公开作者缓存。

### 隐私边界

- 不缓存 `UserCardResponse`，因为其中手机号、`phoneVisible` 和 `self` 都依赖当前访问者。
- 不缓存“无权限”结果，也不把用户实体写入 Redis，避免密码摘要、手机号和隐私开关进入缓存。
- 权限敏感列表 Key 始终包含访问者，并在缓存命中后继续进行可见性校验；Redis 异常统一按缓存未命中降级。

### 本次验证

| 验证项 | 结果 |
|--------|------|
| P0/P1 动态、聊天关系和用户缓存专项测试 | 101/101 通过 |
| 后端全量非外部 Redis 单元/组件测试 | 162/162 通过 |
| 后端全量测试（包含真实 Redis 连接） | 170 项中 162 项非外部 Redis 测试通过、6 个连接错误；本机 `127.0.0.1:6379` 未运行 Redis |
| Java 编译 | 通过 |

真实 Redis 往返测试未完成的项目为连接、完整聊天序列化、动态 P0/P1 序列化、未读/幂等、在线状态和限流。失败原因均为本机 Redis 端口未监听，不是断言或业务逻辑失败；启动开发 Redis 后需重新执行 `mvn test` 完成最终验收。
