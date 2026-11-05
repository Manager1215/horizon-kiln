package net.minecraft.world.item.crafting;

import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code RecipeHolder}: a recipe and its id. */
public record RecipeHolder<T extends Recipe<?>>(ResourceKey<Recipe<?>> id, T value) {
    @Override
    public String toString() {
        return id.identifier().toString();
    }
}
