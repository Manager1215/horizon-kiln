package net.minecraft.world.item;

import net.minecraft.world.level.block.Block;

/** Kiln's stand-in for {@code BlockItem}: the item that places a block. Horizon places it; Kiln knows the pair. */
public class BlockItem extends Item {
    private final Block block;

    public BlockItem(Block block, Item.Properties properties) {
        super(properties);
        this.block = block;
        Block.kiln$item(block, this);
    }

    public Block getBlock() {
        return block;
    }
}
