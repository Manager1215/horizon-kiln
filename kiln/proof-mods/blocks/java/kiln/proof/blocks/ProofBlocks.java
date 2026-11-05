package kiln.proof.blocks;

import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Kiln's proof of a mod's blocks (kiln/proof.py): a switch that a click or a step lights, a sprout that its own random
 * tick ripens, a timer that faces its placer, lights by its scheduled tick, goes out when a neighbour changes and
 * drops a star when broken, a counter whose block entity counts ticks, and a crusher whose block entity has slots. Written against Minecraft 26.3 and Fabric's API as any mod is; built against Kiln's stand-ins.
 * Their data for Horizon is the content mod beside it (horizon/).
 */
public final class ProofBlocks implements ModInitializer {
    public static final String ID = "kiln_proof_blocks";
    public static final Block SWITCH = register("switch", Switch::new, BlockBehaviour.Properties.of().strength(1.5F));
    public static final Block SPROUT = register("sprout", Sprout::new,
            BlockBehaviour.Properties.of().randomTicks().noCollision().instabreak());
    public static final Block TIMER = register("timer", Timer::new, BlockBehaviour.Properties.of().strength(1.5F));
    public static final Block COUNTER = register("counter", Counter::new, BlockBehaviour.Properties.of().strength(1.5F));
    public static final BlockEntityType<CounterEntity> COUNTER_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(ID, "counter"),
            FabricBlockEntityTypeBuilder.create(CounterEntity::new, COUNTER).build());
    public static final Block CRUSHER = register("crusher", Crusher::new, BlockBehaviour.Properties.of().strength(1.5F));
    public static final BlockEntityType<CrusherEntity> CRUSHER_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(ID, "crusher"),
            new BlockEntityType<>(CrusherEntity::new, java.util.Set.of(CRUSHER)));

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
            BlockBehaviour.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(ID, name);
        Block block = factory.apply(properties.setId(ResourceKey.create(Registries.BLOCK, id)));
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block,
                new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));
        return block;
    }

    @Override
    public void onInitialize() {
        // The blocks registered with the class.
    }
}
