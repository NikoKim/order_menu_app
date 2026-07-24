# order_menu_app Redis 改造：可逐步执行实施文档

> 适用项目：`NikoKim/order_menu_app`  
> 适用分支：`feature/20260717_网页版前端_W`  
> 后端：Spring Boot 3.3.6、Java 17、MyBatis-Plus、MySQL  
> 文档目标：开发人员可以按照本文件从上到下逐阶段执行，每个阶段都有修改内容、验证标准与回滚方式。

---

## 0. 重要说明

### 0.1 本文采用的 Redis 配置

用户提供的信息中，`Password` 与 Redis URI 中的密码不一致：

```text
Password：1714672038qwe
原 URI：redis://:OrderMenu_Redis_2026_StrongPassword@127.0.0.1:6379/0
```

本文以明确标注的 `Password：1714672038qwe` 为准。

最终统一配置：

```text
Host：127.0.0.1
Port：6379
Password：1714672038qwe
Database：0
正确 URI：redis://:1714672038qwe@127.0.0.1:6379/0
```

如果你当前 Redis 容器实际使用的是 `OrderMenu_Redis_2026_StrongPassword`，必须先统一密码。应用配置、Docker 配置和测试命令不能使用两个不同密码。

### 0.2 核心原则

1. 先完成数据库、索引和分页优化，再增加 Redis。
2. MySQL 是最终数据源，Redis 只负责加速和临时协调。
3. Redis 关闭或故障时，菜单、动态、聊天和通知仍然可以通过 MySQL 工作。
4. Redis 不作为库存、订单和聊天历史的唯一存储。
5. 所有缓存失效必须在数据库事务提交后执行。
6. 当前单实例部署不启用 Redis WebSocket 总线；后端部署两个及以上实例时再启用。
7. 不缓存包含当前用户点赞状态、权限状态等用户专属字段的公共 DTO。
8. 不使用 `KEYS *`；调试使用 `SCAN`。

### 0.3 最终实施顺序

严格按照以下顺序执行：

```text
阶段 0：建立分支、启动 Redis、增加基础依赖和基础设施
阶段 1：聊天分页、独立已读位置、数据库索引
阶段 2：库存原子扣减/恢复、订单状态幂等
阶段 3：订单 clientRequestId 数据库唯一约束
阶段 4：店铺详情和菜单缓存
阶段 5：动态基础数据缓存
阶段 6：聊天最近消息缓存
阶段 7：通知/聊天未读数缓存
阶段 8：订单 Redis 幂等加速
阶段 9：多实例 WebSocket Pub/Sub（暂不启用）
阶段 10：监控、压测、灰度上线
```

每个阶段必须通过本阶段的验收标准后才能继续。

---

# 阶段 0：基础准备和 Redis 连接

## 0.1 创建独立开发分支

在项目根目录执行：

```bash
git status
git switch feature/20260717_网页版前端_W
git pull
git switch -c feature/20260722_redis_upgrade
```

确认：

```bash
git branch --show-current
```

应该输出：

```text
feature/20260722_redis_upgrade
```

如果本地有未提交修改，先提交或暂存，不要直接切换：

```bash
git stash push -u -m "before redis upgrade"
```

---

## 0.2 检查 6379 端口是否已被容器占用

Windows PowerShell：

```powershell
docker ps -a --filter "publish=6379"
docker ps -a --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}"
```

如果已有 Redis 容器，先验证它的密码：

```powershell
docker exec -e REDISCLI_AUTH=1714672038qwe <现有容器名称> redis-cli -n 0 PING
```

返回：

```text
PONG
```

表示可以继续使用现有容器。

如果返回：

```text
WRONGPASS
```

说明现有容器密码与本文配置不一致。开发环境可以重新创建容器。删除容器前先确认其中没有需要保留的数据。

---

## 0.3 创建 Docker Redis 配置

在 `backend` 目录创建：

```text
backend/
├── docker-compose.redis.yml
├── .env.redis
└── redis/
    └── redis.conf
```

### 0.3.1 `backend/.env.redis`

```dotenv
REDIS_PASSWORD=1714672038qwe
```

该文件包含密码，必须加入 `.gitignore`：

```gitignore
# Local Redis secret
backend/.env.redis
.env.redis
```

可以提交一个示例文件：

### `backend/.env.redis.example`

```dotenv
REDIS_PASSWORD=change_me
```

### 0.3.2 `backend/redis/redis.conf`

```conf
bind 0.0.0.0
port 6379
protected-mode yes

appendonly yes
appendfsync everysec

maxmemory-policy noeviction

timeout 0
tcp-keepalive 60

databases 16

loglevel notice
```

说明：

- Redis 端口只通过 Docker 映射到 `127.0.0.1`，不会暴露给局域网。
- 当前 Redis 同时会保存缓存和幂等协调数据，因此使用 `noeviction`。
- 开发环境开启 AOF，方便容器重启后保留调试数据。
- 不配置很小的 `maxmemory`，避免幂等 Key 被淘汰。

### 0.3.3 `backend/docker-compose.redis.yml`

```yaml
services:
  redis:
    image: redis:7.4-alpine
    container_name: order-menu-redis
    restart: unless-stopped
    env_file:
      - .env.redis
    ports:
      - "127.0.0.1:6379:6379"
    command:
      - /bin/sh
      - -c
      - >
        exec redis-server /usr/local/etc/redis/redis.conf
        --requirepass "$${REDIS_PASSWORD}"
    volumes:
      - order-menu-redis-data:/data
      - ./redis/redis.conf:/usr/local/etc/redis/redis.conf:ro
    healthcheck:
      test:
        [
          "CMD-SHELL",
          "REDISCLI_AUTH=$${REDIS_PASSWORD} redis-cli -h 127.0.0.1 -p 6379 -n 0 PING | grep PONG"
        ]
      interval: 10s
      timeout: 3s
      retries: 5
      start_period: 5s

volumes:
  order-menu-redis-data:
```

### 0.3.4 启动 Redis

进入后端目录：

```powershell
cd backend
docker compose --env-file .env.redis -f docker-compose.redis.yml up -d
```

查看状态：

```powershell
docker compose --env-file .env.redis -f docker-compose.redis.yml ps
docker logs order-menu-redis
```

### 0.3.5 验证连接

```powershell
docker exec -e REDISCLI_AUTH=1714672038qwe order-menu-redis redis-cli -n 0 PING
```

预期：

```text
PONG
```

写入、读取、删除测试：

```powershell
docker exec -e REDISCLI_AUTH=1714672038qwe order-menu-redis redis-cli -n 0 SET oma:dev:test:connection ok EX 60
docker exec -e REDISCLI_AUTH=1714672038qwe order-menu-redis redis-cli -n 0 GET oma:dev:test:connection
docker exec -e REDISCLI_AUTH=1714672038qwe order-menu-redis redis-cli -n 0 DEL oma:dev:test:connection
```

预期读取结果：

```text
ok
```

### 阶段 0.3 验收

- [ ] 容器状态为 `healthy`
- [ ] `PING` 返回 `PONG`
- [ ] 可以执行 `SET/GET/DEL`
- [ ] Redis 只绑定到 `127.0.0.1:6379`
- [ ] `.env.redis` 没有被 Git 跟踪

检查：

```bash
git status
git check-ignore backend/.env.redis
```

---

## 0.4 修改 Maven 依赖

修改：

```text
backend/pom.xml
```

在 `<dependencies>` 中添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

当前方案采用显式 Cache-Aside，不使用 `@Cacheable`，因此第一阶段不强制添加：

```xml
spring-boot-starter-cache
```

监控依赖在阶段 10 再添加。

执行：

```powershell
cd backend
mvn clean test
```

如果项目使用 Maven Wrapper：

```powershell
.\mvnw.cmd clean test
```

### 阶段 0.4 验收

