package com.order.menu.module.moment.service;

import com.order.menu.module.moment.dto.MomentBaseCacheDto;
import com.order.menu.module.moment.dto.MomentDtos;
import com.order.menu.module.moment.dto.MomentIdPageCacheDto;
import com.order.menu.module.moment.dto.MomentStatsCacheDto;
import com.order.menu.module.moment.dto.MomentViewerStateCacheDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MomentCachePort {

    Optional<MomentBaseCacheDto> getMoment(Long momentId);

    void putMoment(Long momentId, MomentBaseCacheDto value);

    Map<Long, MomentBaseCacheDto> getMoments(Collection<Long> momentIds);

    void evictMoment(Long momentId);

    void evictUserFeeds(Long userId);

    void evictViewerFeeds(Collection<Long> userIds);

    Optional<MomentIdPageCacheDto> getFeed(Long userId, String type, long page, long size);

    void putFeed(Long userId, String type, MomentIdPageCacheDto value);

    Optional<MomentIdPageCacheDto> getTopicMoments(Long viewerId, Long topicId, long page, long size);

    void putTopicMoments(Long viewerId, Long topicId, MomentIdPageCacheDto value);

    Optional<MomentIdPageCacheDto> getUserMoments(Long viewerId, Long userId, long page, long size);

    void putUserMoments(Long viewerId, Long userId, MomentIdPageCacheDto value);

    void evictTopicMoments(Collection<Long> topicIds);

    void evictUserMoments(Long userId);

    Optional<Long> getUnread(Long userId);

    void putUnread(Long userId, long count);

    void evictUnread(Collection<Long> userIds);

    Optional<List<MomentDtos.TopicResponse>> getHotTopics();

    void putHotTopics(List<MomentDtos.TopicResponse> topics);

    void evictHotTopics();

    Optional<List<Long>> getHotMomentIds();

    void putHotMomentIds(List<Long> ids);

    void evictHotMomentIds();

    Optional<MomentIdPageCacheDto> getFirstComments(Long momentId, long size);

    void putFirstComments(Long momentId, MomentIdPageCacheDto value);

    Optional<Long> getHotCommentId(Long momentId);

    void putHotCommentId(Long momentId, Long commentId);

    Optional<MomentStatsCacheDto> getStats(Long momentId);

    void putStats(Long momentId, MomentStatsCacheDto value);

    void evictInteraction(Long momentId);

    Map<Long, MomentViewerStateCacheDto> getViewerStates(Long viewerId, Collection<Long> momentIds);

    void putViewerStates(Long viewerId, Map<Long, MomentViewerStateCacheDto> states);

    void evictViewerStates(Long viewerId, Collection<Long> momentIds);
}
