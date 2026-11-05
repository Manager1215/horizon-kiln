package net.minecraft.world.level.block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Kiln's stand-in for {@code WeatheringCopperCollection}: one of each copper block's four weather states, unwaxed and
 * waxed ({@code Blocks.CUT_COPPER}, its ids, its items).
 */
public record WeatheringCopperCollection<T>(ByState<T> weathering, ByState<T> waxed) {
    public static final WeatheringCopperCollection<String> PREFIXES = new WeatheringCopperCollection<>(
            new ByState<>("", "exposed_", "weathered_", "oxidized_"),
            new ByState<>("waxed_", "waxed_exposed_", "waxed_weathered_", "waxed_oxidized_"));

    public static WeatheringCopperCollection<String> create(String name) {
        return same(ByState.create(name));
    }

    public static WeatheringCopperCollection<String> same(ByState<String> names) {
        return new WeatheringCopperCollection<>(names, names);
    }

    public static WeatheringCopperCollection<String> prefixWithState(WeatheringCopperCollection<String> names) {
        return zipMap(PREFIXES, names, String::concat);
    }

    /**
     * The game's ids of the copper blocks called {@code name} ({@code cut_copper}: {@code exposed_cut_copper} and
     * the rest); the copper block's own unaffected one is {@code copper_block}, its others {@code exposed_copper}.
     */
    public static WeatheringCopperCollection<String> kiln$ids(String name) {
        ByState<String> names = name.equals("copper_block") ? new ByState<>(name, "copper", "copper", "copper")
                : ByState.create(name);
        return prefixWithState(same(names));
    }

    public List<T> asList() {
        List<T> all = new ArrayList<>(weathering.asList());
        all.addAll(waxed.asList());
        return all;
    }

    public void forEach(Consumer<T> action) {
        asList().forEach(action);
    }

    public <U> WeatheringCopperCollection<U> map(Function<T, U> f) {
        return new WeatheringCopperCollection<>(weathering.map(f), waxed.map(f));
    }

    public <U> WeatheringCopperCollection<U> apply(Function<ByState<T>, ByState<U>> f) {
        return apply(f, f);
    }

    public <U> WeatheringCopperCollection<U> apply(Function<ByState<T>, ByState<U>> unwaxed,
            Function<ByState<T>, ByState<U>> waxed) {
        return new WeatheringCopperCollection<>(unwaxed.apply(this.weathering), waxed.apply(this.waxed));
    }

    public static <T, U> void zipApply(WeatheringCopperCollection<T> a, WeatheringCopperCollection<U> b,
            BiConsumer<T, U> f) {
        zipApply(a.weathering, b.weathering, f);
        zipApply(a.waxed, b.waxed, f);
    }

    public static <T, U, R> WeatheringCopperCollection<R> zipMap(WeatheringCopperCollection<T> a,
            WeatheringCopperCollection<U> b, BiFunction<T, U, R> f) {
        return new WeatheringCopperCollection<>(zipMap(a.weathering, b.weathering, f), zipMap(a.waxed, b.waxed, f));
    }

    public void zipUnwaxedWaxed(BiConsumer<T, T> f) {
        zipApply(weathering, waxed, f);
    }

    public static <T, U> void zipApply(ByState<T> a, ByState<U> b, BiConsumer<T, U> f) {
        f.accept(a.unaffected, b.unaffected);
        f.accept(a.exposed, b.exposed);
        f.accept(a.weathered, b.weathered);
        f.accept(a.oxidized, b.oxidized);
    }

    public static <T, U, R> ByState<R> zipMap(ByState<T> a, ByState<U> b, BiFunction<T, U, R> f) {
        return new ByState<>(f.apply(a.unaffected, b.unaffected), f.apply(a.exposed, b.exposed),
                f.apply(a.weathered, b.weathered), f.apply(a.oxidized, b.oxidized));
    }

    /** One of each weather state. */
    public record ByState<T>(T unaffected, T exposed, T weathered, T oxidized) {
        public static <T> ByState<T> create(T value) {
            return new ByState<>(value, value, value, value);
        }

        public List<T> asList() {
            return List.of(unaffected, exposed, weathered, oxidized);
        }

        public <U> ByState<U> map(Function<T, U> f) {
            return new ByState<>(f.apply(unaffected), f.apply(exposed), f.apply(weathered), f.apply(oxidized));
        }
    }
}
