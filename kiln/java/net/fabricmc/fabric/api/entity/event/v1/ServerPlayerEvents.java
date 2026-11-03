package net.fabricmc.fabric.api.entity.event.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

/** Kiln's stand-in for Fabric's {@code ServerPlayerEvents}: a player respawned, as a new player copied from the old. */
public final class ServerPlayerEvents {
    public static final Event<CopyFrom> COPY_FROM = Event.kiln$of(listeners -> (oldPlayer, newPlayer, alive) -> {
        for (CopyFrom l : listeners) {
            l.copyFromPlayer(oldPlayer, newPlayer, alive);
        }
    });
    public static final Event<AfterRespawn> AFTER_RESPAWN = Event.kiln$of(listeners -> (oldPlayer, newPlayer, alive) -> {
        for (AfterRespawn l : listeners) {
            l.afterRespawn(oldPlayer, newPlayer, alive);
        }
    });

    private ServerPlayerEvents() {}

    @FunctionalInterface
    public interface CopyFrom {
        void copyFromPlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive);
    }

    @FunctionalInterface
    public interface AfterRespawn {
        void afterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive);
    }
}
