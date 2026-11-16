package net.minecraft.world.inventory;

import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Kiln's stand-in for {@code AnvilMenu}: an anvil's two inputs and its result, and the levels it costs, worked out as
 * the game does for joining two of an item and putting enchantments on (a book's at half their anvil cost, plus the
 * inputs' prior work), and repairing with a material (a game item's {@code repairable} items, from the prepared
 * resources' {@code items.json}). The game runs the real anvil; this one is for a mod that asks (a recipe viewer's
 * costs).
 */
public class AnvilMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private final Inputs inputs = new Inputs();
    private int cost;
    /** Kiln's own: the game's anvil's two inputs as the client sees them, for a screen's menu (D-176); else null. */
    private Container seen;

    /** The anvil's menu with the player's inventory after its three slots (3 to 38), as the game numbers it (D-167). */
    public AnvilMenu(int containerId, Inventory inventory) {
        this(MenuType.ANVIL, containerId);
        kiln$inventory(inventory, 8, 84);
    }

    /**
     * Kiln's own: the menu of the game's anvil screen, whose two inputs show what {@code seen} holds (the game's slots,
     * as the tick says them, D-176): a recipe viewer's "+" counts what is in them.
     */
    public AnvilMenu(int containerId, Inventory inventory, Container seen) {
        this(containerId, inventory);
        this.seen = seen;
    }

    /** Kiln's own: an anvil menu of {@code menuType} (a screen's stand-in menu, kiln.ContainerScreens). */
    protected AnvilMenu(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
        addSlot(new Slot(inputs, INPUT_SLOT, 27, 47));
        addSlot(new Slot(inputs, ADDITIONAL_SLOT, 76, 47));
        addSlot(new Slot(inputs, RESULT_SLOT, 134, 47));
    }

    /** The levels the result costs; 0 when there is none. */
    public int getCost() {
        return cost;
    }

    /** The game's {@code ItemCombinerMenu.getResultSlot}: the result's slot, the third. */
    public int getResultSlot() {
        return RESULT_SLOT;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private static int repairCost(ItemStack stack) {
        Integer cost = stack.get(DataComponents.REPAIR_COST);
        return cost == null ? 0 : cost;
    }

    /** Whether {@code material} repairs {@code item}: a game item's {@code repairable} items say. */
    static boolean repairsWith(ItemStack item, ItemStack material) {
        com.google.gson.JsonElement repairable =
                kiln.GameItems.itemComponent(item.getItem().kiln$id().toString(), "minecraft:repairable");
        if (!(repairable instanceof com.google.gson.JsonObject o) || !o.has("items")) {
            return false;
        }
        String items = o.get("items").getAsString();
        String id = material.getItem().kiln$id().toString();
        return items.startsWith("#")
                ? kiln.GameItems.inTag("item", net.minecraft.resources.Identifier.parse(items.substring(1)), id)
                : net.minecraft.resources.Identifier.parse(items).toString().equals(id);
    }

    /** The game's {@code AnvilMenu.createResult}, without renaming. */
    public void createResult() {
        ItemStack left = inputs.items.get(INPUT_SLOT);
        ItemStack right = inputs.items.get(ADDITIONAL_SLOT);
        cost = 0;
        inputs.items.set(RESULT_SLOT, ItemStack.EMPTY);
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }
        ItemStack result = left.copy();
        int added = 0;
        long base = (long) repairCost(left) + repairCost(right);
        if (result.isDamageableItem() && repairsWith(left, right)) {
            // Each material mends up to a quarter of the item's durability and costs a level more.
            int repair = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
            if (repair <= 0) {
                return;
            }
            int used = 0;
            while (repair > 0 && used < right.getCount()) {
                result.setDamageValue(result.getDamageValue() - repair);
                added++;
                used++;
                repair = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
            }
            cost = (int) Math.min(base + added, Integer.MAX_VALUE);
            result.set(DataComponents.REPAIR_COST, (int) Math.min(Math.max(repairCost(left), repairCost(right)) * 2L + 1,
                    Integer.MAX_VALUE));
            inputs.items.set(RESULT_SLOT, result);
            return;
        }
        ItemEnchantments rightEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(right);
        boolean book = right.is(Items.ENCHANTED_BOOK) && !rightEnchantments.isEmpty();
        if (!book && (!result.is(right.getItem()) || !result.isDamageableItem())) {
            return;
        }
        if (result.isDamageableItem() && !book) {
            int max = result.getMaxDamage();
            int left1 = max - left.getDamageValue();
            int right1 = max - right.getDamageValue();
            int damage = Math.max(0, max - (left1 + right1 + max * 12 / 100));
            if (damage < result.getDamageValue()) {
                result.setDamageValue(damage);
                added += 2;
            }
        }
        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(result));
        boolean any = false;
        boolean refused = false;
        for (var entry : rightEnchantments.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int current = merged.getLevel(enchantment);
            int level = entry.getIntValue();
            level = current == level ? level + 1 : Math.max(level, current);
            boolean fits = enchantment.value().canEnchant(left) || left.is(Items.ENCHANTED_BOOK);
            for (Holder<Enchantment> other : merged.keySet()) {
                if (!other.equals(enchantment) && !Enchantment.areCompatible(enchantment, other)) {
                    fits = false;
                    added++;
                }
            }
            if (!fits) {
                refused = true;
                continue;
            }
            any = true;
            level = Math.min(level, enchantment.value().getMaxLevel());
            merged.set(enchantment, level);
            int anvil = enchantment.value().getAnvilCost();
            if (book) {
                anvil = Math.max(1, anvil / 2);
            }
            added += anvil * level;
            if (left.getCount() > 1) {
                added = 40;
            }
        }
        if (refused && !any || added <= 0) {
            return;
        }
        cost = (int) Math.min(base + added, Integer.MAX_VALUE);
        EnchantmentHelper.setEnchantments(result, merged.toImmutable());
        result.set(DataComponents.REPAIR_COST, (int) Math.min(Math.max(repairCost(left), repairCost(right)) * 2L + 1,
                Integer.MAX_VALUE));
        inputs.items.set(RESULT_SLOT, result);
    }

    /** The anvil's three slots; a change to an input works out the result again. */
    private final class Inputs implements Container {
        private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);

        @Override
        public int getContainerSize() {
            return 3;
        }

        @Override
        public boolean isEmpty() {
            for (int slot = 0; slot < 3; slot++) {
                if (!getItem(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return seen != null && slot < RESULT_SLOT ? seen.getItem(slot) : items.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack taken = items.get(slot).split(count);
            setChanged();
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack taken = items.get(slot);
            items.set(slot, ItemStack.EMPTY);
            return taken;
        }

        /**
         * As the game's input container ({@code SimpleContainer.setItem} tells the menu): a new input works out the
         * result, whatever calls it (a recipe viewer's {@code Slot.set}, whose own {@code setChanged} a mod may cancel).
         */
        @Override
        public void setItem(int slot, ItemStack stack) {
            items.set(slot, stack);
            if (slot != RESULT_SLOT) {
                createResult();
            }
        }

        @Override
        public void setChanged() {
            createResult();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            items.replaceAll(s -> ItemStack.EMPTY);
        }
    }

    /** The repair cost an item has after one more use of an anvil (vanilla's rule: twice it and one, at most int's). */
    public static int calculateIncreasedRepairCost(int cost) {
        return (int) Math.min(cost * 2L + 1L, Integer.MAX_VALUE);
    }
}
