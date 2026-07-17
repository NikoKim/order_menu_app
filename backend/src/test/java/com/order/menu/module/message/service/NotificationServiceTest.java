package com.order.menu.module.message.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

import com.order.menu.module.dish.mapper.DishMapper;
import com.order.menu.module.family.mapper.FamilyMemberMapper;
import com.order.menu.module.message.dto.MessageDtos;
import com.order.menu.module.message.entity.UserNotification;
import com.order.menu.module.message.entity.ChatMessage;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void deduplicatedNotificationOnlyDispatchesTheFirstTime() {
        java.util.concurrent.atomic.AtomicReference<UserNotification> stored = new java.util.concurrent.atomic.AtomicReference<>();
        when(notificationMapper.selectOne(any())).thenAnswer(invocation -> stored.get());
        when(notificationMapper.insert(any(UserNotification.class))).thenAnswer(invocation -> {
            UserNotification value = invocation.getArgument(0); value.setId(91L); stored.set(value); return 1;
        });
        when(notificationMapper.selectCount(any())).thenReturn(1L);
        MessageService service = service();
        MobilePushService mobile = mock(MobilePushService.class);
        service.setMobilePushService(mobile);

        service.upsertNotification(9L, "MOMENT_LIKED", "like", "first", "{}", "moment:dedupe");
        service.updateExistingNotificationByDedupe(9L, "MOMENT_LIKED", "cancelled", "cancelled", "{}", "moment:dedupe");
        service.upsertNotification(9L, "MOMENT_LIKED", "like", "latest", "{}", "moment:dedupe");

        verify(notificationMapper, times(1)).insert(any(UserNotification.class));
        verify(notificationMapper, times(2)).updateById(stored.get());
        assertEquals("latest", stored.get().getContent());
        verify(wsPushService, times(1)).pushToUser(any(), any(), any(), any());
        verify(mobile, times(1)).dispatch(9L, "like", "first", "{}");
    }

    @Test
    void concurrentDuplicateInsertReloadsAndUpdatesTheWinningNotification() {
        UserNotification winner = new UserNotification();
        winner.setId(92L); winner.setUserId(9L); winner.setDedupeKey("moment:emoji");
        winner.setType("MOMENT_REACTED"); winner.setTitle("emoji"); winner.setContent("first");
        when(notificationMapper.selectOne(any())).thenReturn(null);
        when(notificationMapper.insert(any(UserNotification.class)))
                .thenThrow(new DuplicateKeyException("concurrent notification"));
        when(notificationMapper.selectByUserAndDedupeKeyForUpdate(9L, "moment:emoji")).thenReturn(winner);

        MessageDtos.NotificationResponse response = service().upsertNotification(
                9L, "MOMENT_REACTED", "emoji", "HEART FIRE", "{}", "moment:emoji");

        assertEquals(92L, response.id());
        assertEquals("HEART FIRE", response.content());
        verify(notificationMapper).updateById(winner);
        verify(wsPushService, never()).pushToUser(any(), any(), any(), any());
    }

    @Test
    void quotedMomentShareKeepsTheCompleteCardSnapshot() {
        ChatMessage message = new ChatMessage();
        message.setId(11L); message.setType("MOMENT_COMMENT_SHARE");
        message.setSharedMomentId(21L); message.setSharedMomentCommentId(31L);
        message.setSharedMomentTitle("Dinner"); message.setSharedMomentContent("Full moment");
        message.setSharedMomentPreview("Moment preview"); message.setSharedCommentPreview("Comment preview");
        message.setSharedMomentAuthorName("Niko"); message.setSharedMomentAuthorAvatar("/avatar.jpg");
        message.setSharedMomentCoverUrl("/cover.jpg"); message.setSharedCardIcon("COMMENT");

        MessageDtos.QuotedMessageSnapshot snapshot = ReflectionTestUtils.invokeMethod(
                service(), "toQuotedSnapshot", message);

        assertEquals(21L, snapshot.sharedMomentId());
        assertEquals(31L, snapshot.sharedMomentCommentId());
        assertEquals("Comment preview", snapshot.sharedCommentPreview());
        assertEquals("COMMENT", snapshot.sharedCardIcon());
    }

    private MessageService service() {
        return new MessageService(mock(ConversationMapper.class), mock(ConversationMemberMapper.class),
                mock(ChatMessageMapper.class), mock(UserMapper.class), mock(ShopMapper.class),
                mock(FamilyMemberMapper.class), mock(DishMapper.class), notificationMapper, wsPushService);
    }
}
