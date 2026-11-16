package net.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Kiln's stand-in for {@code Options}: the game's options mods read, as the window's game said with the tick (field
 * of view, render distance, GUI scale), and vanilla's key mappings, on their default keys.
 */
public class Options {
    private final OptionInstance<Integer> fov = new OptionInstance<>("fov", 70, new OptionInstance.IntRange(30, 110));
    private final OptionInstance<Integer> renderDistance =
            new OptionInstance<>("render_distance", 12, new OptionInstance.IntRange(2, 32));
    private final OptionInstance<Integer> guiScale = new OptionInstance<>(0);
    private final OptionInstance<Boolean> showSubtitles =
            new OptionInstance<>("show_subtitles", false, v -> java.util.Optional.ofNullable(v));
    /** The creative inventory's operator items tab: off, as the game's default (Horizon doesn't say). */
    private final OptionInstance<Boolean> operatorItemsTab = new OptionInstance<>(false);
    /** Whether item tooltips show their ids and durability numbers (F3+H); Kiln has the game's default, off. */
    public boolean advancedItemTooltips;
    public final KeyMapping keyJump = new KeyMapping("key.jump", InputConstants.Type.KEYBOARD, 44, KeyMapping.Category.MOVEMENT);
    public final KeyMapping keyShift = new KeyMapping("key.sneak", InputConstants.Type.KEYBOARD, 225, KeyMapping.Category.MOVEMENT);
    public final KeyMapping keySprint = new KeyMapping("key.sprint", InputConstants.Type.KEYBOARD, 224, KeyMapping.Category.MOVEMENT);
    public final KeyMapping keyUp = new KeyMapping("key.forward", InputConstants.Type.KEYBOARD, 26, KeyMapping.Category.MOVEMENT);
    public final KeyMapping keyLeft = new KeyMapping("key.left", InputConstants.Type.KEYBOARD, 4, KeyMapping.Category.MOVEMENT);
    public final KeyMapping keyDown = new KeyMapping("key.back", InputConstants.Type.KEYBOARD, 22, KeyMapping.Category.MOVEMENT);
    public final KeyMapping keyRight = new KeyMapping("key.right", InputConstants.Type.KEYBOARD, 7, KeyMapping.Category.MOVEMENT);
    public final KeyMapping keyAttack = new KeyMapping("key.attack", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_LEFT, KeyMapping.Category.GAMEPLAY);
    public final KeyMapping keyUse = new KeyMapping("key.use", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT, KeyMapping.Category.GAMEPLAY);
    public final KeyMapping keyPickItem = new KeyMapping("key.pickItem", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_MIDDLE, KeyMapping.Category.GAMEPLAY);
    public final KeyMapping keyInventory = new KeyMapping("key.inventory", InputConstants.Type.KEYBOARD, 8, KeyMapping.Category.INVENTORY);
    public final KeyMapping keySwapOffhand = new KeyMapping("key.swapOffhand", InputConstants.Type.KEYBOARD, 9, KeyMapping.Category.INVENTORY);
    public final KeyMapping keyDrop = new KeyMapping("key.drop", InputConstants.Type.KEYBOARD, 20, KeyMapping.Category.INVENTORY);
    public final KeyMapping keyChat = new KeyMapping("key.chat", InputConstants.Type.KEYBOARD, 23, KeyMapping.Category.MULTIPLAYER);
    public final KeyMapping keyPlayerList = new KeyMapping("key.playerlist", InputConstants.Type.KEYBOARD, 43, KeyMapping.Category.MULTIPLAYER);
    public final KeyMapping keyCommand = new KeyMapping("key.command", InputConstants.Type.KEYBOARD, 56, KeyMapping.Category.MULTIPLAYER);
    public final KeyMapping keySocialInteractions = new KeyMapping("key.socialInteractions", InputConstants.Type.KEYBOARD, 19, KeyMapping.Category.MULTIPLAYER);
    public final KeyMapping keyScreenshot = new KeyMapping("key.screenshot", InputConstants.Type.KEYBOARD, 59, KeyMapping.Category.MISC);
    public final KeyMapping keyTogglePerspective = new KeyMapping("key.togglePerspective", InputConstants.Type.KEYBOARD, 62, KeyMapping.Category.MISC);
    public final KeyMapping keyFullscreen = new KeyMapping("key.fullscreen", InputConstants.Type.KEYBOARD, 68, KeyMapping.Category.MISC);
    public final KeyMapping keyAdvancements = new KeyMapping("key.advancements", InputConstants.Type.KEYBOARD, 15, KeyMapping.Category.MISC);
    public final KeyMapping keySaveHotbarActivator = new KeyMapping("key.saveToolbarActivator", InputConstants.Type.KEYBOARD, 6, KeyMapping.Category.CREATIVE);
    public final KeyMapping keyLoadHotbarActivator = new KeyMapping("key.loadToolbarActivator", InputConstants.Type.KEYBOARD, 27, KeyMapping.Category.CREATIVE);
    /** The hotbar's keys, 1 to 9. */
    public final KeyMapping[] keyHotbarSlots = hotbar();
    /**
     * Every key mapping, vanilla's first, then the mods' as they register them (Fabric's KeyMappingHelper adds them,
     * as Kiln's does): mods' mixins and code read it.
     */
    public KeyMapping[] keyMappings = vanilla();

