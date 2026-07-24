package com.order.menu.module.moment.service;

import static org.junit.jupiter.api.Assertions.*; import static org.mockito.ArgumentMatchers.*; import static org.mockito.Mockito.*;
import com.order.menu.common.exception.BusinessException; import com.order.menu.module.dish.mapper.DishMapper; import com.order.menu.module.family.mapper.FamilyMemberMapper; import com.order.menu.module.message.entity.*; import com.order.menu.module.message.mapper.*; import com.order.menu.module.message.service.MessageService; import com.order.menu.module.moment.dto.MomentBaseCacheDto; import com.order.menu.module.moment.dto.MomentDtos; import com.order.menu.module.moment.dto.MomentViewerStateCacheDto; import com.order.menu.module.moment.entity.*; import com.order.menu.module.moment.mapper.*; import com.order.menu.module.moment.service.cache.MomentAuxiliaryCachePort; import com.order.menu.module.moment.service.cache.MomentCacheRebuildGuard; import com.order.menu.module.shop.mapper.ShopMapper; import com.order.menu.module.user.entity.User; import com.order.menu.module.user.mapper.UserMapper;
import java.util.*; import java.util.concurrent.atomic.AtomicReference; import org.junit.jupiter.api.BeforeEach; import org.junit.jupiter.api.Test; import org.junit.jupiter.api.extension.ExtendWith; import org.mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class MomentServiceTest {
  @Mock MomentMapper moments; @Mock MomentTopicMapper topics; @Mock MomentTopicRelationMapper topicRelations;
  @Mock MomentLikeMapper likes; @Mock MomentReactionMapper reactions; @Mock MomentCommentMapper comments;
  @Mock MomentReadMapper reads; @Mock MomentCommentLikeMapper commentLikes; @Mock MomentUserPreferenceMapper preferences;
  @Mock MomentFeedExposureMapper exposures; @Mock com.order.menu.websocket.WsPushService wsPushService;
  @Mock MomentQuoteMapper quotes; @Mock MomentMentionMapper mentions; @Mock MomentCommentMentionMapper commentMentions;
  @Mock UserMapper users; @Mock ShopMapper shops; @Mock DishMapper dishes; @Mock ConversationMemberMapper members;
  @Mock FamilyMemberMapper families; @Mock ChatMessageMapper chatMessages; @Mock MessageService messages; @Mock MomentCachePort cache;
  @Mock MomentAccessCacheService accessCache; @Mock MomentFeedExposureService exposureService; @Mock MomentAuxiliaryCachePort auxiliaryCache; @Mock MomentCacheRebuildGuard rebuildGuard;
  @InjectMocks MomentService service;

  @BeforeEach void cacheDefaults() {
    lenient().when(accessCache.accessFor(anyLong())).thenReturn(new MomentAccessSnapshot(Set.of(),Set.of()));
    lenient().when(exposureService.findExposed(anyLong(),anyString(),anyCollection())).thenReturn(Set.of());
    lenient().when(rebuildGuard.tryAcquire(anyString(),anyString(),any())).thenReturn(MomentCacheRebuildGuard.Lease.bypass());
  }

  @Test void incompleteDraftCanBeSaved() {
    AtomicReference<Moment> saved=new AtomicReference<>();
    doAnswer(inv->{Moment m=inv.getArgument(0);m.setId(9L);saved.set(m);return 1;}).when(moments).insert(any(Moment.class));
    when(moments.selectById(9L)).thenAnswer(inv->saved.get());
    when(comments.selectCount(any())).thenReturn(4L,1L);
    User user=new User();user.setId(1L);user.setNickname("u");user.setRole("CUSTOMER");when(users.selectById(1L)).thenReturn(user);
    MomentDtos.MomentResponse result=service.create(1L,new MomentDtos.UpsertRequest("","","PRIVATE","DRAFT",null,null,List.of(),List.of()));
    assertEquals("DRAFT",result.status()); assertEquals("",saved.get().getTitle());
    assertEquals(4L,result.commentCount());assertEquals(1L,result.rootCommentCount());
  }

  @Test void ownMentionCardIsMarkedSelfAndRevealsPrivatePhone() {
    User user=new User();user.setId(1L);user.setNickname("owner");user.setRole("CUSTOMER");user.setPhone("13800000000");user.setShowPhone(false);when(users.selectById(1L)).thenReturn(user);

    MomentDtos.UserCardResponse result=service.userCard(1L,1L);

    assertTrue(result.self());assertTrue(result.phoneVisible());assertEquals("13800000000",result.phone());
  }

  @Test void publishedMomentRequiresCompleteTitleAndContent() {
    assertThrows(BusinessException.class,()->service.create(1L,new MomentDtos.UpsertRequest("abc","body","PUBLIC","PUBLISHED",null,null,List.of(),List.of())));
    assertThrows(BusinessException.class,()->service.create(1L,new MomentDtos.UpsertRequest("valid"," ","PUBLIC","PUBLISHED",null,null,List.of(),List.of())));
  }

  @Test void unreadRelatedFeedUsesOnlyEstablishedTwoWayChats() {
    when(accessCache.accessFor(1L)).thenReturn(new MomentAccessSnapshot(Set.of(),Set.of(3L)));
    when(moments.selectCount(any())).thenReturn(2L);
    assertEquals(2L,service.unread(1L).relatedUnreadCount());
    verify(accessCache,times(2)).accessFor(1L);
    verifyNoInteractions(chatMessages);
  }

  @Test void unreadCountUsesRedisCacheWithoutDatabaseFallback() {
    when(cache.getUnread(1L)).thenReturn(Optional.of(7L));

    assertEquals(7L,service.unread(1L).relatedUnreadCount());

    verifyNoInteractions(chatMessages,reads,moments);
  }

  @Test void markReadLoadsMomentsAndExistingStatesInBatches() {
    Moment first=published(41L,2L);Moment second=published(42L,3L);
    when(accessCache.accessFor(1L)).thenReturn(new MomentAccessSnapshot(Set.of(),Set.of(2L,3L)));
    when(moments.selectByIds(anyCollection())).thenReturn(List.of(first,second));
    MomentRead existing=new MomentRead();existing.setMomentId(41L);existing.setUserId(1L);
    when(reads.selectList(any())).thenReturn(List.of(existing));
    when(moments.selectCount(any())).thenReturn(0L);

    service.markRead(1L,List.of(41L,42L,42L));

    verify(moments).selectByIds(List.of(41L,42L));
    verify(moments,never()).selectById(anyLong());
    verify(reads,never()).selectCount(any());
    verify(reads,times(2)).selectList(any());
    verify(reads,times(1)).insert(argThat((MomentRead row)->Long.valueOf(42L).equals(row.getMomentId())));
    verify(cache).evictViewerStates(1L,List.of(41L,42L));
  }

  @Test void hotFeedFiltersPrivateCandidatesBeforeReturningTopTen() {
    Moment privateMoment=published(10L,2L);privateMoment.setVisibility("PRIVATE");
    Moment publicMoment=published(11L,3L);
    when(cache.getHotMomentIds()).thenReturn(Optional.of(List.of(10L,11L)));
    when(moments.selectByIds(any())).thenReturn(List.of(privateMoment,publicMoment));
    when(cache.getMoments(any())).thenReturn(Map.of());
    when(cache.getStats(11L)).thenReturn(Optional.empty());
    when(cache.getHotCommentId(11L)).thenReturn(Optional.of(0L));
    User author=new User();author.setId(3L);author.setNickname("public-author");author.setRole("CUSTOMER");
    when(users.selectById(3L)).thenReturn(author);

    List<MomentDtos.MomentResponse> result=service.hot(1L,10);

    assertEquals(List.of(11L),result.stream().map(MomentDtos.MomentResponse::id).toList());
    verify(moments,never()).selectHotCandidateIds(anyInt());
  }

  @Test void commentReplyPreviewUsesOldestFirstOrdering() {
    Moment moment=published(22L,8L);MomentComment root=new MomentComment();root.setId(101L);root.setMomentId(22L);root.setAuthorUserId(8L);root.setCreatedAt(java.time.LocalDateTime.now());
    MomentComment older=new MomentComment();older.setId(102L);older.setMomentId(22L);older.setParentCommentId(101L);older.setAuthorUserId(8L);older.setCreatedAt(java.time.LocalDateTime.of(2026,7,17,12,0));
    MomentComment newer=new MomentComment();newer.setId(103L);newer.setMomentId(22L);newer.setParentCommentId(101L);newer.setAuthorUserId(8L);newer.setCreatedAt(java.time.LocalDateTime.of(2026,7,17,13,0));
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<MomentComment> raw=new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,10);raw.setRecords(List.of(root));raw.setTotal(1);
    when(moments.selectById(22L)).thenReturn(moment);when(comments.selectPage(any(),any())).thenReturn(raw);
    User author=new User();author.setId(8L);author.setNickname("author");author.setRole("CUSTOMER");when(users.selectById(8L)).thenReturn(author);
    when(comments.selectList(any())).thenReturn(List.of(newer,older));

    var result=service.comments(7L,22L,1,10);

    assertEquals(List.of(102L,103L),result.records().get(0).replies().stream().map(MomentDtos.CommentResponse::id).toList());
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
    when(moments.selectPage(any(),any())).thenReturn(raw);
    when(users.selectById(anyLong())).thenAnswer(inv->{Long id=inv.getArgument(0);User u=new User();u.setId(id);u.setNickname("u"+id);u.setRole("CUSTOMER");return u;});

    MomentDtos.PageResponse<MomentDtos.MomentResponse> result=service.feed(1L,"RECOMMENDED",1,4,true);

    assertEquals(List.of(2L,4L,1L,3L),result.records().stream().map(MomentDtos.MomentResponse::id).toList());
    assertEquals(Set.of(1L,2L,3L,4L),new HashSet<>(result.records().stream().map(MomentDtos.MomentResponse::id).toList()));
    verify(exposureService).recordExposures(eq(1L),eq("RECOMMENDED"),eq(List.of(2L,4L)),any());
  }

  @Test void viewerStateCacheMissLoadsWholePageWithOneQueryPerInteractionType() {
    Moment first=published(41L,2L);Moment second=published(42L,3L);
    when(cache.getHotMomentIds()).thenReturn(Optional.of(List.of(41L,42L)));
    when(moments.selectByIds(any())).thenReturn(List.of(first,second));
    when(cache.getMoments(any())).thenReturn(Map.of(41L,base(first,"HEART"),42L,base(second,"HEART")));
    when(cache.getViewerStates(eq(1L),anyCollection())).thenReturn(Map.of());
    MomentLike liked=new MomentLike();liked.setMomentId(41L);liked.setUserId(1L);
    MomentRead read=new MomentRead();read.setMomentId(42L);read.setUserId(1L);
    MomentReaction reacted=new MomentReaction();reacted.setMomentId(41L);reacted.setUserId(1L);reacted.setEmoji("HEART");
    when(likes.selectList(any())).thenReturn(List.of(liked));
    when(reads.selectList(any())).thenReturn(List.of(read));
    when(reactions.selectList(any())).thenReturn(List.of(reacted));

    List<MomentDtos.MomentResponse> result=service.hot(1L,10);

    assertTrue(result.get(0).likedByMe());
    assertTrue(result.get(0).reactions().get(0).reactedByMe());
    assertTrue(result.get(1).readByMe());
    verify(likes,times(1)).selectList(any());
    verify(reads,times(1)).selectList(any());
    verify(reactions,times(1)).selectList(any());
    verify(likes,never()).selectCount(any());
    verify(reads,never()).selectCount(any());
    verify(cache).putViewerStates(eq(1L),argThat(states->
        states.size()==2
            && states.get(41L).liked()
            && states.get(41L).reactionEmojis().contains("HEART")
            && states.get(42L).read()));
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

  @Test void commentLikeResponseUsesCommittedIntentInsteadOfStaleViewerCache() {
    Moment moment=published(7L,1L);when(moments.selectById(7L)).thenReturn(moment);
    MomentComment comment=new MomentComment();comment.setId(5L);comment.setMomentId(7L);comment.setAuthorUserId(1L);
    when(comments.selectById(5L)).thenReturn(comment);
    User author=new User();author.setId(1L);author.setNickname("author");author.setRole("CUSTOMER");
    when(users.selectById(1L)).thenReturn(author);

    MomentDtos.CommentResponse result=service.likeComment(1L,5L,true);

    assertTrue(result.likedByMe());
    verify(auxiliaryCache).evictCommentLike(1L,5L);
    verify(auxiliaryCache,never()).getCommentLikes(anyLong(),anyCollection());
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

  @Test void quotedMomentStoresSnapshotAndNotifiesWithPublishingMomentLink() {
    Moment source=published(10L,2L);source.setTitle("被引用标题");source.setContent("被引用正文");
    AtomicReference<Moment> saved=new AtomicReference<>();AtomicReference<MomentQuote> savedQuote=new AtomicReference<>();
    doAnswer(inv->{Moment m=inv.getArgument(0);m.setId(20L);saved.set(m);return 1;}).when(moments).insert(any(Moment.class));
    doAnswer(inv->{savedQuote.set(inv.getArgument(0));return 1;}).when(quotes).insert(any(MomentQuote.class));
    when(moments.selectById(anyLong())).thenAnswer(inv->{Long id=inv.getArgument(0);return id==10L?source:saved.get();});
    when(quotes.selectById(20L)).thenAnswer(inv->savedQuote.get());
    when(users.selectById(anyLong())).thenAnswer(inv->{Long id=inv.getArgument(0);User u=new User();u.setId(id);u.setNickname(id==1L?"发布者":"来源作者");u.setRole("CUSTOMER");u.setStatus(1);return u;});

    MomentDtos.MomentResponse result=service.create(1L,new MomentDtos.UpsertRequest(
        "发表标题","发表正文","PUBLIC","PUBLISHED",null,null,List.of(),List.of(),10L,null,List.of()));

    assertEquals("被引用标题",result.quote().momentTitle());assertEquals("被引用正文",result.quote().content());
    verify(messages).upsertNotification(eq(2L),eq("MOMENT_QUOTED"),eq("动态通知"),
        eq("发布者在发表标题下引用了你的动态：被引用标题"),argThat(payload->payload.contains("\"momentId\":20")),anyString());
  }

  @Test void editingKeepsUnavailableQuoteSnapshotWithoutRevalidatingSource() {
    Moment owner=published(20L,1L);Moment hidden=published(10L,2L);hidden.setVisibility("PRIVATE");
    MomentQuote snapshot=new MomentQuote();snapshot.setMomentId(20L);snapshot.setQuoteType("MOMENT");snapshot.setQuotedMomentId(10L);snapshot.setAuthorUserId(2L);snapshot.setAuthorNickname("来源作者");snapshot.setAuthorRole("CUSTOMER");snapshot.setMomentTitle("快照标题");snapshot.setQuotedContent("快照正文");snapshot.setQuotedAt(java.time.LocalDateTime.now());
    when(moments.selectById(20L)).thenReturn(owner);when(moments.selectById(10L)).thenReturn(hidden);when(quotes.selectById(20L)).thenReturn(snapshot);
    User author=new User();author.setId(1L);author.setNickname("发布者");author.setRole("CUSTOMER");when(users.selectById(1L)).thenReturn(author);

    MomentDtos.MomentResponse result=service.update(1L,20L,new MomentDtos.UpsertRequest(
        "新标题","新正文","PUBLIC","PUBLISHED",null,null,List.of(),List.of(),10L,null,List.of()));

    assertFalse(result.quote().available());assertEquals("快照正文",result.quote().content());
    verify(quotes,never()).deleteById(20L);verify(quotes,never()).insert(any(MomentQuote.class));
  }

  @Test void appShareIsCountedOnlyAfterChatMessageSucceeds() {
    Moment moment=published(31L,2L);when(moments.selectById(31L)).thenReturn(moment);
    User author=new User();author.setId(2L);author.setNickname("author");when(users.selectById(2L)).thenReturn(author);
    when(messages.shareMoment(anyLong(),anyLong(),any(),any(),any(),any())).thenReturn(null);

    service.share(1L,31L,new MomentDtos.ShareRequest(9L,null));

    verify(moments).incrementAppShareCount(31L);

    reset(messages,moments);when(moments.selectById(31L)).thenReturn(moment);when(users.selectById(2L)).thenReturn(author);
    when(messages.shareMoment(anyLong(),anyLong(),any(),any(),any(),any())).thenThrow(new RuntimeException("send failed"));
    assertThrows(RuntimeException.class,()->service.share(1L,31L,new MomentDtos.ShareRequest(9L,null)));
    verify(moments,never()).incrementAppShareCount(anyLong());
  }

  @Test void confirmedExternalShareReturnsUpdatedChannelCounts() {
    Moment moment=published(32L,2L);moment.setShareCount(7L);moment.setAppShareCount(4L);moment.setExternalShareCount(3L);
    when(moments.selectById(32L)).thenReturn(moment);
    doAnswer(inv->{moment.setShareCount(8L);moment.setExternalShareCount(4L);return 1;}).when(moments).incrementExternalShareCount(32L);

    MomentDtos.ShareStatsResponse result=service.recordExternalShare(1L,32L,null);

    verify(moments).incrementExternalShareCount(32L);
    assertEquals(8L,result.shareCount());assertEquals(4L,result.appShareCount());assertEquals(4L,result.externalShareCount());
  }

  private Moment published(Long id,Long author){Moment m=new Moment();m.setId(id);m.setAuthorUserId(author);m.setTitle("title");m.setContent("content");m.setStatus("PUBLISHED");m.setVisibility("PUBLIC");m.setPublishedAt(java.time.LocalDateTime.now());m.setCreatedAt(m.getPublishedAt());return m;}
  private MomentBaseCacheDto base(Moment moment,String emoji){return new MomentBaseCacheDto(
      1,moment.getId(),moment.getAuthorUserId(),"author-"+moment.getAuthorUserId(),null,"CUSTOMER",
      moment.getTitle(),moment.getContent(),moment.getVisibility(),moment.getStatus(),List.of(),
      1,0,0,0,0,0,List.of(new MomentBaseCacheDto.ReactionSummary(emoji,1)),
      null,null,null,null,null,null,null,null,null,List.of(),
      null,null,null,null,null,null,null,null,null,null,
      null,List.of(),null,moment.getPublishedAt(),moment.getCreatedAt());}
}
