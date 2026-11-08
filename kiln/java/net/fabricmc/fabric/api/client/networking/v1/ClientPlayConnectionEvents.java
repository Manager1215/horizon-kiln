package net.fabricmc.fabric.api.client.networking.v1;

import kiln.Native;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * Kiln's stand-in for Fabric's {@code ClientPlayConnectionEvents}: a world's connection made, ready (the player in
 * it), and closed. The sender handed over sends a mod's payloads to the server ({@link kiln.Net}).
 */
public final class ClientPlayConnectionEvents {
    public static final Event<Init> INIT = Event.kiln$of(listeners -> (handler, client) -> {
        for (Init l : listeners) {
            try {
                l.onPlayInit(handler, client);
            } catch (RuntimeException | LinkageError e) {
                Native.log("an INIT listener threw " + e);
            }
        }
    });

    public static final Event<Join> JOIN = Event.kiln$of(listeners -> (handler, sender, client) -> {
        for (Join l : listeners) {
            try {
                l.onPlayReady(handler, sender, client);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a JOIN listener threw " + e);
            }
        }
    });

    public static final Event<Disconnect> DISCONNECT = Event.kiln$of(listeners -> (handler, client) -> {
        for (Disconnect l : listeners) {
            try {
                l.onPlayDisconnect(handler, client);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a DISCONNECT listener threw " + e);
            }
        }
    });

    private ClientPlayConnectionEvents() {}

    @FunctionalInterface
    public interface Init {
        void onPlayInit(ClientPacketListener handler, Minecraft client);
    }

    @FunctionalInterface
    public interface Join {
        void onPlayReady(ClientPacketListener handler, PacketSender sender, Minecraft client);
    }

    @FunctionalInterface
    public interface Disconnect {
        void onPlayDisconnect(ClientPacketListener handler, Minecraft client);
    }
}
