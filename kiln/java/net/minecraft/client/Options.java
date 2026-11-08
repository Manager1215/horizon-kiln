package net.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Kiln's stand-in for {@code Options}: the game's options mods read, as the window's game said with the tick (field
 * of view, render distance, GUI scale), and vanilla's key mappings, on their default keys.
 */
public class Options {
    private final OptionInstance<Integer> fov = new OptionInstance<>(70);
    private final OptionInstance<Integer> renderDistance = new OptionInstance<>(12);
    private final OptionInstance<Integer> guiScale = new OptionInstance<>(0);
    public final KeyMapping keyJump = new KeyMapping("key.jump", InputConstants.Type.KEYBOARD, 44, KeyMapping.Category.MOVEMENT);
    public final KeyMapping keyShift = new KeyMapping("key.sneak", InputConstants.Type.KEYBOARD, 225, KeyMapping.Category.MOVEMENT);
    public final KeyMapping keySprint = new KeyMapping("key.sprint", InputConstants.Type.KEYBOARD, 224, KeyMapping.Category.MOVEMENT);
    public final KeyMapping keyUp = new KeyMapping("key.forward", InputConstants.Type.KEYBOARD, 26, KeyMapping.Category.MOVEMENT);
    public final KeyMapping keyLeft = new KeyMapping("key.left", InputConstants.Type.KEYBOARD, 4, KeyMapping.Category.MOVEMENT);
    public final KeyMapping keyDown = new KeyMapping("key.back", InputConstants.Type.KEYBOARD, 22, KeyMapping.Category.MOVEMENT);
    public final KeyMapping keyRight = new KeyMapping("key.right", InputConstants.Type.KEYBOARD, 7, KeyMapping.Category.MOVEMENT);

    /** The options read, as the game reads them when it makes them: Horizon keeps the game's own (D-093). */
    public Options() {
        load();
    }

    /** The game's options read from its file. Horizon reads its own; the mods' mixins in this run as Kiln starts. */
    public void load() {}

    /** The game's options written to its file. Horizon writes its own; Kiln has nothing to write. */
    public void save() {}

    public OptionInstance<Integer> fov() {
        return fov;
    }

    public OptionInstance<Integer> renderDistance() {
        return renderDistance;
    }

    public OptionInstance<Integer> guiScale() {
        return guiScale;
    }

    /** Kiln's own: the options the game said. */
    public void kiln$set(int fov, int renderDistance, int guiScale) {
        this.fov.kiln$value(fov);
        this.renderDistance.kiln$value(renderDistance);
        this.guiScale.kiln$value(guiScale);
    }
}
