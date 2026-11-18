package net.minecraft.world.clock;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;

/**
 * Kiln's stand-in for {@code WorldClock}: a clock of the world (the overworld's, the End's), whose total ticks the
 * server keeps ({@link ServerClockManager}). Its entries are the game's two ({@link WorldClocks}), registered when
 * first asked for.
 */
public record WorldClock() {
    /** Kiln's own: the game's clock {@code key}'s holder, the same each time. */
    public static synchronized Holder<WorldClock> kiln$holder(ResourceKey<WorldClock> key) {
        Registry<WorldClock> clocks = BuiltInRegistries.kiln$WORLD_CLOCK;
        return clocks.get(key.identifier()).<Holder<WorldClock>>map(h -> h)
                .orElseGet(() -> Registry.registerForHolder(clocks, key, new WorldClock()));
    }
}
