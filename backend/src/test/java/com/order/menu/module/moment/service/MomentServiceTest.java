package com.order.menu.module.moment.service;

import static org.junit.jupiter.api.Assertions.*; import static org.mockito.ArgumentMatchers.*; import static org.mockito.Mockito.*;
import com.order.menu.common.exception.BusinessException; import com.order.menu.module.dish.mapper.DishMapper; import com.order.menu.module.family.mapper.FamilyMemberMapper; import com.order.menu.module.message.entity.*; import com.order.menu.module.message.mapper.*; import com.order.menu.module.message.service.MessageService; import com.order.menu.module.moment.dto.MomentDtos; import com.order.menu.module.moment.entity.*; import com.order.menu.module.moment.mapper.*; import com.order.menu.module.shop.mapper.ShopMapper; import com.order.menu.module.user.entity.User; import com.order.menu.module.user.mapper.UserMapper;
import java.util.*; import java.util.concurrent.atomic.AtomicReference; import org.junit.jupiter.api.Test; import org.junit.jupiter.api.extension.ExtendWith; import org.mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class MomentServiceTest {
  @Mock MomentMapper moments; @Mock MomentTopicMapper topics; @Mock MomentTopicRelationMapper topicRelations;
  @Mock MomentLikeMapper likes; @Mock MomentReactionMapper reactions; @Mock MomentCommentMapper comments;
  @Mock MomentReadMapper reads; @Mock MomentCommentLikeMapper commentLikes; @Mock MomentUserPreferenceMapper preferences;
  @Mock MomentFeedExposureMapper exposures; @Mock com.order.menu.websocket.WsPushService wsPushService;
  @Mock UserMapper users; @Mock ShopMapper shops; @Mock DishMapper dishes; @Mock ConversationMemberMapper members;
  @Mock FamilyMemberMapper families; @Mock ChatMessageMapper chatMessages; @Mock MessageService messages; @Mock MomentCachePort cache;
  @InjectMocks MomentService service;

  @Test void incompleteDraftCanBeSaved() {
    AtomicReference<Moment> saved=new AtomicReference<>();
    doAnswer(inv->{Moment m=inv.getArgument(0);m.setId(9L);saved.set(m);return 1;}).when(moments).insert(any(Moment.class));
    when(moments.selectById(9L)).thenAnswer(inv->saved.get());
    User user=new User();user.setId(1L);user.setNickname("u");user.setRole("CUSTOMER");when(users.selectById(1L)).thenReturn(user);
    MomentDtos.MomentResponse result=service.create(1L,new MomentDtos.UpsertRequest("","","PRIVATE","DRAFT",null,null,List.of(),List.of()));
    assertEquals("DRAFT",result.status()); assertEquals("",saved.get().getTitle());
  }

  @Test void publishedMomentRequiresCompleteTitleAndContent() {
    assertThrows(BusinessException.class,()->service.create(1L,new MomentDtos.UpsertRequest("abc","body","PUBLIC","PUBLISHED",null,null,List.of(),List.of())));
    assertThrows(BusinessException.class,()->service.create(1L,new MomentDtos.UpsertRequest("valid"," ","PUBLIC","PUBLISHED",null,null,List.of(),List.of())));
  }

  @Test void unreadRelatedFeedUsesOnlyEstablishedTwoWayChats() {
    when(chatMessages.selectEstablishedDirectChatUserIds(1L)).thenReturn(List.of(3L));
    when(moments.selectCount(any())).thenReturn(2L);
    assertEquals(2L,service.unread(1L).relatedUnreadCount());
    verify(chatMessages).selectEstablishedDirectChatUserIds(1L);
  }

  @Test void partialPreferenceUpdatePreservesOtherSwitch() {
    com.order.menu.module.moment.entity.MomentUserPreference existing=new com.order.menu.module.moment.entity.MomentUserPreference();existing.setId(5L);existing.setUserId(1L);existing.setTargetUserId(2L);existing.setHideTargetMoments(true);existing.setHideMyMoments(false);
    when(preferences.selectOne(any())).thenReturn(existing);User target=new User();target.setId(2L);when(users.selectById(2L)).thenReturn(target);
    MomentDtos.PreferenceResponse result=service.updatePreference(1L,2L,new MomentDtos.PreferenceRequest(null,true));
    assertTrue(result.hideTargetMoments());assertTrue(result.hideMyMoments());verify(preferences).updateById(existing);
  }

  @Test void replyTargetMustBelongToSameRootThread() {
    Moment moment=new Moment();moment.setId(7L);moment.setAuthorUserId(2L);moment.setStatus("PUBLISHED");moment.setVisibility("PUBLIC");when(moments.selectById(7L)).thenReturn(moment);
    com.order.menu.module.moment.entity.MomentComment first=new com.order.menu.module.moment.entity.MomentComment();first.setId(10L);first.setMomentId(7L);first.setAuthorUserId(2L);
    com.order.menu.module.moment.entity.MomentComment second=new com.order.menu.module.moment.entity.MomentComment();second.setId(20L);second.setMomentId(7L);second.setAuthorUserId(3L);
    when(comments.selectById(10L)).thenReturn(first);when(comments.selectById(20L)).thenReturn(second);
    assertThrows(BusinessException.class,()->service.addComment(1L,7L,new MomentDtos.CommentRequest("reply",10L,20L,List.of())));
    verify(comments,never()).insert(any(com.order.menu.module.moment.entity.MomentComment.class));
  }

  @Test void privateMomentCannotBeSharedToAnotherMember() {
    Moment moment=new Moment();moment.setId(8L);moment.setAuthorUserId(1L);moment.setStatus("PUBLISHED");moment.setVisibility("PRIVATE");when(moments.selectById(8L)).thenReturn(moment);
    ConversationMember recipient=new ConversationMember();recipient.setConversationId(4L);recipient.setUserId(2L);recipient.setMemberStatus("ACTIVE");when(members.selectList(any())).thenReturn(List.of(recipient));
    assertThrows(BusinessException.class,()->service.share(1L,8L,new MomentDtos.ShareRequest(4L,null)));
    verify(messages,never()).shareMoment(anyLong(),anyLong(),any(),any(),any(),any());
  }

  @Test void topicUsageCountsDistinctPublishedAuthors() {
    AtomicReference<Moment> saved=new AtomicReference<>();doAnswer(inv->{Moment m=inv.getArgument(0);m.setId(30L);saved.set(m);return 1;}).when(moments).insert(any(Moment.class));when(moments.selectById(30L)).thenAnswer(inv->saved.get());
    User author=new User();author.setId(1L);author.setNickname("author");author.setRole("CUSTOMER");when(users.selectById(1L)).thenReturn(author);
    com.order.menu.module.moment.entity.MomentTopic topic=new com.order.menu.module.moment.entity.MomentTopic();topic.setId(6L);topic.setName("food");topic.setUsageCount(0L);when(topics.selectOne(any())).thenReturn(topic);when(topics.selectById(6L)).thenReturn(topic);
    com.order.menu.module.moment.entity.MomentTopicRelation relation=new com.order.menu.module.moment.entity.MomentTopicRelation();relation.setMomentId(30L);relation.setTopicId(6L);
    when(topicRelations.selectList(any())).thenReturn(List.of(),List.of(relation),List.of());
    Moment a=new Moment();a.setAuthorUserId(1L);a.setStatus("PUBLISHED");Moment sameAuthor=new Moment();sameAuthor.setAuthorUserId(1L);sameAuthor.setStatus("PUBLISHED");Moment other=new Moment();other.setAuthorUserId(2L);other.setStatus("PUBLISHED");Moment draft=new Moment();draft.setAuthorUserId(3L);draft.setStatus("DRAFT");when(moments.selectByIds(any())).thenReturn(List.of(a,sameAuthor,other,draft));
    service.create(1L,new MomentDtos.UpsertRequest("valid","content","PUBLIC","PUBLISHED",null,null,List.of(),List.of("food")));
    assertEquals(2L,topic.getUsageCount());verify(topics).updateById(topic);
  }

  @Test void silentFeedPollingDoesNotConsumeFirstExposure() {
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Moment> raw=new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,10);raw.setRecords(List.of());raw.setTotal(0);
    when(moments.selectPage(any(),any())).thenReturn(raw);
    service.feed(1L,"RECOMMENDED",1,10,false);
    verifyNoInteractions(exposures);
  }

  @Test void searchIncludesMomentsWhoseTopicNameMatches() {
    MomentTopic topic=new MomentTopic();topic.setId(6L);topic.setName("美食");topic.setNormalizedName("美食");
    MomentTopicRelation relation=new MomentTopicRelation();relation.setTopicId(6L);relation.setMomentId(42L);
    when(topics.selectList(any())).thenReturn(List.of(topic));when(topicRelations.selectList(any())).thenReturn(List.of(relation));
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Moment> raw=new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,10);raw.setRecords(List.of());raw.setTotal(0);
    when(moments.selectPage(any(),any())).thenReturn(raw);

    service.search(1L,"美食",1,10);

    verify(topics).selectList(any());verify(topicRelations).selectList(any());verify(moments).selectPage(any(),any());
  }

  @Test void exactTopicRanksFirstForHashtagNavigation() {
    MomentTopic partial=new MomentTopic();partial.setId(1L);partial.setName("美食探店");partial.setNormalizedName("美食探店");
    MomentTopic exact=new MomentTopic();exact.setId(2L);exact.setName("美食");exact.setNormalizedName("美食");
    when(topics.selectList(any())).thenReturn(List.of(partial,exact));

    var result=service.topicsPage("#美食",1,10);

    assertEquals(2L,result.records().get(0).id());
  }

  @Test void firstPagePinsOnlyDisplayedOwnPostsWithoutChangingPageMembership() {
    Moment peerOne=published(1L,2L);Moment ownOne=published(2L,1L);Moment peerTwo=published(3L,3L);Moment ownTwo=published(4L,1L);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Moment> raw=new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,4);raw.setRecords(List.of(peerOne,ownOne,peerTwo,ownTwo));raw.setTotal(4);
    when(moments.selectPage(any(),any())).thenReturn(raw);when(exposures.selectList(any())).thenReturn(List.of());
    when(users.selectById(anyLong())).thenAnswer(inv->{Long id=inv.getArgument(0);User u=new User();u.setId(id);u.setNickname("u"+id);u.setRole("CUSTOMER");return u;});

    MomentDtos.PageResponse<MomentDtos.MomentResponse> result=service.feed(1L,"RECOMMENDED",1,4,true);

    assertEquals(List.of(2L,4L,1L,3L),result.records().stream().map(MomentDtos.MomentResponse::id).toList());
    assertEquals(Set.of(1L,2L,3L,4L),new HashSet<>(result.records().stream().map(MomentDtos.MomentResponse::id).toList()));
    verify(exposures,times(2)).insert(any(com.order.menu.module.moment.entity.MomentFeedExposure.class));
  }

  @Test void cancellingLikesAndReactionsDoesNotTouchNotifications() {
    Moment moment=published(7L,2L);when(moments.selectById(7L)).thenReturn(moment);when(moments.selectByIdForUpdate(7L)).thenReturn(moment);
    User author=new User();author.setId(2L);author.setNickname("author");author.setRole("CUSTOMER");when(users.selectById(2L)).thenReturn(author);
    com.order.menu.module.moment.entity.MomentLike like=new com.order.menu.module.moment.entity.MomentLike();like.setId(10L);when(likes.selectOne(any())).thenReturn(like);
    com.order.menu.module.moment.entity.MomentReaction reaction=new com.order.menu.module.moment.entity.MomentReaction();reaction.setId(11L);reaction.setEmoji("FIRE");when(reactions.selectOne(any())).thenReturn(reaction);

    service.like(1L,7L,false);
    service.react(1L,7L,"FIRE",false);

    verify(likes).deleteById(10L);verify(reactions).deleteById(11L);verifyNoInteractions(messages);
  }

  @Test void reactionLocksMomentBeforeMutatingAndAggregatingAllCurrentEmojis() throws Exception {
    Moment moment=published(8L,2L);when(moments.selectByIdForUpdate(8L)).thenReturn(moment);
    User author=new User();author.setId(2L);author.setNickname("author");author.setRole("CUSTOMER");User actor=new User();actor.setId(1L);actor.setNickname("actor");actor.setRole("CUSTOMER");when(users.selectById(2L)).thenReturn(author);when(users.selectById(1L)).thenReturn(actor);
    com.order.menu.module.moment.entity.MomentReaction heart=new com.order.menu.module.moment.entity.MomentReaction();heart.setId(20L);heart.setMomentId(8L);heart.setUserId(1L);heart.setEmoji("HEART");
    com.order.menu.module.moment.entity.MomentReaction fire=new com.order.menu.module.moment.entity.MomentReaction();fire.setId(21L);fire.setMomentId(8L);fire.setUserId(1L);fire.setEmoji("FIRE");when(reactions.selectList(any())).thenReturn(List.of(heart,fire));

    service.react(1L,8L,"FIRE",true);

    verify(messages).upsertNotification(eq(2L),eq("MOMENT_REACTED"),anyString(),argThat(content->content.contains("HEART")&&content.contains("FIRE")),anyString(),eq("MOMENT:1:MOMENT:8:MOMENT_REACTED"));
    InOrder order=inOrder(moments,reactions,messages);order.verify(moments).selectByIdForUpdate(8L);order.verify(reactions).selectOne(any());order.verify(reactions).insert(any(com.order.menu.module.moment.entity.MomentReaction.class));order.verify(reactions).selectList(any());order.verify(messages).upsertNotification(any(),any(),any(),any(),any(),any());
    org.apache.ibatis.annotations.Select lockingSelect=MomentMapper.class.getMethod("selectByIdForUpdate",Long.class).getAnnotation(org.apache.ibatis.annotations.Select.class);
    assertTrue(String.join(" ",lockingSelect.value()).contains("FOR UPDATE"));
  }

  private Moment published(Long id,Long author){Moment m=new Moment();m.setId(id);m.setAuthorUserId(author);m.setTitle("title");m.setContent("content");m.setStatus("PUBLISHED");m.setVisibility("PUBLIC");m.setPublishedAt(java.time.LocalDateTime.now());m.setCreatedAt(m.getPublishedAt());return m;}
}
