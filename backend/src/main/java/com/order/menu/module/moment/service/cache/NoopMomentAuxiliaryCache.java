package com.order.menu.module.moment.service.cache;

import com.order.menu.module.moment.dto.MomentDtos;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NoopMomentAuxiliaryCache implements MomentAuxiliaryCachePort {

    @Override public Map<Long, Boolean> getCommentLikes(Long viewerId, Collection<Long> commentIds) { return Map.of(); }
    @Override public void putCommentLikes(Long viewerId, Map<Long, Boolean> states) { }
    @Override public void evictCommentLike(Long viewerId, Long commentId) { }
    @Override public Optional<MomentDtos.AuthorResponse> getAuthor(Long userId) { return Optional.empty(); }
    @Override public Map<Long, MomentDtos.AuthorResponse> getAuthors(Collection<Long> userIds) { return Map.of(); }
    @Override public void putAuthor(Long userId, MomentDtos.AuthorResponse author) { }
    @Override public void putAuthors(Map<Long, MomentDtos.AuthorResponse> authors) { }
    @Override public void evictAuthor(Long userId) { }
    @Override public Optional<List<MomentDtos.AuthorResponse>> getMentionSearch(String query) { return Optional.empty(); }
    @Override public void putMentionSearch(String query, List<MomentDtos.AuthorResponse> authors) { }
    @Override public void evictMentionSearches() { }
    @Override public Optional<MomentDtos.TopicResponse> getTopic(Long topicId) { return Optional.empty(); }
    @Override public void putTopic(Long topicId, MomentDtos.TopicResponse topic) { }
    @Override public void evictTopic(Long topicId) { }
    @Override public Optional<TopicSearchCacheDto> getTopicSearch(String query, long page, long pageSize) { return Optional.empty(); }
    @Override public void putTopicSearch(String query, TopicSearchCacheDto result) { }
    @Override public void evictTopicSearches() { }
    @Override public boolean isMomentMissing(Long momentId) { return false; }
    @Override public void markMomentMissing(Long momentId) { }
    @Override public void evictMomentMissing(Long momentId) { }
    @Override public boolean isCommentMissing(Long commentId) { return false; }
    @Override public void markCommentMissing(Long commentId) { }
    @Override public void evictCommentMissing(Long commentId) { }
}
