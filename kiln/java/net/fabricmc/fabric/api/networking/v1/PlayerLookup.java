package net.fabricmc.fabric.api.networking.v1;

import java.util.Collection;
import java.util.Objects;
import kiln.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Kiln's stand-in for Fabric's {@code PlayerLookup}: the players a mod's packets go to, from the players Horizon's
 * integrated server has ({@link Players}). A player tracks a chunk the game sends them, so a block entity's sync (a
 * machine's screen) goes to those near enough to see it.
 */
public final class PlayerLookup {
    private PlayerLookup() {}

    public static Collection<ServerPlayer> all(MinecraftServer server) {
        Objects.requireNonNull(server, "The server cannot be null");
        return Players.where(o -> true);
    }

    public static Collection<ServerPlayer> level(ServerLevel level) {
        String dimension = Players.dimension(Objects.requireNonNull(level, "The level cannot be null"));
        return Players.where(o -> Players.dimension(o.player().level()).equals(dimension));
    }

    public static Collection<ServerPlayer> tracking(ServerLevel level, ChunkPos pos) {
        Objects.requireNonNull(pos, "The chunk position cannot be null");
        return tracking(level, pos.x(), pos.z(), null);
    }

    public static Collection<ServerPlayer> tracking(ServerLevel level, BlockPos pos) {
        Objects.requireNonNull(pos, "The block position cannot be null");
        return tracking(level, pos.getX() >> 4, pos.getZ() >> 4, null);
    }

    public static Collection<ServerPlayer> tracking(BlockEntity blockEntity) {
        Objects.requireNonNull(blockEntity, "The block entity cannot be null");
        Level level = Objects.requireNonNull(blockEntity.getLevel(), "The block entity has no level");
        if (!(level instanceof ServerLevel server)) {
            throw new IllegalArgumentException("Only supported on server levels!");
        }
        return tracking(server, blockEntity.getBlockPos());
    }

    /** The players who see the entity: those sent its chunk, but never a player themselves. */
    public static Collection<ServerPlayer> tracking(Entity entity) {
        Objects.requireNonNull(entity, "The entity cannot be null");
        if (!(entity.level() instanceof ServerLevel server)) {
            throw new IllegalArgumentException("Only supported on server levels!");
        }
        BlockPos at = entity.blockPosition();
        return tracking(server, at.getX() >> 4, at.getZ() >> 4, entity);
    }

    public static Collection<ServerPlayer> around(ServerLevel level, Vec3 pos, double radius) {
        Objects.requireNonNull(pos, "The position cannot be null");
        String dimension = Players.dimension(Objects.requireNonNull(level, "The level cannot be null"));
        double squared = radius * radius;
        return Players.where(o -> Players.dimension(o.player().level()).equals(dimension)
                && o.player().position().distanceToSqr(pos) <= squared);
    }

    public static Collection<ServerPlayer> around(ServerLevel level, Vec3i pos, double radius) {
        Objects.requireNonNull(pos, "The position cannot be null");
        return around(level, Vec3.atCenterOf(pos), radius);
    }

    private static Collection<ServerPlayer> tracking(ServerLevel level, int x, int z, Entity except) {
        String dimension = Players.dimension(Objects.requireNonNull(level, "The level cannot be null"));
        return Players.where(o -> {
            ServerPlayer p = o.player();
            if (except != null && p.getUUID().equals(except.getUUID())) {
                return false;
            }
            BlockPos at = p.blockPosition();
            return Players.dimension(p.level()).equals(dimension)
                    && Players.sees(at.getX() >> 4, at.getZ() >> 4, o.viewDistance(), x, z);
        });
    }
}
