package net.minecraft.world.item.alchemy;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * Kiln's stand-in for {@code Potion}: a kind of potion, its name ({@code "swiftness"}, the key of its item's name) and
 * the effects it gives. The game's own are {@link Potions}, entries of {@link BuiltInRegistries#POTION}.
 */
public class Potion implements FeatureElement {
    /** By id, as the game saves it ({@code "minecraft:swiftness"}). */
    public static final Codec<Holder<Potion>> CODEC = BuiltInRegistries.POTION.holderByNameCodec();

    private final String name;
    private final List<MobEffectInstance> effects;

    public Potion(String name, MobEffectInstance... effects) {
        this.name = name;
        this.effects = List.of(effects);
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return FeatureFlagSet.of();
    }

    public List<MobEffectInstance> getEffects() {
        return effects;
    }

    public String name() {
        return name;
    }

    public boolean hasInstantEffects() {
        for (MobEffectInstance effect : effects) {
            if (effect.getEffect().value().isInstantaneous()) {
                return true;
            }
        }
        return false;
    }
}
