package net.minecraft.world.item.component;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Kiln's stand-in for {@code ItemAttributeModifiers} (D-177): what an item adds to its holder's attributes in which
 * slots (a sword's attack damage in the main hand, a chestplate's armor on the chest), saved as the game saves it, a
 * list of {@code {"type": attribute, "id", "amount", "operation", "slot", "display"}}. Its tooltip lines are the
 * game's ({@link Display.Default#apply}); {@code kiln.ItemTooltips} writes them per slot group.
 */
public record ItemAttributeModifiers(List<ItemAttributeModifiers.Entry> modifiers) {
    public static final ItemAttributeModifiers EMPTY = new ItemAttributeModifiers(List.of());
    public static final Codec<ItemAttributeModifiers> CODEC =
            Entry.CODEC.listOf().xmap(ItemAttributeModifiers::new, ItemAttributeModifiers::modifiers);
    /** The game's: at most two decimals, no grouping, "." whatever the language. */
    public static final DecimalFormat ATTRIBUTE_MODIFIER_FORMAT =
            new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    public ItemAttributeModifiers {
        modifiers = List.copyOf(modifiers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public ItemAttributeModifiers withModifierAdded(Holder<Attribute> attribute, AttributeModifier modifier,
            EquipmentSlotGroup slot) {
        ImmutableList.Builder<Entry> next = ImmutableList.builder();
        for (Entry entry : modifiers) {
            if (!entry.matches(attribute, modifier.id())) {
                next.add(entry);
            }
        }
        next.add(new Entry(attribute, modifier, slot));
        return new ItemAttributeModifiers(next.build());
    }

    /** Each modifier of slot group {@code slot} itself (the game's: not the groups that hold its slots). */
    public void forEach(EquipmentSlotGroup slot, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        for (Entry entry : modifiers) {
            if (entry.slot().equals(slot)) {
                action.accept(entry.attribute(), entry.modifier());
            }
        }
    }

    /** Each modifier that applies in equipment slot {@code slot}. */
    public void forEach(EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        for (Entry entry : modifiers) {
            if (entry.slot().test(slot)) {
                action.accept(entry.attribute(), entry.modifier());
            }
        }
    }

    /** The sum the modifiers of {@code slot} make of {@code base}, as the game works an attribute's value out. */
    public double compute(Holder<Attribute> attribute, double base, EquipmentSlot slot) {
        double value = base;
        for (Entry entry : modifiers) {
            if (entry.slot().test(slot) && entry.attribute().is(attribute)) {
                double amount = entry.modifier().amount();
                value += switch (entry.modifier().operation()) {
                    case ADD_VALUE -> amount;
                    case ADD_MULTIPLIED_BASE -> amount * base;
                    case ADD_MULTIPLIED_TOTAL -> amount * value;
                };
            }
        }
        return value;
    }

    public static class Builder {
        private final ImmutableList.Builder<Entry> entries = ImmutableList.builder();

        Builder() {}

        public Builder add(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
            entries.add(new Entry(attribute, modifier, slot));
            return this;
        }

        public Builder add(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot,
                Display display) {
            entries.add(new Entry(attribute, modifier, slot, display));
            return this;
        }

        public ItemAttributeModifiers build() {
            return new ItemAttributeModifiers(entries.build());
        }
    }

    public record Entry(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot,
            Display display) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Attributes.CODEC.fieldOf("type").forGetter(Entry::attribute),
                AttributeModifier.MAP_CODEC.forGetter(Entry::modifier),
                EquipmentSlotGroup.CODEC.optionalFieldOf("slot", EquipmentSlotGroup.ANY).forGetter(Entry::slot),
                Display.CODEC.optionalFieldOf("display", Display.Default.INSTANCE).forGetter(Entry::display))
                .apply(i, Entry::new));

        public Entry(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
            this(attribute, modifier, slot, Display.Default.INSTANCE);
        }

