package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import kiln.Native;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Kiln's stand-in for Fabric's {@code ClientChunkEvents}: a chunk of the player's level came to the window's game, or
 * went (its level left too). Kiln tells them with the next tick.
 */
public final class ClientChunkEvents {
    public static final Event<Load> CHUNK_LOAD = Event.kiln$of(listeners -> (level, chunk) -> {
        for (Load l : listeners) {
            try {
                l.onChunkLoad(level, chunk);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a CHUNK_LOAD listener threw " + e);
            }
        }
    });

    public static final Event<Unload> CHUNK_UNLOAD = Event.kiln$of(listeners -> (level, chunk) -> {
        for (Unload l : listeners) {
            try {
                l.onChunkUnload(level, chunk);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a CHUNK_UNLOAD listener threw " + e);
            }
        }
    });

    private ClientChunkEvents() {}

    @FunctionalInterface
    public interface Load {
        void onChunkLoad(ClientLevel level, LevelChunk chunk);
    }

    @FunctionalInterface
    public interface Unload {
        void onChunkUnload(ClientLevel level, LevelChunk chunk);
    }
}
