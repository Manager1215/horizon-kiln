package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.ticks.TickPriority;

/** Kiln's stand-in for {@code ScheduledTickAccess}: a block's tick in so many game ticks ({@code Block.tick}). */
public interface ScheduledTickAccess {
    default void scheduleTick(BlockPos pos, Block block, int delay, TickPriority priority) {
        kiln$scheduleTick(pos, block, delay);
    }

    default void scheduleTick(BlockPos pos, Block block, int delay) {
        kiln$scheduleTick(pos, block, delay);
    }

    /** Kiln's own: where the game's rules run, the game schedules it; elsewhere nothing happens. */
    void kiln$scheduleTick(BlockPos pos, Block block, int delay);
}
