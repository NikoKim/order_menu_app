package com.order.menu.module.entertainment.mode.guessstock.engine;

import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class GuessStockRules {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final Pattern STOCK_CODE = Pattern.compile("[A-Z0-9]{2,10}");
    private static final Set<String> LIQUIDITY = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> DIFFICULTIES = Set.of("EASY", "NORMAL", "HARD");
    private static final Set<String> STYLES = Set.of("CONSERVATIVE", "TREND", "CONTRARIAN", "AGGRESSIVE", "RANDOM");
    private static final Set<String> EVENT_TYPES = Set.of("DEMAND", "SUPPLY", "POLICY", "FINANCIAL", "ACCIDENT", "INDUSTRY", "SENTIMENT");
    private static final Set<String> EVENT_RELEVANCE = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> EVENT_CREDIBILITY = Set.of("RUMOR", "PRELIMINARY", "CONFIRMED");
    private static final Set<String> EVENT_SURPRISE = Set.of("EXPECTED", "NORMAL", "MAJOR");
    private static final Set<String> EVENT_STRENGTH = Set.of("WEAK", "MEDIUM", "STRONG");

    private GuessStockRules() {}

    public static GuessStockConfig validate(GuessStockConfig config) {
        if (config == null) bad("缺少猜股票配置");
        String code = config.stockCode() == null ? "" : config.stockCode().trim().toUpperCase();
        if (!STOCK_CODE.matcher(code).matches()) bad("股票代码需为 2～10 位大写字母或数字");
        if (config.stockType() == null || config.stockType().isBlank() || config.stockType().trim().length() > 30) {
            bad("股票类型不能为空且最多 30 字");
        }
        int rounds = value(config.roundCount(), 0);
        if (rounds < 3 || rounds > 200) bad("回合数需在 3～200 之间");
        int reveal = value(config.revealSeconds(), 1);
        if (reveal != 1) bad("揭晓阶段固定为 1 秒");
        int trading = value(config.tradingSeconds(), 0);
        if (trading < 3 || trading > 300) bad("交易阶段需在 3～300 秒之间");
        positive(config.initialCash(), "初始金额");
        positive(config.initialPrice(), "初始价格");
        between(config.baseVolatilityPercent(), ZERO, new BigDecimal("20"), "基础波动率");
        String liquidity = config.liquidity() == null ? "" : config.liquidity().toUpperCase();
        if (!LIQUIDITY.contains(liquidity)) bad("市场流动性必须为 LOW、MEDIUM 或 HIGH");
        BigDecimal liquidityShares = config.marketLiquidityShares() == null
                ? defaultLiquidityShares(liquidity) : config.marketLiquidityShares();
        if (liquidityShares.compareTo(new BigDecimal("100")) < 0
                || liquidityShares.compareTo(new BigDecimal("10000000")) > 0) {
            bad("市场流动性参数需在 100～10000000 股之间");
        }
        between(config.maxOrderImpactPercent(), ZERO, new BigDecimal("20"), "订单流影响上限");
        long maxBuyQuantity = config.maxBuyQuantity() == null ? 1_000L : config.maxBuyQuantity();
        if (maxBuyQuantity < 1 || maxBuyQuantity > 10_000_000L) bad("单次最大买入需在 1～10000000 股之间");
        String progressionMode = config.progressionMode() == null ? "AUTO" : config.progressionMode().toUpperCase();
        if (!Set.of("AUTO", "MANUAL").contains(progressionMode)) bad("回合推进模式必须为 AUTO 或 MANUAL");
        String pricingEngineVersion = config.pricingEngineVersion() == null
                ? "V1" : config.pricingEngineVersion().trim().toUpperCase();
        if (!Set.of("V1", "V2", "V3").contains(pricingEngineVersion)) bad("价格引擎版本必须为 V1、V2 或 V3");
        boolean multiplayerEnabled = Boolean.TRUE.equals(config.multiplayerEnabled());
        int maxHumanPlayers = config.maxHumanPlayers() == null ? 8 : config.maxHumanPlayers();
        if (maxHumanPlayers < 2 || maxHumanPlayers > 8) bad("多人房间人数需在 2～8 之间");

        boolean aiEnabled = Boolean.TRUE.equals(config.aiEnabled());
        List<GuessStockConfig.AiPlayerConfig> requestedAiPlayers = config.aiPlayers() == null ? List.of() : config.aiPlayers();
        int aiCount = requestedAiPlayers.isEmpty() ? value(config.aiCount(), 0) : requestedAiPlayers.size();
        if (aiEnabled && aiCount < 1) bad("启用 AI 时至少需要 1 个 AI");
        if (!aiEnabled && aiCount != 0) bad("未启用 AI 时数量必须为 0");
        String difficulty = config.aiDifficulty() == null ? "NORMAL" : config.aiDifficulty().toUpperCase();
        if (!DIFFICULTIES.contains(difficulty)) bad("不支持的 AI 难度");
        List<String> styles = requestedAiPlayers.isEmpty()
                ? (config.aiStyles() == null ? List.of() : config.aiStyles())
                : requestedAiPlayers.stream().map(ai -> ai == null || ai.style() == null ? "RANDOM" : ai.style())
                .map(String::toUpperCase).distinct().toList();
        if (aiEnabled && styles.isEmpty()) bad("启用 AI 时至少选择一种 AI 风格");
        Set<String> normalizedStyles = new LinkedHashSet<>();
        for (String style : styles) {
            String normalized = style == null ? "" : style.toUpperCase();
            if (!STYLES.contains(normalized)) bad("不支持的 AI 风格");
            normalizedStyles.add(normalized);
        }
        if (normalizedStyles.size() != styles.size()) bad("AI 风格不能重复");

        List<GuessStockConfig.AiPlayerConfig> aiPlayers = new ArrayList<>();
        if (aiEnabled) {
            if (requestedAiPlayers.isEmpty()) {
                for (int index = 0; index < aiCount; index++) {
                    String style = styles.get(index % styles.size()).toUpperCase();
                    aiPlayers.add(defaultAiPlayer(index, style, config.initialCash(), config.maxBuyQuantity()));
                }
            } else {
                for (int index = 0; index < requestedAiPlayers.size(); index++) {
                    GuessStockConfig.AiPlayerConfig ai = requestedAiPlayers.get(index);
                    if (ai == null) bad("AI 配置不能为空");
                    String name = ai.name() == null || ai.name().isBlank() ? "AI " + (index + 1) : ai.name().trim();
                    if (name.length() > 30) bad("AI 名称最多 30 字");
                    String style = ai.style() == null ? "RANDOM" : ai.style().toUpperCase();
                    if (!STYLES.contains(style)) bad("不支持的 AI 风格");
                    BigDecimal initialCash = ai.initialCash() == null ? config.initialCash() : ai.initialCash();
                    positive(initialCash, "AI 初始资金");
                    long maxOrderQuantity = ai.maxOrderQuantity() == null ? maxBuyQuantity : ai.maxOrderQuantity();
                    if (maxOrderQuantity < 1 || maxOrderQuantity > 10_000_000L) bad("AI 单次订单上限需在 1～10000000 股之间");
                    BigDecimal activity = ai.activityPercent() == null ? new BigDecimal("70") : ai.activityPercent();
                    BigDecimal risk = ai.riskPercent() == null ? new BigDecimal("50") : ai.riskPercent();
                    between(activity, ZERO, ONE_HUNDRED, "AI 交易活跃度");
                    between(risk, ZERO, new BigDecimal("300"), "AI 最大股票敞口");
                    String strategy = ai.strategyPrompt() == null || ai.strategyPrompt().isBlank()
                            ? null : ai.strategyPrompt().trim();
                    if (strategy != null && strategy.length() > 300) bad("AI 自定义策略最多 300 字");
                    GuardDefaults defaults = guardDefaults(style);
                    BigDecimal rebalanceBand = ai.rebalanceBandPctPoints() == null
                            ? defaults.rebalanceBandPctPoints() : ai.rebalanceBandPctPoints();
                    int minHoldRounds = ai.minHoldRounds() == null ? defaults.minHoldRounds() : ai.minHoldRounds();
                    BigDecimal minTradeAsset = ai.minTradeAssetPct() == null
                            ? new BigDecimal("2") : ai.minTradeAssetPct();
                    BigDecimal maxRoundTurnover = ai.maxRoundTurnoverPct() == null
                            ? defaults.maxRoundTurnoverPct() : ai.maxRoundTurnoverPct();
                    BigDecimal maxEstimatedImpact = ai.maxEstimatedImpactPct() == null
                            ? new BigDecimal("2") : ai.maxEstimatedImpactPct();
                    BigDecimal maxPositionStep = ai.maxPositionStepPctPoints() == null
                            ? new BigDecimal("15") : ai.maxPositionStepPctPoints();
                    BigDecimal highUrgencyStep = ai.highUrgencyPositionStepPctPoints() == null
                            ? new BigDecimal("30") : ai.highUrgencyPositionStepPctPoints();
                    int reverseCooldown = ai.reverseCooldownRounds() == null ? 2 : ai.reverseCooldownRounds();
                    between(rebalanceBand, new BigDecimal("0.5"), new BigDecimal("20"), "AI 调仓缓冲");
                    if (minHoldRounds < 0 || minHoldRounds > 20) bad("AI 最短持有回合需在 0～20 之间");
                    between(minTradeAsset, ZERO, new BigDecimal("20"), "AI 最小交易金额比例");
                    between(maxRoundTurnover, new BigDecimal("1"), ONE_HUNDRED, "AI 单回合换手上限");
                    between(maxEstimatedImpact, new BigDecimal("0.1"), new BigDecimal("20"), "AI 最大预估冲击");
                    between(maxPositionStep, new BigDecimal("1"), ONE_HUNDRED, "AI 最大仓位步长");
                    between(highUrgencyStep, maxPositionStep, ONE_HUNDRED, "AI 高紧急度仓位步长");
                    if (reverseCooldown < 0 || reverseCooldown > 20) bad("AI 反向交易冷却需在 0～20 回合之间");
                    aiPlayers.add(new GuessStockConfig.AiPlayerConfig(name, style, money(initialCash),
                            maxOrderQuantity, rate(activity), rate(risk), strategy, rate(rebalanceBand),
                            minHoldRounds, rate(minTradeAsset), rate(maxRoundTurnover), rate(maxEstimatedImpact),
                            rate(maxPositionStep), rate(highUrgencyStep), reverseCooldown));
                }
            }
        }

        List<GuessStockConfig.EventConfig> events = config.events() == null ? List.of() : config.events();
        List<GuessStockConfig.EventConfig> normalizedEvents = new ArrayList<>();
        for (GuessStockConfig.EventConfig event : events) {
            if (event == null) bad("事件配置不能为空");
            if (event.name() == null || event.name().trim().length() < 2 || event.name().trim().length() > 50) {
                bad("事件名称需为 2～50 字");
            }
            if (event.description() != null && event.description().length() > 500) bad("事件介绍最多 500 字");
            between(event.occurrenceProbability(), ZERO, ONE_HUNDRED, "事件发生概率");
            between(event.effectProbability(), ZERO, ONE_HUNDRED, "事件生效概率");
            String direction = event.direction() == null ? "" : event.direction().toUpperCase();
            if (!Set.of("UP", "DOWN", "RANDOM").contains(direction)) bad("不支持的事件影响方向");
            between(event.minImpactPercent(), ZERO, new BigDecimal("50"), "事件最小影响幅度");
            between(event.maxImpactPercent(), ZERO, new BigDecimal("50"), "事件最大影响幅度");
            if (event.minImpactPercent().compareTo(event.maxImpactPercent()) > 0) bad("事件最小影响幅度不能大于最大影响幅度");
            String eventType = enumValue(event.eventType(), "INDUSTRY", EVENT_TYPES, "不支持的事件类型");
            String relevance = enumValue(event.relevance(), "MEDIUM", EVENT_RELEVANCE, "不支持的事件相关度");
            String credibility = enumValue(event.credibility(), "CONFIRMED", EVENT_CREDIBILITY, "不支持的事件可信度");
            String surprise = enumValue(event.surprise(), "NORMAL", EVENT_SURPRISE, "不支持的事件意外程度");
            String strength = enumValue(event.strength(), "MEDIUM", EVENT_STRENGTH, "不支持的事件强度");
            BigDecimal candidateWeight = event.candidateWeight() == null ? BigDecimal.ONE : event.candidateWeight();
            between(candidateWeight, new BigDecimal("0.01"), new BigDecimal("1000"), "事件候选权重");
            String chainKey = event.chainKey() == null || event.chainKey().isBlank() ? null
                    : event.chainKey().trim().toUpperCase();
            int chainStep = event.chainStep() == null ? 0 : event.chainStep();
            int maxChainDepth = event.maxChainDepth() == null ? 4 : event.maxChainDepth();
            if (chainStep < 0 || chainStep > 20 || maxChainDepth < 1 || maxChainDepth > 20) {
                bad("事件链步骤和最大深度无效");
            }
            BigDecimal followUpBoost = event.followUpProbabilityBoost() == null
                    ? ZERO : event.followUpProbabilityBoost();
            between(followUpBoost, ZERO, ONE_HUNDRED, "后续事件概率加成");
            normalizedEvents.add(new GuessStockConfig.EventConfig(event.name().trim(),
                    event.description() == null || event.description().isBlank() ? null : event.description().trim(),
                    rate(event.occurrenceProbability()), rate(event.effectProbability()), direction,
                    rate(event.minImpactPercent()), rate(event.maxImpactPercent()), eventType, relevance,
                    credibility, surprise, strength, !Boolean.FALSE.equals(event.enabled()), rate(candidateWeight),
                    chainKey, chainStep, safeTags(event.parentTags()), safeTags(event.followUpTags()),
                    rate(followUpBoost), maxChainDepth));
        }
        boolean loanEnabled = Boolean.TRUE.equals(config.loanEnabled());
        int creditRulesVersion = config.creditRulesVersion() == null ? 1 : config.creditRulesVersion();
        if (creditRulesVersion != 1) bad("暂不支持该贷款规则版本");
        List<GuessStockConfig.CreditProductConfig> requestedProducts = config.creditProducts() == null
                || config.creditProducts().isEmpty() ? defaultCreditProducts() : config.creditProducts();
        List<GuessStockConfig.CreditProductConfig> creditProducts = loanEnabled
                ? normalizeCreditProducts(requestedProducts) : List.of();
        boolean creditPhaseTimed = Boolean.TRUE.equals(config.creditPhaseTimed());
        int creditPhaseSeconds = phaseSeconds(config.creditPhaseSeconds(), "银行贷款阶段");
        boolean repaymentPhaseTimed = Boolean.TRUE.equals(config.repaymentPhaseTimed());
        int repaymentPhaseSeconds = phaseSeconds(config.repaymentPhaseSeconds(), "银行还贷阶段");
        String creditInteractionMode = enumValue(config.creditInteractionMode(), "PHASED",
                Set.of("PHASED", "INLINE"), "不支持的贷款交互模式");
        String scenarioPreset = enumValue(config.scenarioPreset(), "CUSTOM",
                Set.of("CUSTOM", "QUICK", "STANDARD", "LONG", "HIGH_VOLATILITY", "CREDIT_CRISIS"),
                "不支持的场景预设");
        boolean fundamentalEnabled = "V3".equals(pricingEngineVersion) || Boolean.TRUE.equals(config.fundamentalEnabled());
        int reportIntervalRounds = config.reportIntervalRounds() == null ? 5 : config.reportIntervalRounds();
        if (reportIntervalRounds < 2 || reportIntervalRounds > 50) bad("财报间隔需在 2～50 回合之间");
        GuessStockConfig.FundamentalConfig fundamentals = normalizeFundamentals(config.initialFundamentals());
        BigDecimal fundamentalSensitivity = config.fundamentalSensitivity() == null
                ? new BigDecimal("0.35") : config.fundamentalSensitivity();
        between(fundamentalSensitivity, ZERO, new BigDecimal("2"), "基本面敏感度");
        int researchPoints = config.researchPoints() == null ? 3 : config.researchPoints();
        if (researchPoints < 0 || researchPoints > 20) bad("研究点需在 0～20 之间");
        String tradingMode = enumValue(config.tradingMode(), "CLASSIC",
                Set.of("CLASSIC", "TARGET_AUCTION"), "不支持的交易模式");
        BigDecimal feePercent = config.feePercent() == null ? new BigDecimal("0.05") : config.feePercent();
        BigDecimal slippagePercent = config.slippagePercent() == null ? new BigDecimal("0.05") : config.slippagePercent();
        between(feePercent, ZERO, new BigDecimal("2"), "交易手续费");
        between(slippagePercent, ZERO, new BigDecimal("2"), "交易滑点");
        return new GuessStockConfig(code, config.stockType().trim(), rounds, reveal, trading,
                money(config.initialCash()), price(config.initialPrice()), rate(config.baseVolatilityPercent()),
                liquidity, money(liquidityShares), rate(config.maxOrderImpactPercent()), maxBuyQuantity,
                progressionMode, multiplayerEnabled,
                maxHumanPlayers, aiEnabled, aiEnabled ? aiCount : 0,
                difficulty, normalizedStyles.stream().toList(), aiPlayers, normalizedEvents, pricingEngineVersion,
                loanEnabled, creditRulesVersion, creditProducts, creditPhaseTimed, creditPhaseSeconds,
                repaymentPhaseTimed, repaymentPhaseSeconds, creditInteractionMode, scenarioPreset,
                fundamentalEnabled, reportIntervalRounds, fundamentals, rate(fundamentalSensitivity), researchPoints,
                tradingMode, rate(feePercent), rate(slippagePercent), Boolean.TRUE.equals(config.dailyChallengeEnabled()));
    }

    private static GuessStockConfig.FundamentalConfig normalizeFundamentals(
            GuessStockConfig.FundamentalConfig value) {
        if (value == null) return new GuessStockConfig.FundamentalConfig(new BigDecimal("100"),
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"));
        return new GuessStockConfig.FundamentalConfig(
                fundamental(value.demandIndex(), "需求指数"), fundamental(value.orderIndex(), "订单指数"),
                fundamental(value.inventoryIndex(), "库存指数"), fundamental(value.marginIndex(), "利润率指数"),
                fundamental(value.cashFlowIndex(), "现金流指数"));
    }

    private static BigDecimal fundamental(BigDecimal value, String label) {
        BigDecimal normalized = value == null ? new BigDecimal("100") : value;
        between(normalized, ZERO, new BigDecimal("200"), label);
        return rate(normalized);
    }

    private static int phaseSeconds(Integer value, String label) {
        int seconds = value == null ? 30 : value;
        if (seconds < 3 || seconds > 600) bad(label + "时长需在 3～600 秒之间");
        return seconds;
    }

    private static List<GuessStockConfig.CreditProductConfig> normalizeCreditProducts(
            List<GuessStockConfig.CreditProductConfig> products) {
        if (products.isEmpty()) bad("开启银行贷款后至少需要一个授信产品");
        Set<String> codes = new LinkedHashSet<>();
        List<GuessStockConfig.CreditProductConfig> result = new ArrayList<>();
        for (GuessStockConfig.CreditProductConfig product : products) {
            if (product == null) bad("授信产品不能为空");
            String code = product.code() == null ? "" : product.code().trim().toUpperCase();
            if (!code.matches("[A-Z0-9_]{2,30}") || !codes.add(code)) bad("授信产品编码无效或重复");
            String name = product.name() == null ? "" : product.name().trim();
            if (name.isEmpty() || name.length() > 40) bad("银行名称需为 1～40 字");
            String risk = enumValue(product.riskLevel(), "MEDIUM", Set.of("LOW", "MEDIUM", "HIGH", "EXTREME"),
                    "银行风险等级无效");
            between(product.maxInitialCapitalRatio(), ZERO, new BigDecimal("200"), "初始资金额度比例");
            between(product.maxDebtToEquity(), ZERO, new BigDecimal("200"), "债务净资产比例");
            between(product.maxExposureRatio(), ONE_HUNDRED, new BigDecimal("300"), "最大股票敞口");
            between(product.interestRatePerRound(), ZERO, new BigDecimal("10"), "每回合利率");
            between(product.minBorrowRatio(), ZERO, ONE_HUNDRED, "最低借款比例");
            between(product.collateralHaircut(), ZERO, new BigDecimal("50"), "抵押折扣");
            between(product.originationLtv(), ZERO, new BigDecimal("95"), "放款LTV");
            between(product.warningLtv(), ZERO, new BigDecimal("95"), "警戒LTV");
            between(product.blockLtv(), ZERO, new BigDecimal("95"), "禁借LTV");
            between(product.liquidationLtv(), ZERO, new BigDecimal("95"), "强平LTV");
            between(product.recoveryLtv(), ZERO, new BigDecimal("95"), "恢复LTV");
            if (!(product.originationLtv().compareTo(product.warningLtv()) < 0
                    && product.warningLtv().compareTo(product.blockLtv()) < 0
                    && product.blockLtv().compareTo(product.liquidationLtv()) < 0)) {
                bad("银行LTV必须满足放款 < 警戒 < 禁借 < 强平");
            }
            if (product.recoveryLtv().compareTo(product.warningLtv()) >= 0) bad("强平恢复LTV必须低于警戒LTV");
            int maturity = value(product.maturityRounds(), 0);
            int cooldown = value(product.borrowCooldownRounds(), 0);
            if (maturity < 1 || maturity > 50) bad("贷款期限需在 1～50 回合之间");
            if (cooldown < 0 || cooldown > 20) bad("借款冷却需在 0～20 回合之间");
            result.add(new GuessStockConfig.CreditProductConfig(code, name, risk,
                    !Boolean.FALSE.equals(product.enabled()), rate(product.maxInitialCapitalRatio()),
                    rate(product.maxDebtToEquity()), rate(product.maxExposureRatio()),
                    rate(product.interestRatePerRound()), maturity, rate(product.minBorrowRatio()), cooldown,
                    rate(product.collateralHaircut()), rate(product.originationLtv()), rate(product.warningLtv()),
                    rate(product.blockLtv()), rate(product.liquidationLtv()), rate(product.recoveryLtv())));
        }
        if (result.stream().noneMatch(item -> Boolean.TRUE.equals(item.enabled()))) bad("至少需要启用一个授信产品");
        return List.copyOf(result);
    }

    public static List<GuessStockConfig.CreditProductConfig> defaultCreditProducts() {
        return List.of(
                credit("CONSERVATIVE_BANK", "稳健抵押银行", "LOW", true, 50, 50, 150, .4, 3, 5, 1, 5, 36, 42, 47, 52, 38),
                credit("GROWTH_BANK", "进取杠杆银行", "MEDIUM", true, 100, 100, 200, .8, 3, 5, 1, 7, 56, 60, 64, 68, 56),
                credit("HIGH_RISK_BANK", "高风险杠杆银行", "HIGH", true, 150, 150, 250, 1.2, 2, 10, 1, 8, 66, 70, 73, 76, 66),
                credit("EXTREME_BANK", "极端杠杆银行", "EXTREME", false, 200, 200, 300, 1.8, 2, 10, 1, 10, 75, 78, 81, 84, 74));
    }

    private static GuessStockConfig.CreditProductConfig credit(String code, String name, String risk, boolean enabled,
            double initial, double debt, double exposure, double rate, int maturity, double minimum, int cooldown,
            double haircut, double origination, double warning, double block, double liquidation, double recovery) {
        return new GuessStockConfig.CreditProductConfig(code, name, risk, enabled, BigDecimal.valueOf(initial),
                BigDecimal.valueOf(debt), BigDecimal.valueOf(exposure), BigDecimal.valueOf(rate), maturity,
                BigDecimal.valueOf(minimum), cooldown, BigDecimal.valueOf(haircut), BigDecimal.valueOf(origination),
                BigDecimal.valueOf(warning), BigDecimal.valueOf(block), BigDecimal.valueOf(liquidation),
                BigDecimal.valueOf(recovery));
    }

    private static String enumValue(String value, String fallback, Set<String> allowed, String message) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
        if (!allowed.contains(normalized)) bad(message);
        return normalized;
    }

    private static List<String> safeTags(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(java.util.Objects::nonNull).map(String::trim)
                .filter(value -> !value.isBlank()).map(String::toUpperCase).distinct().limit(10).toList();
    }

    private static GuessStockConfig.AiPlayerConfig defaultAiPlayer(int index, String style,
                                                                    BigDecimal initialCash, Long maxBuyQuantity) {
        String label = switch (style) {
            case "CONSERVATIVE" -> "稳健"; case "TREND" -> "趋势"; case "CONTRARIAN" -> "逆势";
            case "AGGRESSIVE" -> "激进"; default -> "随机";
        };
        BigDecimal activity = "CONSERVATIVE".equals(style) ? new BigDecimal("55")
                : "AGGRESSIVE".equals(style) ? new BigDecimal("90") : new BigDecimal("72");
        BigDecimal risk = "CONSERVATIVE".equals(style) ? new BigDecimal("40")
                : "TREND".equals(style) ? new BigDecimal("65")
                : "CONTRARIAN".equals(style) ? new BigDecimal("60")
                : "AGGRESSIVE".equals(style) ? new BigDecimal("85") : new BigDecimal("70");
        GuardDefaults guards = guardDefaults(style);
        return new GuessStockConfig.AiPlayerConfig(label + " AI " + (index + 1), style,
                money(initialCash), maxBuyQuantity == null ? 1_000L : maxBuyQuantity,
                rate(activity), rate(risk), null, guards.rebalanceBandPctPoints(), guards.minHoldRounds(),
                new BigDecimal("2.0000"), guards.maxRoundTurnoverPct(), new BigDecimal("2.0000"),
                new BigDecimal("15.0000"), new BigDecimal("30.0000"), 2);
    }

    private static GuardDefaults guardDefaults(String style) {
        return switch (style) {
            case "CONSERVATIVE" -> new GuardDefaults(new BigDecimal("7"), 2, new BigDecimal("20"));
            case "TREND" -> new GuardDefaults(new BigDecimal("5"), 1, new BigDecimal("30"));
            case "CONTRARIAN" -> new GuardDefaults(new BigDecimal("5"), 1, new BigDecimal("25"));
            case "AGGRESSIVE" -> new GuardDefaults(new BigDecimal("3"), 0, new BigDecimal("40"));
            default -> new GuardDefaults(new BigDecimal("5"), 1, new BigDecimal("30"));
        };
    }

    private record GuardDefaults(BigDecimal rebalanceBandPctPoints, int minHoldRounds,
                                 BigDecimal maxRoundTurnoverPct) {}

    public static StagePricingResult openingPrice(BigDecimal previousClose, long totalBuyQuantity,
                                                   BigDecimal baseChangePercent, BigDecimal eventChangePercent,
                                                   BigDecimal liquidityShares,
                                                   int participantCount, BigDecimal maxOrderImpactPercent) {
        BigDecimal flow = quantityImpact(totalBuyQuantity, liquidityShares, participantCount, maxOrderImpactPercent);
        return stagePrice(previousClose, baseChangePercent.add(eventChangePercent).add(flow), flow);
    }

    public static StagePricingResult closingPrice(BigDecimal openingPrice, long totalSellQuantity,
                                                   BigDecimal baseChangePercent, BigDecimal eventChangePercent,
                                                   BigDecimal meanReversionChangePercent,
                                                   BigDecimal liquidityShares, int participantCount,
                                                   BigDecimal maxOrderImpactPercent) {
        BigDecimal sellImpact = quantityImpact(totalSellQuantity, liquidityShares, participantCount, maxOrderImpactPercent);
        return stagePrice(openingPrice, baseChangePercent.add(eventChangePercent)
                .add(meanReversionChangePercent).subtract(sellImpact), sellImpact.negate());
    }

    /** A small pull toward the previous close prevents one-sided drift without fixing the outcome. */
    public static BigDecimal meanReversionPercent(BigDecimal openingPrice, BigDecimal previousClose,
                                                   BigDecimal maximumPercent) {
        if (openingPrice == null || previousClose == null || openingPrice.signum() <= 0
                || previousClose.signum() <= 0 || maximumPercent == null || maximumPercent.signum() <= 0) {
            return ZERO.setScale(4);
        }
        double distancePercent = Math.log(openingPrice.doubleValue() / previousClose.doubleValue()) * 100.0;
        double pull = Math.max(-maximumPercent.doubleValue(), Math.min(maximumPercent.doubleValue(), -distancePercent * 0.18));
        return rate(BigDecimal.valueOf(pull));
    }

    public static long maxBuyQuantity(BigDecimal availableCash, BigDecimal previousClose) {
        if (availableCash == null || previousClose == null || availableCash.signum() < 0 || previousClose.signum() <= 0) {
            return 0L;
        }
        return availableCash.divide(previousClose, 0, RoundingMode.DOWN).longValue();
    }

    private static StagePricingResult stagePrice(BigDecimal referencePrice, BigDecimal totalChange,
                                                  BigDecimal orderFlowChange) {
        // Treat all components as log returns. Equal positive/negative shocks are symmetric,
        // the price always remains positive, and large configured events cannot cross zero.
        double multiplier = Math.exp(totalChange.doubleValue() / 100.0);
        BigDecimal result = referencePrice.multiply(BigDecimal.valueOf(multiplier)).max(MIN_PRICE);
        return new StagePricingResult(price(result), rate(orderFlowChange), rate(totalChange));
    }

    private static BigDecimal quantityImpact(long quantity, BigDecimal liquidityShares, int participantCount,
                                             BigDecimal maxOrderImpactPercent) {
        double depth = liquidityShares.doubleValue() * Math.max(1, participantCount);
        double pressure = Math.max(0, quantity) / depth;
        // tanh is near-linear for ordinary flow and approaches the configured cap smoothly.
        return rate(BigDecimal.valueOf(maxOrderImpactPercent.doubleValue() * Math.tanh(pressure)));
    }

    public static BigDecimal orderFlowImpactPercent(long quantity, BigDecimal liquidityShares,
                                                    int participantCount, BigDecimal maxOrderImpactPercent) {
        return quantityImpact(quantity, liquidityShares, participantCount, maxOrderImpactPercent);
    }

    private static BigDecimal defaultLiquidityShares(String liquidity) {
        return switch (liquidity) {
            case "LOW" -> new BigDecimal("500");
            case "HIGH" -> new BigDecimal("2500");
            default -> new BigDecimal("1000");
        };
    }

    public static BigDecimal money(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
    public static BigDecimal price(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
    public static BigDecimal rate(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }

    private static int value(Integer value, int fallback) { return value == null ? fallback : value; }
    private static void positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) bad(name + "必须大于 0");
        if (value.compareTo(new BigDecimal("10000000")) > 0) bad(name + "不能超过 10000000");
    }
    private static void between(BigDecimal value, BigDecimal min, BigDecimal max, String name) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            bad(name + "需在 " + min.stripTrailingZeros().toPlainString() + "～" + max.stripTrailingZeros().toPlainString() + " 之间");
        }
    }
    private static void bad(String message) { throw new BusinessException(ErrorCode.BAD_REQUEST, message); }

    public record StagePricingResult(BigDecimal price, BigDecimal orderFlowChangePercent,
                                     BigDecimal totalChangePercent) {}
}
