package net.fabricmc.fabric.api.networking.v1;

import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Kiln's stand-in for Fabric's {@code PlayerLookup}: the players a mod's packets go to. Kiln sends a mod's own packets
 * to no player yet (Horizon's client is not the mod's), so none: a block entity that syncs its screen to the players
 * watching it syncs to nobody, and goes on.
 */
public final class PlayerLookup {
    private PlayerLookup() {}

    public static Collection<ServerPlayer> all(MinecraftServer server) {
        return List.of();
    }

    public static Collection<ServerPlayer> level(ServerLevel level) {
        return List.of();
    }

    public static Collection<ServerPlayer> tracking(ServerLevel level, ChunkPos pos) {
        return List.of();
    }

    public static Collection<ServerPlayer> tracking(ServerLevel level, BlockPos pos) {
        return List.of();
    }

    public static Collection<ServerPlayer> tracking(BlockEntity blockEntity) {
        return List.of();
    }

    public static Collection<ServerPlayer> tracking(Entity entity) {
        return List.of();
    }
}
