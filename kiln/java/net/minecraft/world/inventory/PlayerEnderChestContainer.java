package net.minecraft.world.inventory;

import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Kiln's stand-in for {@code PlayerEnderChestContainer}: a player's ender chest, 27 slots. On Kiln's server side it is
 * the game's (D-209): a server call's player gets theirs from Horizon the first time the call reaches it, and what the
 * call changed goes back when it ends ({@code kiln.EnderChests}). Saved and read as vanilla saves it, a slot each.
 */
public class PlayerEnderChestContainer extends SimpleContainer {
    public PlayerEnderChestContainer() {
        super(27);
    }

    /** The slots {@code slots} names, the others empty, as the game reads a player's {@code EnderItems}. */
    public void fromSlots(ValueInput.TypedInputList<ItemStackWithSlot> slots) {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            setItem(slot, ItemStack.EMPTY);
        }
        for (ItemStackWithSlot stack : slots) {
            if (stack.isValidInContainer(getContainerSize())) {
                setItem(stack.slot(), stack.stack());
            }
        }
    }

    /** Each slot that holds something, with its number, as the game saves a player's {@code EnderItems}. */
    public void storeAsSlots(ValueOutput.TypedOutputList<ItemStackWithSlot> output) {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            ItemStack stack = getItem(slot);
            if (!stack.isEmpty()) {
                output.add(new ItemStackWithSlot(slot, stack));
            }
        }
    }

    /** An ender chest opened anywhere stays open: Kiln has no ender chest block the player must stay near. */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
