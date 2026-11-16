package net.minecraft.network.chat;

import java.util.Optional;

/** Kiln's stand-in for {@code FormattedText}: text in pieces, each with its style. */
public interface FormattedText {
    /** No text. */
    FormattedText EMPTY = new FormattedText() {
        @Override
        public <T> Optional<T> visit(ContentConsumer<T> consumer) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style style) {
            return Optional.empty();
        }
    };

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

    /** {@code text} in {@code style}. */
    static FormattedText of(String text, Style style) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(ContentConsumer<T> consumer) {
                return consumer.accept(text);
            }

            @Override
            public <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style parent) {
                return consumer.accept(parent.applyTo(style), text);
            }
        };
    }

    static FormattedText composite(FormattedText... parts) {
        return composite(java.util.List.of(parts));
    }

    /** The parts one after another, each with its own styles. */
    static FormattedText composite(java.util.List<? extends FormattedText> parts) {
        java.util.List<FormattedText> copy = java.util.List.copyOf(parts);
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(ContentConsumer<T> consumer) {
                for (FormattedText part : copy) {
                    Optional<T> out = part.visit(consumer);
                    if (out.isPresent()) {
                        return out;
                    }
                }
                return Optional.empty();
            }

            @Override
            public <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style style) {
                for (FormattedText part : copy) {
                    Optional<T> out = part.visit(consumer, style);
                    if (out.isPresent()) {
                        return out;
                    }
                }
                return Optional.empty();
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
