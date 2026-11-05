package net.fabricmc.fabric.api.lookup.v1.item;

import kiln.ApiLookups;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** Kiln's stand-in for Fabric's {@code ItemApiLookup}: an API (an energy storage) found for an item stack. */
public interface ItemApiLookup<A, C> {
    static <A, C> ItemApiLookup<A, C> get(Identifier id, Class<A> apiClass, Class<C> contextClass) {
        return ApiLookups.item(id, apiClass, contextClass);
    }

    A find(ItemStack stack, C context);

    void registerSelf(ItemLike... items);

    void registerForItems(ItemApiProvider<A, C> provider, ItemLike... items);

    void registerFallback(ItemApiProvider<A, C> provider);

    Identifier getId();

    Class<A> apiClass();

    Class<C> contextClass();

    ItemApiProvider<A, C> getProvider(Item item);

    @FunctionalInterface
    interface ItemApiProvider<A, C> {
        A find(ItemStack stack, C context);
    }
}
