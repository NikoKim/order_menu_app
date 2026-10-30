package com.order.menu.module.entertainment.mode.guessstock.agent;

import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockTrade;
import java.util.*;

/** Accepts settled trades only. Never accepts orders, private balances, or another AI's thesis. */
public final class AiPublicOpponentModel {
    private AiPublicOpponentModel() {}

    public static Summary summarize(List<GuessStockTrade> settled) {
        Map<Long, long[]> actors = new TreeMap<>();
        Map<Integer, long[]> rounds = new TreeMap<>();
        for (GuessStockTrade trade : settled) {
            long[] actor = actors.computeIfAbsent(trade.getParticipantId(), ignored -> new long[3]);
            long[] round = rounds.computeIfAbsent(trade.getRoundNo(), ignored -> new long[2]);
            int side = "BUY".equals(trade.getSide()) ? 0 : 1;
            actor[side] += trade.getQuantity(); actor[2]++;
            round[side] += trade.getQuantity();
        }
        long buys = actors.values().stream().mapToLong(value -> value[0]).sum();
        long sells = actors.values().stream().mapToLong(value -> value[1]).sum();
        double buyConcentration = buys == 0 ? 0 : actors.values().stream().mapToLong(value -> value[0]).max().orElse(0) / (double) buys;
        double sellConcentration = sells == 0 ? 0 : actors.values().stream().mapToLong(value -> value[1]).max().orElse(0) / (double) sells;
        List<List<Long>> active = actors.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Long, long[]>>comparingLong(entry -> -entry.getValue()[2])
                        .thenComparingLong(Map.Entry::getKey)).limit(8)
                .map(entry -> List.of(entry.getKey(), entry.getValue()[0] - entry.getValue()[1], entry.getValue()[2])).toList();
        List<long[]> recent = new ArrayList<>(rounds.values());
        double persistence = recent.stream().skip(Math.max(0, recent.size() - 4))
                .mapToDouble(value -> Math.signum(value[0] - value[1])).average().orElse(0);
        return new Summary(round(buyConcentration), round(sellConcentration), round(persistence), active);
    }

    private static double round(double value) { return Math.round(value * 100d) / 100d; }
    // activeActors entries: participant id, settled net shares, settled trade count. Net shares are NOT current holdings.
    public record Summary(double buyConcentration, double sellConcentration, double flowPersistence,
                          List<List<Long>> activeActors) {}
}
