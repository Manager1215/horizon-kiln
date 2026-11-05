package net.fabricmc.fabric.api.transfer.v1.item;

import com.mojang.serialization.Codec;
import kiln.Variants;
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/** Kiln's stand-in for Fabric's {@code ItemVariant}: an item as a resource to store and move. */
public interface ItemVariant extends TransferVariant<Item> {
    Codec<ItemVariant> CODEC = Codec.STRING.xmap(id -> of(Item.kiln$byId(id)),
            v -> v.getItem().kiln$id().toString());

    static ItemVariant blank() {
        return of(Items.AIR);
    }

    static ItemVariant of(ItemStack stack) {
        return of(stack.getItem());
    }

    static ItemVariant of(ItemLike item) {
        return Variants.item(item.asItem());
    }

    static ItemVariant of(ItemLike item, DataComponentPatch components) {
        return of(item);
    }

    default boolean matches(ItemStack stack) {
        return stack.getItem() == getItem();
    }

    default Item getItem() {
        return getObject();
    }

    @Override
    default Holder<Item> typeHolder() {
        return Variants.holder(getItem());
    }

    default ItemStack toStack() {
        return toStack(1);
    }

    default ItemStack toStack(int count) {
        return isBlank() ? ItemStack.EMPTY : new ItemStack(getItem(), count);
    }

    @Override
    ItemVariant withComponents(DataComponentPatch patch);
}
