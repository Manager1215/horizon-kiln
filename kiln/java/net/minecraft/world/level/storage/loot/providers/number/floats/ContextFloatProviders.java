package net.minecraft.world.level.storage.loot.providers.number.floats;

import net.minecraft.core.Holder;

/** Kiln's stand-in for {@code ContextFloatProviders}: a uniform range; the game's named ones aren't registered. */
public class ContextFloatProviders {
    private record Range(float kiln$min, float kiln$max) implements ContextFloatProvider {
        /** Kiln rolls nothing: the range's middle. */
        @Override
        public float getFloatUnsafe(net.minecraft.world.level.storage.loot.LootContext context) {
            return (kiln$min + kiln$max) / 2;
        }

        @Override
        public void validate(net.minecraft.world.level.storage.loot.ValidationContext context) {}

        @Override
        public com.mojang.serialization.MapCodec<? extends ContextFloatProvider> codec() {
            return null;
        }
    }

    public ContextFloatProviders() {}

    /** The game's named providers: Kiln registers none, it reads the game's from its resources (kiln.ContextNumbers). */
    public static void bootstrap(net.minecraft.data.worldgen.BootstrapContext<ContextFloatProvider> context) {}

    public static Holder<ContextFloatProvider> between(float min, float max) {
        return Holder.direct(new Range(min, max));
    }
}
