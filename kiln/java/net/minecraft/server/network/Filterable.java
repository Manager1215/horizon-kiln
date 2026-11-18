package net.minecraft.server.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Function;

/**
 * Kiln's stand-in for {@code Filterable}: a text as written ({@code raw}) and, where the server's chat filter changed
 * it, as filtered. Saved as the raw value alone when there is no filtered one, else as {@code {raw, filtered}}, as the
 * game saves a book's title and pages.
 */
public record Filterable<T>(T raw, Optional<T> filtered) {
    public static <T> Codec<Filterable<T>> codec(Codec<T> codec) {
        Codec<Filterable<T>> full = RecordCodecBuilder.create(i -> i.group(
                codec.fieldOf("raw").forGetter(Filterable::raw),
                codec.optionalFieldOf("filtered").forGetter(Filterable::filtered))
                .apply(i, Filterable::new));
        Codec<Filterable<T>> plain = codec.xmap(Filterable::passThrough, Filterable::raw);
        return Codec.withAlternative(full, plain);
    }

    /** The text as written, nothing filtered. */
    public static <T> Filterable<T> passThrough(T value) {
        return new Filterable<>(value, Optional.empty());
    }

    /** The filtered text where there is one, when filtering is on; the raw otherwise. */
    public T get(boolean filterEnabled) {
        return filterEnabled ? filtered.orElse(raw) : raw;
    }

    public <U> Filterable<U> map(Function<T, U> f) {
        return new Filterable<>(f.apply(raw), filtered.map(f));
    }
}