- [ ] Maven 可以下载依赖
- [ ] 项目编译通过
- [ ] 原有测试通过
- [ ] 尚未修改业务逻辑

---

## 0.5 增加 Spring Boot Redis 配置

修改公共配置文件：

```text
backend/src/main/resources/application.yml
```

增加：

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}
      database: ${REDIS_DATABASE:0}
      connect-timeout: 2s
      timeout: 2s
      repositories:
        enabled: false
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 1
          max-wait: 1s

order-menu:
  redis:
    enabled: ${ORDER_MENU_REDIS_ENABLED:false}
    environment: ${ORDER_MENU_REDIS_ENV:dev}

    menu-cache-enabled: ${ORDER_MENU_MENU_CACHE_ENABLED:false}
    moment-cache-enabled: ${ORDER_MENU_MOMENT_CACHE_ENABLED:false}
    chat-cache-enabled: ${ORDER_MENU_CHAT_CACHE_ENABLED:false}
    unread-cache-enabled: ${ORDER_MENU_UNREAD_CACHE_ENABLED:false}
    idempotency-enabled: ${ORDER_MENU_IDEMPOTENCY_ENABLED:false}
    websocket-bus-enabled: ${ORDER_MENU_WEBSOCKET_BUS_ENABLED:false}

    shop-detail-ttl-seconds: 300
    shop-menu-ttl-seconds: 120
    moment-base-ttl-seconds: 300
    chat-message-ttl-seconds: 1800
    chat-recent-ttl-seconds: 1800
    unread-count-ttl-seconds: 300
    idempotency-pending-ttl-seconds: 120
    idempotency-success-ttl-seconds: 600
```

不要把密码直接提交到 `application.yml`。

本地启动前，在 PowerShell 设置：

```powershell
$env:REDIS_HOST="127.0.0.1"
$env:REDIS_PORT="6379"
$env:REDIS_PASSWORD="1714672038qwe"
$env:REDIS_DATABASE="0"

$env:ORDER_MENU_REDIS_ENABLED="true"
$env:ORDER_MENU_REDIS_ENV="dev"

$env:ORDER_MENU_MENU_CACHE_ENABLED="false"
$env:ORDER_MENU_MOMENT_CACHE_ENABLED="false"
$env:ORDER_MENU_CHAT_CACHE_ENABLED="false"
$env:ORDER_MENU_UNREAD_CACHE_ENABLED="false"
$env:ORDER_MENU_IDEMPOTENCY_ENABLED="false"
$env:ORDER_MENU_WEBSOCKET_BUS_ENABLED="false"
```

此时只建立 Redis 基础设施，不启用任何业务缓存。

---

## 0.6 创建 Redis 配置属性类

创建：

```text
backend/src/main/java/com/order/menu/config/OrderMenuRedisProperties.java
```

```java
package com.order.menu.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "order-menu.redis")
public class OrderMenuRedisProperties {

    private boolean enabled;
    private String environment = "dev";

    private boolean menuCacheEnabled;
    private boolean momentCacheEnabled;
    private boolean chatCacheEnabled;
    private boolean unreadCacheEnabled;
    private boolean idempotencyEnabled;
    private boolean websocketBusEnabled;

    private long shopDetailTtlSeconds = 300;
    private long shopMenuTtlSeconds = 120;
    private long momentBaseTtlSeconds = 300;
    private long chatMessageTtlSeconds = 1800;
    private long chatRecentTtlSeconds = 1800;
    private long unreadCountTtlSeconds = 300;
    private long idempotencyPendingTtlSeconds = 120;
    private long idempotencySuccessTtlSeconds = 600;
}
```

创建：

```text
backend/src/main/java/com/order/menu/config/RedisInfrastructureConfiguration.java
```

```java
package com.order.menu.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OrderMenuRedisProperties.class)
public class RedisInfrastructureConfiguration {
    // Spring Boot 已自动提供 StringRedisTemplate。
    // 不要在这里重复创建 StringRedisTemplate Bean。
}
```

---

## 0.7 创建统一 Key 工厂

创建：

```text
backend/src/main/java/com/order/menu/common/redis/RedisKeyFactory.java
```

```java
package com.order.menu.common.redis;

import com.order.menu.config.OrderMenuRedisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisKeyFactory {

    private final OrderMenuRedisProperties properties;

    private String prefix() {
        return "oma:" + properties.getEnvironment();
    }

    public String shopDetail(Long shopId) {
        return prefix() + ":shop:detail:v1:" + shopId;
    }

    public String shopMenu(Long shopId) {
        return prefix() + ":shop:menu:v1:" + shopId;
    }

    public String momentBase(Long momentId) {
        return prefix() + ":moment:base:v1:" + momentId;
    }

    public String chatRecentIds(Long conversationId) {
        return prefix() + ":chat:recent-ids:v1:" + conversationId;
    }

    public String chatMessage(Long messageId) {
        return prefix() + ":chat:message:v1:" + messageId;
    }

    public String notificationUnread(Long userId) {
        return prefix() + ":notification:unread:v1:" + userId;
    }

    public String chatUnread(Long userId, Long conversationId) {
        return prefix() + ":chat:unread:v1:" + userId + ":" + conversationId;
    }

    public String chatUnreadTotal(Long userId) {
        return prefix() + ":chat:unread-total:v1:" + userId;
    }

    public String orderIdempotency(Long userId, String clientRequestId) {
        return prefix() + ":order:idempotency:v1:" + userId + ":" + clientRequestId;
    }

    public String websocketChannel() {
        return prefix() + ":ws:events";
    }
}
```

Key 规范中不使用 `{env}`。花括号是 Redis Cluster Hash Tag，不能把所有生产 Key 固定到一个槽位。

---

## 0.8 创建 JSON 编解码器

创建：

```text
backend/src/main/java/com/order/menu/common/redis/RedisJsonCodec.java
```

```java
package com.order.menu.common.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisJsonCodec {

    private final ObjectMapper objectMapper;

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Redis JSON serialization failed", exception);
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Redis JSON deserialization failed", exception);
        }
    }

    public <T> List<T> readList(String json, Class<T> elementType) {
        try {
            JavaType type = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, elementType);
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Redis JSON list deserialization failed", exception);
        }
    }
}
```

不要使用会把 Java 类名写入缓存的通用类型序列化方案。缓存值统一使用明确 DTO。

---

## 0.9 创建事务提交后执行工具

创建：

```text
backend/src/main/java/com/order/menu/common/transaction/AfterCommitExecutor.java
```

```java
package com.order.menu.common.transaction;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class AfterCommitExecutor {

    public void execute(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }
        );
    }
}
```

后续所有缓存删除和缓存写入都通过该工具在事务提交后执行。

---

## 0.10 增加 Redis 连接测试

创建：

```text
backend/src/test/java/com/order/menu/RedisConnectionTest.java
```

```java
package com.order.menu;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.data.redis.password=1714672038qwe",
        "spring.data.redis.database=0",
        "order-menu.redis.enabled=true"
})
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldReadAndWriteRedis() {
        String key = "oma:dev:test:junit";

        redisTemplate.opsForValue().set(key, "ok");
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("ok");
        redisTemplate.delete(key);
    }
}
```

执行：

```powershell
mvn test -Dtest=RedisConnectionTest
```

### 阶段 0 总验收

- [ ] Redis 容器健康
- [ ] Spring Boot Redis 测试通过
- [ ] 所有业务 Redis 开关仍为 `false`
- [ ] 原业务可以正常启动
- [ ] 项目中没有第二个手动创建的 `StringRedisTemplate`
- [ ] 密码没有提交进 Git

提交：

```bash
git add backend/pom.xml backend/docker-compose.redis.yml backend/redis backend/src backend/.env.redis.example .gitignore
git commit -m "chore: add redis infrastructure and local docker config"
```

---

# 阶段 1：聊天分页和独立已读位置

本阶段不依赖 Redis。完成后即使关闭 Redis，聊天加载性能也会改善。

## 1.1 创建数据库迁移脚本

创建：

```text
backend/docs/db/migration/V20260722_01__chat_cursor_and_read_receipt.sql
```

执行 SQL 前先检查现有结构：

```sql
SHOW CREATE TABLE chat_messages;
SHOW CREATE TABLE chat_message_deletions;
SHOW CREATE TABLE conversation_members;

