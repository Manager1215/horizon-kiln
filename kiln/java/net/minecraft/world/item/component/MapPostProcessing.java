package net.minecraft.world.item.component;

/**
 * Kiln's stand-in for {@code MapPostProcessing} ({@code minecraft:map_post_processing}): what the game does to a map
 * item's map once it is in an inventory, a copy locked or zoomed out (a map extended at a crafting table).
 */
public enum MapPostProcessing {
    LOCK(0),
    SCALE(1);

    private final int id;

    MapPostProcessing(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }
}
