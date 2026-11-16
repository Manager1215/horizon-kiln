package net.fabricmc.fabric.api.event.player;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Kiln's stand-in for Fabric's {@code UseBlockCallback}: a player right clicks a block, any block, the game's
 * included. The first listener that doesn't pass decides, and neither the item's use on the block nor the block's own
 * runs; Kiln asks Java about every block's use while a mod listens, on the server.
 */
@FunctionalInterface
public interface UseBlockCallback {
    Event<UseBlockCallback> EVENT = Event.kiln$of(listeners -> (player, level, hand, hit) -> {
        for (UseBlockCallback listener : listeners) {
            InteractionResult result = listener.interact(player, level, hand, hit);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return InteractionResult.PASS;
    });

    InteractionResult interact(Player player, Level level, InteractionHand hand, BlockHitResult hit);
}
