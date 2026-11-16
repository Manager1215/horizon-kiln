package net.minecraft.client.gui.components;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Kiln's stand-in for {@code EditBox}: a line of text the player types into while it has the focus, as vanilla's: characters at
 * the cursor (replacing the selection), Backspace and Delete (with Control a word), the arrows (with Control a word),
 * Home and End, Shift with any of them selecting, Control+A, C, X and V (the system's clipboard, D-174), a double
 * click selecting a word, the text scrolled to keep the cursor in view; a responder told each change, a filter and a
 * length limit.
 */
public class EditBox extends AbstractWidget {
    private final Font font;
    private String value = "";
    private int maxLength = 32;
    private int cursor;
    private int highlight;
    private int display;
    private boolean bordered = true;
    private boolean editable = true;
    private int textColor = 0xFFE0E0E0;
    private int textColorUneditable = 0xFF707070;
    private Component hint;
    private String suggestion;
    private Consumer<String> responder;
    private Predicate<String> filter = s -> true;
    private int frame;

    public EditBox(Font font, int width, int height, Component message) {
        this(font, 0, 0, width, height, message);
    }

    public EditBox(Font font, int x, int y, int width, int height, Component message) {
        this(font, x, y, width, height, null, message);
    }

    public EditBox(Font font, int x, int y, int width, int height, EditBox previous, Component message) {
        super(x, y, width, height, message);
        this.font = font;
        if (previous != null) {
            setValue(previous.getValue());
        }
    }

    public void setResponder(Consumer<String> responder) {
        this.responder = responder;
    }

    public void setFilter(Predicate<String> filter) {
        this.filter = filter;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength);
            changed();
        }
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setValue(String text) {
        if (!filter.test(text)) {
            return;
        }
        value = text.length() > maxLength ? text.substring(0, maxLength) : text;
        moveCursorToEnd(false);
        setHighlightPos(cursor);
        changed();
    }

    public String getValue() {
        return value;
    }

    /** The selected text: between the cursor and the selection's other end. */
    public String getHighlighted() {
        return value.substring(Math.min(cursor, highlight), Math.max(cursor, highlight));
    }

    /** Text typed or pasted at the cursor, in place of the selection; what the filter or the limit refuses is not. */
    public void insertText(String text) {
        int from = Math.min(cursor, highlight);
        int to = Math.max(cursor, highlight);
        int room = maxLength - value.length() + (to - from);
        if (room <= 0) {
            return;
        }
        StringBuilder kept = new StringBuilder();
        text.codePoints().filter(c -> new CharacterEvent(c).isAllowedChatCharacter()).forEach(kept::appendCodePoint);
        if (kept.length() > room) {
            kept.setLength(room);
            if (kept.length() > 0 && Character.isHighSurrogate(kept.charAt(kept.length() - 1))) {
                kept.setLength(kept.length() - 1);
            }
        }
        String next = new StringBuilder(value).replace(from, to, kept.toString()).toString();
        if (!filter.test(next)) {
            return;
        }
        value = next;
        setCursorPosition(from + kept.length());
        setHighlightPos(cursor);
        changed();
    }

    private void deleteText(int count, boolean wholeWord) {
        if (wholeWord) {
            deleteWords(count);
        } else {
            deleteChars(count);
        }
    }

    /** Words before (count < 0) or after the cursor deleted; the selection instead when there is one. */
    public void deleteWords(int count) {
        if (!value.isEmpty()) {
            if (highlight != cursor) {
                insertText("");
            } else {
                deleteCharsToPos(getWordPosition(count));
            }
        }
    }

    /** Characters before (count < 0) or after the cursor deleted; the selection instead when there is one. */
    public void deleteChars(int count) {
        deleteCharsToPos(offsetByCodepoints(cursor, count));
    }

    public void deleteCharsToPos(int position) {
        if (value.isEmpty()) {
            return;
        }
        if (highlight != cursor) {
            insertText("");
            return;
        }
        int from = Math.clamp(Math.min(position, cursor), 0, value.length());
        int to = Math.clamp(Math.max(position, cursor), 0, value.length());
        if (from == to) {
            return;
        }
        String next = value.substring(0, from) + value.substring(to);
        if (!filter.test(next)) {
            return;
        }
        value = next;
        moveCursorTo(from, false);
        changed();
    }

    private void changed() {
        if (responder != null) {
            responder.accept(value);
        }
    }

    /** Where {@code count} words from the cursor are (backwards when negative), as vanilla's: spaces skipped. */
    public int getWordPosition(int count) {
        return getWordPosition(count, cursor);
    }

    private int getWordPosition(int count, int from) {
        int at = from;
        boolean back = count < 0;
        for (int i = 0; i < Math.abs(count); i++) {
            if (!back) {
                int length = value.length();
                at = value.indexOf(' ', at);
                if (at == -1) {
                    at = length;
                } else {
                    while (at < length && value.charAt(at) == ' ') {
                        at++;
                    }
                }
            } else {
                while (at > 0 && value.charAt(at - 1) == ' ') {
                    at--;
                }
                while (at > 0 && value.charAt(at - 1) != ' ') {
                    at--;
                }
            }
        }
        return at;
    }

    private int offsetByCodepoints(int from, int count) {
        int at = from;
        for (int i = 0; i < Math.abs(count); i++) {
            if (count < 0) {
                if (at <= 0) {
                    break;
                }
                at--;
                if (at > 0 && Character.isLowSurrogate(value.charAt(at)) && Character.isHighSurrogate(value.charAt(at - 1))) {
                    at--;
                }
            } else {
                if (at >= value.length()) {
                    break;
                }
                at++;
                if (at < value.length() && Character.isLowSurrogate(value.charAt(at)) && Character.isHighSurrogate(value.charAt(at - 1))) {
                    at++;
                }
            }
        }
        return at;
    }

    public void moveCursor(int count, boolean select) {
        moveCursorTo(offsetByCodepoints(cursor, count), select);
    }

    public void moveCursorTo(int position, boolean select) {
        setCursorPosition(position);
        if (!select) {
            setHighlightPos(cursor);
        }
    }

    public int getCursorPosition() {
        return cursor;
    }

    public void setCursorPosition(int position) {
        cursor = Math.clamp(position, 0, value.length());
        scrollTo(cursor);
    }

    /** The selection's other end; the text scrolls to keep it in view. */
    public void setHighlightPos(int position) {
        highlight = Math.clamp(position, 0, value.length());
        scrollTo(highlight);
    }

    /** Kiln's own: the selection's other end. */
    public int kiln$highlightPos() {
        return highlight;
    }

    /** The first character shown moved so that {@code position} is in view, as vanilla's {@code scrollTo}. */
    private void scrollTo(int position) {
        if (font == null) {
            return;
        }
        display = Math.min(display, value.length());
        int inner = getInnerWidth();
        String shown = font.plainSubstrByWidth(value.substring(display), inner);
        int end = display + shown.length();
        if (position == display) {
            display -= font.plainSubstrByWidth(new StringBuilder(value.substring(0, display)).reverse().toString(), inner).length();
            display = Math.max(display, 0);
        }
        if (position > end) {
            display += position - end;
        } else if (position <= display) {
            display -= display - position;
        }
        display = Math.clamp(display, 0, value.length());
    }

    public void moveCursorToStart(boolean select) {
        moveCursorTo(0, select);
    }

    public void moveCursorToEnd(boolean select) {
        moveCursorTo(value.length(), select);
    }

    private static net.minecraft.client.KeyboardHandler keyboard() {
        return net.minecraft.client.Minecraft.getInstance().kiln$keyboard();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isActive() || !isFocused()) {
            return false;
        }
        if (event.isSelectAll()) {
            moveCursorToEnd(false);
            setHighlightPos(0);
            return true;
        }
        if (event.isCopy()) {
            keyboard().setClipboard(getHighlighted());
            return true;
        }
        if (event.isPaste()) {
            if (editable) {
                insertText(keyboard().getClipboard());
            }
            return true;
        }
        if (event.isCut()) {
            keyboard().setClipboard(getHighlighted());
            if (editable) {
                insertText("");
            }
            return true;
        }
        boolean word = event.hasControlDownWithQuirk();
        boolean select = event.hasShiftDown();
        switch (event.key()) {
            case 42 -> {
                if (editable) {
                    deleteText(-1, word);
                }
            }
            case 76 -> {
                if (editable) {
                    deleteText(1, word);
                }
            }
            case 80 -> {
                if (word) {
                    moveCursorTo(getWordPosition(-1), select);
                } else {
                    moveCursor(-1, select);
                }
            }
            case 79 -> {
                if (word) {
                    moveCursorTo(getWordPosition(1), select);
                } else {
                    moveCursor(1, select);
                }
            }
            case 74 -> moveCursorToStart(select);
            case 77 -> moveCursorToEnd(select);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!canConsumeInput() || !event.isAllowedChatCharacter()) {
            return false;
        }
        insertText(event.codepointAsString());
        return true;
    }

    /** A click puts the cursor at the character under the pointer (Shift selects to it); a double click selects a word. */
    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (doubleClick) {
            int from = getWordPosition(-1, cursor);
            int to = getWordPosition(1, cursor);
            while (to > from && to > 0 && value.charAt(to - 1) == ' ') {
                to--;
            }
            moveCursorTo(from, false);
            moveCursorTo(to, true);
            return;
        }
        int x = (int) event.x() - getX() - (bordered ? 4 : 0);
        String shown = font.plainSubstrByWidth(value.substring(display), getInnerWidth());
        int at = 0;
        while (at < shown.length() && font.width(shown.substring(0, at + 1)) <= x) {
            at++;
        }
        moveCursorTo(display + at, net.minecraft.client.Minecraft.getInstance().hasShiftDown());
    }

    /** Kiln's own: whether a field with the focus drew since Kiln last cleared it (a mod's field takes the keys, D-156). */
    public static boolean kiln$focusDrawn;

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (isFocused() && isVisible()) {
            kiln$focusDrawn = true;
        }
        if (bordered) {
            graphics.kiln$sprite(isFocused() ? "widget/text_field_highlighted" : "widget/text_field", getX(), getY(),
                    getWidth(), getHeight(), 1, 0xFF000000);
        }
        int left = getX() + (bordered ? 4 : 0);
        int top = bordered ? getY() + (getHeight() - 8) / 2 : getY();
        // A bordered box cuts inside its border; one without a border (JEI's search) cuts at its edge, where its text
        // starts: cut a pixel in, it lost the first column of the first letter (D-172).
        int inset = bordered ? 1 : 0;
        graphics.enableScissor(getX() + inset, getY(), getRight() - inset, getBottom());
        int color = editable ? textColor : textColorUneditable;
        display = Math.min(display, value.length());
        String shown = font.plainSubstrByWidth(value.substring(display), getInnerWidth());
        int caret = cursor - display;
        boolean caretShown = caret >= 0 && caret <= shown.length();
        // The selection: a blue box behind its text, as vanilla's highlight reads.
        int mark = Math.clamp(highlight - display, 0, shown.length());
        if (isFocused() && mark != Math.clamp(caret, 0, shown.length())) {
            int a = left + font.width(shown.substring(0, Math.min(mark, Math.clamp(caret, 0, shown.length()))));
            int b = left + font.width(shown.substring(0, Math.max(mark, Math.clamp(caret, 0, shown.length()))));
            graphics.fill(a, top - 1, b, top + 9, 0xFF0000FF);
        }
        if (value.isEmpty() && hint != null && !isFocused()) {
            graphics.text(font, hint.getString(), left, top, 0xFF707070, true);
        } else {
            graphics.text(font, shown, left, top, color, true);
        }
        if (suggestion != null && cursor == value.length()) {
            graphics.text(font, suggestion, left + font.width(shown), top, 0xFF808080, true);
        }
        frame++;
        if (isFocused() && editable && caretShown && frame / 6 % 2 == 0) {
            int cx = left + font.width(shown.substring(0, caret));
            if (cursor < value.length() || suggestion != null || value.length() >= maxLength) {
                graphics.fill(cx, top - 1, cx + 1, top + 10, 0xFFD0D0D0);
            } else {
                graphics.text(font, "_", cx, top, color, true);
            }
        }
        graphics.disableScissor();
    }

    public void setBordered(boolean bordered) {
        this.bordered = bordered;
    }

    public boolean isBordered() {
        return bordered;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setTextColor(int color) {
        textColor = color;
    }

    public void setTextColorUneditable(int color) {
        textColorUneditable = color;
    }

    public void setHint(Component hint) {
        this.hint = hint;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public int getInnerWidth() {
        return bordered ? width - 8 : width;
    }

    // Public, as JEI's access widener makes it.
    public boolean canLoseFocus = true;
    private final java.util.List<TextFormatter> formatters = new java.util.ArrayList<>();

    public void setCanLoseFocus(boolean canLoseFocus) {
        this.canLoseFocus = canLoseFocus;
    }

    /** A field that can't lose the focus keeps it (vanilla's). */
    @Override
    public void setFocused(boolean focused) {
        if (canLoseFocus || focused) {
            super.setFocused(focused);
        }
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    /** Whether it takes typing now: shown, focused and editable. */
    public boolean canConsumeInput() {
        return isVisible() && isFocused() && editable;
    }

    /** A formatter of its text's styles: kept; Kiln draws the text plain. */
    public void addFormatter(TextFormatter formatter) {
        formatters.add(formatter);
    }

    @FunctionalInterface
    public interface TextFormatter {
        net.minecraft.util.FormattedCharSequence format(String text, int firstCharacterIndex);
    }
}
