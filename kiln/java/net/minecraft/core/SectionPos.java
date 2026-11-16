package net.minecraft.core;

/** Kiln's stand-in for {@code SectionPos}: a 16-block cube of a level; here, the block-to-section arithmetic. */
public class SectionPos extends Vec3i {
    protected SectionPos() {
        super(0, 0, 0);
    }

    /** The section (or chunk) coordinate of a block coordinate. */
    public static int blockToSectionCoord(int block) {
        return block >> 4;
    }

    public static int blockToSectionCoord(double block) {
        return net.minecraft.util.Mth.floor(block) >> 4;
    }

    /** The first block coordinate of a section. */
    public static int sectionToBlockCoord(int section) {
        return section << 4;
    }

    public static int sectionToBlockCoord(int section, int offset) {
        return (section << 4) + offset;
    }

    /** A block coordinate's place in its section, 0 to 15. */
    public static int sectionRelative(int block) {
        return block & 15;
    }
}
