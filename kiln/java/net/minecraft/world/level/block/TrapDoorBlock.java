package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Kiln's stand-in for {@code TrapDoorBlock}: a trapdoor, its facing, its half, open or shut (D-226). Placed on a block's
 * side it faces out of it, in the half clicked; on a top or bottom face, towards the player. Used, one of a set that
 * opens by hand opens or shuts. Redstone doesn't open it here.
 */
public class TrapDoorBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public BlockSetType type;

    public TrapDoorBlock(BlockSetType type, BlockBehaviour.Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false)
                .setValue(HALF, Half.BOTTOM).setValue(POWERED, false).setValue(WATERLOGGED, false));
    }

    protected TrapDoorBlock() {
        this(null, null);
    }

    public BlockSetType getType() {
        return type;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockState state = defaultBlockState();
        if (face.getAxis().isHorizontal()) {
            boolean top = context.getClickLocation().y - context.getClickedPos().getY() > 0.5;
            return state.setValue(FACING, face).setValue(HALF, top ? Half.TOP : Half.BOTTOM);
        }
        return state.setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, face == Direction.UP ? Half.BOTTOM : Half.TOP);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (type != null && !type.canOpenByHand()) {
            return InteractionResult.PASS;
        }
        level.setBlock(pos, state.cycle(OPEN), UPDATE_CLIENTS);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, HALF, POWERED, WATERLOGGED);
    }
}
