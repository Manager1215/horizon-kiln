package net.minecraft.client.gui.screens.inventory;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Kiln's stand-in for {@code AbstractContainerScreen}: a screen with a menu's window in its middle. For the window's
 * game's container screens (the inventory, a chest's, a furnace's, D-156) the game draws the window and runs its slots;
 * Kiln places this screen where the game drew it ({@link #kiln$place}), so the mods that draw beside it or over its
 * slots (JEI's item list) find it where it shows, and the slot under the pointer is {@link #hoveredSlot}. Its own
 * drawing is the mods': nothing of the window, then the carried item's turn ({@code extractCarriedItem}, where mods
 * draw over it).
 */
public abstract class AbstractContainerScreen<T extends AbstractContainerMenu> extends Screen implements MenuAccess<T> {
    public static final Identifier INVENTORY_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
    public static final Identifier SLOT_HIGHLIGHT_BACK_SPRITE = Identifier.withDefaultNamespace("container/slot_highlight_back");
    public static final Identifier SLOT_HIGHLIGHT_FRONT_SPRITE = Identifier.withDefaultNamespace("container/slot_highlight_front");
    // Public, as JEI's access widener makes them (Kiln applies none).
    public int imageWidth = 176;
    public int imageHeight = 166;
    public int titleLabelX;
    public int titleLabelY;
    public int inventoryLabelX;
    public int inventoryLabelY;
    protected final T menu;
    protected final Component playerInventoryTitle;
    public Slot hoveredSlot;
    public int leftPos;
    public int topPos;
    /** Kiln's own: the window as the game placed it ({@link #kiln$place}); until then, vanilla's middle. */
    private boolean placed;

    public AbstractContainerScreen(T menu, Inventory inventory, Component title) {
        this(menu, inventory, title, 176, 166);
    }

    public AbstractContainerScreen(T menu, Inventory inventory, Component title, int imageWidth, int imageHeight) {
        super(title);
        this.menu = menu;
        this.playerInventoryTitle = Component.translatable("container.inventory");
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = imageHeight - 94;
    }

    protected AbstractContainerScreen() {
        this(null, null, Component.empty());
    }

    /** Kiln's own: where the game drew the window, in GUI pixels. */
    public void kiln$place(int x, int y, int width, int height) {
        leftPos = x;
        topPos = y;
        imageWidth = width;
        imageHeight = height;
        placed = true;
    }

    /** Kiln's own: the pointer moved, so the slot under it may be another ({@link #kiln$slotAt}). */
    public void kiln$pointer(double mouseX, double mouseY) {
        hoveredSlot = getHoveredSlot(mouseX, mouseY);
    }

    /**
     * Kiln's own: where the game puts slot {@code index} of the menu in its window ({@code x, y} from the window's top
     * left), or null where Kiln doesn't know the game's layout: the slot's own place, where its menu gave one (a chest's).
     */
    protected int[] kiln$slotAt(int index) {
        Slot slot = menu.slots.get(index);
        return slot.x == 0 && slot.y == 0 ? null : new int[] {slot.x, slot.y};
    }

    @Override
    protected void init() {
        if (!placed) {
            leftPos = (width - imageWidth) / 2;
            topPos = (height - imageHeight) / 2;
        }
    }

    /** The game draws the window; then the carried item's turn, where mods draw over it (JEI's highlights). */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractContents(graphics, mouseX, mouseY, partialTick);
        extractCarriedItem(graphics, mouseX, mouseY);
    }

    /**
     * The window's contents as vanilla's are drawn (D-215): the screen's widgets, then the labels in the window's place,
     * the two calls the mods hook after (Architectury's container background and foreground events). The game draws
     * the slots and the labels themselves.
     */
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos, topPos);
        extractLabels(graphics, mouseX, mouseY);
        graphics.pose().popMatrix();
    }

    /** The window's title and the inventory's label, in the window's place: the game draws them. */
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}

    /** The item on the pointer: the game draws it. */
    protected void extractCarriedItem(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}

    /** The game draws the window's background, under nothing of the mods'. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}

    /** The menu's slot under (mouseX, mouseY), where Kiln knows the game's layout. */
    public Slot getHoveredSlot(double mouseX, double mouseY) {
        if (menu == null) {
            return null;
        }
        for (int i = 0; i < menu.slots.size(); i++) {
            int[] at = kiln$slotAt(i);
            if (at != null && isHovering(at[0], at[1], 16, 16, mouseX, mouseY)) {
                return menu.slots.get(i);
            }
        }
        return null;
    }

    /** Whether (mouseX, mouseY) is in the box {@code width} by {@code height} at (x, y) of the window, one pixel out. */
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        double mx = mouseX - leftPos;
        double my = mouseY - topPos;
        return mx >= x - 1 && mx < x + width + 1 && my >= y - 1 && my < y + height + 1;
    }

    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return mouseX < left || mouseY < top || mouseX >= left + imageWidth || mouseY >= top + imageHeight;
    }

    /** The game takes the clicks on its window; a mod's widgets on this screen get theirs. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(event);
    }

    /** The game's keys (E, Escape) are the game's: the screen's widgets get the rest. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
    }

    @Override
    public T getMenu() {
        return menu;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public boolean isQuickCrafting;
    private int quickCraftingButton;
    private boolean skipNextRelease;
    public int quickCraftingRemainder;
    protected void extractSlot(net.minecraft.client.gui.GuiGraphicsExtractor p0, net.minecraft.world.inventory.Slot p1, int p2, int p3) { kiln.Missing.called("net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.extractSlot"); }
    public static int getContainerClickButton(net.minecraft.client.input.MouseButtonEvent p0) { kiln.Missing.called("net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.getContainerClickButton"); return 0; }
    public boolean isHovering(net.minecraft.world.inventory.Slot p0, double p1, double p2) { kiln.Missing.called("net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.isHovering"); return false; }
    public void slotClicked(net.minecraft.world.inventory.Slot p0, int p1, int p2, net.minecraft.world.inventory.ContainerInput p3) { kiln.Missing.called("net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.slotClicked"); }
    // End of what tools/kiln-stubs.py generated.
}
