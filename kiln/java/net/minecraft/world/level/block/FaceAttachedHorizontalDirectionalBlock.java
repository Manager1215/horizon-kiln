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
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * Kiln's stand-in for {@code FaceAttachedHorizontalDirectionalBlock}: a block on a floor, wall or ceiling (a button, a
 * lever), and its facing (D-226). Placed on a wall it faces out of it; on a floor or ceiling, the way the player looks.
 */
public abstract class FaceAttachedHorizontalDirectionalBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;

    protected FaceAttachedHorizontalDirectionalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    protected FaceAttachedHorizontalDirectionalBlock() {
        this(null);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        if (!state.hasProperty(FACE) || !state.hasProperty(FACING)) {
            return state;
        }
        Direction face = context.getClickedFace();
        if (face == Direction.UP) {
            return state.setValue(FACE, AttachFace.FLOOR).setValue(FACING, context.getHorizontalDirection());
        }
        if (face == Direction.DOWN) {
            return state.setValue(FACE, AttachFace.CEILING).setValue(FACING, context.getHorizontalDirection());
        }
        return state.setValue(FACE, AttachFace.WALL).setValue(FACING, face);
    }
}
