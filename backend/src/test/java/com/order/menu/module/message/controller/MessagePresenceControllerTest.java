package com.order.menu.module.message.controller;

import com.order.menu.module.message.service.presence.ChatPresenceService;
import com.order.menu.security.LoginUser;
import com.order.menu.security.UserContext;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagePresenceControllerTest {

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void heartbeatAndTypingAreScopedToCurrentUser() {
        ChatPresenceService service = mock(ChatPresenceService.class);
        MessagePresenceController controller = new MessagePresenceController(service);
        UserContext.set(new LoginUser(7L, "tester", "CUSTOMER"));
        when(service.typingUsers(7L, 9L)).thenReturn(Set.of(11L));

        controller.heartbeat();
        controller.typing(9L, new MessagePresenceController.TypingRequest(true));
        var response = controller.typingUsers(9L);

        verify(service).heartbeat(7L);
        verify(service).setTyping(7L, 9L, true);
        assertThat(response.getData().userIds()).containsExactly(11L);
    }
}
