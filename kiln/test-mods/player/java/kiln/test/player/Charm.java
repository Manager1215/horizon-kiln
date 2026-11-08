package kiln.test.player;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** An item with a use of its own: it heals its user by 4 and is used up, one at a time. */
public class Charm extends Item {
    public Charm(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.heal(4.0F);
        player.getItemInHand(hand).shrink(1);
        return InteractionResult.SUCCESS;
    }
}
