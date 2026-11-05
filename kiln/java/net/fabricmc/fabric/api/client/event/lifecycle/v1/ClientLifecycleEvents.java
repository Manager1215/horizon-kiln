package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import kiln.Native;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;

/**
 * Kiln's stand-in for Fabric's {@code ClientLifecycleEvents}: the client started (once the mods' entrypoints ran, at
 * Kiln's first tick) and is stopping (Kiln's Java told to stop).
 */
public final class ClientLifecycleEvents {
    public static final Event<ClientStarted> CLIENT_STARTED = Event.kiln$of(listeners -> (client) -> {
        for (ClientStarted l : listeners) {
            try {
                l.onClientStarted(client);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a CLIENT_STARTED listener threw " + e);
            }
        }
    });

    public static final Event<ClientStopping> CLIENT_STOPPING = Event.kiln$of(listeners -> (client) -> {
        for (ClientStopping l : listeners) {
            try {
                l.onClientStopping(client);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a CLIENT_STOPPING listener threw " + e);
            }
        }
    });

    private ClientLifecycleEvents() {}

    @FunctionalInterface
    public interface ClientStarted {
        void onClientStarted(Minecraft client);
    }

    @FunctionalInterface
    public interface ClientStopping {
        void onClientStopping(Minecraft client);
    }
}
