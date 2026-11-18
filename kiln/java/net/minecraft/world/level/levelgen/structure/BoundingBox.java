package net.minecraft.world.level.levelgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;

/**
 * Kiln's stand-in for {@code BoundingBox}: a box of whole blocks, both corners in it. As vanilla's, {@code encapsulate}
 * and {@code move} change it; the rest make a new one.
 */
public class BoundingBox {
    /** Six ints, the least corner first, as the game saves a box. */
    public static final Codec<BoundingBox> CODEC = Codec.INT_STREAM.comapFlatMap(stream -> {
        int[] i = stream.limit(7).toArray();
        return i.length == 6 ? DataResult.success(new BoundingBox(i[0], i[1], i[2], i[3], i[4], i[5]))
                : DataResult.error(() -> "Input is not a list of 6 ints");
    }, box -> IntStream.of(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)).stable();

    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    public BoundingBox(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    /** From its corners; inverted bounds are put in order, as vanilla does. */
    public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    protected BoundingBox() {
        this(0, 0, 0, 0, 0, 0);
    }

    public static BoundingBox fromCorners(Vec3i a, Vec3i b) {
        return new BoundingBox(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    public static BoundingBox infinite() {
        return new BoundingBox(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /** The box around all of {@code positions}; none when there are none. */
    public static Optional<BoundingBox> encapsulatingPositions(Iterable<BlockPos> positions) {
        Iterator<BlockPos> each = positions.iterator();
        if (!each.hasNext()) {
            return Optional.empty();
        }
        BoundingBox box = new BoundingBox(each.next());
        each.forEachRemaining(box::encapsulate);
        return Optional.of(box);
    }

    /** The chunks the box reaches into, column by column. */
    public Stream<ChunkPos> intersectingChunks() {
        int fromX = minX >> 4, toX = maxX >> 4, fromZ = minZ >> 4, toZ = maxZ >> 4;
        return IntStream.rangeClosed(fromX, toX).boxed()
                .flatMap(x -> IntStream.rangeClosed(fromZ, toZ).mapToObj(z -> new ChunkPos(x, z)));
    }

    public boolean intersects(BoundingBox other) {
        return maxX >= other.minX && minX <= other.maxX && maxZ >= other.minZ && minZ <= other.maxZ
                && maxY >= other.minY && minY <= other.maxY;
    }

    /** Whether the box meets the columns from x, z to x, z (both included), at any height. */
    public boolean intersects(int fromX, int fromZ, int toX, int toZ) {
        return maxX >= fromX && minX <= toX && maxZ >= fromZ && minZ <= toZ;
    }

    /** This box, grown to hold {@code other} too. */
    public BoundingBox encapsulate(BoundingBox other) {
        minX = Math.min(minX, other.minX);
        minY = Math.min(minY, other.minY);
        minZ = Math.min(minZ, other.minZ);
        maxX = Math.max(maxX, other.maxX);
        maxY = Math.max(maxY, other.maxY);
        maxZ = Math.max(maxZ, other.maxZ);
        return this;
    }

    /** This box, grown to hold {@code pos} too. */
    public BoundingBox encapsulate(BlockPos pos) {
        return encapsulate(new BoundingBox(pos));
    }

    /** This box, moved by x, y, z. */
    public BoundingBox move(int x, int y, int z) {
        minX += x;
        minY += y;
        minZ += z;
        maxX += x;
        maxY += y;
        maxZ += z;
        return this;
    }

    /** A new box, this one moved by x, y, z. */
    public BoundingBox moved(int x, int y, int z) {
        return new BoundingBox(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
    }

    public BoundingBox inflatedBy(int by) {
        return new BoundingBox(minX - by, minY - by, minZ - by, maxX + by, maxY + by, maxZ + by);
    }

    /** From the least corner to the greatest. */
    public Vec3i getLength() {
        return new Vec3i(maxX - minX, maxY - minY, maxZ - minZ);
    }

    public int getXSpan() {
        return maxX - minX + 1;
    }

    public int getYSpan() {
        return maxY - minY + 1;
    }

    public int getZSpan() {
        return maxZ - minZ + 1;
    }

    public BlockPos getCenter() {
        return new BlockPos(minX + (maxX - minX + 1) / 2, minY + (maxY - minY + 1) / 2, minZ + (maxZ - minZ + 1) / 2);
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public boolean isInside(Vec3i pos) {
        return isInside(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean isInside(int x, int y, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof BoundingBox b && minX == b.minX && minY == b.minY && minZ == b.minZ
                && maxX == b.maxX && maxY == b.maxY && maxZ == b.maxZ;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public String toString() {
        return "BoundingBox{minX=" + minX + ", minY=" + minY + ", minZ=" + minZ + ", maxX=" + maxX + ", maxY=" + maxY
                + ", maxZ=" + maxZ + "}";
    }
}
