package com.order.menu.module.feedback.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.order.menu.common.constant.StatusConstants;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.common.response.PageResponse;
import com.order.menu.module.feedback.dto.FeedbackDtos;
import com.order.menu.module.feedback.entity.Feedback;
import com.order.menu.module.feedback.mapper.FeedbackMapper;
import com.order.menu.module.message.service.MessageService;
import com.order.menu.module.shop.entity.Shop;
import com.order.menu.module.shop.service.ShopService;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import com.order.menu.websocket.WsPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class FeedbackService {
    private final FeedbackMapper feedbackMapper;
    private final ShopService shopService;
    private final UserMapper userMapper;
    private final WsPushService wsPushService;
    private final MessageService messageService;

    public FeedbackDtos.FeedbackResponse create(Long userId, FeedbackDtos.FeedbackRequest request) {
        if (request.shopId() != null) {
            shopService.requireById(request.shopId());
        }
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setShopId(request.shopId());
        feedback.setContent(request.content().trim());
        feedback.setStatus(StatusConstants.FEEDBACK_PENDING);
        feedbackMapper.insert(feedback);

        if (feedback.getShopId() != null) {
            wsPushService.pushFeedbackCreated(feedback);
            messageService.createShopNotification(feedback.getShopId(), "FEEDBACK", "收到新的顾客留言",
                    "有顾客提交了新的留言建议", feedbackPayload(feedback));
        }

        return toResponse(feedback);
    }

    public PageResponse<FeedbackDtos.FeedbackResponse> my(Long userId, int page, int pageSize) {
        Page<Feedback> result = feedbackMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getUserId, userId)
                .orderByDesc(Feedback::getCreatedAt));
        return new PageResponse<>(result.getRecords().stream().map(this::toResponse).toList(), page, pageSize, result.getTotal());
    }

    public PageResponse<FeedbackDtos.FeedbackResponse> shopFeedback(Long ownerUserId, int page, int pageSize, String status) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        String normalizedStatus = normalizeFeedbackStatus(status);
        Page<Feedback> result = feedbackMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getShopId, shop.getId())
                .eq(StringUtils.hasText(normalizedStatus), Feedback::getStatus, normalizedStatus)
                .orderByDesc(Feedback::getCreatedAt));
        return new PageResponse<>(result.getRecords().stream().map(this::toResponse).toList(), page, pageSize, result.getTotal());
    }

    public FeedbackDtos.FeedbackResponse reply(Long ownerUserId, Long feedbackId, FeedbackDtos.ReplyRequest request) {
        Shop shop = shopService.requireOwnedShop(ownerUserId);
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null || !shop.getId().equals(feedback.getShopId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Feedback not found");
        }
        feedback.setReply(request.reply().trim());
        feedback.setStatus(StatusConstants.FEEDBACK_REPLIED);
        feedbackMapper.updateById(feedback);
        Feedback updated = feedbackMapper.selectById(feedbackId);
        wsPushService.pushFeedbackReplied(updated);
        messageService.createNotification(updated.getUserId(), "FEEDBACK", "商家已回复留言",
                "商家回复了你的留言建议", feedbackPayload(updated));
        return toResponse(updated);
    }

    private String normalizeFeedbackStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim();
        if (!StatusConstants.isFeedbackStatus(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid feedback status");
        }
        return normalized;
    }

    private FeedbackDtos.FeedbackResponse toResponse(Feedback feedback) {
        String nickname = null;
        String avatarUrl = null;

        if (feedback.getUserId() != null) {
            User user = userMapper.selectById(feedback.getUserId());
            if (user != null) {
                nickname = user.getNickname();
                avatarUrl = user.getAvatarUrl();
            }
        }

        return new FeedbackDtos.FeedbackResponse(
                feedback.getId(),
                feedback.getUserId(),
                feedback.getShopId(),
                feedback.getContent(),
                feedback.getReply(),
                feedback.getStatus(),
                feedback.getCreatedAt(),
                nickname,
                avatarUrl
        );
    }

    private String feedbackPayload(Feedback feedback) {
        return "{\"feedbackId\":" + feedback.getId() + ",\"shopId\":" + feedback.getShopId() + "}";
    }
}
