package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Kiln's stand-in for {@code BlockGetter}: what reads the blocks of a place (a level), as a mod's multiblock check
 * does.
 */
public interface BlockGetter {
    BlockEntity getBlockEntity(BlockPos pos);

    BlockState getBlockState(BlockPos pos);

    FluidState getFluidState(BlockPos pos);
}
