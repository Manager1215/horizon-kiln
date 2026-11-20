package net.minecraft.client.particle;

/** Kiln's stand-in for {@code ParticleRenderType}: a group of particles drawn together, by name. */
public record ParticleRenderType(String name, String shorthand) {
    public static final ParticleRenderType SINGLE_QUADS = new ParticleRenderType("SINGLE_QUADS", "Q");
    public static final ParticleRenderType ITEM_PICKUP = new ParticleRenderType("ITEM_PICKUP", "P");
    public static final ParticleRenderType ELDER_GUARDIANS = new ParticleRenderType("ELDER_GUARDIANS", "E");
    public static final ParticleRenderType NO_RENDER = new ParticleRenderType("NO_RENDER", "N");
}
