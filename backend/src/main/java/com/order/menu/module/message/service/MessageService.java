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
import com.order.menu.module.message.entity.ChatBlock;
import com.order.menu.module.message.entity.ChatMessageDeletion;
import com.order.menu.module.message.entity.Conversation;
import com.order.menu.module.message.entity.ConversationMember;
import com.order.menu.module.message.entity.UserNotification;
import com.order.menu.module.message.mapper.ChatMessageMapper;
import com.order.menu.module.message.mapper.ChatBlockMapper;
import com.order.menu.module.message.mapper.ChatMessageDeletionMapper;
import com.order.menu.module.message.mapper.ConversationMapper;
import com.order.menu.module.message.mapper.ConversationMemberMapper;
import com.order.menu.module.message.mapper.UserNotificationMapper;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.mapper.ShopMapper;
import com.order.menu.module.order.entity.Order;
import com.order.menu.module.order.entity.OrderItem;
import com.order.menu.module.order.mapper.OrderMapper;
import com.order.menu.module.order.mapper.OrderItemMapper;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import com.order.menu.websocket.WsPushService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class MessageService {
    private static final String DIRECT = "DIRECT";
    private static final String FAMILY_GROUP = "FAMILY_GROUP";
    private static final String ACTIVE = "ACTIVE";
    private static final String PENDING = "PENDING";
    private static final String DELIVERED = "DELIVERED";
    private static final String FAILED_BLOCKED = "FAILED_BLOCKED";
    private static final String BLOCKED_MESSAGE = "对方已将您拉黑，无法发送消息";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper conversationMemberMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageDeletionMapper chatMessageDeletionMapper;
    private final ChatBlockMapper chatBlockMapper;
    private final UserMapper userMapper;
    private final ShopMapper shopMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final DishMapper dishMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserNotificationMapper userNotificationMapper;
    private final WsPushService wsPushService;

    public MessageService(ConversationMapper conversationMapper,
                          ConversationMemberMapper conversationMemberMapper,
                          ChatMessageMapper chatMessageMapper,
                          UserMapper userMapper,
                          ShopMapper shopMapper,
                          FamilyMemberMapper familyMemberMapper,
                          DishMapper dishMapper,
                          UserNotificationMapper userNotificationMapper,
                          WsPushService wsPushService) {
        this(conversationMapper, conversationMemberMapper, chatMessageMapper, null, null, userMapper, shopMapper,
                familyMemberMapper, dishMapper, null, null, userNotificationMapper, wsPushService);
    }

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
        Map<String, Object> pushData = new java.util.HashMap<>();
        if (notification.getId() != null) pushData.put("notificationId", notification.getId());
        pushData.put("unreadCount", unreadCount);
        wsPushService.pushToUser(userId, "NOTIFICATION", "CREATED", pushData);
        return toNotificationResponse(notification);
    }

    @Transactional
    public MessageDtos.NotificationResponse createShopNotification(Long shopId, String type, String title,
                                                                     String content, String payload) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || shop.getOwnerUserId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "店铺不存在");
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
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在");
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
            throw new BusinessException(ErrorCode.CONFLICT, "至少需要两名已通过的家庭成员");
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
            throw new BusinessException(ErrorCode.CONFLICT, "家庭群聊正在创建，请稍后重试");
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
            throw new BusinessException(ErrorCode.NOT_FOUND, "家庭群聊不存在");
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
        if (request.joinApprovalRequired() != null) {
            conversation.setJoinApprovalRequired(request.joinApprovalRequired());
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
            throw new BusinessException(ErrorCode.FORBIDDEN, "该家庭群聊已不可用");
        }
        requireApprovedFamilyMember(userId, conversation.getShopId());
        ConversationMember member = member(conversationId, userId);
        if (member == null || !PENDING.equals(member.getMemberStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前没有待处理的群聊邀请");
        }
        member.setMemberStatus(ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        member.setLeftAt(null);
        conversationMemberMapper.updateById(member);
        insertSystemMessage(conversation, userId, displayName(userId) + "加入了群聊");
        pushMemberUpdated(conversationId, userId);
    }

    @Transactional
    public void declineFamilyGroupMembership(Long userId, Long conversationId) {
        requireFamilyGroupConversation(conversationId);
        ConversationMember member = member(conversationId, userId);
        if (member == null || !PENDING.equals(member.getMemberStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前没有待处理的群聊邀请");
        }
        markMemberLeft(member, "LEFT");
        pushMemberUpdated(conversationId, userId);
    }

    @Transactional
    public void approveFamilyGroupMember(Long ownerUserId, Long conversationId, Long memberUserId) {
        Conversation conversation = requireOwnedFamilyGroup(ownerUserId, conversationId);
        ConversationMember member = member(conversationId, memberUserId);
        if (member == null || !PENDING.equals(member.getMemberStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该成员不在待审核状态");
        }
        member.setMemberStatus(ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        member.setLeftAt(null);
        conversationMemberMapper.updateById(member);
        insertSystemMessage(conversation, memberUserId, displayName(memberUserId) + "加入了群聊");
        createNotification(memberUserId, "FAMILY_GROUP_APPROVED", "群聊申请已通过",
                "商家已同意你加入“" + conversation.getName() + "”", familyGroupPayload(conversationId));
        pushMemberUpdated(conversationId, memberUserId);
    }

    @Transactional
    public void rejectFamilyGroupMember(Long ownerUserId, Long conversationId, Long memberUserId) {
        Conversation conversation = requireOwnedFamilyGroup(ownerUserId, conversationId);
        ConversationMember member = member(conversationId, memberUserId);
        if (member == null || !PENDING.equals(member.getMemberStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该成员不在待审核状态");
        }
        markMemberLeft(member, "LEFT");
        createNotification(memberUserId, "FAMILY_GROUP_REJECTED", "群聊申请未通过",
                "商家暂未同意你加入“" + conversation.getName() + "”", familyGroupPayload(conversationId));
        pushMemberUpdated(conversationId, memberUserId);
    }

    @Transactional
    public void rejoinFamilyGroup(Long userId, Long conversationId) {
        Conversation conversation = requireFamilyGroupConversation(conversationId);
        if (!ACTIVE.equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该家庭群聊已不可用");
        }
        requireApprovedFamilyMember(userId, conversation.getShopId());
        ConversationMember member = member(conversationId, userId);
        boolean needsApproval = Boolean.TRUE.equals(conversation.getJoinApprovalRequired());
        if (member == null) {
            insertMember(conversationId, userId, "MEMBER", needsApproval ? PENDING : ACTIVE);
            if (needsApproval) notifyOwnerOfJoinRequest(conversation, userId);
            else insertSystemMessage(conversation, userId, displayName(userId) + "加入了群聊");
            pushMemberUpdated(conversationId, userId);
            return;
        }
        if ("OWNER".equals(member.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "群主无需重新加入群聊");
        }
        member.setMemberStatus(needsApproval ? PENDING : ACTIVE);
        member.setJoinedAt(needsApproval ? null : LocalDateTime.now());
        member.setLeftAt(null);
        conversationMemberMapper.updateById(member);
        if (needsApproval) notifyOwnerOfJoinRequest(conversation, userId);
        else insertSystemMessage(conversation, userId, displayName(userId) + "加入了群聊");
        pushMemberUpdated(conversationId, userId);
    }

    @Transactional
    public void leaveFamilyGroup(Long userId, Long conversationId) {
        Conversation conversation = requireFamilyGroupMember(userId, conversationId);
        ConversationMember member = member(conversation.getId(), userId);
        if (member == null || "OWNER".equals(member.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "群主不能退出家庭群聊");
        }
        markMemberLeft(member, "LEFT");
        insertSystemMessage(conversation, userId, displayName(userId) + "退出了群聊");
        pushMemberUpdated(conversationId, userId);
    }

    @Transactional
    public void removeFamilyGroupMember(Long ownerUserId, Long conversationId, Long memberUserId) {
        Conversation conversation = requireOwnedFamilyGroup(ownerUserId, conversationId);
        ConversationMember member = member(conversationId, memberUserId);
        if (member == null || !ACTIVE.equals(member.getMemberStatus()) || "OWNER".equals(member.getRole())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "群成员不存在");
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
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择会话和菜品");
        }
        Conversation conversation = requireConversationMember(userId, request.conversationId());
        if (!ACTIVE.equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该会话已不可用");
        }
        boolean deliveryFailed = false;
        if (DIRECT.equals(conversation.getType())) {
            Long recipientUserId = requireDirectRecipient(conversation, userId);
            if (hasBlocked(userId, recipientUserId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "您已拉黑对方，请先取消拉黑后再分享菜品");
            }
            deliveryFailed = hasBlocked(recipientUserId, userId);
        }
        if (DIRECT.equals(conversation.getType()) && !deliveryFailed) {
            assertDirectRecipientAllowsPrivateChat(conversation, userId);
            assertDirectMessageAllowed(conversation, userId);
        }
        Dish dish = dishMapper.selectById(request.dishId());
        if (dish == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "菜品不存在");
        }
        Shop shop = shopMapper.selectById(dish.getShopId());
        if (shop == null || shop.getStatus() == null || shop.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "店铺不存在");
        }
        if (DIRECT.equals(conversation.getType())) {
            Long recipientUserId = otherDirectUserId(conversation.getDirectKey(), userId);
            User recipient = recipientUserId == null ? null : userMapper.selectById(recipientUserId);
            if (recipient != null && RoleConstants.SHOP.equals(recipient.getRole())
                    && !recipientUserId.equals(shop.getOwnerUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "该店铺的菜品只能分享给对应商家");
            }
        }
        if ("FAMILY".equals(shop.getVisibilityType())) {
            assertFamilyDishShareAllowed(userId, conversation, shop.getId());
        }
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setSenderUserId(userId);
        message.setType("DISH_SHARE");
        message.setDeliveryStatus(deliveryFailed ? FAILED_BLOCKED : DELIVERED);
        message.setDeliveryFailureReason(deliveryFailed ? BLOCKED_MESSAGE : null);
        message.setDishId(dish.getId());
        message.setShopId(shop.getId());
        message.setShopName(shop.getName());
        message.setDishName(dish.getName());
        message.setDishImageUrl(dish.getImageUrl());
        message.setDishPrice(dish.getPrice());
        message.setDishDescription(dish.getDescription());
        message.setDishSalesCount(dish.getSalesCount());
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
        if (deliveryFailed) {
            pushChatMessageCreatedToUser(userId, conversation.getId(), message.getId());
            pushConversationUpdatedToUser(userId, conversation.getId());
        } else {
            conversation.setLastMessageAt(message.getCreatedAt());
            conversationMapper.updateById(conversation);
            pushChatMessageCreated(conversation.getId(), message.getId());
            pushConversationUpdated(conversation.getId());
        }
        return toMessageResponse(message);
    }

    @Transactional
    public MessageDtos.ChatMessageResponse shareOrder(Long userId, MessageDtos.ShareOrderRequest request) {
        if (request.conversationId() == null || request.orderId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择会话和订单");
        }
        Conversation conversation = requireConversationMember(userId, request.conversationId());
        if (!ACTIVE.equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该会话已不可用");
        }
        Order order = orderMapper.selectById(request.orderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!userId.equals(order.getCustomerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能分享自己的订单");
        }
        boolean deliveryFailed = false;
        if (DIRECT.equals(conversation.getType())) {
            Long recipientUserId = requireDirectRecipient(conversation, userId);
            if (hasBlocked(userId, recipientUserId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "您已拉黑对方，请先取消拉黑后再分享订单");
            }
            deliveryFailed = hasBlocked(recipientUserId, userId);
            if (!deliveryFailed) {
                assertDirectRecipientAllowsPrivateChat(conversation, userId);
                assertDirectMessageAllowed(conversation, userId);
            }
        }
        Shop shop = shopMapper.selectById(order.getShopId());
        List<MessageDtos.OrderShareItemResponse> items = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()).orderByAsc(OrderItem::getId))
                .stream()
                .map(item -> new MessageDtos.OrderShareItemResponse(item.getDishName(), item.getDishImageUrl(),
                        item.getUnitPrice(), item.getQuantity(), item.getSubtotalAmount(), item.getSelectedOptions()))
                .toList();

        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setSenderUserId(userId);
        message.setType("ORDER_SHARE");
        message.setDeliveryStatus(deliveryFailed ? FAILED_BLOCKED : DELIVERED);
        message.setDeliveryFailureReason(deliveryFailed ? BLOCKED_MESSAGE : null);
        message.setOrderId(order.getId());
        message.setOrderNo(order.getOrderNo());
        message.setOrderCreatedAt(order.getCreatedAt());
        message.setOrderStatus(order.getStatus());
        message.setOrderShopName(shop == null ? null : shop.getName());
        message.setOrderItemsJson(writeOrderItems(items));
        message.setOrderTotalAmount(order.getTotalAmount());
        message.setOrderRemark(order.getRemark());
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
        if (deliveryFailed) {
            pushChatMessageCreatedToUser(userId, conversation.getId(), message.getId());
            pushConversationUpdatedToUser(userId, conversation.getId());
        } else {
            conversation.setLastMessageAt(message.getCreatedAt());
            conversationMapper.updateById(conversation);
            pushChatMessageCreated(conversation.getId(), message.getId());
            pushConversationUpdated(conversation.getId());
        }
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
            throw new BusinessException(ErrorCode.NOT_FOUND, "店铺不存在");
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
                .map(conversation -> toConversationResponse(conversation, userId))
                .sorted(Comparator.comparing(MessageDtos.ConversationResponse::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public MessageDtos.ConversationMessagesResponse messages(Long userId, Long conversationId) {
        Conversation conversation = requireConversationMember(userId, conversationId);
        List<ChatMessage> allEntities = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .orderByAsc(ChatMessage::getCreatedAt)
                        .orderByAsc(ChatMessage::getId));
        markConversationRead(userId, conversationId, allEntities);
        List<ChatMessage> entities = visibleMessages(userId, allEntities);
        List<MessageDtos.ChatMessageResponse> messages = entities.stream()
                .map(this::toMessageResponse)
                .toList();
        return new MessageDtos.ConversationMessagesResponse(toConversationResponse(conversation, userId), messages);
    }

    public MessageDtos.ChatBlockResponse chatBlock(Long userId, Long otherUserId) {
        requireBlockTarget(userId, otherUserId);
        return new MessageDtos.ChatBlockResponse(otherUserId,
                hasBlocked(userId, otherUserId), hasBlocked(otherUserId, userId));
    }

    @Transactional
    public void blockUser(Long userId, Long otherUserId) {
        requireBlockTarget(userId, otherUserId);
        if (!hasBlocked(userId, otherUserId)) {
            ChatBlock block = new ChatBlock();
            block.setBlockerUserId(userId);
            block.setBlockedUserId(otherUserId);
            block.setCreatedAt(LocalDateTime.now());
            chatBlockMapper.insert(block);
        }
        pushBlockChanged(userId, otherUserId);
    }

    @Transactional
    public void unblockUser(Long userId, Long otherUserId) {
        requireBlockTarget(userId, otherUserId);
        chatBlockMapper.delete(new LambdaQueryWrapper<ChatBlock>()
                .eq(ChatBlock::getBlockerUserId, userId)
                .eq(ChatBlock::getBlockedUserId, otherUserId));
        pushBlockChanged(userId, otherUserId);
    }

    @Transactional
    public MessageDtos.ChatMessageResponse sendMessage(Long userId, Long conversationId, MessageDtos.SendMessageRequest request) {
        Conversation conversation = requireConversationMember(userId, conversationId);
        if (!ACTIVE.equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该会话已不可用");
        }
        String content = request.content().trim();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息内容不能为空");
        }
        boolean deliveryFailed = false;
        if (DIRECT.equals(conversation.getType())) {
            Long recipientUserId = requireDirectRecipient(conversation, userId);
            if (hasBlocked(userId, recipientUserId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "您已拉黑对方，请先取消拉黑后再发送消息");
            }
            deliveryFailed = hasBlocked(recipientUserId, userId);
        }
        if (DIRECT.equals(conversation.getType()) && !deliveryFailed) {
            assertDirectRecipientAllowsPrivateChat(conversation, userId);
            assertDirectMessageAllowed(conversation, userId);
        }

        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setSenderUserId(userId);
        message.setType("TEXT");
        message.setContent(content);
        message.setDeliveryStatus(deliveryFailed ? FAILED_BLOCKED : DELIVERED);
        message.setDeliveryFailureReason(deliveryFailed ? BLOCKED_MESSAGE : null);
        applyQuote(message, request.quotedMessageId(), conversationId);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);

        if (deliveryFailed) {
            pushChatMessageCreatedToUser(userId, conversation.getId(), message.getId());
            pushConversationUpdatedToUser(userId, conversation.getId());
        } else {
            conversation.setLastMessageAt(message.getCreatedAt());
            conversationMapper.updateById(conversation);
            pushChatMessageCreated(conversation.getId(), message.getId());
            pushConversationUpdated(conversation.getId());
        }
        return toMessageResponse(message);
    }

    @Transactional
    public MessageDtos.ChatMessageResponse recallMessage(Long userId, Long conversationId, Long messageId) {
        requireConversationMember(userId, conversationId);
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null || !conversationId.equals(message.getConversationId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        if (!userId.equals(message.getSenderUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能撤回自己发送的消息");
        }
        if (message.getRecalledAt() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "该消息已经撤回");
        }
        if (message.getCreatedAt() == null || message.getCreatedAt().plusMinutes(3).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "消息发送超过3分钟，无法撤回");
        }
        message.setRecalledAt(LocalDateTime.now());
        chatMessageMapper.updateById(message);
        pushMessageChanged(conversationId, messageId, "MESSAGE_UPDATED", null);
        pushConversationUpdated(conversationId);
        return toMessageResponse(message);
    }

    @Transactional
    public void deleteMessage(Long userId, Long conversationId, Long messageId) {
        requireConversationMember(userId, conversationId);
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null || !conversationId.equals(message.getConversationId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        Long existing = chatMessageDeletionMapper.selectCount(new LambdaQueryWrapper<ChatMessageDeletion>()
                .eq(ChatMessageDeletion::getMessageId, messageId)
                .eq(ChatMessageDeletion::getUserId, userId));
        if (existing == null || existing == 0) {
            ChatMessageDeletion deletion = new ChatMessageDeletion();
            deletion.setMessageId(messageId);
            deletion.setUserId(userId);
            deletion.setCreatedAt(LocalDateTime.now());
            chatMessageDeletionMapper.insert(deletion);
        }
        pushMessageChanged(conversationId, messageId, "MESSAGE_DELETED", userId);
    }

    private MessageDtos.ConversationResponse resolveDirectConversation(Long requesterUserId, Long recipientUserId) {
        if (requesterUserId == null || requesterUserId.equals(recipientUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能与自己发起私聊");
        }
        requireEnabledUser(requesterUserId);
        User recipient = requireEnabledUser(recipientUserId);
        if (Boolean.FALSE.equals(recipient.getAllowPrivateChat())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "对方未开启私聊");
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
            throw new BusinessException(ErrorCode.CONFLICT, "私聊正在创建，请稍后重试");
        }
        insertMember(conversation.getId(), requesterUserId, "OWNER");
        insertMember(conversation.getId(), recipientUserId, "MEMBER");
        pushConversationUpdated(conversation.getId());
        return toConversationResponse(conversation, requesterUserId);
    }

    private void insertMember(Long conversationId, Long userId, String role) {
        insertMember(conversationId, userId, role, ACTIVE);
    }

    private void insertMember(Long conversationId, Long userId, String role, String status) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(role);
        member.setMemberStatus(status);
        member.setMuted(false);
        member.setJoinedAt(ACTIVE.equals(status) ? LocalDateTime.now() : null);
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
        if (PENDING.equals(member.getMemberStatus())) {
            createNotification(userId, "FAMILY_GROUP_INVITE", "家庭群聊邀请",
                    "“" + conversation.getName() + "”邀请你加入家庭群聊", familyGroupPayload(conversation.getId()));
        } else {
            insertSystemMessage(conversation, userId, displayName(userId) + "加入了群聊");
        }
    }

    private Shop requireOwnedFamilyShop(Long ownerUserId) {
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getOwnerUserId, ownerUserId)
                .eq(Shop::getStatus, 1)
                .last("limit 1"));
        if (shop == null || !"FAMILY".equals(shop.getVisibilityType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前店铺未开启家庭功能");
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
            throw new BusinessException(ErrorCode.NOT_FOUND, "家庭群聊不存在");
        }
        return conversation;
    }

    private Conversation requireOwnedFamilyGroup(Long ownerUserId, Long conversationId) {
        Conversation conversation = requireFamilyGroupConversation(conversationId);
        if (!ownerUserId.equals(conversation.getOwnerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅群主可以管理该群聊");
        }
        return conversation;
    }

    private Conversation requireFamilyGroupMember(Long userId, Long conversationId) {
        Conversation conversation = requireFamilyGroupConversation(conversationId);
        ConversationMember member = member(conversationId, userId);
        if (member == null || !ACTIVE.equals(member.getMemberStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "你当前不是有效群成员");
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
            throw new BusinessException(ErrorCode.FORBIDDEN, "你不是该店铺已通过的家庭成员");
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
                throw new BusinessException(ErrorCode.FORBIDDEN, "家庭菜品只能分享到对应家庭群聊");
            }
            return;
        }
        if (!DIRECT.equals(conversation.getType())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "家庭菜品不能分享到该会话");
        }
        Long recipientUserId = otherDirectUserId(conversation.getDirectKey(), senderUserId);
        if (recipientUserId == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "私聊参与者信息异常");
        }
        Shop dishShop = shopMapper.selectById(shopId);
        if (dishShop != null && recipientUserId.equals(dishShop.getOwnerUserId())) {
            return;
        }
        User recipient = requireEnabledUser(recipientUserId);
        if (RoleConstants.SHOP.equals(recipient.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该店铺的菜品只能分享给对应商家");
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
                conversation.getCoverImageUrl(), conversation.getAnnouncement(), conversation.getStatus(),
                Boolean.TRUE.equals(conversation.getJoinApprovalRequired()), members);
    }

    private void applyQuote(ChatMessage message, Long quotedMessageId, Long conversationId) {
        if (quotedMessageId == null) return;
        ChatMessage quoted = chatMessageMapper.selectById(quotedMessageId);
        if (quoted == null || !conversationId.equals(quoted.getConversationId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "引用的消息不存在");
        }
        message.setQuotedMessageId(quoted.getId());
        message.setQuotedType(quoted.getType());
        message.setQuotedContent(quotedPreview(quoted));
        message.setQuotedSenderName(displayName(quoted.getSenderUserId()));
    }

    private String quotedPreview(ChatMessage quoted) {
        if ("DISH_SHARE".equals(quoted.getType())) return quoted.getDishName();
        if ("ORDER_SHARE".equals(quoted.getType())) return "订单 " +
                (StringUtils.hasText(quoted.getOrderNo()) ? quoted.getOrderNo() : "分享");
        return quoted.getContent();
    }

    private List<ChatMessage> visibleMessages(Long userId, List<ChatMessage> messages) {
        if (messages.isEmpty()) return messages;
        Set<Long> deletedIds = chatMessageDeletionMapper == null ? Set.of() : new HashSet<>(chatMessageDeletionMapper.selectList(
                        new LambdaQueryWrapper<ChatMessageDeletion>().eq(ChatMessageDeletion::getUserId, userId))
                .stream().map(ChatMessageDeletion::getMessageId).toList());
        return messages.stream()
                .filter(message -> !deletedIds.contains(message.getId()))
                .filter(message -> !FAILED_BLOCKED.equals(message.getDeliveryStatus())
                        || userId.equals(message.getSenderUserId()))
                .toList();
    }

    private void requireBlockTarget(Long userId, Long otherUserId) {
        if (userId == null || otherUserId == null || userId.equals(otherUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能拉黑自己");
        }
        requireEnabledUser(userId);
        requireEnabledUser(otherUserId);
    }

    private boolean hasBlocked(Long blockerUserId, Long blockedUserId) {
        if (chatBlockMapper == null) return false;
        Long count = chatBlockMapper.selectCount(new LambdaQueryWrapper<ChatBlock>()
                .eq(ChatBlock::getBlockerUserId, blockerUserId)
                .eq(ChatBlock::getBlockedUserId, blockedUserId));
        return count != null && count > 0;
    }

    private Long requireDirectRecipient(Conversation conversation, Long userId) {
        Long recipientUserId = otherDirectUserId(conversation.getDirectKey(), userId);
        if (recipientUserId == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "私聊参与者信息异常");
        }
        return recipientUserId;
    }

    private String writeOrderItems(List<MessageDtos.OrderShareItemResponse> items) {
        try {
            return JSON.writeValueAsString(items);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "订单分享数据生成失败");
        }
    }

    private List<MessageDtos.OrderShareItemResponse> readOrderItems(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<MessageDtos.OrderShareItemResponse>>() { });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private MessageDtos.QuotedMessageSnapshot toQuotedSnapshot(ChatMessage message) {
        if (message == null) return null;
        return new MessageDtos.QuotedMessageSnapshot(message.getId(), message.getType(), message.getContent(),
                message.getDishId(), message.getShopId(), message.getShopName(), message.getDishName(),
                message.getDishImageUrl(), message.getDishPrice(), message.getDishDescription(),
                message.getDishSalesCount(), message.getOrderId(), message.getOrderNo(), message.getOrderCreatedAt(),
                message.getOrderStatus(), message.getOrderShopName(), readOrderItems(message.getOrderItemsJson()),
                message.getOrderTotalAmount(), message.getOrderRemark());
    }

    private String displayName(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getNickname())) return "用户";
        if (RoleConstants.SHOP.equals(user.getRole())) {
            Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                    .eq(Shop::getOwnerUserId, userId).last("limit 1"));
            if (shop != null && StringUtils.hasText(shop.getName())) return shop.getName();
        }
        return user.getNickname();
    }

    private String familyGroupPayload(Long conversationId) {
        return "{\"conversationId\":" + conversationId + "}";
    }

    private void notifyOwnerOfJoinRequest(Conversation conversation, Long userId) {
        createNotification(conversation.getOwnerUserId(), "FAMILY_GROUP_JOIN_REQUEST", "新的入群申请",
                displayName(userId) + "申请加入“" + conversation.getName() + "”", familyGroupPayload(conversation.getId()));
    }

    private void insertSystemMessage(Conversation conversation, Long actorUserId, String content) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setSenderUserId(actorUserId);
        message.setType("SYSTEM");
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
        conversation.setLastMessageAt(message.getCreatedAt());
        conversationMapper.updateById(conversation);
        pushChatMessageCreated(conversation.getId(), message.getId());
        pushConversationUpdated(conversation.getId());
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
            throw new BusinessException(ErrorCode.FORBIDDEN, "对方回复前只能发送一条消息");
        }
    }

    private void assertDirectRecipientAllowsPrivateChat(Conversation conversation, Long userId) {
        Long recipientUserId = otherDirectUserId(conversation.getDirectKey(), userId);
        if (recipientUserId == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "私聊参与者信息异常");
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
                throw new BusinessException(ErrorCode.FORBIDDEN, "对方未开启私聊");
            }
        }
    }

    private Conversation requireConversationMember(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        boolean member = activeMembers(userId).stream()
                .anyMatch(item -> conversationId.equals(item.getConversationId()));
        if (!member) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "你不是该会话成员");
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
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅顾客可以搜索并私聊其他顾客");
        }
        return user;
    }

    private User requireEnabledUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "用户账号已停用");
        }
        return user;
    }

    private MessageDtos.ConversationResponse toConversationResponse(Conversation conversation, Long viewerUserId) {
        ConversationMember viewer = member(conversation.getId(), viewerUserId);
        List<ChatMessage> conversationMessages = visibleMessages(viewerUserId,
                chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversation.getId())
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .orderByDesc(ChatMessage::getId)));
        ChatMessage latest = conversationMessages.isEmpty() ? null : conversationMessages.get(0);
        LocalDateTime visibleLastMessageAt = latest == null ? null : latest.getCreatedAt();
        long unread = conversationMessages.stream()
                .filter(message -> !viewerUserId.equals(message.getSenderUserId()))
                .filter(message -> viewer == null || viewer.getLastReadMessageId() == null
                        || (message.getId() != null && message.getId() > viewer.getLastReadMessageId()))
                .count();
        String lastContent = latest == null ? null : (latest.getRecalledAt() != null
                ? "撤回了一条消息" : ("DISH_SHARE".equals(latest.getType())
                ? "[菜品] " + latest.getDishName() : ("ORDER_SHARE".equals(latest.getType())
                ? "[订单] " + (StringUtils.hasText(latest.getOrderNo()) ? latest.getOrderNo() : "订单分享")
                : latest.getContent())));
        boolean muted = viewer != null && Boolean.TRUE.equals(viewer.getMuted());
        if (!DIRECT.equals(conversation.getType())) {
            return new MessageDtos.ConversationResponse(conversation.getId(), conversation.getType(), conversation.getName(),
                    conversation.getCoverImageUrl(), conversation.getStatus(), visibleLastMessageAt, null, null, null,
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
                conversation.getStatus(), visibleLastMessageAt, directUserId,
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
        ChatMessage quoted = message.getQuotedMessageId() == null ? null
                : chatMessageMapper.selectById(message.getQuotedMessageId());
        String deliveryStatus = StringUtils.hasText(message.getDeliveryStatus()) ? message.getDeliveryStatus() : DELIVERED;
        return new MessageDtos.ChatMessageResponse(message.getId(), message.getConversationId(), message.getSenderUserId(),
                senderName, senderAvatar, message.getType(), message.getContent(), message.getDishId(), message.getShopId(),
                message.getShopName(), message.getDishName(), message.getDishImageUrl(), message.getDishPrice(),
                message.getDishDescription(), message.getDishSalesCount(), message.getOrderId(), message.getOrderNo(),
                message.getOrderCreatedAt(), message.getOrderStatus(), message.getOrderShopName(),
                readOrderItems(message.getOrderItemsJson()), message.getOrderTotalAmount(), message.getOrderRemark(),
                message.getQuotedMessageId(),
                message.getQuotedType(), message.getQuotedContent(), message.getQuotedSenderName(),
                toQuotedSnapshot(quoted), deliveryStatus, FAILED_BLOCKED.equals(deliveryStatus),
                message.getDeliveryFailureReason(),
                message.getRecalledAt(), message.getCreatedAt());
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

    private void pushChatMessageCreatedToUser(Long userId, Long conversationId, Long messageId) {
        afterCommit(() -> wsPushService.pushToUser(userId, "CHAT", "MESSAGE_CREATED", Map.of(
                "conversationId", conversationId, "messageId", messageId)));
    }

    private void pushConversationUpdatedToUser(Long userId, Long conversationId) {
        afterCommit(() -> wsPushService.pushToUser(userId, "CHAT", "CONVERSATION_UPDATED", Map.of(
                "conversationId", conversationId)));
    }

    private void pushBlockChanged(Long userId, Long otherUserId) {
        afterCommit(() -> {
            wsPushService.pushToUser(userId, "CHAT", "BLOCK_CHANGED", Map.of("userId", otherUserId));
            wsPushService.pushToUser(otherUserId, "CHAT", "BLOCK_CHANGED", Map.of("userId", userId));
        });
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

    private void pushMessageChanged(Long conversationId, Long messageId, String action, Long onlyUserId) {
        afterCommit(() -> conversationMemberMapper.selectList(new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getMemberStatus, ACTIVE))
                .stream()
                .filter(member -> onlyUserId == null || onlyUserId.equals(member.getUserId()))
                .forEach(member -> wsPushService.pushToUser(member.getUserId(), "CHAT", action, Map.of(
                        "conversationId", conversationId, "messageId", messageId))));
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
