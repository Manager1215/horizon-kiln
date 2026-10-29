package io.github.manager1215.kilntimer.core;

import java.util.Locale;

/** Times as runners read them. All times are nanoseconds. */
public final class Times {
    private Times() {}

    /** {@code 1:02.345}; from an hour on {@code 1:02:03.456}. Negative times read as zero. */
    public static String format(long nanos) {
        long ms = Math.max(0, nanos) / 1_000_000;
        long hours = ms / 3_600_000;
        long minutes = ms / 60_000 % 60;
        long seconds = ms / 1000 % 60;
        long millis = ms % 1000;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d.%03d", hours, minutes, seconds, millis);
        }
        return String.format(Locale.ROOT, "%d:%02d.%03d", minutes, seconds, millis);
    }

    /** A difference to the personal best: {@code -1.234} ahead, {@code +1:02.345} behind. */
    public static String delta(long nanos) {
        String sign = nanos < 0 ? "-" : "+";
        long abs = Math.abs(nanos);
        if (abs < 60_000_000_000L) {
            long ms = abs / 1_000_000;
            return String.format(Locale.ROOT, "%s%d.%03d", sign, ms / 1000, ms % 1000);
        }
        return sign + format(abs);
    }
}
