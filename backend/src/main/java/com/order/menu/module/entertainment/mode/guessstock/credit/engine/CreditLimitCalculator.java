package com.order.menu.module.entertainment.mode.guessstock.credit.engine;

import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class CreditLimitCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private CreditLimitCalculator() {}

    public static Limit calculate(BigDecimal initialCash, BigDecimal cash, long shares, BigDecimal price,
                                  BigDecimal principal, BigDecimal interest,
                                  GuessStockConfig.CreditProductConfig product) {
        return calculate(initialCash, cash, shares, price, principal, interest, principal, interest, product);
    }

    public static Limit calculate(BigDecimal initialCash, BigDecimal cash, long shares, BigDecimal price,
                                  BigDecimal totalPrincipal, BigDecimal totalInterest,
                                  BigDecimal bankPrincipal, BigDecimal bankInterest,
                                  GuessStockConfig.CreditProductConfig product) {
        CreditRiskCalculator.Snapshot risk = CreditRiskCalculator.calculate(cash, shares, price,
                totalPrincipal, totalInterest,
                product, false);
        BigDecimal initialLimit = initialCash.multiply(product.maxInitialCapitalRatio())
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);
        BigDecimal equityLimit = risk.netAsset().max(BigDecimal.ZERO).multiply(product.maxDebtToEquity())
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);
        BigDecimal debt = value(totalPrincipal).add(value(totalInterest));
        BigDecimal bankDebt = value(bankPrincipal).add(value(bankInterest));
        BigDecimal origination = product.originationLtv().divide(HUNDRED, 10, RoundingMode.HALF_UP);
        BigDecimal ltvHeadroom;
        if (origination.compareTo(BigDecimal.ONE) >= 0) ltvHeadroom = initialLimit;
        else ltvHeadroom = origination.multiply(risk.collateralValue()).subtract(debt)
                .divide(BigDecimal.ONE.subtract(origination), 10, RoundingMode.DOWN).max(BigDecimal.ZERO);
        BigDecimal available = min(initialLimit.subtract(bankDebt), equityLimit.subtract(debt), ltvHeadroom)
                .max(BigDecimal.ZERO);
        return new Limit(money(bankDebt.add(available)), money(available), risk);
    }

    public static GuessStockConfig.CreditProductConfig route(List<GuessStockConfig.CreditProductConfig> products,
                                                              BigDecimal targetExposurePercent) {
        return products.stream().filter(item -> Boolean.TRUE.equals(item.enabled()))
                .filter(item -> item.maxExposureRatio().compareTo(targetExposurePercent) >= 0)
                .sorted(java.util.Comparator.comparing(GuessStockConfig.CreditProductConfig::interestRatePerRound)
                        .thenComparing(GuessStockConfig.CreditProductConfig::maxExposureRatio))
                .findFirst().orElseGet(() -> products.stream().filter(item -> Boolean.TRUE.equals(item.enabled()))
                        .max(java.util.Comparator.comparing(GuessStockConfig.CreditProductConfig::maxExposureRatio))
                        .orElse(null));
    }

    private static BigDecimal min(BigDecimal... values) {
        BigDecimal result = values[0];
        for (BigDecimal value : values) if (value.compareTo(result) < 0) result = value;
        return result;
    }
    private static BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static BigDecimal money(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
    public record Limit(BigDecimal maximumCredit, BigDecimal availableCredit, CreditRiskCalculator.Snapshot risk) {}
}
