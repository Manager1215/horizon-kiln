package kiln;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric's API lookups, one per id as Fabric keeps them: providers by block, block entity type or item, asked first,
 * then the fallbacks in the order they came.
 */
public final class ApiLookups {
    private static final Map<Identifier, Object> LOOKUPS = new ConcurrentHashMap<>();

    private ApiLookups() {}

    @SuppressWarnings("unchecked")
    public static <A, C> BlockApiLookup<A, C> block(Identifier id, Class<A> api, Class<C> context) {
        return (BlockApiLookup<A, C>) LOOKUPS.computeIfAbsent(id, i -> new Blocks<>(i, api, context));
    }

    /** The block lookup a mod made under {@code id}, or null when none did. */
    public static BlockApiLookup<?, ?> existingBlock(Identifier id) {
        return LOOKUPS.get(id) instanceof BlockApiLookup<?, ?> lookup ? lookup : null;
    }

    @SuppressWarnings("unchecked")
    public static <A, C> ItemApiLookup<A, C> item(Identifier id, Class<A> api, Class<C> context) {
        return (ItemApiLookup<A, C>) LOOKUPS.computeIfAbsent(id, i -> new Items<>(i, api, context));
    }

    private record Blocks<A, C>(Identifier id, Class<A> api, Class<C> context,
            Map<Block, BlockApiProvider<A, C>> byBlock,
            Map<BlockEntityType<?>, BlockEntityApiProvider<A, C>> byType,
            List<BlockApiProvider<A, C>> fallbacks) implements BlockApiLookup<A, C> {
        Blocks(Identifier id, Class<A> api, Class<C> context) {
            this(id, api, context, new IdentityHashMap<>(), new IdentityHashMap<>(), new ArrayList<>());
        }

        @Override
        public A find(Level level, BlockPos pos, BlockState state, BlockEntity entity, C ctx) {
            BlockState here = state != null ? state : level.getBlockState(pos);
            BlockEntity at = entity != null ? entity : level.getBlockEntity(pos);
            BlockApiProvider<A, C> provider = byBlock.get(here.getBlock());
            if (provider != null) {
                A found = provider.find(level, pos, here, at, ctx);
                if (found != null) {
                    return found;
                }
            }
            if (at != null && byType.get(at.getType()) instanceof BlockEntityApiProvider<A, C> p) {
                A found = p.find(at, ctx);
                if (found != null) {
                    return found;
                }
            }
            for (BlockApiProvider<A, C> fallback : fallbacks) {
                A found = fallback.find(level, pos, here, at, ctx);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        @Override
        public void registerSelf(BlockEntityType<?>... types) {
            registerForBlockEntities((entity, ctx) -> api.isInstance(entity) ? api.cast(entity) : null, types);
        }

        @Override
        public void registerForBlocks(BlockApiProvider<A, C> provider, Block... blocks) {
            for (Block block : blocks) {
                byBlock.put(block, provider);
            }
        }

        @Override
        public void registerForBlockEntities(BlockEntityApiProvider<A, C> provider, BlockEntityType<?>... types) {
            for (BlockEntityType<?> type : types) {
                byType.put(type, provider);
            }
        }

        @Override
        public void registerFallback(BlockApiProvider<A, C> provider) {
            fallbacks.add(provider);
        }

        @Override
        public Identifier getId() {
            return id;
        }

        @Override
        public Class<A> apiClass() {
            return api;
        }

        @Override
        public Class<C> contextClass() {
            return context;
        }

        @Override
        public BlockApiProvider<A, C> getProvider(Block block) {
            return byBlock.get(block);
        }
    }

    private record Items<A, C>(Identifier id, Class<A> api, Class<C> context, Map<Item, ItemApiProvider<A, C>> byItem,
            List<ItemApiProvider<A, C>> fallbacks) implements ItemApiLookup<A, C> {
        Items(Identifier id, Class<A> api, Class<C> context) {
            this(id, api, context, new IdentityHashMap<>(), new ArrayList<>());
        }

        @Override
        public A find(ItemStack stack, C ctx) {
            ItemApiProvider<A, C> provider = byItem.get(stack.getItem());
            if (provider != null) {
                A found = provider.find(stack, ctx);
                if (found != null) {
                    return found;
                }
            }
            for (ItemApiProvider<A, C> fallback : fallbacks) {
                A found = fallback.find(stack, ctx);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        @Override
        public void registerSelf(ItemLike... items) {
            registerForItems((stack, ctx) -> api.isInstance(stack.getItem()) ? api.cast(stack.getItem()) : null, items);
        }

        @Override
        public void registerForItems(ItemApiProvider<A, C> provider, ItemLike... items) {
            for (ItemLike item : items) {
                byItem.put(item.asItem(), provider);
            }
        }

        @Override
        public void registerFallback(ItemApiProvider<A, C> provider) {
            fallbacks.add(provider);
        }

        @Override
        public Identifier getId() {
            return id;
        }

        @Override
        public Class<A> apiClass() {
            return api;
        }

        @Override
        public Class<C> contextClass() {
            return context;
        }

        @Override
        public ItemApiProvider<A, C> getProvider(Item item) {
            return byItem.get(item);
        }
    }
}
