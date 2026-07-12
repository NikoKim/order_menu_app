package com.order.menu.module.message.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.dish.entity.Dish;
import com.order.menu.module.dish.mapper.DishMapper;
import com.order.menu.module.family.entity.FamilyMember;
import com.order.menu.module.family.mapper.FamilyMemberMapper;
import com.order.menu.module.message.dto.MessageDtos;
import com.order.menu.module.message.entity.ChatMessage;
import com.order.menu.module.message.entity.Conversation;
import com.order.menu.module.message.entity.ConversationMember;
import com.order.menu.module.message.mapper.ChatMessageMapper;
import com.order.menu.module.message.mapper.ConversationMapper;
import com.order.menu.module.message.mapper.ConversationMemberMapper;
import com.order.menu.module.message.mapper.UserNotificationMapper;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.mapper.ShopMapper;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import com.order.menu.websocket.WsPushService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FamilyGroupServiceTest {
    @Mock private ConversationMapper conversationMapper;
    @Mock private ConversationMemberMapper conversationMemberMapper;
    @Mock private ChatMessageMapper chatMessageMapper;
    @Mock private UserMapper userMapper;
    @Mock private ShopMapper shopMapper;
    @Mock private FamilyMemberMapper familyMemberMapper;
    @Mock private DishMapper dishMapper;

    @Test
    void createFamilyGroupRequiresTwoApprovedMembers() {
        when(shopMapper.selectOne(any())).thenReturn(shop(8L, 1L));
        when(familyMemberMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class, () -> service().createFamilyGroup(1L));

        assertEquals(ErrorCode.CONFLICT, error.getCode());
        verify(conversationMapper, never()).insert(any(Conversation.class));
    }

    @Test
    void createFamilyGroupKeepsAutoJoinOptOutMemberPending() {
        when(shopMapper.selectOne(any())).thenReturn(shop(8L, 1L));
        when(familyMemberMapper.selectCount(any())).thenReturn(2L);
        when(conversationMapper.selectOne(any())).thenReturn(null);
        when(familyMemberMapper.selectList(any())).thenReturn(List.of(familyMember(2L)));
        when(userMapper.selectById(2L)).thenReturn(user(2L, false));
        when(conversationMapper.insert(any(Conversation.class))).thenAnswer(invocation -> {
            invocation.<Conversation>getArgument(0).setId(44L);
            return 1;
        });

        service().createFamilyGroup(1L);

        ArgumentCaptor<ConversationMember> captor = ArgumentCaptor.forClass(ConversationMember.class);
        verify(conversationMemberMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals("PENDING", captor.getAllValues().get(1).getMemberStatus());
    }

    @Test
    void createFamilyGroupActivatesAutoJoinMember() {
        when(shopMapper.selectOne(any())).thenReturn(shop(8L, 1L));
        when(familyMemberMapper.selectCount(any())).thenReturn(2L);
        when(conversationMapper.selectOne(any())).thenReturn(null);
        when(familyMemberMapper.selectList(any())).thenReturn(List.of(familyMember(2L)));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        when(conversationMapper.insert(any(Conversation.class))).thenAnswer(invocation -> {
            invocation.<Conversation>getArgument(0).setId(44L);
            return 1;
        });

        service().createFamilyGroup(1L);

        ArgumentCaptor<ConversationMember> captor = ArgumentCaptor.forClass(ConversationMember.class);
        verify(conversationMemberMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals("ACTIVE", captor.getAllValues().get(1).getMemberStatus());
    }

    @Test
    void ownerCanRemoveGroupMemberWithoutChangingFamilyMembership() {
        Conversation conversation = familyConversation(44L, 8L, 1L);
        when(conversationMapper.selectById(44L)).thenReturn(conversation);
        when(conversationMemberMapper.selectOne(any())).thenReturn(activeMember(44L, 2L));
        when(familyMemberMapper.selectCount(any())).thenReturn(1L);

        service().removeFamilyGroupMember(1L, 44L, 2L);

        ArgumentCaptor<ConversationMember> captor = ArgumentCaptor.forClass(ConversationMember.class);
        verify(conversationMemberMapper).updateById(captor.capture());
        assertEquals("REMOVED", captor.getValue().getMemberStatus());
        verify(familyMemberMapper, never()).deleteById(any(Long.class));
        verify(familyMemberMapper, never()).delete(any());
    }

    @Test
    void dissolvedFamilyGroupRejectsMessages() {
        Conversation conversation = familyConversation(44L, 8L, 1L);
        conversation.setStatus("DISSOLVED");
        when(conversationMapper.selectById(44L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(activeMember(44L, 1L)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().sendMessage(1L, 44L, new MessageDtos.SendMessageRequest("hello")));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
    }

    @Test
    void ownerCanRestoreDissolvedFamilyGroup() {
        Conversation conversation = familyConversation(44L, 8L, 1L);
        conversation.setStatus("DISSOLVED");
        when(conversationMapper.selectById(44L)).thenReturn(conversation);

        service().restoreFamilyGroup(1L, 44L);

        assertEquals("ACTIVE", conversation.getStatus());
        verify(conversationMapper).updateById(conversation);
    }

    @Test
    void approvedMemberCanDiscoverFamilyGroupByShopAfterLeavingChat() {
        Conversation conversation = familyConversation(44L, 8L, 1L);
        when(conversationMapper.selectOne(any())).thenReturn(conversation);
        when(familyMemberMapper.selectCount(any())).thenReturn(1L);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of());

        MessageDtos.FamilyGroupResponse response = service().familyGroupByShop(2L, 8L);

        assertEquals(44L, response.id());
    }

    @Test
    void newlyApprovedMemberCanReenterExistingGroupAfterEarlierDeparture() {
        Conversation conversation = familyConversation(44L, 8L, 1L);
        ConversationMember departed = activeMember(44L, 2L);
        departed.setMemberStatus("LEFT");
        when(conversationMapper.selectOne(any())).thenReturn(conversation);
        when(conversationMemberMapper.selectOne(any())).thenReturn(departed);
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));

        service().onFamilyMemberApproved(familyMember(2L));

        assertEquals("ACTIVE", departed.getMemberStatus());
        verify(conversationMemberMapper).updateById(departed);
    }

    @Test
    void familyDishCannotBeSharedWithNonMemberInDirectConversation() {
        Conversation conversation = directConversation(51L, "1:3");
        when(conversationMapper.selectById(51L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(activeMember(51L, 1L), activeMember(51L, 3L)));
        when(userMapper.selectById(3L)).thenReturn(user(3L, true));
        when(dishMapper.selectById(9L)).thenReturn(dish(9L, 8L));
        when(shopMapper.selectById(8L)).thenReturn(shop(8L, 1L));
        when(familyMemberMapper.selectCount(any())).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().shareDish(1L, new MessageDtos.ShareDishRequest(51L, 9L)));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
    }

    @Test
    void directChatInitiatorCannotShareSecondMessageBeforeReply() {
        Conversation conversation = directConversation(51L, "1:2");
        conversation.setOwnerUserId(1L);
        when(conversationMapper.selectById(51L)).thenReturn(conversation);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(
                activeMember(51L, 1L), activeMember(51L, 2L)));
        when(userMapper.selectById(2L)).thenReturn(user(2L, true));
        ChatMessage unanswered = new ChatMessage();
        unanswered.setSenderUserId(1L);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(unanswered));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service().shareDish(1L, new MessageDtos.ShareDishRequest(51L, 9L)));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
    }

    private MessageService service() {
        return new MessageService(conversationMapper, conversationMemberMapper, chatMessageMapper, userMapper,
                shopMapper, familyMemberMapper, dishMapper, mock(UserNotificationMapper.class), mock(WsPushService.class));
    }

    private Shop shop(Long id, Long ownerUserId) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setOwnerUserId(ownerUserId);
        shop.setStatus(1);
        shop.setVisibilityType("FAMILY");
        shop.setFamilyImageUrl("family.png");
        return shop;
    }

    private User user(Long id, boolean autoJoin) {
        User user = new User();
        user.setId(id);
        user.setStatus(1);
        user.setAutoJoinFamilyChat(autoJoin);
        return user;
    }

    private FamilyMember familyMember(Long userId) {
        FamilyMember member = new FamilyMember();
        member.setShopId(8L);
        member.setUserId(userId);
        member.setStatus("APPROVED");
        return member;
    }

    private Conversation familyConversation(Long id, Long shopId, Long ownerUserId) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setType("FAMILY_GROUP");
        conversation.setShopId(shopId);
        conversation.setOwnerUserId(ownerUserId);
        conversation.setStatus("ACTIVE");
        return conversation;
    }

    private Conversation directConversation(Long id, String directKey) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setType("DIRECT");
        conversation.setDirectKey(directKey);
        conversation.setStatus("ACTIVE");
        return conversation;
    }

    private ConversationMember activeMember(Long conversationId, Long userId) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setMemberStatus("ACTIVE");
        return member;
    }

    private Dish dish(Long id, Long shopId) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setShopId(shopId);
        dish.setName("Noodles");
        dish.setImageUrl("dish.png");
        dish.setPrice(new BigDecimal("12.50"));
        dish.setStatus("ON_SALE");
        return dish;
    }
}
