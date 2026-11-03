package net.minecraft.world.level.saveddata;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;

/** Kiln's stand-in for {@code SavedDataType}: a kind of saved data, its id, how to make it new and its codec. */
public record SavedDataType<T extends SavedData>(Identifier id, Supplier<T> constructor, Codec<T> codec,
        DataFixTypes dataFixType) {}
