package net.minecraft.network.chat.contents;

import java.util.Optional;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;

/** Kiln's stand-in for {@code PlainTextContents}: a literal text, what {@code Component.literal} holds. */
public interface PlainTextContents extends ComponentContents {
    PlainTextContents EMPTY = new LiteralContents("");

    static PlainTextContents create(String text) {
        return text.isEmpty() ? EMPTY : new LiteralContents(text);
    }

    String text();

    record LiteralContents(String text) implements PlainTextContents {
        @Override
        public <T> Optional<T> visit(FormattedText.ContentConsumer<T> consumer) {
            return consumer.accept(text);
        }

        @Override
        public String toString() {
            return "literal{" + text + "}";
        }
    }
}
