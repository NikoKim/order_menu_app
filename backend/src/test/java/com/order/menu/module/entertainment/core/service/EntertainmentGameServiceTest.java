package com.order.menu.module.entertainment.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.module.entertainment.core.entity.EntertainmentGame;
import com.order.menu.module.entertainment.core.mapper.EntertainmentGameMapper;
import com.order.menu.module.entertainment.core.mapper.GameRoomMapper;
import com.order.menu.module.entertainment.core.mapper.GameSessionMapper;
import com.order.menu.module.entertainment.core.mapper.GameVersionMapper;
import com.order.menu.module.entertainment.core.mode.GameModeRegistry;
import com.order.menu.module.user.entity.User;
import com.order.menu.module.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntertainmentGameServiceTest {
    @Mock private EntertainmentGameMapper gameMapper;
    @Mock private GameVersionMapper versionMapper;
    @Mock private GameSessionMapper sessionMapper;
    @Mock private GameRoomMapper roomMapper;
    @Mock private UserMapper userMapper;
    @Mock private ObjectMapper objectMapper;
    @Mock private GameModeRegistry modeRegistry;
    private EntertainmentGameService service;

    @BeforeEach
    void setUp() {
        service = new EntertainmentGameService(gameMapper, versionMapper, sessionMapper, roomMapper,
                userMapper, objectMapper, modeRegistry);
        User user = new User();
        user.setId(7L);
        user.setRole("CUSTOMER");
        user.setStatus(1);
        when(userMapper.selectById(7L)).thenReturn(user);
    }

    @Test
    void deletesOnlyAnOfflineOwnedGame() {
        EntertainmentGame game = game("OFFLINE");
        when(gameMapper.selectByIdForUpdate(12L)).thenReturn(game);

        service.delete(7L, 12L);

        ArgumentCaptor<EntertainmentGame> saved = ArgumentCaptor.forClass(EntertainmentGame.class);
        verify(gameMapper).updateById(saved.capture());
        assertEquals("DELETED", saved.getValue().getStatus());
    }

    @Test
    void rejectsDeletingAPublishedGame() {
        when(gameMapper.selectByIdForUpdate(12L)).thenReturn(game("PUBLISHED"));

        assertThrows(BusinessException.class, () -> service.delete(7L, 12L));
        verify(gameMapper, org.mockito.Mockito.never()).updateById(any(EntertainmentGame.class));
    }

    private static EntertainmentGame game(String status) {
        EntertainmentGame game = new EntertainmentGame();
        game.setId(12L);
        game.setCreatorUserId(7L);
        game.setStatus(status);
        return game;
    }
}
