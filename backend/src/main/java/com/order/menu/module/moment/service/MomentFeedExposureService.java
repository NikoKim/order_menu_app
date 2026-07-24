package com.order.menu.module.moment.service;

import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.common.transaction.AfterCommitExecutor;
import com.order.menu.config.OrderMenuRedisProperties;
import com.order.menu.module.moment.mapper.MomentFeedExposureMapper;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class MomentFeedExposureService {

    static final int MAX_CACHED_EXPOSURES = 2048;
    static final Duration CACHE_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final RedisFailurePolicy failurePolicy;
    private final AfterCommitExecutor afterCommitExecutor;
    private final OrderMenuRedisProperties properties;
    private final MomentFeedExposureMapper exposureMapper;

    public MomentFeedExposureService(
            StringRedisTemplate redisTemplate,
            RedisFailurePolicy failurePolicy,
            AfterCommitExecutor afterCommitExecutor,
            OrderMenuRedisProperties properties,
            MomentFeedExposureMapper exposureMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.failurePolicy = failurePolicy;
        this.afterCommitExecutor = afterCommitExecutor;
        this.properties = properties;
        this.exposureMapper = exposureMapper;
    }

    public Set<Long> findExposed(Long userId, String feedType, Collection<Long> momentIds) {
        List<Long> candidates = normalizedIds(momentIds);
        if (candidates.isEmpty()) {
            return Set.of();
        }
        if (!cacheEnabled()) {
            return selectFromDatabase(userId, feedType, candidates);
        }

        String key = exposureKey(userId, feedType);
        String checkedKey = checkedKey(userId, feedType);
        Optional<CachedExposureState> cachedState = failurePolicy.execute(
                "moment-exposure",
                "read",
                key,
                () -> Optional.of(new CachedExposureState(
                        parseIds(redisTemplate.opsForZSet().range(key, 0, -1)),
                        parseIds(redisTemplate.opsForZSet().range(checkedKey, 0, -1)))),
                Optional.empty()
        );
        if (cachedState.isEmpty()) {
            failurePolicy.recordCacheResult("moment-exposure", false);
            return selectFromDatabase(userId, feedType, candidates);
        }

        Set<Long> exposed = new HashSet<>(cachedState.get().exposed());
        Set<Long> resolved = new HashSet<>(cachedState.get().checked());
        resolved.addAll(exposed);
        List<Long> misses = candidates.stream().filter(id -> !resolved.contains(id)).toList();
        if (misses.isEmpty()) {
            failurePolicy.recordCacheResult("moment-exposure", true);
            exposed.retainAll(candidates);
            return exposed;
        }

        // An absent member is not proof of no exposure because the ZSET is bounded.
        Set<Long> databaseMatches = selectFromDatabase(userId, feedType, misses);
        List<Long> databaseMisses = misses.stream()
                .filter(id -> !databaseMatches.contains(id))
                .toList();
        cacheAfterCommit(checkedKey, databaseMisses, System.currentTimeMillis());
        if (!databaseMatches.isEmpty()) {
            cacheAfterCommit(key, databaseMatches, System.currentTimeMillis());
            exposed.addAll(databaseMatches);
        }
        failurePolicy.recordCacheResult("moment-exposure", false);
        exposed.retainAll(candidates);
        return exposed;
    }

    public void recordExposures(
            Long userId,
            String feedType,
            Collection<Long> momentIds,
            LocalDateTime exposedAt
    ) {
        List<Long> ids = normalizedIds(momentIds);
        if (ids.isEmpty()) {
            return;
        }
        LocalDateTime recordedAt = exposedAt == null ? LocalDateTime.now() : exposedAt;
        exposureMapper.insertIgnoreBatch(userId, feedType, ids, recordedAt);
        if (cacheEnabled()) {
            long score = recordedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            cacheRecordedAfterCommit(
                    exposureKey(userId, feedType),
                    checkedKey(userId, feedType),
                    ids,
                    score);
        }
    }

    private Set<Long> selectFromDatabase(Long userId, String feedType, Collection<Long> ids) {
        return new HashSet<>(exposureMapper.selectExposedMomentIds(userId, feedType, ids));
    }

    private void cacheAfterCommit(String key, Collection<Long> ids, long score) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
        ids.forEach(id -> tuples.add(new DefaultTypedTuple<>(String.valueOf(id), (double) score)));
        afterCommitExecutor.execute(() -> failurePolicy.execute(
                "moment-exposure",
                "write",
                key,
                () -> {
                    redisTemplate.opsForZSet().add(key, tuples);
                    redisTemplate.opsForZSet().removeRange(key, 0, -MAX_CACHED_EXPOSURES - 1L);
                    redisTemplate.expire(key, CACHE_TTL);
                }
        ));
    }

    private void cacheRecordedAfterCommit(
            String exposureKey,
            String checkedKey,
            Collection<Long> ids,
            long score
    ) {
        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
        ids.forEach(id -> tuples.add(new DefaultTypedTuple<>(String.valueOf(id), (double) score)));
        Object[] members = ids.stream().map(String::valueOf).toArray();
        afterCommitExecutor.execute(() -> failurePolicy.execute(
                "moment-exposure",
                "record",
                exposureKey,
                () -> {
                    redisTemplate.opsForZSet().remove(checkedKey, members);
                    redisTemplate.opsForZSet().add(exposureKey, tuples);
                    redisTemplate.opsForZSet()
                            .removeRange(exposureKey, 0, -MAX_CACHED_EXPOSURES - 1L);
                    redisTemplate.expire(exposureKey, CACHE_TTL);
                }
        ));
    }

    private Set<Long> parseIds(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new HashSet<>();
        for (String value : values) {
            try {
                result.add(Long.valueOf(value));
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy members and let the database resolve candidates.
            }
        }
        return result;
    }

    private List<Long> normalizedIds(Collection<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private boolean cacheEnabled() {
        return properties.isEnabled() && properties.isMomentCacheEnabled();
    }

    private String exposureKey(Long userId, String feedType) {
        return "oma:" + properties.getEnvironment()
                + ":moment:exposure:v1:" + userId + ":" + feedType;
    }

    private String checkedKey(Long userId, String feedType) {
        return "oma:" + properties.getEnvironment()
                + ":moment:exposure-checked:v1:" + userId + ":" + feedType;
    }

    private record CachedExposureState(Set<Long> exposed, Set<Long> checked) {
    }
}
