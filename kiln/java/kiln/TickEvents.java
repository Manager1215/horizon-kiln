package kiln;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Fabric's server tick events, fired where Horizon's server ticks: {@code START_SERVER_TICK} before its levels tick,
 * each level's {@code START_LEVEL_TICK} and {@code END_LEVEL_TICK} around that level's tick (its block entities in
 * it), {@code END_SERVER_TICK} after the last. Each is a server call of its own ({@code server_tick <phase>
 * [<dimension>]}), made only while a mod listens to that event: Tech Reborn's cable networks tick by a counter
 * {@code START_SERVER_TICK} moves on, RebornCore's chunk loaders on {@code START_LEVEL_TICK}.
 */
public final class TickEvents {
    /** The failures said in Kiln's log: each listener's once, not twenty times a second. */
    private static final Set<String> SAID = ConcurrentHashMap.newKeySet();

    private TickEvents() {}

    /**
     * Runs {@code call} for each of {@code listeners}, in order. A listener that fails (a mod calling what Kiln has
     * no stand-in for) is said once in Kiln's log and the others still run; a game that gave up on the call stops
     * them all ({@link Host.GaveUp}).
     */
    public static <T> void each(String event, List<T> listeners, Consumer<T> call) {
        for (T listener : listeners) {
            try {
                call.accept(listener);
            } catch (Host.GaveUp e) {
                throw e;
            } catch (RuntimeException | LinkageError e) {
                if (SAID.add(event + " " + listener.getClass().getName() + " " + e)) {
                    Host.send("log", "a mod's " + event + " listener failed (said once): " + e);
                    e.printStackTrace();
                }
            }
        }
    }

    /** The {@code server_tick} call: the event of {@code phase}, the level's for a level's phase. */
    static String fire(String phase, ServerLevel level) {
        switch (phase) {
            case "start" -> ServerTickEvents.START_SERVER_TICK.invoker().onStartTick(MinecraftServer.kiln$server());
            case "end" -> ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(MinecraftServer.kiln$server());
            case "level_start" -> ServerTickEvents.START_LEVEL_TICK.invoker().onStartTick(level);
            case "level_end" -> ServerTickEvents.END_LEVEL_TICK.invoker().onEndTick(level);
            default -> throw new IllegalArgumentException("unknown tick phase " + phase);
        }
        return "pass";
    }

    /** The tick events the mods listen to, as the game's calls name them, added to {@code events}. */
    static void heard(List<String> events) {
        if (ServerTickEvents.START_SERVER_TICK.kiln$heard()) {
            events.add("server_tick_start");
        }
        if (ServerTickEvents.END_SERVER_TICK.kiln$heard()) {
            events.add("server_tick_end");
        }
        if (ServerTickEvents.START_LEVEL_TICK.kiln$heard()) {
            events.add("level_tick_start");
        }
        if (ServerTickEvents.END_LEVEL_TICK.kiln$heard()) {
            events.add("level_tick_end");
        }
    }
}