SHOW INDEX FROM chat_messages;
SHOW INDEX FROM chat_message_deletions;
SHOW INDEX FROM conversation_members;
```

确认没有同名字段和索引后执行：

```sql
ALTER TABLE conversation_members
    ADD COLUMN last_read_message_id BIGINT NULL
    COMMENT '用户在该会话中最后确认已读的消息ID';

CREATE INDEX idx_chat_messages_conversation_id_id
    ON chat_messages (conversation_id, id);

CREATE UNIQUE INDEX uk_chat_message_deletion_user_message
    ON chat_message_deletions (user_id, message_id);
```

如果 `chat_message_deletions` 已经有等价唯一索引，不重复创建。

### 回滚 SQL

创建：

```text
backend/docs/db/migration/rollback/V20260722_01__chat_cursor_and_read_receipt_rollback.sql
```

```sql
DROP INDEX idx_chat_messages_conversation_id_id ON chat_messages;
DROP INDEX uk_chat_message_deletion_user_message ON chat_message_deletions;

ALTER TABLE conversation_members
    DROP COLUMN last_read_message_id;
```

生产环境执行回滚前必须确认新代码已经回滚，否则新代码会继续访问已删除字段。

---

## 1.2 修改 ConversationMember 实体

找到：

```text
backend/src/main/java/com/order/menu/module/message/entity/ConversationMember.java
```

增加：

```java
private Long lastReadMessageId;
```

MyBatis-Plus 默认可以将 `last_read_message_id` 映射到 `lastReadMessageId`。如果项目关闭了下划线转驼峰，需要显式增加：

```java
@TableField("last_read_message_id")
private Long lastReadMessageId;
```

---

## 1.3 增加 ChatMessageMapper 游标查询

修改：

```text
backend/src/main/java/com/order/menu/module/message/mapper/ChatMessageMapper.java
```

增加：

```java
package com.order.menu.module.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.message.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("""
        SELECT m.*
        FROM chat_messages m
        WHERE m.conversation_id = #{conversationId}
          AND NOT EXISTS (
              SELECT 1
              FROM chat_message_deletions d
              WHERE d.message_id = m.id
                AND d.user_id = #{userId}
          )
        ORDER BY m.id DESC
        LIMIT #{limit}
        """)
    List<ChatMessage> selectVisibleLatest(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("limit") int limit
    );

    @Select("""
        SELECT m.*
        FROM chat_messages m
        WHERE m.conversation_id = #{conversationId}
          AND m.id < #{beforeId}
          AND NOT EXISTS (
              SELECT 1
              FROM chat_message_deletions d
              WHERE d.message_id = m.id
                AND d.user_id = #{userId}
          )
        ORDER BY m.id DESC
        LIMIT #{limit}
        """)
    List<ChatMessage> selectVisibleBefore(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit
    );

    @Select("""
        SELECT COUNT(*)
        FROM chat_messages
        WHERE id = #{messageId}
          AND conversation_id = #{conversationId}
        """)
    int countMessageInConversation(
            @Param("conversationId") Long conversationId,
            @Param("messageId") Long messageId
    );
}
```

如果 Mapper 当前已有方法，不要覆盖原有方法，只追加以上方法。

---

## 1.4 增加 ConversationMemberMapper 已读更新

修改：

```text
backend/src/main/java/com/order/menu/module/message/mapper/ConversationMemberMapper.java
```

增加：

```java
@Update("""
    UPDATE conversation_members
    SET last_read_message_id =
        GREATEST(COALESCE(last_read_message_id, 0), #{lastReadMessageId})
    WHERE conversation_id = #{conversationId}
      AND user_id = #{userId}
    """)
