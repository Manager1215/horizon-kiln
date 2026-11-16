package net.minecraft.client.gui.components.debug;

/** Kiln's stand-in for {@code DebugScreenEntryStatus}: when a debug entry shows, as vanilla's. */
public enum DebugScreenEntryStatus implements net.minecraft.util.StringRepresentable {
    ALWAYS_ON("alwaysOn"),
    IN_OVERLAY("inOverlay"),
    NEVER("never");

    private final String name;

    DebugScreenEntryStatus(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Kiln's own: the status called {@code name}, or none. */
    public static DebugScreenEntryStatus kiln$byName(String name) {
        for (DebugScreenEntryStatus s : values()) {
            if (s.name.equals(name)) {
                return s;
            }
        }
        return null;
    }
}
