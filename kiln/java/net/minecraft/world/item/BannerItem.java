package net.minecraft.world.item;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Kiln's stand-in for {@code BannerItem}: a banner's item and its base color. The game's 16 banners are these too
 * ({@link Item#kiln$byId}), so a mod that keeps the banners by their class (a recipe viewer's shield recipes) finds
 * them.
 */
public class BannerItem extends StandingAndWallBlockItem {
    private final DyeColor color;

    public BannerItem(Block standing, Block wall, Item.Properties properties) {
        super(standing, wall, net.minecraft.core.Direction.DOWN, properties);
        this.color = DyeColor.WHITE;
    }

    /** Kiln's own: a banner of {@code color}. */
    public BannerItem(Block standing, Block wall, DyeColor color, Item.Properties properties) {
        super(standing, wall, net.minecraft.core.Direction.DOWN, properties);
        this.color = color;
    }

    /** Kiln's own: the game's banner of {@code color}, called {@code id}, its blocks found when asked. */
    BannerItem(Identifier id, DyeColor color, Item.Properties properties) {
        super(id, Identifier.fromNamespaceAndPath(id.getNamespace(), color.getName() + "_wall_banner"), properties);
        this.color = color;
    }

    public DyeColor getColor() {
        return color;
    }
}
