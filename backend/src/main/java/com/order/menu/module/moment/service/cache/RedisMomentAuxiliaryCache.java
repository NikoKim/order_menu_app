package com.order.menu.module.moment.service.cache;

import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.transaction.AfterCommitExecutor;
import com.order.menu.config.OrderMenuRedisProperties;
import com.order.menu.module.moment.dto.MomentDtos;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class RedisMomentAuxiliaryCache implements MomentAuxiliaryCachePort {

    static final long COMMENT_LIKE_TTL_SECONDS = 45;
    static final long AUTHOR_TTL_SECONDS = 60;
    static final long MENTION_SEARCH_TTL_SECONDS = 30;
    static final long TOPIC_TTL_SECONDS = 120;
    static final long TOPIC_SEARCH_TTL_SECONDS = 30;
    static final long NEGATIVE_TTL_SECONDS = 15;

    private final StringRedisTemplate redis;
    private final RedisJsonCodec codec;
    private final RedisFailurePolicy failurePolicy;
    private final AfterCommitExecutor afterCommitExecutor;
    private final MomentAuxiliaryCacheKeys keys;

    public RedisMomentAuxiliaryCache(
            StringRedisTemplate redis,
            RedisJsonCodec codec,
            RedisFailurePolicy failurePolicy,
            AfterCommitExecutor afterCommitExecutor,
            OrderMenuRedisProperties properties
    ) {
        this.redis = redis;
        this.codec = codec;
        this.failurePolicy = failurePolicy;
        this.afterCommitExecutor = afterCommitExecutor;
        this.keys = new MomentAuxiliaryCacheKeys(properties);
    }

    @Override
    public Map<Long, Boolean> getCommentLikes(Long viewerId, Collection<Long> commentIds) {
        if (viewerId == null || commentIds == null || commentIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = distinctIds(commentIds);
        List<String> redisKeys = ids.stream().map(id -> keys.commentLike(viewerId, id)).toList();
        Map<Long, Boolean> result = failurePolicy.execute(
                "moment-aux", "comment-like-multi-read", "comment-like",
                () -> {
                    List<String> values = redis.opsForValue().multiGet(redisKeys);
                    Map<Long, Boolean> found = new LinkedHashMap<>();
                    if (values != null) {
                        for (int index = 0; index < Math.min(ids.size(), values.size()); index++) {
                            String value = values.get(index);
                            if ("1".equals(value) || "0".equals(value)) {
                                found.put(ids.get(index), "1".equals(value));
                            }
                        }
                    }
                    return found;
                },
                Map.of()
        );
        failurePolicy.recordCacheResult("moment-comment-like", result.size() == ids.size());
        return result;
    }

    @Override
    public void putCommentLikes(Long viewerId, Map<Long, Boolean> states) {
        if (viewerId == null || states == null || states.isEmpty()) {
            return;
        }
        states.forEach((commentId, liked) -> {
            if (commentId != null && liked != null) {
                putRaw(keys.commentLike(viewerId, commentId), liked ? "1" : "0",
                        COMMENT_LIKE_TTL_SECONDS, "comment-like-write");
            }
        });
    }

    @Override
    public void evictCommentLike(Long viewerId, Long commentId) {
        deleteAfterCommit(keys.commentLike(viewerId, commentId), "comment-like-evict");
    }

    @Override
    public Optional<MomentDtos.AuthorResponse> getAuthor(Long userId) {
        return get(keys.author(userId), MomentDtos.AuthorResponse.class, "author-read", "moment-author");
    }

    @Override
    public Map<Long, MomentDtos.AuthorResponse> getAuthors(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = distinctIds(userIds);
        List<String> redisKeys = ids.stream().map(keys::author).toList();
        Map<Long, MomentDtos.AuthorResponse> result = failurePolicy.execute(
                "moment-aux", "author-multi-read", "author",
                () -> {
                    List<String> values = redis.opsForValue().multiGet(redisKeys);
                    Map<Long, MomentDtos.AuthorResponse> found = new LinkedHashMap<>();
                    if (values != null) {
                        for (int index = 0; index < Math.min(ids.size(), values.size()); index++) {
                            String value = values.get(index);
                            if (value != null) {
                                found.put(ids.get(index), codec.read(value, MomentDtos.AuthorResponse.class));
                            }
                        }
                    }
                    return found;
                },
                Map.of()
        );
        failurePolicy.recordCacheResult("moment-author-batch", result.size() == ids.size());
        return result;
    }

    @Override
    public void putAuthor(Long userId, MomentDtos.AuthorResponse author) {
        if (userId != null && author != null && Objects.equals(userId, author.id())) {
            putJson(keys.author(userId), author, AUTHOR_TTL_SECONDS, "author-write");
        }
    }

    @Override
    public void putAuthors(Map<Long, MomentDtos.AuthorResponse> authors) {
        if (authors != null) {
            authors.forEach(this::putAuthor);
        }
    }

    @Override
    public void evictAuthor(Long userId) {
        deleteAfterCommit(keys.author(userId), "author-evict");
    }

    @Override
    public Optional<List<MomentDtos.AuthorResponse>> getMentionSearch(String query) {
        long version = version(keys.mentionSearchVersion());
        String key = keys.mentionSearch(query, version);
        Optional<List<MomentDtos.AuthorResponse>> result = failurePolicy.execute(
                "moment-aux", "mention-search-read", key,
                () -> {
                    String value = redis.opsForValue().get(key);
                    return value == null
                            ? Optional.empty()
                            : Optional.of(codec.readList(value, MomentDtos.AuthorResponse.class));
                },
                Optional.empty()
        );
        failurePolicy.recordCacheResult("moment-mention-search", result.isPresent());
        return result;
    }

    @Override
    public void putMentionSearch(String query, List<MomentDtos.AuthorResponse> authors) {
        long version = version(keys.mentionSearchVersion());
        putJson(keys.mentionSearch(query, version), authors == null ? List.of() : authors,
                MENTION_SEARCH_TTL_SECONDS, "mention-search-write");
    }

    @Override
    public void evictMentionSearches() {
        incrementAfterCommit(keys.mentionSearchVersion(), "mention-search-version");
    }

    @Override
    public Optional<MomentDtos.TopicResponse> getTopic(Long topicId) {
        return get(keys.topic(topicId), MomentDtos.TopicResponse.class, "topic-read", "moment-topic-detail");
    }

    @Override
    public void putTopic(Long topicId, MomentDtos.TopicResponse topic) {
        if (topicId != null && topic != null && Objects.equals(topicId, topic.id())) {
            putJson(keys.topic(topicId), topic, TOPIC_TTL_SECONDS, "topic-write");
        }
    }

    @Override
    public void evictTopic(Long topicId) {
        deleteAfterCommit(keys.topic(topicId), "topic-evict");
    }

    @Override
    public Optional<TopicSearchCacheDto> getTopicSearch(String query, long page, long pageSize) {
        long version = version(keys.topicSearchVersion());
        return get(keys.topicSearch(query, version, page, pageSize), TopicSearchCacheDto.class,
                "topic-search-read", "moment-topic-search");
    }

    @Override
    public void putTopicSearch(String query, TopicSearchCacheDto result) {
        if (result == null) {
            return;
        }
        long version = version(keys.topicSearchVersion());
        putJson(keys.topicSearch(query, version, result.page(), result.pageSize()), result,
                TOPIC_SEARCH_TTL_SECONDS, "topic-search-write");
    }

    @Override
    public void evictTopicSearches() {
        incrementAfterCommit(keys.topicSearchVersion(), "topic-search-version");
    }

    @Override
    public boolean isMomentMissing(Long momentId) {
        return exists(keys.missingMoment(momentId), "missing-moment-read");
    }

    @Override
    public void markMomentMissing(Long momentId) {
        putRawAfterCommit(keys.missingMoment(momentId), "1",
                NEGATIVE_TTL_SECONDS, "missing-moment-write");
    }

    @Override
    public void evictMomentMissing(Long momentId) {
        deleteAfterCommit(keys.missingMoment(momentId), "missing-moment-evict");
    }

    @Override
    public boolean isCommentMissing(Long commentId) {
        return exists(keys.missingComment(commentId), "missing-comment-read");
    }

    @Override
    public void markCommentMissing(Long commentId) {
        putRawAfterCommit(keys.missingComment(commentId), "1",
                NEGATIVE_TTL_SECONDS, "missing-comment-write");
    }

    @Override
    public void evictCommentMissing(Long commentId) {
        deleteAfterCommit(keys.missingComment(commentId), "missing-comment-evict");
    }

    private <T> Optional<T> get(String key, Class<T> type, String operation, String metric) {
        Optional<T> result = failurePolicy.execute(
                "moment-aux", operation, key,
                () -> {
                    String value = redis.opsForValue().get(key);
                    return value == null ? Optional.empty() : Optional.of(codec.read(value, type));
                },
                Optional.empty()
        );
        failurePolicy.recordCacheResult(metric, result.isPresent());
        return result;
    }

    private boolean exists(String key, String operation) {
        Boolean result = failurePolicy.execute(
                "moment-aux", operation, key,
                () -> redis.hasKey(key),
                Boolean.FALSE
        );
        return Boolean.TRUE.equals(result);
    }

    private long version(String key) {
        String value = failurePolicy.execute(
                "moment-aux", "version-read", key,
                () -> redis.opsForValue().get(key),
                null
        );
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void putJson(String key, Object value, long ttlSeconds, String operation) {
        putRaw(key, codec.write(value), ttlSeconds, operation);
    }

    private void putRaw(String key, String value, long ttlSeconds, String operation) {
        failurePolicy.execute("moment-aux", operation, key, () ->
                redis.opsForValue().set(key, value, jittered(ttlSeconds)));
    }

    private void putRawAfterCommit(String key, String value, long ttlSeconds, String operation) {
        afterCommitExecutor.execute(() -> putRaw(key, value, ttlSeconds, operation));
    }

    private void deleteAfterCommit(String key, String operation) {
        afterCommitExecutor.execute(() ->
                failurePolicy.execute("moment-aux", operation, key, () -> redis.delete(key)));
    }

    private void incrementAfterCommit(String key, String operation) {
        afterCommitExecutor.execute(() ->
                failurePolicy.execute("moment-aux", operation, key,
                        () -> redis.opsForValue().increment(key)));
    }

    private Duration jittered(long ttlSeconds) {
        return Duration.ofSeconds(ttlSeconds + ThreadLocalRandom.current().nextLong(0, 11));
    }

    private List<Long> distinctIds(Collection<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
