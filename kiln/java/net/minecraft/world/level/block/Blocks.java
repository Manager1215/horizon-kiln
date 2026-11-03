package net.minecraft.world.level.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Kiln's stand-in for {@code Blocks}: air and a few of the game's blocks mods name, registered in {@code
 * BuiltInRegistries.BLOCK} as vanilla's are. Horizon has them all; other game blocks Kiln makes by id when it meets
 * them.
 */
public final class Blocks {
    public static final Block AIR = register("air");
    public static final Block CAVE_AIR = register("cave_air");
    public static final Block VOID_AIR = register("void_air");
    public static final Block STONE = register("stone");
    public static final Block COBBLESTONE = register("cobblestone");
    public static final Block DIRT = register("dirt");
    public static final Block GRASS_BLOCK = register("grass_block");
    public static final Block FARMLAND = register("farmland");
    public static final Block GLOWSTONE = register("glowstone");
    public static final Block SAND = register("sand");
    public static final Block WATER = register("water");

    private Blocks() {}

    private static Block register(String name) {
        Identifier id = Identifier.withDefaultNamespace(name);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                new Block(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id))));
    }
}
