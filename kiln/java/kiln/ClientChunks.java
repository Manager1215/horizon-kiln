package kiln;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

/**
 * The blocks of the client's chunks, for mods that read the world away from the player (D-186, #219): a scanner, a
 * minimap, an ore finder. Java asks for a chunk ({@code want_chunk x z}); the game sends its sections with its next
 * tick ({@code chunk_blocks}), each as a palette of block states and every cell's entry in it. A chunk's blocks are
 * kept for a second, then asked for again: the game says nothing when a block changes.
 *
 * <p>A mod's own thread (blockgrep scans on one) waits for the game's next tick, at most two seconds. Kiln's own
 * thread never waits (the tick that would answer is the one it runs): it gets what it has, and asks.
 */
public final class ClientChunks {
    private ClientChunks() {}

    /** How long a chunk's blocks are kept before they are asked for again, in milliseconds. */
    private static final long FRESH = 1000;
    private static final long WAIT = 2000;

    private record Kept(LevelChunkSection[] sections, long at) {}

    private static final Map<Long, Kept> KEPT = new ConcurrentHashMap<>();
    private static final Set<Long> ASKED = new HashSet<>();
    private static final Object LOCK = new Object();
    /** Kiln's own thread: the one that takes the game's lines. */
    private static volatile Thread host;

    /** The sections of the chunk at chunk x and z, from the lowest; null when the game hasn't given them. */
    public static LevelChunkSection[] sections(int x, int z) {
        long key = ChunkPos.pack(x, z);
        Kept kept = KEPT.get(key);
        long now = System.currentTimeMillis();
        if (kept != null && now - kept.at < FRESH) {
            return kept.sections;
        }
        ask(key, x, z);
        if (Thread.currentThread() == host) {
            return kept == null ? null : kept.sections;
        }
        long until = now + WAIT;
        synchronized (LOCK) {
            while (true) {
                Kept fresh = KEPT.get(key);
                if (fresh != null && fresh != kept) {
                    return fresh.sections;
                }
                long left = until - System.currentTimeMillis();
                if (left <= 0 || Thread.currentThread() == host) {
                    return kept == null ? null : kept.sections;
                }
                try {
                    LOCK.wait(left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return kept == null ? null : kept.sections;
                }
            }
        }
    }

    /** The block at a position, from the chunk's sections the game gave; null when it gave none. */
    public static BlockState block(int x, int y, int z, int minSectionY) {
        LevelChunkSection[] sections = sections(x >> 4, z >> 4);
        if (sections == null) {
            return null;
        }
        int index = (y >> 4) - minSectionY;
        if (index < 0 || index >= sections.length) {
            return null;
        }
        return sections[index].getBlockState(x & 15, y & 15, z & 15);
    }

    private static void ask(long key, int x, int z) {
        synchronized (LOCK) {
            if (!ASKED.add(key)) {
                return;
            }
        }
        Host.send("want_chunk", Integer.toString(x), Integer.toString(z));
    }

    /**
     * Kiln's own: a chunk's sections from the game ({@code chunk_blocks x z sections}): each section's palette of
     * block states joined by {@code |}, then {@code #} and every cell's entry as hex (two digits each, four with more
     * than 256 states; none for a section of one state); the sections joined by {@code ;}, from the lowest.
     */
    public static void put(String x, String z, String sections) {
        host = Thread.currentThread();
        long key = ChunkPos.pack(Integer.parseInt(x), Integer.parseInt(z));
        List<LevelChunkSection> parsed = new ArrayList<>();
        Map<String, BlockState> states = new java.util.HashMap<>();
        for (String section : sections.isEmpty() ? new String[0] : sections.split(";", -1)) {
            int hash = section.indexOf('#');
            String[] names = (hash < 0 ? section : section.substring(0, hash)).split("\\|");
            List<BlockState> palette = new ArrayList<>(names.length);
            for (String name : names) {
                palette.add(states.computeIfAbsent(name.isEmpty() ? "minecraft:air" : name, BlockState::kiln$parse));
            }
            String cells = hash < 0 ? "" : section.substring(hash + 1);
            short[] entries = null;
            if (!cells.isEmpty()) {
                int width = cells.length() / 4096;
                entries = new short[4096];
                for (int i = 0; i < 4096; i++) {
                    entries[i] = (short) Integer.parseInt(cells, i * width, i * width + width, 16);
                }
            }
            parsed.add(new LevelChunkSection(PalettedContainer.kiln$of(palette, entries), null));
        }
        synchronized (LOCK) {
            KEPT.put(key, new Kept(parsed.toArray(new LevelChunkSection[0]), System.currentTimeMillis()));
            ASKED.remove(key);
            LOCK.notifyAll();
        }
    }

    /** Kiln's own: the thread calling is Kiln's own, which never waits for the game. */
    public static void host() {
        host = Thread.currentThread();
    }

    /** Kiln's own: a chunk the game no longer has. */
    public static void forget(long key) {
        KEPT.remove(key);
    }

    /** Kiln's own: the level was left. */
    public static void clear() {
        synchronized (LOCK) {
            KEPT.clear();
            ASKED.clear();
            LOCK.notifyAll();
        }
    }
}
