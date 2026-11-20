package net.minecraft.client.gui.screens.packs;

import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.SelectableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Kiln's stand-in for {@code TransferableSelectionList}: one of the pack screen's two lists, its packs one to a row
 * (their names; Kiln draws no pack icons). Horizon has its own pack screen: Kiln makes this list only for a mod.
 */
public class TransferableSelectionList extends ObjectSelectionList<TransferableSelectionList.Entry> {
    public final PackSelectionScreen screen;
    private final Component title;

    public TransferableSelectionList(Minecraft minecraft, PackSelectionScreen screen, int width, int height,
            Component title) {
        super(minecraft, width, height, 33, 36);
        this.screen = screen;
        this.title = title;
    }

    /** Kiln's own: a list of no screen (a generated subclass's). */
    protected TransferableSelectionList() {
        this(Minecraft.getInstance(), null, 0, 0, Component.empty());
    }

    @Override
    public int getRowWidth() {
        return getWidth() - 4;
    }

    /** The rows again: one for each pack, the one to focus kept focused. */
    public void updateList(Stream<PackSelectionModel.Entry> packs, PackSelectionModel.EntryBase focus) {
        clearEntries();
        packs.forEach(pack -> addEntry(new PackEntry(minecraft(), this, pack)));
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    /** A row of the list. */
    public abstract class Entry extends ObjectSelectionList.Entry<Entry> {
        public Entry() {}

        @Override
        public int getWidth() {
            return TransferableSelectionList.this.getRowWidth();
        }

        public abstract String getPackId();
    }

    /** A pack's row: its title. */
    public class PackEntry extends Entry implements SelectableEntry {
        public static final int ICON_SIZE = 32;
        protected final Minecraft minecraft;
        private final TransferableSelectionList parent;
        private final PackSelectionModel.Entry pack;

        public PackEntry(Minecraft minecraft, TransferableSelectionList parent, PackSelectionModel.Entry pack) {
            this.minecraft = minecraft;
            this.parent = parent;
            this.pack = pack;
        }

        @Override
        public Component getNarration() {
            Component title = pack.getTitle();
            return title == null ? Component.literal(pack.getId()) : title;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
                float partialTick) {
            graphics.text(minecraft.font, getNarration(), getContentX() + ICON_SIZE + 2, getContentY() + 1, -1);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return true;
        }

        public void keyboardSelection() {}

        @Override
        public String getPackId() {
            return pack.getId();
        }

        public boolean shouldTakeFocusAfterInteraction() {
            return true;
        }
    }
}
