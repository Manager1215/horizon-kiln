package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Kiln's stand-in for {@code VegetationBlock}: a plant, which stays where what is below it supports it. */
public abstract class VegetationBlock extends Block {
    protected VegetationBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Whether it grows on {@code state}: a block of the game's {@code supports_vegetation} tag. */
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.SUPPORTS_VEGETATION);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return mayPlaceOn(level.getBlockState(below), level, below);
    }
}
