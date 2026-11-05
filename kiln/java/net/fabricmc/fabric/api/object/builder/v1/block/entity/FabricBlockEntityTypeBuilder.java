package net.fabricmc.fabric.api.object.builder.v1.block.entity;

import com.mojang.datafixers.types.Type;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Kiln's stand-in for Fabric's {@code FabricBlockEntityTypeBuilder}. */
public final class FabricBlockEntityTypeBuilder<T extends BlockEntity> {
    private final Factory<? extends T> factory;
    private final Set<Block> blocks = new LinkedHashSet<>();

    private FabricBlockEntityTypeBuilder(Factory<? extends T> factory, List<Block> blocks) {
        this.factory = factory;
        this.blocks.addAll(blocks);
    }

    public static <T extends BlockEntity> FabricBlockEntityTypeBuilder<T> create(Factory<? extends T> factory,
            Block... blocks) {
        return new FabricBlockEntityTypeBuilder<>(factory, List.of(blocks));
    }

    public FabricBlockEntityTypeBuilder<T> addBlock(Block block) {
        blocks.add(block);
        return this;
    }

    public FabricBlockEntityTypeBuilder<T> addBlocks(Block... blocks) {
        return addBlocks(List.of(blocks));
    }

    public FabricBlockEntityTypeBuilder<T> addBlocks(Collection<? extends Block> blocks) {
        this.blocks.addAll(blocks);
        return this;
    }

    public FabricBlockEntityTypeBuilder<T> canPotentiallyExecuteCommands(boolean can) {
        return this;
    }

    public BlockEntityType<T> build() {
        return new BlockEntityType<>(factory::create, blocks);
    }

    public BlockEntityType<T> build(Type<?> type) {
        return build();
    }

    @FunctionalInterface
    public interface Factory<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }
}
