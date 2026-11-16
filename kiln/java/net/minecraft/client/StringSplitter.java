package net.minecraft.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/** Kiln's stand-in for {@code StringSplitter}: text broken into lines by its font's widths, as the game's. */
public class StringSplitter {
    private final Font font;

    public StringSplitter(Font font) {
        this.font = font;
    }

    public float stringWidth(String text) {
        return text == null ? 0 : font.width(text);
    }

    public float stringWidth(FormattedText text) {
        return font.width(text);
    }

    public float stringWidth(net.minecraft.util.FormattedCharSequence text) {
        return font.width(text);
    }

    public String plainHeadByWidth(String text, int maxWidth, Style style) {
        return font.plainSubstrByWidth(text, maxWidth);
    }

    public FormattedText headByWidth(FormattedText text, int maxWidth, Style style) {
        return font.substrByWidth(text, maxWidth);
    }

    /** {@code text} in lines no wider than {@code maxWidth}, on top of {@code style}, each piece keeping its own. */
    public List<FormattedText> splitLines(FormattedText text, int maxWidth, Style style) {
        List<FormattedText> out = new ArrayList<>();
        for (List<Object[]> line : font.kiln$styledLines(text, maxWidth, style)) {
            List<FormattedText> parts = new ArrayList<>();
            for (Object[] run : line) {
                parts.add(FormattedText.of((String) run[1], (Style) run[0]));
            }
            out.add(FormattedText.composite(parts));
        }
        return out;
    }

    public List<FormattedText> splitLines(String text, int maxWidth, Style style) {
        return splitLines(FormattedText.of(text, style), maxWidth, Style.EMPTY);
    }
}
