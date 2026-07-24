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

public class NoopMomentCache implements MomentCachePort {

    @Override
    public Optional<MomentBaseCacheDto> getMoment(Long momentId) {
        return Optional.empty();
    }

    @Override
    public void putMoment(Long momentId, MomentBaseCacheDto value) {
    }

    @Override
    public Map<Long, MomentBaseCacheDto> getMoments(Collection<Long> momentIds) { return Map.of(); }

    @Override
    public void evictMoment(Long momentId) {
    }

    @Override
    public void evictUserFeeds(Long userId) {
    }

    @Override
    public void evictViewerFeeds(Collection<Long> userIds) {
    }

    @Override public Optional<MomentIdPageCacheDto> getFeed(Long userId,String type,long page,long size){return Optional.empty();}
    @Override public void putFeed(Long userId,String type,MomentIdPageCacheDto value){}
    @Override public Optional<MomentIdPageCacheDto> getTopicMoments(Long viewerId,Long topicId,long page,long size){return Optional.empty();}
    @Override public void putTopicMoments(Long viewerId,Long topicId,MomentIdPageCacheDto value){}
    @Override public Optional<MomentIdPageCacheDto> getUserMoments(Long viewerId,Long userId,long page,long size){return Optional.empty();}
    @Override public void putUserMoments(Long viewerId,Long userId,MomentIdPageCacheDto value){}
    @Override public void evictTopicMoments(Collection<Long> topicIds){}
    @Override public void evictUserMoments(Long userId){}
    @Override public Optional<Long> getUnread(Long userId){return Optional.empty();}
    @Override public void putUnread(Long userId,long count){}
    @Override public void evictUnread(Collection<Long> userIds){}
    @Override public Optional<List<MomentDtos.TopicResponse>> getHotTopics(){return Optional.empty();}
    @Override public void putHotTopics(List<MomentDtos.TopicResponse> topics){}
    @Override public void evictHotTopics(){}
    @Override public Optional<List<Long>> getHotMomentIds(){return Optional.empty();}
    @Override public void putHotMomentIds(List<Long> ids){}
    @Override public void evictHotMomentIds(){}
    @Override public Optional<MomentIdPageCacheDto> getFirstComments(Long momentId,long size){return Optional.empty();}
    @Override public void putFirstComments(Long momentId,MomentIdPageCacheDto value){}
    @Override public Optional<Long> getHotCommentId(Long momentId){return Optional.empty();}
    @Override public void putHotCommentId(Long momentId,Long commentId){}
    @Override public Optional<MomentStatsCacheDto> getStats(Long momentId){return Optional.empty();}
    @Override public void putStats(Long momentId,MomentStatsCacheDto value){}
    @Override public void evictInteraction(Long momentId){}
    @Override public Map<Long,MomentViewerStateCacheDto> getViewerStates(Long viewerId,Collection<Long> momentIds){return Map.of();}
    @Override public void putViewerStates(Long viewerId,Map<Long,MomentViewerStateCacheDto> states){}
    @Override public void evictViewerStates(Long viewerId,Collection<Long> momentIds){}
}
