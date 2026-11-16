package net.minecraft.world.item;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Kiln's stand-in for {@code BlockItem}: the item that places a block. Horizon places it; Kiln knows the pair. A
 * game item that places the game's block of the same id is one of these too ({@link Item#kiln$byId}), and finds its
 * block the first time it is asked, so the game's items don't load all of its blocks.
 */
public class BlockItem extends Item {
    private volatile Block block;
    private final Identifier blockId;

    public BlockItem(Block block, Item.Properties properties) {
        super(properties);
        this.block = block;
        this.blockId = null;
        if (block != null) {
            Block.kiln$item(block, this);
        }
    }

    /** Kiln's own: a game item that places the game's block {@code block}. */
    BlockItem(Identifier block, Item.Properties properties) {
        super(properties);
        this.blockId = block;
    }

    public Block getBlock() {
        Block known = block;
        if (known == null && blockId != null) {
            known = Block.kiln$byId(blockId.toString());
            Block.kiln$item(known, this);
            block = known;
        }
        return known;
    }
}
