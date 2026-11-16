package net.minecraft.commands.arguments.blocks;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Kiln's stand-in for {@code BlockInput}: a block state a player typed, with the block entity data they gave it. */
public class BlockInput {
    private final BlockState state;
    private final Set<Property<?>> properties;
    private final CompoundTag tag;

    public BlockInput(BlockState state, Set<Property<?>> properties, CompoundTag tag) {
        this.state = state;
        this.properties = properties;
        this.tag = tag;
    }

    public BlockState getState() {
        return state;
    }

    public Set<Property<?>> getDefinedProperties() {
        return properties;
    }

    /** Whether the level's block at {@code pos} is this state. */
    public boolean test(ServerLevel level, BlockPos pos) {
        return state.equals(level.getBlockState(pos));
    }

    /** The state set at {@code pos}, as the game's {@code /setblock} does (the block entity data isn't carried yet). */
    public boolean place(ServerLevel level, BlockPos pos, int flags) {
        return level.setBlock(pos, state, flags);
    }
}
