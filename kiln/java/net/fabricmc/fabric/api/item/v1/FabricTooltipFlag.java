package net.fabricmc.fabric.api.item.v1;

/**
 * Kiln's stand-in for Fabric's {@code FabricTooltipFlag}: whether a tooltip shows everything it can (a search's, which
 * Fabric adds onto the game's TooltipFlag). The game's own flags say no.
 */
public interface FabricTooltipFlag {
    default boolean shouldDisplayAllInformation() {
        return false;
    }
}
