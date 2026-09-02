package com.order.menu.module.entertainment.mode.guessstock.agent;

/** Event information that is safe to expose before the current stage settles. */
public record PublicEventDigest(ResidualSignal residualSignal, int visibleRemainingStages) {
    public static PublicEventDigest none() {
        return new PublicEventDigest(ResidualSignal.NONE, 0);
    }

    public enum ResidualSignal { NONE, LOW, MEDIUM, HIGH }
}
