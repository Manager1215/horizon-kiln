package net.minecraft.world.item.crafting.display;

import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code SlotDisplayContext}: what a {@link SlotDisplay} resolves with. Kiln's displays read the
 * game's fuels and tags from its resources ({@code kiln.GameItems}), so the level is carried but nothing needs it.
 */
public final class SlotDisplayContext {
    public static final ContextKey<Level> LEVEL = new ContextKey<>(Identifier.withDefaultNamespace("level"));

    private SlotDisplayContext() {}

    public static ContextMap fromLevel(Level level) {
        return ContextMap.builder().withOptionalParameter(LEVEL, level).build();
    }
}
