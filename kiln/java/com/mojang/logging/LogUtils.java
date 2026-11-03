package com.mojang.logging;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

/**
 * Kiln's stand-in for Minecraft 26.3's {@code LogUtils} (Mojang's logging library, which Kiln doesn't ship): SLF4J
 * loggers named after the calling class, written to {@code <game folder>/kiln/java.log}.
 */
public class LogUtils {
    public static final String FATAL_MARKER_ID = "FATAL";
    public static final Marker FATAL_MARKER = MarkerFactory.getMarker(FATAL_MARKER_ID);
    private static final StackWalker CALLER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public LogUtils() {}

    public static boolean isLoggerActive() {
        return true;
    }

    /** Kiln logs at SLF4J's default level; the root level can't be changed. */
    public static void configureRootLoggingLevel(Level level) {}

    /** A value whose text is computed only when a log line uses it. */
    public static Object defer(Supplier<Object> value) {
        return new Object() {
            @Override
            public String toString() {
                return String.valueOf(value.get());
            }
        };
    }

    public static Logger getLogger() {
        return LoggerFactory.getLogger(CALLER.getCallerClass());
    }
}
