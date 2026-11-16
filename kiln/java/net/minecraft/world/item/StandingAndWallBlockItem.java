package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Kiln's stand-in for {@code StandingAndWallBlockItem}: the item of a block that stands on the floor or hangs on a
 * wall (a torch, a sign, a banner, a head). Horizon places it; Kiln knows the two blocks.
 */
public class StandingAndWallBlockItem extends BlockItem {
    private volatile Block wallBlock;
    private final Identifier wallId;

    public StandingAndWallBlockItem(Block block, Block wallBlock, Direction attachmentDirection, Item.Properties properties) {
        super(block, properties);
        this.wallBlock = wallBlock;
        this.wallId = null;
    }

    /** Kiln's own: a game item whose blocks are the game's {@code block} and {@code wallBlock}, found when asked. */
    StandingAndWallBlockItem(Identifier block, Identifier wallBlock, Item.Properties properties) {
        super(block, properties);
        this.wallId = wallBlock;
    }

    /** What a generated subclass's own no-argument constructor calls. */
    protected StandingAndWallBlockItem() {
        super((Block) null, (Item.Properties) null);
        this.wallId = null;
    }

    /** Kiln's own: the block it hangs on a wall as. */
    public Block kiln$wallBlock() {
        Block wall = wallBlock;
        if (wall == null && wallId != null) {
            wall = Block.kiln$byId(wallId.toString());
            wallBlock = wall;
        }
        return wall;
    }
}
