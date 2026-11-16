package net.minecraft.util;

import net.minecraft.network.chat.Style;

/** Kiln's stand-in for {@code FormattedCharSink}: hears text a character at a time; false stops it. */
@FunctionalInterface
public interface FormattedCharSink {
    boolean accept(int index, Style style, int codepoint);
}
