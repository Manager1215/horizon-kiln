package net.minecraft.world.food;

/**
 * Kiln's stand-in for {@code FoodProperties}: what eating an item gives, as the game's {@code food} component holds it.
 * A mod's food item gets it from {@code Item.Properties.food}; Kiln tells the game at the next start (kiln.ItemFacts).
 */
public record FoodProperties(int nutrition, float saturation, boolean canAlwaysEat)
        implements net.minecraft.world.item.component.ConsumableListener {
    /** Eating it is the game's: Horizon's food component feeds the player. */
    public void onConsume(net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity entity,
            net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.component.Consumable consumable) {}

    public static class Builder {
        private int nutrition;
        private float saturationModifier;
        private boolean canAlwaysEat;

        public Builder() {}

        public Builder nutrition(int nutrition) {
            this.nutrition = nutrition;
            return this;
        }

        public Builder saturationModifier(float modifier) {
            this.saturationModifier = modifier;
            return this;
        }

        public Builder alwaysEdible() {
            this.canAlwaysEat = true;
            return this;
        }

        /** As the game's: the saturation is nutrition times the modifier, twice. */
        public FoodProperties build() {
            return new FoodProperties(nutrition, nutrition * saturationModifier * 2.0F, canAlwaysEat);
        }
    }
}
