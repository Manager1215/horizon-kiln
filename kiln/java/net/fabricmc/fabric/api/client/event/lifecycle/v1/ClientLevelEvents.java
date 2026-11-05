package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import kiln.Native;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Kiln's stand-in for Fabric's {@code ClientLevelEvents}: the client's level changed (a world joined, a dimension
 * entered).
 */
public final class ClientLevelEvents {
    public static final Event<AfterClientLevelChange> AFTER_CLIENT_LEVEL_CHANGE =
            Event.kiln$of(listeners -> (client, level) -> {
                for (AfterClientLevelChange l : listeners) {
                    try {
                        l.afterLevelChange(client, level);
                    } catch (RuntimeException | LinkageError e) {
                        Native.log("an AFTER_CLIENT_LEVEL_CHANGE listener threw " + e);
                    }
                }
            });

    private ClientLevelEvents() {}

    @FunctionalInterface
    public interface AfterClientLevelChange {
        void afterLevelChange(Minecraft client, ClientLevel level);
    }
}
