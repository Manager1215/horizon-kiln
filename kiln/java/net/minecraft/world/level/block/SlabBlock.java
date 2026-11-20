package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Kiln's stand-in for {@code SlabBlock}: a slab's half, or both (D-226). Placed, it takes the upper half when the
 * click was on a block's lower face or upper half, and both where the same slab already is.
 */
public class SlabBlock extends Block implements SimpleWaterloggedBlock {
    public static final EnumProperty<SlabType> TYPE = BlockStateProperties.SLAB_TYPE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public SlabBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TYPE, SlabType.BOTTOM).setValue(WATERLOGGED, false));
    }

    protected SlabBlock() {
        this(null);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState there = context.getLevel().getBlockState(pos);
        if (there.is(this)) {
            return there.setValue(TYPE, SlabType.DOUBLE).setValue(WATERLOGGED, false);
        }
        Direction face = context.getClickedFace();
        boolean top = face == Direction.DOWN
                || face != Direction.UP && context.getClickLocation().y - pos.getY() > 0.5;
        return defaultBlockState().setValue(TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE, WATERLOGGED);
    }
}
