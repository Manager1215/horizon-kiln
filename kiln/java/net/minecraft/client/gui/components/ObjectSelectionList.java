package net.minecraft.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Kiln's stand-in for {@code ObjectSelectionList}: a list whose rows are picked as a whole, a click or the keys
 * selecting one ({@link AbstractSelectionList}). Narration and focus paths are left out.
 */
public abstract class ObjectSelectionList<E extends ObjectSelectionList.Entry<E>> extends AbstractSelectionList<E> {
    public ObjectSelectionList(Minecraft minecraft, int width, int height, int y, int defaultEntryHeight) {
        super(minecraft, width, height, y, defaultEntryHeight);
    }

    /** {@code ObjectSelectionList.Entry}: a row that takes any press on it, so the list selects it. */
    public abstract static class Entry<E extends Entry<E>> extends AbstractSelectionList.Entry<E> {
        public Entry() {}

        /** What a narrator would read for it; Kiln reads none, but mods' rows have it. */
        public abstract Component getNarration();

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return true;
        }
    }
}
