package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.border.WorldBorder;

/**
 * Kiln's stand-in for {@code LevelReader}: what reads a level's blocks and its border (a block's {@code canSurvive}
 * asks one).
 */
public interface LevelReader extends BlockGetter {
    /** Whether the block at {@code pos} is one of the game's airs. */
    default boolean isEmptyBlock(BlockPos pos) {
        return getBlockState(pos).isAir();
    }

    /** The world's border: Kiln's server keeps every place inside it (Horizon's own checks stay the game's). */
    default WorldBorder getWorldBorder() {
        return WorldBorder.KILN$OPEN;
    }
}
