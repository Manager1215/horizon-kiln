package net.minecraft.client.gui.screens.inventory.tooltip;

import org.joml.Vector2ic;

/** Kiln's stand-in for {@code ClientTooltipPositioner}: where a tooltip of a size goes, by the pointer. */
public interface ClientTooltipPositioner {
    Vector2ic positionTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth,
            int tooltipHeight);
}
