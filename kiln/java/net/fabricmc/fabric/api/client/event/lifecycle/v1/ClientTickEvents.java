package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import kiln.Breadcrumb;
import kiln.Native;
import kiln.SoftSwitch;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Kiln's stand-in for Fabric's {@code ClientTickEvents}: the client's tick, 20 times a second, in a world or not,
 * and on the pause screen, as in vanilla. A listener that throws (also a {@link LinkageError}: a call Kiln
 * has no stand-in for) is logged; one that runs Java out of its heap or stack has its mod turned off; the others still
 * run. Each runs in D-112's breadcrumb ({@link Breadcrumb}).
 */
public final class ClientTickEvents {
    public static final Event<StartTick> START_CLIENT_TICK = Event.kiln$of(listeners -> client -> {
        for (StartTick l : listeners) {
            int depth = Breadcrumb.enter(l, "START_CLIENT_TICK");
            try {
                l.onStartTick(client);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a START_CLIENT_TICK listener threw " + e);
            } catch (OutOfMemoryError | StackOverflowError e) {
                SoftSwitch.trip(l, e, "START_CLIENT_TICK");
            } finally {
                Breadcrumb.leave(depth);
            }
        }
    });

    public static final Event<EndTick> END_CLIENT_TICK = Event.kiln$of(listeners -> client -> {
        for (EndTick l : listeners) {
            int depth = Breadcrumb.enter(l, "END_CLIENT_TICK");
            try {
                l.onEndTick(client);
            } catch (RuntimeException | LinkageError e) {
                Native.log("an END_CLIENT_TICK listener threw " + e);
            } catch (OutOfMemoryError | StackOverflowError e) {
                SoftSwitch.trip(l, e, "END_CLIENT_TICK");
            } finally {
                Breadcrumb.leave(depth);
            }
        }
    });

    public static final Event<StartLevelTick> START_LEVEL_TICK = Event.kiln$of(listeners -> (level) -> {
        for (StartLevelTick l : listeners) {
            int depth = Breadcrumb.enter(l, "START_LEVEL_TICK");
            try {
                l.onStartTick(level);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a START_LEVEL_TICK listener threw " + e);
            } catch (OutOfMemoryError | StackOverflowError e) {
                SoftSwitch.trip(l, e, "START_LEVEL_TICK");
            } finally {
                Breadcrumb.leave(depth);
            }
        }
    });

    public static final Event<EndLevelTick> END_LEVEL_TICK = Event.kiln$of(listeners -> (level) -> {
        for (EndLevelTick l : listeners) {
            int depth = Breadcrumb.enter(l, "END_LEVEL_TICK");
            try {
                l.onEndTick(level);
            } catch (RuntimeException | LinkageError e) {
                Native.log("an END_LEVEL_TICK listener threw " + e);
            } catch (OutOfMemoryError | StackOverflowError e) {
                SoftSwitch.trip(l, e, "END_LEVEL_TICK");
            } finally {
                Breadcrumb.leave(depth);
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

    @FunctionalInterface
    public interface StartLevelTick {
        void onStartTick(ClientLevel level);
    }

    @FunctionalInterface
    public interface EndLevelTick {
        void onEndTick(ClientLevel level);
    }
}
