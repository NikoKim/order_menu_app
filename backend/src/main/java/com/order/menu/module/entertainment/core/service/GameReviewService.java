package com.order.menu.module.entertainment.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.entertainment.core.dto.EntertainmentDtos;
import com.order.menu.module.entertainment.core.dto.GameReviewDtos;
import com.order.menu.module.entertainment.core.entity.EntertainmentGame;
import com.order.menu.module.entertainment.core.entity.GameReview;
import com.order.menu.module.entertainment.core.mapper.EntertainmentGameMapper;
import com.order.menu.module.entertainment.core.mapper.GameReviewMapper;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameReviewService {
    private final EntertainmentGameMapper gameMapper;
    private final GameReviewMapper reviewMapper;
    private final UserMapper userMapper;

    public GameReviewDtos.ReviewPageResponse list(Long gameId, Long viewerId, int page, int pageSize) {
        requireVisible(gameId, viewerId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, pageSize));
        Page<GameReview> result = reviewMapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<GameReview>().eq(GameReview::getGameId, gameId)
                        .orderByDesc(GameReview::getUpdatedAt).orderByDesc(GameReview::getId));
        GameReview mine = viewerId == null ? null : findMine(gameId, viewerId);
        Set<Long> authorIds = result.getRecords().stream().map(GameReview::getUserId).collect(Collectors.toSet());
        if (mine != null) authorIds.add(mine.getUserId());
        Map<Long, User> authors = new HashMap<>();
        if (!authorIds.isEmpty()) userMapper.selectBatchIds(authorIds).forEach(user -> authors.put(user.getId(), user));
        User viewer = viewerId == null ? null : userMapper.selectById(viewerId);
        boolean canReview = activeCustomer(viewer) && reviewMapper.hasPlayed(gameId, viewerId);
        return new GameReviewDtos.ReviewPageResponse(
                result.getRecords().stream().map(review -> response(review, authors.get(review.getUserId()), viewerId)).toList(),
                safePage, safeSize, result.getTotal(), reviewMapper.averageRating(gameId).setScale(1, RoundingMode.HALF_UP),
                canReview, mine == null ? null : response(mine, authors.get(mine.getUserId()), viewerId));
    }

    @Transactional
    public GameReviewDtos.ReviewResponse save(Long gameId, Long userId, GameReviewDtos.SaveRequest request) {
        requireVisible(gameId, userId);
        User user = userMapper.selectById(userId);
        if (!activeCustomer(user) || !reviewMapper.hasPlayed(gameId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有玩过这个游戏的已登录用户可以评论和评分");
        }
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5
                || request.content() == null || request.content().isBlank() || request.content().length() > 1000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择 1 到 5 星并填写 1000 字以内的评论");
        }
        // A unique game/user key makes retries and concurrent submissions update one review.
        reviewMapper.upsert(gameId, userId, request.rating(), request.content().trim());
        return response(findMine(gameId, userId), user, userId);
    }

    @Transactional
    public void deleteMine(Long gameId, Long userId) {
        requireVisible(gameId, userId);
        reviewMapper.delete(new LambdaQueryWrapper<GameReview>()
                .eq(GameReview::getGameId, gameId).eq(GameReview::getUserId, userId));
    }

    private GameReview findMine(Long gameId, Long userId) {
        return reviewMapper.selectOne(new LambdaQueryWrapper<GameReview>()
                .eq(GameReview::getGameId, gameId).eq(GameReview::getUserId, userId));
    }

    private void requireVisible(Long gameId, Long viewerId) {
        EntertainmentGame game = gameMapper.selectById(gameId);
        if (game == null || "DELETED".equals(game.getStatus())
                || (!"PUBLISHED".equals(game.getStatus()) && !Objects.equals(viewerId, game.getCreatorUserId()))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "游戏不存在或未发布");
        }
    }

    private boolean activeCustomer(User user) {
        return user != null && Integer.valueOf(1).equals(user.getStatus()) && "CUSTOMER".equals(user.getRole());
    }

    private GameReviewDtos.ReviewResponse response(GameReview review, User user, Long viewerId) {
        return new GameReviewDtos.ReviewResponse(review.getId(), review.getGameId(),
                new EntertainmentDtos.CreatorResponse(review.getUserId(), user == null ? "用户" : user.getNickname(),
                        user == null ? null : user.getAvatarUrl()),
                review.getRating(), review.getContent(), review.getCreatedAt(), review.getUpdatedAt(),
                Objects.equals(viewerId, review.getUserId()));
    }
}
