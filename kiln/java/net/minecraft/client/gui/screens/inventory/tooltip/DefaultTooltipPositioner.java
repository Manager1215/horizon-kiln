package net.minecraft.client.gui.screens.inventory.tooltip;

import org.joml.Vector2i;
import org.joml.Vector2ic;

/**
 * Kiln's stand-in for {@code DefaultTooltipPositioner}: right of the pointer and a little up; left of it where it
 * would leave the screen on the right, and up where it would leave it at the bottom.
 */
public class DefaultTooltipPositioner implements ClientTooltipPositioner {
    public static final ClientTooltipPositioner INSTANCE = new DefaultTooltipPositioner();

    private DefaultTooltipPositioner() {}

    @Override
    public Vector2ic positionTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth,
            int tooltipHeight) {
        Vector2i at = new Vector2i(mouseX, mouseY).add(12, -12);
        if (at.x + tooltipWidth > screenWidth) {
            at.x = Math.max(at.x - 24 - tooltipWidth, 4);
        }
        int height = tooltipHeight + 3;
        if (at.y + height > screenHeight) {
            at.y = screenHeight - height;
        }
        return at;
    }
}
