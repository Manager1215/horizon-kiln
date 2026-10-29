package net.minecraft.advancements;

/** Kiln's stand-in for {@code AdvancementProgress}. Kiln knows only the advancements the player has made. */
public class AdvancementProgress implements Comparable<AdvancementProgress> {
    private final boolean done;

    public AdvancementProgress(boolean done) {
        this.done = done;
    }

    public boolean isDone() {
        return done;
    }

    @Override
    public int compareTo(AdvancementProgress o) {
        return Boolean.compare(done, o.done);
    }
}
