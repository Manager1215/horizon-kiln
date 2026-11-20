package net.minecraft.client.gui;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * Kiln's stand-in for {@code ActiveTextCollector}: where a widget sends its text, one line at a time, to be drawn
 * ({@code GuiGraphicsExtractor.textRenderer()}) or looked through for the style under the pointer
 * ({@link ClickableStyleFinder}). Text too wide for its box is drawn from the box's left edge, cut to the box: Kiln
 * doesn't scroll it.
 */
public interface ActiveTextCollector {
    double PERIOD_PER_SCROLLED_PIXEL = 0.5;
    double MIN_SCROLL_PERIOD = 3.0;

    Parameters defaultParameters();

    void defaultParameters(Parameters parameters);

    default void accept(int x, int y, FormattedCharSequence text) {
        accept(TextAlignment.LEFT, x, y, defaultParameters(), text);
    }

    default void accept(int x, int y, Component text) {
        accept(TextAlignment.LEFT, x, y, defaultParameters(), text.getVisualOrderText());
    }

    default void accept(TextAlignment alignment, int x, int y, Parameters parameters, Component text) {
        accept(alignment, x, y, parameters, text.getVisualOrderText());
    }

    void accept(TextAlignment alignment, int x, int y, Parameters parameters, FormattedCharSequence text);

    default void accept(TextAlignment alignment, int x, int y, Component text) {
        accept(alignment, x, y, defaultParameters(), text.getVisualOrderText());
    }

    default void accept(TextAlignment alignment, int x, int y, FormattedCharSequence text) {
        accept(alignment, x, y, defaultParameters(), text);
    }

    void acceptScrolling(Component text, int centerX, int left, int right, int top, int bottom, Parameters parameters);

    default void acceptScrolling(Component text, int centerX, int left, int right, int top, int bottom) {
        acceptScrolling(text, centerX, left, right, top, bottom, defaultParameters());
    }

    default void acceptScrollingWithDefaultCenter(Component text, int left, int right, int top, int bottom) {
        acceptScrolling(text, (left + right) / 2, left, right, top, bottom);
    }

    /**
     * A line in a box, the middle of it at {@code centerX} and the middle of the box's height; one wider than the
     * box starts at its left edge and is cut to it.
     */
    default void defaultScrollingHelper(Component text, int centerX, int left, int right, int top, int bottom,
            int width, int lineHeight, Parameters parameters) {
        int y = (top + bottom - lineHeight) / 2 + 1;
        int room = right - left;
        if (width > room) {
            accept(TextAlignment.LEFT, left, y, parameters.withScissor(left, right, top, bottom), text);
        } else {
            int middle = Math.min(Math.max(centerX, left + width / 2), right - width / 2);
            accept(TextAlignment.CENTER, middle, y, parameters, text);
        }
    }

    static boolean isPointInRectangle(float x, float y, float left, float top, float right, float bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    /** How a line is drawn: through a pose, at an opacity, cut to a box (none: not cut). */
    record Parameters(Matrix3x2fc pose, float opacity, ScreenRectangle scissor) {
        public Parameters(Matrix3x2fc pose) {
            this(pose, 1.0F, null);
        }

        public Parameters withPose(Matrix3x2fc pose) {
            return new Parameters(pose, opacity, scissor);
        }

        public Parameters withScale(float scale) {
            return withPose(new Matrix3x2f(pose == null ? new Matrix3x2f() : pose).scale(scale));
        }

        public Parameters withOpacity(float opacity) {
            return new Parameters(pose, opacity, scissor);
        }

        public Parameters withScissor(ScreenRectangle scissor) {
            return new Parameters(pose, opacity, scissor);
        }

        public Parameters withScissor(int left, int right, int top, int bottom) {
            ScreenRectangle box = new ScreenRectangle(left, top, right - left, bottom - top);
            if (scissor == null) {
                return withScissor(box);
            }
            int x0 = Math.max(box.left(), scissor.left());
            int y0 = Math.max(box.top(), scissor.top());
            int x1 = Math.max(x0, Math.min(box.right(), scissor.right()));
            int y1 = Math.max(y0, Math.min(box.bottom(), scissor.bottom()));
            return withScissor(new ScreenRectangle(x0, y0, x1 - x0, y1 - y0));
        }
    }

    /** The style of the character at (x, y) among the lines it is given, when it has a click (or an insertion). */
    class ClickableStyleFinder implements ActiveTextCollector {
        private final Font font;
        private final int testX;
        private final int testY;
        private Parameters parameters = new Parameters(new Matrix3x2f());
        private boolean includeInsertions;
        private Style result;

        public ClickableStyleFinder(Font font, int testX, int testY) {
            this.font = font;
            this.testX = testX;
            this.testY = testY;
        }

        /** Kiln's own: a finder for no font (a generated subclass's). */
        protected ClickableStyleFinder() {
            this(new Font(), 0, 0);
        }

        @Override
        public Parameters defaultParameters() {
            return parameters;
        }

        @Override
        public void defaultParameters(Parameters parameters) {
            this.parameters = parameters;
        }

        @Override
        public void accept(TextAlignment alignment, int x, int y, Parameters parameters, FormattedCharSequence text) {
            if (testY < y || testY >= y + 9) {
                return;
            }
            ScreenRectangle cut = parameters.scissor();
            if (cut != null && !cut.containsPoint(testX, testY)) {
                return;
            }
            int[] left = {alignment.calculateLeft(x, font, text)};
            text.accept((index, style, codepoint) -> {
                int width = font.width(new String(Character.toChars(codepoint)));
                if (testX >= left[0] && testX < left[0] + width) {
                    if (style.getClickEvent() != null || includeInsertions && style.getInsertion() != null) {
                        result = style;
                    }
                    return false;
                }
                left[0] += width;
                return true;
            });
        }

        @Override
        public void acceptScrolling(Component text, int centerX, int left, int right, int top, int bottom,
                Parameters parameters) {
            defaultScrollingHelper(text, centerX, left, right, top, bottom, font.width(text), 9, parameters);
        }

        public ClickableStyleFinder includeInsertions(boolean include) {
            this.includeInsertions = include;
            return this;
        }

        public Style result() {
            return result;
        }
    }
}
