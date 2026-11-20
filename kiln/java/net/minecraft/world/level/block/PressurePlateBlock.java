package net.minecraft.world.level.block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Kiln's stand-in for {@code PressurePlateBlock}: a plate, pressed or not (D-226). */
public class PressurePlateBlock extends BasePressurePlateBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public PressurePlateBlock(BlockSetType type, BlockBehaviour.Properties properties) {
        super(properties, type);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    protected PressurePlateBlock() {
        this(null, null);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }
}
