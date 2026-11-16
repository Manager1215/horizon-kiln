package net.minecraft.client.renderer.item.properties.select;

import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

/**
 * Kiln's stand-in for {@code SelectItemModelProperties}: the properties a {@code select} item model may switch on,
 * by id. A mod adds its own in its client entrypoint; Horizon draws the items, so Kiln keeps them and reads none.
 */
public class SelectItemModelProperties {
    public static final ExtraCodecs.LateBoundIdMapper<Identifier, SelectItemModelProperty.Type<?, ?>> ID_MAPPER =
            new ExtraCodecs.LateBoundIdMapper<>();

    public SelectItemModelProperties() {}
}
