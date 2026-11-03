package net.minecraft.world.level.saveddata;

/** Kiln's stand-in for {@code SavedData}: a mod's data kept with the world, written when it is marked dirty. */
public abstract class SavedData {
    private boolean dirty;

    public void setDirty() {
        setDirty(true);
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return dirty;
    }
}
