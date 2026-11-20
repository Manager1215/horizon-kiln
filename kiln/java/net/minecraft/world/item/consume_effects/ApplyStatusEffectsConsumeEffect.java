package net.minecraft.world.item.consume_effects;

/**
 * Kiln's stand-in for {@code ApplyStatusEffectsConsumeEffect}: the effects eating an item gives, with a chance. Applying
 * them is the game's (Horizon's consumable component, which Kiln gives it at the next start: kiln.ItemFacts).
 */
public record ApplyStatusEffectsConsumeEffect(java.util.List<net.minecraft.world.effect.MobEffectInstance> effects,
        float probability) implements ConsumeEffect {
    public ApplyStatusEffectsConsumeEffect(net.minecraft.world.effect.MobEffectInstance effect, float probability) {
        this(java.util.List.of(effect), probability);
    }

    public ApplyStatusEffectsConsumeEffect(java.util.List<net.minecraft.world.effect.MobEffectInstance> effects) {
        this(effects, 1.0F);
    }

    public ApplyStatusEffectsConsumeEffect(net.minecraft.world.effect.MobEffectInstance effect) {
        this(effect, 1.0F);
    }

    public ConsumeEffect.Type<ApplyStatusEffectsConsumeEffect> getType() {
        return ConsumeEffect.Type.APPLY_EFFECTS;
    }

    public boolean apply(net.minecraft.world.level.Level level, net.minecraft.world.item.ItemStack stack,
            net.minecraft.world.entity.LivingEntity entity) {
        kiln.Missing.called("net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect.apply");
        return false;
    }
}
