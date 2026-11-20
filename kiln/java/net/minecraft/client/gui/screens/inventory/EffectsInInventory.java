package net.minecraft.client.gui.screens.inventory;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Kiln's stand-in for {@code EffectsInInventory}: the player's effects beside an inventory screen. Horizon draws the
 * game's own; this one says whether there is room and draws nothing of its own.
 */
public class EffectsInInventory {
    public static final int SPACING = 7;
    public static final int SPRITE_SQUARE_SIZE = 18;
    private final AbstractContainerScreen<?> screen;

    public EffectsInInventory(AbstractContainerScreen<?> screen) {
        this.screen = screen;
    }

    /** Whether the screen leaves room on its right for the effects (the game's least: 32 pixels). */
    public boolean canSeeEffects() {
        return screen.width - (screen.leftPos + screen.imageWidth) >= 32;
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}
}
