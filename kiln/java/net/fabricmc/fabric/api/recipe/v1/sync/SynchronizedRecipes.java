package net.fabricmc.fabric.api.recipe.v1.sync;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for Fabric's {@code SynchronizedRecipes}: the recipes the client has from the server. Kiln's are
 * read on the client's side ({@code kiln.ClientRecipes}): the game's, from its resources, and the mods', from their
 * jars.
 */
public interface SynchronizedRecipes {
    <I extends RecipeInput, T extends Recipe<I>> Stream<RecipeHolder<T>> getAllMatches(RecipeType<T> type, I input,
            Level level);

    <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> getAllOfType(RecipeType<T> type);

    <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getFirstMatch(RecipeType<T> type, I input,
            Level level);

    RecipeHolder<?> get(ResourceKey<Recipe<?>> key);

    Collection<RecipeHolder<?>> recipes();

    /** Kiln's own: {@code map}'s recipes, as synchronized ones. */
    static SynchronizedRecipes kiln$of(RecipeMap map) {
        return new SynchronizedRecipes() {
            @Override
            public <I extends RecipeInput, T extends Recipe<I>> Stream<RecipeHolder<T>> getAllMatches(RecipeType<T> type,
                    I input, Level level) {
                return map.<I, T>byType(type).stream().filter(r -> r.value().matches(input, level));
            }

            @Override
            public <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> getAllOfType(RecipeType<T> type) {
                return map.<I, T>byType(type);
            }

            @Override
            public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getFirstMatch(RecipeType<T> type,
                    I input, Level level) {
                return getAllMatches(type, input, level).findFirst();
            }

            @Override
            public RecipeHolder<?> get(ResourceKey<Recipe<?>> key) {
                return map.byKey(key);
            }

            @Override
            public Collection<RecipeHolder<?>> recipes() {
                return map.values();
            }
        };
    }
}
