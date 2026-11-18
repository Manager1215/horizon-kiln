package net.minecraft.nbt;

/**
 * Kiln's stand-in for {@code NbtAccounter}: how many bytes and how deep a compound read may go. Kiln's reads count
 * neither ({@code NbtIo}): nothing is used.
 */
public class NbtAccounter {
    public NbtAccounter(long quota, int maxDepth) {}

    /** No quota, the game's deepest compound (512). */
    public static NbtAccounter unlimitedHeap() {
        return new NbtAccounter(Long.MAX_VALUE, 512);
    }

    /** {@code quota} bytes, the game's deepest compound (512). */
    public static NbtAccounter create(long quota) {
        return new NbtAccounter(quota, 512);
    }

    public long getUsage() {
        return 0;
    }

    public int getDepth() {
        return 0;
    }
}
