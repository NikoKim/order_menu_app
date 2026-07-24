package com.order.menu.module.moment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.transaction.AfterCommitExecutor;
import com.order.menu.config.OrderMenuRedisProperties;
import com.order.menu.module.message.mapper.ChatMessageMapper;
import com.order.menu.module.moment.entity.MomentUserPreference;
import com.order.menu.module.moment.mapper.MomentUserPreferenceMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class MomentAccessCacheService {

    private static final long MIN_TTL_SECONDS = 10;
    // Authorization snapshots are intentionally much shorter-lived than content caches.
    private static final long MAX_TTL_SECONDS = 15;
    private static final String REQUEST_ATTRIBUTE_PREFIX =
            MomentAccessCacheService.class.getName() + ".snapshot.";

    private final StringRedisTemplate redisTemplate;
    private final RedisJsonCodec codec;
    private final RedisFailurePolicy failurePolicy;
    private final AfterCommitExecutor afterCommitExecutor;
    private final OrderMenuRedisProperties properties;
    private final MomentUserPreferenceMapper preferenceMapper;
    private final ChatMessageMapper chatMessageMapper;

    public MomentAccessCacheService(
            StringRedisTemplate redisTemplate,
            RedisJsonCodec codec,
            RedisFailurePolicy failurePolicy,
            AfterCommitExecutor afterCommitExecutor,
            OrderMenuRedisProperties properties,
            MomentUserPreferenceMapper preferenceMapper,
            ChatMessageMapper chatMessageMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.codec = codec;
        this.failurePolicy = failurePolicy;
        this.afterCommitExecutor = afterCommitExecutor;
        this.properties = properties;
        this.preferenceMapper = preferenceMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    public MomentAccessSnapshot accessFor(Long userId) {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        String requestKey = REQUEST_ATTRIBUTE_PREFIX + userId;
        if (request != null) {
            Object value = request.getAttribute(requestKey, RequestAttributes.SCOPE_REQUEST);
            if (value instanceof MomentAccessSnapshot snapshot) {
                return snapshot;
            }
        }
        if (!cacheEnabled()) {
            return cacheInRequest(request, requestKey, loadFromDatabase(userId));
        }
        String key = accessKey(userId);
        Optional<MomentAccessSnapshot> cached = failurePolicy.execute(
                "moment-access",
                "read",
                key,
                () -> Optional.ofNullable(redisTemplate.opsForValue().get(key))
                        .map(value -> codec.read(value, MomentAccessSnapshot.class)),
                Optional.empty()
        );
        if (cached.isPresent()) {
            failurePolicy.recordCacheResult("moment-access", true);
            return cacheInRequest(request, requestKey, cached.get());
        }

        failurePolicy.recordCacheResult("moment-access", false);
        MomentAccessSnapshot loaded = loadFromDatabase(userId);
        failurePolicy.execute("moment-access", "write", key, () ->
                redisTemplate.opsForValue().set(
                        key,
                        codec.write(loaded),
                        Duration.ofSeconds(accessTtlSeconds()
                                + ThreadLocalRandom.current().nextLong(0, 6))
                ));
        return cacheInRequest(request, requestKey, loaded);
    }

    public void invalidatePreference(Long userId, Long targetUserId) {
        invalidateUsers(List.of(userId, targetUserId));
    }

    public void invalidateRelationship(Collection<Long> userIds) {
        invalidateUsers(userIds);
    }

    private void invalidateUsers(Collection<Long> userIds) {
        if (!cacheEnabled() || userIds == null || userIds.isEmpty()) {
            return;
        }
        List<String> keys = userIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(this::accessKey)
                .toList();
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null) {
            userIds.stream().filter(java.util.Objects::nonNull).distinct().forEach(id ->
                    request.removeAttribute(REQUEST_ATTRIBUTE_PREFIX + id,
                            RequestAttributes.SCOPE_REQUEST));
        }
        if (keys.isEmpty()) {
            return;
        }
        afterCommitExecutor.execute(() -> failurePolicy.execute(
                "moment-access",
                "evict",
                "moment:access",
                () -> redisTemplate.delete(keys)
        ));
    }

    private MomentAccessSnapshot loadFromDatabase(Long userId) {
        Set<Long> blocked = new HashSet<>();
        preferenceMapper.selectList(new LambdaQueryWrapper<MomentUserPreference>()
                .and(query -> query
                        .eq(MomentUserPreference::getUserId, userId)
                        .eq(MomentUserPreference::getHideTargetMoments, true)
                        .or()
                        .eq(MomentUserPreference::getTargetUserId, userId)
                        .eq(MomentUserPreference::getHideMyMoments, true)))
                .forEach(preference -> blocked.add(
                        preference.getUserId().equals(userId)
                                ? preference.getTargetUserId()
                                : preference.getUserId()));
        Set<Long> related = new HashSet<>(
                chatMessageMapper.selectEstablishedDirectChatUserIds(userId));
        return new MomentAccessSnapshot(blocked, related);
    }

    private MomentAccessSnapshot cacheInRequest(
            RequestAttributes request,
            String requestKey,
            MomentAccessSnapshot snapshot
    ) {
        if (request != null) {
            request.setAttribute(requestKey, snapshot, RequestAttributes.SCOPE_REQUEST);
        }
        return snapshot;
    }

    private boolean cacheEnabled() {
        return properties.isEnabled() && properties.isMomentCacheEnabled();
    }

    private long accessTtlSeconds() {
        return Math.max(MIN_TTL_SECONDS,
                Math.min(MAX_TTL_SECONDS, properties.getMomentUnreadTtlSeconds()));
    }

    private String accessKey(Long userId) {
        return "oma:" + properties.getEnvironment() + ":moment:access:v1:" + userId;
    }
}
