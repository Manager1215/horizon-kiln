package net.minecraft.world.entity;

/** Kiln's stand-in for {@code EntitySpawnRequest}: why an entity is made, and whether it is a preview (D-173). */
public record EntitySpawnRequest(EntitySpawnReason reason, boolean preview) {
}
