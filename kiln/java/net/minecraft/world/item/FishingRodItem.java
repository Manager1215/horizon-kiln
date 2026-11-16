package net.minecraft.world.item;

/**
 * Kiln's stand-in for {@code FishingRodItem}: the game's fishing rod ({@code Items.FISHING_ROD}). Horizon casts and
 * reels it in; using it from a mod goes to Horizon ({@code MultiPlayerGameMode.useItem}).
 */
public class FishingRodItem extends Item {
    public FishingRodItem(Item.Properties properties) {
        super(properties);
    }
}
