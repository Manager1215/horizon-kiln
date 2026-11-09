package kiln;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * The players on Horizon's integrated server, for Fabric's {@code PlayerLookup} and the server's player list. Kiln's Java
 * asks the game during a server call ({@code ask <call> players}), once a call, and the game answers {@code
 * <player>;<player>...}, each {@code <uuid> <name> <dimension> <x> <y> <z> <game mode> <health> <view distance>}: the
 * players as they were when the server's tick began, the call's own player as they are now, and the view distance as
 * the radius of chunks that player is sent.
 *
 * <p>A player the call describes (the one using an item, joining, leaving...) is the same object here as in the call.
 * The others are quiet: what a mod's code changes on them goes nowhere, as Kiln can only change the call's player, but
 * a payload sent to them goes ({@link Net} sends by UUID). Outside a call there is nobody to ask: the last players
 * known. Nor in a call the game gave up on (Java answered past its time): the last players known, with the call's own.
 */
public final class Players {
    /** A player on the server, and the radius of chunks they are sent ({@code ChunkTrackingView}'s). */
    public record Online(ServerPlayer player, int viewDistance) {}

    private static String call;
    private static long gameTime;
    private static SavedDataStorage storage;
    /** The players the running call describes, by UUID. */
    private static final Map<UUID, ServerPlayer> described = new LinkedHashMap<>();
    /** The players the game named this call; null until asked. */
    private static List<Online> online;
    private static List<Online> last = List.of();

    private Players() {}

    /** A server call begins: no players asked yet. */
    static void begin(String number, long time, SavedDataStorage data) {
        call = number;
        gameTime = time;
        storage = data;
        described.clear();
        online = null;
    }

    /** The call ended. */
    static void end() {
        call = null;
        described.clear();
        online = null;
    }

    /** A player the running call describes: the lookups hand out this one for them. */
    static void described(ServerPlayer player) {
        if (call != null) {
            described.put(player.getUUID(), player);
        }
    }

    /** Every player on the server, in the order the game lists them. */
    public static List<Online> online() {
        if (call == null) {
            return last;
        }
        if (online == null) {
            try {
                online = read(Server.ask("players"));
                last = online;
            } catch (Host.GaveUp e) {
                // A call Java answers late (a payload, a player joining: D-088) has nobody to ask any more, but what
                // it does is still done when it comes: the last players known, with the call's own as they are.
                online = known();
            }
        }
        return online;
    }

    /** The last players known, the ones the running call describes in their place (or added, as {@link #read}). */
    static List<Online> known() {
        List<Online> players = new ArrayList<>();
        Map<UUID, ServerPlayer> left = new LinkedHashMap<>(described);
        for (Online o : last) {
            ServerPlayer player = left.remove(o.player().getUUID());
            players.add(player == null ? o : new Online(player, o.viewDistance()));
        }
        for (ServerPlayer p : left.values()) {
            players.add(new Online(p, MIN_VIEW));
        }
        return List.copyOf(players);
    }

    /** The players on the server that {@code which} takes, as a collection mods can't change. */
    public static Collection<ServerPlayer> where(Predicate<Online> which) {
        List<ServerPlayer> players = new ArrayList<>();
        for (Online o : online()) {
            if (which.test(o)) {
                players.add(o.player());
            }
        }
        return List.copyOf(players);
    }

    /** The game's answer, read: a player it doesn't describe whole is left out; one the call describes is added. */
    static List<Online> read(String answer) {
        List<Online> players = new ArrayList<>();
        Map<UUID, ServerPlayer> left = new LinkedHashMap<>(described);
        Map<String, ServerLevel> levels = new LinkedHashMap<>();
        for (ServerPlayer p : described.values()) {
            levels.putIfAbsent(dimension(p.level()), p.level());
        }
        for (String entry : answer.split(";")) {
            String[] f = entry.split(" ");
            if (f.length != 9) {
                continue;
            }
            try {
                UUID uuid = UUID.fromString(f[0]);
                int view = Integer.parseInt(f[8]);
                ServerPlayer player = left.remove(uuid);
                if (player == null) {
                    ServerLevel level = levels.computeIfAbsent(f[2], d -> ServerLevel.kiln$of(d, gameTime, storage));
                    player = new ServerPlayer(uuid, f[1], level, Double.parseDouble(f[3]), Double.parseDouble(f[4]),
                            Double.parseDouble(f[5]), Integer.parseInt(f[6]), Float.parseFloat(f[7]));
                    player.kiln$quiet();
                }
                players.add(new Online(player, view));
            } catch (IllegalArgumentException e) {
                // Not a player as the game writes one: left out.
            }
        }
        // The call's player is on the server, whether or not the game had them when its tick began.
        for (ServerPlayer p : left.values()) {
            players.add(new Online(p, MIN_VIEW));
        }
        return List.copyOf(players);
    }

    /** The smallest view distance the game sends a player (vanilla's 2). */
    static final int MIN_VIEW = 2;

    /** A level's dimension as the game names it ({@code minecraft:overworld}). */
    public static String dimension(net.minecraft.world.level.Level level) {
        return level.dimension().identifier().toString();
    }

    /**
     * Whether a player sent the chunks around chunk {@code centerX}, {@code centerZ} at {@code view} has chunk {@code x},
     * {@code z}: the game sends a round area, the chunks whose nearest corner is closer than the view distance plus
     * one, the ring of chunks just past the edge included.
     */
    public static boolean sees(int centerX, int centerZ, int view, int x, int z) {
        long dx = Math.max(0, Math.abs((long) x - centerX) - 1);
        long dz = Math.max(0, Math.abs((long) z - centerZ) - 1);
        long radius = Math.max(view, MIN_VIEW) + 1L;
        return dx * dx + dz * dz < radius * radius;
    }
}