int updateLastReadMessageId(
        @Param("conversationId") Long conversationId,
        @Param("userId") Long userId,
        @Param("lastReadMessageId") Long lastReadMessageId
);
```

---

## 1.5 修改消息 DTO

修改：

```text
backend/src/main/java/com/order/menu/module/message/dto/MessageDtos.java
```

将会话消息响应调整为兼容结构：

```java
public record ConversationMessagesResponse(
        List<ChatMessageResponse> messages,
        boolean hasMore,
        Long nextBeforeId
) {
}
```

增加：

```java
public record ReadConversationRequest(
        Long lastReadMessageId
) {
}
```

必须保留 `messages` 字段名称，避免旧前端完全无法解析。

---

## 1.6 修改 MessageService 消息查询

将原来“一次查询整个会话全部消息”的逻辑改成：

```java
public MessageDtos.ConversationMessagesResponse messages(
        Long userId,
        Long conversationId,
        Long beforeId,
        Integer limit
) {
    requireConversationMember(userId, conversationId);

    int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 100));
    int queryLimit = safeLimit + 1;

    List<ChatMessage> entities;

    if (beforeId == null) {
        entities = chatMessageMapper.selectVisibleLatest(
                conversationId,
                userId,
                queryLimit
        );
    } else {
        entities = chatMessageMapper.selectVisibleBefore(
                conversationId,
                userId,
                beforeId,
                queryLimit
        );
    }

    boolean hasMore = entities.size() > safeLimit;

    if (hasMore) {
        entities = new java.util.ArrayList<>(
                entities.subList(0, safeLimit)
        );
    } else {
        entities = new java.util.ArrayList<>(entities);
    }

    // SQL 是 DESC，前端需要按旧消息 -> 新消息显示。
    java.util.Collections.reverse(entities);

    List<MessageDtos.ChatMessageResponse> responses = entities.stream()
            .map(this::toMessageResponse)
            .toList();

    Long nextBeforeId = entities.isEmpty()
            ? null
            : entities.get(0).getId();

    return new MessageDtos.ConversationMessagesResponse(
            responses,
            hasMore,
            nextBeforeId
    );
}
```

必须删除该 GET 方法中的：

```java
markConversationRead(...)
```

GET 消息接口不再自动标记整个会话已读。

---

## 1.7 增加独立已读方法

在 `MessageService` 增加：

```java
@Transactional
public void markConversationRead(
        Long userId,
        Long conversationId,
        Long lastReadMessageId
) {
    requireConversationMember(userId, conversationId);

    if (lastReadMessageId == null) {
        throw new BusinessException(
                ErrorCode.BAD_REQUEST,
                "lastReadMessageId不能为空"
        );
    }

    int belongs = chatMessageMapper.countMessageInConversation(
            conversationId,
            lastReadMessageId
    );

    if (belongs == 0) {
        throw new BusinessException(
                ErrorCode.BAD_REQUEST,
                "消息不属于当前会话"
        );
    }

    int affected = conversationMemberMapper.updateLastReadMessageId(
            conversationId,
            userId,
            lastReadMessageId
    );

    if (affected == 0) {
        throw new BusinessException(
                ErrorCode.NOT_FOUND,
                "会话成员不存在"
        );
    }

    // 保留现有 WebSocket 已读通知逻辑，但要放在事务提交之后。
    afterCommit(() -> {
        // 调用项目原有的已读状态推送方法。
    });
}
```

如果项目已有同名私有方法，重命名旧方法或直接替换，不要保留两个语义不同的同名实现。

---

## 1.8 修改 MessageController

消息查询接口：

```java
@GetMapping("/conversations/{conversationId}/messages")
public MessageDtos.ConversationMessagesResponse messages(
        @PathVariable Long conversationId,
        @RequestParam(required = false) Long beforeId,
        @RequestParam(defaultValue = "50") Integer limit
) {
    Long userId = currentUserId();
    return messageService.messages(userId, conversationId, beforeId, limit);
}
```

已读接口：

```java
@PutMapping("/conversations/{conversationId}/read")
public void markRead(
        @PathVariable Long conversationId,
        @RequestBody MessageDtos.ReadConversationRequest request
) {
    Long userId = currentUserId();

    messageService.markConversationRead(
            userId,
            conversationId,
            request.lastReadMessageId()
    );
}
```

`currentUserId()` 替换为当前项目实际使用的认证用户获取方式。

---

## 1.9 修改三个前端聊天页面

必须搜索所有消息接口调用：

```bash
git grep -n "conversations/.*/messages"
git grep -n "/messages" customer_frontend shop_frontend admin_frontend
git grep -n "ConversationMessagesResponse"
```

网页版目录名称以仓库实际结构为准，也要搜索。

前端状态至少增加：

```text
messages
nextBeforeId
hasMore
isLoadingInitial
isLoadingMore
loadedMessageIds
```

首次加载：

```text
GET /api/messages/conversations/{id}/messages?limit=50
```

上滑加载：

```text
GET /api/messages/conversations/{id}/messages?beforeId={nextBeforeId}&limit=50
```

前端必须做到：

1. 历史消息插入列表顶部。
2. 根据 `message.id` 去重。
3. 插入历史消息后保持原滚动位置。
4. `isLoadingMore=true` 时不重复请求。
5. `hasMore=false` 时停止请求。
6. WebSocket 新消息追加到底部。
7. 页面确实展示到最新消息后，调用：

```http
PUT /api/messages/conversations/{id}/read
```

请求：

```json
{
  "lastReadMessageId": 1053
}
```

8. 切换会话时清空旧会话分页状态。
9. 请求返回后确认会话 ID 仍是当前会话，避免旧请求覆盖新会话。

---

## 1.10 阶段 1 测试

### 接口测试

首次：

```http
GET /api/messages/conversations/15/messages?limit=50
```

历史：

```http
GET /api/messages/conversations/15/messages?beforeId=851&limit=50
```

验证：

- [ ] 每页最多 50 条
- [ ] 消息顺序是旧到新
- [ ] 相邻页没有重复
- [ ] 相邻页没有遗漏
- [ ] `nextBeforeId` 是当前页最小消息 ID
- [ ] 最后一页 `hasMore=false`
- [ ] GET 不改变 `last_read_message_id`
- [ ] PUT `/read` 只允许提交本会话消息
- [ ] 非会话成员不能读取
- [ ] `limit > 100` 被限制为 100
- [ ] 前端能继续上滑加载

### 回滚

1. 回滚后端和前端提交。
2. 恢复原 GET 消息实现。
3. 最后执行数据库回滚 SQL。

提交：

```bash
git add .
git commit -m "feat: add chat cursor pagination and explicit read receipt"
```

---

# 阶段 2：库存原子扣减和状态幂等恢复

本阶段不使用 Redis。

## 2.1 确认 NULL 库存语义

检查现有 `OrderService`：

```bash
git grep -n "getStock()" backend/src/main/java
git grep -n "setStock(" backend/src/main/java
```

如果原代码类似：

```java
if (dish.getStock() != null && dish.getStock() < quantity) {
    // 库存不足
}
```

说明：

```text
stock = NULL 表示不限库存
```

本文后续 SQL 按“NULL 表示不限库存”处理。

---

## 2.2 修改 DishMapper

修改：

```text
backend/src/main/java/com/order/menu/module/dish/mapper/DishMapper.java
```

增加：

```java
@Update("""
    UPDATE dishes
    SET stock = CASE
        WHEN stock IS NULL THEN NULL
        ELSE stock - #{quantity}
    END
    WHERE id = #{dishId}
      AND (stock IS NULL OR stock >= #{quantity})
    """)
int deductStock(
        @Param("dishId") Long dishId,
        @Param("quantity") Integer quantity
);

@Update("""
    UPDATE dishes
    SET stock = CASE
        WHEN stock IS NULL THEN NULL
        ELSE stock + #{quantity}
    END
    WHERE id = #{dishId}
    """)
int restoreStock(
        @Param("dishId") Long dishId,
        @Param("quantity") Integer quantity
);
```

如果实际表名不是 `dishes`，以 `SHOW CREATE TABLE` 和 `Dish` 实体的 `@TableName` 为准。

---

## 2.3 修改创建订单逻辑

删除：

```text
查询库存
Java 中 stock - quantity
updateById
```

改为：

```java
int affected = dishMapper.deductStock(
        dish.getId(),
        requestItem.quantity()
);

if (affected == 0) {
    throw new BusinessException(
            ErrorCode.CONFLICT,
            "菜品库存不足：" + dish.getName()
    );
}
```

整个创建订单方法必须保留 `@Transactional`。

多个菜品按 `dishId` 排序后再扣减：

```java
items.stream()
        .sorted(java.util.Comparator.comparing(Item::dishId))
        .forEach(...);
```

这样可以降低并发订单以不同顺序锁定菜品导致的死锁概率。

任何一个菜品扣减失败，事务整体回滚，之前已扣减的库存会由 MySQL 自动回滚。

---

## 2.4 修改取消/拒绝订单逻辑

先通过条件更新保证状态只变化一次：

在 `OrderMapper` 增加类似方法：

```java
@Update("""
    UPDATE orders
    SET status = #{targetStatus},
        updated_at = NOW()
    WHERE id = #{orderId}
      AND status IN ('PENDING', 'CONFIRMED')
    """)
int changeToCancelledIfAllowed(
        @Param("orderId") Long orderId,
        @Param("targetStatus") String targetStatus
);
```

业务逻辑：

```java
@Transactional
public void cancelOrder(Long orderId) {
    int changed = orderMapper.changeToCancelledIfAllowed(
            orderId,
            "CANCELLED"
    );

    if (changed == 0) {
        // 已取消、已完成或状态不允许，不重复恢复库存。
        return;
    }

    List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);

    items.stream()
            .sorted(java.util.Comparator.comparing(OrderItem::getDishId))
            .forEach(item -> {
                int restored = dishMapper.restoreStock(
                        item.getDishId(),
                        item.getQuantity()
                );

                if (restored == 0) {
                    throw new BusinessException(
                            ErrorCode.NOT_FOUND,
                            "恢复库存失败，菜品不存在"
                    );
                }
            });
}
```

拒单、关闭订单等会恢复库存的路径也必须使用同样模式。

---

## 2.5 并发验收

准备一个库存为 10 的测试菜品。

并发发起 100 个订单，每个购买 1 份。

验收：

- [ ] 成功订单恰好 10 个
- [ ] 失败订单提示库存不足
- [ ] 最终库存为 0
- [ ] 库存没有负数
- [ ] 同一个订单并发取消 10 次，库存只恢复一次
- [ ] 事务失败时库存自动回滚

提交：

```bash
git add .
git commit -m "fix: make stock deduction and restoration atomic"
```

---

# 阶段 3：订单 clientRequestId 数据库幂等

先由数据库保证正确性，再增加 Redis 加速。

## 3.1 数据库迁移

创建：

```text
backend/docs/db/migration/V20260722_02__order_idempotency.sql
```

```sql
ALTER TABLE orders
    ADD COLUMN client_request_id VARCHAR(64) NULL
    COMMENT '客户端订单请求幂等ID';

CREATE UNIQUE INDEX uk_orders_customer_request
    ON orders (customer_user_id, client_request_id);
```

MySQL 唯一索引允许多条 `client_request_id IS NULL` 的旧订单存在。

回滚：

```sql
DROP INDEX uk_orders_customer_request ON orders;

ALTER TABLE orders
    DROP COLUMN client_request_id;
