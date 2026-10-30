package com.order.menu.module.entertainment.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.order.menu.common.exception.BusinessException;
import com.order.menu.config.JwtProperties;
import com.order.menu.module.entertainment.core.dto.EntertainmentDtos;
import com.order.menu.module.entertainment.core.mapper.GameAiCredentialMapper;
import org.junit.jupiter.api.Test;

class GameAiCredentialConnectionTest {
    private final GameAiCredentialMapper mapper = mock(GameAiCredentialMapper.class);
    private final GameAiCredentialService service = new GameAiCredentialService(mapper, new JwtProperties());
    private final PlayerIdentity guest = new PlayerIdentity("GUEST", null, 1L, "G:1", "访客");

    @Test
    void testingAnUnsavedCredentialDoesNotStoreIt() {
        var result = service.credentialForConnectionTest(guest, "OWN", null, null,
                new EntertainmentDtos.AiCredentialRequest("https://8.8.8.8/v1/", " model ", " test-secret "));
        assertEquals("https://8.8.8.8/v1", result.baseUrl());
        assertEquals("model", result.model());
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsLocalOrNonHttpsTargetsAndGuestDefaultCredentials() {
        for (String address : new String[] { "http://8.8.8.8/v1", "https://127.0.0.1/v1", "https://10.0.0.1/v1", "https://user:password@8.8.8.8/v1" }) {
            assertThrows(BusinessException.class, () -> service.credentialForConnectionTest(guest, "OWN", null, null,
                    new EntertainmentDtos.AiCredentialRequest(address, "model", "test-secret")));
        }
        assertThrows(BusinessException.class, () -> service.credentialForConnectionTest(guest, "DEFAULT", null, null, null));
        verifyNoInteractions(mapper);
    }
}
