package com.order.menu.module.entertainment.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.module.entertainment.core.dto.GameReviewDtos;
import com.order.menu.module.entertainment.core.entity.EntertainmentGame;
import com.order.menu.module.entertainment.core.entity.GameReview;
import com.order.menu.module.entertainment.core.mapper.EntertainmentGameMapper;
import com.order.menu.module.entertainment.core.mapper.GameReviewMapper;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameReviewServiceTest {
    @Mock EntertainmentGameMapper gameMapper;
    @Mock GameReviewMapper reviewMapper;
    @Mock UserMapper userMapper;
    GameReviewService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GameReview.class);
        service = new GameReviewService(gameMapper, reviewMapper, userMapper);
        EntertainmentGame game = new EntertainmentGame();
        game.setId(12L);
        game.setCreatorUserId(8L);
        game.setStatus("PUBLISHED");
        when(gameMapper.selectById(12L)).thenReturn(game);
    }

    @Test
    void unplayedCustomerCannotSubmitEvenWhenCallingApiDirectly() {
        when(userMapper.selectById(7L)).thenReturn(customer());
        when(reviewMapper.hasPlayed(12L, 7L)).thenReturn(false);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.save(12L, 7L, new GameReviewDtos.SaveRequest(5, "Good game")));
        assertEquals(403, error.getCode());
        verify(reviewMapper, never()).upsert(anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    void playedCustomerSavesTrimmedReviewWithAtomicUpsert() {
        when(userMapper.selectById(7L)).thenReturn(customer());
        when(reviewMapper.hasPlayed(12L, 7L)).thenReturn(true);
        when(reviewMapper.selectOne(any())).thenReturn(review(7L));
        var response = service.save(12L, 7L, new GameReviewDtos.SaveRequest(4, "  Good game  "));
        verify(reviewMapper).upsert(12L, 7L, 4, "Good game");
        assertTrue(response.mine());
        assertEquals(7L, response.author().id());
    }

    @Test
    void invalidScoresAndBlankCommentsAreRejected() {
        when(userMapper.selectById(7L)).thenReturn(customer());
        when(reviewMapper.hasPlayed(12L, 7L)).thenReturn(true);
        for (var request : List.of(new GameReviewDtos.SaveRequest(0, "Good"),
                new GameReviewDtos.SaveRequest(6, "Good"), new GameReviewDtos.SaveRequest(4, "   "),
                new GameReviewDtos.SaveRequest(4, "x".repeat(1001)))) {
            assertEquals(400, assertThrows(BusinessException.class, () -> service.save(12L, 7L, request)).getCode());
        }
        verify(reviewMapper, never()).upsert(anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    void disabledOrNonCustomerAccountsCannotReview() {
        User user = customer();
        user.setStatus(0);
        when(userMapper.selectById(7L)).thenReturn(user);
        assertThrows(BusinessException.class, () -> service.save(12L, 7L, new GameReviewDtos.SaveRequest(4, "Good")));
        user.setStatus(1);
        user.setRole("SHOP");
        assertThrows(BusinessException.class, () -> service.save(12L, 7L, new GameReviewDtos.SaveRequest(4, "Good")));
        verify(reviewMapper, never()).hasPlayed(anyLong(), anyLong());
    }

    @Test
    void guestsCanReadButHaveNoReviewPermission() {
        when(reviewMapper.selectPage(any(Page.class), any())).thenReturn(new Page<GameReview>(1, 5, 0));
        when(reviewMapper.averageRating(12L)).thenReturn(BigDecimal.ZERO);
        var result = service.list(12L, null, -5, 1000);
        assertFalse(result.canReview());
        assertNull(result.myReview());
        assertEquals(1, result.page());
        assertEquals(50, result.pageSize());
        verifyNoInteractions(userMapper);
        verify(reviewMapper, never()).hasPlayed(anyLong(), anyLong());
    }

    @Test
    void reviewsIncludeAverageAndCurrentPlayersOwnReview() {
        Page<GameReview> page = new Page<>(1, 5, 1);
        page.setRecords(List.of(review(7L)));
        when(reviewMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(reviewMapper.selectOne(any())).thenReturn(review(7L));
        when(reviewMapper.averageRating(12L)).thenReturn(new BigDecimal("4.6667"));
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(customer()));
        when(userMapper.selectById(7L)).thenReturn(customer());
        when(reviewMapper.hasPlayed(12L, 7L)).thenReturn(true);
        var result = service.list(12L, 7L, 1, 5);
        assertTrue(result.canReview());
        assertEquals(new BigDecimal("4.7"), result.averageRating());
        assertEquals("Player", result.records().get(0).author().nickname());
        assertTrue(result.myReview().mine());
    }

    @Test
    void unpublishedGameCannotLeakReviewsToOtherPlayers() {
        EntertainmentGame game = new EntertainmentGame();
        game.setCreatorUserId(8L);
        game.setStatus("OFFLINE");
        when(gameMapper.selectById(12L)).thenReturn(game);
        assertThrows(BusinessException.class, () -> service.list(12L, 7L, 1, 5));
        verifyNoInteractions(reviewMapper);
    }

    @Test
    void deleteIsRestrictedToTheAuthenticatedUsersReview() {
        service.deleteMine(12L, 7L);
        verify(reviewMapper).delete(argThat(query -> {
            String sql = query.getSqlSegment();
            var params = ((LambdaQueryWrapper<GameReview>) query).getParamNameValuePairs();
            return sql.contains("game_id") && sql.contains("user_id")
                    && params.containsValue(12L) && params.containsValue(7L);
        }));
    }

    private User customer() {
        User user = new User();
        user.setId(7L);
        user.setStatus(1);
        user.setRole("CUSTOMER");
        user.setNickname("Player");
        return user;
    }

    private GameReview review(Long userId) {
        GameReview review = new GameReview();
        review.setId(22L);
        review.setGameId(12L);
        review.setUserId(userId);
        review.setRating(4);
        review.setContent("Good game");
        return review;
    }
}
