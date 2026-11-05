package net.minecraft.world.effect;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code MobEffect}: a kind of effect, registered by id, with its category and colour. Kiln knows
 * the game's own effects by id and category, not their colours.
 */
public class MobEffect {
    private final MobEffectCategory category;
    /** Its colour, RGB; -1 for one of the game's, whose colour Kiln doesn't know. */
    private final int color;
    private final boolean instantaneous;
    private String descriptionId;

    protected MobEffect(MobEffectCategory category, int color) {
        this(category, color, false);
    }

    protected MobEffect(MobEffectCategory category, int color, ParticleOptions particle) {
        this(category, color, false);
    }

    private MobEffect(MobEffectCategory category, int color, boolean instantaneous) {
        this.category = category;
        this.color = color;
        this.instantaneous = instantaneous;
    }

    /** Kiln's own: the registry's entry for {@code id}; for one no Java mod registered, an entry of its own. */
    public static net.minecraft.core.Holder<MobEffect> kiln$entry(Identifier id) {
        return BuiltInRegistries.MOB_EFFECT.get(id).<net.minecraft.core.Holder<MobEffect>>map(h -> h)
                .orElseGet(() -> UNKNOWN.computeIfAbsent(id, i -> net.minecraft.core.Holder.Reference.kiln$of(
                        net.minecraft.resources.ResourceKey.create(BuiltInRegistries.MOB_EFFECT.key(), i),
                        new MobEffect(null, -1, false))));
    }

    /** The game's effects of content mods Java doesn't know, by id. */
    private static final java.util.Map<Identifier, net.minecraft.core.Holder<MobEffect>> UNKNOWN =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Kiln's own: one of the game's effects. */
    static MobEffect kiln$game(MobEffectCategory category, boolean instantaneous) {
        return new MobEffect(category, -1, instantaneous);
    }

    /** Its category; for a content mod's effect Java doesn't know, none (said once in Kiln's log). */
    public MobEffectCategory getCategory() {
        if (category == null) {
            kiln.Missing.called("the category of a content mod's effect");
        }
        return category;
    }

    public boolean isBeneficial() {
        return category == MobEffectCategory.BENEFICIAL;
    }

    /** Its colour, RGB: a mod's own; for one of the game's, Kiln has none (0, said once in Kiln's log). */
    public int getColor() {
        if (color == -1) {
            kiln.Missing.called("the colour of the game's effects");
            return 0;
        }
        return color;
    }

    /** Applied at once and gone (instant health and damage, saturation), rather than over time. */
    public boolean isInstantaneous() {
        return instantaneous;
    }

    /** {@code effect.<namespace>.<path>}, from its id. */
    public String getDescriptionId() {
        if (descriptionId == null) {
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(this);
            if (id == null) {
                id = UNKNOWN.entrySet().stream().filter(e -> e.getValue().value() == this).map(java.util.Map.Entry::getKey)
                        .findFirst().orElse(null);
            }
            descriptionId = id == null ? "effect.unregistered" : "effect." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        }
        return descriptionId;
    }

    public Component getDisplayName() {
        return Component.translatable(getDescriptionId());
    }
}
