package net.minecraft.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Kiln's stand-in for {@code BlockTags}: the game's block tags, by id. Whether a block is in one is the game's to say,
 * from the world's tags ({@link kiln.Tags#blockIn}), during a server call.
 */
public class BlockTags {
    public static final TagKey<Block> CROPS = create("crops");
    public static final TagKey<Block> DIRT = create("dirt");
    public static final TagKey<Block> SAND = create("sand");
    public static final TagKey<Block> LOGS = create("logs");
    public static final TagKey<Block> LEAVES = create("leaves");
    public static final TagKey<Block> WART_BLOCKS = create("wart_blocks");
    public static final TagKey<Block> SHULKER_BOXES = create("shulker_boxes");
    public static final TagKey<Block> MINEABLE_WITH_PICKAXE = create("mineable/pickaxe");
    public static final TagKey<Block> INCORRECT_FOR_DIAMOND_TOOL = create("incorrect_for_diamond_tool");
    public static final TagKey<Block> INCORRECT_FOR_IRON_TOOL = create("incorrect_for_iron_tool");
    public static final TagKey<Block> REPLACEABLE_BY_TREES = create("replaceable_by_trees");
    public static final TagKey<Block> SUPPORTS_VEGETATION = create("supports_vegetation");
    public static final TagKey<Block> SUPPORTS_CROPS = create("supports_crops");
    public static final TagKey<Block> SUPPORTS_SUGAR_CANE = create("supports_sugar_cane");
    public static final TagKey<Block> WOODEN_SHELVES = create("wooden_shelves");

    protected BlockTags() {}

    private static TagKey<Block> create(String name) {
        return TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(name));
    }
}
