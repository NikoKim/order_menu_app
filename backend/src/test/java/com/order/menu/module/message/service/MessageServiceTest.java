package com.order.menu.module.message.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.order.menu.common.constant.RoleConstants;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.message.dto.MessageDtos;
import com.order.menu.module.message.entity.ChatMessage;
import com.order.menu.module.message.entity.ChatBlock;
import com.order.menu.module.message.entity.Conversation;
import com.order.menu.module.message.entity.ConversationMember;
import com.order.menu.module.message.entity.MessageEssence;
import com.order.menu.module.message.entity.MessageEssenceLike;
import com.order.menu.module.message.mapper.ChatMessageMapper;
import com.order.menu.module.message.mapper.ChatBlockMapper;
import com.order.menu.module.message.mapper.ConversationMapper;
import com.order.menu.module.message.mapper.ConversationMemberMapper;
import com.order.menu.module.message.mapper.UserNotificationMapper;
import com.order.menu.module.message.mapper.MessageEssenceMapper;
import com.order.menu.module.message.mapper.MessageEssenceLikeMapper;
import com.order.menu.module.family.mapper.FamilyMemberMapper;
import com.order.menu.module.family.entity.FamilyMember;
import com.order.menu.module.dish.mapper.DishMapper;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.mapper.ShopMapper;
import com.order.menu.module.order.entity.Order;
import com.order.menu.module.order.entity.OrderItem;
import com.order.menu.module.order.mapper.OrderMapper;
import com.order.menu.module.order.mapper.OrderItemMapper;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import com.order.menu.module.moment.entity.Moment;
import com.order.menu.module.moment.entity.MomentComment;
import com.order.menu.websocket.WsPushService;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private ConversationMemberMapper conversationMemberMapper;
    @Mock
    private ChatMessageMapper chatMessageMapper;
    @Mock
    private ChatBlockMapper chatBlockMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ShopMapper shopMapper;
    @Mock
    private WsPushService wsPushService;
    @Mock
    private FamilyMemberMapper familyMemberMapper;
    @Mock
    private DishMapper dishMapper;
    @Mock
    private UserNotificationMapper userNotificationMapper;
    @Mock
    private MessageEssenceMapper messageEssenceMapper;
    @Mock
    private MessageEssenceLikeMapper messageEssenceLikeMapper;

    @Test
    void shopShareRejectsAnotherShopsFamilyGroup() {
        Conversation group = familyGroup(55L, 10L, 1L);
        when(conversationMapper.selectById(55L)).thenReturn(group);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(activeMember(55L, 2L)));
        Shop shop = new Shop();
        shop.setId(11L);
        shop.setStatus(1);
        when(shopMapper.selectById(11L)).thenReturn(shop);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().shareShop(2L, new MessageDtos.ShareShopRequest(55L, 11L, null)));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
    }

    @Test
    void ordinaryShopCanBeSharedInAnyDirectConversation() {
        Conversation direct = directConversation(55L, 1L);
        direct.setDirectKey("1:2");
        when(conversationMapper.selectById(55L)).thenReturn(direct);
        when(conversationMemberMapper.selectList(any())).thenReturn(
                List.of(activeMember(55L, 1L), activeMember(55L, 2L)));
        when(chatBlockMapper.selectCount(any())).thenReturn(0L);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectById(1L)).thenReturn(user(1L, true));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        Shop shop = new Shop();
        shop.setId(11L);
        shop.setName("普通店铺");
        shop.setStatus(1);
        shop.setVisibilityType("PUBLIC");
        when(shopMapper.selectById(11L)).thenReturn(shop);
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            invocation.<ChatMessage>getArgument(0).setId(99L);
            return 1;
        });

        MessageDtos.ChatMessageResponse response = service().shareShop(
                1L, new MessageDtos.ShareShopRequest(55L, 11L, null));

        assertEquals("SHOP_SHARE", response.type());
        assertEquals(11L, response.sharedShopId());
    }

    @Test
    void momentCommentSharePersistsCompleteUntruncatedSnapshots() {
        Conversation direct = directConversation(55L, 1L); direct.setDirectKey("1:2");
        when(conversationMapper.selectById(55L)).thenReturn(direct);
        when(conversationMemberMapper.selectList(any())).thenReturn(
                List.of(activeMember(55L, 1L), activeMember(55L, 2L)));
        when(chatBlockMapper.selectCount(any())).thenReturn(0L);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectById(1L)).thenReturn(user(1L, true));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            invocation.<ChatMessage>getArgument(0).setId(99L); return 1;
        });
        String fullMoment="M".repeat(1800), fullComment="C".repeat(1600);
        Moment moment=new Moment();moment.setId(20L);moment.setAuthorUserId(1L);moment.setTitle("complete");moment.setContent(fullMoment);
        MomentComment comment=new MomentComment();comment.setId(30L);comment.setMomentId(20L);comment.setContent(fullComment);

        MessageDtos.ChatMessageResponse response=service().shareMoment(1L,55L,moment,comment,"author","/cover.jpg");

        assertEquals(fullMoment,response.sharedMomentContent());
        assertEquals(fullMoment,response.sharedMomentPreview());
        assertEquals(fullComment,response.sharedCommentPreview());
    }

    @Test
    void familyShopCanBeSharedPrivatelyBetweenApprovedFamilyMembers() {
        Conversation direct = directConversation(55L, 1L);
        direct.setDirectKey("1:2");
        when(conversationMapper.selectById(55L)).thenReturn(direct);
        when(conversationMemberMapper.selectList(any())).thenReturn(
                List.of(activeMember(55L, 1L), activeMember(55L, 2L)));
        when(chatBlockMapper.selectCount(any())).thenReturn(0L);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectById(1L)).thenReturn(user(1L, true));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        when(familyMemberMapper.selectCount(any())).thenReturn(1L);
        Shop shop = new Shop();
        shop.setId(11L);
        shop.setName("家庭店铺");
        shop.setStatus(1);
        shop.setVisibilityType("FAMILY");
        when(shopMapper.selectById(11L)).thenReturn(shop);
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            invocation.<ChatMessage>getArgument(0).setId(99L);
            return 1;
        });

        MessageDtos.ChatMessageResponse response = service().shareShop(
                1L, new MessageDtos.ShareShopRequest(55L, 11L, null));

        assertEquals("SHOP_SHARE", response.type());
        assertEquals(11L, response.sharedShopId());
    }

    @Test
    void familyShopCannotBeSharedPrivatelyToAnExternalUser() {
        Conversation direct = directConversation(55L, 1L);
        direct.setDirectKey("1:2");
        when(conversationMapper.selectById(55L)).thenReturn(direct);
        when(conversationMemberMapper.selectList(any())).thenReturn(
                List.of(activeMember(55L, 1L), activeMember(55L, 2L)));
        when(familyMemberMapper.selectCount(any())).thenReturn(0L);
        Shop shop = new Shop();
        shop.setId(11L);
        shop.setName("家庭店铺");
        shop.setStatus(1);
        shop.setVisibilityType("FAMILY");
        when(shopMapper.selectById(11L)).thenReturn(shop);

        BusinessException error = assertThrows(BusinessException.class, () -> service().shareShop(
                1L, new MessageDtos.ShareShopRequest(55L, 11L, null)));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
    }

    @Test
    void incomingMessageRestoresHiddenMembershipEvenWhenMuted() {
        Conversation group = familyGroup(55L, 10L, 1L);
        ConversationMember sender = activeMember(55L, 1L);
        ConversationMember recipient = activeMember(55L, 2L);
        recipient.setMuted(true);
        recipient.setHiddenAt(LocalDateTime.now().minusDays(1));
        when(conversationMapper.selectById(55L)).thenReturn(group);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(sender, recipient));
        when(userMapper.selectById(1L)).thenReturn(user(1L, RoleConstants.SHOP, true));
        org.mockito.Mockito.doAnswer(invocation -> {
            ((ChatMessage) invocation.getArgument(0)).setId(99L);
            return 1;
        }).when(chatMessageMapper).insert(any(ChatMessage.class));

        service().sendMessage(1L, 55L, new MessageDtos.SendMessageRequest("hello", null));

        assertEquals(null, recipient.getHiddenAt());
        verify(conversationMemberMapper).update(isNull(), any());
    }

    @Test
    void sendingFromAnExistingDetailDoesNotRestoreTheSendersHiddenConversation() {
        Conversation direct = directConversation(55L, 1L);
        direct.setDirectKey("1:2");
        ConversationMember sender = activeMember(55L, 1L);
        sender.setHiddenAt(LocalDateTime.now().minusDays(1));
        ConversationMember recipient = activeMember(55L, 2L);
        when(conversationMapper.selectById(55L)).thenReturn(direct);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(sender, recipient));
        when(chatBlockMapper.selectCount(any())).thenReturn(0L);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectById(1L)).thenReturn(user(1L, true));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            invocation.<ChatMessage>getArgument(0).setId(99L);
            return 1;
        });

        service().sendMessage(1L, 55L, new MessageDtos.SendMessageRequest("hello", null));

        assertTrue(sender.getHiddenAt() != null);
        verify(conversationMemberMapper, never()).update(isNull(), any());
    }

    @Test
    void guardedHideRejectsAdvancedVersionEvenWhenTimestampCouldBeSameSecond() {
        Conversation conversation = directConversation(55L, 1L);
        conversation.setDirectKey("1:2");
        conversation.setMessageVersion(2L);
        conversation.setLastMessageAt(LocalDateTime.of(2026, 7, 15, 10, 20, 30));
        ConversationMember membership = activeMember(55L, 1L);
        when(conversationMapper.selectById(55L)).thenReturn(conversation);
        when(conversationMapper.selectByIdForUpdate(55L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(membership));

        MessageDtos.ConversationHideResponse response = service().hideConversation(1L, 55L, true, 1L);

        assertFalse(response.hidden());
        assertEquals(2L, response.messageVersion());
        verify(conversationMemberMapper, never()).update(isNull(), any());
    }

    @Test
    void unguardedHideRemainsCompatibleWithoutAnExpectedVersion() {
        Conversation conversation = directConversation(55L, 1L);
        conversation.setDirectKey("1:2");
        conversation.setMessageVersion(9L);
        ConversationMember membership = activeMember(55L, 1L);
        when(conversationMapper.selectById(55L)).thenReturn(conversation);
        when(conversationMapper.selectByIdForUpdate(55L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(membership));
        when(conversationMemberMapper.selectOne(any())).thenReturn(membership);
        when(userMapper.selectById(1L)).thenReturn(user(1L, RoleConstants.CUSTOMER, true));

        MessageDtos.ConversationHideResponse response = service().hideConversation(1L, 55L, false, null);

        assertTrue(response.hidden());
        assertEquals(9L, response.messageVersion());
    }

    @Test
    void guardedHidePersistsWhenConversationVersionStillMatches() {
        Conversation conversation = directConversation(55L, 1L);
        conversation.setDirectKey("1:2");
        conversation.setMessageVersion(2L);
        ConversationMember membership = activeMember(55L, 1L);
        when(conversationMapper.selectById(55L)).thenReturn(conversation);
        when(conversationMapper.selectByIdForUpdate(55L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(membership));
        when(conversationMemberMapper.selectOne(any())).thenReturn(membership);
        when(userMapper.selectById(1L)).thenReturn(user(1L, RoleConstants.CUSTOMER, true));

        MessageDtos.ConversationHideResponse response = service().hideConversation(1L, 55L, true, 2L);

        assertTrue(response.hidden());
        assertEquals(2L, response.messageVersion());
        assertTrue(membership.getHiddenAt() != null);
        verify(conversationMemberMapper).update(isNull(), any());
    }

    @Test
    void successfulMessageLocksConversationBeforeMemberRestoreAndInsertThenAdvancesVersion() {
        Conversation conversation = directConversation(55L, 1L);
        conversation.setDirectKey("1:2");
        conversation.setMessageVersion(7L);
        ConversationMember sender = activeMember(55L, 1L);
        ConversationMember recipient = activeMember(55L, 2L);
        recipient.setHiddenAt(LocalDateTime.now().minusDays(1));
        when(conversationMapper.selectById(55L)).thenReturn(conversation);
        when(conversationMapper.selectByIdForUpdate(55L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(sender, recipient));
        when(chatBlockMapper.selectCount(any())).thenReturn(0L);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectById(1L)).thenReturn(user(1L, true));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            invocation.<ChatMessage>getArgument(0).setId(99L);
            return 1;
        });

        service().sendMessage(1L, 55L, new MessageDtos.SendMessageRequest("hello", null));

        assertEquals(8L, conversation.getMessageVersion());
        InOrder order = inOrder(conversationMapper, conversationMemberMapper, chatMessageMapper);
        order.verify(conversationMapper).selectByIdForUpdate(55L);
        order.verify(conversationMemberMapper).update(isNull(), any());
        order.verify(chatMessageMapper).insert(any(ChatMessage.class));
        order.verify(conversationMapper).updateById(conversation);
    }

    @Test
    void failedMessageDoesNotLockOrAdvanceConversationVersion() {
        Conversation conversation = directConversation(55L, 1L);
        conversation.setDirectKey("1:2");
        conversation.setMessageVersion(7L);
        when(conversationMapper.selectById(55L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(
                List.of(activeMember(55L, 1L), activeMember(55L, 2L)));
        when(chatBlockMapper.selectCount(any())).thenReturn(0L, 1L);
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            invocation.<ChatMessage>getArgument(0).setId(99L);
            return 1;
        });

        service().sendMessage(1L, 55L, new MessageDtos.SendMessageRequest("blocked", null));

        assertEquals(7L, conversation.getMessageVersion());
        verify(conversationMapper, never()).selectByIdForUpdate(55L);
        verify(conversationMapper, never()).updateById(conversation);
    }

    @Test
    void systemMessageLocksConversationBeforeMemberMutationAndInsert() {
        Conversation conversation = familyGroup(55L, 10L, 1L);
        conversation.setMessageVersion(3L);
        ConversationMember pending = activeMember(55L, 2L);
        pending.setMemberStatus("PENDING");
        when(conversationMapper.selectById(55L)).thenReturn(conversation);
        when(conversationMapper.selectByIdForUpdate(55L)).thenReturn(conversation);
        when(conversationMemberMapper.selectOne(any())).thenReturn(pending);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(activeMember(55L, 1L), pending));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            invocation.<ChatMessage>getArgument(0).setId(100L);
            return 1;
        });

        service().approveFamilyGroupMember(1L, 55L, 2L);

        assertEquals(4L, conversation.getMessageVersion());
        InOrder order = inOrder(conversationMapper, conversationMemberMapper, chatMessageMapper);
        order.verify(conversationMapper).selectByIdForUpdate(55L);
        order.verify(conversationMemberMapper).updateById(pending);
        order.verify(chatMessageMapper).insert(any(ChatMessage.class));
        order.verify(conversationMapper).updateById(conversation);
    }

    @Test
    void hiddenShopDirectConversationCannotBeActivelyRestored() {
        Conversation conversation = directConversation(55L, 1L);
        conversation.setDirectKey("1:2");
        ConversationMember membership = activeMember(55L, 1L);
        membership.setHiddenAt(LocalDateTime.now().minusDays(1));
        when(conversationMapper.selectById(55L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(membership));
        when(conversationMemberMapper.selectOne(any())).thenReturn(membership);
        when(userMapper.selectById(1L)).thenReturn(user(1L, RoleConstants.SHOP, true));

        BusinessException error = assertThrows(BusinessException.class, () -> service()
                .updateConversationPreferences(1L, 55L,
                        new MessageDtos.ConversationPreferenceRequest(null, false, null)));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
    }

    @Test
    void privateProfileShowsPhoneWhileHidingJoinedFamilies() {
        Conversation direct = directConversation(55L, 1L);
        direct.setDirectKey("1:2");
        ConversationMember viewer = activeMember(55L, 1L);
        ConversationMember targetMember = activeMember(55L, 2L);
        User target = user(2L, RoleConstants.CUSTOMER, true);
        target.setPhone("13800138000");
        target.setShowPhone(true);
        target.setShowJoinedFamilies(false);
        when(conversationMapper.selectById(55L)).thenReturn(direct);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(viewer));
        when(conversationMemberMapper.selectOne(any())).thenReturn(targetMember);
        when(userMapper.selectById(2L)).thenReturn(target);

        MessageDtos.ChatUserProfileResponse response = service().chatUserProfile(1L, 2L, 55L);

        assertEquals("13800138000", response.phone());
        assertTrue(response.phoneVisible());
        assertFalse(response.joinedFamiliesVisible());
        assertTrue(response.joinedFamilies().isEmpty());
    }

    @Test
    void groupProfileHidesPhoneWhileShowingJoinedFamilies() {
        Conversation group = familyGroup(55L, 10L, 1L);
        ConversationMember viewer = activeMember(55L, 1L);
        ConversationMember targetMember = activeMember(55L, 2L);
        User target = user(2L, RoleConstants.CUSTOMER, true);
        target.setPhone("13800138000");
        target.setShowPhone(false);
        target.setShowJoinedFamilies(true);
        FamilyMember family = new FamilyMember();
        family.setShopId(10L);
        family.setUserId(2L);
        family.setStatus("APPROVED");
        Shop shop = new Shop();
        shop.setId(10L);
        shop.setName("店铺");
        when(conversationMapper.selectById(55L)).thenReturn(group);
        when(conversationMapper.selectOne(any())).thenReturn(group);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(viewer));
        when(conversationMemberMapper.selectOne(any())).thenReturn(targetMember);
        when(userMapper.selectById(2L)).thenReturn(target);
        when(familyMemberMapper.selectList(any())).thenReturn(List.of(family));
        when(shopMapper.selectById(10L)).thenReturn(shop);

        MessageDtos.ChatUserProfileResponse response = service().chatUserProfile(1L, 2L, 55L);

        assertEquals(null, response.phone());
        assertFalse(response.phoneVisible());
        assertTrue(response.joinedFamiliesVisible());
        assertEquals(1, response.joinedFamilies().size());
    }

    @Test
    void nonOwnerCannotAddEssence() {
        when(conversationMapper.selectById(55L)).thenReturn(familyGroup(55L, 10L, 1L));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service().addEssence(2L, 55L, 99L));
        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(messageEssenceMapper, never()).insert(any(MessageEssence.class));
    }

    @Test
    void memberCanLikeEssenceAndUnreadCountUsesLastReadTime() {
        MessageService service = service();
        service.setMessageEssenceMappers(messageEssenceMapper, messageEssenceLikeMapper);
        Conversation group = familyGroup(55L, 10L, 1L);
        ConversationMember member = activeMember(55L, 2L);
        member.setLastEssenceReadAt(LocalDateTime.now().minusDays(2));
        MessageEssence essence = new MessageEssence();
        essence.setId(7L);
        essence.setConversationId(55L);
        essence.setMessageId(99L);
        essence.setCreatedAt(LocalDateTime.now().minusDays(1));
        ChatMessage message = new ChatMessage();
        message.setId(99L);
        message.setConversationId(55L);
        message.setSenderUserId(1L);
        message.setType("TEXT");
        message.setContent("important");
        when(conversationMapper.selectById(55L)).thenReturn(group);
        when(conversationMemberMapper.selectOne(any())).thenReturn(member);
        when(messageEssenceMapper.selectOne(any())).thenReturn(essence);
        when(messageEssenceMapper.selectList(any())).thenReturn(List.of(essence));
        when(chatMessageMapper.selectById(99L)).thenReturn(message);
        when(userMapper.selectById(1L)).thenReturn(user(1L, RoleConstants.SHOP, true));
        when(messageEssenceLikeMapper.selectCount(any())).thenReturn(0L, 0L, 1L, 1L);

        MessageDtos.EssenceItemResponse liked = service.likeEssence(2L, 55L, 99L, true);
        MessageDtos.EssenceListResponse list = service.essences(2L, 55L);

        verify(messageEssenceLikeMapper).insert(any(MessageEssenceLike.class));
        assertEquals(1L, list.unreadCount());
        assertTrue(liked.likeCount() >= 0);
    }

    @Test
    void multiplePinnedEssencesSortByMostRecentPinBeforeUnpinnedItems() {
        MessageEssence olderPin = essence(1L, LocalDateTime.now().minusMinutes(2), LocalDateTime.now());
        MessageEssence newerPin = essence(2L, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusDays(1));
        MessageEssence unpinned = essence(3L, null, LocalDateTime.now().plusDays(1));

        List<MessageEssence> sorted = java.util.stream.Stream.of(unpinned, olderPin, newerPin)
                .sorted(MessageService.essenceOrdering()).toList();

        assertEquals(List.of(newerPin, olderPin, unpinned), sorted);
    }

    @Test
    void conversationPreferencesPersistPinAndMuteIndependently() {
        Conversation conversation = directConversation(55L, 1L);
        conversation.setDirectKey("1:2");
        ConversationMember membership = activeMember(55L, 1L);
        when(conversationMapper.selectById(55L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(membership));
        when(conversationMemberMapper.selectOne(any())).thenReturn(membership);
        when(userMapper.selectById(1L)).thenReturn(user(1L, RoleConstants.CUSTOMER, true));

        MessageDtos.ConversationPreferenceResponse response = service().updateConversationPreferences(1L, 55L,
                new MessageDtos.ConversationPreferenceRequest(true, null, true));

        assertTrue(response.muted());
        assertTrue(response.pinned());
        verify(conversationMemberMapper).update(isNull(), any());
    }

    @Test
    void conversationPreferencesPersistUnpinWithExplicitNullUpdate() {
        Conversation conversation = directConversation(55L, 1L);
        conversation.setDirectKey("1:2");
        ConversationMember membership = activeMember(55L, 1L);
        membership.setPinnedAt(LocalDateTime.now().minusMinutes(1));
        when(conversationMapper.selectById(55L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(membership));
        when(conversationMemberMapper.selectOne(any())).thenReturn(membership);
        when(userMapper.selectById(1L)).thenReturn(user(1L, RoleConstants.CUSTOMER, true));

        MessageDtos.ConversationPreferenceResponse response = service().updateConversationPreferences(1L, 55L,
                new MessageDtos.ConversationPreferenceRequest(null, null, false));

        assertFalse(response.pinned());
        assertEquals(null, response.pinnedAt());
        verify(conversationMemberMapper).update(isNull(), any());
    }

    @Test
    void essenceUnpinPersistsExplicitNullUpdate() {
        MessageService service = service();
        service.setMessageEssenceMappers(messageEssenceMapper, messageEssenceLikeMapper);
        Conversation group = familyGroup(55L, 10L, 1L);
        MessageEssence essence = new MessageEssence();
        essence.setId(7L);
        essence.setConversationId(55L);
        essence.setMessageId(99L);
        essence.setPinnedAt(LocalDateTime.now().minusMinutes(1));
        essence.setCreatedAt(LocalDateTime.now().minusDays(1));
        ChatMessage message = new ChatMessage();
        message.setId(99L);
        message.setConversationId(55L);
        message.setSenderUserId(1L);
        message.setType("TEXT");
        message.setContent("important");
        when(conversationMapper.selectById(55L)).thenReturn(group);
        when(messageEssenceMapper.selectOne(any())).thenReturn(essence);
        when(chatMessageMapper.selectById(99L)).thenReturn(message);
        when(userMapper.selectById(1L)).thenReturn(user(1L, RoleConstants.SHOP, true));
        when(messageEssenceLikeMapper.selectCount(any())).thenReturn(0L);

        MessageDtos.EssenceItemResponse response = service.pinEssence(1L, 55L, 99L, false);

        assertFalse(response.pinned());
        assertEquals(null, response.pinnedAt());
        verify(messageEssenceMapper).update(isNull(), any());
    }

    @Test
    void ownerCannotHideOwnFamilyGroup() {
        Conversation conversation = new Conversation();
        conversation.setId(55L);
        conversation.setType("FAMILY_GROUP");
        conversation.setStatus("ACTIVE");
        conversation.setOwnerUserId(1L);
        ConversationMember membership = activeMember(55L, 1L);
        when(conversationMapper.selectById(55L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(membership));
        when(conversationMemberMapper.selectOne(any())).thenReturn(membership);
        when(userMapper.selectById(1L)).thenReturn(user(1L, RoleConstants.SHOP, true));

        BusinessException error = assertThrows(BusinessException.class, () -> service()
                .updateConversationPreferences(1L, 55L,
                        new MessageDtos.ConversationPreferenceRequest(null, true, null)));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(conversationMemberMapper, never()).updateById(membership);
    }

    @Test
    void createDirectRejectsRecipientWhoDisablesPrivateChat() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, true));
        when(userMapper.selectById(2L)).thenReturn(user(2L, false));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().createDirectWithUser(1L, 2L));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(conversationMapper, never()).insert(any(Conversation.class));
    }

    @Test
    void createDirectReusesExistingConversationForSamePair() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, true));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        Conversation existing = directConversation(55L, 1L);
        existing.setDirectKey("1:2");
        when(conversationMapper.selectOne(any())).thenReturn(existing);

        MessageDtos.ConversationResponse response = service().createDirectWithUser(2L, 1L);

        assertEquals(55L, response.id());
        assertEquals(1L, response.directUserId());
        verify(conversationMapper, never()).insert(any(Conversation.class));
        verify(conversationMemberMapper, never()).insert(any(ConversationMember.class));
    }

    @Test
    void createDirectWithUserRejectsShopRequester() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, RoleConstants.SHOP, true));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().createDirectWithUser(1L, 2L));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(userMapper, never()).selectById(2L);
    }

    @Test
    void createDirectWithUserRejectsShopRecipient() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, RoleConstants.CUSTOMER, true));
        when(userMapper.selectById(2L)).thenReturn(user(2L, RoleConstants.SHOP, true));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().createDirectWithUser(1L, 2L));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(conversationMapper, never()).insert(any(Conversation.class));
    }

    @Test
    void searchCustomersRejectsShopRequester() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, RoleConstants.SHOP, true));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().searchCustomers(1L, "Alice"));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(userMapper, never()).selectList(any());
    }

    @Test
    void createDirectWithShopUsesShopOwnerAsRecipient() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, RoleConstants.CUSTOMER, true));
        when(userMapper.selectById(2L)).thenReturn(user(2L, RoleConstants.SHOP, true));
        Shop shop = new Shop();
        shop.setId(88L);
        shop.setStatus(1);
        shop.setOwnerUserId(2L);
        when(shopMapper.selectById(88L)).thenReturn(shop);
        when(conversationMapper.selectOne(any())).thenReturn(null);

        MessageDtos.ConversationResponse response = service().createDirectWithShop(1L, 88L);

        assertEquals(2L, response.directUserId());
        verify(conversationMapper).insert(any(Conversation.class));
    }

    @Test
    void createDirectRequeriesConversationAfterConcurrentUniqueCollision() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, true));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        Conversation persistedByAnotherRequest = directConversation(55L, 1L);
        persistedByAnotherRequest.setDirectKey("1:2");
        when(conversationMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(persistedByAnotherRequest);
        when(conversationMapper.insert(any(Conversation.class)))
                .thenThrow(new DuplicateKeyException("duplicate direct conversation"));

        MessageDtos.ConversationResponse response = service().createDirectWithUser(1L, 2L);

        assertEquals(55L, response.id());
        verify(conversationMemberMapper, never()).insert(any(ConversationMember.class));
    }

    @Test
    void initiatorCannotSendSecondMessageBeforeRecipientReplies() {
        Conversation conversation = directConversation(9L, 1L);
        conversation.setDirectKey("1:2");
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(
                member(9L, 1L), member(9L, 2L)));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        ChatMessage unanswered = new ChatMessage();
        unanswered.setSenderUserId(1L);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(unanswered));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().sendMessage(1L, 9L, new MessageDtos.SendMessageRequest("again")));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
    }

    @Test
    void eitherParticipantCannotSendSecondMessageBeforeTheOtherReplies() {
        Conversation conversation = directConversation(9L, 2L);
        conversation.setDirectKey("1:2");
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(
                member(9L, 1L), member(9L, 2L)));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        ChatMessage unanswered = new ChatMessage();
        unanswered.setSenderUserId(1L);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(unanswered));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().sendMessage(1L, 9L, new MessageDtos.SendMessageRequest("again")));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
    }

    @Test
    void sendMessageRejectsDirectConversationWhenRecipientDisablesPrivateChat() {
        Conversation conversation = directConversation(9L, 1L);
        conversation.setDirectKey("1:2");
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(
                member(9L, 1L), member(9L, 2L)));
        when(userMapper.selectById(2L)).thenReturn(user(2L, false));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().sendMessage(1L, 9L, new MessageDtos.SendMessageRequest("hello")));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
    }

    @Test
    void sendMessageAllowsReplyWhenDisabledRecipientStartedTheConversation() {
        Conversation conversation = directConversation(9L, 1L);
        conversation.setDirectKey("1:2");
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(
                member(9L, 1L), member(9L, 2L)));
        when(userMapper.selectById(1L)).thenReturn(user(1L, false));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        ChatMessage firstMessage = new ChatMessage();
        firstMessage.setSenderUserId(1L);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(firstMessage));
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            invocation.<ChatMessage>getArgument(0).setId(42L);
            return 1;
        });

        service().sendMessage(2L, 9L, new MessageDtos.SendMessageRequest("reply"));

        verify(chatMessageMapper).insert(any(ChatMessage.class));
    }

    @Test
    void sendMessagePersistsMessageAndUpdatesConversation() {
        Conversation conversation = directConversation(9L, 1L);
        conversation.setDirectKey("1:2");
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(
                member(9L, 1L), member(9L, 2L)));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        User sender = user(1L, true);
        sender.setNickname("Alice");
        sender.setAvatarUrl("alice.png");
        when(userMapper.selectById(1L)).thenReturn(sender);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(42L);
            return 1;
        });

        MessageDtos.ChatMessageResponse response = service()
                .sendMessage(1L, 9L, new MessageDtos.SendMessageRequest(" hello "));

        org.mockito.ArgumentCaptor<ChatMessage> messageCaptor = org.mockito.ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).insert(messageCaptor.capture());
        ChatMessage savedMessage = messageCaptor.getValue();
        assertEquals(42L, response.id());
        assertEquals(9L, response.conversationId());
        assertEquals(1L, response.senderUserId());
        assertEquals("hello", response.content());
        assertEquals("Alice", response.senderNickname());
        assertEquals("alice.png", response.senderAvatarUrl());
        assertEquals(9L, savedMessage.getConversationId());
        assertEquals(1L, savedMessage.getSenderUserId());
        assertEquals("TEXT", savedMessage.getType());
        assertEquals("hello", savedMessage.getContent());
        verify(conversationMapper).updateById(conversation);
    }

    @Test
    void shopParticipantUsesShopNameInConversationAndMessage() {
        Conversation conversation = directConversation(9L, 2L);
        conversation.setDirectKey("1:2");
        User shopUser = user(2L, RoleConstants.SHOP, true);
        shopUser.setNickname("account nickname");
        when(userMapper.selectById(2L)).thenReturn(shopUser);
        Shop shop = new Shop();
        shop.setOwnerUserId(2L);
        shop.setName("Happy Kitchen");
        when(shopMapper.selectOne(any())).thenReturn(shop);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(member(9L, 1L), member(9L, 2L)));
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        ChatMessage message = new ChatMessage();
        message.setId(5L);
        message.setConversationId(9L);
        message.setSenderUserId(2L);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(message));

        MessageDtos.ConversationMessagesResponse response = service().messages(1L, 9L);

        assertEquals("Happy Kitchen", response.conversation().directUserNickname());
        assertEquals("Happy Kitchen", response.messages().get(0).senderNickname());
    }

    @Test
    void messagesRejectsUserWhoIsNotAnActiveConversationMember() {
        when(conversationMapper.selectById(9L)).thenReturn(directConversation(9L, 1L));
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().messages(3L, 9L));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(chatMessageMapper, never()).selectList(any());
    }

    @Test
    void conversationSummaryContainsLatestMessageAndUnreadCount() {
        Conversation conversation = directConversation(9L, 1L);
        conversation.setDirectKey("1:2");
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(member(9L, 1L), member(9L, 2L)));
        when(conversationMapper.selectBatchIds(any())).thenReturn(List.of(conversation));
        ChatMessage latest = new ChatMessage();
        latest.setId(12L);
        latest.setConversationId(9L);
        latest.setSenderUserId(2L);
        latest.setType("TEXT");
        latest.setContent("latest hello");
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(latest));
        User peer = user(2L, true);
        peer.setNickname("Bob");
        when(userMapper.selectById(2L)).thenReturn(peer);

        MessageDtos.ConversationResponse response = service().conversations(1L).get(0);

        assertEquals("latest hello", response.lastMessageContent());
        assertEquals(1L, response.unreadCount());
        assertFalse(response.muted());
    }

    @Test
    void openingMessagesMarksLatestMessageRead() {
        Conversation conversation = directConversation(9L, 1L);
        conversation.setDirectKey("1:2");
        ConversationMember viewer = member(9L, 1L);
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(viewer, member(9L, 2L)));
        when(conversationMemberMapper.selectOne(any())).thenReturn(viewer);
        ChatMessage latest = new ChatMessage();
        latest.setId(12L);
        latest.setConversationId(9L);
        latest.setSenderUserId(2L);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(latest));

        service().messages(1L, 9L);

        assertEquals(12L, viewer.getLastReadMessageId());
        verify(conversationMemberMapper).updateById(viewer);
        verify(wsPushService).pushToUser(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("CHAT"),
                org.mockito.ArgumentMatchers.eq("CONVERSATION_UPDATED"),
                any());
    }

    @Test
    void senderCanRecallOwnMessageWithinThreeMinutes() {
        Conversation conversation = directConversation(9L, 1L);
        ChatMessage message = new ChatMessage();
        message.setId(12L);
        message.setConversationId(9L);
        message.setSenderUserId(1L);
        message.setType("TEXT");
        message.setContent("hello");
        message.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(member(9L, 1L)));
        when(chatMessageMapper.selectById(12L)).thenReturn(message);
        when(userMapper.selectById(1L)).thenReturn(user(1L, true));

        MessageDtos.ChatMessageResponse response = service().recallMessage(1L, 9L, 12L);

        assertEquals(12L, response.id());
        verify(chatMessageMapper).updateById(message);
    }

    @Test
    void messageCannotBeRecalledAfterThreeMinutes() {
        Conversation conversation = directConversation(9L, 1L);
        ChatMessage message = new ChatMessage();
        message.setId(12L);
        message.setConversationId(9L);
        message.setSenderUserId(1L);
        message.setCreatedAt(LocalDateTime.now().minusMinutes(4));
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(member(9L, 1L)));
        when(chatMessageMapper.selectById(12L)).thenReturn(message);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().recallMessage(1L, 9L, 12L));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(chatMessageMapper, never()).updateById(any(ChatMessage.class));
    }

    @Test
    void messageBlockedByRecipientIsVisibleAsFailedOnlyToSender() {
        Conversation conversation = directConversation(9L, 1L);
        conversation.setDirectKey("1:2");
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(member(9L, 1L), member(9L, 2L)));
        when(chatBlockMapper.selectCount(any())).thenReturn(0L, 1L);
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            invocation.<ChatMessage>getArgument(0).setId(77L);
            return 1;
        });
        when(userMapper.selectById(1L)).thenReturn(user(1L, true));

        MessageDtos.ChatMessageResponse response = service()
                .sendMessage(1L, 9L, new MessageDtos.SendMessageRequest("hello"));

        assertEquals("FAILED_BLOCKED", response.deliveryStatus());
        assertEquals(true, response.deliveryFailed());
        assertEquals("对方已将您拉黑，无法发送消息", response.failureReason());
        verify(wsPushService).pushToUser(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("CHAT"),
                org.mockito.ArgumentMatchers.eq("MESSAGE_CREATED"), any());
        verify(wsPushService, never()).pushToUser(org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq("CHAT"),
                org.mockito.ArgumentMatchers.eq("MESSAGE_CREATED"), any());
        verify(conversationMapper, never()).updateById(any(Conversation.class));
    }

    @Test
    void blockedRecipientCannotLoadFailedMessage() {
        Conversation conversation = directConversation(9L, 1L);
        conversation.setDirectKey("1:2");
        conversation.setLastMessageAt(LocalDateTime.now());
        ChatMessage failed = new ChatMessage();
        failed.setId(77L);
        failed.setConversationId(9L);
        failed.setSenderUserId(1L);
        failed.setType("TEXT");
        failed.setContent("hello");
        failed.setDeliveryStatus("FAILED_BLOCKED");
        failed.setDeliveryFailureReason("对方已将您拉黑，无法发送消息");
        failed.setCreatedAt(LocalDateTime.now());
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(member(9L, 2L)));
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(failed));
        when(userMapper.selectById(1L)).thenReturn(user(1L, true));

        MessageDtos.ConversationMessagesResponse response = service().messages(2L, 9L);

        assertEquals(List.of(), response.messages());
        assertEquals(null, response.conversation().lastMessageContent());
        assertEquals(null, response.conversation().lastMessageAt());
        verify(conversationMemberMapper, never()).updateById(any(ConversationMember.class));
    }

    @Test
    void blockRelationCanBeCreatedQueriedAndRemoved() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, true));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        when(chatBlockMapper.selectCount(any())).thenReturn(1L, 0L);

        MessageDtos.ChatBlockResponse response = service().chatBlock(1L, 2L);
        service().blockUser(1L, 2L);
        service().unblockUser(1L, 2L);

        assertEquals(true, response.blockedByMe());
        assertEquals(false, response.blockedMe());
        verify(chatBlockMapper).insert(any(ChatBlock.class));
        verify(chatBlockMapper).delete(any());
    }

    @Test
    void orderSharePersistsOwnerOrderSnapshot() {
        Conversation conversation = directConversation(9L, 1L);
        conversation.setDirectKey("1:2");
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(member(9L, 1L), member(9L, 2L)));
        Order order = new Order();
        order.setId(31L);
        order.setOrderNo("202607130001");
        order.setCustomerUserId(1L);
        order.setShopId(8L);
        order.setStatus("COMPLETED");
        order.setTotalAmount(new BigDecimal("25.50"));
        order.setRemark("少辣");
        order.setCreatedAt(LocalDateTime.now().minusHours(1));
        when(orderMapper.selectById(31L)).thenReturn(order);
        Shop shop = new Shop();
        shop.setId(8L);
        shop.setName("幸福小馆");
        when(shopMapper.selectById(8L)).thenReturn(shop);
        OrderItem item = new OrderItem();
        item.setDishName("宫保鸡丁");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("25.50"));
        item.setSubtotalAmount(new BigDecimal("25.50"));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            invocation.<ChatMessage>getArgument(0).setId(88L);
            return 1;
        });
        when(userMapper.selectById(1L)).thenReturn(user(1L, true));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));

        MessageDtos.ChatMessageResponse response = service().shareOrder(1L,
                new MessageDtos.ShareOrderRequest(9L, 31L));

        assertEquals("ORDER_SHARE", response.type());
        assertEquals(31L, response.orderId());
        assertEquals("202607130001", response.orderNo());
        assertEquals("幸福小馆", response.orderShopName());
        assertEquals("宫保鸡丁", response.orderItems().get(0).dishName());
    }

    @Test
    void quotedOrderCardContainsCompleteSnapshotIncludingOrderNumber() {
        Conversation conversation = directConversation(9L, 1L);
        conversation.setDirectKey("1:2");
        ChatMessage orderShare = new ChatMessage();
        orderShare.setId(51L);
        orderShare.setConversationId(9L);
        orderShare.setSenderUserId(2L);
        orderShare.setType("ORDER_SHARE");
        orderShare.setOrderId(31L);
        orderShare.setOrderNo("202607130001");
        orderShare.setOrderCreatedAt(LocalDateTime.now().minusHours(1));
        orderShare.setOrderStatus("COMPLETED");
        orderShare.setOrderShopName("幸福小馆");
        orderShare.setOrderItemsJson("[{\"dishName\":\"宫保鸡丁\",\"unitPrice\":25.50,\"quantity\":1,\"subtotalAmount\":25.50}]");
        orderShare.setOrderTotalAmount(new BigDecimal("25.50"));
        orderShare.setOrderRemark("少辣");
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(member(9L, 1L), member(9L, 2L)));
        when(chatMessageMapper.selectById(51L)).thenReturn(orderShare);
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            invocation.<ChatMessage>getArgument(0).setId(52L);
            return 1;
        });
        when(userMapper.selectById(1L)).thenReturn(user(1L, true));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));

        MessageDtos.ChatMessageResponse response = service().sendMessage(1L, 9L,
                new MessageDtos.SendMessageRequest("收到", 51L));

        assertEquals(51L, response.quotedMessageId());
        assertEquals("ORDER_SHARE", response.quotedMessage().type());
        assertEquals(31L, response.quotedMessage().orderId());
        assertEquals("202607130001", response.quotedMessage().orderNo());
        assertEquals("幸福小馆", response.quotedMessage().orderShopName());
        assertEquals("宫保鸡丁", response.quotedMessage().orderItems().get(0).dishName());
        assertEquals(new BigDecimal("25.50"), response.quotedMessage().orderTotalAmount());
        assertEquals("少辣", response.quotedMessage().orderRemark());
    }

    @Test
    void orderShareRejectsOrderOwnedByAnotherCustomer() {
        when(conversationMapper.selectById(9L)).thenReturn(directConversation(9L, 1L));
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(member(9L, 1L)));
        Order order = new Order();
        order.setId(31L);
        order.setCustomerUserId(2L);
        when(orderMapper.selectById(31L)).thenReturn(order);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().shareOrder(1L, new MessageDtos.ShareOrderRequest(9L, 31L)));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
    }

    private MessageService service() {
        return new MessageService(conversationMapper, conversationMemberMapper, chatMessageMapper, null, chatBlockMapper,
                userMapper, shopMapper, familyMemberMapper, dishMapper, orderMapper,
                orderItemMapper, userNotificationMapper, wsPushService);
    }

    private User user(Long id, boolean allowPrivateChat) {
        return user(id, RoleConstants.CUSTOMER, allowPrivateChat);
    }

    private User user(Long id, String role, boolean allowPrivateChat) {
        User user = new User();
        user.setId(id);
        user.setStatus(1);
        user.setRole(role);
        user.setAllowPrivateChat(allowPrivateChat);
        return user;
    }

    private Conversation directConversation(Long id, Long ownerUserId) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setType("DIRECT");
        conversation.setOwnerUserId(ownerUserId);
        conversation.setStatus("ACTIVE");
        return conversation;
    }

    private Conversation familyGroup(Long id, Long shopId, Long ownerUserId) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setType("FAMILY_GROUP");
        conversation.setShopId(shopId);
        conversation.setOwnerUserId(ownerUserId);
        conversation.setName("家庭群");
        conversation.setStatus("ACTIVE");
        return conversation;
    }

    private ConversationMember activeMember(Long conversationId, Long userId) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setMemberStatus("ACTIVE");
        member.setMuted(false);
        return member;
    }

    private MessageEssence essence(Long id, LocalDateTime pinnedAt, LocalDateTime createdAt) {
        MessageEssence essence = new MessageEssence();
        essence.setId(id);
        essence.setPinnedAt(pinnedAt);
        essence.setCreatedAt(createdAt);
        return essence;
    }

    private ConversationMember member(Long conversationId, Long userId) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setMemberStatus("ACTIVE");
        return member;
    }
}
