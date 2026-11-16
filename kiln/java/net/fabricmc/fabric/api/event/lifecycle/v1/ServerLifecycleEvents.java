package net.fabricmc.fabric.api.event.lifecycle.v1;

import kiln.Native;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;

/**
 * Kiln's stand-in for Fabric's {@code ServerLifecycleEvents}: the integrated server starting, started, stopping and
 * stopped. Kiln tells the first two as a single-player world's server loads its level (D-152: JEI's and MezzConfig's
 * server configs load there), the last two as it goes. A listener that throws is logged and the rest still run.
 */
public final class ServerLifecycleEvents {
    public static final Event<ServerStarting> SERVER_STARTING = Event.kiln$of(listeners -> server -> {
        for (ServerStarting l : listeners) {
            try {
                l.onServerStarting(server);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a SERVER_STARTING listener threw " + e);
            }
        }
    });

    public static final Event<ServerStarted> SERVER_STARTED = Event.kiln$of(listeners -> server -> {
        for (ServerStarted l : listeners) {
            try {
                l.onServerStarted(server);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a SERVER_STARTED listener threw " + e);
            }
        }
    });

    public static final Event<ServerStopping> SERVER_STOPPING = Event.kiln$of(listeners -> server -> {
        for (ServerStopping l : listeners) {
            try {
                l.onServerStopping(server);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a SERVER_STOPPING listener threw " + e);
            }
        }
    });

    public static final Event<ServerStopped> SERVER_STOPPED = Event.kiln$of(listeners -> server -> {
        for (ServerStopped l : listeners) {
            try {
                l.onServerStopped(server);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a SERVER_STOPPED listener threw " + e);
            }
        }
    });

    /**
     * A data pack reload starting and ending ({@code /reload}): kept, not told yet, as Kiln hears of no reload (Block
     * Variant Swapper registers for its end, D-171).
     */
    public static final Event<StartDataPackReload> START_DATA_PACK_RELOAD = Event.kiln$of(listeners -> (server, manager) -> {
        for (StartDataPackReload l : listeners) {
            l.startDataPackReload(server, manager);
        }
    });

    public static final Event<EndDataPackReload> END_DATA_PACK_RELOAD = Event.kiln$of(listeners -> (server, manager, success) -> {
        for (EndDataPackReload l : listeners) {
            l.endDataPackReload(server, manager, success);
        }
    });

    private ServerLifecycleEvents() {}

    @FunctionalInterface
    public interface StartDataPackReload {
        void startDataPackReload(MinecraftServer server,
                net.minecraft.server.packs.resources.CloseableResourceManager resourceManager);
    }

    @FunctionalInterface
    public interface EndDataPackReload {
        void endDataPackReload(MinecraftServer server,
                net.minecraft.server.packs.resources.CloseableResourceManager resourceManager, boolean success);
    }

    @FunctionalInterface
    public interface ServerStarting {
        void onServerStarting(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStarted {
        void onServerStarted(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStopping {
        void onServerStopping(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStopped {
        void onServerStopped(MinecraftServer server);
    }
}
