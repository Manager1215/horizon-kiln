package net.minecraft.world.attribute;

import net.minecraft.core.BlockPos;

/**
 * Kiln's stand-in for {@code EnvironmentAttributeSystem}: a level's environment. On the server Kiln asks the game for
 * the attributes it answers (the sun's angle); on a client, and for the others, Kiln knows none: null.
 */
public class EnvironmentAttributeSystem implements EnvironmentAttributeReader {
    /** Kiln's own: how an attribute is read at a place; null where the attribute isn't known. */
    public interface Source {
        Object value(EnvironmentAttribute<?> attribute, BlockPos pos);
    }

    private final Source source;

    protected EnvironmentAttributeSystem() {
        this((attribute, pos) -> null);
    }

    /** Kiln's own: an environment read from {@code source}. */
    public EnvironmentAttributeSystem(Source source) {
        this.source = source;
    }

    @Override
    public <Value> Value getDimensionValue(EnvironmentAttribute<Value> attribute) {
        return getValue(attribute, BlockPos.ZERO);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <Value> Value getValue(EnvironmentAttribute<Value> attribute, BlockPos pos) {
        Object value = source.value(attribute, pos);
        if (value == null) {
            kiln.Missing.called("EnvironmentAttributeSystem.getValue(" + attribute + ")");
        }
        return (Value) value;
    }
}
