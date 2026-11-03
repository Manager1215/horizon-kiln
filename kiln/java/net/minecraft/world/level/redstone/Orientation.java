package net.minecraft.world.level.redstone;

/**
 * Kiln's stand-in for {@code Orientation}: which way a redstone update travels, handed to {@code neighborChanged}.
 * Horizon doesn't say; Kiln hands {@code null}, as vanilla does for updates with no direction.
 */
public class Orientation {}
