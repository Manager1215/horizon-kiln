package net.minecraft.world.clock;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code WorldClocks}: the game's clocks, by their keys. */
public interface WorldClocks {
    ResourceKey<WorldClock> OVERWORLD =
            ResourceKey.create(Registries.WORLD_CLOCK, Identifier.withDefaultNamespace("overworld"));
    ResourceKey<WorldClock> THE_END =
            ResourceKey.create(Registries.WORLD_CLOCK, Identifier.withDefaultNamespace("the_end"));
}
