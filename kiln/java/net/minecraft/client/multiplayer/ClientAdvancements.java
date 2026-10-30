package net.minecraft.client.multiplayer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code ClientAdvancements}: the player's advancements in this world. Kiln lists the ones
 * made (recipe advancements left out); each is done.
 */
public class ClientAdvancements {
    private static final AdvancementProgress DONE = new AdvancementProgress(true);
    private final Map<AdvancementHolder, AdvancementProgress> progress = new LinkedHashMap<>();

    public Map<AdvancementHolder, AdvancementProgress> progress() {
        return Collections.unmodifiableMap(progress);
    }

    public AdvancementHolder get(Identifier id) {
        AdvancementHolder holder = new AdvancementHolder(id);
        return progress.containsKey(holder) ? holder : null;
    }

    /** Kiln's own: the player made the advancement {@code id}. */
    public void kiln$done(String id) {
        progress.put(new AdvancementHolder(Identifier.parse(id)), DONE);
    }
}