```

---

## 3.2 修改 Order 实体与 DTO

`Order` 实体增加：

```java
private String clientRequestId;
```

`CreateOrderRequest` 增加：

```java
String clientRequestId
```

校验：

```java
if (!StringUtils.hasText(request.clientRequestId())
        || request.clientRequestId().length() > 64) {
    throw new BusinessException(
            ErrorCode.BAD_REQUEST,
            "clientRequestId不能为空且长度不能超过64"
    );
}
```

保存订单时：

```java
order.setClientRequestId(request.clientRequestId());
```

捕获唯一索引冲突：

```java
try {
    orderMapper.insert(order);
} catch (DuplicateKeyException exception) {
    Order existing = orderMapper.selectByCustomerAndClientRequestId(
            userId,
            request.clientRequestId()
    );

    if (existing != null) {
        return toOrderResponse(existing);
    }

    throw exception;
}
```

在 `OrderMapper` 增加按用户和请求 ID 查询的方法。

---

## 3.3 修改客户端

顾客端创建订单页面：

1. 用户第一次点击提交时生成 UUID。
2. 网络超时、重试、重复点击时复用同一个 UUID。
3. 订单成功或用户修改购物车内容后，才生成新的 UUID。

示例：

```dart
final String clientRequestId = const Uuid().v4();
```

网页版：

```javascript
const clientRequestId = crypto.randomUUID();
```

请求：

```json
{
  "clientRequestId": "b7ea1b4a-0bf4-4b30-b9cc-7c209cfa8b20",
  "shopId": 1,
  "items": []
}
```

### 阶段 3 验收

相同用户、相同 `clientRequestId` 并发提交 20 次：

- [ ] 数据库只有一个订单
- [ ] 所有成功响应指向同一个 `orderId`
- [ ] Redis 尚未启用也能保证幂等
- [ ] 不同用户可使用相同 UUID
- [ ] 相同用户的新订单必须使用新 UUID

提交：

```bash
git add .
git commit -m "feat: add database-backed order idempotency"
```

---

# 阶段 4：店铺详情和菜单缓存

## 4.1 创建缓存 Port

创建：

```text
backend/src/main/java/com/order/menu/module/shop/service/cache/ShopCachePort.java
```

```java
package com.order.menu.module.shop.service.cache;

import com.order.menu.module.shop.dto.ShopDtos;

import java.util.Optional;

public interface ShopCachePort {

    Optional<ShopDtos.ShopDetailResponse> getShopDetail(Long shopId);

    void putShopDetail(Long shopId, ShopDtos.ShopDetailResponse response);

    Optional<ShopDtos.MenuResponse> getMenu(Long shopId);

    void putMenu(Long shopId, ShopDtos.MenuResponse response);

    void evictShopDetail(Long shopId);

    void evictMenu(Long shopId);
}
```

DTO 名称按项目实际 `ShopDtos` 记录名称调整。

---

## 4.2 创建 NoopShopCache

```text
backend/src/main/java/com/order/menu/module/shop/service/cache/NoopShopCache.java
```

```java
package com.order.menu.module.shop.service.cache;

import com.order.menu.module.shop.dto.ShopDtos;

import java.util.Optional;

public class NoopShopCache implements ShopCachePort {

    @Override
    public Optional<ShopDtos.ShopDetailResponse> getShopDetail(Long shopId) {
        return Optional.empty();
    }

    @Override
    public void putShopDetail(
            Long shopId,
            ShopDtos.ShopDetailResponse response
    ) {
    }

    @Override
    public Optional<ShopDtos.MenuResponse> getMenu(Long shopId) {
        return Optional.empty();
    }

    @Override
    public void putMenu(Long shopId, ShopDtos.MenuResponse response) {
    }

    @Override
    public void evictShopDetail(Long shopId) {
    }

    @Override
    public void evictMenu(Long shopId) {
    }
}
```

---

## 4.3 创建 RedisShopCache

创建：

```text
backend/src/main/java/com/order/menu/module/shop/service/cache/RedisShopCache.java
```

实现要求：

1. 使用 `StringRedisTemplate`。
2. 使用 `RedisKeyFactory`。
3. 使用 `RedisJsonCodec`。
4. 所有 Redis 异常只记录警告并返回缓存未命中。
5. 菜单 TTL 为 120 秒，并增加 0～30 秒随机抖动。
6. 店铺详情 TTL 为 300 秒，并增加 0～30 秒随机抖动。

核心结构：

```java
public Optional<ShopDtos.MenuResponse> getMenu(Long shopId) {
    String key = keyFactory.shopMenu(shopId);

    try {
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(
                codec.read(value, ShopDtos.MenuResponse.class)
        );
    } catch (RuntimeException exception) {
        log.warn("Redis menu read failed, key={}", key, exception);
        return Optional.empty();
    }
}

public void putMenu(Long shopId, ShopDtos.MenuResponse response) {
    String key = keyFactory.shopMenu(shopId);

    try {
        long seconds = properties.getShopMenuTtlSeconds()
                + ThreadLocalRandom.current().nextLong(0, 31);

        redisTemplate.opsForValue().set(
                key,
                codec.write(response),
                Duration.ofSeconds(seconds)
        );
    } catch (RuntimeException exception) {
        log.warn("Redis menu write failed, key={}", key, exception);
    }
}
```

其他方法按相同模式实现。

---

## 4.4 创建条件配置

创建：

```text
backend/src/main/java/com/order/menu/module/shop/service/cache/ShopCacheConfiguration.java
```

```java
@Configuration
public class ShopCacheConfiguration {

    @Bean
    @ConditionalOnExpression(
            "'${order-menu.redis.enabled:false}' == 'true' "
            + "and '${order-menu.redis.menu-cache-enabled:false}' == 'true'"
    )
    public ShopCachePort redisShopCache(
            StringRedisTemplate redisTemplate,
            RedisKeyFactory keyFactory,
            RedisJsonCodec codec,
            OrderMenuRedisProperties properties
    ) {
        return new RedisShopCache(
                redisTemplate,
                keyFactory,
                codec,
                properties
        );
    }

    @Bean
    @ConditionalOnMissingBean(ShopCachePort.class)
    public ShopCachePort noopShopCache() {
        return new NoopShopCache();
    }
}
```

确保 `RedisShopCache` 不再同时标注 `@Component`，否则可能重复创建 Bean。

---

## 4.5 修改菜单查询流程

查询顺序必须是：

```text
1. 查询并验证店铺存在
2. 执行当前用户访问权限校验
3. 读取 Redis
4. 未命中则查询 MySQL
5. 写入 Redis
6. 返回
```

不能把权限判断缓存起来，也不能在权限判断之前直接返回共享缓存。

示例：

```java
public ShopDtos.MenuResponse menu(Long userId, Long shopId) {
    Shop shop = requireById(shopId);

    assertCanViewShop(userId, shop);

    Optional<ShopDtos.MenuResponse> cached =
            shopCachePort.getMenu(shopId);

    if (cached.isPresent()) {
        return cached.get();
    }

    ShopDtos.MenuResponse response = loadMenuFromDatabase(shop);

    shopCachePort.putMenu(shopId, response);

    return response;
}
```

---

## 4.6 创建统一失效器

创建：

```text
backend/src/main/java/com/order/menu/module/shop/service/cache/MenuCacheInvalidator.java
```

```java
@Component
@RequiredArgsConstructor
public class MenuCacheInvalidator {

    private final ShopCachePort shopCachePort;
    private final AfterCommitExecutor afterCommitExecutor;

    public void evictMenuAfterCommit(Long shopId) {
        afterCommitExecutor.execute(
                () -> shopCachePort.evictMenu(shopId)
        );
    }

