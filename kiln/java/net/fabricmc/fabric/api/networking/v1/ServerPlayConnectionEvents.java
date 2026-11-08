package net.fabricmc.fabric.api.networking.v1;

import kiln.Native;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Kiln's stand-in for Fabric's {@code ServerPlayConnectionEvents}: a player's connection to the server made (INIT),
 * ready for packets (JOIN: the player is in the world and on the player list, where most mods send their first sync),
 * and closed (DISCONNECT). Horizon's integrated server calls Kiln at the player's first tick for the first two, and as
 * they leave for the last ({@link kiln.Server}). The sender handed over sends to that player ({@link kiln.Net}).
 */
public final class ServerPlayConnectionEvents {
    public static final Event<Init> INIT = Event.kiln$of(listeners -> (handler, server) -> {
        for (Init l : listeners) {
            try {
                l.onPlayInit(handler, server);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a server INIT listener threw " + e);
            }
        }
    });

    public static final Event<Join> JOIN = Event.kiln$of(listeners -> (handler, sender, server) -> {
        for (Join l : listeners) {
            try {
                l.onPlayReady(handler, sender, server);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a server JOIN listener threw " + e);
            }
        }
    });

    public static final Event<Disconnect> DISCONNECT = Event.kiln$of(listeners -> (handler, server) -> {
        for (Disconnect l : listeners) {
            try {
                l.onPlayDisconnect(handler, server);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a server DISCONNECT listener threw " + e);
            }
        }
    });

    private ServerPlayConnectionEvents() {}

    @FunctionalInterface
    public interface Init {
        void onPlayInit(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }

    @FunctionalInterface
    public interface Join {
        void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server);
    }

    @FunctionalInterface
    public interface Disconnect {
        void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }
}
