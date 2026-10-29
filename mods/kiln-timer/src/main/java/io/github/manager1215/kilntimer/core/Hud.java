package io.github.manager1215.kilntimer.core;

import io.github.manager1215.kilntimer.core.Timer.SplitTime;
import java.util.ArrayList;
import java.util.List;

/** What the timer's HUD panel shows, as rows the client draws: a label on the left, a time and a delta on the right. */
public final class Hud {
    public static final int WHITE = 0xFFFFFF;
    public static final int GRAY = 0xAAAAAA;
    public static final int GREEN = 0x55FF55;
    public static final int RED = 0xFF5555;
    public static final int GOLD = 0xFFAA00;

    /** One row: {@code delta} may be empty. */
    public record Row(String label, String time, String delta, int color, int deltaColor) {
        static Row of(String label, String time, int color) {
            return new Row(label, time, "", color, color);
        }
    }

    private Hud() {}

    /**
     * The panel: RTA and IGT, then each split (its time and delta to the personal best once reached, the
     * personal best's time in gray before), then after the finish the final IGT, gold for a new personal best.
     */
    public static List<Row> rows(Timer timer, Records records, boolean newPb, long now) {
        List<Row> rows = new ArrayList<>();
        int clock = timer.phase() == Timer.Phase.FINISHED ? (newPb ? GOLD : WHITE) : WHITE;
        rows.add(Row.of("RTA", Times.format(timer.rta(now)), clock));
        rows.add(Row.of("IGT", Times.format(timer.igt(now)), clock));
        for (Split split : Split.values()) {
            SplitTime reached = timer.splits().get(split);
            if (reached == null) {
                SplitTime pb = records.pb().get(split);
                rows.add(Row.of(split.label(), pb == null ? "-" : Times.format(pb.igt()), GRAY));
                continue;
            }
            Long delta = records.delta(split, reached.igt()).orElse(null);
            String deltaText = delta == null ? "" : Times.delta(delta);
            int deltaColor = delta == null ? WHITE : delta < 0 ? GREEN : RED;
            rows.add(new Row(split.label(), Times.format(reached.igt()), deltaText, WHITE, deltaColor));
        }
        if (timer.phase() == Timer.Phase.FINISHED) {
            rows.add(Row.of(newPb ? "PB!" : "Final", Times.format(timer.igt(now)), newPb ? GOLD : WHITE));
        }
        return rows;
    }
}
