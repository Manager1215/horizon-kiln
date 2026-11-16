package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code RecipeMap}: recipes by id and by type, as vanilla's. */
public class RecipeMap {
    public static final RecipeMap EMPTY = new RecipeMap(List.of());

    private final Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey = new LinkedHashMap<>();
    private final Map<RecipeType<?>, List<RecipeHolder<?>>> byType = new LinkedHashMap<>();

    protected RecipeMap() {
        this(List.of());
    }

    private RecipeMap(Iterable<RecipeHolder<?>> recipes) {
        for (RecipeHolder<?> recipe : recipes) {
            byKey.put(recipe.id(), recipe);
            RecipeType<?> type = recipe.value().getType();
            if (type != null) {
                byType.computeIfAbsent(type, t -> new ArrayList<>()).add(recipe);
            }
        }
    }

    public static RecipeMap create(Iterable<RecipeHolder<?>> recipes) {
        return new RecipeMap(recipes);
    }

    public static RecipeMap create(HolderLookup<Recipe<?>> recipes) {
        List<RecipeHolder<?>> list = new ArrayList<>();
        recipes.listElements().forEach(h -> list.add(new RecipeHolder<>(h.key(), h.value())));
        return new RecipeMap(list);
    }

    @SuppressWarnings("unchecked")
    public <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type) {
        return (Collection<RecipeHolder<T>>) (Collection<?>) Collections.unmodifiableList(byType.getOrDefault(type, List.of()));
    }

    public RecipeHolder<?> byKey(ResourceKey<Recipe<?>> key) {
        return byKey.get(key);
    }

    public Collection<RecipeHolder<?>> values() {
        return Collections.unmodifiableCollection(byKey.values());
    }

}
