package com.order.menu.websocket;

import com.order.menu.common.transaction.AfterCommitExecutor;
import com.order.menu.module.family.entity.FamilyMember;
import com.order.menu.module.feedback.entity.Feedback;
import com.order.menu.module.order.entity.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WsPushService {
    private final WsEventTransport transport;
    private final WsInstanceIdentity instanceIdentity;
    private final AfterCommitExecutor afterCommitExecutor;

    public void pushToShop(Long shopId, String module, String action, Map<String, Object> data) {
        if (shopId == null) {
            return;
        }

        publish(WsTargetType.SHOP, shopId, module, action, data);
    }

    public void pushToCustomer(Long customerUserId, String module, String action, Map<String, Object> data) {
        if (customerUserId == null) {
            return;
        }

        publish(WsTargetType.CUSTOMER, customerUserId, module, action, data);
    }

    public void pushToUser(Long userId, String module, String action, Map<String, Object> data) {
        if (userId == null) {
            return;
        }
        publish(WsTargetType.USER, userId, module, action, data);
    }

    private void publish(WsTargetType targetType, Long targetId, String module, String action,
                         Map<String, Object> data) {
        WsEvent event = WsEvent.create(targetType, targetId, WsMessage.of(module, action, data),
                instanceIdentity.value());
        afterCommitExecutor.execute(() -> transport.publish(event));
    }

    public void pushShopBadgeChanged(Long shopId, String reason) {
        pushToShop(shopId, "NOTIFICATION", "BADGE_CHANGED", Map.of(
                "shopId", shopId,
                "reason", reason
        ));
    }

    public void pushOrderCreated(Order order) {
        pushToShop(order.getShopId(), "ORDER", "CREATED", Map.of(
                "orderId", order.getId(),
                "orderNo", order.getOrderNo(),
                "shopId", order.getShopId(),
                "customerUserId", order.getCustomerUserId(),
                "status", order.getStatus()
        ));

        pushShopBadgeChanged(order.getShopId(), "ORDER_CREATED");
    }

    public void pushOrderCancelled(Order order) {
        pushToShop(order.getShopId(), "ORDER", "CANCELLED", Map.of(
                "orderId", order.getId(),
                "orderNo", order.getOrderNo(),
                "shopId", order.getShopId(),
                "customerUserId", order.getCustomerUserId(),
                "status", order.getStatus()
        ));

        pushToCustomer(order.getCustomerUserId(), "ORDER", "CANCELLED", Map.of(
                "orderId", order.getId(),
                "orderNo", order.getOrderNo(),
                "shopId", order.getShopId(),
                "status", order.getStatus()
        ));

        pushShopBadgeChanged(order.getShopId(), "ORDER_CANCELLED");
    }

    public void pushOrderAccepted(Order order) {
        pushOrderStatusChanged(order, "ACCEPTED", "ORDER_ACCEPTED");
    }

    public void pushOrderRejected(Order order) {
        pushOrderStatusChanged(order, "REJECTED", "ORDER_REJECTED");
    }

    public void pushOrderCompleted(Order order) {
        pushOrderStatusChanged(order, "COMPLETED", "ORDER_COMPLETED");
    }

    private void pushOrderStatusChanged(Order order, String action, String reason) {
        pushToShop(order.getShopId(), "ORDER", action, Map.of(
                "orderId", order.getId(),
                "orderNo", order.getOrderNo(),
                "shopId", order.getShopId(),
                "customerUserId", order.getCustomerUserId(),
                "status", order.getStatus()
        ));

        pushToCustomer(order.getCustomerUserId(), "ORDER", action, Map.of(
                "orderId", order.getId(),
                "orderNo", order.getOrderNo(),
                "shopId", order.getShopId(),
                "status", order.getStatus()
        ));

        pushShopBadgeChanged(order.getShopId(), reason);
    }

    public void pushFeedbackCreated(Feedback feedback) {
        pushToShop(feedback.getShopId(), "FEEDBACK", "CREATED", Map.of(
                "feedbackId", feedback.getId(),
                "shopId", feedback.getShopId(),
                "userId", feedback.getUserId(),
                "status", feedback.getStatus()
        ));

        pushShopBadgeChanged(feedback.getShopId(), "FEEDBACK_CREATED");
    }

    public void pushFeedbackReplied(Feedback feedback) {
        pushToShop(feedback.getShopId(), "FEEDBACK", "REPLIED", Map.of(
                "feedbackId", feedback.getId(),
                "shopId", feedback.getShopId(),
                "userId", feedback.getUserId(),
                "status", feedback.getStatus()
        ));

        pushToCustomer(feedback.getUserId(), "FEEDBACK", "REPLIED", Map.of(
                "feedbackId", feedback.getId(),
                "shopId", feedback.getShopId(),
                "status", feedback.getStatus()
        ));

        pushShopBadgeChanged(feedback.getShopId(), "FEEDBACK_REPLIED");
    }

    public void pushFamilyMemberApplied(FamilyMember member) {
        pushToShop(member.getShopId(), "FAMILY", "MEMBER_APPLIED", Map.of(
                "memberId", member.getId(),
                "shopId", member.getShopId(),
                "userId", member.getUserId(),
                "status", member.getStatus()
        ));

        pushShopBadgeChanged(member.getShopId(), "FAMILY_MEMBER_APPLIED");
    }

    public void pushFamilyMemberApproved(FamilyMember member) {
        pushToShop(member.getShopId(), "FAMILY", "MEMBER_APPROVED", Map.of(
                "memberId", member.getId(),
                "shopId", member.getShopId(),
                "userId", member.getUserId(),
                "status", member.getStatus()
        ));

        pushToCustomer(member.getUserId(), "FAMILY", "MEMBER_APPROVED", Map.of(
                "memberId", member.getId(),
                "shopId", member.getShopId(),
                "status", member.getStatus()
        ));

        pushShopBadgeChanged(member.getShopId(), "FAMILY_MEMBER_APPROVED");
    }

    public void pushFamilyMemberRejected(Long memberId, Long shopId, Long userId) {
        pushToShop(shopId, "FAMILY", "MEMBER_REJECTED", Map.of(
                "memberId", memberId,
                "shopId", shopId,
                "userId", userId,
                "status", "REJECTED"
        ));

        pushToCustomer(userId, "FAMILY", "MEMBER_REJECTED", Map.of(
                "memberId", memberId,
                "shopId", shopId,
                "status", "REJECTED"
        ));

        pushShopBadgeChanged(shopId, "FAMILY_MEMBER_REJECTED");
    }

    public void pushShopProfileChanged(Long shopId) {
        pushToShop(shopId, "SHOP", "PROFILE_CHANGED", Map.of(
                "shopId", shopId
        ));
    }

    public void pushShopBusinessStatusChanged(Long shopId, String businessStatus) {
        pushToShop(shopId, "SHOP", "BUSINESS_STATUS_CHANGED", Map.of(
                "shopId", shopId,
                "businessStatus", businessStatus,
                "isOpen", "OPEN".equals(businessStatus)
        ));
    }
}
