package net.minecraft.world.item.component;

/** Kiln's stand-in for {@code Consumables}: the game's usual ways to eat and drink. */
public class Consumables {
    public static final Consumable DEFAULT_FOOD = defaultFood().build();
    public static final Consumable DEFAULT_DRINK = defaultDrink().build();

    public Consumables() {}

    public static Consumable.Builder defaultFood() {
        return Consumable.builder().consumeSeconds(1.6F).animation(net.minecraft.world.item.ItemUseAnimation.EAT)
                .sound(net.minecraft.sounds.SoundEvents.GENERIC_EAT).hasConsumeParticles(true);
    }

    public static Consumable.Builder defaultDrink() {
        return Consumable.builder().consumeSeconds(1.6F).animation(net.minecraft.world.item.ItemUseAnimation.DRINK)
                .sound(net.minecraft.sounds.SoundEvents.GENERIC_DRINK).hasConsumeParticles(false);
    }
}
