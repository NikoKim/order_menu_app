package com.order.menu.module.entertainment.mode.guessstock.credit.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class LoanInterestCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private LoanInterestCalculator() {}

    public static BigDecimal simpleInterest(BigDecimal principal, BigDecimal ratePercentPerRound) {
        if (principal == null || ratePercentPerRound == null || principal.signum() <= 0
                || ratePercentPerRound.signum() <= 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return principal.multiply(ratePercentPerRound)
                .divide(HUNDRED, 10, RoundingMode.HALF_UP)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
