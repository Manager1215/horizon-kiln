package net.fabricmc.fabric.api.networking.v1;

import kiln.Native;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/**
 * Kiln's stand-in for Fabric's {@code ServerConfigurationConnectionEvents}: a player's configuration, before the play
 * phase (D-199). Horizon's configuration phase carries no mod's payloads, so Kiln runs it where the player's play
 * connection starts ({@link kiln.Server}): BEFORE_CONFIGURE, then CONFIGURE, then Fabric's play INIT and JOIN. What a
 * mod sends then goes to the player's window over Kiln's channel, ahead of its play payloads; DISCONNECT is a player who
 * left before their play connection was made, which Kiln's configuration never sees.
 */
public final class ServerConfigurationConnectionEvents {
    public static final Event<Configure> BEFORE_CONFIGURE = Event.kiln$of(listeners -> (handler, server) -> {
        for (Configure l : listeners) {
            try {
                l.onSendConfiguration(handler, server);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a server BEFORE_CONFIGURE listener threw " + e);
            }
        }
    });

    public static final Event<Configure> CONFIGURE = Event.kiln$of(listeners -> (handler, server) -> {
        for (Configure l : listeners) {
            try {
                l.onSendConfiguration(handler, server);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a server CONFIGURE listener threw " + e);
            }
        }
    });

    public static final Event<Disconnect> DISCONNECT = Event.kiln$of(listeners -> (handler, server) -> {
        for (Disconnect l : listeners) {
            try {
                l.onConfigureDisconnect(handler, server);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a server configuration DISCONNECT listener threw " + e);
            }
        }
    });

    private ServerConfigurationConnectionEvents() {}

    @FunctionalInterface
    public interface Configure {
        void onSendConfiguration(ServerConfigurationPacketListenerImpl handler, MinecraftServer server);
    }

    @FunctionalInterface
    public interface Disconnect {
        void onConfigureDisconnect(ServerConfigurationPacketListenerImpl handler, MinecraftServer server);
    }
}
