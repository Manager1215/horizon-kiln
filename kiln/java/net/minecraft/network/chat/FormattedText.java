package net.minecraft.network.chat;

import java.util.Optional;

/** Kiln's stand-in for {@code FormattedText}: text in pieces, each with its style. */
public interface FormattedText {
    /** Each piece's text, until {@code consumer} gives back a value. */
    <T> Optional<T> visit(ContentConsumer<T> consumer);

    /** Each piece with its style on top of {@code style}; Kiln's text is one piece in one style. */
    default <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style style) {
        return visit(text -> consumer.accept(style, text));
    }

    /** All its pieces' text. */
    default String getString() {
        StringBuilder b = new StringBuilder();
        visit(text -> {
            b.append(text);
            return Optional.empty();
        });
        return b.toString();
    }

    static FormattedText of(String text) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(ContentConsumer<T> consumer) {
                return consumer.accept(text);
            }
        };
    }

    interface ContentConsumer<T> {
        Optional<T> accept(String text);
    }

    interface StyledContentConsumer<T> {
        Optional<T> accept(Style style, String text);
    }
}
