package net.minecraft.client;

import java.util.List;
import net.minecraft.server.packs.PackResources;

/**
 * Kiln's stand-in for {@code ResourceLoadStateTracker}: when the client's resources load. The game loads its own; Kiln
 * marks the initial load once, after the mods' client entrypoints (D-152), where the mods' mixins hook it.
 */
public class ResourceLoadStateTracker {
    private ReloadReason reason;

    public void startReload(ReloadReason reason, List<PackResources> packs) {
        this.reason = reason;
    }

    public void finishReload() {}

    /** Kiln's own: why the last load was, null before the first. */
    public ReloadReason kiln$reason() {
        return reason;
    }

    public enum ReloadReason {
        INITIAL("initial"),
        MANUAL("manual"),
        UNKNOWN("unknown");

        private final String name;

        ReloadReason(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
