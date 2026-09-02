package com.order.menu.module.entertainment.mode.guessstock.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockRound;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiMarketSnapshotServiceTest {
    private final AiMarketSnapshotService service = new AiMarketSnapshotService();

    @Test
    void compactSnapshotKeepsFourClosesAndDetectsRegimeFlip() {
        GuessStockRound currentRound = round(5, "27");
        AiMarketSnapshot snapshot = service.create(5, 20, "OPENING", bd("27"), bd("29.8"),
                List.of(round(1, "30"), round(2, "30"), round(3, "30"), round(4, "29.8")),
                currentRound, 0, 0, 3);

        assertEquals(List.of(bd("30"), bd("30"), bd("29.8"), bd("27")), snapshot.recentCloses());
        assertEquals("EXTREME_VOLATILITY", snapshot.marketRegime());
        assertEquals("STRONG_DOWN", snapshot.trendState());
        assertEquals("SIDEWAYS", snapshot.previousMarketRegime());
        assertTrue(snapshot.regimeChanged());
    }

    @Test
    void openingAtLastCloseDoesNotReportAFalseRegimeChange() {
        GuessStockRound persisted = round(4, "95");
        persisted.setTrendState("DOWN");
        persisted.setVolatilityState("NORMAL");
        persisted.setTrendAge(4);
        persisted.setRegimeAge(4);
        persisted.setTrendScore(bd("-0.6"));
        persisted.setCurrentVolatilityPercent(bd("2"));

        AiMarketSnapshot snapshot = service.create(5, 20, "OPENING", bd("95"), bd("95"),
                List.of(round(1, "100"), round(2, "98"), round(3, "96"), persisted),
                round(5, "95"), 0, 0, 3);

        assertEquals("DOWN", snapshot.marketRegime());
        assertEquals("DOWN", snapshot.previousMarketRegime());
        assertTrue(!snapshot.regimeChanged());
    }

    private static GuessStockRound round(int roundNo, String close) {
        GuessStockRound round = new GuessStockRound();
        round.setRoundNo(roundNo);
        round.setClosePrice(bd(close));
        return round;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
