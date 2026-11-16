package net.minecraft.world.item.alchemy;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code PotionContents}: what a potion, a tipped arrow or a cauldron holds, its potion, a colour,
 * effects of its own and a name. It is read from the stack's {@code potion_contents} as the game saves it, a potion's
 * id or the whole object. Drinking one is the game's: Kiln doesn't apply the effects itself.
 */
public record PotionContents(Optional<Holder<Potion>> potion, Optional<Integer> customColor,
        List<MobEffectInstance> customEffects, Optional<String> customName) {
    public static final PotionContents EMPTY = new PotionContents(Optional.empty(), Optional.empty(), List.of(),
            Optional.empty());
    public static final int BASE_POTION_COLOR = -13083194;

    private static final Codec<MobEffectInstance> EFFECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("id").forGetter(MobEffectInstance::getEffect),
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(MobEffectInstance::getAmplifier),
            Codec.INT.optionalFieldOf("duration", 0).forGetter(MobEffectInstance::getDuration),
            Codec.BOOL.optionalFieldOf("ambient", false).forGetter(MobEffectInstance::isAmbient),
            Codec.BOOL.optionalFieldOf("show_particles", true).forGetter(MobEffectInstance::isVisible),
            Codec.BOOL.optionalFieldOf("show_icon").forGetter(e -> Optional.of(e.showIcon())))
            .apply(i, (effect, amplifier, duration, ambient, particles, icon) ->
                    new MobEffectInstance(effect, duration, amplifier, ambient, particles, icon.orElse(particles))));
    private static final Codec<PotionContents> FULL_CODEC = RecordCodecBuilder.create(i -> i.group(
            Potion.CODEC.optionalFieldOf("potion").forGetter(PotionContents::potion),
            Codec.INT.optionalFieldOf("custom_color").forGetter(PotionContents::customColor),
            EFFECT_CODEC.listOf().optionalFieldOf("custom_effects", List.of()).forGetter(PotionContents::customEffects),
            Codec.STRING.optionalFieldOf("custom_name").forGetter(PotionContents::customName))
            .apply(i, PotionContents::new));
    /** As the game saves it: {@code "minecraft:swiftness"}, or {@code {"potion": ..., "custom_effects": [...]}}. */
    public static final Codec<PotionContents> CODEC = Codec.either(Potion.CODEC, FULL_CODEC).xmap(
            either -> either.map(PotionContents::new, contents -> contents),
            contents -> contents.potion.isPresent() && contents.customColor.isEmpty()
                    && contents.customEffects.isEmpty() && contents.customName.isEmpty()
                    ? Either.left(contents.potion.get()) : Either.right(contents));

    public PotionContents(Holder<Potion> potion) {
        this(Optional.of(potion), Optional.empty(), List.of(), Optional.empty());
    }

    public static ItemStack createItemStack(Item item, Holder<Potion> potion) {
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return stack;
    }

    public boolean is(Holder<Potion> other) {
        return potion.isPresent() && potion.get().is(other);
    }

    public boolean isPotionWithoutCustomEffects() {
        return potion.isPresent() && customEffects.isEmpty();
    }

    /** The potion's effects, then its own. */
    public Iterable<MobEffectInstance> getAllEffects() {
        if (potion.isEmpty()) {
            return customEffects;
        }
        if (customEffects.isEmpty()) {
            return potion.get().value().getEffects();
        }
        List<MobEffectInstance> all = new ArrayList<>(potion.get().value().getEffects());
        all.addAll(customEffects);
        return all;
    }

    public void forEachEffect(java.util.function.Consumer<MobEffectInstance> consumer, float durationScale) {
        for (MobEffectInstance effect : getAllEffects()) {
            int duration = effect.getEffect().value().isInstantaneous()
                    ? effect.getDuration() : Math.round(effect.getDuration() * durationScale);
            consumer.accept(new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(),
                    effect.isAmbient(), effect.isVisible(), effect.showIcon()));
        }
    }

    public PotionContents withPotion(Holder<Potion> other) {
        return new PotionContents(Optional.of(other), customColor, customEffects, customName);
    }

    public PotionContents withEffectAdded(MobEffectInstance effect) {
        List<MobEffectInstance> effects = new ArrayList<>(customEffects);
        effects.add(effect);
        return new PotionContents(potion, customColor, List.copyOf(effects), customName);
    }

    public boolean hasEffects() {
        return getAllEffects().iterator().hasNext();
    }

    public int getColor() {
        return getColorOr(BASE_POTION_COLOR);
    }

    public int getColorOr(int fallback) {
        return customColor.orElseGet(() -> getColorOptional(getAllEffects()).orElse(fallback));
    }

    /** The effects' colours mixed, each weighed by its level, as the game mixes them; none for no visible effect. */
    public static OptionalInt getColorOptional(Iterable<MobEffectInstance> effects) {
        int r = 0;
        int g = 0;
        int b = 0;
        int total = 0;
        for (MobEffectInstance effect : effects) {
            if (effect.isVisible()) {
                int color = effect.getEffect().value().getColor();
                int weight = effect.getAmplifier() + 1;
                r += weight * ((color >> 16) & 0xFF);
                g += weight * ((color >> 8) & 0xFF);
                b += weight * (color & 0xFF);
                total += weight;
            }
        }
        if (total == 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(0xFF000000 | (r / total) << 16 | (g / total) << 8 | b / total);
    }

    /** The item's name for it: {@code item.minecraft.potion.effect.swiftness} for a prefix of {@code item.minecraft.potion.effect.}. */
    public net.minecraft.network.chat.Component getName(String prefix) {
        String name = customName.orElseGet(() -> potion.map(p -> p.value().name()).orElse("empty"));
        return net.minecraft.network.chat.Component.translatable(prefix + name);
    }

    public static net.minecraft.network.chat.MutableComponent getPotionDescription(Holder<MobEffect> effect,
            int amplifier) {
        net.minecraft.network.chat.MutableComponent name =
                net.minecraft.network.chat.Component.translatable(effect.value().getDescriptionId());
        return amplifier > 0 ? net.minecraft.network.chat.Component.translatable("potion.withAmplifier", name,
                net.minecraft.network.chat.Component.translatable("potion.potency." + amplifier)) : name;
    }
}
