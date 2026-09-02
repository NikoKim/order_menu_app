package com.order.menu.module.entertainment.mode.guessstock.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Converts one total event shock into deterministic future market-stage slices. */
public final class EventImpactSchedule {
    private EventImpactSchedule() {}

    public static List<Slice> create(String eventType, int sourceRound, String sourceStage,
                                     BigDecimal totalImpactPct, double saturationFactor,
                                     double contrarianFactor) {
        double[] weights = weights(eventType);
        List<Slice> slices = new ArrayList<>(weights.length);
        int round = sourceRound;
        String stage = sourceStage;
        BigDecimal adjusted = totalImpactPct.multiply(BigDecimal.valueOf(saturationFactor * contrarianFactor));
        for (int index = 0; index < weights.length; index++) {
            if (index > 0) {
                if ("OPENING".equals(stage)) stage = "CLOSING";
                else { stage = "OPENING"; round++; }
            }
            slices.add(new Slice(index, round, stage, BigDecimal.valueOf(weights[index]).setScale(4,
                    RoundingMode.HALF_UP), adjusted.multiply(BigDecimal.valueOf(weights[index]))
                    .setScale(4, RoundingMode.HALF_UP)));
        }
        return List.copyOf(slices);
    }

    private static double[] weights(String eventType) {
        return switch (eventType == null ? "INDUSTRY" : eventType) {
            case "ACCIDENT" -> new double[] {.70, .20, .10};
            case "FINANCIAL" -> new double[] {.50, .30, .15, .05};
            case "DEMAND", "SUPPLY" -> new double[] {.35, .30, .20, .10, .05};
            case "POLICY" -> new double[] {.10, .30, .30, .20, .10};
            case "SENTIMENT" -> new double[] {.60, .25, .10, .05};
            default -> new double[] {.20, .25, .25, .20, .10};
        };
    }

    public record Slice(int offset, int targetRound, String targetStage,
                        BigDecimal weight, BigDecimal impactPct) {}
}
