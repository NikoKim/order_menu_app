package com.order.menu.module.entertainment.mode.naval;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.order.menu.common.exception.BusinessException;
import com.order.menu.config.CorsProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class NavalShareControllerTest {
    private final NavalService service = mock(NavalService.class);
    private final CorsProperties cors = new CorsProperties();
    private final MockEnvironment environment = new MockEnvironment();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private NavalShareController controller;

    @BeforeEach void setup() {
        cors.setAllowedOrigins(List.of("https://fleet.example", "http://localhost:5174"));
        environment.setActiveProfiles("prod");
        controller = new NavalShareController(service, cors, environment, "https://fleet.example");
        request.setScheme("https"); request.setServerName("api.fleet.example"); request.setServerPort(443);
        request.setRequestURI("/api/entertainment/naval/share/ABC234");
        when(service.getShareMetadata("ABC234")).thenReturn(Map.of(
                "title", "海战棋 · 甲的房间", "description", "甲 对阵 乙 · 对局进行中",
                "ships", "PRIVATE_FLEET_COORDINATES", "mines", "PRIVATE_MINE_COORDINATES"));
    }

    @Test void rendersCrawlerMetadataAndPlayableFrontendWithoutPrivateState() {
        var response = controller.share("abc234", "https://fleet.example", request);
        String html = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().getContentType());
        assertEquals("text/html;charset=UTF-8", response.getHeaders().getContentType().toString());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertTrue(html.contains("<title>海战棋 · 甲的房间</title>"));
        assertTrue(html.contains("property=\"og:description\" content=\"甲 对阵 乙 · 对局进行中\""));
        assertTrue(html.contains("https://fleet.example/entertainment/naval/v2/harbor.png"));
        assertTrue(html.contains("src=\"https://fleet.example/customer/entertainment/naval?invite=ABC234\""));
        assertTrue(html.contains("allow=\"fullscreen; web-share; clipboard-write\" allowfullscreen"));
        assertTrue(html.contains("https://api.fleet.example/api/entertainment/naval/share/ABC234"));
        assertFalse(html.contains("PRIVATE_FLEET_COORDINATES"));
        assertFalse(html.contains("PRIVATE_MINE_COORDINATES"));
        verify(service).getShareMetadata("ABC234");
    }

    @Test void militaryShareUsesModeNameAndItsOwnCover() {
        when(service.getShareMetadata("ABC234")).thenReturn(Map.of("ruleSet","MILITARY","title","海战棋 · 军事模式 · 甲的房间"));
        String html=controller.share("ABC234",null,request).getBody();
        assertTrue(html.contains("军事模式"));
        assertTrue(html.contains("/entertainment/naval/military/harbor-v2.png"));
        assertFalse(html.contains("/naval/v2/harbor.png"));
    }

    @Test void escapesUserProvidedNamesAndDescriptionsInAllHtmlContexts() {
        when(service.getShareMetadata("ABC234")).thenReturn(Map.of(
                "title", "舰队\"><script>alert(1)</script>",
                "description", "<img src=x onerror=alert(2)> & \"指挥官\""));
        String html = controller.share("ABC234", null, request).getBody();
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(2)&gt; &amp; &quot;指挥官&quot;"));
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<img src=x"));
        assertFalse(html.contains("content=\"舰队\"><"));
    }

    @Test void rejectsUntrustedOrMalformedFrontendOrigins() {
        for (String supplied : List.of("https://attacker.example", "javascript:alert(1)",
                "https://fleet.example@attacker.example", "https://fleet.example/other",
                "https://fleet.example?next=https://attacker.example", "https://fleet.example:0")) {
            String html = controller.share("ABC234", supplied, request).getBody();
            assertTrue(html.contains("src=\"https://fleet.example/customer/entertainment/naval?invite=ABC234\""), supplied);
            assertFalse(html.contains("attacker.example"), supplied);
        }
    }

    @Test void allowsExplicitCorsFrontendAndCanonicalizesDefaultPort() {
        String html = controller.share("ABC234", "http://localhost:5174", request).getBody();
        assertTrue(html.contains("src=\"http://localhost:5174/customer/entertainment/naval?invite=ABC234\""));
        assertTrue(html.contains("http://localhost:5174/api/entertainment/naval/share/ABC234"));
        html = controller.share("ABC234", "https://FLEET.EXAMPLE:443/", request).getBody();
        assertTrue(html.contains("src=\"https://fleet.example/customer/entertainment/naval?invite=ABC234\""));
    }

    @Test void onlyDevelopmentProfileAllowsUnlistedPrivateLanFrontend() {
        String html = controller.share("ABC234", "http://192.168.1.12:5174", request).getBody();
        assertFalse(html.contains("192.168.1.12"));
        environment.setActiveProfiles("dev");
        html = controller.share("ABC234", "http://192.168.1.12:5174", request).getBody();
        assertTrue(html.contains("http://192.168.1.12:5174/customer/entertainment/naval?invite=ABC234"));
        assertFalse(controller.share("ABC234", "http://192.168.1.999:5174", request).getBody().contains("192.168.1.999"));
        assertFalse(controller.share("ABC234", "http://10.attacker.example", request).getBody().contains("attacker.example"));
    }

    @Test void expiredRoomsRenderFriendlyNotFoundCardAndLobbyLink() {
        when(service.getShareMetadata("ABC234")).thenThrow(new BusinessException(404, "PRIVATE_INTERNAL_MESSAGE"));
        var response = controller.share("ABC234", null, request);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("房间已结束或邀请码已失效"));
        assertTrue(response.getBody().contains("href=\"https://fleet.example/customer/entertainment/naval\""));
        assertFalse(response.getBody().contains("<iframe"));
        assertFalse(response.getBody().contains("PRIVATE_INTERNAL_MESSAGE"));
    }

    @Test void transientFailuresDoNotExposeInternalDetails() {
        when(service.getShareMetadata("ABC234")).thenThrow(new IllegalStateException("DATABASE_SECRET"));
        var response = controller.share("ABC234", null, request);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(response.getBody().contains("房间暂时无法连接"));
        assertFalse(response.getBody().contains("DATABASE_SECRET"));
    }

    @Test void malformedCodesNeverReachRoomLookup() {
        assertEquals(HttpStatus.NOT_FOUND, controller.share("<bad>", null, request).getStatusCode());
        verifyNoInteractions(service);
    }
}
