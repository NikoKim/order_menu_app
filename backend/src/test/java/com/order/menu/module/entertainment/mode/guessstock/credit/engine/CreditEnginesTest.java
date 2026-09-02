package com.order.menu.module.entertainment.mode.guessstock.credit.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import com.order.menu.module.entertainment.mode.guessstock.engine.GuessStockRules;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreditEnginesTest {
    private final List<GuessStockConfig.CreditProductConfig> products = GuessStockRules.defaultCreditProducts();

    @Test
    void debtAndPriceMoveRiskInExpectedDirection() {
        GuessStockConfig.CreditProductConfig product = product("HIGH_RISK_BANK");
        CreditRiskCalculator.Snapshot noDebt = CreditRiskCalculator.calculate(bd("0"), 2500, bd("100"),
                bd("0"), bd("0"), product, false);
        CreditRiskCalculator.Snapshot leveraged = CreditRiskCalculator.calculate(bd("0"), 2500, bd("100"),
                bd("150000"), bd("0"), product, false);
        CreditRiskCalculator.Snapshot afterDrop = CreditRiskCalculator.calculate(bd("0"), 2500, bd("85"),
                bd("150000"), bd("0"), product, false);

        assertEquals(0, noDebt.ltvPercent().signum());
        assertEquals(bd("100000.0000"), leveraged.netAsset());
        assertEquals(bd("250.0000"), leveraged.exposurePercent());
        assertTrue(afterDrop.ltvPercent().compareTo(leveraged.ltvPercent()) > 0);
        assertEquals("LIQUIDATION", afterDrop.riskState());
    }

    @Test
    void cashAndRepaymentReduceRiskWhileInterestRaisesIt() {
        GuessStockConfig.CreditProductConfig product = product("HIGH_RISK_BANK");
        CreditRiskCalculator.Snapshot base = CreditRiskCalculator.calculate(bd("10000"), 1000, bd("100"),
                bd("60000"), bd("0"), product, false);
        CreditRiskCalculator.Snapshot moreCash = CreditRiskCalculator.calculate(bd("20000"), 1000, bd("100"),
                bd("60000"), bd("0"), product, false);
        CreditRiskCalculator.Snapshot repaid = CreditRiskCalculator.calculate(bd("10000"), 1000, bd("100"),
                bd("50000"), bd("0"), product, false);
        CreditRiskCalculator.Snapshot withInterest = CreditRiskCalculator.calculate(bd("10000"), 1000, bd("100"),
                bd("60000"), bd("1200"), product, false);

        assertTrue(moreCash.ltvPercent().compareTo(base.ltvPercent()) < 0);
        assertTrue(repaid.ltvPercent().compareTo(base.ltvPercent()) < 0);
        assertTrue(withInterest.ltvPercent().compareTo(base.ltvPercent()) > 0);
    }

    @Test
    void borrowingCashDoesNotInflateNetAsset() {
        GuessStockConfig.CreditProductConfig product = product("CONSERVATIVE_BANK");
        CreditRiskCalculator.Snapshot before = CreditRiskCalculator.calculate(bd("100000"), 0, bd("100"),
                bd("0"), bd("0"), product, false);
        CreditRiskCalculator.Snapshot after = CreditRiskCalculator.calculate(bd("150000"), 0, bd("100"),
                bd("50000"), bd("0"), product, false);
        assertEquals(before.netAsset(), after.netAsset());
    }

    @Test
    void riskThresholdsAreInclusive() {
        GuessStockConfig.CreditProductConfig product = product("HIGH_RISK_BANK");
        assertEquals("WARNING", cashOnlyRisk(bd("70"), product).riskState());
        assertEquals("BLOCKED", cashOnlyRisk(bd("73"), product).riskState());
        assertEquals("LIQUIDATION", cashOnlyRisk(bd("76"), product).riskState());
    }

    @Test
    void routeChoosesCheapestBankThatCanMeetExposure() {
        assertEquals("CONSERVATIVE_BANK", CreditLimitCalculator.route(products, bd("130")).code());
        assertEquals("GROWTH_BANK", CreditLimitCalculator.route(products, bd("180")).code());
        assertEquals("HIGH_RISK_BANK", CreditLimitCalculator.route(products, bd("230")).code());
        assertEquals("HIGH_RISK_BANK", CreditLimitCalculator.route(products, bd("290")).code(),
                "disabled extreme product must not be routed");
    }

    @Test
    void conservativeCreditIsCappedAtHalfInitialCapital() {
        CreditLimitCalculator.Limit limit = CreditLimitCalculator.calculate(bd("100000"), bd("100000"), 0,
                bd("100"), bd("0"), bd("0"), product("CONSERVATIVE_BANK"));
        assertEquals(bd("50000.0000"), limit.maximumCredit());
        assertEquals(bd("50000.0000"), limit.availableCredit());
    }

    @Test
    void anotherBankRetainsItsOwnFacilityAfterFirstBankBorrowing() {
        CreditLimitCalculator.Limit limit = CreditLimitCalculator.calculate(bd("100000"), bd("150000"), 0,
                bd("100"), bd("50000"), bd("0"), bd("0"), bd("0"), product("GROWTH_BANK"));
        assertEquals(bd("50000.0000"), limit.availableCredit());
    }

    @Test
    void fallingEquityShrinksAvailableCredit() {
        GuessStockConfig.CreditProductConfig product = product("HIGH_RISK_BANK");
        CreditLimitCalculator.Limit healthy = CreditLimitCalculator.calculate(bd("100000"), bd("0"), 1000,
                bd("100"), bd("50000"), bd("0"), product);
        CreditLimitCalculator.Limit afterLoss = CreditLimitCalculator.calculate(bd("100000"), bd("0"), 1000,
                bd("70"), bd("50000"), bd("0"), product);
        assertTrue(afterLoss.availableCredit().compareTo(healthy.availableCredit()) < 0);
    }

    @Test
    void defaultProductsExposeExpectedLeverageTiers() {
        assertEquals(0, bd("150").compareTo(product("CONSERVATIVE_BANK").maxExposureRatio()));
        assertEquals(0, bd("200").compareTo(product("GROWTH_BANK").maxExposureRatio()));
        assertEquals(0, bd("250").compareTo(product("HIGH_RISK_BANK").maxExposureRatio()));
        assertEquals(0, bd("300").compareTo(product("EXTREME_BANK").maxExposureRatio()));
    }

    @Test
    void simpleInterestDoesNotCompound() {
        BigDecimal oneRound = LoanInterestCalculator.simpleInterest(bd("50000"), bd("0.5"));
        assertEquals(bd("250.0000"), oneRound);
        assertEquals(bd("750.0000"), oneRound.multiply(bd("3")).setScale(4));
    }

    @Test
    void maintenanceLiquidationSellsOnlySharesNeededForRecovery() {
        GuessStockConfig.CreditProductConfig product = product("HIGH_RISK_BANK");
        long shares = ForcedLiquidationPlanner.minimumShares(2500, bd("85"), bd("0"),
                bd("150000"), bd("0"), product);
        assertTrue(shares > 0);
        assertTrue(shares < 2500, "maintenance liquidation should not default to a full exit");
    }

    private GuessStockConfig.CreditProductConfig product(String code) {
        return products.stream().filter(item -> code.equals(item.code())).findFirst().orElseThrow();
    }

    private CreditRiskCalculator.Snapshot cashOnlyRisk(BigDecimal debt, GuessStockConfig.CreditProductConfig product) {
        return CreditRiskCalculator.calculate(bd("100"), 0, bd("100"), debt, bd("0"), product, false);
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
