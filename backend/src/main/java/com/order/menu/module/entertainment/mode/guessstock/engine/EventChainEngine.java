package com.order.menu.module.entertainment.mode.guessstock.engine;

import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/** Selects a possible follow-up without making event chains deterministic or guaranteed. */
public final class EventChainEngine {
    private EventChainEngine() {}

    public static Selection next(GuessStockConfig.EventConfig source, List<GuessStockConfig.EventConfig> candidates,
                                 ChainState state, long seed) {
        if (source == null || source.chainKey() == null || state.depth() >= source.maxChainDepth()) return null;
        List<GuessStockConfig.EventConfig> eligible = candidates.stream()
                .filter(item -> !Boolean.FALSE.equals(item.enabled()))
                .filter(item -> source.chainKey().equalsIgnoreCase(item.chainKey()))
                .filter(item -> item.chainStep() != null && item.chainStep() > state.lastStep())
                .filter(item -> source.followUpTags() == null || source.followUpTags().isEmpty()
                        || item.parentTags() != null && item.parentTags().stream().anyMatch(source.followUpTags()::contains))
                .toList();
        if (eligible.isEmpty()) return null;
        GuessStockConfig.EventConfig selected = eligible.stream().min(Comparator.comparingDouble(item -> {
            long mixed = mix(seed ^ item.name().hashCode());
            double unit = ((mixed >>> 11) + 1d) * 0x1.0p-53;
            double weight = item.candidateWeight() == null ? 1 : item.candidateWeight().doubleValue();
            return -Math.log(Math.min(1, unit)) / Math.max(.01, weight);
        })).orElseThrow();
        double boost = source.followUpProbabilityBoost() == null ? 0 : source.followUpProbabilityBoost().doubleValue();
        double probability = Math.min(100, selected.occurrenceProbability().doubleValue() + boost);
        if (new SplittableRandom(mix(seed ^ 0x72c5L)).nextDouble(100) >= probability) return null;
        return new Selection(selected, new ChainState(source.chainKey(), selected.chainStep(),
                state.depth() + 1, state.cooldownUntilRound()));
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public record ChainState(String chainKey, int lastStep, int depth, int cooldownUntilRound) {}
    public record Selection(GuessStockConfig.EventConfig event, ChainState nextState) {}
}
