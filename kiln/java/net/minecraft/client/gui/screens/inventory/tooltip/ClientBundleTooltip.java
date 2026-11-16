package net.minecraft.client.gui.screens.inventory.tooltip;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code ClientBundleTooltip} (D-177): a bundle's contents under its tooltip as the game draws
 * them (Horizon's {@code draw_bundle_tooltip}): 96 pixels wide, centred; up to twelve stacks in slots of 24, four a
 * row, filled from the bottom right with the last put in, "+n" for the items past eleven; then the fullness bar,
 * "Full" over a full one. An empty bundle shows "Can hold a mixed stack of items" (grey) over an "Empty" bar.
 */
public class ClientBundleTooltip implements ClientTooltipComponent {
    private static final int WIDTH = 96;
    private static final int SLOT = 24;
    private static final int BAR_HEIGHT = 13;
    private static final int FILL_MAX = 94;
    private static final Component EMPTY_DESCRIPTION = Component.translatable("item.minecraft.bundle.empty.description");

    private final List<ItemStack> stacks;
    /** The bundle's weight as a fraction: {@code numerator / denominator}, 1 when full. */
    private final long numerator;
    private final long denominator;

    public ClientBundleTooltip(BundleTooltip tooltip) {
        this.stacks = tooltip.kiln$stacks();
        long[] weight = kiln.ItemTooltips.bundleWeight(stacks);
        this.numerator = weight[0];
        this.denominator = weight[1];
    }

    private int rows() {
        return (Math.min(12, stacks.size()) + 3) / 4;
    }

    /** {@code BundleContents.getNumberOfItemsToShow}: the last row's gaps go to the top left, "+n" takes a slot. */
    private int shownCount() {
        int count = stacks.size();
        int available = count > 12 ? 11 : 12;
        int lastRow = count % 4;
        return Math.min(count, available - (lastRow == 0 ? 0 : 4 - lastRow));
    }

    private int descriptionHeight(Font font) {
        return font.split(EMPTY_DESCRIPTION, WIDTH).size() * font.lineHeight;
    }

    @Override
    public int getHeight(Font font) {
        return (stacks.isEmpty() ? descriptionHeight(font) : rows() * SLOT) + BAR_HEIGHT + 8;
    }

    @Override
    public int getWidth(Font font) {
        return WIDTH;
    }

    @Override
    public boolean showTooltipWithItemInHand() {
        return true;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        int left = x + (width - WIDTH) / 2;
        if (stacks.isEmpty()) {
            int line = y;
            for (net.minecraft.util.FormattedCharSequence text : font.split(EMPTY_DESCRIPTION, WIDTH)) {
                graphics.text(font, text, left, line, 0xFFAAAAAA, false);
                line += font.lineHeight;
            }
            bar(font, graphics, left, y + descriptionHeight(font) + 4);
            return;
        }
        int shown = shownCount();
        int hidden = 0;
        for (int i = shown; i < stacks.size(); i++) {
            hidden += stacks.get(i).getCount();
        }
        int rows = rows();
        int right = left + WIDTH;
        int bottom = y + rows * SLOT;
        int slot = 0;
        for (int row = 1; row <= rows; row++) {
            for (int column = 1; column <= 4; column++) {
                int sx = right - column * SLOT;
                int sy = bottom - row * SLOT;
                if (hidden > 0 && row == 1 && column == 1) {
                    graphics.centeredText(font, "+" + hidden, sx + 12, sy + 10, 0xFFFFFFFF);
                } else if (slot < shown) {
                    // The cell of slot number n holds item size - n: the first put in last, bottom right.
                    ItemStack stack = stacks.get(shown - 1 - slot);
                    graphics.blitSprite(null, sprite("slot_background"), sx, sy, SLOT, SLOT);
                    graphics.item(stack, sx + 4, sy + 4);
                    graphics.itemDecorations(font, stack, sx + 4, sy + 4);
                    slot++;
                }
            }
        }
        bar(font, graphics, left, y + rows * SLOT + 4);
    }

    /** The fullness bar: its fill (94 pixels at most, the full sprite when full), its border, and its label. */
    private void bar(Font font, GuiGraphicsExtractor graphics, int x, int y) {
        boolean full = denominator > 0 && numerator >= denominator;
        int fill = denominator <= 0 ? 0 : (int) Math.max(0, Math.min(FILL_MAX, numerator * FILL_MAX / denominator));
        if (fill > 0) {
            graphics.blitSprite(null, sprite(full ? "bundle_progressbar_full" : "bundle_progressbar_fill"), x + 1, y,
                    fill, BAR_HEIGHT);
        }
        graphics.blitSprite(null, sprite("bundle_progressbar_border"), x, y, WIDTH, BAR_HEIGHT);
        String label = stacks.isEmpty() ? "item.minecraft.bundle.empty" : full ? "item.minecraft.bundle.full" : null;
        if (label != null) {
            graphics.centeredText(font, Component.translatable(label), x + WIDTH / 2, y + 3, 0xFFFFFFFF);
        }
    }

    private static Identifier sprite(String name) {
        return Identifier.withDefaultNamespace("container/bundle/" + name);
    }
}
