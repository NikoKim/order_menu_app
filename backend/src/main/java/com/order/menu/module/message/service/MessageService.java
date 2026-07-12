package com.order.menu.module.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.order.menu.common.constant.RoleConstants;
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
import com.order.menu.module.message.entity.UserNotification;
import com.order.menu.module.message.mapper.ChatMessageMapper;
import com.order.menu.module.message.mapper.ConversationMapper;
import com.order.menu.module.message.mapper.ConversationMemberMapper;
import com.order.menu.module.message.mapper.UserNotificationMapper;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.mapper.ShopMapper;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import com.order.menu.websocket.WsPushService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MessageService {
    private static final String DIRECT = "DIRECT";
    private static final String FAMILY_GROUP = "FAMILY_GROUP";
    private static final String ACTIVE = "ACTIVE";
    private static final String PENDING = "PENDING";

    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper conversationMemberMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;
    private final ShopMapper shopMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final DishMapper dishMapper;
    private final UserNotificationMapper userNotificationMapper;
    private final WsPushService wsPushService;

    @Transactional
    public MessageDtos.NotificationResponse createNotification(Long userId, String type, String title,
                                                                 String content, String payload) {
        UserNotification notification = new UserNotification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setPayload(payload);
        notification.setCreatedAt(LocalDateTime.now());
        userNotificationMapper.insert(notification);
        long unreadCount = unreadCount(userId);
        wsPushService.pushToUser(userId, "NOTIFICATION", "CREATED", Map.of(
                "notificationId", notification.getId(), "unreadCount", unreadCount));
        return toNotificationResponse(notification);
    }

    @Transactional
    public MessageDtos.NotificationResponse createShopNotification(Long shopId, String type, String title,
                                                                     String content, String payload) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || shop.getOwnerUserId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Shop not found");
        }
        return createNotification(shop.getOwnerUserId(), type, title, content, payload);
    }

    public MessageDtos.NotificationListResponse notifications(Long userId) {
        List<MessageDtos.NotificationResponse> notifications = userNotificationMapper.selectList(
                        new LambdaQueryWrapper<UserNotification>()
                                .eq(UserNotification::getUserId, userId)
                                .orderByDesc(UserNotification::getCreatedAt)
                                .orderByDesc(UserNotification::getId))
                .stream()
                .map(this::toNotificationResponse)
                .toList();
        return new MessageDtos.NotificationListResponse(notifications, unreadCount(userId));
    }

    public long unreadCount(Long userId) {
        Long count = userNotificationMapper.selectCount(new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .isNull(UserNotification::getReadAt));
        return count == null ? 0L : count;
    }

    @Transactional
    public void markNotificationRead(Long userId, Long notificationId) {
        UserNotification notification = userNotificationMapper.selectById(notificationId);
        if (notification == null || !userId.equals(notification.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Notification not found");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            userNotificationMapper.updateById(notification);
        }
        pushNotificationReadChanged(userId);
    }

    @Transactional
    public void markAllNotificationsRead(Long userId) {
        UserNotification values = new UserNotification();
        values.setReadAt(LocalDateTime.now());
        userNotificationMapper.update(values, new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .isNull(UserNotification::getReadAt));
        pushNotificationReadChanged(userId);
    }

    @Transactional
    public MessageDtos.FamilyGroupResponse createFamilyGroup(Long ownerUserId) {
        Shop shop = requireOwnedFamilyShop(ownerUserId);
        Long approvedCount = familyMemberMapper.selectCount(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getShopId, shop.getId())
                .eq(FamilyMember::getStatus, "APPROVED"));
        if (approvedCount == null || approvedCount < 2) {
            throw new BusinessException(ErrorCode.CONFLICT, "At least two approved family members are required");
        }
        Conversation existing = familyConversationForShop(shop.getId());
        if (existing != null) {
            return toFamilyGroupResponse(existing);
        }

        Conversation conversation = new Conversation();
        conversation.setType(FAMILY_GROUP);
        conversation.setShopId(shop.getId());
        conversation.setOwnerUserId(ownerUserId);
        conversation.setName(StringUtils.hasText(shop.getFamilyName()) ? shop.getFamilyName() : shop.getName());
        conversation.setCoverImageUrl(shop.getFamilyImageUrl());
        conversation.setStatus(ACTIVE);
        try {
            conversationMapper.insert(conversation);
        } catch (DuplicateKeyException duplicateKeyException) {
            Conversation concurrentConversation = familyConversationForShop(shop.getId());
            if (concurrentConversation != null) {
                return toFamilyGroupResponse(concurrentConversation);
            }
            throw new BusinessException(ErrorCode.CONFLICT, "A family group is being created");
        }
        insertMember(conversation.getId(), ownerUserId, "OWNER");
        familyMemberMapper.selectList(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getShopId, shop.getId())
                .eq(FamilyMember::getStatus, "APPROVED"))
                .forEach(member -> addFamilyMemberToGroup(conversation, member.getUserId()));
        pushConversationUpdated(conversation.getId());
        return toFamilyGroupResponse(conversation);
    }

    public MessageDtos.FamilyGroupResponse familyGroup(Long userId, Long conversationId) {
        Conversation conversation = requireFamilyGroupMember(userId, conversationId);
        return toFamilyGroupResponse(conversation);
    }

    public MessageDtos.FamilyGroupResponse familyGroupByShop(Long userId, Long shopId) {
        Conversation conversation = familyConversationForShop(shopId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Family group not found");
        }
        if (!userId.equals(conversation.getOwnerUserId())) {
            requireApprovedFamilyMember(userId, shopId);
        }
        return toFamilyGroupResponse(conversation);
    }

    @Transactional
    public MessageDtos.FamilyGroupResponse updateFamilyGroup(Long ownerUserId, Long conversationId,
                                                              MessageDtos.FamilyGroupProfileRequest request) {
        Conversation conversation = requireOwnedFamilyGroup(ownerUserId, conversationId);
        if (StringUtils.hasText(request.name())) {
            conversation.setName(request.name().trim());
        }
        if (request.coverImageUrl() != null) {
            conversation.setCoverImageUrl(trimToNull(request.coverImageUrl()));
        }
        if (request.announcement() != null) {
            conversation.setAnnouncement(trimToNull(request.announcement()));
        }
        conversationMapper.updateById(conversation);
        pushConversationUpdated(conversationId);
        return toFamilyGroupResponse(conversation);
    }

    @Transactional
    public void dissolveFamilyGroup(Long ownerUserId, Long conversationId) {
        Conversation conversation = requireOwnedFamilyGroup(ownerUserId, conversationId);
        conversation.setStatus("DISSOLVED");
        conversationMapper.updateById(conversation);
        pushConversationUpdated(conversationId);
    }

    @Transactional
    public MessageDtos.FamilyGroupResponse restoreFamilyGroup(Long ownerUserId, Long conversationId) {
        Conversation conversation = requireOwnedFamilyGroup(ownerUserId, conversationId);
        conversation.setStatus(ACTIVE);
        conversationMapper.updateById(conversation);
        ConversationMember owner = member(conversationId, ownerUserId);
        if (owner != null && !ACTIVE.equals(owner.getMemberStatus())) {
            owner.setMemberStatus(ACTIVE);
            owner.setLeftAt(null);
            owner.setJoinedAt(LocalDateTime.now());
            conversationMemberMapper.updateById(owner);
        }
        pushConversationUpdated(conversationId);
        return toFamilyGroupResponse(conversation);
    }

    @Transactional
    public void approveFamilyGroupMembership(Long userId, Long conversationId) {
        Conversation conversation = requireFamilyGroupConversation(conversationId);
        if (!ACTIVE.equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This family group is no longer active");
        }
        requireApprovedFamilyMember(userId, conversation.getShopId());
        ConversationMember member = member(conversationId, userId);
        if (member == null || !PENDING.equals(member.getMemberStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "No pending family group invitation");
        }
        member.setMemberStatus(ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        member.setLeftAt(null);
        conversationMemberMapper.updateById(member);
        pushMemberUpdated(conversationId, userId);
    }

    @Transactional
    public void rejoinFamilyGroup(Long userId, Long conversationId) {
        Conversation conversation = requireFamilyGroupConversation(conversationId);
        if (!ACTIVE.equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This family group is no longer active");
        }
        requireApprovedFamilyMember(userId, conversation.getShopId());
        ConversationMember member = member(conversationId, userId);
        if (member == null) {
            insertMember(conversationId, userId, "MEMBER");
            pushMemberUpdated(conversationId, userId);
            return;
        }
        if ("OWNER".equals(member.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "The group owner cannot rejoin");
        }
        member.setMemberStatus(ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        member.setLeftAt(null);
        conversationMemberMapper.updateById(member);
        pushMemberUpdated(conversationId, userId);
    }

    @Transactional
    public void leaveFamilyGroup(Long userId, Long conversationId) {
        Conversation conversation = requireFamilyGroupMember(userId, conversationId);
        ConversationMember member = member(conversation.getId(), userId);
        if (member == null || "OWNER".equals(member.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "The group owner cannot leave the family group");
        }
        markMemberLeft(member, "LEFT");
        pushMemberUpdated(conversationId, userId);
    }

    @Transactional
    public void removeFamilyGroupMember(Long ownerUserId, Long conversationId, Long memberUserId) {
        Conversation conversation = requireOwnedFamilyGroup(ownerUserId, conversationId);
        ConversationMember member = member(conversationId, memberUserId);
        if (member == null || !ACTIVE.equals(member.getMemberStatus()) || "OWNER".equals(member.getRole())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Family group member not found");
        }
        requireApprovedFamilyMember(memberUserId, conversation.getShopId());
        markMemberLeft(member, "REMOVED");
        pushMemberUpdated(conversationId, memberUserId);
    }

    @Transactional
    public void setFamilyGroupMuted(Long userId, Long conversationId, boolean muted) {
        requireFamilyGroupMember(userId, conversationId);
        ConversationMember member = member(conversationId, userId);
        member.setMuted(muted);
        conversationMemberMapper.updateById(member);
        pushMemberUpdated(conversationId, userId);
    }

    @Transactional
    public MessageDtos.ChatMessageResponse shareDish(Long userId, MessageDtos.ShareDishRequest request) {
        if (request.conversationId() == null || request.dishId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Conversation and dish are required");
        }
        Conversation conversation = requireConversationMember(userId, request.conversationId());
        if (!ACTIVE.equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This conversation is no longer active");
        }
        if (DIRECT.equals(conversation.getType())) {
            assertDirectRecipientAllowsPrivateChat(conversation, userId);
            assertDirectMessageAllowed(conversation, userId);
        }
        Dish dish = dishMapper.selectById(request.dishId());
        if (dish == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Dish not found");
        }
        Shop shop = shopMapper.selectById(dish.getShopId());
        if (shop == null || shop.getStatus() == null || shop.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Shop not found");
        }
        if ("FAMILY".equals(shop.getVisibilityType())) {
            assertFamilyDishShareAllowed(userId, conversation, shop.getId());
        }
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setSenderUserId(userId);
        message.setType("DISH_SHARE");
        message.setDishId(dish.getId());
        message.setShopId(shop.getId());
        message.setDishName(dish.getName());
        message.setDishImageUrl(dish.getImageUrl());
        message.setDishPrice(dish.getPrice());
        message.setDishDescription(dish.getDescription());
        message.setDishSalesCount(dish.getSalesCount());
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
        conversation.setLastMessageAt(message.getCreatedAt());
        conversationMapper.updateById(conversation);
        pushChatMessageCreated(conversation.getId(), message.getId());
        pushConversationUpdated(conversation.getId());
        return toMessageResponse(message);
    }

    /** Keeps an existing family group synchronized when a family application is approved. */
    @Transactional
    public void onFamilyMemberApproved(FamilyMember familyMember) {
        Conversation conversation = familyConversationForShop(familyMember.getShopId());
        if (conversation == null || !ACTIVE.equals(conversation.getStatus())) {
            return;
        }
        ConversationMember existingMember = member(conversation.getId(), familyMember.getUserId());
        if (existingMember == null) {
            addFamilyMemberToGroup(conversation, familyMember.getUserId());
            pushMemberUpdated(conversation.getId(), familyMember.getUserId());
        } else if (!"OWNER".equals(existingMember.getRole()) && !ACTIVE.equals(existingMember.getMemberStatus())) {
            User user = requireEnabledUser(familyMember.getUserId());
            existingMember.setMemberStatus(Boolean.FALSE.equals(user.getAutoJoinFamilyChat()) ? PENDING : ACTIVE);
            existingMember.setLeftAt(null);
            existingMember.setJoinedAt(ACTIVE.equals(existingMember.getMemberStatus()) ? LocalDateTime.now() : null);
            conversationMemberMapper.updateById(existingMember);
            pushMemberUpdated(conversation.getId(), familyMember.getUserId());
        }
    }

    /** Removes chat access whenever a customer leaves the family, without deleting family history. */
    @Transactional
    public void onFamilyMemberLeft(Long shopId, Long userId) {
        Conversation conversation = familyConversationForShop(shopId);
        if (conversation == null) {
            return;
        }
        ConversationMember member = member(conversation.getId(), userId);
        if (member != null && !"OWNER".equals(member.getRole())) {
            markMemberLeft(member, "LEFT");
        }
    }

    @Transactional
    public MessageDtos.ConversationResponse createDirectWithUser(Long requesterUserId, Long recipientUserId) {
        requireCustomerUser(requesterUserId);
        requireCustomerUser(recipientUserId);
        return resolveDirectConversation(requesterUserId, recipientUserId);
    }

    @Transactional
    public MessageDtos.ConversationResponse createDirectWithShop(Long requesterUserId, Long shopId) {
        requireEnabledUser(requesterUserId);
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || shop.getStatus() == null || shop.getStatus() != 1 || shop.getOwnerUserId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Shop not found");
        }
        return resolveDirectConversation(requesterUserId, shop.getOwnerUserId());
    }

    public List<MessageDtos.CustomerSearchResponse> searchCustomers(Long requesterUserId, String nickname) {
        requireCustomerUser(requesterUserId);
        if (!StringUtils.hasText(nickname)) {
            return List.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .eq(User::getRole, RoleConstants.CUSTOMER)
                        .eq(User::getStatus, 1)
                        .like(User::getNickname, nickname.trim())
                        .orderByAsc(User::getNickname)
                        .last("limit 20"))
                .stream()
                .filter(user -> !user.getId().equals(requesterUserId))
                .map(user -> new MessageDtos.CustomerSearchResponse(user.getId(), user.getNickname(), user.getAvatarUrl()))
                .toList();
    }

    public List<MessageDtos.ConversationResponse> conversations(Long userId) {
        List<ConversationMember> memberships = activeMembers(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<Long> conversationIds = memberships.stream().map(ConversationMember::getConversationId).toList();
        return conversationMapper.selectBatchIds(conversationIds).stream()
                .filter(conversation -> ACTIVE.equals(conversation.getStatus())
                        || (FAMILY_GROUP.equals(conversation.getType()) && "DISSOLVED".equals(conversation.getStatus())))
                .sorted(Comparator.comparing(Conversation::getLastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(conversation -> toConversationResponse(conversation, userId))
                .toList();
    }

    public MessageDtos.ConversationMessagesResponse messages(Long userId, Long conversationId) {
        Conversation conversation = requireConversationMember(userId, conversationId);
        List<ChatMessage> entities = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .orderByAsc(ChatMessage::getCreatedAt)
                        .orderByAsc(ChatMessage::getId));
        markConversationRead(userId, conversationId, entities);
        List<MessageDtos.ChatMessageResponse> messages = entities.stream()
                .map(this::toMessageResponse)
                .toList();
        return new MessageDtos.ConversationMessagesResponse(toConversationResponse(conversation, userId), messages);
    }

    @Transactional
    public MessageDtos.ChatMessageResponse sendMessage(Long userId, Long conversationId, MessageDtos.SendMessageRequest request) {
        Conversation conversation = requireConversationMember(userId, conversationId);
        if (!ACTIVE.equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This conversation is no longer active");
        }
        String content = request.content().trim();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Message content must not be blank");
        }
        if (DIRECT.equals(conversation.getType())) {
            assertDirectRecipientAllowsPrivateChat(conversation, userId);
            assertDirectMessageAllowed(conversation, userId);
        }

        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setSenderUserId(userId);
        message.setType("TEXT");
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);

        conversation.setLastMessageAt(message.getCreatedAt());
        conversationMapper.updateById(conversation);
        pushChatMessageCreated(conversation.getId(), message.getId());
        pushConversationUpdated(conversation.getId());
        return toMessageResponse(message);
    }

    private MessageDtos.ConversationResponse resolveDirectConversation(Long requesterUserId, Long recipientUserId) {
        if (requesterUserId == null || requesterUserId.equals(recipientUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "A direct conversation requires another user");
        }
        requireEnabledUser(requesterUserId);
        User recipient = requireEnabledUser(recipientUserId);
        if (Boolean.FALSE.equals(recipient.getAllowPrivateChat())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "The recipient does not allow private chat");
        }

        String directKey = directKey(requesterUserId, recipientUserId);
        Conversation existing = findDirectConversation(directKey);
        if (existing != null) {
            return toConversationResponse(existing, requesterUserId);
        }

        Conversation conversation = new Conversation();
        conversation.setType(DIRECT);
        conversation.setDirectKey(directKey);
        conversation.setOwnerUserId(requesterUserId);
        conversation.setStatus(ACTIVE);
        try {
            conversationMapper.insert(conversation);
        } catch (DuplicateKeyException duplicateKeyException) {
            Conversation concurrentConversation = findDirectConversation(directKey);
            if (concurrentConversation != null) {
                return toConversationResponse(concurrentConversation, requesterUserId);
            }
            throw new BusinessException(ErrorCode.CONFLICT, "A direct conversation is being created");
        }
        insertMember(conversation.getId(), requesterUserId, "OWNER");
        insertMember(conversation.getId(), recipientUserId, "MEMBER");
        pushConversationUpdated(conversation.getId());
        return toConversationResponse(conversation, requesterUserId);
    }

    private void insertMember(Long conversationId, Long userId, String role) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(role);
        member.setMemberStatus(ACTIVE);
        member.setMuted(false);
        member.setJoinedAt(LocalDateTime.now());
        conversationMemberMapper.insert(member);
    }

    private void addFamilyMemberToGroup(Conversation conversation, Long userId) {
        if (conversation.getOwnerUserId().equals(userId)) {
            return;
        }
        User user = requireEnabledUser(userId);
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversation.getId());
        member.setUserId(userId);
        member.setRole("MEMBER");
        member.setMemberStatus(Boolean.FALSE.equals(user.getAutoJoinFamilyChat()) ? PENDING : ACTIVE);
        member.setMuted(false);
        if (ACTIVE.equals(member.getMemberStatus())) {
            member.setJoinedAt(LocalDateTime.now());
        }
        conversationMemberMapper.insert(member);
    }

    private Shop requireOwnedFamilyShop(Long ownerUserId) {
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getOwnerUserId, ownerUserId)
                .eq(Shop::getStatus, 1)
                .last("limit 1"));
        if (shop == null || !"FAMILY".equals(shop.getVisibilityType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Current shop is not a family shop");
        }
        return shop;
    }

    private Conversation familyConversationForShop(Long shopId) {
        return conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getShopId, shopId)
                .eq(Conversation::getType, FAMILY_GROUP)
                .last("limit 1"));
    }

    private Conversation requireFamilyGroupConversation(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !FAMILY_GROUP.equals(conversation.getType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Family group not found");
        }
        return conversation;
    }

    private Conversation requireOwnedFamilyGroup(Long ownerUserId, Long conversationId) {
        Conversation conversation = requireFamilyGroupConversation(conversationId);
        if (!ownerUserId.equals(conversation.getOwnerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the family group owner can manage this group");
        }
        return conversation;
    }

    private Conversation requireFamilyGroupMember(Long userId, Long conversationId) {
        Conversation conversation = requireFamilyGroupConversation(conversationId);
        ConversationMember member = member(conversationId, userId);
        if (member == null || !ACTIVE.equals(member.getMemberStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You are not an active family group member");
        }
        return conversation;
    }

    private ConversationMember member(Long conversationId, Long userId) {
        return conversationMemberMapper.selectOne(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getUserId, userId)
                .last("limit 1"));
    }

    private void requireApprovedFamilyMember(Long userId, Long shopId) {
        Long memberCount = familyMemberMapper.selectCount(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getUserId, userId)
                .eq(FamilyMember::getShopId, shopId)
                .eq(FamilyMember::getStatus, "APPROVED"));
        if (memberCount == null || memberCount == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You are not an approved family member");
        }
    }

    private void markMemberLeft(ConversationMember member, String status) {
        member.setMemberStatus(status);
        member.setLeftAt(LocalDateTime.now());
        conversationMemberMapper.updateById(member);
    }

    private void assertFamilyDishShareAllowed(Long senderUserId, Conversation conversation, Long shopId) {
        if (FAMILY_GROUP.equals(conversation.getType())) {
            if (!shopId.equals(conversation.getShopId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Family dishes can only be shared to their family group");
            }
            return;
        }
        if (!DIRECT.equals(conversation.getType())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Family dishes cannot be shared to this conversation");
        }
        Long recipientUserId = otherDirectUserId(conversation.getDirectKey(), senderUserId);
        if (recipientUserId == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Direct conversation participants are invalid");
        }
        requireApprovedFamilyMember(recipientUserId, shopId);
    }

    private MessageDtos.FamilyGroupResponse toFamilyGroupResponse(Conversation conversation) {
        List<MessageDtos.FamilyGroupMemberResponse> members = conversationMemberMapper.selectList(
                        new LambdaQueryWrapper<ConversationMember>()
                                .eq(ConversationMember::getConversationId, conversation.getId())
                                .ne(ConversationMember::getMemberStatus, "LEFT")
                                .ne(ConversationMember::getMemberStatus, "REMOVED"))
                .stream()
                .map(member -> {
                    User user = userMapper.selectById(member.getUserId());
                    return new MessageDtos.FamilyGroupMemberResponse(member.getUserId(),
                            user == null ? null : user.getNickname(), user == null ? null : user.getAvatarUrl(),
                            member.getRole(), member.getMemberStatus(), member.getMuted());
                })
                .toList();
        return new MessageDtos.FamilyGroupResponse(conversation.getId(), conversation.getShopId(), conversation.getName(),
                conversation.getCoverImageUrl(), conversation.getAnnouncement(), conversation.getStatus(), members);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void assertDirectMessageAllowed(Conversation conversation, Long userId) {
        List<ChatMessage> history = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .orderByAsc(ChatMessage::getCreatedAt)
                .orderByAsc(ChatMessage::getId));
        boolean recipientReplied = history.stream()
                .anyMatch(message -> !userId.equals(message.getSenderUserId()));
        boolean initiatorAlreadyMessaged = history.stream()
                .anyMatch(message -> userId.equals(message.getSenderUserId()));
        if (initiatorAlreadyMessaged && !recipientReplied) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Wait for the recipient to reply before sending another message");
        }
    }

    private void assertDirectRecipientAllowsPrivateChat(Conversation conversation, Long userId) {
        Long recipientUserId = otherDirectUserId(conversation.getDirectKey(), userId);
        if (recipientUserId == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Direct conversation participants are invalid");
        }
        User recipient = requireEnabledUser(recipientUserId);
        if (Boolean.FALSE.equals(recipient.getAllowPrivateChat())) {
            boolean recipientStartedContact = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getConversationId, conversation.getId())
                            .eq(ChatMessage::getSenderUserId, recipientUserId)
                            .last("limit 1"))
                    .stream()
                    .findAny()
                    .isPresent();
            if (!recipientStartedContact) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "The recipient does not allow private chat");
            }
        }
    }

    private Conversation requireConversationMember(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Conversation not found");
        }
        boolean member = activeMembers(userId).stream()
                .anyMatch(item -> conversationId.equals(item.getConversationId()));
        if (!member) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You are not a member of this conversation");
        }
        return conversation;
    }

    private List<ConversationMember> activeMembers(Long userId) {
        return conversationMemberMapper.selectList(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getUserId, userId)
                .eq(ConversationMember::getMemberStatus, ACTIVE));
    }

    private Conversation findDirectConversation(String directKey) {
        return conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getDirectKey, directKey));
    }

    private User requireCustomerUser(Long userId) {
        User user = requireEnabledUser(userId);
        if (!RoleConstants.CUSTOMER.equals(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only customers can start private chats with customers");
        }
        return user;
    }

    private User requireEnabledUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "User account is disabled");
        }
        return user;
    }

    private MessageDtos.ConversationResponse toConversationResponse(Conversation conversation, Long viewerUserId) {
        ConversationMember viewer = member(conversation.getId(), viewerUserId);
        List<ChatMessage> conversationMessages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .orderByDesc(ChatMessage::getCreatedAt)
                .orderByDesc(ChatMessage::getId));
        ChatMessage latest = conversationMessages.isEmpty() ? null : conversationMessages.get(0);
        long unread = conversationMessages.stream()
                .filter(message -> !viewerUserId.equals(message.getSenderUserId()))
                .filter(message -> viewer == null || viewer.getLastReadMessageId() == null
                        || (message.getId() != null && message.getId() > viewer.getLastReadMessageId()))
                .count();
        String lastContent = latest == null ? null : ("DISH_SHARE".equals(latest.getType())
                ? "[Dish] " + latest.getDishName() : latest.getContent());
        boolean muted = viewer != null && Boolean.TRUE.equals(viewer.getMuted());
        if (!DIRECT.equals(conversation.getType())) {
            return new MessageDtos.ConversationResponse(conversation.getId(), conversation.getType(), conversation.getName(),
                    conversation.getCoverImageUrl(), conversation.getStatus(), conversation.getLastMessageAt(), null, null, null,
                    lastContent, unread, muted);
        }
        Long directUserId = otherDirectUserId(conversation.getDirectKey(), viewerUserId);
        User directUser = directUserId == null ? null : userMapper.selectById(directUserId);
        String displayName = directUser == null ? null : directUser.getNickname();
        String displayAvatar = directUser == null ? null : directUser.getAvatarUrl();
        if (directUser != null && RoleConstants.SHOP.equals(directUser.getRole())) {
            Shop directShop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                    .eq(Shop::getOwnerUserId, directUserId)
                    .last("limit 1"));
            if (directShop != null) {
                displayName = directShop.getName();
                if (StringUtils.hasText(directShop.getCoverImageUrl())) {
                    displayAvatar = directShop.getCoverImageUrl();
                }
            }
        }
        return new MessageDtos.ConversationResponse(conversation.getId(), conversation.getType(), null, null,
                conversation.getStatus(), conversation.getLastMessageAt(), directUserId,
                displayName, displayAvatar, lastContent, unread, muted);
    }

    private void markConversationRead(Long userId, Long conversationId, List<ChatMessage> messages) {
        if (messages.isEmpty()) return;
        ConversationMember viewer = member(conversationId, userId);
        ChatMessage latest = messages.get(messages.size() - 1);
        if (viewer != null && latest.getId() != null
                && (viewer.getLastReadMessageId() == null || latest.getId() > viewer.getLastReadMessageId())) {
            viewer.setLastReadMessageId(latest.getId());
            conversationMemberMapper.updateById(viewer);
            pushConversationUpdated(conversationId);
        }
    }

    private MessageDtos.ChatMessageResponse toMessageResponse(ChatMessage message) {
        User sender = userMapper.selectById(message.getSenderUserId());
        String senderName = sender == null ? null : sender.getNickname();
        String senderAvatar = sender == null ? null : sender.getAvatarUrl();
        if (sender != null && RoleConstants.SHOP.equals(sender.getRole())) {
            Shop senderShop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                    .eq(Shop::getOwnerUserId, sender.getId()).last("limit 1"));
            if (senderShop != null) {
                senderName = senderShop.getName();
                if (StringUtils.hasText(senderShop.getCoverImageUrl())) senderAvatar = senderShop.getCoverImageUrl();
            }
        }
        return new MessageDtos.ChatMessageResponse(message.getId(), message.getConversationId(), message.getSenderUserId(),
                senderName, senderAvatar, message.getType(), message.getContent(), message.getDishId(), message.getShopId(), message.getDishName(),
                message.getDishImageUrl(), message.getDishPrice(), message.getDishDescription(), message.getDishSalesCount(), message.getCreatedAt());
    }

    private MessageDtos.NotificationResponse toNotificationResponse(UserNotification notification) {
        return new MessageDtos.NotificationResponse(notification.getId(), notification.getUserId(), notification.getType(),
                notification.getTitle(), notification.getContent(), notification.getPayload(), notification.getReadAt(),
                notification.getCreatedAt());
    }

    private void pushNotificationReadChanged(Long userId) {
        wsPushService.pushToUser(userId, "NOTIFICATION", "READ_CHANGED", Map.of("unreadCount", unreadCount(userId)));
    }

    private void pushChatMessageCreated(Long conversationId, Long messageId) {
        afterCommit(() -> pushChatMessageCreatedNow(conversationId, messageId));
    }

    private void pushChatMessageCreatedNow(Long conversationId, Long messageId) {
        conversationMemberMapper.selectList(new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getMemberStatus, ACTIVE))
                .forEach(member -> wsPushService.pushToUser(member.getUserId(), "CHAT", "MESSAGE_CREATED", Map.of(
                        "conversationId", conversationId, "messageId", messageId)));
    }

    private void pushConversationUpdated(Long conversationId) {
        afterCommit(() -> pushConversationUpdatedNow(conversationId));
    }

    private void pushConversationUpdatedNow(Long conversationId) {
        conversationMemberMapper.selectList(new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getMemberStatus, ACTIVE))
                .forEach(member -> wsPushService.pushToUser(member.getUserId(), "CHAT", "CONVERSATION_UPDATED", Map.of(
                        "conversationId", conversationId)));
    }

    private void pushMemberUpdated(Long conversationId, Long memberUserId) {
        afterCommit(() -> pushMemberUpdatedNow(conversationId, memberUserId));
    }

    private void pushMemberUpdatedNow(Long conversationId, Long memberUserId) {
        conversationMemberMapper.selectList(new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId))
                .forEach(member -> wsPushService.pushToUser(member.getUserId(), "CHAT", "MEMBER_UPDATED", Map.of(
                        "conversationId", conversationId, "memberUserId", memberUserId)));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    private String directKey(Long firstUserId, Long secondUserId) {
        return firstUserId < secondUserId ? firstUserId + ":" + secondUserId : secondUserId + ":" + firstUserId;
    }

    private Long otherDirectUserId(String directKey, Long userId) {
        if (!StringUtils.hasText(directKey)) {
            return null;
        }
        String[] users = directKey.split(":", -1);
        if (users.length != 2) {
            return null;
        }
        try {
            Long first = Long.valueOf(users[0]);
            Long second = Long.valueOf(users[1]);
            return userId.equals(first) ? second : first;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
