package com.order.menu.module.entertainment.mode.guessstock.config;

import java.math.BigDecimal;
import java.util.List;

public record GuessStockConfig(
        String stockCode,
        String stockType,
        Integer roundCount,
        Integer revealSeconds,
        Integer tradingSeconds,
        BigDecimal initialCash,
        BigDecimal initialPrice,
        BigDecimal baseVolatilityPercent,
        String liquidity,
        BigDecimal marketLiquidityShares,
        BigDecimal maxOrderImpactPercent,
        Long maxBuyQuantity,
        String progressionMode,
        Boolean multiplayerEnabled,
        Integer maxHumanPlayers,
        Boolean aiEnabled,
        Integer aiCount,
        String aiDifficulty,
        List<String> aiStyles,
        List<AiPlayerConfig> aiPlayers,
        List<EventConfig> events,
        String pricingEngineVersion,
        Boolean loanEnabled,
        Integer creditRulesVersion,
        List<CreditProductConfig> creditProducts,
        Boolean creditPhaseTimed,
        Integer creditPhaseSeconds,
        Boolean repaymentPhaseTimed,
        Integer repaymentPhaseSeconds,
        String creditInteractionMode,
        String scenarioPreset,
        Boolean fundamentalEnabled,
        Integer reportIntervalRounds,
        FundamentalConfig initialFundamentals,
        BigDecimal fundamentalSensitivity,
        Integer researchPoints,
        String tradingMode,
        BigDecimal feePercent,
        BigDecimal slippagePercent,
        Boolean dailyChallengeEnabled
) {
    public GuessStockConfig(String stockCode, String stockType, Integer roundCount, Integer revealSeconds,
                            Integer tradingSeconds, BigDecimal initialCash, BigDecimal initialPrice,
                            BigDecimal baseVolatilityPercent, String liquidity, BigDecimal maxOrderImpactPercent,
                            String progressionMode, Boolean multiplayerEnabled, Integer maxHumanPlayers,
                            Boolean aiEnabled, Integer aiCount, String aiDifficulty, List<String> aiStyles,
                            List<EventConfig> events) {
        this(stockCode, stockType, roundCount, revealSeconds, tradingSeconds, initialCash, initialPrice,
                baseVolatilityPercent, liquidity, null, maxOrderImpactPercent, null, progressionMode,
                multiplayerEnabled, maxHumanPlayers, aiEnabled, aiCount, aiDifficulty, aiStyles, null, events, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    public GuessStockConfig(String stockCode, String stockType, Integer roundCount, Integer revealSeconds,
                            Integer tradingSeconds, BigDecimal initialCash, BigDecimal initialPrice,
                            BigDecimal baseVolatilityPercent, String liquidity, BigDecimal marketLiquidityShares,
                            BigDecimal maxOrderImpactPercent, Long maxBuyQuantity, String progressionMode,
                            Boolean multiplayerEnabled, Integer maxHumanPlayers, Boolean aiEnabled, Integer aiCount,
                            String aiDifficulty, List<String> aiStyles, List<AiPlayerConfig> aiPlayers,
                            List<EventConfig> events) {
        this(stockCode, stockType, roundCount, revealSeconds, tradingSeconds, initialCash, initialPrice,
                baseVolatilityPercent, liquidity, marketLiquidityShares, maxOrderImpactPercent, maxBuyQuantity,
                progressionMode, multiplayerEnabled, maxHumanPlayers, aiEnabled, aiCount, aiDifficulty,
                aiStyles, aiPlayers, events, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    public GuessStockConfig(String stockCode, String stockType, Integer roundCount, Integer revealSeconds,
                            Integer tradingSeconds, BigDecimal initialCash, BigDecimal initialPrice,
                            BigDecimal baseVolatilityPercent, String liquidity, BigDecimal marketLiquidityShares,
                            BigDecimal maxOrderImpactPercent, Long maxBuyQuantity, String progressionMode,
                            Boolean multiplayerEnabled, Integer maxHumanPlayers, Boolean aiEnabled, Integer aiCount,
                            String aiDifficulty, List<String> aiStyles, List<AiPlayerConfig> aiPlayers,
                            List<EventConfig> events, String pricingEngineVersion, Boolean loanEnabled,
                            Integer creditRulesVersion, List<CreditProductConfig> creditProducts) {
        this(stockCode, stockType, roundCount, revealSeconds, tradingSeconds, initialCash, initialPrice,
                baseVolatilityPercent, liquidity, marketLiquidityShares, maxOrderImpactPercent, maxBuyQuantity,
                progressionMode, multiplayerEnabled, maxHumanPlayers, aiEnabled, aiCount, aiDifficulty,
                aiStyles, aiPlayers, events, pricingEngineVersion, loanEnabled, creditRulesVersion, creditProducts,
                null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    public GuessStockConfig(String stockCode, String stockType, Integer roundCount, Integer revealSeconds,
                            Integer tradingSeconds, BigDecimal initialCash, BigDecimal initialPrice,
                            BigDecimal baseVolatilityPercent, String liquidity, BigDecimal marketLiquidityShares,
                            BigDecimal maxOrderImpactPercent, Long maxBuyQuantity, String progressionMode,
                            Boolean multiplayerEnabled, Integer maxHumanPlayers, Boolean aiEnabled, Integer aiCount,
                            String aiDifficulty, List<String> aiStyles, List<AiPlayerConfig> aiPlayers,
                            List<EventConfig> events, String pricingEngineVersion, Boolean loanEnabled,
                            Integer creditRulesVersion, List<CreditProductConfig> creditProducts,
                            Boolean creditPhaseTimed, Integer creditPhaseSeconds,
                            Boolean repaymentPhaseTimed, Integer repaymentPhaseSeconds) {
        this(stockCode, stockType, roundCount, revealSeconds, tradingSeconds, initialCash, initialPrice,
                baseVolatilityPercent, liquidity, marketLiquidityShares, maxOrderImpactPercent, maxBuyQuantity,
                progressionMode, multiplayerEnabled, maxHumanPlayers, aiEnabled, aiCount, aiDifficulty,
                aiStyles, aiPlayers, events, pricingEngineVersion, loanEnabled, creditRulesVersion, creditProducts,
                creditPhaseTimed, creditPhaseSeconds, repaymentPhaseTimed, repaymentPhaseSeconds,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    public record FundamentalConfig(BigDecimal demandIndex, BigDecimal orderIndex,
                                    BigDecimal inventoryIndex, BigDecimal marginIndex,
                                    BigDecimal cashFlowIndex) {}

    public record AiPlayerConfig(
            String name,
            String style,
            BigDecimal initialCash,
            Long maxOrderQuantity,
            BigDecimal activityPercent,
            BigDecimal riskPercent,
            String strategyPrompt,
            BigDecimal rebalanceBandPctPoints,
            Integer minHoldRounds,
            BigDecimal minTradeAssetPct,
            BigDecimal maxRoundTurnoverPct,
            BigDecimal maxEstimatedImpactPct,
            BigDecimal maxPositionStepPctPoints,
            BigDecimal highUrgencyPositionStepPctPoints,
            Integer reverseCooldownRounds
    ) {
        public AiPlayerConfig(String name, String style, BigDecimal initialCash, Long maxOrderQuantity,
                              BigDecimal activityPercent, BigDecimal riskPercent, String strategyPrompt) {
            this(name, style, initialCash, maxOrderQuantity, activityPercent, riskPercent, strategyPrompt,
                    null, null, null, null, null, null, null, null);
        }

        public AiPlayerConfig(String name, String style, BigDecimal initialCash, Long maxOrderQuantity,
                              BigDecimal activityPercent, BigDecimal riskPercent, String strategyPrompt,
                              BigDecimal rebalanceBandPctPoints, Integer minHoldRounds,
                              BigDecimal minTradeAssetPct, BigDecimal maxRoundTurnoverPct,
                              BigDecimal maxEstimatedImpactPct) {
            this(name, style, initialCash, maxOrderQuantity, activityPercent, riskPercent, strategyPrompt,
                    rebalanceBandPctPoints, minHoldRounds, minTradeAssetPct, maxRoundTurnoverPct,
                    maxEstimatedImpactPct, null, null, null);
        }
    }

    public record EventConfig(
            String name,
            String description,
            BigDecimal occurrenceProbability,
            BigDecimal effectProbability,
            String direction,
            BigDecimal minImpactPercent,
            BigDecimal maxImpactPercent,
            String eventType,
            String relevance,
            String credibility,
            String surprise,
            String strength,
            Boolean enabled,
            BigDecimal candidateWeight,
            String chainKey,
            Integer chainStep,
            List<String> parentTags,
            List<String> followUpTags,
            BigDecimal followUpProbabilityBoost,
            Integer maxChainDepth
    ) {
        public EventConfig(String name, String description, BigDecimal occurrenceProbability,
                           BigDecimal effectProbability, String direction, BigDecimal minImpactPercent,
                           BigDecimal maxImpactPercent, String eventType, String relevance,
                           String credibility, String surprise, String strength, Boolean enabled,
                           BigDecimal candidateWeight) {
            this(name, description, occurrenceProbability, effectProbability, direction,
                    minImpactPercent, maxImpactPercent, eventType, relevance, credibility,
                    surprise, strength, enabled, candidateWeight, null, null, null, null, null, null);
        }

        public EventConfig(String name, String description, BigDecimal occurrenceProbability,
                           BigDecimal effectProbability, String direction, BigDecimal minImpactPercent,
                           BigDecimal maxImpactPercent, String eventType, String relevance,
                           String credibility, String surprise, String strength, Boolean enabled) {
            this(name, description, occurrenceProbability, effectProbability, direction,
                    minImpactPercent, maxImpactPercent, eventType, relevance, credibility,
                    surprise, strength, enabled, null, null, null, null, null, null, null);
        }

        public EventConfig(String name, String description, BigDecimal occurrenceProbability,
                           BigDecimal effectProbability, String direction, BigDecimal minImpactPercent,
                           BigDecimal maxImpactPercent, Boolean enabled) {
            this(name, description, occurrenceProbability, effectProbability, direction,
                    minImpactPercent, maxImpactPercent, "INDUSTRY", "MEDIUM",
                    "CONFIRMED", "NORMAL", "MEDIUM", enabled, null,
                    null, null, null, null, null, null);
        }
    }

    public record CreditProductConfig(
            String code,
            String name,
            String riskLevel,
            Boolean enabled,
            BigDecimal maxInitialCapitalRatio,
            BigDecimal maxDebtToEquity,
            BigDecimal maxExposureRatio,
            BigDecimal interestRatePerRound,
            Integer maturityRounds,
            BigDecimal minBorrowRatio,
            Integer borrowCooldownRounds,
            BigDecimal collateralHaircut,
            BigDecimal originationLtv,
            BigDecimal warningLtv,
            BigDecimal blockLtv,
            BigDecimal liquidationLtv,
            BigDecimal recoveryLtv
    ) {}
}
