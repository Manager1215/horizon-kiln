package net.minecraft.world.level.block;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Kiln's stand-in for {@code Block}. A mod's block is the mod's own subclass: Kiln runs its code where Horizon's
 * server reaches Horizon's block of the same id (the mod's data, carried by Horizon's import).
 */
public class Block extends BlockBehaviour implements ItemLike {
    public static final int UPDATE_NEIGHBORS = 1;
    public static final int UPDATE_CLIENTS = 2;
    public static final int UPDATE_INVISIBLE = 4;
    public static final int UPDATE_IMMEDIATE = 8;
    public static final int UPDATE_KNOWN_SHAPE = 16;
    public static final int UPDATE_SUPPRESS_DROPS = 32;
    public static final int UPDATE_MOVE_BY_PISTON = 64;
    public static final int UPDATE_NONE = 4;
    public static final int UPDATE_ALL = 3;
    public static final int UPDATE_ALL_IMMEDIATE = 11;
    private static final Map<Block, Item> ITEMS = new ConcurrentHashMap<>();
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    private final ResourceKey<Block> id;
    protected final StateDefinition<Block, BlockState> stateDefinition;
    private BlockState defaultBlockState;

    public Block(BlockBehaviour.Properties properties) {
        super(properties);
        if (properties.kiln$id() == null) {
            throw new NullPointerException("Block id not set");
        }
        this.id = properties.kiln$id();
        StateDefinition.Builder<Block, BlockState> builder = new StateDefinition.Builder<>(this);
        createBlockStateDefinition(builder);
        this.stateDefinition = builder.kiln$create(values -> BlockState.kiln$of(this, values));
        registerDefaultState(stateDefinition.any());
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {}

    public StateDefinition<Block, BlockState> getStateDefinition() {
        return stateDefinition;
    }

    protected final void registerDefaultState(BlockState state) {
        this.defaultBlockState = state;
    }

    public final BlockState defaultBlockState() {
        return defaultBlockState;
    }

    /** This block's default state with the values {@code other} has for the properties both have. */
    public final BlockState withPropertiesOf(BlockState other) {
        BlockState state = defaultBlockState;
        for (Property<?> property : other.getBlock().getStateDefinition().kiln$properties()) {
            if (state.hasProperty(property)) {
                state = copy(other, state, property);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    /** The state the block takes where it is placed: its default unless the block says otherwise. */
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    /** It was placed by {@code placer} from {@code stack}. */
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {}

    /** A player is about to break it; the state it was. */
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        return state;
    }

    /** An entity stands on the block (each tick it does). */
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {}

    /**
     * Its item: a mod's block item, else the game's item of the same id when Kiln has met it (vanilla's blocks and
     * their items share ids), else air.
     */
    @Override
    public Item asItem() {
        Item item = ITEMS.get(this);
        if (item != null) {
            return item;
        }
        net.minecraft.resources.Identifier id = kiln$id();
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id) : Items.AIR;
    }

    public static Block byItem(Item item) {
        return item instanceof BlockItem blockItem ? blockItem.getBlock() : Blocks.AIR;
    }

    /** {@code stack} dropped at {@code pos}, as a block drops what it gives. */
    public static void popResource(Level level, BlockPos pos, ItemStack stack) {
        if (!stack.isEmpty()) {
            level.kiln$drop(pos, stack);
        }
    }

    /** Its translation key: {@code block.<namespace>.<path>}. */
    public String getDescriptionId() {
        return "block." + kiln$id().getNamespace() + "." + kiln$id().getPath().replace('/', '.');
    }

    /** Its name, in the game's language. */
    public net.minecraft.network.chat.MutableComponent getName() {
        return net.minecraft.network.chat.Component.translatable(getDescriptionId());
    }

    /** Kiln's own: the id it was made with. */
    public Identifier kiln$id() {
        return id.identifier();
    }

    /** Kiln's own: its registry entry (a direct one for a block no mod registered). */
    public Holder<Block> kiln$holder() {
        return BuiltInRegistries.BLOCK.get(id.identifier())
                .filter(h -> h.value() == this)
                .<Holder<Block>>map(h -> h)
                .orElseGet(() -> Holder.direct(this));
    }

    /** Kiln's own: whether it is one of the game's airs. */
    public boolean kiln$isAir() {
        return AIR.contains(id.identifier().toString());
    }

    /** Kiln's own: the item a BlockItem made for this block. */
    public static void kiln$item(Block block, Item item) {
        ITEMS.putIfAbsent(block, item);
    }

    /**
     * Kiln's own: the block called {@code id}: a mod's when one registered it, else the game's, registered as Kiln
     * meets it (vanilla's registry has them all).
     */
    public static synchronized Block kiln$byId(String id) {
        Identifier name = Identifier.parse(id);
        if (BuiltInRegistries.BLOCK.containsKey(name)) {
            return BuiltInRegistries.BLOCK.getValue(name);
        }
        return net.minecraft.core.Registry.register(BuiltInRegistries.BLOCK, name,
                new Block(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, name))));
    }

    @Override
    public String toString() {
        return "Block{" + id.identifier() + "}";
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static int getId(net.minecraft.world.level.block.state.BlockState p0) { kiln.Missing.called("net.minecraft.world.level.block.Block.getId"); return 0; }
    public static net.minecraft.world.phys.shapes.VoxelShape box(double p0, double p1, double p2, double p3, double p4, double p5) { kiln.Missing.called("net.minecraft.world.level.block.Block.box"); return null; }
    public static java.util.List<net.minecraft.world.item.ItemStack> getDrops(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.server.level.ServerLevel p1, net.minecraft.core.BlockPos p2, net.minecraft.world.level.block.entity.BlockEntity p3) { kiln.Missing.called("net.minecraft.world.level.block.Block.getDrops"); return null; }
    public static void dropResources(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.LevelAccessor p1, net.minecraft.core.BlockPos p2, net.minecraft.world.level.block.entity.BlockEntity p3) { kiln.Missing.called("net.minecraft.world.level.block.Block.dropResources"); }
    public void wasExploded(net.minecraft.server.level.ServerLevel p0, net.minecraft.core.BlockPos p1, net.minecraft.world.level.Explosion p2) { kiln.Missing.called("net.minecraft.world.level.block.Block.wasExploded"); }
    public void playerDestroy(net.minecraft.server.level.ServerLevel p0, net.minecraft.server.level.ServerPlayer p1, net.minecraft.core.BlockPos p2, net.minecraft.world.level.block.state.BlockState p3, net.minecraft.world.level.block.entity.BlockEntity p4, net.minecraft.world.item.ItemStack p5) { kiln.Missing.called("net.minecraft.world.level.block.Block.playerDestroy"); }
    // End of what tools/kiln-stubs.py generated.
}
