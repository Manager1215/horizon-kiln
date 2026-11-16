package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashSet;
import java.util.SequencedSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code TooltipDisplay} (D-177): whether a stack's tooltip shows at all and which of its
 * components' lines it leaves out, saved as the game saves it ({@code {"hide_tooltip": false, "hidden_components":
 * ["minecraft:attribute_modifiers"]}}). Tech Reborn's suits hide their attributes with one.
 */
public record TooltipDisplay(boolean hideTooltip, SequencedSet<DataComponentType<?>> hiddenComponents) {
    public static final TooltipDisplay DEFAULT = new TooltipDisplay(false, new LinkedHashSet<>());
    /** The hidden types by id; one this game hasn't (a mod's not loaded) is left out. */
    public static final Codec<TooltipDisplay> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("hide_tooltip", false).forGetter(TooltipDisplay::hideTooltip),
            Identifier.CODEC.listOf().optionalFieldOf("hidden_components", java.util.List.of())
                    .forGetter(d -> d.hiddenComponents().stream()
                            .map(t -> BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(t))
                            .filter(java.util.Objects::nonNull).toList()))
            .apply(i, (hide, hidden) -> {
                SequencedSet<DataComponentType<?>> types = new LinkedHashSet<>();
                for (Identifier id : hidden) {
                    DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
                    if (type != null) {
                        types.add(type);
                    }
                }
                return new TooltipDisplay(hide, types);
            }));

    public TooltipDisplay withHidden(DataComponentType<?> type, boolean hidden) {
        if (hiddenComponents.contains(type) == hidden) {
            return this;
        }
        SequencedSet<DataComponentType<?>> next = new LinkedHashSet<>(hiddenComponents);
        if (hidden) {
            next.add(type);
        } else {
            next.remove(type);
        }
        return new TooltipDisplay(hideTooltip, next);
    }

    public boolean shows(DataComponentType<?> type) {
        return !hideTooltip && !hiddenComponents.contains(type);
    }
}
