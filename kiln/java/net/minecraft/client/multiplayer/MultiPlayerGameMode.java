package net.minecraft.client.multiplayer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * Kiln's stand-in for {@code MultiPlayerGameMode}: the window's player's actions on the world, which Horizon does for
 * the game. Using the item in hand goes to Horizon with the tick's replies (kiln.Back), as the use key would: it casts
 * a fishing rod, eats, throws. A menu's throw goes to the game too; creative slots and other clicks aren't Horizon's to take from a mod yet.
 */
public class MultiPlayerGameMode {
    private final Minecraft minecraft;

    public MultiPlayerGameMode(Minecraft minecraft, ClientPacketListener connection) {
        this.minecraft = minecraft;
    }

    /**
     * The item in {@code hand} used, as the use key uses it: Horizon uses it with its next tick (the main hand's
     * first, then the off hand's, as the key). What it did comes with the tick after, so the answer is the game's
     * usual one for an item that does something (a success), or a pass for an empty hand or a spectator.
     */
    public InteractionResult useItem(Player player, InteractionHand hand) {
        if (player == null || player.isSpectator() || player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        kiln.Back.act("use_item", hand == InteractionHand.OFF_HAND ? "off" : "main");
        return InteractionResult.SUCCESS;
    }

    public InteractionResult useItemOn(net.minecraft.client.player.LocalPlayer player, InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        kiln.Missing.called("net.minecraft.client.multiplayer.MultiPlayerGameMode.useItemOn");
        return InteractionResult.PASS;
    }

    /** A creative inventory's slot set: not one a mod can set in Horizon yet. */
    public void handleCreativeModeItemAdd(net.minecraft.world.item.ItemStack stack, int slot) {
        kiln.Missing.called("net.minecraft.client.multiplayer.MultiPlayerGameMode.handleCreativeModeItemAdd");
    }

    /**
     * A click on a slot of the open menu ({@code containerId}). A throw (what the drop key does over a slot) goes to
     * the game, which predicts it and sends it to the server as its own (D-138); the other clicks aren't one a mod can
     * make in Horizon yet.
     */
    public void handleContainerInput(int containerId, int slot, int button,
            net.minecraft.world.inventory.ContainerInput input, Player player) {
        if (input == net.minecraft.world.inventory.ContainerInput.THROW && slot >= 0) {
            kiln.Native.throwSlot(slot, button);
        } else {
            kiln.Missing.called("net.minecraft.client.multiplayer.MultiPlayerGameMode.handleContainerInput");
        }
    }

    public net.minecraft.world.level.GameType getPlayerMode() {
        Player player = minecraft.player;
        if (player == null) {
            return net.minecraft.world.level.GameType.DEFAULT_MODE;
        }
        return player.isCreative() ? net.minecraft.world.level.GameType.CREATIVE
                : player.isSpectator() ? net.minecraft.world.level.GameType.SPECTATOR
                : net.minecraft.world.level.GameType.SURVIVAL;
    }

    /** Whether a block is being broken: Horizon breaks them, and doesn't say. */
    public boolean isDestroying() {
        return false;
    }
}
