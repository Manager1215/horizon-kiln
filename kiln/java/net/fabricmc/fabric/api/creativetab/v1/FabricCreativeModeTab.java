package net.fabricmc.fabric.api.creativetab.v1;

import net.minecraft.world.item.CreativeModeTab;

/** Kiln's stand-in for Fabric's {@code FabricCreativeModeTab}: a mod's tab, placed after the game's own. */
public final class FabricCreativeModeTab {
    private FabricCreativeModeTab() {}

    public static CreativeModeTab.Builder builder() {
        return new CreativeModeTab.Builder(CreativeModeTab.Row.TOP, 0);
    }
}
