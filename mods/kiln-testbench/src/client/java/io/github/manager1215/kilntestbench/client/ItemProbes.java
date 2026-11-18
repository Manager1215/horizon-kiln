package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.check;
import static io.github.manager1215.kilntestbench.client.Game.expect;
import static io.github.manager1215.kilntestbench.client.Game.level;
import static io.github.manager1215.kilntestbench.client.Game.player;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.WORLD;

import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Probe;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/** What the game works out for an item: its tooltip's lines, an anvil's repair and rename, a name set upright. */
final class ItemProbes {
    private ItemProbes() {}

    private static final String NAME = "Kiln Testbench sword";
    private static final String LINE = "[Kiln Testbench] tooltip line";

    static void add(Bench b) {
        b.add("item.tooltipLines", WORLD, () -> new Probe() {
            public Object run() {
                // The callback adds a line to the tooltip of the stack this probe names, and only that one.
                ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
                    if (stack.has(DataComponents.CUSTOM_NAME) && stack.getHoverName().getString().equals(NAME)) {
                        lines.add(Component.literal(LINE));
                    }
                });
                ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
                named.set(DataComponents.CUSTOM_NAME, Component.literal(NAME));
                List<Component> lines = named.getTooltipLines(Item.TooltipContext.of(level()), player(),
                        TooltipFlag.NORMAL);
                // The name comes first, the callback's line after it, once.
                expect(lines.get(0).getString(), NAME);
                check(lines.stream().filter(l -> l.getString().equals(LINE)).count() == 1, "the callback's line: " + lines);
                check(!lines.get(0).getString().equals(LINE), "the callback's line came first");
                // A stack the callback doesn't name gets none.
                List<Component> plain = new ItemStack(Items.DIAMOND).getTooltipLines(Item.TooltipContext.of(level()),
                        player(), TooltipFlag.NORMAL);
                expect(plain.get(0).getString(), new ItemStack(Items.DIAMOND).getHoverName().getString());
                check(plain.stream().noneMatch(l -> l.getString().equals(LINE)), "a line on a stack it didn't name");
                return lines.size() + " lines";
            }
        });
        b.add("item.anvilRepair", WORLD, () -> new Probe() {
            public Object run() {
                // A sword one use from breaking and two diamonds: each diamond mends a quarter of its durability, and
                // each costs a level.
                AnvilMenu menu = new AnvilMenu(0, player().getInventory());
                ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                int max = sword.getMaxDamage();
                check(max > 100, "durability " + max);
                sword.setDamageValue(max - 1);
                menu.getSlot(AnvilMenu.INPUT_SLOT).set(sword);
                menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DIAMOND, 2));
                menu.createResult();
                ItemStack repaired = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
                expect(repaired.getItem() == Items.DIAMOND_SWORD, true);
                expect(repaired.getDamageValue(), max - 1 - 2 * (max / 4));
                expect(menu.getCost(), 2);
                // The inputs are as they were: the result is a copy.
                expect(menu.getSlot(AnvilMenu.INPUT_SLOT).getItem().getDamageValue(), max - 1);
                // A dirt block mends nothing: no result, and no cost.
                menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DIRT));
                menu.createResult();
                expect(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(), true);
                expect(menu.getCost(), 0);
                return "repaired by " + (max - 1 - repaired.getDamageValue()) + " of " + max;
            }
        });
        b.add("item.anvilRename", WORLD, () -> new Probe() {
            public Object run() {
                // Renaming alone costs a level; with a repair, a level more than the repair.
                AnvilMenu menu = new AnvilMenu(0, player().getInventory());
                menu.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.DIAMOND_SWORD));
                menu.createResult();
                expect(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(), true);
                expect(menu.setItemName(NAME), true);
                expect(menu.setItemName(NAME), false);
                ItemStack renamed = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
                expect(renamed.getHoverName().getString(), NAME);
                expect(menu.getCost(), 1);
                ItemStack worn = new ItemStack(Items.DIAMOND_SWORD);
                worn.setDamageValue(worn.getMaxDamage() / 2);
                menu.getSlot(AnvilMenu.INPUT_SLOT).set(worn);
                menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DIAMOND));
                menu.createResult();
                expect(menu.getCost(), 2);
                expect(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().getHoverName().getString(), NAME);
                return "cost " + menu.getCost();
            }
        });
        b.add("text.formatOff", WORLD, () -> new Probe() {
            public Object run() {
                // A format set off is not a format left unset (an item's name set upright, #217), and unsetting it
                // again gives the empty style back.
                Style upright = Style.EMPTY.withItalic(false);
                check(!upright.isItalic(), "upright is italic");
                check(!upright.isEmpty(), "italic set off is no style at all");
                check(Style.EMPTY.withItalic(true).isItalic(), "italic isn't");
                check(upright.withItalic(null).isEmpty(), "italic unset again isn't empty");
                check(upright.equals(Style.EMPTY.withItalic(false)), "two upright styles differ");
                check(!upright.equals(Style.EMPTY.withItalic(true)), "upright is italic's equal");
                ItemStack named = new ItemStack(Items.DIAMOND);
                named.set(DataComponents.CUSTOM_NAME, Component.literal("upright").withStyle(upright));
                check(!named.getHoverName().getStyle().isItalic(), "the name set upright is italic");
                return expect(named.getHoverName().getString(), "upright");
            }
        });
    }
}
