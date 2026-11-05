package net.fabricmc.fabric.api.event.player;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for Fabric's {@code UseItemCallback}: a player uses the item in a hand (a right click, not on a
 * block). The first listener that doesn't pass decides, and the item's own use doesn't run; Kiln asks Java about
 * every item's use while a mod listens.
 */
@FunctionalInterface
public interface UseItemCallback {
    Event<UseItemCallback> EVENT = Event.kiln$of(listeners -> (player, level, hand) -> {
        for (UseItemCallback listener : listeners) {
            InteractionResult result = listener.interact(player, level, hand);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return InteractionResult.PASS;
    });

    InteractionResult interact(Player player, Level level, InteractionHand hand);
}
