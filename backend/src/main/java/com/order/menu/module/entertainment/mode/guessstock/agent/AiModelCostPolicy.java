package com.order.menu.module.entertainment.mode.guessstock.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Locale;

/** Peak prices checked 2026-10-22. Unknown gateways use configurable estimates, not provider billing. */
public final class AiModelCostPolicy {
    private AiModelCostPolicy() {}

    public static boolean officialDeepSeek(String baseUrl) {
        try { return "api.deepseek.com".equalsIgnoreCase(URI.create(baseUrl).getHost()); }
        catch (RuntimeException ignored) { return false; }
    }

    public static Rates rates(String baseUrl, String model, AiModelSettings settings) {
        String name = model == null ? "" : model.toLowerCase(Locale.ROOT);
        if (officialDeepSeek(baseUrl)) {
            if (java.util.Set.of("deepseek-flash", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp").contains(name)) {
                return new Rates(new BigDecimal("2"), new BigDecimal("0.04"), new BigDecimal("8"), true);
            }
            if ("deepseek-v4-pro".equals(name)) {
                return new Rates(new BigDecimal("9"), new BigDecimal("0.30"), new BigDecimal("27"), true);
            }
        }
        return new Rates(settings.getUnknownInputRate(), settings.getUnknownCachedRate(),
                settings.getUnknownOutputRate(), false);
    }

    public record Rates(BigDecimal input, BigDecimal cached, BigDecimal output, boolean officialPrice) {
        public BigDecimal cost(long prompt, long cacheHits, long completion) {
            long hits = Math.max(0, Math.min(prompt, cacheHits));
            return input.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(Math.max(0, prompt - hits)))
                    .add(cached.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(hits)))
                    .add(output.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(Math.max(0, completion))))
                    .divide(new BigDecimal("1000000"), 8, RoundingMode.CEILING);
        }
    }
}
