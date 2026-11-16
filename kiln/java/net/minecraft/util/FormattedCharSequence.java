package net.minecraft.util;

import java.util.List;
import net.minecraft.network.chat.Style;

/**
 * Kiln's stand-in for {@code FormattedCharSequence}: text laid out to draw, each character with its style, given to a
 * {@link FormattedCharSink} one at a time. Kiln has no right-to-left text: the visual order is the text's order.
 */
@FunctionalInterface
public interface FormattedCharSequence {
    FormattedCharSequence EMPTY = sink -> true;

    /** Each character to {@code sink}, in order; false when the sink stopped it. */
    boolean accept(FormattedCharSink sink);

    static FormattedCharSequence codepoint(int codepoint, Style style) {
        return sink -> sink.accept(0, style, codepoint);
    }

    /** {@code text}'s characters, all in {@code style}. */
    static FormattedCharSequence forward(String text, Style style) {
        return text.isEmpty() ? EMPTY : sink -> {
            for (int i = 0; i < text.length(); ) {
                int c = text.codePointAt(i);
                if (!sink.accept(i, style, c)) {
                    return false;
                }
                i += Character.charCount(c);
            }
            return true;
        };
    }

    static FormattedCharSequence backward(String text, Style style) {
        return forward(new StringBuilder(text).reverse().toString(), style);
    }

    static FormattedCharSequence composite(FormattedCharSequence first, FormattedCharSequence second) {
        return sink -> first.accept(sink) && second.accept(sink);
    }

    static FormattedCharSequence composite(FormattedCharSequence... parts) {
        return composite(List.of(parts));
    }

    static FormattedCharSequence composite(List<FormattedCharSequence> parts) {
        return switch (parts.size()) {
            case 0 -> EMPTY;
            case 1 -> parts.get(0);
            default -> {
                List<FormattedCharSequence> copy = List.copyOf(parts);
                yield sink -> {
                    for (FormattedCharSequence part : copy) {
                        if (!part.accept(sink)) {
                            return false;
                        }
                    }
                    return true;
                };
            }
        };
    }

    static FormattedCharSequence fromList(List<FormattedCharSequence> parts) {
        return composite(parts);
    }

    /** Kiln's own: the plain text of {@code sequence}, its styles dropped. */
    static String kiln$plain(FormattedCharSequence sequence) {
        StringBuilder b = new StringBuilder();
        sequence.accept((index, style, codepoint) -> {
            b.appendCodePoint(codepoint);
            return true;
        });
        return b.toString();
    }
}
