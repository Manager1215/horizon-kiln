package net.minecraft.nbt;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Kiln's stand-in for {@code NbtUtils}: block states as the game saves them ({@code {Name, Properties}}). */
public final class NbtUtils {
    private NbtUtils() {}

    public static BlockState readBlockState(HolderGetter<Block> blocks, CompoundTag tag) {
        String name = tag.getStringOr("Name", "minecraft:air");
        CompoundTag properties = tag.getCompoundOrEmpty("Properties");
        List<String> pairs = new ArrayList<>();
        for (String key : properties.keySet()) {
            pairs.add(key + "=" + properties.getStringOr(key, ""));
        }
        return BlockState.kiln$parse(pairs.isEmpty() ? name : name + "[" + String.join(",", pairs) + "]");
    }

    public static CompoundTag writeBlockState(BlockState state) {
        String text = state.kiln$text();
        CompoundTag tag = new CompoundTag();
        int open = text.indexOf('[');
        tag.putString("Name", open < 0 ? text : text.substring(0, open));
        if (open >= 0) {
            CompoundTag properties = new CompoundTag();
            for (String pair : text.substring(open + 1, text.length() - 1).split(",")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    properties.putString(kv[0], kv[1]);
                }
            }
            tag.put("Properties", properties);
        }
        return tag;
    }
}
