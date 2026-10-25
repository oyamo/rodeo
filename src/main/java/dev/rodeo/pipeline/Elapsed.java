package dev.rodeo.pipeline;

import java.time.Duration;

public final class Elapsed {

    private Elapsed() {
    }

    public static String format(Duration elapsed) {
        if (elapsed.toSeconds() < 60) {
            return String.format("%.1fs", elapsed.toMillis() / 1000.0);
        }
        return String.format("%dm %02ds", elapsed.toMinutes(), elapsed.toSecondsPart());
    }
}
