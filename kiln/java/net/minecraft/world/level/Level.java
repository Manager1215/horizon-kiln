package net.minecraft.world.level;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code Level}: a dimension of the world, as the game last told Kiln about it. */
public abstract class Level {
    private ResourceKey<Level> dimension = ResourceKey.kiln$of(Identifier.withDefaultNamespace("overworld"));
    private long gameTime;

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    /** Ticks since the world was made (vanilla's {@code LevelAccessor.getGameTime}). */
    public long getGameTime() {
        return gameTime;
    }

    /** Kiln's own: what the game said this tick. */
    public void kiln$update(String dimension, long gameTime) {
        if (!this.dimension.identifier().toString().equals(dimension)) {
            this.dimension = ResourceKey.kiln$of(Identifier.parse(dimension));
        }
        this.gameTime = gameTime;
    }
}
