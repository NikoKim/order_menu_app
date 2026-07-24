package com.order.menu.module.moment.service.cache;

import com.order.menu.module.moment.dto.MomentDtos;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Short-lived caches for public lookup data and viewer-specific comment state.
 *
 * <p>Implementations must fail open: an unavailable cache is represented by a
 * miss so callers can continue with the database.</p>
 */
public interface MomentAuxiliaryCachePort {

    Map<Long, Boolean> getCommentLikes(Long viewerId, Collection<Long> commentIds);

    void putCommentLikes(Long viewerId, Map<Long, Boolean> states);

    void evictCommentLike(Long viewerId, Long commentId);

    Optional<MomentDtos.AuthorResponse> getAuthor(Long userId);

    Map<Long, MomentDtos.AuthorResponse> getAuthors(Collection<Long> userIds);

    void putAuthor(Long userId, MomentDtos.AuthorResponse author);

    void putAuthors(Map<Long, MomentDtos.AuthorResponse> authors);

    void evictAuthor(Long userId);

    Optional<List<MomentDtos.AuthorResponse>> getMentionSearch(String query);

    void putMentionSearch(String query, List<MomentDtos.AuthorResponse> authors);

    void evictMentionSearches();

    Optional<MomentDtos.TopicResponse> getTopic(Long topicId);

    void putTopic(Long topicId, MomentDtos.TopicResponse topic);

    void evictTopic(Long topicId);

    Optional<TopicSearchCacheDto> getTopicSearch(String query, long page, long pageSize);

    void putTopicSearch(String query, TopicSearchCacheDto result);

    void evictTopicSearches();

    boolean isMomentMissing(Long momentId);

    void markMomentMissing(Long momentId);

    void evictMomentMissing(Long momentId);

    boolean isCommentMissing(Long commentId);

    void markCommentMissing(Long commentId);

    void evictCommentMissing(Long commentId);
}
