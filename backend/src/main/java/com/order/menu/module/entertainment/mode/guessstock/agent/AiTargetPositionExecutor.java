package com.order.menu.module.entertainment.mode.guessstock.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class AiTargetPositionExecutor {
    public ExecutionPlan plan(Guard guard, Context context) {
        double requestedTarget = clamp(context.targetPositionPct(), 0, guard.maxTotalPositionPct().doubleValue());
        boolean escape = context.thesisInvalidated() || context.criticalRisk();
        double maxStep = "HIGH".equals(context.urgency())
                ? guard.highUrgencyPositionStepPctPoints().doubleValue()
                : guard.maxPositionStepPctPoints().doubleValue();
        if (requestedTarget < context.currentPositionPct()) maxStep *= 2;
        double target = escape ? requestedTarget : context.currentPositionPct()
                + clamp(requestedTarget - context.currentPositionPct(), -maxStep, maxStep);
        double delta = target - context.currentPositionPct();
        boolean riskReduction = escape && delta < 0;
        if (Math.abs(delta) < .0000001
                || Math.abs(delta) < guard.rebalanceBandPctPoints().doubleValue() && !riskReduction) {
            return hold(target, requestedTarget, "目标敞口差小于调仓缓冲区");
        }
        String direction = delta > 0 ? "BUY" : "SELL";
        if (!context.bidirectionalTrading() && (("OPENING".equals(context.stage()) && "SELL".equals(direction))
                || ("CLOSING".equals(context.stage()) && "BUY".equals(direction)))) {
            return hold(target, requestedTarget, "目标仓位已保留，当前交易阶段暂不支持该方向");
        }
        if ("SELL".equals(direction) && context.lastBuyRound() != null
                && context.roundNo() - context.lastBuyRound() < guard.minHoldRounds() && !escape) {
            return hold(target, requestedTarget, "尚未达到最短持有回合");
        }
        boolean reverse = context.lastTradeDirection() != null
                && !direction.equals(context.lastTradeDirection());
        if (reverse && context.lastTradeRound() != null
                && context.roundNo() - context.lastTradeRound() < guard.reverseCooldownRounds()
                && !escape && !context.confirmedRegimeReversal()) {
            return hold(target, requestedTarget, "尚处于跨回合反向交易冷却期");
        }
        if (!"NONE".equals(context.roundTradeDirection())
                && !direction.equals(context.roundTradeDirection()) && !escape) {
            return hold(target, requestedTarget, "同回合反向交易已被观点连续性规则拦截");
        }
        long targetShares = targetShares(context.totalAssets(), context.price(), target);
        long desiredShares = Math.abs(targetShares - context.currentShares());
        double executionRatio = switch (context.urgency()) {
            case "LOW" -> .50;
            case "HIGH" -> 1;
            default -> .75;
        };
        desiredShares = Math.max(1, (long) Math.floor(desiredShares * executionRatio));
        long affordable = "BUY".equals(direction)
                ? context.availableCash().divide(context.price(), 0, RoundingMode.FLOOR).longValue()
                : context.currentShares();
        BigDecimal remainingTurnover = context.roundStartAssets().multiply(guard.maxRoundTurnoverPct())
                .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP).subtract(context.usedRoundTurnoverValue());
        long turnoverCapacity = remainingTurnover.signum() <= 0 ? 0
                : remainingTurnover.divide(context.price(), 0, RoundingMode.FLOOR).longValue();
        long impactCapacity = impactCapacity(guard, context);
        long shares = Math.max(0, Math.min(desiredShares, Math.min(guard.singleOrderMaxShares(),
                Math.min(affordable, Math.min(turnoverCapacity, impactCapacity)))));
        BigDecimal value = context.price().multiply(BigDecimal.valueOf(shares));
        BigDecimal minimum = context.totalAssets().multiply(guard.minTradeAssetPct())
                .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
        if (shares <= 0) return hold(target, requestedTarget, "现金、持仓、换手或市场冲击额度不足");
        if (value.compareTo(minimum) < 0 && !riskReduction) return hold(target, requestedTarget, "计划交易金额低于最小资产比例");
        return new ExecutionPlan(direction, shares, target, requestedTarget, value,
                target == requestedTarget ? "目标敞口通过统一执行约束" : "目标敞口按单次步长分批执行");
    }

    private static long impactCapacity(Guard guard, Context context) {
        if (context.marketMaxOrderImpactPct().signum() <= 0) return guard.singleOrderMaxShares();
        double ratio = guard.maxEstimatedImpactPct().divide(context.marketMaxOrderImpactPct(), 8, RoundingMode.HALF_UP)
                .doubleValue();
        return Math.max(0, (long) Math.floor(context.marketLiquidityShares().doubleValue()
                * Math.max(1, context.participantCount()) * Math.min(1, ratio)));
    }

    private static long targetShares(BigDecimal totalAsset, BigDecimal price, double targetPct) {
        if (totalAsset.signum() <= 0 || price.signum() <= 0 || targetPct <= 0) return 0;
        return totalAsset.multiply(BigDecimal.valueOf(targetPct)).divide(new BigDecimal("100"), 10,
                RoundingMode.HALF_UP).divide(price, 0, RoundingMode.FLOOR).longValue();
    }

    private static ExecutionPlan hold(double target, double requestedTarget, String reason) {
        return new ExecutionPlan("HOLD", 0, target, requestedTarget, BigDecimal.ZERO, reason);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Guard(BigDecimal maxTotalPositionPct, BigDecimal rebalanceBandPctPoints,
                        int minHoldRounds, BigDecimal minTradeAssetPct, BigDecimal maxRoundTurnoverPct,
                        long singleOrderMaxShares, BigDecimal maxEstimatedImpactPct,
                        BigDecimal maxPositionStepPctPoints, BigDecimal highUrgencyPositionStepPctPoints,
                        int reverseCooldownRounds) {
        public Guard(BigDecimal maxTotalPositionPct, BigDecimal rebalanceBandPctPoints,
                     int minHoldRounds, BigDecimal minTradeAssetPct, BigDecimal maxRoundTurnoverPct,
                     long singleOrderMaxShares, BigDecimal maxEstimatedImpactPct) {
            this(maxTotalPositionPct, rebalanceBandPctPoints, minHoldRounds, minTradeAssetPct,
                    maxRoundTurnoverPct, singleOrderMaxShares, maxEstimatedImpactPct,
                    new BigDecimal("15"), new BigDecimal("30"), 2);
        }
    }

    public record Context(String stage, int roundNo, double targetPositionPct, double currentPositionPct,
                          long currentShares, BigDecimal availableCash, BigDecimal totalAssets,
                          BigDecimal roundStartAssets, BigDecimal usedRoundTurnoverValue, BigDecimal price,
                          String urgency, Integer lastBuyRound, String roundTradeDirection,
                          boolean thesisInvalidated, boolean criticalRisk, BigDecimal marketLiquidityShares,
                          int participantCount, BigDecimal marketMaxOrderImpactPct,
                          Integer lastTradeRound, String lastTradeDirection,
                          boolean confirmedRegimeReversal, boolean bidirectionalTrading) {
        public Context(String stage, int roundNo, double targetPositionPct, double currentPositionPct,
                       long currentShares, BigDecimal availableCash, BigDecimal totalAssets,
                       BigDecimal roundStartAssets, BigDecimal usedRoundTurnoverValue, BigDecimal price,
                       String urgency, Integer lastBuyRound, String roundTradeDirection,
                       boolean thesisInvalidated, boolean criticalRisk, BigDecimal marketLiquidityShares,
                       int participantCount, BigDecimal marketMaxOrderImpactPct,
                       Integer lastTradeRound, String lastTradeDirection, boolean confirmedRegimeReversal) {
            this(stage, roundNo, targetPositionPct, currentPositionPct, currentShares, availableCash,
                    totalAssets, roundStartAssets, usedRoundTurnoverValue, price, urgency, lastBuyRound,
                    roundTradeDirection, thesisInvalidated, criticalRisk, marketLiquidityShares,
                    participantCount, marketMaxOrderImpactPct, lastTradeRound, lastTradeDirection,
                    confirmedRegimeReversal, false);
        }
        public Context(String stage, int roundNo, double targetPositionPct, double currentPositionPct,
                       long currentShares, BigDecimal availableCash, BigDecimal totalAssets,
                       BigDecimal roundStartAssets, BigDecimal usedRoundTurnoverValue, BigDecimal price,
                       String urgency, Integer lastBuyRound, String roundTradeDirection,
                       boolean thesisInvalidated, boolean criticalRisk, BigDecimal marketLiquidityShares,
                       int participantCount, BigDecimal marketMaxOrderImpactPct) {
            this(stage, roundNo, targetPositionPct, currentPositionPct, currentShares, availableCash,
                    totalAssets, roundStartAssets, usedRoundTurnoverValue, price, urgency, lastBuyRound,
                    roundTradeDirection, thesisInvalidated, criticalRisk, marketLiquidityShares,
                    participantCount, marketMaxOrderImpactPct, null, null, false, false);
        }
    }

    public record ExecutionPlan(String direction, long shares, double targetPositionPct,
                                double requestedTargetPositionPct,
                                BigDecimal estimatedValue, String reason) {}
}
