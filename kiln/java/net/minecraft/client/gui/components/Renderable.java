package net.minecraft.client.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Kiln's stand-in for {@code Renderable}: something a screen draws, the pointer at (mouseX, mouseY). */
public interface Renderable {
    void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);
}
