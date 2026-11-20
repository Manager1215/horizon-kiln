package net.minecraft.client.gui;

/** Kiln's stand-in for {@code Font}: text widths as Horizon measures them (Kiln sends a table once). */
public class Font {
    public final int lineHeight = 9;
    /** The game's one font's widths: every {@code Font} measures by them (sent before the client is made, D-200). */
    private static int[] widths = new int[0];

    /**
     * Width of {@code text} in GUI pixels. Characters Kiln has no width for count as 6. A {@code §} code takes no room
     * and a bold character one pixel more ({@code §l}, till a colour or {@code §r}), as the game's font (D-164).
     */
    public int width(String text) {
        int w = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                net.minecraft.ChatFormatting format = net.minecraft.ChatFormatting.getByCode(text.charAt(++i));
                if (format == net.minecraft.ChatFormatting.BOLD) {
                    bold = true;
                } else if (format != null && (format.isColor() || format == net.minecraft.ChatFormatting.RESET)) {
                    bold = false;
                }
                continue;
            }
            w += charWidth(c) + (bold ? 1 : 0);
        }
        return w;
    }

    /** The longest start of {@code text} no wider than {@code maxWidth} (vanilla's, for plain text). */
    public String plainSubstrByWidth(String text, int maxWidth) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += charWidth(text.charAt(i));
            if (w > maxWidth) {
                return text.substring(0, i);
            }
        }
        return text;
    }

    /** The longest start ({@code tail}: end) of {@code text} no wider than {@code maxWidth}. */
    public String plainSubstrByWidth(String text, int maxWidth, boolean tail) {
        if (!tail) {
            return plainSubstrByWidth(text, maxWidth);
        }
        int w = 0;
        for (int i = text.length() - 1; i >= 0; i--) {
            w += charWidth(text.charAt(i));
            if (w > maxWidth) {
                return text.substring(i + 1);
            }
        }
        return text;
    }

    /** Width of the text's pieces together, a bold one's characters a pixel wider each. */
    public int width(net.minecraft.network.chat.FormattedText text) {
        int[] w = {0};
        text.visit((style, piece) -> {
            w[0] += style.isBold() ? width(piece) + piece.codePointCount(0, piece.length()) : width(piece);
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return w[0];
    }

    /**
     * Kiln's own: {@code text} in lines no wider than {@code maxWidth}, as vanilla's splitter breaks them: at a new
     * line, else after the last space that fits (the space goes), else before the character that doesn't fit (a
     * line holds one character at least).
     */
    public java.util.List<String> kiln$wrap(String text, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (int[] range : kiln$lines(text, maxWidth)) {
            lines.add(text.substring(range[0], range[1]));
        }
        return lines;
    }

    /** Kiln's own: where {@link #kiln$wrap} breaks {@code text}: each line's start and end in it. */
    public java.util.List<int[]> kiln$lines(String text, int maxWidth) {
        java.util.List<int[]> lines = new java.util.ArrayList<>();
        int start = 0;
        while (start <= text.length()) {
            int width = 0;
            int space = -1;
            int end = start;
            boolean broke = false;
            while (end < text.length()) {
                char c = text.charAt(end);
                if (c == '\n') {
                    lines.add(new int[] {start, end});
                    start = end + 1;
                    broke = true;
                    break;
                }
                int w = charWidth(c);
                if (width + w > maxWidth && end > start) {
                    if (space >= start) {
                        lines.add(new int[] {start, space});
                        start = space + 1;
                    } else {
                        lines.add(new int[] {start, end});
                        start = end;
                    }
                    broke = true;
                    break;
                }
                if (c == ' ') {
                    space = end;
                }
                width += w;
                end++;
            }
            if (!broke) {
                lines.add(new int[] {start, text.length()});
                break;
            }
        }
        return lines;
    }

    private int charWidth(int c) {
        return c < widths.length && widths[c] > 0 ? widths[c] : 6;
    }

    /** Width of the characters of {@code text}, a bold one's a pixel wider, as the game's font. */
    public int width(net.minecraft.util.FormattedCharSequence text) {
        int[] w = {0};
        text.accept((index, style, codepoint) -> {
            w[0] += charWidth(codepoint) + (style.isBold() ? 1 : 0);
            return true;
        });
        return w[0];
    }

    /** {@code text} in lines no wider than {@code maxWidth}, each piece keeping its style. */
    /** The height of a text wrapped at {@code width}: its lines, each {@link #lineHeight} high. */
    public int wordWrapHeight(net.minecraft.network.chat.FormattedText text, int width) {
        return split(text, width).size() * lineHeight;
    }

    public java.util.List<net.minecraft.util.FormattedCharSequence> split(net.minecraft.network.chat.FormattedText text,
            int maxWidth) {
        java.util.List<net.minecraft.util.FormattedCharSequence> out = new java.util.ArrayList<>();
        for (java.util.List<Object[]> line : kiln$styledLines(text, maxWidth, net.minecraft.network.chat.Style.EMPTY)) {
            java.util.List<net.minecraft.util.FormattedCharSequence> parts = new java.util.ArrayList<>();
            for (Object[] run : line) {
                parts.add(net.minecraft.util.FormattedCharSequence.forward((String) run[1],
                        (net.minecraft.network.chat.Style) run[0]));
            }
            out.add(net.minecraft.util.FormattedCharSequence.composite(parts));
        }
        return out;
    }

    /** The longest start of {@code text} no wider than {@code maxWidth}, its pieces keeping their styles. */
    public net.minecraft.network.chat.FormattedText substrByWidth(net.minecraft.network.chat.FormattedText text,
            int maxWidth) {
        java.util.List<net.minecraft.network.chat.FormattedText> parts = new java.util.ArrayList<>();
        int[] left = {maxWidth};
        text.visit((style, piece) -> {
            String fit = plainSubstrByWidth(piece, left[0]);
            if (!fit.isEmpty()) {
                parts.add(net.minecraft.network.chat.FormattedText.of(fit, style));
            }
            left[0] -= width(fit);
            return fit.length() < piece.length() ? java.util.Optional.of(Boolean.TRUE) : java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return net.minecraft.network.chat.FormattedText.composite(parts);
    }

    /** Breaks text into lines by width, as the game's own splitter. */
    public net.minecraft.client.StringSplitter getSplitter() {
        return new net.minecraft.client.StringSplitter(this);
    }

    /**
     * Kiln's own: {@code text} broken as {@link #kiln$wrap} breaks its plain text; each line its runs of one style,
     * {@code {style, text}}.
     */
    public java.util.List<java.util.List<Object[]>> kiln$styledLines(net.minecraft.network.chat.FormattedText text,
            int maxWidth, net.minecraft.network.chat.Style base) {
        StringBuilder plain = new StringBuilder();
        java.util.List<net.minecraft.network.chat.Style> styles = new java.util.ArrayList<>();
        text.visit((style, piece) -> {
            plain.append(piece);
            for (int i = 0; i < piece.length(); i++) {
                styles.add(style);
            }
            return java.util.Optional.empty();
        }, base);
        String all = plain.toString();
        java.util.List<java.util.List<Object[]>> lines = new java.util.ArrayList<>();
        for (int[] range : kiln$lines(all, maxWidth)) {
            java.util.List<Object[]> runs = new java.util.ArrayList<>();
            int from = range[0];
            for (int i = range[0] + 1; i <= range[1]; i++) {
                if (i == range[1] || styles.get(i) != styles.get(from)) {
                    runs.add(new Object[] {styles.get(from), all.substring(from, i)});
                    from = i;
                }
            }
            lines.add(runs);
        }
        return lines;
    }

    /** Kiln's own: widths by character, from the game's font. */
    public static void kiln$table(int[] widths) {
        Font.widths = widths.clone();
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static enum DisplayMode {
        NORMAL, SEE_THROUGH, POLYGON_OFFSET;
    }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public java.lang.Object provider;
    public net.minecraft.client.StringSplitter splitter;
    public java.lang.Object getGlyphSource(net.minecraft.network.chat.FontDescription p0) { kiln.Missing.called("net.minecraft.client.gui.Font.getGlyphSource"); return null; }
    public java.lang.Object getGlyph(int p0, net.minecraft.network.chat.Style p1) { kiln.Missing.called("net.minecraft.client.gui.Font.getGlyph"); return null; }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static interface Provider {
        net.minecraft.client.gui.GlyphSource glyphs(net.minecraft.network.chat.FontDescription p0);
        net.minecraft.client.gui.font.glyphs.EffectGlyph effect();
    }
    public class PreparedTextBuilder implements net.minecraft.client.gui.Font.PreparedText, net.minecraft.util.FormattedCharSink {
        public int backgroundColor;
        public float x;
        public float y;
        public float backgroundLeft;
        public float backgroundTop;
        public float backgroundRight;
        public float backgroundBottom;
        public java.util.List<net.minecraft.client.gui.font.TextRenderable.Styled> glyphs;
        public java.util.List<net.minecraft.client.gui.font.TextRenderable> effects;
        public java.util.List<net.minecraft.client.gui.font.EmptyArea> emptyAreas;
        public PreparedTextBuilder(float p0, float p1, int p2, boolean p3, boolean p4) {  }
        public PreparedTextBuilder(float p0, float p1, int p2, int p3, boolean p4, boolean p5) {  }
        public boolean accept(int p0, net.minecraft.network.chat.Style p1, int p2) { kiln.Missing.called("net.minecraft.client.gui.Font.PreparedTextBuilder.accept"); return false; }
        public boolean accept(int p0, net.minecraft.network.chat.Style p1, net.minecraft.client.gui.font.glyphs.BakedGlyph p2) { kiln.Missing.called("net.minecraft.client.gui.Font.PreparedTextBuilder.accept"); return false; }
        public void visit(net.minecraft.client.gui.Font.GlyphVisitor p0) { kiln.Missing.called("net.minecraft.client.gui.Font.PreparedTextBuilder.visit"); }
        public int getShadowColor(net.minecraft.network.chat.Style p0, int p1) { kiln.Missing.called("net.minecraft.client.gui.Font.PreparedTextBuilder.getShadowColor"); return 0; }
        public net.minecraft.client.gui.navigation.ScreenRectangle bounds() { kiln.Missing.called("net.minecraft.client.gui.Font.PreparedTextBuilder.bounds"); return new net.minecraft.client.gui.navigation.ScreenRectangle((net.minecraft.client.gui.navigation.ScreenPosition) null, (int) 0, (int) 0); }
        protected PreparedTextBuilder() {  }
    }
    public static interface GlyphVisitor {
    }
    public static interface PreparedText {
        void visit(net.minecraft.client.gui.Font.GlyphVisitor p0);
        net.minecraft.client.gui.navigation.ScreenRectangle bounds();
    }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public boolean isBidirectional() { kiln.Missing.called("net.minecraft.client.gui.Font.isBidirectional"); return false; }
    // End of what tools/kiln-stubs.py generated.
}
