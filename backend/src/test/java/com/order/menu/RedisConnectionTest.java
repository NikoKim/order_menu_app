package com.order.menu;

import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.ratelimit.RateLimiter;
import com.order.menu.module.message.dto.ChatMessageCacheDto;
import com.order.menu.module.message.entity.ChatMessage;
import com.order.menu.module.message.service.cache.ChatCachePort;
import com.order.menu.module.message.service.cache.RedisChatCache;
import com.order.menu.module.message.service.cache.RedisUnreadCountCache;
import com.order.menu.module.message.service.cache.UnreadCountCache;
import com.order.menu.module.message.service.presence.ChatPresenceService;
import com.order.menu.module.moment.dto.MomentBaseCacheDto;
import com.order.menu.module.moment.dto.MomentDtos;
import com.order.menu.module.moment.dto.MomentIdPageCacheDto;
import com.order.menu.module.moment.dto.MomentStatsCacheDto;
import com.order.menu.module.moment.service.MomentCachePort;
import com.order.menu.module.moment.service.RedisMomentCacheAdapter;
import com.order.menu.module.order.service.IdempotencyService;
import com.order.menu.module.shop.service.cache.RedisShopCache;
import com.order.menu.module.shop.service.cache.ShopCachePort;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.data.redis.database=0",
        "order-menu.redis.enabled=true",
        "order-menu.redis.menu-cache-enabled=true",
        "order-menu.redis.moment-cache-enabled=true",
        "order-menu.redis.chat-cache-enabled=true",
        "order-menu.redis.unread-cache-enabled=true",
        "order-menu.redis.idempotency-enabled=true",
        "order-menu.redis.realtime.presence-enabled=true",
        "order-menu.redis.realtime.rate-limit-enabled=true"
})
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired private RedisKeyFactory keyFactory;
    @Autowired private ShopCachePort shopCachePort;
    @Autowired private MomentCachePort momentCachePort;
    @Autowired private ChatCachePort chatCachePort;
    @Autowired private UnreadCountCache unreadCountCache;
    @Autowired private IdempotencyService idempotencyService;
    @Autowired private ChatPresenceService chatPresenceService;
    @Autowired private RateLimiter rateLimiter;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldReadAndWriteRedis() {
        String key = "oma:dev:test:junit";

        redisTemplate.opsForValue().set(key, "ok");
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("ok");
        redisTemplate.delete(key);
    }

    @Test
    void shouldWireAllEnabledRedisAdapters() {
        assertThat(shopCachePort).isInstanceOf(RedisShopCache.class);
        assertThat(momentCachePort).isInstanceOf(RedisMomentCacheAdapter.class);
        assertThat(chatCachePort).isInstanceOf(RedisChatCache.class);
        assertThat(unreadCountCache).isInstanceOf(RedisUnreadCountCache.class);
    }

    @Test
    void shouldRoundTripCompleteChatMessage() {
        long suffix = System.nanoTime();
        long conversationId = 9_000_000_000L + suffix % 100_000_000L;
        long messageId = 8_000_000_000L + suffix % 100_000_000L;
        ChatMessage message = new ChatMessage();
        message.setId(messageId);
        message.setConversationId(conversationId);
        message.setSenderUserId(7L);
        message.setType("SHOP_SHARE");
        message.setContent("完整缓存消息");
        message.setSharedShopId(42L);
        message.setSharedShopName("Redis Test Shop");
        message.setSharedShopDescription("share fields must survive cache serialization");
        message.setCreatedAt(LocalDateTime.now());

        try {
            chatCachePort.cacheRecentMessages(conversationId, List.of(ChatMessageCacheDto.from(message)));
            ChatMessage cached = chatCachePort.getRecentMessages(conversationId, 1)
                    .orElseThrow().get(0).toEntity();

            assertThat(cached.getId()).isEqualTo(messageId);
            assertThat(cached.getSharedShopId()).isEqualTo(42L);
            assertThat(cached.getSharedShopName()).isEqualTo("Redis Test Shop");
            assertThat(cached.getSharedShopDescription()).contains("survive");
        } finally {
            redisTemplate.delete(keyFactory.chatMessage(messageId));
            redisTemplate.delete(keyFactory.chatRecentIds(conversationId));
        }
    }

    @Test
    void shouldCacheUnreadCountsAndUseTokenizedIdempotency() {
        long suffix = System.nanoTime();
        long userId = 7_000_000_000L + suffix % 100_000_000L;
        long conversationId = 6_000_000_000L + suffix % 100_000_000L;
        String clientRequestId = "redis-test-" + suffix;
        try {
            unreadCountCache.putChatUnread(userId, conversationId, 3L);
            unreadCountCache.putChatUnreadTotal(userId, 5L);
            assertThat(unreadCountCache.getChatUnread(userId, conversationId)).contains(3L);
            assertThat(unreadCountCache.getChatUnreadTotal(userId)).contains(5L);

            IdempotencyService.AcquireResult acquired = idempotencyService.acquire(userId, clientRequestId);
            assertThat(acquired.type()).isEqualTo(IdempotencyService.AcquireResult.Type.ACQUIRED);
            assertThat(idempotencyService.acquire(userId, clientRequestId).type())
                    .isEqualTo(IdempotencyService.AcquireResult.Type.PENDING);
            idempotencyService.markSuccess(userId, clientRequestId, 123L, acquired.pendingToken());
            IdempotencyService.AcquireResult existing = idempotencyService.acquire(userId, clientRequestId);
            assertThat(existing.type()).isEqualTo(IdempotencyService.AcquireResult.Type.EXISTING);
            assertThat(existing.existingOrderId()).isEqualTo(123L);
        } finally {
            redisTemplate.delete(keyFactory.chatUnread(userId, conversationId));
            redisTemplate.delete(keyFactory.chatUnreadTotal(userId));
            redisTemplate.delete(keyFactory.orderIdempotency(userId, clientRequestId));
        }
    }

    @Test
    void shouldRoundTripMomentP0AndP1Caches() {
        long suffix = System.nanoTime();
        long userId = 4_000_000_000L + suffix % 100_000_000L;
        long momentId = 3_000_000_000L + suffix % 100_000_000L;
        long topicId = 2_000_000_000L + suffix % 100_000_000L;
        MomentIdPageCacheDto page = new MomentIdPageCacheDto(List.of(momentId), 1, 10, 1, false);
        MomentStatsCacheDto stats = new MomentStatsCacheDto(
                2, 3, 1, 4, 2, 2,
                List.of(new MomentBaseCacheDto.ReactionSummary("👍", 2)));
        MomentDtos.TopicResponse topic = new MomentDtos.TopicResponse(topicId, "Redis", 1L, 1L, 1L);

        try {
            momentCachePort.putFeed(userId, "RECOMMENDED", page);
            momentCachePort.putTopicMoments(userId, topicId, page);
            momentCachePort.putUserMoments(userId, userId, page);
            momentCachePort.putUnread(userId, 5);
            momentCachePort.putHotMomentIds(List.of(momentId));
            momentCachePort.putHotTopics(List.of(topic));
            momentCachePort.putFirstComments(momentId, page);
            momentCachePort.putHotCommentId(momentId, 99L);
            momentCachePort.putStats(momentId, stats);

            assertThat(momentCachePort.getFeed(userId, "RECOMMENDED", 1, 10)).contains(page);
            assertThat(momentCachePort.getTopicMoments(userId, topicId, 1, 10)).contains(page);
            assertThat(momentCachePort.getUserMoments(userId, userId, 1, 10)).contains(page);
            assertThat(momentCachePort.getUnread(userId)).contains(5L);
            assertThat(momentCachePort.getHotMomentIds()).contains(List.of(momentId));
            assertThat(momentCachePort.getHotTopics()).contains(List.of(topic));
            assertThat(momentCachePort.getFirstComments(momentId, 10)).contains(page);
            assertThat(momentCachePort.getHotCommentId(momentId)).contains(99L);
            assertThat(momentCachePort.getStats(momentId)).contains(stats);
        } finally {
            redisTemplate.delete(List.of(
                    keyFactory.momentFeed(userId, "RECOMMENDED", 0, 0, 1, 10),
                    keyFactory.momentTopicIds(userId, topicId, 0, 1, 10),
                    keyFactory.momentUserIds(userId, userId, 0, 1, 10),
                    keyFactory.momentUnread(userId), keyFactory.momentHotIds(),
                    keyFactory.momentHotTopics(), keyFactory.momentCommentsFirst(momentId, 10),
                    keyFactory.momentHotComment(momentId), keyFactory.momentStats(momentId)));
        }
    }

    @Test
    void shouldTrackOnlinePresenceWithShortLivedKey() {
        long userId = 5_000_000_000L + System.nanoTime() % 100_000_000L;
        try {
            chatPresenceService.heartbeat(userId);
            assertThat(chatPresenceService.isOnline(userId)).isTrue();
        } finally {
            redisTemplate.delete(keyFactory.userOnline(userId));
        }
    }

    @Test
    void shouldEnforceRateLimitAtomically() {
        String action = "junit";
        String subject = "request-" + System.nanoTime();
        try {
            assertThat(rateLimiter.tryAcquire(action, subject, 2, Duration.ofMinutes(1))).isTrue();
            assertThat(rateLimiter.tryAcquire(action, subject, 2, Duration.ofMinutes(1))).isTrue();
            assertThat(rateLimiter.tryAcquire(action, subject, 2, Duration.ofMinutes(1))).isFalse();
        } finally {
            redisTemplate.delete(keyFactory.rateLimit(action, subject));
        }
    }

    @Test
    void shouldHaveRequiredRedisMigrationSchema() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND ((TABLE_NAME = 'conversation_members' AND COLUMN_NAME = 'last_read_message_id')
                    OR (TABLE_NAME = 'orders' AND COLUMN_NAME = 'client_request_id'))
                """, String.class);
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND INDEX_NAME IN ('idx_chat_messages_conversation_id_id',
                    'uk_chat_message_deletion_user_message', 'uk_orders_customer_request')
                """, String.class);

        assertThat(indexes).containsExactlyInAnyOrder(
                "idx_chat_messages_conversation_id_id", "uk_chat_message_deletion_user_message",
                "uk_orders_customer_request");
        assertThat(columns).containsExactlyInAnyOrder(
                "conversation_members.last_read_message_id", "orders.client_request_id");
    }
}
