package net.minecraft.client.gui.screens.inventory.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Kiln's stand-in for {@code ClientTooltipComponent}: one part of a tooltip, a line of text or a picture a mod draws,
 * with its size. A tooltip draws its parts' text first, then their pictures, from the top down.
 */
public interface ClientTooltipComponent {
    static ClientTooltipComponent create(FormattedCharSequence text) {
        return new ClientTextTooltip(text);
    }

    /**
     * The part that draws {@code component}: itself when it draws itself (as the mods' own do), a bundle's contents
     * as the game draws them (D-177); another of the game's kinds Kiln hasn't takes no room.
     */
    static ClientTooltipComponent create(TooltipComponent component) {
        if (component instanceof ClientTooltipComponent self) {
            return self;
        }
        if (component instanceof net.minecraft.world.inventory.tooltip.BundleTooltip bundle) {
            return new ClientBundleTooltip(bundle);
        }
        return new ClientTooltipComponent() {
            @Override
            public int getHeight(Font font) {
                return 0;
            }

            @Override
            public int getWidth(Font font) {
                return 0;
            }
        };
    }

    int getHeight(Font font);

    int getWidth(Font font);

    default boolean showTooltipWithItemInHand() {
        return false;
    }

    /** Its text, its top left at (x, y). */
    default void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {}

    /** Its picture, in the box {@code width} by {@code height} from (x, y). */
    default void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {}
}
