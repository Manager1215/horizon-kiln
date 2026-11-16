package net.minecraft.network.chat;

/**
 * Kiln's stand-in for {@code ComponentContents}: what a component holds before its style and siblings, a literal text
 * ({@code PlainTextContents}) or a translation ({@code TranslatableContents}). A mod tells a death message by its key
 * this way.
 */
public interface ComponentContents {
    default <T> java.util.Optional<T> visit(FormattedText.ContentConsumer<T> consumer) {
        return java.util.Optional.empty();
    }
}
