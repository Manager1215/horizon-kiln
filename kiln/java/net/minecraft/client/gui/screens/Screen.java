package net.minecraft.client.gui.screens;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Kiln's stand-in for {@code Screen}: a screen of the window's game (the game draws its own, {@link kiln.Screens}) or a
 * mod's, which Kiln shows (D-094): drawn each client tick through Kiln's ops, its widgets taking the mouse, keys and
 * typing as vanilla's do, closed by Escape ({@link #onClose}: back to the game).
 */
public abstract class Screen extends AbstractContainerEventHandler implements Renderable {
    protected final Component title;
    protected final Minecraft minecraft;
    protected final Font font;
    /** Its size in GUI pixels, from its init. */
    public int width;
    public int height;
    private final List<GuiEventListener> children = new ArrayList<>();
    private final List<Renderable> renderables = new ArrayList<>();
    private boolean initialized;

    protected Screen(Component title) {
        this(Minecraft.getInstance(), Minecraft.getInstance().font, title);
    }

    protected Screen(Minecraft minecraft, Font font, Component title) {
        this.title = title;
        this.minecraft = minecraft;
        this.font = font;
    }

    public Component getTitle() {
        return title;
    }

    public Component getNarrationMessage() {
        return title;
    }

    /** It opens, or the window changed size: its size, its widgets made again by its own {@link #init()}. */
    public final void init(int width, int height) {
        this.width = width;
        this.height = height;
        if (!initialized) {
            init();
            setInitialFocus();
        } else {
            repositionElements();
        }
        initialized = true;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        repositionElements();
    }

    protected void repositionElements() {
        rebuildWidgets();
    }

    protected void rebuildWidgets() {
        clearWidgets();
        setFocused(null);
        init();
        setInitialFocus();
    }

    protected void setInitialFocus() {}

    protected void setInitialFocus(GuiEventListener listener) {
        setFocused(listener);
    }

    public void clearFocus() {
        setFocused(null);
    }

    protected void init() {}

    /** A client tick while it is open. */
    public void tick() {}

    /** It became the screen on top. */
    public void added() {}

    /** It is no longer the screen on top. */
    public void removed() {}

    /** Whether the game pauses under it in single player. */
    public boolean isPauseScreen() {
        return true;
    }

    public boolean isInGameUi() {
        return false;
    }

    public boolean shouldCloseOnEsc() {
        return true;
    }

    /** Escape: back to the game, as vanilla's ({@code Gui.setScreen(null)}). */
    public void onClose() {
        minecraft.gui.setScreen(null);
    }

    /** What the screen draws, then its tooltips (Kiln draws no subtitles). */
    public final void extractRenderStateWithTooltipAndSubtitles(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        extractBackground(graphics, mouseX, mouseY, partialTick);
        extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** Its widgets and what else it added to draw, in order. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        for (Renderable renderable : List.copyOf(renderables)) {
            renderable.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    /**
     * Under a screen: in a world, vanilla's darkening of the world behind it; on the title screen, a dark ground (Kiln
     * has no panorama). Vanilla's blur and menu textures are left out.
     */
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level != null) {
            extractTransparentBackground(graphics);
        } else {
            graphics.fill(0, 0, width, height, 0xFF101010);
        }
    }

    protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {}

    protected void extractMenuBackground(GuiGraphicsExtractor graphics) {
        extractMenuBackground(graphics, 0, 0, width, height);
    }

    protected void extractMenuBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0x60000000);
    }

    public void extractTransparentBackground(GuiGraphicsExtractor graphics) {
        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && shouldCloseOnEsc()) {
            onClose();
            return true;
        }
        if (super.keyPressed(event)) {
            return true;
        }
        if (event.isCycleFocus()) {
            cycleFocus(!event.hasShiftDown());
            return true;
        }
        return false;
    }

    /** Tab: the focus to the next widget that takes it (back with Shift), as vanilla's tab navigation. */
    private void cycleFocus(boolean forward) {
        List<GuiEventListener> takers = new ArrayList<>();
        for (GuiEventListener child : children) {
            if (!(child instanceof net.minecraft.client.gui.components.AbstractWidget w) || w.isActive()) {
                takers.add(child);
            }
        }
        if (takers.isEmpty()) {
            return;
        }
        int at = takers.indexOf(getFocused());
        int next = at < 0 ? (forward ? 0 : takers.size() - 1) : Math.floorMod(at + (forward ? 1 : -1), takers.size());
        setFocused(takers.get(next));
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return children;
    }

    protected <T extends GuiEventListener & Renderable> T addRenderableWidget(T widget) {
        renderables.add(widget);
        return addWidget(widget);
    }

    protected <T extends Renderable> T addRenderableOnly(T renderable) {
        renderables.add(renderable);
        return renderable;
    }

    protected <T extends GuiEventListener> T addWidget(T widget) {
        children.add(widget);
        return widget;
    }

    protected void removeWidget(GuiEventListener widget) {
        if (widget instanceof Renderable renderable) {
            renderables.remove(renderable);
        }
        children.remove(widget);
        if (getFocused() == widget) {
            setFocused(null);
        }
    }

    protected void clearWidgets() {
        renderables.clear();
        children.clear();
    }

    protected void insertText(String text, boolean overwrite) {}

    public boolean isMouseOver(double x, double y) {
        return true;
    }

    public Font getFont() {
        return font;
    }

    public boolean showsActiveEffects() {
        return false;
    }

    public boolean canInterruptWithAnotherScreen() {
        return shouldCloseOnEsc();
    }

    public boolean isAllowedInPortal() {
        return isPauseScreen();
    }

    public void afterMouseMove() {}

    public void afterMouseAction() {}

    public void afterKeyboardAction() {}
}
