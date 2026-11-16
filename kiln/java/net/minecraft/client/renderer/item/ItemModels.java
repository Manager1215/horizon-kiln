package net.minecraft.client.renderer.item;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

/**
 * Kiln's stand-in for {@code ItemModels}: the kinds of item model a model file may name, by id. A mod adds its own
 * kinds here in its client entrypoint (Tech Reborn's); Horizon draws the items, so Kiln keeps them and reads none.
 */
public class ItemModels {
    public static final ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>> ID_MAPPER =
            new ExtraCodecs.LateBoundIdMapper<>();

    public ItemModels() {}
}
