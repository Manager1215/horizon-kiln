package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * An item's attribute modifiers in its tooltip, as the game's {@code ItemStack.addAttributeTooltips} writes them
 * (D-177): for each slot group in the game's order, a blank line, "When in Main Hand:" (grey), then each modifier of
 * the stack (its own, else its item's) and of its enchantments' attribute effects (Efficiency's mining efficiency),
 * by {@link ItemAttributeModifiers.Display}. And the modifiers the game gives a mod's armour and weapons.
 */
public final class ItemAttributes {
    private ItemAttributes() {}

    /** The lines, unless the stack's {@code tooltip_display} hides its attributes. */
    public static void lines(ItemStack stack, Player player, Consumer<Component> lines) {
        if (ItemTooltips.hides(stack, "minecraft:attribute_modifiers")) {
            return;
        }
        ItemAttributeModifiers own = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (own == null) {
            own = ItemAttributeModifiers.EMPTY;
        }
        List<Enchanted> enchanted = enchanted(stack);
        for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
            boolean[] first = {true};
            Consumer<Runnable> header = line -> {
                if (first[0]) {
                    first[0] = false;
                    lines.accept(CommonComponents.EMPTY);
                    lines.accept(Component.translatable("item.modifiers." + group.getSerializedName())
                            .withStyle(ChatFormatting.GRAY));
                }
                line.run();
            };
            for (ItemAttributeModifiers.Entry entry : own.modifiers()) {
                if (entry.slot().equals(group) && !ItemAttributeModifiers.Display.hidden().equals(entry.display())) {
                    header.accept(() -> entry.display().apply(lines, player, entry.attribute(), entry.modifier()));
                }
            }
            for (Enchanted e : enchanted) {
                if (e.slots().contains(group.getSerializedName())) {
                    header.accept(() -> ItemAttributeModifiers.Display.Default.INSTANCE.apply(lines, player,
                            Attributes.byId(e.attribute()), new AttributeModifier(e.id(), e.amount(), e.operation())));
                }
            }
        }
    }

    /** An attribute effect of an enchantment on the stack, at its level. */
    private record Enchanted(List<String> slots, Identifier attribute, Identifier id, double amount,
            AttributeModifier.Operation operation) {}

    /** The {@code minecraft:attributes} effects of the stack's enchantments (not a book's stored ones), from their data. */
    private static List<Enchanted> enchanted(ItemStack stack) {
        if (!(stack.kiln$saved("minecraft:enchantments") instanceof Map<?, ?> saved) || saved.isEmpty()) {
            return List.of();
        }
        Map<?, ?> levels = saved.get("levels") instanceof Map<?, ?> inner ? inner : saved;
        List<Enchanted> found = new java.util.ArrayList<>();
        for (Map.Entry<?, ?> e : levels.entrySet()) {
            if (!(e.getValue() instanceof Number level)) {
                continue;
            }
            JsonObject data = ItemTooltips.gameData("enchantment", String.valueOf(e.getKey()));
            if (data == null || !(data.get("effects") instanceof JsonObject effects)
                    || !(effects.get("minecraft:attributes") instanceof com.google.gson.JsonArray attributes)) {
                continue;
            }
            List<String> slots = new java.util.ArrayList<>();
            if (data.get("slots") instanceof com.google.gson.JsonArray list) {
                list.forEach(s -> slots.add(s.getAsString()));
            }
            for (JsonElement effect : attributes) {
                if (!(effect instanceof JsonObject o) || !o.has("attribute") || !o.has("id")) {
                    continue;
                }
                AttributeModifier.Operation operation = AttributeModifier.Operation.CODEC
                        .parse(com.mojang.serialization.JsonOps.INSTANCE, o.get("operation")).result()
                        .orElse(AttributeModifier.Operation.ADD_VALUE);
                found.add(new Enchanted(slots, Identifier.parse(o.get("attribute").getAsString()),
                        Identifier.parse(o.get("id").getAsString()), levelBased(o.get("amount"), level.intValue()),
                        operation));
            }
        }
        return found;
    }

    /** A {@code LevelBasedValue} of the game's data at {@code level}. */
    static double levelBased(JsonElement json, int level) {
        if (json == null) {
            return 0.0;
        }
        if (json.isJsonPrimitive()) {
            return json.getAsDouble();
        }
        if (!(json instanceof JsonObject o)) {
            return 0.0;
        }
        String type = o.has("type") ? o.get("type").getAsString().replace("minecraft:", "") : "linear";
        return switch (type) {
            case "linear" -> {
                double base = levelBased(o.get("base"), level);
                double per = o.has("per_level_above_first") ? levelBased(o.get("per_level_above_first"), level) : base;
                yield base + per * (level - 1);
            }
            case "levels_squared" -> level * level + (o.has("added") ? o.get("added").getAsDouble() : 0.0);
            case "clamped" -> Math.max(o.get("min").getAsDouble(),
                    Math.min(o.get("max").getAsDouble(), levelBased(o.get("value"), level)));
            case "fraction" -> {
                double denominator = levelBased(o.get("denominator"), level);
                yield denominator == 0.0 ? 0.0 : levelBased(o.get("numerator"), level) / denominator;
            }
            case "lookup" -> {
                com.google.gson.JsonArray values = o.getAsJsonArray("values");
                yield level >= 1 && level <= values.size() ? values.get(level - 1).getAsDouble()
                        : levelBased(o.get("fallback"), level);
            }
            case "exponent" -> Math.pow(levelBased(o.get("base"), level), levelBased(o.get("power"), level));
            default -> 0.0;
        };
    }

    /** A piece of armour's modifiers, as the game's {@code ArmorMaterial.createAttributes}. */
    public static ItemAttributeModifiers armor(ArmorMaterial material, ArmorType type) {
        int defense = material.defense() == null ? 0 : material.defense().getOrDefault(type, 0);
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(type.getSlot());
        Identifier id = Identifier.withDefaultNamespace("armor." + type.getName());
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(id, defense, AttributeModifier.Operation.ADD_VALUE), group)
                .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, material.toughness(),
                        AttributeModifier.Operation.ADD_VALUE), group);
        if (material.knockbackResistance() > 0.0F) {
            builder.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, material.knockbackResistance(),
                    AttributeModifier.Operation.ADD_VALUE), group);
        }
        return builder.build();
    }

    /** A weapon's or a tool's attack, in the main hand, as the game's {@code ToolMaterial.createToolAttributes}. */
    public static ItemAttributeModifiers weapon(float attackDamage, float attackSpeed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamage,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }
}
