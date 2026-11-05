package net.minecraft.commands;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/** Kiln's stand-in for {@code SharedSuggestionProvider}: what a command's source offers to complete with. */
public interface SharedSuggestionProvider {
    /** The names of the players the connection lists. */
    Collection<String> getOnlinePlayerNames();

    /** Teams: Kiln knows of none. */
    default Collection<String> getAllTeams() {
        return List.of();
    }

    /** Of {@code values}, those that start with what is typed so far (any case), as the builder's suggestions. */
    static CompletableFuture<Suggestions> suggest(Iterable<String> values, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(value);
            }
        }
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggest(Stream<String> values, SuggestionsBuilder builder) {
        return suggest(values::iterator, builder);
    }

    static CompletableFuture<Suggestions> suggest(String[] values, SuggestionsBuilder builder) {
        return suggest(List.of(values), builder);
    }

    enum ElementSuggestionType {
        TAGS, ELEMENTS, ALL;
    }
}
