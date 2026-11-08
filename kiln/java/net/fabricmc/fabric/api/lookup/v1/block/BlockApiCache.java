package net.fabricmc.fabric.api.lookup.v1.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Kiln's stand-in for Fabric's {@code BlockApiCache}: a lookup at one position of a level, which a mod keeps (Tech
 * Reborn's cables keep one per face, for their neighbours' energy). Kiln keeps nothing in it: each find asks the
 * lookup again, in its dimension's level as the running call has it ({@code ServerLevel.kiln$now}).
 */
public interface BlockApiCache<A, C> {
    static <A, C> BlockApiCache<A, C> create(BlockApiLookup<A, C> lookup, ServerLevel level, BlockPos pos) {
        BlockPos at = pos.immutable();
        return new BlockApiCache<>() {
            @Override
            public A find(BlockState state, C context) {
                return lookup.find(level.kiln$now(), at, state, null, context);
            }

            @Override
            public BlockEntity getBlockEntity() {
                return level.kiln$now().getBlockEntity(at);
            }

            @Override
            public BlockApiLookup<A, C> getLookup() {
                return lookup;
            }

            @Override
            public ServerLevel getLevel() {
                return level.kiln$now();
            }

            @Override
            public BlockPos getPos() {
                return at;
            }
        };
    }

    default A find(C context) {
        return find(null, context);
    }

    A find(BlockState state, C context);

    BlockEntity getBlockEntity();

    BlockApiLookup<A, C> getLookup();

    ServerLevel getLevel();

    BlockPos getPos();
}
