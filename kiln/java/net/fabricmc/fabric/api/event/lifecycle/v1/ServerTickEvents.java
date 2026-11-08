package net.fabricmc.fabric.api.event.lifecycle.v1;

import java.util.List;
import java.util.function.Consumer;
import kiln.TickEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Kiln's stand-in for Fabric's {@code ServerTickEvents}: the server's tick and each level's, fired by Kiln once each
 * per server tick from the server's thread, where Horizon's server ticks ({@code kiln.Server}'s {@code server_tick}
 * call), and only for the events a mod listens to. A listener that fails is said once in Kiln's log and the others
 * still run ({@link TickEvents}).
 */
public final class ServerTickEvents {
    public static final Event<StartTick> START_SERVER_TICK =
            event("START_SERVER_TICK", (listeners, run) -> server -> run.accept(l -> l.onStartTick(server)));
    public static final Event<EndTick> END_SERVER_TICK =
            event("END_SERVER_TICK", (listeners, run) -> server -> run.accept(l -> l.onEndTick(server)));
    public static final Event<StartLevelTick> START_LEVEL_TICK =
            event("START_LEVEL_TICK", (listeners, run) -> level -> run.accept(l -> l.onStartTick(level)));
    public static final Event<EndLevelTick> END_LEVEL_TICK =
            event("END_LEVEL_TICK", (listeners, run) -> level -> run.accept(l -> l.onEndTick(level)));

    private ServerTickEvents() {}

    /** An event whose invoker runs each listener on its own ({@code run}), in the order they registered. */
    private static <T> Event<T> event(String name, Invoker<T> invoker) {
        return Event.kiln$of(listeners -> invoker.make(listeners, call -> TickEvents.each(name, listeners, call)));
    }

    private interface Invoker<T> {
        T make(List<T> listeners, Consumer<Consumer<T>> run);
    }

    @FunctionalInterface
    public interface StartTick {
        void onStartTick(MinecraftServer server);
    }

    @FunctionalInterface
    public interface EndTick {
        void onEndTick(MinecraftServer server);
    }

    @FunctionalInterface
    public interface StartLevelTick {
        void onStartTick(ServerLevel level);
    }

    @FunctionalInterface
    public interface EndLevelTick {
        void onEndTick(ServerLevel level);
    }
}
