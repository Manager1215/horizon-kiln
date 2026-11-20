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
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * Kiln's stand-in for {@code StairBlock}: a stair, its facing, its half and its corner (D-226). Placed, it faces the
 * way the player looks and takes the upper half when the click was on a block's lower face or upper half; its corner is
 * straight (Kiln doesn't work out the corners with the stairs beside it).
 */
public class StairBlock extends Block implements SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final EnumProperty<StairsShape> SHAPE = BlockStateProperties.STAIRS_SHAPE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public Block base;
    public BlockState baseState;

    public StairBlock(BlockState baseState, BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.BOTTOM)
                .setValue(SHAPE, StairsShape.STRAIGHT).setValue(WATERLOGGED, false));
        this.baseState = baseState;
        this.base = baseState == null ? null : baseState.getBlock();
    }

    protected StairBlock() {
        this(null, null);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockPos pos = context.getClickedPos();
        boolean top = face == Direction.DOWN
                || face != Direction.UP && context.getClickLocation().y - pos.getY() > 0.5;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection())
                .setValue(HALF, top ? Half.TOP : Half.BOTTOM);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, SHAPE, WATERLOGGED);
    }
}
