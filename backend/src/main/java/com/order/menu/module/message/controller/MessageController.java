package com.order.menu.module.message.controller;

import com.order.menu.common.response.ApiResponse;
import com.order.menu.module.message.dto.MessageDtos;
import com.order.menu.module.message.service.MessageService;
import com.order.menu.security.UserContext;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;

    @PostMapping("/direct/users/{userId}")
    public ApiResponse<MessageDtos.ConversationResponse> createDirectWithUser(@PathVariable Long userId) {
        return ApiResponse.success(messageService.createDirectWithUser(UserContext.get().userId(), userId));
    }

    @PostMapping("/direct/shops/{shopId}")
    public ApiResponse<MessageDtos.ConversationResponse> createDirectWithShop(@PathVariable Long shopId) {
        return ApiResponse.success(messageService.createDirectWithShop(UserContext.get().userId(), shopId));
    }

    @GetMapping("/users")
    public ApiResponse<List<MessageDtos.CustomerSearchResponse>> searchCustomers(@RequestParam(required = false) String nickname) {
        return ApiResponse.success(messageService.searchCustomers(UserContext.get().userId(), nickname));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<MessageDtos.ConversationResponse>> conversations() {
        return ApiResponse.success(messageService.conversations(UserContext.get().userId()));
    }

    @GetMapping("/notifications")
    public ApiResponse<MessageDtos.NotificationListResponse> notifications() {
        return ApiResponse.success(messageService.notifications(UserContext.get().userId()));
    }

    @GetMapping("/notifications/unread-count")
    public ApiResponse<Long> unreadNotificationCount() {
        return ApiResponse.success(messageService.unreadCount(UserContext.get().userId()));
    }

    @PutMapping("/notifications/{notificationId}/read")
    public ApiResponse<Void> markNotificationRead(@PathVariable Long notificationId) {
        messageService.markNotificationRead(UserContext.get().userId(), notificationId);
        return ApiResponse.success();
    }

    @PutMapping("/notifications/read-all")
    public ApiResponse<Void> markAllNotificationsRead() {
        messageService.markAllNotificationsRead(UserContext.get().userId());
        return ApiResponse.success();
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<MessageDtos.ConversationMessagesResponse> messages(@PathVariable Long conversationId) {
        return ApiResponse.success(messageService.messages(UserContext.get().userId(), conversationId));
    }

    @GetMapping("/blocks/users/{userId}")
    public ApiResponse<MessageDtos.ChatBlockResponse> chatBlock(@PathVariable Long userId) {
        return ApiResponse.success(messageService.chatBlock(UserContext.get().userId(), userId));
    }

    @PostMapping("/blocks/users/{userId}")
    public ApiResponse<Void> blockUser(@PathVariable Long userId) {
        messageService.blockUser(UserContext.get().userId(), userId);
        return ApiResponse.success();
    }

    @DeleteMapping("/blocks/users/{userId}")
    public ApiResponse<Void> unblockUser(@PathVariable Long userId) {
        messageService.unblockUser(UserContext.get().userId(), userId);
        return ApiResponse.success();
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ApiResponse<MessageDtos.ChatMessageResponse> sendMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody MessageDtos.SendMessageRequest request) {
        return ApiResponse.success(messageService.sendMessage(UserContext.get().userId(), conversationId, request));
    }

    @PostMapping("/conversations/{conversationId}/messages/{messageId}/recall")
    public ApiResponse<MessageDtos.ChatMessageResponse> recallMessage(
            @PathVariable Long conversationId, @PathVariable Long messageId) {
        return ApiResponse.success(messageService.recallMessage(UserContext.get().userId(), conversationId, messageId));
    }

    @DeleteMapping("/conversations/{conversationId}/messages/{messageId}")
    public ApiResponse<Void> deleteMessage(@PathVariable Long conversationId, @PathVariable Long messageId) {
        messageService.deleteMessage(UserContext.get().userId(), conversationId, messageId);
        return ApiResponse.success();
    }

    @PostMapping("/family-groups")
    public ApiResponse<MessageDtos.FamilyGroupResponse> createFamilyGroup() {
        return ApiResponse.success(messageService.createFamilyGroup(UserContext.get().userId()));
    }

    @GetMapping("/family-groups/{conversationId}")
    public ApiResponse<MessageDtos.FamilyGroupResponse> familyGroup(@PathVariable Long conversationId) {
        return ApiResponse.success(messageService.familyGroup(UserContext.get().userId(), conversationId));
    }

    @GetMapping("/family-groups/by-shop/{shopId}")
    public ApiResponse<MessageDtos.FamilyGroupResponse> familyGroupByShop(@PathVariable Long shopId) {
        return ApiResponse.success(messageService.familyGroupByShop(UserContext.get().userId(), shopId));
    }

    @PutMapping("/family-groups/{conversationId}")
    public ApiResponse<MessageDtos.FamilyGroupResponse> updateFamilyGroup(
            @PathVariable Long conversationId, @Valid @RequestBody MessageDtos.FamilyGroupProfileRequest request) {
        return ApiResponse.success(messageService.updateFamilyGroup(UserContext.get().userId(), conversationId, request));
    }

    @PostMapping("/family-groups/{conversationId}/dissolve")
    public ApiResponse<Void> dissolveFamilyGroup(@PathVariable Long conversationId) {
        messageService.dissolveFamilyGroup(UserContext.get().userId(), conversationId);
        return ApiResponse.success();
    }

    @PostMapping("/family-groups/{conversationId}/restore")
    public ApiResponse<MessageDtos.FamilyGroupResponse> restoreFamilyGroup(@PathVariable Long conversationId) {
        return ApiResponse.success(messageService.restoreFamilyGroup(UserContext.get().userId(), conversationId));
    }

    @PostMapping("/family-groups/{conversationId}/join")
    public ApiResponse<Void> rejoinFamilyGroup(@PathVariable Long conversationId) {
        messageService.rejoinFamilyGroup(UserContext.get().userId(), conversationId);
        return ApiResponse.success();
    }

    @PostMapping("/family-groups/{conversationId}/approve-membership")
    public ApiResponse<Void> approveFamilyGroupMembership(@PathVariable Long conversationId) {
        messageService.approveFamilyGroupMembership(UserContext.get().userId(), conversationId);
        return ApiResponse.success();
    }

    @PostMapping("/family-groups/{conversationId}/decline-membership")
    public ApiResponse<Void> declineFamilyGroupMembership(@PathVariable Long conversationId) {
        messageService.declineFamilyGroupMembership(UserContext.get().userId(), conversationId);
        return ApiResponse.success();
    }

    @PostMapping("/family-groups/{conversationId}/members/{userId}/approve")
    public ApiResponse<Void> approveFamilyGroupMember(
            @PathVariable Long conversationId, @PathVariable Long userId) {
        messageService.approveFamilyGroupMember(UserContext.get().userId(), conversationId, userId);
        return ApiResponse.success();
    }

    @PostMapping("/family-groups/{conversationId}/members/{userId}/reject")
    public ApiResponse<Void> rejectFamilyGroupMember(
            @PathVariable Long conversationId, @PathVariable Long userId) {
        messageService.rejectFamilyGroupMember(UserContext.get().userId(), conversationId, userId);
        return ApiResponse.success();
    }

    @PostMapping("/family-groups/{conversationId}/leave")
    public ApiResponse<Void> leaveFamilyGroup(@PathVariable Long conversationId) {
        messageService.leaveFamilyGroup(UserContext.get().userId(), conversationId);
        return ApiResponse.success();
    }

    @PostMapping("/family-groups/{conversationId}/members/{userId}/remove")
    public ApiResponse<Void> removeFamilyGroupMember(@PathVariable Long conversationId, @PathVariable Long userId) {
        messageService.removeFamilyGroupMember(UserContext.get().userId(), conversationId, userId);
        return ApiResponse.success();
    }

    @PutMapping("/family-groups/{conversationId}/mute")
    public ApiResponse<Void> setFamilyGroupMuted(@PathVariable Long conversationId,
                                                  @RequestBody MessageDtos.SetMuteRequest request) {
        messageService.setFamilyGroupMuted(UserContext.get().userId(), conversationId,
                request.muted() != null && request.muted());
        return ApiResponse.success();
    }

    @PostMapping("/dish-shares")
    public ApiResponse<MessageDtos.ChatMessageResponse> shareDish(@Valid @RequestBody MessageDtos.ShareDishRequest request) {
        return ApiResponse.success(messageService.shareDish(UserContext.get().userId(), request));
    }

    @PostMapping("/order-shares")
    public ApiResponse<MessageDtos.ChatMessageResponse> shareOrder(
            @Valid @RequestBody MessageDtos.ShareOrderRequest request) {
        return ApiResponse.success(messageService.shareOrder(UserContext.get().userId(), request));
    }
}
