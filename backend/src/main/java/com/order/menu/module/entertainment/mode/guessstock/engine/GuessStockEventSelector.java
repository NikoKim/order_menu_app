package com.order.menu.module.entertainment.mode.guessstock.engine;

import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

public final class GuessStockEventSelector {
    private GuessStockEventSelector() {}

    /**
     * Picks at most one candidate before rolling its configured probability. This keeps the
     * overall event frequency near the configured rates instead of increasing with pool size.
     */
    public static Integer select(List<GuessStockConfig.EventConfig> events, Set<Integer> used, long seed) {
        return select(events, used, seed, false);
    }

    public static Integer select(List<GuessStockConfig.EventConfig> events, Set<Integer> used, long seed,
                                 boolean forceOccurrence) {
        List<Integer> eligible = new ArrayList<>();
        for (int index = 0; index < events.size(); index++) {
            GuessStockConfig.EventConfig event = events.get(index);
            if (!Boolean.FALSE.equals(event.enabled()) && !used.contains(index)) eligible.add(index);
        }
        if (eligible.isEmpty()) return null;
        // Exponential race: deterministic, upload-order independent weighted sampling.
        int index = eligible.stream().min((left, right) -> {
            double leftPriority = weightedPriority(seed, events.get(left));
            double rightPriority = weightedPriority(seed, events.get(right));
            int compared = Double.compare(leftPriority, rightPriority);
            return compared != 0 ? compared : Integer.compare(left, right);
        }).orElseThrow();
        if (forceOccurrence) return index;
        SplittableRandom occurrence = new SplittableRandom(mix(seed ^ 0x6a09e667f3bcc909L));
        return occurrence.nextDouble(100) < events.get(index).occurrenceProbability().doubleValue() ? index : null;
    }

    private static double weightedPriority(long seed, GuessStockConfig.EventConfig event) {
        long value = mix(seed ^ stableHash(event));
        double unit = ((value >>> 11) + 1d) * 0x1.0p-53;
        double weight = event.candidateWeight() == null ? 1 : Math.max(.01, event.candidateWeight().doubleValue());
        return -Math.log(Math.min(1, unit)) / weight;
    }

    private static long stableHash(GuessStockConfig.EventConfig event) {
        String value = String.join("\u0000", event.name(), String.valueOf(event.description()),
                event.occurrenceProbability().toPlainString(), event.effectProbability().toPlainString(),
                event.direction(), event.minImpactPercent().toPlainString(), event.maxImpactPercent().toPlainString(),
                String.valueOf(event.eventType()), String.valueOf(event.relevance()), String.valueOf(event.credibility()),
                String.valueOf(event.surprise()), String.valueOf(event.strength()));
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