    public void evictShopAfterCommit(Long shopId) {
        afterCommitExecutor.execute(() -> {
            shopCachePort.evictShopDetail(shopId);
            shopCachePort.evictMenu(shopId);
        });
    }
}
```

以下操作全部调用失效器：

- 店铺资料更新
- 店铺营业状态更新
- 分类新增、修改、删除、排序
- 菜品新增、修改、删除、上下架
- 菜品规格组和规格项修改
- 订单创建成功后库存扣减
- 订单取消/拒绝后库存恢复

不能在事务提交前删除缓存。

---

## 4.7 启用菜单缓存

PowerShell：

```powershell
$env:ORDER_MENU_MENU_CACHE_ENABLED="true"
```

重启后端。

验证 Key：

```powershell
docker exec -e REDISCLI_AUTH=1714672038qwe order-menu-redis redis-cli -n 0 SCAN 0 MATCH "oma:dev:shop:*" COUNT 100
```

### 阶段 4 验收

- [ ] 第一次访问菜单查询 MySQL
- [ ] 第二次访问命中 Redis
- [ ] 菜品修改后事务提交，菜单 Key 被删除
- [ ] 创建订单扣库存后菜单 Key 被删除
- [ ] 取消订单恢复库存后菜单 Key 被删除
- [ ] Redis 停止后菜单接口仍可查询 MySQL
- [ ] 家庭店铺等访问权限没有被缓存绕过
- [ ] 菜单旧库存最长只保留约 2～2.5 分钟

提交：

```bash
git add .
git commit -m "feat: add redis cache for shop detail and menu"
```

---

# 阶段 5：动态基础数据缓存

## 5.1 修改 MomentCachePort 为强类型接口

当前接口中的：

```java
Optional<Object> get(String key)
void put(String key, Object value)
```

替换成：

```java
public interface MomentCachePort {

    Optional<MomentBaseCacheDto> getMoment(Long momentId);

    void putMoment(Long momentId, MomentBaseCacheDto value);

    void evictMoment(Long momentId);
}
```

个性化 Feed 第一版不缓存，先删除或暂时保留 `evictUserFeeds` 的空实现。

---

## 5.2 创建 MomentBaseCacheDto

只包含用户无关数据：

```java
public record MomentBaseCacheDto(
        int schemaVersion,
        Long id,
        Long authorUserId,
        String authorNickname,
        String authorAvatar,
        String title,
        String content,
        List<String> images,
        LocalDateTime publishedAt,
        long likeCount,
        long commentCount,
        List<MomentTopicDto> topics,
        MomentQuoteDto quote
) {
}
```

不要包含：

```text
likedByMe
myReaction
favoritedByMe
canEdit
canDelete
当前用户权限
```

---

## 5.3 修改 NoopMomentCache

不要继续使用裸 `@Component`。

将其改为普通类，由配置类创建：

```java
public class NoopMomentCache implements MomentCachePort {
    // 所有 get 返回 Optional.empty()
    // put/evict 不执行操作
}
```

---

## 5.4 创建 RedisMomentCacheAdapter

使用：

```text
oma:dev:moment:base:v1:{momentId}
TTL：300 秒 + 0～30 秒抖动
```

读取失败返回 `Optional.empty()`，写入和删除失败只记录警告。

---

## 5.5 修改 MomentService

动态详情流程：

```text
1. 查询动态基本存在状态或执行必要权限校验
2. 获取 MomentBaseCacheDto
3. 未命中时查询 MySQL 并写缓存
4. 每次单独查询当前用户状态
5. 合并为最终 MomentResponse
```

以下操作事务提交后删除基础缓存：

- 编辑动态
- 删除动态
- 点赞
- 取消点赞
- 新增评论
- 删除评论
- 修改话题关联

第一版不在 Redis 中执行点赞计数 `INCR/DECR`，避免计数漂移。

个性化动态流暂不缓存。原因：

- 可见范围复杂
- 包含屏蔽关系
- 包含点赞状态
- 存在曝光记录写入

---

## 5.6 启用动态缓存

```powershell
$env:ORDER_MENU_MOMENT_CACHE_ENABLED="true"
```

验证：

```powershell
docker exec -e REDISCLI_AUTH=1714672038qwe order-menu-redis redis-cli -n 0 SCAN 0 MATCH "oma:dev:moment:*" COUNT 100
```

### 阶段 5 验收

- [ ] 两个用户查看同一动态时，点赞状态互不串用
- [ ] 点赞后当前用户状态立即正确
- [ ] 点赞计数在缓存失效后正确
- [ ] 编辑和删除动态后旧缓存不可见
- [ ] Redis 故障时动态详情正常从 MySQL 返回
- [ ] 个性化 Feed 尚未缓存

提交：

```bash
git add .
git commit -m "feat: add redis cache for user-independent moment data"
```

---

# 阶段 6：聊天最近消息缓存

## 6.1 缓存范围

MySQL：

```text
保存全部聊天历史
```

Redis：

```text
每个活跃会话最近 200 个消息 ID
单条消息 DTO，TTL 30 分钟
```

旧消息分页始终查 MySQL。

---

## 6.2 创建 ChatMessageCacheDto

缓存发送时快照，不缓存当前用户专属字段：

```java
public record ChatMessageCacheDto(
        int schemaVersion,
        Long id,
        Long conversationId,
        Long senderUserId,
        String type,
        String content,
        String deliveryStatus,
        LocalDateTime createdAt,
        LocalDateTime recalledAt,
        Long quotedMessageId,
        String imageStoragePath,
        String imageUrl
        // 继续加入项目 ChatMessageResponse 所需的共享快照字段
) {
}
```

如果图片 URL 是短期签名 URL，不要长期缓存临时 URL。优先缓存存储路径，返回时重新生成 URL。

---

## 6.3 创建 ChatCachePort

至少定义：

```java
public interface ChatCachePort {

    Optional<List<ChatMessageCacheDto>> getRecentMessages(
            Long conversationId,
            Long userId,
            int limit
    );

    void cacheRecentMessages(
            Long conversationId,
            List<ChatMessageCacheDto> messages
    );

    void appendMessageAfterCommit(ChatMessageCacheDto message);

    void evictMessageAfterCommit(
            Long conversationId,
            Long messageId
    );
}
```

Noop 实现返回缓存未命中。

---

## 6.4 RedisChatCache 数据结构

消息 ID：

```text
ZSET：oma:dev:chat:recent-ids:v1:{conversationId}
score = messageId
member = messageId
保留最近 200 条
TTL = 30 分钟
```

单条消息：

```text
STRING：oma:dev:chat:message:v1:{messageId}
value = ChatMessageCacheDto JSON
TTL = 30 分钟
```

发送成功并提交事务后：

```text
SET messageKey JSON EX 1800
ZADD recentKey messageId messageId
ZREMRANGEBYRANK recentKey 0 -201
EXPIRE recentKey 1800
```

---

## 6.5 最新一页读取算法

只在：

```text
beforeId == null
```

时尝试 Redis。

算法：

1. 从 ZSET 读取最新 100 个 ID。
2. MGET 100 个消息 Key。
3. 任一消息 Key 缺失，直接回退 MySQL。
4. 批量查询当前用户在这 100 条中的删除记录。
5. 过滤用户删除消息。
6. 如果可见消息至少有 `limit + 1` 条：
   - 返回前 `limit` 条
   - `hasMore=true`
7. 如果不足 `limit + 1`：
   - 回退 MySQL，保证 `hasMore` 准确
8. MySQL 查询结果回填 Redis。

该策略会让消息少的会话继续查询 MySQL，但逻辑最简单可靠。活跃的大型会话可以命中 Redis。

历史页：

```text
beforeId != null → 直接查询 MySQL
```

---

## 6.6 用户删除消息过滤

修改 `ChatMessageDeletionMapper`，增加按本批消息 ID 查询：

```sql
SELECT message_id
FROM chat_message_deletions
WHERE user_id = #{userId}
  AND message_id IN (...)
