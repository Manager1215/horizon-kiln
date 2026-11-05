package net.minecraft.world.attribute;

import net.minecraft.core.BlockPos;

/** Kiln's stand-in for {@code EnvironmentAttributeReader}: an environment's attributes, read at a place. */
public interface EnvironmentAttributeReader {
    <Value> Value getDimensionValue(EnvironmentAttribute<Value> attribute);

    <Value> Value getValue(EnvironmentAttribute<Value> attribute, BlockPos pos);
}
