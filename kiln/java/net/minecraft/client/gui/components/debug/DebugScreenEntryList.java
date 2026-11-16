package net.minecraft.client.gui.components.debug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code DebugScreenEntryList}: each debug entry's status, as the game's debug profile has it
 * (Horizon's, said with each tick). A mod's change goes to Horizon (kiln.Back), which keeps it in its
 * {@code debug-profile.json} and draws by it, as F3+B and F3+G do; until Horizon says it back (a second at most), the
 * mod's change is the status here.
 */
public class DebugScreenEntryList {
    /** The game's statuses that aren't {@code NEVER}, by id. */
    private final Map<Identifier, DebugScreenEntryStatus> game = new LinkedHashMap<>();
    /** A mod's changes Horizon hasn't said back yet, and the ticks they have waited. */
    private final Map<Identifier, DebugScreenEntryStatus> pending = new LinkedHashMap<>();
    private final Map<Identifier, Integer> waited = new LinkedHashMap<>();
    /** The game's entries that show now (by the F3 overlay, and the world's reduced debug info). */
    private final java.util.Set<Identifier> on = new java.util.LinkedHashSet<>();
    private boolean overlay;
    private long version;

    public DebugScreenEntryList(java.io.File dir, Object fixer) {}

    /** Kiln's own: made with the client, before the game says its statuses. */
    public DebugScreenEntryList() {}

    /** The game's profile read: Horizon reads its own. */
    public void load() {}

    /** The game's profile written: Horizon writes its own. */
    public void save() {}

    public DebugScreenEntryStatus getStatus(Identifier id) {
        DebugScreenEntryStatus mine = pending.get(id);
        return mine != null ? mine : game.getOrDefault(id, DebugScreenEntryStatus.NEVER);
    }

    /**
     * Whether it shows now: as the game said, or, for a mod's change Horizon hasn't said back yet, always or with the
     * F3 overlay open.
     */
    public boolean isCurrentlyEnabled(Identifier id) {
        DebugScreenEntryStatus mine = pending.get(id);
        if (mine == null) {
            return on.contains(id);
        }
        return mine == DebugScreenEntryStatus.ALWAYS_ON || (mine == DebugScreenEntryStatus.IN_OVERLAY && overlay);
    }

    /** A status set, as the debug options screen sets one: the game's too, with the next tick. */
    public void setStatus(Identifier id, DebugScreenEntryStatus status) {
        if (status == null || id == null) {
            return;
        }
        pending.put(id, status);
        waited.put(id, 0);
        version++;
        if (id.getNamespace().equals("minecraft")) {
            kiln.Back.act("debug", id.getPath() + " " + status.getSerializedName());
        }
    }

    /** As F3+B and F3+G toggle one: whether it shows afterwards. */
    public boolean toggleStatus(Identifier id) {
        DebugScreenEntryStatus now = pending.containsKey(id) || game.containsKey(id) ? getStatus(id) : null;
        DebugScreenEntryStatus next;
        if (now == DebugScreenEntryStatus.ALWAYS_ON || (now == DebugScreenEntryStatus.IN_OVERLAY && overlay)) {
            next = DebugScreenEntryStatus.NEVER;
        } else if (now == DebugScreenEntryStatus.NEVER && overlay) {
            next = DebugScreenEntryStatus.IN_OVERLAY;
        } else {
            next = DebugScreenEntryStatus.ALWAYS_ON;
        }
        setStatus(id, next);
        return next != DebugScreenEntryStatus.NEVER;
    }

    public Collection<Identifier> getCurrentlyEnabled() {
        List<Identifier> on = new ArrayList<>();
        java.util.Set<Identifier> all = new java.util.LinkedHashSet<>(this.on);
        all.addAll(pending.keySet());
        for (Identifier id : all) {
            if (isCurrentlyEnabled(id)) {
                on.add(id);
            }
        }
        return on;
    }

    public boolean isOverlayVisible() {
        return overlay;
    }

    /** F3 pressed by a mod: Horizon opens and closes its overlay with the key alone, so it stays as it is. */
    public void toggleDebugOverlay() {
        kiln.Missing.called("the debug overlay opened or closed by a mod");
    }

    public void setOverlayVisible(boolean visible) {
        if (visible != overlay) {
            kiln.Missing.called("the debug overlay opened or closed by a mod");
        }
    }

    public void rebuildCurrentList() {}

    public long getCurrentlyEnabledVersion() {
        return version;
    }

    /**
     * Kiln's own: the game's statuses with the tick ({@code <path>:<status>} joined by {@code ,}, the ones that aren't
     * {@code never}), the paths of those that show now, and whether its F3 overlay is open. A mod's change Horizon
     * now says is the game's; one it hasn't said after a second gives way.
     */
    public void kiln$game(String statuses, String shown, boolean overlay) {
        Map<Identifier, DebugScreenEntryStatus> now = new LinkedHashMap<>();
        for (String entry : statuses.isEmpty() ? new String[0] : statuses.split(",")) {
            int colon = entry.lastIndexOf(':');
            DebugScreenEntryStatus status = colon > 0 ? DebugScreenEntryStatus.kiln$byName(entry.substring(colon + 1)) : null;
            if (status != null) {
                now.put(Identifier.withDefaultNamespace(entry.substring(0, colon)), status);
            }
        }
        java.util.Set<Identifier> nowOn = new java.util.LinkedHashSet<>();
        for (String path : shown.isEmpty() ? new String[0] : shown.split(",")) {
            nowOn.add(Identifier.withDefaultNamespace(path));
        }
        if (!now.equals(game) || !nowOn.equals(on) || overlay != this.overlay) {
            version++;
        }
        game.clear();
        game.putAll(now);
        on.clear();
        on.addAll(nowOn);
        this.overlay = overlay;
        pending.entrySet().removeIf(e -> {
            int ticks = waited.merge(e.getKey(), 1, Integer::sum);
            boolean taken = game.getOrDefault(e.getKey(), DebugScreenEntryStatus.NEVER) == e.getValue();
            if (taken || ticks > 20) {
                waited.remove(e.getKey());
                return true;
            }
            return false;
        });
    }
}
