package com.order.menu.module.entertainment.mode.naval;

import com.order.menu.common.exception.BusinessException;
import com.order.menu.config.CorsProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/entertainment/naval/share")
public class NavalShareController {
    private final NavalService service;
    private final CorsProperties cors;
    private final Environment environment;
    private final String configuredFrontendOrigin;

    public NavalShareController(NavalService service, CorsProperties cors, Environment environment,
            @Value("${app.frontend-origin:${ORDER_MENU_FRONTEND_ORIGIN:https://moment.zsj-unknown.top}}")
            String configuredFrontendOrigin) {
        this.service = service;
        this.cors = cors;
        this.environment = environment;
        this.configuredFrontendOrigin = configuredFrontendOrigin;
    }

    @GetMapping(value = "/{inviteCode}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> share(@PathVariable String inviteCode,
            @RequestParam(required = false) String frontendOrigin, HttpServletRequest request) {
        String code = inviteCode.trim().toUpperCase(Locale.ROOT);
        String origin = frontendOrigin(frontendOrigin);
        String lobby = origin + "/customer/entertainment/naval";
        if (!code.matches("[A-Z2-9]{6}")) {
            return unavailable(HttpStatus.NOT_FOUND, origin, lobby);
        }
        try {
            Map<String, Object> metadata = service.getShareMetadata(code);
            String title = text(metadata.get("title"), "海战棋 · 舰队指挥部");
            String description = text(metadata.get("description"), "双人海战，邀请你加入舰队对决");
            String target = UriComponentsBuilder.fromHttpUrl(lobby).queryParam("invite", code)
                    .build().toUriString();
            String cardBase = isLocalOrigin(origin) ? origin + "/api/entertainment/naval/share/" + code
                    : request.getRequestURL().toString();
            String cardUrl = UriComponentsBuilder.fromHttpUrl(cardBase).replaceQuery(null)
                    .queryParam("frontendOrigin", origin).build().encode().toUriString();
            return html(HttpStatus.OK, shareHtml(title, description, origin, target, cardUrl, true,"MILITARY".equals(metadata.get("ruleSet"))));
        } catch (BusinessException error) {
            return unavailable(error.getCode() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE,
                    origin, lobby);
        } catch (RuntimeException error) {
            return unavailable(HttpStatus.SERVICE_UNAVAILABLE, origin, lobby);
        }
    }

    private ResponseEntity<String> unavailable(HttpStatus status, String origin, String lobby) {
        String description = status == HttpStatus.NOT_FOUND ? "房间已结束或邀请码已失效，仍可前往大厅开始新的对局"
                : "房间暂时无法连接，请稍后重试或前往大厅";
        return html(status, shareHtml("海战棋房间暂不可用", description, origin, lobby, lobby, false));
    }

    private ResponseEntity<String> html(HttpStatus status, String html) {
        return ResponseEntity.status(status).contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .cacheControl(CacheControl.noStore()).body(html);
    }

    private String frontendOrigin(String requested) {
        String candidate = normalizeOrigin(requested);
        String configured = normalizeOrigin(configuredFrontendOrigin);
        if (candidate != null && (candidate.equals(configured) || allowedOrigins().contains(candidate)
                || (isDevelopment() && isLocalOrigin(candidate)))) {
            return candidate;
        }
        if (configured != null) return configured;
        for (String allowed : allowedOrigins()) {
            String normalized = normalizeOrigin(allowed);
            if (normalized != null) return normalized;
        }
        return "https://moment.zsj-unknown.top";
    }

    private List<String> allowedOrigins() {
        return cors.getAllowedOrigins() == null ? List.of() : cors.getAllowedOrigins();
    }

    private boolean isDevelopment() {
        List<String> profiles = List.of(environment.getActiveProfiles());
        return profiles.contains("dev") || profiles.contains("test");
    }

