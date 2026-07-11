package com.order.menu.module.message.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.order.menu.common.constant.RoleConstants;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.message.dto.MessageDtos;
import com.order.menu.module.message.entity.ChatMessage;
import com.order.menu.module.message.entity.Conversation;
import com.order.menu.module.message.entity.ConversationMember;
import com.order.menu.module.message.mapper.ChatMessageMapper;
import com.order.menu.module.message.mapper.ConversationMapper;
import com.order.menu.module.message.mapper.ConversationMemberMapper;
import com.order.menu.module.message.mapper.UserNotificationMapper;
import com.order.menu.module.family.mapper.FamilyMemberMapper;
import com.order.menu.module.dish.mapper.DishMapper;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.mapper.ShopMapper;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import com.order.menu.websocket.WsPushService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
    private UserMapper userMapper;
    @Mock
    private ShopMapper shopMapper;

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
    }

    private MessageService service() {
        return new MessageService(conversationMapper, conversationMemberMapper, chatMessageMapper, userMapper, shopMapper,
                mock(FamilyMemberMapper.class), mock(DishMapper.class), mock(UserNotificationMapper.class), mock(WsPushService.class));
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

    private ConversationMember member(Long conversationId, Long userId) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setMemberStatus("ACTIVE");
        return member;
    }
}
