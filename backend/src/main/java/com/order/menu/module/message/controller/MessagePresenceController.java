package com.order.menu.module.message.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.module.message.service.presence.ChatPresenceService;
import com.order.menu.security.UserContext;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessagePresenceController {

    private final ChatPresenceService presenceService;

    @PutMapping("/presence/online")
    public ApiResponse<Void> heartbeat() {
        presenceService.heartbeat(UserContext.get().userId());
        return ApiResponse.success();
    }

    @PutMapping("/conversations/{conversationId}/typing")
    public ApiResponse<Void> typing(
            @PathVariable Long conversationId,
            @RequestBody(required = false) TypingRequest request) {
        presenceService.setTyping(
                UserContext.get().userId(),
                conversationId,
                request == null || request.active());
        return ApiResponse.success();
    }

    @GetMapping("/conversations/{conversationId}/typing")
    public ApiResponse<TypingUsersResponse> typingUsers(@PathVariable Long conversationId) {
        Long userId = UserContext.get().userId();
        Set<Long> users = presenceService.typingUsers(userId, conversationId);
        return ApiResponse.success(new TypingUsersResponse(users));
    }

    public record TypingRequest(boolean active) {
    }

    public record TypingUsersResponse(Set<Long> userIds) {
    }
}
