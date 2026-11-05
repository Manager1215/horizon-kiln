package net.minecraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/** Kiln's stand-in for {@code ItemStackWithSlot}: a stack and its slot, as a container saves it. */
public record ItemStackWithSlot(int slot, ItemStack stack) {
    public static final Codec<ItemStackWithSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BYTE.xmap(b -> b & 0xFF, s -> (byte) s.intValue()).fieldOf("Slot").forGetter(ItemStackWithSlot::slot),
            ItemStack.MAP_CODEC.forGetter(ItemStackWithSlot::stack))
            .apply(i, ItemStackWithSlot::new));

    public boolean isValidInContainer(int size) {
        return slot >= 0 && slot < size;
    }
}
