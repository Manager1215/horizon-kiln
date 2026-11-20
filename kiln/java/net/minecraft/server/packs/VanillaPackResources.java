package net.minecraft.server.packs;

/**
 * Kiln's stand-in for {@code VanillaPackResources}: the game's built-in pack. Its resources are the game's prepared
 * ones, which Kiln reads itself where a mod asks (recipes, tags, items); the pack is there so that a mod that gathers
 * the packs it would load from (REI's local recipes, D-217) has the game's among them.
 */
public class VanillaPackResources {
    private final PackResources full = () -> "vanilla";

    public VanillaPackResources(net.minecraft.server.packs.FixedPathPackResources p0, java.util.List<net.minecraft.server.packs.PackResources> p1) {  }

    public net.minecraft.server.packs.PackResources fullResources() {
        return full;
    }

    protected VanillaPackResources() {  }

    /** Kiln's own: the game's pack, as {@code ServerPacksSource.createVanillaPackSource} gives it. */
    public static VanillaPackResources kiln$game() {
        return new VanillaPackResources();
    }
}