```

不要：

- 查询用户全部删除记录
- 从共享 ZSET 删除该消息
- 为每个用户创建完整消息列表缓存

用户删除只影响该用户，不影响其他会话成员。

---

## 6.7 消息变更失效矩阵

| 操作 | Redis 处理 |
|---|---|
| 发送消息 | afterCommit 写消息 Key 和 ZSET |
| 撤回消息 | afterCommit 删除消息 Key，下一次从 DB 回填 |
| 用户删除自己的消息 | 不修改共享缓存，只由 DB 删除记录过滤 |
| 删除整个会话 | 删除 recent-ids；消息 Key 等待 TTL |
| 修改图片路径/URL | 删除对应消息 Key |
| 系统消息创建 | 与普通消息相同写入缓存 |
| 分享订单/菜品/店铺/动态 | 缓存发送时快照 |

所有写缓存和删缓存都必须在事务提交后执行。

---

## 6.8 启用聊天缓存

```powershell
$env:ORDER_MENU_CHAT_CACHE_ENABLED="true"
```

验证：

```powershell
docker exec -e REDISCLI_AUTH=1714672038qwe order-menu-redis redis-cli -n 0 SCAN 0 MATCH "oma:dev:chat:*" COUNT 100
```

### 阶段 6 验收

- [ ] 最新一页可由 Redis 返回
- [ ] 历史页始终走 MySQL
- [ ] 用户删除消息不会影响其他用户
- [ ] 删除消息较多时仍可回退 DB 并返回完整 50 条
- [ ] 撤回后不显示旧内容
- [ ] WebSocket 新消息和分页消息通过 `messageId` 去重
- [ ] Redis 断开后聊天仍正常
- [ ] Redis 中最多保留每会话最近 200 个 ID

提交：

```bash
git add .
git commit -m "feat: cache recent chat messages in redis"
```

---

# 阶段 7：未读数量缓存

## 7.1 第一版使用“查询结果缓存 + 删除失效”

不要一开始使用复杂的 `INCR/DECR`，避免异常导致数字漂移。

通知 Key：

```text
oma:dev:notification:unread:v1:{userId}
```

聊天 Key：

```text
oma:dev:chat:unread:v1:{userId}:{conversationId}
oma:dev:chat:unread-total:v1:{userId}
```

读取：

```text
Redis 命中 → 返回
Redis 未命中 → MySQL COUNT → 写 Redis 5 分钟
```

失效：

- 创建通知：删除通知未读 Key
- 标记通知已读：删除通知未读 Key
- 全部通知已读：删除通知未读 Key
- 收到聊天消息：删除目标用户的会话未读 Key和总未读 Key
- PUT `/read`：删除当前用户的会话未读 Key和总未读 Key

---

## 7.2 启用

```powershell
$env:ORDER_MENU_UNREAD_CACHE_ENABLED="true"
```

### 阶段 7 验收

- [ ] 未读数第一次从 MySQL 读取
- [ ] 第二次命中 Redis
- [ ] 新通知后缓存被删除
- [ ] 已读后缓存被删除
- [ ] 聊天新消息后目标用户未读数刷新
- [ ] Redis 故障时重新从 MySQL 统计
- [ ] 未读数量不出现负数

提交：

```bash
git add .
git commit -m "feat: cache notification and chat unread counts"
```

---

# 阶段 8：订单 Redis 幂等加速

数据库唯一约束已在阶段 3 上线。Redis 只减少重复请求压力。

## 8.1 幂等 Key

```text
oma:dev:order:idempotency:v1:{userId}:{clientRequestId}
```

状态：

```text
PENDING
SUCCESS:{orderId}
```

TTL：

```text
PENDING：120 秒
SUCCESS：600 秒
```

---

## 8.2 创建 IdempotencyService

获取请求：

```java
Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
        key,
        "PENDING",
        Duration.ofSeconds(
                properties.getIdempotencyPendingTtlSeconds()
        )
);
```

处理：

```text
SET NX 成功：
继续创建订单

Key = SUCCESS:{orderId}：
返回原订单

Key = PENDING：
先查询 MySQL
    已存在 → 返回原订单
    不存在 → 返回 ORDER_CREATING，不阻塞等待

Redis 异常：
直接执行数据库幂等流程
```

不要在请求线程中循环等待 `PENDING`。

---

## 8.3 OrderService 流程

```text
1. 校验 clientRequestId
2. 调用 Redis acquire
3. 如果返回已有 orderId，直接查询并返回
4. 如果返回 PENDING 且 DB 还没有订单，返回 ORDER_CREATING
5. 创建订单
6. MySQL 唯一索引最终兜底
7. 事务提交后写 SUCCESS:{orderId}
8. 事务回滚后删除 PENDING
```

如果服务在 DB 提交后、写 Redis 前崩溃：

- 下一次请求即使 Redis 仍是 PENDING
- 也会查到 MySQL 中已有订单
- 然后返回原订单

---

## 8.4 启用

```powershell
$env:ORDER_MENU_IDEMPOTENCY_ENABLED="true"
```

### 阶段 8 验收

相同 `clientRequestId` 并发提交 20 次：

- [ ] 数据库只有一个订单
- [ ] 不会重复扣库存
- [ ] 成功请求返回相同 `orderId`
- [ ] PENDING 不会无限等待
- [ ] Redis 停止后，数据库唯一索引仍保证一个订单
- [ ] Redis Key 到期后，DB 查询仍能返回原订单

提交：

```bash
git add .
git commit -m "feat: add redis acceleration for order idempotency"
```

---

# 阶段 9：多实例 WebSocket Pub/Sub

当前如果后端只有一个实例：

```powershell
$env:ORDER_MENU_WEBSOCKET_BUS_ENABLED="false"
```

暂不开发或不启用该功能。

只有后端部署两个及以上实例时执行本阶段。

## 9.1 事件结构

不要使用无法明确反序列化的 `Object payload`。

```java
public record WsEvent(
        int schemaVersion,
        String eventId,
        String eventType,
        String targetType,
        Long targetId,
        JsonNode payload,
        Instant occurredAt,
        String sourceInstanceId
) {
}
```

Channel：

```text
oma:dev:ws:events
```

生产：

```text
oma:prod:ws:events
```

---

## 9.2 投递规则

Redis 开启：

```text
业务层只发布到 Redis
所有实例，包括发送实例，订阅 Channel
每个实例只向本地 WsSessionRegistry 中存在的 Session 推送
```

Redis 关闭：

```text
直接执行当前本地 WsSessionRegistry 推送
```

禁止同时：

```text
本地直接推送 + Redis 发布
```

否则发送实例会收到两次事件。

---

## 9.3 重连恢复

Redis Pub/Sub 不保存离线事件。

客户端 WebSocket 重连后必须重新请求：

- 最新聊天消息
- 未读数量
- 通知列表
- 订单最新状态
- 店铺营业状态

MySQL 仍是最终数据源。

### 阶段 9 验收

- [ ] 两个后端实例之间可以跨实例推送
- [ ] 发送实例不会重复收到两次
- [ ] 目标用户只在持有本地 Session 的实例收到
- [ ] Redis Pub/Sub 中断后应用不崩溃
- [ ] 客户端重连能通过 REST 恢复状态
- [ ] `eventId` 可用于前端短时间去重

---

# 阶段 10：监控、压测、灰度和上线

## 10.1 增加依赖

`pom.xml`：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

配置只暴露必要端点：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

Redis 是可降级依赖，Redis DOWN 不应该直接让核心业务 readiness 失败。将 Redis 状态作为独立指标告警，不把它作为唯一的服务可用性判断。

---

## 10.2 必须监控

- Redis 连接失败次数
- Redis 读失败次数
- Redis 写失败次数
- 菜单缓存命中/未命中
- 动态缓存命中/未命中
- 聊天缓存命中/未命中
- MySQL 回源次数
- Redis 命令延迟
- Redis 内存使用
- Redis `evicted_keys`
- Redis `rejected_connections`
- Lettuce 连接池等待时间
- 聊天接口 p50/p95/p99
- 菜单接口 p50/p95/p99
- 动态详情 p50/p95/p99

查看 Redis 信息：

```powershell
docker exec -e REDISCLI_AUTH=1714672038qwe order-menu-redis redis-cli -n 0 INFO memory
docker exec -e REDISCLI_AUTH=1714672038qwe order-menu-redis redis-cli -n 0 INFO stats
docker exec -e REDISCLI_AUTH=1714672038qwe order-menu-redis redis-cli -n 0 INFO clients
```

---

## 10.3 故障测试

### Redis 在应用运行中停止

```powershell
docker stop order-menu-redis
```

验证：

- [ ] 菜单仍可查询
- [ ] 动态仍可查询
- [ ] 聊天仍可分页
- [ ] 通知未读数从 MySQL 返回
- [ ] 订单仍可创建且不重复
- [ ] 应用没有退出

恢复：

```powershell
docker start order-menu-redis
```

验证应用能够自动恢复 Redis 连接。

### noeviction 写失败模拟

不能在生产随意测试。测试环境可临时设置较小 `maxmemory`，写满后验证：

- 缓存写失败只告警
- 数据读取回源 MySQL
- 订单数据库唯一约束仍生效

---

## 10.4 并发压测清单

### 库存

```text
库存 10
并发下单 100
成功 10
库存 0
无负库存
```

### 取消订单

```text
同一订单并发取消 10 次
状态只成功变化一次
库存只恢复一次
```

### 幂等

```text
同一用户 + 同一 clientRequestId
并发请求 20 次
数据库只有一个订单
```

### 聊天分页

```text
会话 10000 条消息
每页 50
页间无重复、无遗漏
```

### 缓存击穿

同一个热门菜单 Key 删除后，并发 100 个请求。

第一版可能出现多次 MySQL 回源，但必须保证：

- 返回结果正确
- 应用不崩溃
- 数据库压力可接受

如果后续出现明显击穿，再增加单机锁或分布式互斥重建，不在第一版提前增加复杂度。

---

## 10.5 灰度开启顺序

生产或测试环境必须按以下顺序开启开关，每次只开一个模块：

```text
1. ORDER_MENU_REDIS_ENABLED=true
2. ORDER_MENU_MENU_CACHE_ENABLED=true
3. ORDER_MENU_MOMENT_CACHE_ENABLED=true
4. ORDER_MENU_CHAT_CACHE_ENABLED=true
5. ORDER_MENU_UNREAD_CACHE_ENABLED=true
6. ORDER_MENU_IDEMPOTENCY_ENABLED=true
7. ORDER_MENU_WEBSOCKET_BUS_ENABLED=true（仅多实例）
```

每开启一个模块，至少观察：

- 错误日志
- Redis 连接
- DB QPS
- 接口 p95
- 数据一致性
- 缓存命中率

发现异常时关闭对应模块开关并重启，不需要同时关闭全部 Redis 功能。

---

# 最终文件清单

## 新增文件

```text
backend/docker-compose.redis.yml
backend/.env.redis.example
backend/redis/redis.conf

