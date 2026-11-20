package net.minecraft.world.item.component;

/**
 * Kiln's stand-in for {@code Consumable}: how an item is eaten or drunk, as the game's {@code consumable} component
 * holds it. Consuming is the game's (Horizon's component); Kiln tells the game a mod item's at the next start
 * (kiln.ItemFacts).
 */
public record Consumable(float consumeSeconds, net.minecraft.world.item.ItemUseAnimation animation,
        net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, boolean hasConsumeParticles,
        java.util.List<net.minecraft.world.item.consume_effects.ConsumeEffect> onConsumeEffects) {
    public static final float DEFAULT_CONSUME_SECONDS = 1.6F;

    public static Builder builder() {
        return new Builder();
    }

    public net.minecraft.world.item.ItemStack onConsume(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack) {
        kiln.Missing.called("net.minecraft.world.item.component.Consumable.onConsume");
        return stack;
    }

    public boolean canConsume(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack) {
        return true;
    }

    public static interface OverrideConsumeSound {
        net.minecraft.sounds.SoundEvent getConsumeSound(net.minecraft.world.item.ItemStack stack);
    }

    public static class Builder {
        private float consumeSeconds = DEFAULT_CONSUME_SECONDS;
        private net.minecraft.world.item.ItemUseAnimation animation = net.minecraft.world.item.ItemUseAnimation.EAT;
        private net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound =
                net.minecraft.sounds.SoundEvents.GENERIC_EAT;
        private boolean hasConsumeParticles = true;
        private final java.util.List<net.minecraft.world.item.consume_effects.ConsumeEffect> onConsumeEffects =
                new java.util.ArrayList<>();

        protected Builder() {}

        public Builder consumeSeconds(float seconds) {
            this.consumeSeconds = seconds;
            return this;
        }

        public Builder animation(net.minecraft.world.item.ItemUseAnimation animation) {
            this.animation = animation;
            return this;
        }

        public Builder sound(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound) {
            this.sound = sound;
            return this;
        }

        public Builder hasConsumeParticles(boolean particles) {
            this.hasConsumeParticles = particles;
            return this;
        }

        public Builder onConsume(net.minecraft.world.item.consume_effects.ConsumeEffect effect) {
            this.onConsumeEffects.add(effect);
            return this;
        }

        public Consumable build() {
            return new Consumable(consumeSeconds, animation, sound, hasConsumeParticles,
                    java.util.List.copyOf(onConsumeEffects));
        }
    }
}
