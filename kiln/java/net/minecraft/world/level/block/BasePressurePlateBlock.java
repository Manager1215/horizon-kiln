package net.minecraft.world.level.block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** Kiln's stand-in for {@code BasePressurePlateBlock}: a pressure plate of its set (D-226). Stepping on it isn't told here. */
public abstract class BasePressurePlateBlock extends Block {
    public BlockSetType type;

    protected BasePressurePlateBlock(BlockBehaviour.Properties properties, BlockSetType type) {
        super(properties);
        this.type = type;
    }

    protected BasePressurePlateBlock() {
        this(null, null);
    }
}