        public boolean matches(Holder<Attribute> attribute, net.minecraft.resources.Identifier id) {
            return attribute.is(this.attribute) && modifier.is(id);
        }
    }

    /** How a modifier shows in a tooltip: the game's line, none, or a text of the item's own. */
    public interface Display {
        Codec<Display> CODEC = Type.CODEC.dispatch("type", Display::type, Type::codec);

        static Display hidden() {
            return Hidden.INSTANCE;
        }

        static Display override(Component text) {
            return new OverrideText(text);
        }

        Type type();

        void apply(Consumer<Component> lines, Player player, Holder<Attribute> attribute, AttributeModifier modifier);

        enum Type implements net.minecraft.util.StringRepresentable {
            DEFAULT("default"),
            HIDDEN("hidden"),
            OVERRIDE("override");

            static final Codec<Type> CODEC = net.minecraft.util.StringRepresentable.fromEnum(Type::values);

            private final String name;

            Type(String name) {
                this.name = name;
            }

            MapCodec<? extends Display> codec() {
                return switch (this) {
                    case DEFAULT -> MapCodec.unit(Default.INSTANCE);
                    case HIDDEN -> MapCodec.unit(Hidden.INSTANCE);
                    case OVERRIDE -> OverrideText.CODEC;
                };
            }

            @Override
            public String getSerializedName() {
                return name;
            }
        }

        /**
         * The game's line: with a player, an attack's base damage and speed as totals (" 7 Attack Damage", dark
         * green, the player's own base added); otherwise "+n Name" in the attribute's colour for a gain, "-n Name" for
         * a loss; a multiplier as a percentage, knockback resistance times ten.
         */
        record Default() implements Display {
            public static final Default INSTANCE = new Default();

            @Override
            public Type type() {
                return Type.DEFAULT;
            }

            @Override
            public void apply(Consumer<Component> lines, Player player, Holder<Attribute> attribute,
                    AttributeModifier modifier) {
                double amount = modifier.amount();
                boolean total = false;
                if (player != null) {
                    if (modifier.is(net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID)) {
                        amount += Attributes.PLAYER_ATTACK_DAMAGE;
                        total = true;
                    } else if (modifier.is(net.minecraft.world.item.Item.BASE_ATTACK_SPEED_ID)) {
                        amount += Attributes.ATTACK_SPEED.value().getDefaultValue();
                        total = true;
                    }
                }
                double shown;
                if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE) {
                    shown = amount * 100.0;
                } else if (attribute.is(Attributes.KNOCKBACK_RESISTANCE)) {
                    shown = amount * 10.0;
                } else {
                    shown = amount;
                }
                int operation = modifier.operation().id();
                Component name = Component.translatable(attribute.value().getDescriptionId());
                if (total) {
                    lines.accept(Component.literal(" ").append(Component.translatable(
                            "attribute.modifier.equals." + operation, ATTRIBUTE_MODIFIER_FORMAT.format(shown), name))
                            .withStyle(ChatFormatting.DARK_GREEN));
                } else if (amount > 0.0) {
                    lines.accept(Component.translatable("attribute.modifier.plus." + operation,
                            ATTRIBUTE_MODIFIER_FORMAT.format(shown), name).withStyle(attribute.value().getStyle(true)));
                } else if (amount < 0.0) {
                    lines.accept(Component.translatable("attribute.modifier.take." + operation,
                            ATTRIBUTE_MODIFIER_FORMAT.format(-shown), name).withStyle(attribute.value().getStyle(false)));
                }
            }
        }

        record Hidden() implements Display {
            public static final Hidden INSTANCE = new Hidden();

            @Override
            public Type type() {
                return Type.HIDDEN;
            }

            @Override
            public void apply(Consumer<Component> lines, Player player, Holder<Attribute> attribute,
                    AttributeModifier modifier) {}
        }

        record OverrideText(Component component) implements Display {
            static final MapCodec<OverrideText> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    ComponentSerialization.CODEC.fieldOf("value").forGetter(OverrideText::component))
                    .apply(i, OverrideText::new));

            @Override
            public Type type() {
                return Type.OVERRIDE;
            }

            @Override
            public void apply(Consumer<Component> lines, Player player, Holder<Attribute> attribute,
                    AttributeModifier modifier) {
                lines.accept(component);
            }
        }
        // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
        static net.minecraft.world.item.component.ItemAttributeModifiers.Display attributeModifiers() { kiln.Missing.called("net.minecraft.world.item.component.ItemAttributeModifiers.Display.attributeModifiers"); return kiln.Constants.nothing(net.minecraft.world.item.component.ItemAttributeModifiers.Display.class); }
        // End of what tools/kiln-stubs.py generated.
    }
}
