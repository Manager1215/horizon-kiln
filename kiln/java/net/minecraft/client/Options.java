package net.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Kiln's stand-in for {@code Options}: the game's options mods read, as the window's game said with the tick (field
 * of view, render distance, GUI scale, brightness), and vanilla's key mappings, on their default keys.
 */
public class Options {
    private final OptionInstance<Integer> fov = new OptionInstance<>("fov", 70, new OptionInstance.IntRange(30, 110));
    private final OptionInstance<Integer> renderDistance =
            new OptionInstance<>("render_distance", 12, new OptionInstance.IntRange(2, 32));
    /** The GUI scale (0 "auto") and the brightness: a mod's change goes back to Horizon (kiln.Back, D-192). */
    private final OptionInstance<Integer> guiScale =
            new OptionInstance<>("gui_scale", 0, v -> java.util.Optional.ofNullable(v).filter(s -> s >= 0));
    /** Public and writable, as the mods' access wideners make vanilla's (Collective's, #271). */
    public OptionInstance<Double> gamma =
            new OptionInstance<>("gamma", 0.5, OptionInstance.UnitDouble.INSTANCE);
    private final OptionInstance<Boolean> showSubtitles =
            new OptionInstance<>("show_subtitles", false, v -> java.util.Optional.ofNullable(v));
    /** The creative inventory's operator items tab: off, as the game's default (Horizon doesn't say). */
    private final OptionInstance<Boolean> operatorItemsTab = new OptionInstance<>(false);
    private final OptionInstance<Boolean> toggleSprint = new OptionInstance<>(false);
    /** The game's cinematic camera: off; Horizon's camera doesn't read it. */
    public boolean smoothCamera;
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

    /** The view distance the server said (vanilla's login and its chunk radius packet). */
    public void setServerRenderDistance(int distance) {
        serverRenderDistance = distance;
    }

    public OptionInstance<Integer> renderDistance() {
        return renderDistance;
    }

    public OptionInstance<Integer> guiScale() {
        return guiScale;
    }

    public OptionInstance<Double> gamma() {
        return gamma;
    }

    public OptionInstance<Boolean> showSubtitles() {
        return showSubtitles;
    }

    /** Whether sprint is a toggle: Kiln's own, off at the start; Horizon's keys don't read it. */
    public OptionInstance<Boolean> toggleSprint() {
        return toggleSprint;
    }

    public OptionInstance<Boolean> operatorItemsTab() {
        return operatorItemsTab;
    }

    /**
     * Kiln's own: the options the game said. A mod's change of the field of view, the render distance or the
     * subtitles goes back to Horizon (kiln.Back, D-105), and of the GUI scale or the brightness (D-192).
     */
    public void kiln$set(int fov, int renderDistance, int guiScale) {
        this.fov.kiln$value(fov);
        this.renderDistance.kiln$value(renderDistance);
        this.guiScale.kiln$value(guiScale);
    }

    /**
     * The volume sliders, by {@code SoundSource}'s order, as the game said with the tick (D-232): a mod's {@code set}
     * goes back to Horizon (kiln.Back's {@code volume_<source>}), as its sound options set them.
     */
    private final OptionInstance<Double>[] volumes = volumes();

    @SuppressWarnings("unchecked")
    private static OptionInstance<Double>[] volumes() {
        net.minecraft.sounds.SoundSource[] sources = net.minecraft.sounds.SoundSource.values();
        OptionInstance<Double>[] all = new OptionInstance[sources.length];
        for (net.minecraft.sounds.SoundSource source : sources) {
            all[source.ordinal()] = new OptionInstance<>("volume_" + source.getName(), 1.0,
                    OptionInstance.UnitDouble.INSTANCE);
        }
        return all;
    }

