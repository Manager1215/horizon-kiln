package net.minecraft.world.inventory;

/**
 * Kiln's stand-in for {@code DataSlot}: one int a menu sends its screen (an anvil's cost), with the game's flag for a
 * change since it was last sent. Mods' mixins shadow a menu's {@code DataSlot} fields by this type.
 */
public abstract class DataSlot {
    private int prevValue;

    /** One that keeps its own value. */
    public static DataSlot standalone() {
        return new DataSlot() {
            private int value;

            @Override
            public int get() {
                return value;
            }

            @Override
            public void set(int value) {
                this.value = value;
            }
        };
    }

    /** One that is {@code values[index]}. */
    public static DataSlot shared(int[] values, int index) {
        return new DataSlot() {
            @Override
            public int get() {
                return values[index];
            }

            @Override
            public void set(int value) {
                values[index] = value;
            }
        };
    }

    public abstract int get();

    public abstract void set(int value);

    /** Whether the value changed since this was last asked. */
    public boolean checkAndClearUpdateFlag() {
        int now = get();
        boolean changed = now != prevValue;
        prevValue = now;
        return changed;
    }
}
