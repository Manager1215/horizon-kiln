import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * A recipe viewer's view of the game's items with components (D-164), for kiln/tests/jei_items.rs: each stack's
 * tooltip as JEI gets it ({@code ItemStack.getTooltipLines}), its lines' runs with their colours and formats; the ops
 * that draw a stack and styled text; and what an enchanting table and an anvil take. It runs as a source file with
 * kiln-runtime.jar on the class path and {@code -Dkiln.resources}. One result a line on stdout:
 *
 * <ul>
 *   <li>{@code tooltip <name>} then {@code line <n> <runs>}, a run {@code [#rrggbb|-][bold ...]text} with "|" between;
 *   <li>{@code op <op>}: an op as GuiGraphicsExtractor writes it;
 *   <li>{@code check <what> <true|false>}.
 * </ul>
 */
public class JeiItems {
    static final String[][] STACKS = {
        {"book", "{id:\"minecraft:enchanted_book\",count:1,components:{\"minecraft:stored_enchantments\":"
                + "{\"minecraft:sharpness\":5,\"minecraft:binding_curse\":1}}}"},
        {"potion", "{id:\"minecraft:potion\",count:1,components:{\"minecraft:potion_contents\":"
                + "{potion:\"minecraft:swiftness\"}}}"},
        {"lingering", "{id:\"minecraft:lingering_potion\",count:1,components:{\"minecraft:potion_contents\":"
                + "\"minecraft:strong_poison\"}}"},
        {"arrow", "{id:\"minecraft:tipped_arrow\",count:1,components:{\"minecraft:potion_contents\":"
                + "\"minecraft:long_night_vision\"}}"},
        {"water", "{id:\"minecraft:potion\",count:1,components:{\"minecraft:potion_contents\":\"minecraft:water\"}}"},
        {"chestplate", "{id:\"minecraft:diamond_chestplate\",count:1,components:{\"minecraft:trim\":"
                + "{material:\"minecraft:gold\",pattern:\"minecraft:coast\"},"
                + "\"minecraft:enchantments\":{\"minecraft:protection\":4,\"minecraft:mending\":1}}}"},
        {"helmet", "{id:\"minecraft:leather_helmet\",count:1,components:{\"minecraft:dyed_color\":16711680,"
                + "\"minecraft:unbreakable\":{}}}"},
        {"hidden", "{id:\"minecraft:diamond_sword\",count:1,components:{\"minecraft:enchantments\":"
                + "{\"minecraft:sharpness\":1},\"minecraft:tooltip_display\":"
                + "{hidden_components:[\"minecraft:enchantments\"]}}}"},
        {"stick", "{id:\"minecraft:stick\",count:1}"},
        {"strength", "{id:\"minecraft:splash_potion\",count:1,components:{\"minecraft:potion_contents\":"
                + "\"minecraft:strong_strength\"}}"},
        {"weakness", "{id:\"minecraft:potion\",count:1,components:{\"minecraft:potion_contents\":"
                + "\"minecraft:weakness\"}}"},
        {"horn", "{id:\"minecraft:goat_horn\",count:1,components:{\"minecraft:instrument\":"
                + "\"minecraft:sing_goat_horn\"}}"},
        {"ponder", "{id:\"minecraft:goat_horn\",count:1}"},
        {"disc", "{id:\"minecraft:music_disc_13\",count:1}"},
        {"rocket", "{id:\"minecraft:firework_rocket\",count:1,components:{\"minecraft:fireworks\":{flight_duration:2b,"
                + "explosions:[{shape:\"star\",colors:[I;11743532],has_trail:1b},{shape:\"star\",colors:[I;11743532],"
                + "has_trail:1b}]}}}"},
        {"star", "{id:\"minecraft:firework_star\",count:1,components:{\"minecraft:firework_explosion\":{shape:\"creeper\","
                + "colors:[I;11743532,123],fade_colors:[I;15435844],has_twinkle:1b}}}"},
        {"bottle", "{id:\"minecraft:ominous_bottle\",count:1,components:{\"minecraft:ominous_bottle_amplifier\":2}}"},
        {"banner", "{id:\"minecraft:white_banner\",count:1,components:{\"minecraft:banner_patterns\":"
                + "[{color:\"cyan\",pattern:\"minecraft:rhombus\"}]}}"},
        {"painting", "{id:\"minecraft:painting\",count:1,components:{\"minecraft:painting/variant\":\"minecraft:alban\"}}"},
        {"horn2", "{id:\"minecraft:goat_horn\",count:1,components:{\"minecraft:instrument\":"
                + "\"minecraft:sing_goat_horn\"}}"},
        // D-177: a written book, a map, a shulker box's contents, attributes (a sword's, an enchantment's, hidden).
        {"written", "{id:\"minecraft:written_book\",count:1,components:{\"minecraft:written_book_content\":"
                + "{title:{raw:\"Field Notes\"},author:\"Alex\",generation:1,pages:[]}}}"},
        {"tattered", "{id:\"minecraft:written_book\",count:1,components:{\"minecraft:written_book_content\":"
                + "{title:\"Old\",author:\"\",generation:3}}}"},
        {"map", "{id:\"minecraft:filled_map\",count:1,components:{\"minecraft:map_id\":7}}"},
        {"shulker", "{id:\"minecraft:shulker_box\",count:1,components:{\"minecraft:container\":["
                + "{slot:3,item:{id:\"minecraft:stick\",count:2}},{slot:0,item:{id:\"minecraft:diamond\",count:3}},"
                + "{slot:4,item:{id:\"minecraft:dirt\",count:1}},{slot:5,item:{id:\"minecraft:dirt\",count:1}},"
                + "{slot:6,item:{id:\"minecraft:dirt\",count:1}},{slot:7,item:{id:\"minecraft:dirt\",count:1}},"
                + "{slot:8,item:{id:\"minecraft:dirt\",count:1}}]}}"},
        {"sword", "{id:\"minecraft:diamond_sword\",count:1}"},
        {"pickaxe", "{id:\"minecraft:diamond_pickaxe\",count:1,components:{\"minecraft:enchantments\":"
                + "{\"minecraft:efficiency\":5}}}"},
        {"leggings", "{id:\"minecraft:netherite_leggings\",count:1}"},
        {"no_attributes", "{id:\"minecraft:iron_sword\",count:1,components:{\"minecraft:tooltip_display\":"
                + "{hidden_components:[\"minecraft:attribute_modifiers\"]}}}"},
        {"own_attributes", "{id:\"minecraft:stick\",count:1,components:{\"minecraft:attribute_modifiers\":["
                + "{type:\"minecraft:movement_speed\",id:\"kiln:test\",amount:0.1d,operation:\"add_multiplied_base\","
                + "slot:\"feet\"},{type:\"minecraft:gravity\",id:\"kiln:test2\",amount:-0.5d,operation:\"add_value\","
                + "slot:\"any\"},{type:\"minecraft:luck\",id:\"kiln:test3\",amount:1.0d,operation:\"add_value\","
                + "slot:\"offhand\",display:{type:\"hidden\"}}]}}"},
        {"bundle", "{id:\"minecraft:bundle\",count:1,components:{\"minecraft:bundle_contents\":["
                + "{id:\"minecraft:diamond\",count:3},{id:\"minecraft:stick\",count:1}]}}"},
        {"empty_bundle", "{id:\"minecraft:red_bundle\",count:1}"},
    };

    static ItemStack stack(String name) {
        for (String[] s : STACKS) {
            if (s[0].equals(name)) {
                return ItemStack.kiln$fromSnbt(s[1]);
            }
        }
        throw new IllegalArgumentException(name);
    }

    public static void main(String[] args) throws Exception {
        var hostOut = Class.forName("kiln.Host").getDeclaredField("out");
        hostOut.setAccessible(true);
        hostOut.set(null, new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        for (String[] s : STACKS) {
            ItemStack stack = ItemStack.kiln$fromSnbt(s[1]);
            out.println("tooltip " + s[0]);
            List<Component> lines = stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL);
            for (int i = 0; i < lines.size(); i++) {
                out.println("line " + i + " " + runs(lines.get(i)));
            }
        }
        // D-177: what needs more than the stack. A player looks: a weapon's attack as their totals.
        net.minecraft.world.entity.player.Player player = new net.minecraft.client.player.LocalPlayer();
        print(out, "player_sword", stack("sword").getTooltipLines(Item.TooltipContext.EMPTY, player, TooltipFlag.NORMAL));
        // A map the window has the data of (kiln.Maps, as the window tells it): its id and lock; its scale with
        // advanced tooltips.
        kiln.Maps.update("7:2:1,9:0:0");
        print(out, "map_known", stack("map").getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL));
        print(out, "map_advanced", stack("map").getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.ADVANCED));
        kiln.Maps.update("");
        // A mod's armour and sword, made as Tech Reborn makes them (humanoidArmor, sword): their modifiers as the game
        // gives them.
        net.minecraft.resources.Identifier armorId = net.minecraft.resources.Identifier.fromNamespaceAndPath("kiln_test",
                "bronze_chestplate");
        net.minecraft.world.item.equipment.ArmorMaterial bronze = new net.minecraft.world.item.equipment.ArmorMaterial(
                12, java.util.Map.of(net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, 6), 15, null, 1.0F, 0.1F,
                null, null);
        Item armor = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM, armorId,
                new Item(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.ITEM, armorId)).stacksTo(1).humanoidArmor(bronze,
                        net.minecraft.world.item.equipment.ArmorType.CHESTPLATE)));
        print(out, "mod_armor", new ItemStack(armor).getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL));
        out.println("check mod_armor_durability " + new ItemStack(armor).getMaxDamage());
        out.println("check mod_armor_slot " + new ItemStack(armor).get(
                net.minecraft.core.component.DataComponents.EQUIPPABLE).slot());
        net.minecraft.resources.Identifier swordId = net.minecraft.resources.Identifier.fromNamespaceAndPath("kiln_test",
                "ruby_sword");
        Item modSword = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM,
                swordId, new Item(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.ITEM, swordId))
                        .sword(net.minecraft.world.item.ToolMaterial.DIAMOND, 3.0F, -2.4F)));
        print(out, "mod_sword", new ItemStack(modSword).getTooltipLines(Item.TooltipContext.EMPTY, null,
                TooltipFlag.NORMAL));
        // A suit that sets its modifiers on the stack and hides them, as Tech Reborn's nano suit does when worn.
        ItemStack suit = new ItemStack(armor);
        suit.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.builder().add(
                        net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(armorId, 8.0,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.CHEST).build());
        print(out, "suit_set", suit.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL));
        suit.set(net.minecraft.core.component.DataComponents.TOOLTIP_DISPLAY,
                net.minecraft.world.item.component.TooltipDisplay.DEFAULT.withHidden(
                        net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, true));
        print(out, "suit_hidden", suit.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL));
        out.println("check suit_saved " + suit.kiln$saved("minecraft:attribute_modifiers"));

        // A bundle's picture: its contents in slots and its bar, as the game draws them; an empty one's description.
        var image = stack("bundle").getTooltipImage();
        out.println("check bundle_image " + image.map(i -> i.getClass().getSimpleName()).orElse("none"));
        out.println("check empty_bundle_image " + stack("empty_bundle").getTooltipImage()
                .map(i -> i.getClass().getSimpleName()).orElse("none"));
        out.println("check stick_image " + stack("stick").getTooltipImage().isPresent());
        long[] weight = kiln.ItemTooltips.bundleWeight(((net.minecraft.world.inventory.tooltip.BundleTooltip)
                image.orElseThrow()).kiln$stacks());
        out.println("check bundle_weight " + weight[0] + "/" + weight[1]);
        Font tipFont = new Font();
        var part = net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(image.orElseThrow());
        out.println("check bundle_size " + part.getWidth(tipFont) + "x" + part.getHeight(tipFont));
        var emptyPart = net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(
                stack("empty_bundle").getTooltipImage().orElseThrow());
        out.println("check empty_bundle_size " + emptyPart.getWidth(tipFont) + "x" + emptyPart.getHeight(tipFont));
        // A full one: a stack of 64 fills it.
        out.println("check full_bundle_weight " + java.util.Arrays.toString(kiln.ItemTooltips.bundleWeight(
                List.of(ItemStack.kiln$fromSnbt("{id:\"minecraft:dirt\",count:64}")))));
        GuiGraphicsExtractor tip = new GuiGraphicsExtractor(640, 360);
        tip.tooltip(tipFont, List.of(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(
                Component.literal("Bundle").getVisualOrderText()), part), 100, 100,
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE, null, false);
        for (String op : tip.kiln$ops()) {
            out.println("bundle_op " + op);
        }
        GuiGraphicsExtractor emptyTip = new GuiGraphicsExtractor(640, 360);
        emptyTip.tooltip(tipFont, List.of(emptyPart), 100, 100,
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE, null, false);
        for (String op : emptyTip.kiln$ops()) {
            out.println("empty_bundle_op " + op);
        }

        ItemStack advanced = ItemStack.kiln$fromSnbt("{id:\"minecraft:diamond_sword\",count:1,components:"
                + "{\"minecraft:damage\":10}}");
        out.println("tooltip advanced");
        List<Component> lines = advanced.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.ADVANCED);
        for (int i = 0; i < lines.size(); i++) {
            out.println("line " + i + " " + runs(lines.get(i)));
        }

        // What draws them: a stack with components whole, a plain one by its id, styled text in one op.
        GuiGraphicsExtractor g = new GuiGraphicsExtractor(640, 360);
        Font font = new Font();
        g.item(ItemStack.kiln$fromSnbt(STACKS[0][1]), 10, 20);
        g.item(ItemStack.kiln$fromSnbt(STACKS[8][1]), 30, 20);
        g.text(font, Component.literal("plain"), 0, 40, 0xFFFFFFFF);
        g.text(font, Component.literal("red").withStyle(ChatFormatting.RED), 0, 50, 0xFFFFFFFF);
        g.text(font, Component.literal("Bold ").withStyle(ChatFormatting.BOLD)
                .append(Component.literal("and blue").withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC)), 0, 60,
                0xFFFFFFFF);
        for (String op : g.kiln$ops()) {
            out.println("op " + op);
        }
        out.println("check width_bold " + (font.width(Component.literal("ab").withStyle(ChatFormatting.BOLD))
                == font.width("ab") + 2));
        out.println("check width_codes " + (font.width("§cab") == font.width("ab")));
        // The components a recipe viewer tells kinds apart by (D-172): equal for two Sing horns, apart for Ponder.
        Object sing = stack("horn").get(net.minecraft.core.component.DataComponents.INSTRUMENT);
        out.println("check instrument_same " + (sing != null
                && sing.equals(stack("horn2").get(net.minecraft.core.component.DataComponents.INSTRUMENT))));
        out.println("check instrument_apart " + (sing != null
                && !sing.equals(stack("ponder").get(net.minecraft.core.component.DataComponents.INSTRUMENT))));
        out.println("check painting_variant " + (stack("painting")
                .get(net.minecraft.core.component.DataComponents.PAINTING_VARIANT) != null));

        // An enchanting table takes an unenchanted sword, neither an enchanted one, a book with enchantments nor a
        // stick; an anvil puts sharpness on a sword, not on a pickaxe; the table offers it on a sword (primary).
        ItemStack sword = ItemStack.kiln$fromSnbt("{id:\"minecraft:diamond_sword\",count:1}");
        ItemStack enchantedSword = ItemStack.kiln$fromSnbt(STACKS[7][1]);
        ItemStack pickaxe = ItemStack.kiln$fromSnbt("{id:\"minecraft:diamond_pickaxe\",count:1}");
        ItemStack book = ItemStack.kiln$fromSnbt("{id:\"minecraft:book\",count:1}");
        out.println("check enchantable_sword " + sword.isEnchantable());
        out.println("check enchantable_enchanted_sword " + enchantedSword.isEnchantable());
        out.println("check enchantable_book " + book.isEnchantable());
        out.println("check enchantable_stick " + ItemStack.kiln$fromSnbt(STACKS[8][1]).isEnchantable());
        Holder<Enchantment> sharpness = kiln.GameEnchantments.holder("minecraft:sharpness");
        Holder<Enchantment> efficiency = kiln.GameEnchantments.holder("minecraft:efficiency");
        var acceptable = net.fabricmc.fabric.api.item.v1.EnchantingContext.ACCEPTABLE;
        var primary = net.fabricmc.fabric.api.item.v1.EnchantingContext.PRIMARY;
        out.println("check sharpness_sword " + sword.canBeEnchantedWith(sharpness, acceptable));
        out.println("check sharpness_sword_primary " + sword.canBeEnchantedWith(sharpness, primary));
        out.println("check sharpness_pickaxe " + pickaxe.canBeEnchantedWith(sharpness, acceptable));
        out.println("check efficiency_pickaxe " + pickaxe.canBeEnchantedWith(efficiency, primary));
        out.println("check rarity_book " + ItemStack.kiln$fromSnbt(STACKS[0][1]).getRarity());
        out.println("check rarity_enchanted_sword " + enchantedSword.getRarity());
    }

    static void print(PrintStream out, String name, List<Component> lines) {
        out.println("tooltip " + name);
        for (int i = 0; i < lines.size(); i++) {
            out.println("line " + i + " " + runs(lines.get(i)));
        }
    }

    /** A line's runs: each part of one style, its colour and formats then its text. */
    static String runs(Component line) {
        StringBuilder b = new StringBuilder();
        net.minecraft.network.chat.Style[] last = {null};
        line.getVisualOrderText().accept((index, style, codepoint) -> {
            if (style != last[0]) {
                if (last[0] != null) {
                    b.append('|');
                }
                b.append('[').append(style.getColor() == null ? "-"
                        : String.format("#%06x", style.getColor().getValue() & 0xFFFFFF));
                for (ChatFormatting f : style.kiln$formats()) {
                    b.append(' ').append(f.name().toLowerCase());
                }
                b.append(']');
                last[0] = style;
            }
            b.appendCodePoint(codepoint);
            return true;
        });
        return b.toString();
    }
}
