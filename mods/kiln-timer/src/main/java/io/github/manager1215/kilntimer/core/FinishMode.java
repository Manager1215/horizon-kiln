package io.github.manager1215.kilntimer.core;

import java.util.Locale;
import java.util.Optional;

/** What ends a run. */
public enum FinishMode {
    /** "Free the End": the dragon dies. The default. */
    DRAGON,
    /** Back in the Overworld from the End, after the credits. */
    LEAVE_END,
    /** Only the finish key or command. */
    MANUAL;

    /** {@code dragon}, {@code leave_end}, {@code manual}: as settings and commands write it. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<FinishMode> parse(String key) {
        for (FinishMode m : values()) {
            if (m.key().equals(key.trim().toLowerCase(Locale.ROOT).replace('-', '_'))) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }
}
