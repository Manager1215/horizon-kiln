package io.github.manager1215.kilntimer.core;

import java.util.Locale;

/** The timer's settings, kept as {@code key=value} lines: where the panel sits, what finishes a run, and more. */
public final class Settings {
    public enum Corner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        public boolean left() {
            return this == TOP_LEFT || this == BOTTOM_LEFT;
        }

        public boolean top() {
            return this == TOP_LEFT || this == TOP_RIGHT;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public Corner corner = Corner.TOP_LEFT;
    public FinishMode finish = FinishMode.DRAGON;
    /** Whether the panel shows (the hide key flips it). */
    public boolean shown = true;
    /** Whether each split is also said in chat. */
    public boolean chatSplits = false;

    public String save() {
        return "# Kiln Speedrun Timer settings\n"
                + "corner=" + corner.key() + "\n"
                + "finish=" + finish.key() + "\n"
                + "shown=" + shown + "\n"
                + "chat_splits=" + chatSplits + "\n";
    }

    /** What {@link #save()} wrote; unknown keys and values keep the defaults. */
    public static Settings load(String text) {
        Settings s = new Settings();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (line.startsWith("#") || eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim().toLowerCase(Locale.ROOT);
            switch (key) {
                case "corner" -> {
                    for (Corner c : Corner.values()) {
                        if (c.key().equals(value)) {
                            s.corner = c;
                        }
                    }
                }
                case "finish" -> FinishMode.parse(value).ifPresent(m -> s.finish = m);
                case "shown" -> s.shown = !value.equals("false");
                case "chat_splits" -> s.chatSplits = value.equals("true");
                default -> {}
            }
        }
        return s;
    }
}
