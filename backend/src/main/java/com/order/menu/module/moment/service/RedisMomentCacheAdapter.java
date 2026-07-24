package com.order.menu.module.moment.service;

import com.order.menu.common.redis.RedisJsonCodec;
import com.order.menu.common.redis.RedisKeyFactory;
import com.order.menu.common.redis.RedisFailurePolicy;
import com.order.menu.common.transaction.AfterCommitExecutor;
import com.order.menu.config.OrderMenuRedisProperties;
import com.order.menu.module.moment.dto.MomentBaseCacheDto;
import com.order.menu.module.moment.dto.MomentDtos;
import com.order.menu.module.moment.dto.MomentIdPageCacheDto;
import com.order.menu.module.moment.dto.MomentStatsCacheDto;
import com.order.menu.module.moment.dto.MomentViewerStateCacheDto;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class RedisMomentCacheAdapter implements MomentCachePort {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final RedisJsonCodec codec;
    private final OrderMenuRedisProperties properties;
    private final AfterCommitExecutor afterCommitExecutor;
    private final RedisFailurePolicy failurePolicy;

    public RedisMomentCacheAdapter(StringRedisTemplate redisTemplate,
                                   RedisKeyFactory keyFactory,
                                   RedisJsonCodec codec,
                                   OrderMenuRedisProperties properties,
                                   AfterCommitExecutor afterCommitExecutor,
                                   RedisFailurePolicy failurePolicy) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.codec = codec;
        this.properties = properties;
        this.afterCommitExecutor = afterCommitExecutor;
        this.failurePolicy = failurePolicy;
    }

    @Override
    public Optional<MomentBaseCacheDto> getMoment(Long momentId) {
        String key = keyFactory.momentBase(momentId);
        Optional<MomentBaseCacheDto> result = failurePolicy.execute("moment", "read", key, () -> {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(codec.read(value, MomentBaseCacheDto.class));
        }, Optional.empty());
        failurePolicy.recordCacheResult("moment", result.isPresent());
        return result;
    }

    @Override
    public void putMoment(Long momentId, MomentBaseCacheDto value) {
        String key = keyFactory.momentBase(momentId);
        failurePolicy.execute("moment", "write", key, () -> {
            long seconds = properties.getMomentBaseTtlSeconds()
                    + ThreadLocalRandom.current().nextLong(0, 31);
            redisTemplate.opsForValue().set(key, codec.write(value), Duration.ofSeconds(seconds));
        });
    }

    @Override
    public Map<Long, MomentBaseCacheDto> getMoments(Collection<Long> momentIds) {
        if (momentIds == null || momentIds.isEmpty()) return Map.of();
        List<Long> ids = momentIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        List<String> keys = ids.stream().map(keyFactory::momentBase).toList();
        Map<Long, MomentBaseCacheDto> result = failurePolicy.execute("moment", "multi-read", "moment:base", () -> {
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            Map<Long, MomentBaseCacheDto> found = new LinkedHashMap<>();
            if (values != null) for (int i = 0; i < Math.min(ids.size(), values.size()); i++) {
                String value = values.get(i);
                if (value != null) found.put(ids.get(i), codec.read(value, MomentBaseCacheDto.class));
            }
            return found;
        }, Map.of());
        failurePolicy.recordCacheResult("moment-batch", result.size() == ids.size());
        return result;
    }

    @Override
    public void evictMoment(Long momentId) {
        String key = keyFactory.momentBase(momentId);
        afterCommitExecutor.execute(() -> failurePolicy.execute(
                "moment", "evict", key, () -> { redisTemplate.delete(key); }));
    }

    @Override
    public void evictUserFeeds(Long userId) {
        String key = keyFactory.momentFeedVersion();
        afterCommitExecutor.execute(() -> failurePolicy.execute(
                "moment", "feed-version", key, () -> redisTemplate.opsForValue().increment(key)));
    }

    @Override public void evictViewerFeeds(Collection<Long> userIds){if(userIds==null)return;afterCommitExecutor.execute(()->userIds.stream().filter(java.util.Objects::nonNull).distinct().forEach(id->{String key=keyFactory.momentFeedViewerVersion(id);failurePolicy.execute("moment","viewer-feed-version",key,()->redisTemplate.opsForValue().increment(key));}));}
    @Override public Optional<MomentIdPageCacheDto> getFeed(Long userId,String type,long page,long size){long version=version(keyFactory.momentFeedVersion()),viewerVersion=version(keyFactory.momentFeedViewerVersion(userId));return get(keyFactory.momentFeed(userId,type,version,viewerVersion,page,size),MomentIdPageCacheDto.class,"feed-read");}
    @Override public void putFeed(Long userId,String type,MomentIdPageCacheDto value){long version=version(keyFactory.momentFeedVersion()),viewerVersion=version(keyFactory.momentFeedViewerVersion(userId));put(keyFactory.momentFeed(userId,type,version,viewerVersion,value.page(),value.pageSize()),value,properties.getMomentListTtlSeconds(),"feed-write");}
    @Override public Optional<MomentIdPageCacheDto> getTopicMoments(Long viewerId,Long topicId,long page,long size){long version=version(keyFactory.momentTopicVersion(topicId));return get(keyFactory.momentTopicIds(viewerId,topicId,version,page,size),MomentIdPageCacheDto.class,"topic-list-read");}
    @Override public void putTopicMoments(Long viewerId,Long topicId,MomentIdPageCacheDto value){long version=version(keyFactory.momentTopicVersion(topicId));put(keyFactory.momentTopicIds(viewerId,topicId,version,value.page(),value.pageSize()),value,properties.getMomentListTtlSeconds(),"topic-list-write");}
    @Override public Optional<MomentIdPageCacheDto> getUserMoments(Long viewerId,Long userId,long page,long size){long version=version(keyFactory.momentUserVersion(userId));return get(keyFactory.momentUserIds(viewerId,userId,version,page,size),MomentIdPageCacheDto.class,"user-list-read");}
    @Override public void putUserMoments(Long viewerId,Long userId,MomentIdPageCacheDto value){long version=version(keyFactory.momentUserVersion(userId));put(keyFactory.momentUserIds(viewerId,userId,version,value.page(),value.pageSize()),value,properties.getMomentListTtlSeconds(),"user-list-write");}
    @Override public void evictTopicMoments(Collection<Long> topicIds){if(topicIds==null)return;afterCommitExecutor.execute(()->topicIds.stream().filter(java.util.Objects::nonNull).distinct().forEach(id->{String key=keyFactory.momentTopicVersion(id);failurePolicy.execute("moment","topic-version",key,()->redisTemplate.opsForValue().increment(key));}));}
    @Override public void evictUserMoments(Long userId){String key=keyFactory.momentUserVersion(userId);afterCommitExecutor.execute(()->failurePolicy.execute("moment","user-version",key,()->redisTemplate.opsForValue().increment(key)));}
    @Override public Optional<Long> getUnread(Long userId){return get(keyFactory.momentUnread(userId),Long.class,"unread-read");}
    @Override public void putUnread(Long userId,long count){put(keyFactory.momentUnread(userId),count,properties.getMomentUnreadTtlSeconds(),"unread-write");}
    @Override public void evictUnread(Collection<Long> userIds){if(userIds==null||userIds.isEmpty())return;List<String> keys=userIds.stream().filter(java.util.Objects::nonNull).distinct().map(keyFactory::momentUnread).toList();afterCommitExecutor.execute(()->failurePolicy.execute("moment","unread-evict","moment:unread",()->redisTemplate.delete(keys)));}
    @Override public Optional<List<MomentDtos.TopicResponse>> getHotTopics(){String key=keyFactory.momentHotTopics();Optional<List<MomentDtos.TopicResponse>> result=failurePolicy.execute("moment","hot-topics-read",key,()->{String value=redisTemplate.opsForValue().get(key);return value==null?Optional.empty():Optional.of(codec.readList(value,MomentDtos.TopicResponse.class));},Optional.empty());failurePolicy.recordCacheResult("moment-hot-topics",result.isPresent());return result;}
    @Override public void putHotTopics(List<MomentDtos.TopicResponse> topics){put(keyFactory.momentHotTopics(),topics,properties.getMomentTopicTtlSeconds(),"hot-topics-write");}
    @Override public void evictHotTopics(){deleteAfterCommit(List.of(keyFactory.momentHotTopics()),"hot-topics-evict");}
    @Override public Optional<List<Long>> getHotMomentIds(){String key=keyFactory.momentHotIds();Optional<List<Long>> result=failurePolicy.execute("moment","hot-read",key,()->{String value=redisTemplate.opsForValue().get(key);return value==null?Optional.empty():Optional.of(codec.readList(value,Long.class));},Optional.empty());failurePolicy.recordCacheResult("moment-hot",result.isPresent());return result;}
    @Override public void putHotMomentIds(List<Long> ids){put(keyFactory.momentHotIds(),ids,properties.getMomentListTtlSeconds(),"hot-write");}
    @Override public void evictHotMomentIds(){deleteAfterCommit(List.of(keyFactory.momentHotIds()),"hot-evict");}
    @Override public Optional<MomentIdPageCacheDto> getFirstComments(Long momentId,long size){return get(keyFactory.momentCommentsFirst(momentId,size),MomentIdPageCacheDto.class,"comments-read");}
    @Override public void putFirstComments(Long momentId,MomentIdPageCacheDto value){put(keyFactory.momentCommentsFirst(momentId,value.pageSize()),value,properties.getMomentCommentTtlSeconds(),"comments-write");}
    @Override public Optional<Long> getHotCommentId(Long momentId){return get(keyFactory.momentHotComment(momentId),Long.class,"hot-comment-read");}
    @Override public void putHotCommentId(Long momentId,Long commentId){put(keyFactory.momentHotComment(momentId),commentId==null?0L:commentId,properties.getMomentCommentTtlSeconds(),"hot-comment-write");}
    @Override public Optional<MomentStatsCacheDto> getStats(Long momentId){return get(keyFactory.momentStats(momentId),MomentStatsCacheDto.class,"stats-read");}
    @Override public void putStats(Long momentId,MomentStatsCacheDto value){put(keyFactory.momentStats(momentId),value,properties.getMomentStatsTtlSeconds(),"stats-write");}
    @Override public void evictInteraction(Long momentId){evictMoment(momentId);evictHotMomentIds();java.util.ArrayList<String> keys=new java.util.ArrayList<>();keys.add(keyFactory.momentStats(momentId));keys.add(keyFactory.momentHotComment(momentId));for(int size=1;size<=10;size++)keys.add(keyFactory.momentCommentsFirst(momentId,size));deleteAfterCommit(keys,"interaction-evict");}

    @Override
    public Map<Long, MomentViewerStateCacheDto> getViewerStates(Long viewerId, Collection<Long> momentIds) {
        if (viewerId == null || momentIds == null || momentIds.isEmpty()) return Map.of();
        List<Long> ids = momentIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        List<String> keys = ids.stream().map(id -> keyFactory.momentViewerState(viewerId, id)).toList();
        Map<Long, MomentViewerStateCacheDto> result = failurePolicy.execute(
                "moment-viewer-state", "multi-read", "moment:viewer-state", () -> {
                    List<String> values = redisTemplate.opsForValue().multiGet(keys);
                    Map<Long, MomentViewerStateCacheDto> found = new LinkedHashMap<>();
                    if (values != null) for (int i = 0; i < Math.min(ids.size(), values.size()); i++) {
                        String value = values.get(i);
                        if (value != null) {
                            found.put(ids.get(i), codec.read(value, MomentViewerStateCacheDto.class));
                        }
                    }
                    return found;
                }, Map.of());
        failurePolicy.recordCacheResult("moment-viewer-state-batch", result.size() == ids.size());
        return result;
    }

    @Override
    public void putViewerStates(Long viewerId, Map<Long, MomentViewerStateCacheDto> states) {
        if (viewerId == null || states == null || states.isEmpty()) return;
        failurePolicy.execute("moment-viewer-state", "batch-write", "moment:viewer-state", () -> {
            RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (Map.Entry<Long, MomentViewerStateCacheDto> entry : states.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) continue;
                    long seconds = properties.getMomentViewerStateTtlSeconds()
                            + ThreadLocalRandom.current().nextLong(0, 31);
                    connection.stringCommands().set(
                            serializer.serialize(keyFactory.momentViewerState(viewerId, entry.getKey())),
                            serializer.serialize(codec.write(entry.getValue())),
                            Expiration.seconds(seconds),
                            SetOption.UPSERT);
                }
                return null;
            });
        });
    }

    @Override
    public void evictViewerStates(Long viewerId, Collection<Long> momentIds) {
        if (viewerId == null || momentIds == null || momentIds.isEmpty()) return;
        List<String> keys = momentIds.stream().filter(java.util.Objects::nonNull).distinct()
                .map(id -> keyFactory.momentViewerState(viewerId, id)).toList();
        if (!keys.isEmpty()) deleteAfterCommit(keys, "viewer-state-evict");
    }

    private long version(String key){String value=failurePolicy.execute("moment","version-read",key,()->redisTemplate.opsForValue().get(key),null);if(value==null)return 0;try{return Long.parseLong(value);}catch(NumberFormatException ignored){return 0;}}
    private <T> Optional<T> get(String key,Class<T> type,String operation){Optional<T> result=failurePolicy.execute("moment",operation,key,()->{String value=redisTemplate.opsForValue().get(key);return value==null?Optional.empty():Optional.of(codec.read(value,type));},Optional.empty());failurePolicy.recordCacheResult("moment",result.isPresent());return result;}
    private void put(String key,Object value,long ttlSeconds,String operation){failurePolicy.execute("moment",operation,key,()->redisTemplate.opsForValue().set(key,codec.write(value),Duration.ofSeconds(ttlSeconds+ThreadLocalRandom.current().nextLong(0,16))));}
    private void deleteAfterCommit(Collection<String> keys,String operation){afterCommitExecutor.execute(()->failurePolicy.execute("moment",operation,"moment",()->redisTemplate.delete(keys)));}
}
