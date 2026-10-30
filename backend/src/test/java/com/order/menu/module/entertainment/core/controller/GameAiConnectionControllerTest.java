package com.order.menu.module.entertainment.core.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.order.menu.module.entertainment.core.dto.AiConnectionDtos;
import com.order.menu.module.entertainment.core.service.*;
import com.order.menu.module.entertainment.mode.guessstock.challenge.DailyChallengeService;
import com.order.menu.module.entertainment.mode.guessstock.service.GuessStockSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class GameAiConnectionControllerTest {
    @Mock EntertainmentGameService games;
    @Mock EntertainmentGuestService guests;
    @Mock GuessStockSessionService sessions;
    @Mock GameRoomService rooms;
    @Mock GameAiCredentialService credentials;
    @Mock GameAiConnectionService connection;
    @Mock GamePlayKeyService playKeys;
    @Mock DailyChallengeService challenges;
    @Mock GameReviewService reviews;
    @InjectMocks EntertainmentController controller;
    private MockMvc mvc;

    @BeforeEach
    void setup() { mvc = MockMvcBuilders.standaloneSetup(controller).build(); }

    @Test
    void exposesTheProbeThroughTheGuestAwareApiWithoutReturningTheKey() throws Exception {
        var player = new PlayerIdentity("GUEST", null, 1L, "G:1", "访客");
        when(guests.resolve(any(), eq("test-guest"), eq(true))).thenReturn(player);
        when(connection.test(eq(player), any())).thenReturn(new AiConnectionDtos.Result(true, "连接有效", "model", 10));
        mvc.perform(post("/api/entertainment/ai-connection/test")
                .header("X-Guest-Token", "test-guest").contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"OWN\",\"aiCredential\":{\"baseUrl\":\"https://example.com/v1\",\"model\":\"model\",\"apiKey\":\"test-private-key\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());
        verify(connection).test(eq(player), argThat(request -> request.source().equals("OWN")
                && request.aiCredential().apiKey().equals("test-private-key")));
    }

    @Test
    void rejectsInvalidSourcesBeforeAnyProbe() throws Exception {
        mvc.perform(post("/api/entertainment/ai-connection/test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(connection);
    }
}
