package net.minecraft.world.level;

import net.minecraft.core.BlockPos;

/**
 * Kiln's stand-in for {@code LevelHeightAccessor}: the heights a place spans (its lowest block and how many), and what
 * follows from them, as every {@link BlockGetter} has them. Kiln's levels give theirs (Horizon's dimension); a place
 * Kiln knows no heights of gives 0, and its log says so once.
 */
public interface LevelHeightAccessor {
    default int getHeight() {
        kiln.Missing.called("net.minecraft.world.level.LevelHeightAccessor.getHeight");
        return 0;
    }

    default int getMinY() {
        kiln.Missing.called("net.minecraft.world.level.LevelHeightAccessor.getMinY");
        return 0;
    }

    /** The highest block's y. */
    default int getMaxY() {
        return getMinY() + getHeight() - 1;
    }

    /** How many 16-block sections high it is. */
    default int getSectionsCount() {
        return getMaxSectionY() - getMinSectionY() + 1;
    }

    default int getMinSectionY() {
        return getMinY() >> 4;
    }

    default int getMaxSectionY() {
        return getMaxY() >> 4;
    }

    default boolean isInsideBuildHeight(BlockPos pos) {
        return isInsideBuildHeight(pos.getY());
    }

    default boolean isInsideBuildHeight(int y) {
        return y >= getMinY() && y <= getMaxY();
    }

    default boolean isOutsideBuildHeight(BlockPos pos) {
        return isOutsideBuildHeight(pos.getY());
    }

    default boolean isOutsideBuildHeight(int y) {
        return !isInsideBuildHeight(y);
    }

    /** The index of the section holding {@code y}, from the lowest (0). */
    default int getSectionIndex(int y) {
        return getSectionIndexFromSectionY(y >> 4);
    }

    default int getSectionIndexFromSectionY(int sectionY) {
        return sectionY - getMinSectionY();
    }

    default int getSectionYFromSectionIndex(int index) {
        return index + getMinSectionY();
    }
}
