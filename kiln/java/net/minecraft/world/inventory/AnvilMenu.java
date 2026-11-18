package net.minecraft.world.inventory;

import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Kiln's stand-in for {@code AnvilMenu}: an anvil's two inputs and its result, and the levels it costs, worked out in
 * the game's order ({@link #createResult}): repairing with a material (a game item's {@code repairable} items, from
 * the prepared resources' {@code items.json}), joining two of an item, putting enchantments on (a book's at half
 * their anvil cost), the inputs' prior work, renaming, and "Too Expensive" at 40 levels for a player who isn't in
 * creative mode. Its fields are the game's ({@code cost} a {@link DataSlot}, {@code itemName}, the inputs and result of
 * {@link ItemCombinerMenu}), so the mods' mixins in it apply.
 *
 * <p>Horizon runs the real anvil. Kiln makes this one for a mod that asks (a recipe viewer's costs, D-176) and for the
 * mixins in {@code createResult} (Kiln's Mixin bridge's {@code anvil_result} row, D-202): made with the game's inputs
 * and the result and cost Horizon worked out ({@link #kiln$bridged}), it works its own out, and Horizon takes it only
 * when it isn't what Horizon had ({@link #kiln$changed}): what a mod's mixin changed.
 */
public class AnvilMenu extends ItemCombinerMenu {
    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    public static final int MAX_NAME_LENGTH = 50;
    private final DataSlot cost = DataSlot.standalone();
    public int repairItemCountCost;
    /**
     * The name the player typed; null when no name was typed (Kiln's: the result keeps the left input's name, where
     * the game's would lose it, so a recipe viewer's costs have no rename in them).
     */
    private String itemName;
    private boolean onlyRenaming;
    /** Kiln's own: what Horizon worked out (the bridge's call, D-202), its result's SNBT and its cost; else null. */
    private String preparedResult;
    private int preparedCost;

    /** The anvil's menu with the player's inventory after its three slots (3 to 38), as the game numbers it (D-167). */
    public AnvilMenu(int containerId, Inventory inventory) {
        this(MenuType.ANVIL, containerId, inventory == null ? null : inventory.player, null);
        kiln$inventory(inventory, 8, 84);
    }

    /**
     * Kiln's own: the menu of the game's anvil screen, whose two inputs show what {@code seen} holds (the game's slots,
     * as the tick says them, D-176): a recipe viewer's "+" counts what is in them.
     */
    public AnvilMenu(int containerId, Inventory inventory, Container seen) {
        this(MenuType.ANVIL, containerId, inventory == null ? null : inventory.player, seen);
        kiln$inventory(inventory, 8, 84);
    }

    /** Kiln's own: an anvil menu of {@code menuType} (a screen's stand-in menu, kiln.ContainerScreens). */
    protected AnvilMenu(MenuType<?> menuType, int containerId) {
        this(menuType, containerId, null, null);
    }

    private AnvilMenu(MenuType<?> menuType, int containerId, Player player, Container seen) {
        super(menuType, containerId, new Inputs(seen), player);
        ((Inputs) inputSlots).menu = this;
        addSlot(new Slot(inputSlots, INPUT_SLOT, 27, 47));
        addSlot(new Slot(inputSlots, ADDITIONAL_SLOT, 76, 47));
        addSlot(new Slot(resultSlots, 0, 134, 47));
    }

    /**
     * Kiln's own (Kiln's Mixin bridge, D-202): the anvil {@code player} has open, its inputs {@code left} and
     * {@code right} and the result and cost Horizon worked out for them (SNBT, "" for none). Its name is the one in
     * Horizon's result (the game's rename text isn't in the hook): what the result's custom name says, "" when the
     * left input had one and the result hasn't.
     */
    public static AnvilMenu kiln$bridged(Player player, String left, String right, String result, int cost) {
        AnvilMenu menu = new AnvilMenu(MenuType.ANVIL, 0, player, null);
        Inputs inputs = (Inputs) menu.inputSlots;
        inputs.items.set(INPUT_SLOT, ItemStack.kiln$fromSnbt(left));
        inputs.items.set(ADDITIONAL_SLOT, ItemStack.kiln$fromSnbt(right));
        ItemStack prepared = ItemStack.kiln$fromSnbt(result);
        ItemStack leftStack = inputs.items.get(INPUT_SLOT);
        if (prepared.has(DataComponents.CUSTOM_NAME)) {
            menu.itemName = prepared.getHoverName().getString();
        } else if (leftStack.has(DataComponents.CUSTOM_NAME) && !prepared.isEmpty()) {
            menu.itemName = "";
        }
        menu.preparedResult = prepared.kiln$snbt();
        menu.preparedCost = cost;
        menu.resultSlots.setItem(0, prepared);
        menu.cost.set(cost);
        return menu;
    }

    /** Kiln's own: whether the result or cost isn't what Horizon worked out ("1"), the mods' mixins' doing. */
    public String kiln$changed() {
        return preparedResult != null && (cost.get() != preparedCost || !kiln$result().equals(preparedResult)) ? "1" : "0";
    }

    /**
     * Kiln's own: the result's SNBT ("" for none). A custom name that says what Horizon's does is Horizon's own (its
     * text's form), so a rename alone isn't a change.
     */
    public String kiln$result() {
        ItemStack result = resultSlots.getItem(0);
        if (preparedResult != null && !result.isEmpty() && result.has(DataComponents.CUSTOM_NAME)) {
            ItemStack prepared = ItemStack.kiln$fromSnbt(preparedResult);
            if (prepared.has(DataComponents.CUSTOM_NAME)
                    && prepared.getHoverName().getString().equals(result.getHoverName().getString())) {
                result = result.copy();
                result.kiln$setSaved("minecraft:custom_name", prepared.kiln$saved("minecraft:custom_name"));
            }
        }
        return result.kiln$snbt();
    }

    /** The levels the result costs. */
    public int getCost() {
        return cost.get();
    }

    /** The game's: the name typed for the result; whether it is a new one. */
    public boolean setItemName(String name) {
        String valid = name == null ? null : StringUtil.filterText(name);
        if (valid == null || valid.length() > MAX_NAME_LENGTH || valid.equals(itemName)) {
            return false;
        }
        itemName = valid;
        if (getSlot(RESULT_SLOT).hasItem()) {
            ItemStack result = getSlot(RESULT_SLOT).getItem();
            if (StringUtil.isBlank(valid)) {
                result.remove(DataComponents.CUSTOM_NAME);
            } else {
                result.set(DataComponents.CUSTOM_NAME, Component.literal(valid));
            }
        }
        createResult();
        return true;
    }

    @Override
    public int getResultSlot() {
        return RESULT_SLOT;
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasStack) {
        return (player.getAbilities().instabuild || player.experienceLevel >= cost.get()) && cost.get() > 0;
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

    /** No result, as the game's: none in the slot, and no cost. */
    private void none() {
        resultSlots.setItem(0, ItemStack.EMPTY);
        cost.set(0);
    }

    /**
     * The game's {@code AnvilMenu.createResult}, in its order (a mod's {@code @ModifyConstant} of 40 or its
     * {@code @Inject} at an {@code Enchantment} call finds what it finds in the game's).
     */
    @Override
    public void createResult() {
        ItemStack left = inputSlots.getItem(INPUT_SLOT);
        onlyRenaming = false;
        cost.set(1);
        int added = 0;
        long base = 0L;
        int renamed = 0;
        if (left.isEmpty() || !EnchantmentHelper.canStoreEnchantments(left)) {
            none();
            return;
        }
        ItemStack result = left.copy();
        ItemStack right = inputSlots.getItem(ADDITIONAL_SLOT);
        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(result));
        base += (long) repairCost(left) + repairCost(right);
        repairItemCountCost = 0;
        if (!right.isEmpty()) {
            boolean book = right.has(DataComponents.STORED_ENCHANTMENTS);
            if (result.isDamageableItem() && repairsWith(left, right)) {
                // Each material mends up to a quarter of the item's durability and costs a level more.
                int repair = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                if (repair <= 0) {
                    none();
                    return;
                }
                int used;
                for (used = 0; repair > 0 && used < right.getCount(); used++) {
                    result.setDamageValue(result.getDamageValue() - repair);
                    added++;
                    repair = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                }
                repairItemCountCost = used;
            } else {
                if (!book && (!result.is(right.getItem()) || !result.isDamageableItem())) {
                    none();
                    return;
                }
                if (result.isDamageableItem() && !book) {
                    int leftLeft = left.getMaxDamage() - left.getDamageValue();
                    int rightLeft = right.getMaxDamage() - right.getDamageValue();
                    int damage = Math.max(0, result.getMaxDamage() - (leftLeft + rightLeft + result.getMaxDamage() * 12 / 100));
                    if (damage < result.getDamageValue()) {
                        result.setDamageValue(damage);
                        added += 2;
                    }
                }
                ItemEnchantments rightEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(right);
                boolean any = false;
                boolean refused = false;
                for (var entry : rightEnchantments.entrySet()) {
                    Holder<Enchantment> enchantment = entry.getKey();
                    int current = merged.getLevel(enchantment);
                    int level = entry.getIntValue();
                    level = current == level ? level + 1 : Math.max(level, current);
                    Enchantment e = enchantment.value();
                    boolean fits = e.canEnchant(left);
                    if (player != null && player.getAbilities().instabuild || left.is(Items.ENCHANTED_BOOK)) {
                        fits = true;
                    }
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
                    if (level > e.getMaxLevel()) {
                        level = e.getMaxLevel();
                    }
                    merged.set(enchantment, level);
                    int anvil = e.getAnvilCost();
                    if (book) {
                        anvil = Math.max(1, anvil / 2);
                    }
                    added += anvil * level;
                    if (left.getCount() > 1) {
                        added = 40;
                    }
                }
                if (refused && !any) {
                    none();
                    return;
                }
            }
        }
        if (itemName != null && StringUtil.isBlank(itemName)) {
            if (left.has(DataComponents.CUSTOM_NAME)) {
                renamed = 1;
                added += renamed;
                result.remove(DataComponents.CUSTOM_NAME);
            }
        } else if (itemName != null && !itemName.equals(left.getHoverName().getString())) {
            renamed = 1;
            added += renamed;
            result.set(DataComponents.CUSTOM_NAME, Component.literal(itemName));
        }
        cost.set((int) Mth.clamp(base + added, 0L, 2147483647L));
        if (added <= 0) {
            result = ItemStack.EMPTY;
        }
        if (renamed == added && renamed > 0) {
            if (cost.get() >= 40) {
                cost.set(39);
            }
            onlyRenaming = true;
        }
        // Too Expensive: a recipe viewer's menu has no player, and shows the cost whatever it is.
        if (cost.get() >= 40 && player != null && !player.hasInfiniteMaterials()) {
            result = ItemStack.EMPTY;
        }
        if (!result.isEmpty()) {
            int repair = repairCost(result);
            if (repair < repairCost(right)) {
                repair = repairCost(right);
            }
            if (renamed != added || renamed == 0) {
                repair = calculateIncreasedRepairCost(repair);
            }
            // The game's set of an item's default (a repair cost of 0) or of the value it already has adds nothing to
            // the stack; Kiln's would write it out.
            if (repair == 0) {
                result.remove(DataComponents.REPAIR_COST);
            } else {
                result.set(DataComponents.REPAIR_COST, repair);
            }
            ItemEnchantments enchantments = merged.toImmutable();
            if (!enchantments.equals(EnchantmentHelper.getEnchantmentsForCrafting(result))) {
                EnchantmentHelper.setEnchantments(result, enchantments);
            }
        }
        resultSlots.setItem(0, result);
        broadcastChanges();
    }

    /** The anvil's two inputs; a change to one works out the result again. */
    private static final class Inputs implements Container {
        private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
        /** The game's anvil's inputs as the client sees them, for a screen's menu (D-176); else null. */
        private final Container seen;
        private AnvilMenu menu;

        Inputs(Container seen) {
            this.seen = seen;
        }

        @Override
        public int getContainerSize() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return getItem(0).isEmpty() && getItem(1).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            if (slot < 0 || slot >= 2) {
                return ItemStack.EMPTY;
            }
            return seen != null ? seen.getItem(slot) : items.get(slot);
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
            setChanged();
        }

        @Override
        public void setChanged() {
            if (menu != null) {
                menu.slotsChanged(this);
            }
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
