package net.minecraft.world.level.block.entity;

import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Kiln's stand-in for {@code BlockEntityType}: how a mod's block entity is made, and for which blocks. */
public class BlockEntityType<T extends BlockEntity> {
    private final BlockEntitySupplier<? extends T> factory;
    private final Set<Block> validBlocks;

    public BlockEntityType(BlockEntitySupplier<? extends T> factory, Set<Block> validBlocks) {
        this.factory = factory;
        this.validBlocks = Set.copyOf(validBlocks);
    }

    public T create(BlockPos pos, BlockState state) {
        return factory.create(pos, state);
    }

    public boolean isValid(BlockState state) {
        return validBlocks.contains(state.getBlock());
    }

    public Holder.Reference<BlockEntityType<?>> builtInRegistryHolder() {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.get(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(this))
                .orElseThrow(() -> new IllegalStateException("a block entity type no mod registered"));
    }

    public boolean onlyOpCanSetNbt() {
        return false;
    }

    /** Kiln's own: the blocks it is made for. */
    public Set<Block> kiln$blocks() {
        return validBlocks;
    }

    /** Kiln's own: the type registered as {@code id}, if a mod registered one. */
    public static Optional<BlockEntityType<?>> kiln$byId(String id) {
        return Optional.ofNullable(BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Identifier.parse(id)));
    }

    @FunctionalInterface
    public interface BlockEntitySupplier<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }
}
