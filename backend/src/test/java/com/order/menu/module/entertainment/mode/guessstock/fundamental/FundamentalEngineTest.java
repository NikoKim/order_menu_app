package com.order.menu.module.entertainment.mode.guessstock.fundamental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FundamentalEngineTest {
    @Test
    void eventsMoveRelatedIndicesAndPriceContributionIsBounded() {
        var improved = FundamentalEngine.applyEvent(FundamentalEngine.neutral(), "DEMAND", "UP", "STRONG");
        assertTrue(improved.demandIndex() > 100);
        assertTrue(improved.orderIndex() > 100);
        var contribution = FundamentalEngine.priceContribution(improved, new BigDecimal("100"),
                new BigDecimal("90"), new BigDecimal("0.35"), true);
        assertTrue(contribution.fundamentalImpactPct().abs().compareTo(new BigDecimal("0.60")) <= 0);
        assertTrue(contribution.reportSurprisePct().abs().compareTo(new BigDecimal("0.80")) <= 0);
        assertEquals(improved, FundamentalEngine.applyEvent(FundamentalEngine.neutral(),
                "DEMAND", "UP", "STRONG"));
    }
}
