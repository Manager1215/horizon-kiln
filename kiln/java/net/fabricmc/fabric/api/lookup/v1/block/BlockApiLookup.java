package net.fabricmc.fabric.api.lookup.v1.block;

import java.util.function.BiFunction;
import kiln.ApiLookups;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Kiln's stand-in for Fabric's {@code BlockApiLookup}: an API (an energy or item storage) found at a position. */
public interface BlockApiLookup<A, C> {
    static <A, C> BlockApiLookup<A, C> get(Identifier id, Class<A> apiClass, Class<C> contextClass) {
        return ApiLookups.block(id, apiClass, contextClass);
    }

    default A find(Level level, BlockPos pos, C context) {
        return find(level, pos, null, null, context);
    }

    A find(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity, C context);

    void registerSelf(BlockEntityType<?>... types);

    void registerForBlocks(BlockApiProvider<A, C> provider, Block... blocks);

    @SuppressWarnings("unchecked")
    default <T extends BlockEntity> void registerForBlockEntity(BiFunction<? super T, C, A> provider,
            BlockEntityType<T> type) {
        registerForBlockEntities((entity, context) -> provider.apply((T) entity, context), type);
    }

    void registerForBlockEntities(BlockEntityApiProvider<A, C> provider, BlockEntityType<?>... types);

    void registerFallback(BlockApiProvider<A, C> provider);

    Identifier getId();

    Class<A> apiClass();

    Class<C> contextClass();

    BlockApiProvider<A, C> getProvider(Block block);

    @FunctionalInterface
    interface BlockApiProvider<A, C> {
        A find(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity, C context);
    }

    @FunctionalInterface
    interface BlockEntityApiProvider<A, C> {
        A find(BlockEntity blockEntity, C context);
    }
}
