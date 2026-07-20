package com.order.menu.module.moment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

public final class MomentDtos {
  private MomentDtos() {}
  public record UpsertRequest(
      @Size(max=120) String title,
      @Size(max=20000) String content,
      @Pattern(regexp="PUBLIC|RELATED|PRIVATE") String visibility,
      @Pattern(regexp="PUBLISHED|DRAFT") String status,
      Long shopId, Long dishId, @Size(max=9) List<@Size(max=500) String> imageUrls,
      @Size(max=5) List<@NotBlank @Size(max=50) String> topics) {}
  public record TopicResponse(Long id,String name,Long usageCount,Long userCount,Long momentCount) {}
  public record TopicCreateRequest(@NotBlank @Size(max=50) String name) {}
  public record AuthorResponse(Long id,String nickname,String avatarUrl,String role,Boolean merchant) {}
  public record ReferenceResponse(Long shopId,String shopName,String shopImageUrl,Long dishId,String dishName,
      String dishImageUrl,BigDecimal dishPrice,String dishDescription,Integer dishSalesCount) {}
  public record ReactionResponse(String emoji,Long count,Boolean reactedByMe) {}
  public record MomentResponse(Long id,AuthorResponse author,String title,String content,String visibility,String status,
      List<String> imageUrls,List<TopicResponse> topics,ReferenceResponse reference,Long likeCount,Boolean likedByMe,
      Long commentCount,List<ReactionResponse> reactions,
      @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'+08:00'") LocalDateTime editedAt,
      @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'+08:00'") LocalDateTime publishedAt,
      @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'+08:00'") LocalDateTime createdAt,
      Boolean editable,Boolean readByMe,CommentResponse hotComment) {}
  public record PageResponse<T>(List<T> records,long page,long pageSize,long total,boolean hasMore) {}
  public record ToggleRequest(Boolean active) {}
  public record ReactionRequest(@NotBlank @Size(max=32) String emoji,Boolean active) {}
  public record CommentRequest(@Size(max=2000) String content,Long parentCommentId,Long replyToCommentId,
      @Size(max=6) List<@Size(max=500) String> imageUrls) {}
  public record CommentResponse(Long id,Long momentId,AuthorResponse author,String content,List<String> imageUrls,
      Long parentCommentId,Long replyToCommentId,String replyToNickname,Boolean authorBadge,Boolean mine,Long likeCount,
      Boolean likedByMe,Long replyCount,List<CommentResponse> replies,
      @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'+08:00'") LocalDateTime createdAt) {}
  public record PreferenceRequest(Boolean hideTargetMoments,Boolean hideMyMoments) {}
  public record PreferenceResponse(Long targetUserId,Boolean hideTargetMoments,Boolean hideMyMoments) {}
  public record ProfileVisibilityRequest(@NotBlank @Pattern(regexp="PUBLIC|CHAT_FRIENDS|FAMILY|PRIVATE") String visibility) {}
  public record ProfileSummary(Long userId,String visibility,Boolean canView,Boolean hiddenByMe,String reason,
      Long momentCount,List<MomentResponse> preview) {}
  public record UploadResponse(String url) {}
  public record ShareRequest(@NotNull Long conversationId,Long commentId) {}
  public record MarkReadRequest(@NotEmpty @Size(max=100) List<Long> momentIds) {}
  public record UnreadResponse(Long relatedUnreadCount) {}
  public record AvailabilityResponse(Boolean available,String status,String reason) {}
}