backend/src/main/java/com/order/menu/config/OrderMenuRedisProperties.java
backend/src/main/java/com/order/menu/config/RedisInfrastructureConfiguration.java

backend/src/main/java/com/order/menu/common/redis/RedisKeyFactory.java
backend/src/main/java/com/order/menu/common/redis/RedisJsonCodec.java
backend/src/main/java/com/order/menu/common/transaction/AfterCommitExecutor.java

backend/src/main/java/com/order/menu/module/shop/service/cache/ShopCachePort.java
backend/src/main/java/com/order/menu/module/shop/service/cache/NoopShopCache.java
backend/src/main/java/com/order/menu/module/shop/service/cache/RedisShopCache.java
backend/src/main/java/com/order/menu/module/shop/service/cache/ShopCacheConfiguration.java
backend/src/main/java/com/order/menu/module/shop/service/cache/MenuCacheInvalidator.java

backend/src/main/java/com/order/menu/module/moment/dto/MomentBaseCacheDto.java
backend/src/main/java/com/order/menu/module/moment/service/RedisMomentCacheAdapter.java
backend/src/main/java/com/order/menu/module/moment/service/MomentCacheConfiguration.java

backend/src/main/java/com/order/menu/module/message/service/cache/ChatCachePort.java
backend/src/main/java/com/order/menu/module/message/service/cache/NoopChatCache.java
backend/src/main/java/com/order/menu/module/message/service/cache/RedisChatCache.java
backend/src/main/java/com/order/menu/module/message/dto/ChatMessageCacheDto.java

backend/src/main/java/com/order/menu/module/order/service/IdempotencyService.java

backend/src/main/java/com/order/menu/websocket/WsEvent.java
backend/src/main/java/com/order/menu/websocket/RedisWsEventPublisher.java
backend/src/main/java/com/order/menu/websocket/RedisWsEventSubscriber.java

backend/docs/db/migration/V20260722_01__chat_cursor_and_read_receipt.sql
backend/docs/db/migration/V20260722_02__order_idempotency.sql
backend/docs/db/migration/rollback/V20260722_01__chat_cursor_and_read_receipt_rollback.sql
backend/docs/db/migration/rollback/V20260722_02__order_idempotency_rollback.sql

backend/src/test/java/com/order/menu/RedisConnectionTest.java
```

## 修改文件

```text
backend/pom.xml
backend/src/main/resources/application.yml
.gitignore

MessageController.java
MessageService.java
MessageDtos.java
ChatMessageMapper.java
ChatMessageDeletionMapper.java
ConversationMemberMapper.java
ConversationMember.java

OrderService.java
OrderDtos.java
Order.java
OrderMapper.java
DishMapper.java

ShopService.java
DishService.java
DishOptionService.java
CategoryService.java

MomentCachePort.java
NoopMomentCache.java
MomentService.java

WsPushService.java（阶段 9）
WsSessionRegistry.java（阶段 9，仅少量调整）

顾客端聊天页面和 API Client
商家端聊天页面和 API Client
网页版聊天页面和 API Client
顾客端创建订单页面
网页版创建订单页面
```

---

# 每次提交前检查

```bash
git diff --check
git status
```

后端：

```powershell
cd backend
mvn clean test
```

Redis：

```powershell
docker exec -e REDISCLI_AUTH=1714672038qwe order-menu-redis redis-cli -n 0 PING
docker exec -e REDISCLI_AUTH=1714672038qwe order-menu-redis redis-cli -n 0 SCAN 0 MATCH "oma:dev:*" COUNT 100
```

不要执行：

```text
KEYS *
FLUSHALL
FLUSHDB
```

---

# 完成定义

只有同时满足以下条件，Redis 改造才算完成：

- [ ] Redis 停止时核心业务仍然工作
- [ ] 聊天不会再一次加载全部历史消息
- [ ] GET 消息不会自动把整个会话标记已读
- [ ] 库存不会超卖或重复恢复
- [ ] 重复订单请求只生成一个订单
- [ ] 菜单修改和库存变化后缓存正确失效
- [ ] 动态缓存不会串用不同用户的点赞/权限状态
- [ ] 聊天历史永久保存于 MySQL
- [ ] Redis 只保存最近聊天消息
- [ ] 用户删除消息不会影响其他用户
- [ ] 所有缓存失效都在事务提交后执行
- [ ] 所有 Redis 模块都可以通过配置单独关闭
- [ ] Redis Key 包含环境名且不滥用花括号
- [ ] Redis 密码没有提交到 Git
- [ ] 前端已支持聊天游标分页和独立已读上报
- [ ] 多实例部署前 WebSocket Redis 总线保持关闭
