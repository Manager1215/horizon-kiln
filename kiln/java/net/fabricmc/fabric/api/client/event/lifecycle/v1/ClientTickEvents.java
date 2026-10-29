package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import kiln.Native;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;

/**
 * Kiln's stand-in for Fabric's {@code ClientTickEvents}: the client's tick, 20 times a second, in a world or not,
 * and on the pause screen, as in vanilla. A listener that throws is logged; the others still run.
 */
public final class ClientTickEvents {
    public static final Event<StartTick> START_CLIENT_TICK = Event.kiln$of(listeners -> client -> {
        for (StartTick l : listeners) {
            try {
                l.onStartTick(client);
            } catch (RuntimeException e) {
                Native.log("a START_CLIENT_TICK listener threw " + e);
            }
        }
    });

    public static final Event<EndTick> END_CLIENT_TICK = Event.kiln$of(listeners -> client -> {
        for (EndTick l : listeners) {
            try {
                l.onEndTick(client);
            } catch (RuntimeException e) {
                Native.log("an END_CLIENT_TICK listener threw " + e);
            }
        }
    });

    private ClientTickEvents() {}

    @FunctionalInterface
    public interface StartTick {
        void onStartTick(Minecraft client);
    }

    @FunctionalInterface
    public interface EndTick {
        void onEndTick(Minecraft client);
    }
}
