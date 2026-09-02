package com.order.menu.module.entertainment.mode.guessstock.credit.engine;

import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CreditRiskCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private CreditRiskCalculator() {}

    public static Snapshot calculate(BigDecimal cash, long shares, BigDecimal price,
                                     BigDecimal principal, BigDecimal interest,
                                     GuessStockConfig.CreditProductConfig product,
                                     boolean defaulted) {
        BigDecimal safeCash = value(cash);
        BigDecimal stock = price.multiply(BigDecimal.valueOf(Math.max(0, shares)));
        BigDecimal debt = value(principal).add(value(interest));
        BigDecimal haircut = product == null ? BigDecimal.ZERO
                : product.collateralHaircut().divide(HUNDRED, 10, RoundingMode.HALF_UP);
        BigDecimal collateral = safeCash.add(stock.multiply(BigDecimal.ONE.subtract(haircut)));
        BigDecimal net = safeCash.add(stock).subtract(debt);
        BigDecimal ltv = debt.signum() == 0 ? BigDecimal.ZERO : collateral.signum() <= 0
                ? new BigDecimal("999") : debt.divide(collateral, 10, RoundingMode.HALF_UP).multiply(HUNDRED);
        BigDecimal exposure = stock.signum() == 0 ? BigDecimal.ZERO : net.signum() <= 0
                ? new BigDecimal("999") : stock.divide(net, 10, RoundingMode.HALF_UP).multiply(HUNDRED);
        String state = riskState(ltv, product, debt, defaulted);
        return new Snapshot(money(stock), money(debt), money(net), money(collateral), rate(ltv), rate(exposure), state);
    }

    private static String riskState(BigDecimal ltv, GuessStockConfig.CreditProductConfig product,
                                    BigDecimal debt, boolean defaulted) {
        if (defaulted) return "DEFAULTED";
        if (debt.signum() <= 0 || product == null) return "NORMAL";
        if (ltv.compareTo(product.liquidationLtv()) >= 0) return "LIQUIDATION";
        if (ltv.compareTo(product.blockLtv()) >= 0) return "BLOCKED";
        if (ltv.compareTo(product.warningLtv()) >= 0) return "WARNING";
        return "NORMAL";
    }

    private static BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static BigDecimal money(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
    private static BigDecimal rate(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }

    public record Snapshot(BigDecimal stockValue, BigDecimal debt, BigDecimal netAsset,
                           BigDecimal collateralValue, BigDecimal ltvPercent,
                           BigDecimal exposurePercent, String riskState) {}
}