    /** The slider of {@code source}. */
    public OptionInstance<Double> getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource source) {
        return volumes[source.ordinal()];
    }

    /** The slider of {@code source}, 0 to 1. */
    public float getSoundSourceVolume(net.minecraft.sounds.SoundSource source) {
        return getSoundSourceOptionInstance(source).get().floatValue();
    }

    /** What a sound of {@code source} plays at: its slider times the master's, as the game's (the master's alone). */
    public float getFinalSoundSourceVolume(net.minecraft.sounds.SoundSource source) {
        float master = getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
        return source == net.minecraft.sounds.SoundSource.MASTER ? master : master * getSoundSourceVolume(source);
    }

    /** Kiln's own: the sliders as kiln.Back sends a mod's changes. */
    public java.util.List<kiln.Back.Value<?>> kiln$volumesBack() {
        java.util.List<kiln.Back.Value<?>> all = new java.util.ArrayList<>();
        for (OptionInstance<Double> volume : volumes) {
            all.add(volume.kiln$back());
        }
        return all;
    }

    /** Kiln's own: the sliders the game said, by {@code SoundSource}'s order, joined by spaces; a missing one keeps its own. */
    public void kiln$volumes(String said) {
        String[] values = said.isEmpty() ? new String[0] : said.split(" ");
        for (int i = 0; i < values.length && i < volumes.length; i++) {
            try {
                // Rounded to a float, as Horizon keeps it (as the brightness).
                volumes[i].kiln$value((double) Float.parseFloat(values[i]));
            } catch (NumberFormatException e) {
                // Not a number: the slider keeps its value.
            }
        }
    }

    /** Kiln's own: the brightness the game said. */
    public void kiln$gamma(double gamma) {
        this.gamma.kiln$value(gamma);
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
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public int serverRenderDistance;
    public java.util.List<java.lang.String> resourcePacks;
    public java.util.List<java.lang.String> incompatibleResourcePacks;
    public boolean pauseOnLostFocus;
    private java.util.Set<net.minecraft.world.entity.player.PlayerModelPart> modelParts;
    public net.minecraft.client.tutorial.TutorialSteps tutorialStep;
    public boolean joinedFirstServer;
    public int glDebugVerbosity;
    public static final net.minecraft.network.chat.Component KEY_TOGGLE = null;
    public static final net.minecraft.network.chat.Component KEY_HOLD = null;
    public boolean skipMultiplayerWarning;
    public net.minecraft.client.KeyMapping keySmoothCamera;
    public net.minecraft.client.KeyMapping keyQuickActions;
    public net.minecraft.client.KeyMapping keySpectatorOutlines;
    public net.minecraft.client.KeyMapping keyDebugModifier;
    public java.lang.String lastMpIp;
    public java.lang.String languageCode;
    public boolean onboardAccessibility;
    public net.minecraft.client.OptionInstance<java.lang.Boolean> hideLightningFlash() { kiln.Missing.called("net.minecraft.client.Options.hideLightningFlash"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Double> sensitivity() { kiln.Missing.called("net.minecraft.client.Options.sensitivity"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Integer> simulationDistance() { kiln.Missing.called("net.minecraft.client.Options.simulationDistance"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Double> entityDistanceScaling() { kiln.Missing.called("net.minecraft.client.Options.entityDistanceScaling"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Integer> framerateLimit() { kiln.Missing.called("net.minecraft.client.Options.framerateLimit"); return null; }
    public net.minecraft.client.OptionInstance<net.minecraft.client.PreferredGraphicsApi> preferredGraphicsBackend() { kiln.Missing.called("net.minecraft.client.Options.preferredGraphicsBackend"); return null; }
    public net.minecraft.client.OptionInstance<net.minecraft.client.GraphicsPreset> graphicsPreset() { kiln.Missing.called("net.minecraft.client.Options.graphicsPreset"); return null; }
    public net.minecraft.client.OptionInstance<net.minecraft.client.InactivityFpsLimit> inactivityFpsLimit() { kiln.Missing.called("net.minecraft.client.Options.inactivityFpsLimit"); return null; }
    public net.minecraft.client.OptionInstance<net.minecraft.client.CloudStatus> cloudStatus() { kiln.Missing.called("net.minecraft.client.Options.cloudStatus"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Integer> cloudRange() { kiln.Missing.called("net.minecraft.client.Options.cloudRange"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Integer> weatherRadius() { kiln.Missing.called("net.minecraft.client.Options.weatherRadius"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> cutoutLeaves() { kiln.Missing.called("net.minecraft.client.Options.cutoutLeaves"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> vignette() { kiln.Missing.called("net.minecraft.client.Options.vignette"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> improvedTransparency() { kiln.Missing.called("net.minecraft.client.Options.improvedTransparency"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> ambientOcclusion() { kiln.Missing.called("net.minecraft.client.Options.ambientOcclusion"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Double> chunkSectionFadeInTime() { kiln.Missing.called("net.minecraft.client.Options.chunkSectionFadeInTime"); return null; }
    public net.minecraft.client.OptionInstance<net.minecraft.client.PrioritizeChunkUpdates> prioritizeChunkUpdates() { kiln.Missing.called("net.minecraft.client.Options.prioritizeChunkUpdates"); return null; }
    public void updateResourcePacks(net.minecraft.server.packs.repository.PackRepository p0) { kiln.Missing.called("net.minecraft.client.Options.updateResourcePacks"); }
    public net.minecraft.client.OptionInstance<net.minecraft.world.entity.player.ChatVisiblity> chatVisibility() { kiln.Missing.called("net.minecraft.client.Options.chatVisibility"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Integer> menuBackgroundBlurriness() { kiln.Missing.called("net.minecraft.client.Options.menuBackgroundBlurriness"); return null; }
    public int getMenuBackgroundBlurriness() { kiln.Missing.called("net.minecraft.client.Options.getMenuBackgroundBlurriness"); return 0; }
    public net.minecraft.client.OptionInstance<java.lang.Double> textBackgroundOpacity() { kiln.Missing.called("net.minecraft.client.Options.textBackgroundOpacity"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> highContrast() { kiln.Missing.called("net.minecraft.client.Options.highContrast"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> highContrastBlockOutline() { kiln.Missing.called("net.minecraft.client.Options.highContrastBlockOutline"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> narratorHotkey() { kiln.Missing.called("net.minecraft.client.Options.narratorHotkey"); return null; }
    public net.minecraft.client.OptionInstance<net.minecraft.world.entity.HumanoidArm> mainHand() { kiln.Missing.called("net.minecraft.client.Options.mainHand"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Double> chatWidth() { kiln.Missing.called("net.minecraft.client.Options.chatWidth"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Double> chatHeightUnfocused() { kiln.Missing.called("net.minecraft.client.Options.chatHeightUnfocused"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Double> notificationDisplayTime() { kiln.Missing.called("net.minecraft.client.Options.notificationDisplayTime"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Integer> mipmapLevels() { kiln.Missing.called("net.minecraft.client.Options.mipmapLevels"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Integer> maxAnisotropyBit() { kiln.Missing.called("net.minecraft.client.Options.maxAnisotropyBit"); return null; }
    public int maxAnisotropyValue() { kiln.Missing.called("net.minecraft.client.Options.maxAnisotropyValue"); return 0; }
    public net.minecraft.client.OptionInstance<net.minecraft.client.TextureFilteringMethod> textureFiltering() { kiln.Missing.called("net.minecraft.client.Options.textureFiltering"); return null; }
    public net.minecraft.client.OptionInstance<net.minecraft.client.AttackIndicatorStatus> attackIndicator() { kiln.Missing.called("net.minecraft.client.Options.attackIndicator"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Integer> biomeBlendRadius() { kiln.Missing.called("net.minecraft.client.Options.biomeBlendRadius"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Double> mouseWheelSensitivity() { kiln.Missing.called("net.minecraft.client.Options.mouseWheelSensitivity"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> autoSuggestions() { kiln.Missing.called("net.minecraft.client.Options.autoSuggestions"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> chatLinks() { kiln.Missing.called("net.minecraft.client.Options.chatLinks"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> chatLinksPrompt() { kiln.Missing.called("net.minecraft.client.Options.chatLinksPrompt"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> enableVsync() { kiln.Missing.called("net.minecraft.client.Options.enableVsync"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> entityShadows() { kiln.Missing.called("net.minecraft.client.Options.entityShadows"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> forceUnicodeFont() { kiln.Missing.called("net.minecraft.client.Options.forceUnicodeFont"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> invertMouseY() { kiln.Missing.called("net.minecraft.client.Options.invertMouseY"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> discreteMouseScroll() { kiln.Missing.called("net.minecraft.client.Options.discreteMouseScroll"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> realmsNotifications() { kiln.Missing.called("net.minecraft.client.Options.realmsNotifications"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> fullscreen() { kiln.Missing.called("net.minecraft.client.Options.fullscreen"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> exclusiveFullscreen() { kiln.Missing.called("net.minecraft.client.Options.exclusiveFullscreen"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> macFullscreenMenuVisibility() { kiln.Missing.called("net.minecraft.client.Options.macFullscreenMenuVisibility"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> bobView() { kiln.Missing.called("net.minecraft.client.Options.bobView"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> toggleCrouch() { kiln.Missing.called("net.minecraft.client.Options.toggleCrouch"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Integer> sprintWindow() { kiln.Missing.called("net.minecraft.client.Options.sprintWindow"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> hideMatchedNames() { kiln.Missing.called("net.minecraft.client.Options.hideMatchedNames"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> showAutosaveIndicator() { kiln.Missing.called("net.minecraft.client.Options.showAutosaveIndicator"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> onlyShowSecureChat() { kiln.Missing.called("net.minecraft.client.Options.onlyShowSecureChat"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Boolean> saveChatDrafts() { kiln.Missing.called("net.minecraft.client.Options.saveChatDrafts"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Double> screenEffectScale() { kiln.Missing.called("net.minecraft.client.Options.screenEffectScale"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Double> fovEffectScale() { kiln.Missing.called("net.minecraft.client.Options.fovEffectScale"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Double> darknessEffectScale() { kiln.Missing.called("net.minecraft.client.Options.darknessEffectScale"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Double> glintStrength() { kiln.Missing.called("net.minecraft.client.Options.glintStrength"); return null; }
    public net.minecraft.client.OptionInstance<java.lang.Double> damageTiltStrength() { kiln.Missing.called("net.minecraft.client.Options.damageTiltStrength"); return null; }
    public net.minecraft.client.OptionInstance<net.minecraft.server.level.ParticleStatus> particles() { kiln.Missing.called("net.minecraft.client.Options.particles"); return null; }
    public net.minecraft.client.OptionInstance<net.minecraft.client.NarratorStatus> narrator() { kiln.Missing.called("net.minecraft.client.Options.narrator"); return null; }
    public net.minecraft.client.OptionInstance<net.minecraft.client.MusicToastDisplayState> musicToast() { kiln.Missing.called("net.minecraft.client.Options.musicToast"); return null; }
    public int getBackgroundColor(float p0) { kiln.Missing.called("net.minecraft.client.Options.getBackgroundColor"); return 0; }
    public int getBackgroundColor(int p0) { kiln.Missing.called("net.minecraft.client.Options.getBackgroundColor"); return 0; }
    private void processOptions(net.minecraft.client.Options.FieldAccess p0) { kiln.Missing.called("net.minecraft.client.Options.processOptions"); }
    public void broadcastOptions() { kiln.Missing.called("net.minecraft.client.Options.broadcastOptions"); }
    public boolean isModelPartEnabled(net.minecraft.world.entity.player.PlayerModelPart p0) { kiln.Missing.called("net.minecraft.client.Options.isModelPartEnabled"); return false; }
    public boolean useNativeTransport() { kiln.Missing.called("net.minecraft.client.Options.useNativeTransport"); return false; }
    public void loadSelectedResourcePacks(net.minecraft.server.packs.repository.PackRepository p0) { kiln.Missing.called("net.minecraft.client.Options.loadSelectedResourcePacks"); }
    public java.io.File getFile() { kiln.Missing.called("net.minecraft.client.Options.getFile"); return null; }
    public int getEffectiveRenderDistance() { kiln.Missing.called("net.minecraft.client.Options.getEffectiveRenderDistance"); return 0; }
    public static net.minecraft.network.chat.Component genericValueLabel(net.minecraft.network.chat.Component p0, net.minecraft.network.chat.Component p1) { kiln.Missing.called("net.minecraft.client.Options.genericValueLabel"); return kiln.Constants.nothing(net.minecraft.network.chat.Component.class); }
    public static net.minecraft.network.chat.Component genericValueLabel(net.minecraft.network.chat.Component p0, int p1) { kiln.Missing.called("net.minecraft.client.Options.genericValueLabel"); return kiln.Constants.nothing(net.minecraft.network.chat.Component.class); }
    public static interface OptionAccess {
        <T> void process(java.lang.String p0, net.minecraft.client.OptionInstance<T> p1);
    }
    public static interface FieldAccess extends net.minecraft.client.Options.OptionAccess {
        int process(java.lang.String p0, int p1);
        boolean process(java.lang.String p0, boolean p1);
        java.lang.String process(java.lang.String p0, java.lang.String p1);
        float process(java.lang.String p0, float p1);
        <T> T process(java.lang.String p0, T p1, java.util.function.Function<java.lang.String, T> p2, java.util.function.Function<T, java.lang.String> p3);
    }
    // End of what tools/kiln-stubs.py generated.
}