    private static KeyMapping[] hotbar() {
        KeyMapping[] slots = new KeyMapping[9];
        for (int i = 0; i < 9; i++) {
            slots[i] = new KeyMapping("key.hotbar." + (i + 1), InputConstants.Type.KEYBOARD, InputConstants.KEY_1 + i,
                    KeyMapping.Category.INVENTORY);
        }
        return slots;
    }

    private KeyMapping[] vanilla() {
        java.util.List<KeyMapping> all = new java.util.ArrayList<>(java.util.List.of(keyAttack, keyUse, keyUp, keyLeft,
                keyDown, keyRight, keyJump, keyShift, keySprint, keyDrop, keyInventory, keyChat, keyPlayerList,
                keyPickItem, keyCommand, keySocialInteractions, keyScreenshot, keyTogglePerspective, keyFullscreen,
                keySwapOffhand, keySaveHotbarActivator, keyLoadHotbarActivator, keyAdvancements));
        all.addAll(java.util.List.of(keyHotbarSlots));
        return all.toArray(KeyMapping[]::new);
    }

    /** Kiln's own: a mod's mapping, registered (Fabric's KeyMappingHelper.registerKeyMapping): added to them all. */
    public void kiln$add(KeyMapping mapping) {
        KeyMapping[] more = java.util.Arrays.copyOf(keyMappings, keyMappings.length + 1);
        more[keyMappings.length] = mapping;
        keyMappings = more;
    }

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

    public OptionInstance<Boolean> showSubtitles() {
        return showSubtitles;
    }

    public OptionInstance<Boolean> operatorItemsTab() {
        return operatorItemsTab;
    }

    /**
     * Kiln's own: the options the game said. A mod's change of the field of view, the render distance or the
     * subtitles goes back to Horizon (kiln.Back, D-105); the GUI scale stays Horizon's.
     */
    public void kiln$set(int fov, int renderDistance, int guiScale) {
        this.fov.kiln$value(fov);
        this.renderDistance.kiln$value(renderDistance);
        this.guiScale.kiln$value(guiScale);
    }

    /** The camera, as the game said with the tick; a mod's change goes back to Horizon (kiln.Back, D-105). */
    private final kiln.Back.Value<Integer> camera = new kiln.Back.Value<>("camera", 0);

    public CameraType getCameraType() {
        Integer at = camera.get();
        return CameraType.values()[at == null || at < 0 || at > 2 ? 0 : at];
    }

    /** The camera set, as F5 or a mod sets it: Horizon's camera follows with the next tick. */
    public void setCameraType(CameraType type) {
        camera.set((type == null ? CameraType.FIRST_PERSON : type).ordinal());
    }

    /** Kiln's own: the camera as the game said it (0 first person, 1 behind, 2 in front). */
    public void kiln$camera(int type) {
        camera.game(type);
    }

    /** Kiln's own: the camera as a value Horizon owns, for kiln.Back. */
    public kiln.Back.Value<Integer> kiln$camera() {
        return camera;
    }

    /** Kiln's own: whether the game shows subtitles, as it said. */
    public void kiln$subtitles(boolean shown) {
        this.showSubtitles.kiln$value(shown);
    }
}