    private static String normalizeOrigin(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                    || uri.getPort() == 0 || uri.getPort() > 65535) return null;
            int port = uri.getPort();
            if ((scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80)) port = -1;
            return new URI(scheme, null, uri.getHost().toLowerCase(Locale.ROOT), port, null, null, null).toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isLocalOrigin(String origin) {
        String host = URI.create(origin).getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.equals("[::1]") || host.equals("::1")) return true;
        String[] parts = host.split("\\.");
        if (parts.length != 4) return false;
        int[] bytes = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                bytes[i] = Integer.parseInt(parts[i]);
                if (bytes[i] < 0 || bytes[i] > 255) return false;
            }
        } catch (NumberFormatException ignored) {
            return false;
        }
        return bytes[0] == 127 || bytes[0] == 10 || (bytes[0] == 192 && bytes[1] == 168)
                || (bytes[0] == 172 && bytes[1] >= 16 && bytes[1] <= 31);
    }

    private static String text(Object value, String fallback) {
        if (!(value instanceof String string) || string.isBlank()) return fallback;
        return string.length() > 180 ? string.substring(0, 180) + "..." : string;
    }

    private static String shareHtml(String title, String description, String origin, String target,
            String cardUrl, boolean available) {
        return shareHtml(title,description,origin,target,cardUrl,available,false);
    }
    private static String shareHtml(String title, String description, String origin, String target,
            String cardUrl, boolean available, boolean military) {
        String safeTitle = HtmlUtils.htmlEscape(title, "UTF-8");
        String safeDescription = HtmlUtils.htmlEscape(description, "UTF-8");
        String safeImage = HtmlUtils.htmlEscape(origin + (military ? "/entertainment/naval/military/harbor-v2.png" : "/entertainment/naval/v2/harbor.png"), "UTF-8");
        String safeTarget = HtmlUtils.htmlEscape(target, "UTF-8");
        String safeCardUrl = HtmlUtils.htmlEscape(cardUrl, "UTF-8");
        String body = available
                ? "<iframe title=\"" + safeTitle + "\" src=\"" + safeTarget
                        + "\" allow=\"fullscreen; web-share; clipboard-write\" allowfullscreen></iframe>"
                        + "<noscript><h1>" + safeTitle + "</h1><p>" + safeDescription
                        + "</p><a href=\"" + safeTarget + "\">进入海战房间</a></noscript>"
                : "<main><img src=\"" + safeImage + "\" alt=\"像素舰队港口\"><h1>" + safeTitle
                        + "</h1><p>" + safeDescription + "</p><a href=\"" + safeTarget + "\">前往海战大厅</a></main>";
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">"
                + "<title>" + safeTitle + "</title><meta name=\"description\" content=\"" + safeDescription + "\">"
                + "<meta itemprop=\"name\" content=\"" + safeTitle + "\"><meta itemprop=\"description\" content=\"" + safeDescription + "\">"
                + "<meta itemprop=\"image\" content=\"" + safeImage + "\">"
                + "<meta property=\"og:type\" content=\"website\"><meta property=\"og:title\" content=\"" + safeTitle + "\">"
                + "<meta property=\"og:description\" content=\"" + safeDescription + "\"><meta property=\"og:image\" content=\"" + safeImage + "\">"
                + "<meta property=\"og:url\" content=\"" + safeCardUrl + "\"><meta name=\"twitter:card\" content=\"summary_large_image\">"
                + "<meta name=\"referrer\" content=\"strict-origin-when-cross-origin\">"
                + "<style>*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;font-family:system-ui,sans-serif;background:#f4f8f9;color:#355864}"
                + "iframe{border:0;display:block;width:100%;height:100dvh}main{max-width:520px;margin:0 auto;padding:48px 24px}"
                + "main img{width:100%;aspect-ratio:3/1;object-fit:cover;image-rendering:pixelated}h1{font-size:22px;line-height:1.5}p{font-size:14px;line-height:1.8}"
                + "a{display:inline-block;color:#b47c3c;padding:12px 0}</style></head><body>" + body + "</body></html>";
    }
}
