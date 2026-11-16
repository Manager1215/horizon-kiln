package net.minecraft.client.gui.screens.inventory.tooltip;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Vector2i;
import org.joml.Vector2ic;

/**
 * Kiln's stand-in for {@code BelowOrAboveWidgetTooltipPositioner}: under a widget, at its left; above it where it
 * would leave the screen at the bottom; moved left to stay on the screen.
 */
public class BelowOrAboveWidgetTooltipPositioner implements ClientTooltipPositioner {
    private final ScreenRectangle widget;

    public BelowOrAboveWidgetTooltipPositioner(ScreenRectangle widget) {
        this.widget = widget;
    }

    @Override
    public Vector2ic positionTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth,
            int tooltipHeight) {
        Vector2i at = new Vector2i();
        at.x = widget.left() + 3;
        at.y = widget.bottom() + 3 + 1;
        if (at.y + tooltipHeight + 3 > screenHeight) {
            at.y = widget.top() - tooltipHeight - 3 - 1;
        }
        if (at.x + tooltipWidth > screenWidth) {
            at.x = Math.max(widget.right() - tooltipWidth - 3, 4);
        }
        return at;
    }
}
