package net.minecraft.client.gui.screens.recipebook;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Kiln's stand-in for {@code RecipeBookTabButton}: a tab of the recipe book, which Kiln never shows (D-156). */
public class RecipeBookTabButton extends AbstractWidget {
    public RecipeBookTabButton(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
}
