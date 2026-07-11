package com.order.menu.module.message.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.order.menu.module.dish.mapper.DishMapper;
import com.order.menu.module.family.mapper.FamilyMemberMapper;
import com.order.menu.module.message.dto.MessageDtos;
import com.order.menu.module.message.entity.UserNotification;
import com.order.menu.module.message.mapper.ChatMessageMapper;
import com.order.menu.module.message.mapper.ConversationMapper;
import com.order.menu.module.message.mapper.ConversationMemberMapper;
import com.order.menu.module.message.mapper.UserNotificationMapper;
import com.order.menu.module.shop.mapper.ShopMapper;
import com.order.menu.module.user.mapper.UserMapper;
import com.order.menu.websocket.WsPushService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock private UserNotificationMapper notificationMapper;
    @Mock private WsPushService wsPushService;

    @Test
    void createNotificationPersistsAndPushesToTheTargetUser() {
        when(notificationMapper.selectCount(any())).thenReturn(1L);
        when(notificationMapper.insert(any(UserNotification.class))).thenAnswer(invocation -> {
            invocation.<UserNotification>getArgument(0).setId(81L);
            return 1;
        });

        MessageDtos.NotificationResponse response = service().createNotification(
                9L, "ORDER", "New order", "Order A-1", "{\"orderId\":1}");

        assertEquals(81L, response.id());
        assertEquals(9L, response.userId());
        assertEquals("ORDER", response.type());
        ArgumentCaptor<UserNotification> captured = ArgumentCaptor.forClass(UserNotification.class);
        verify(notificationMapper).insert(captured.capture());
        assertEquals("New order", captured.getValue().getTitle());
        verify(wsPushService).pushToUser(9L, "NOTIFICATION", "CREATED", java.util.Map.of(
                "notificationId", 81L, "unreadCount", 1L));
    }

    @Test
    void unreadCountOnlyCountsUnreadNotificationsForTheCurrentUser() {
        when(notificationMapper.selectCount(any())).thenReturn(3L);

        assertEquals(3L, service().unreadCount(9L));
    }

    @Test
    void markAllReadUpdatesOnlyUnreadNotificationsForTheCurrentUser() {
        service().markAllNotificationsRead(9L);

        verify(notificationMapper).update(any(), any());
        verify(wsPushService).pushToUser(9L, "NOTIFICATION", "READ_CHANGED", java.util.Map.of("unreadCount", 0L));
    }

    private MessageService service() {
        return new MessageService(mock(ConversationMapper.class), mock(ConversationMemberMapper.class),
                mock(ChatMessageMapper.class), mock(UserMapper.class), mock(ShopMapper.class),
                mock(FamilyMemberMapper.class), mock(DishMapper.class), notificationMapper, wsPushService);
    }
}
