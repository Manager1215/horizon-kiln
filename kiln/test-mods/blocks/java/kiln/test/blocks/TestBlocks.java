package kiln.test.blocks;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * A mod for Kiln's tests of a mod's block whose id the game's registry report lists too (kiln/tests/content_blocks.rs):
 * a pad with no properties, so its one state is written as its id alone, as Sponge Succ's lava sponge's is. Its code
 * runs when it is placed ({@link Pad}). Written against Minecraft 26.3 and Fabric's API as any mod is; built against
 * Kiln's stand-ins.
 */
public final class TestBlocks implements ModInitializer {
    public static final String ID = "kiln_test_blocks";
    public static final ResourceKey<Block> PAD_KEY =
            ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(ID, "pad"));
    public static final Block PAD =
            Registry.register(BuiltInRegistries.BLOCK, PAD_KEY, new Pad(BlockBehaviour.Properties.of().setId(PAD_KEY)));

    @Override
    public void onInitialize() {}
}
