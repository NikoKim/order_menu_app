package com.order.menu.module.entertainment.mode.guessstock.credit.engine;

import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import java.math.BigDecimal;

public final class ForcedLiquidationPlanner {
    private ForcedLiquidationPlanner() {}

    public static long minimumShares(long shares, BigDecimal price, BigDecimal cash,
                                     BigDecimal principal, BigDecimal interest,
                                     GuessStockConfig.CreditProductConfig product) {
        if (shares <= 0 || price == null || price.signum() <= 0) return 0;
        long low = 0;
        long high = shares;
        while (low < high) {
            long mid = low + (high - low) / 2;
            BigDecimal proceeds = price.multiply(BigDecimal.valueOf(mid));
            BigDecimal debt = principal.add(interest);
            BigDecimal repaid = proceeds.min(debt);
            BigDecimal remainingDebt = debt.subtract(repaid);
            CreditRiskCalculator.Snapshot after = CreditRiskCalculator.calculate(cash, shares - mid, price,
                    remainingDebt, BigDecimal.ZERO, product, false);
            if (remainingDebt.signum() == 0 || after.ltvPercent().compareTo(product.recoveryLtv()) <= 0) high = mid;
            else low = mid + 1;
        }
        return low;
    }
}
