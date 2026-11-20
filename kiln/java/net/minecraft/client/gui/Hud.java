package net.minecraft.client.gui;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.Elements.Entry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code Hud}: whether the game's HUD shows (F1 hides it), its chat, and the mods' HUD drawn in
 * {@link #extractRenderState} each frame, where the game draws its HUD (D-093).
 */
public class Hud {
    private final net.minecraft.client.Minecraft minecraft;
    /** The HUD's ticks, one a client tick in a world while the game isn't paused. */
    private int tickCount;

    public Hud(net.minecraft.client.Minecraft minecraft) {
        this.minecraft = minecraft;
        this.chat = new net.minecraft.client.gui.components.ChatComponent(minecraft);
        // A mod's replacement of a vanilla layer gets that layer as this HUD draws it.
        HudElementRegistry.Elements.kiln$layers(id -> (graphics, delta) -> vanilla(id, graphics, delta));
    }

    /**
     * The HUD drawn for a frame, layer by layer in the game's order (D-100): vanilla's are this class's methods, which
     * Horizon draws (empty here: the mods' mixins in them run), and the mods' elements (Fabric's HudElementRegistry)
     * are drawn before or after the ones they were put next to, as the Fabric API draws them inside the game's. An
     * element that throws is said in Kiln's log and the others are drawn.
     */
    public void extractRenderState(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        ran("hud");
        List<Entry> layers = HudElementRegistry.kiln$entries();
        draw(layers, 0, layers.size(), -1, graphics, delta);
    }

    /**
     * The layers from {@code from} to {@code to}, vanilla's groups drawn through their methods; the group that starts at
     * {@code group} is the one being drawn (its first layer is drawn as a layer).
     */
    private void draw(List<Entry> layers, int from, int to, int group, GuiGraphicsExtractor graphics,
            net.minecraft.client.DeltaTracker delta) {
        for (int i = from; i < to; i++) {
            Entry e = layers.get(i);
            if (e.vanilla() && e.element() == null) {
                int end = i == group ? -1 : groupEnd(layers, i, to);
                if (end > i) {
                    // A group of vanilla's layers the game draws in one method of its own.
                    range = new int[] {i, end + 1};
                    current = layers;
                    if (e.id().equals(VanillaHudElements.SPECTATOR_MENU)) {
                        ran("called hotbar_and_decorations");
                        extractHotbarAndDecorations(graphics, delta);
                    } else {
                        extractPlayerHealth(graphics);
                    }
                    i = end;
                    continue;
                }
                vanilla(e.id(), graphics, delta);
                continue;
            }
            if (kiln.SoftSwitch.offFor(e.element())) {
                // Its mod is turned off now (D-113): nothing of it is drawn, and a vanilla layer it replaced is the
                // game's again.
                if (e.vanilla()) {
                    vanilla(e.id(), graphics, delta);
                }
                continue;
            }
            int depth = kiln.Breadcrumb.enter(e.element(), "the HUD element " + e.id());
            try {
                e.element().extractRenderState(graphics, delta);
            } catch (RuntimeException | LinkageError t) {
                kiln.Native.log("a HUD element threw " + t);
                t.printStackTrace();
            } catch (OutOfMemoryError | StackOverflowError t) {
                kiln.SoftSwitch.trip(e.element(), t, "the HUD element " + e.id());
            } finally {
                kiln.Breadcrumb.leave(depth);
            }
        }
    }

    /** The group a vanilla layer starts (the hotbar and what is drawn with it, the player's bars): its last index. */
    private int groupEnd(List<Entry> layers, int at, int to) {
        Identifier id = layers.get(at).id();
        Identifier last = id.equals(VanillaHudElements.SPECTATOR_MENU) ? VanillaHudElements.SPECTATOR_TOOLTIP
                : id.equals(VanillaHudElements.ARMOR_BAR) && inHotbar ? VanillaHudElements.AIR_BAR : null;
        if (last == null) {
            return -1;
        }
        for (int j = at + 1; j < to; j++) {
            if (layers.get(j).id().equals(last)) {
                return j;
            }
        }
        return -1;
    }

    private List<Entry> current = List.of();
    private int[] range = {0, 0};
    private boolean inHotbar;

    /** The layers of the group being drawn, as {@link #draw} set them. */
    private void drawGroup(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        int[] r = range;
        draw(current, r[0], r[1], r[0], graphics, delta);
    }

    /** A vanilla layer: its method in the game's {@code Hud} (Horizon draws what it draws). */
    private void vanilla(Identifier id, GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        net.minecraft.world.entity.player.Player player = minecraft == null ? null : minecraft.player;
        String path = id.getPath();
        switch (path) {
            case "misc_overlays" -> extractCameraOverlays(graphics, delta);
            case "crosshair" -> extractCrosshair(graphics, delta);
            case "hotbar" -> extractItemHotbar(graphics, delta);
            case "armor_bar" -> extractArmor(graphics, player, 0, 0, 0, 0);
            case "health_bar" -> extractHearts(graphics, player, 0, 0, 0, 0, 0.0F, 0, 0, 0, false);
            case "food_bar" -> extractFood(graphics, player, 0, 0);
            case "air_bar" -> extractAirBubbles(graphics, player, 0, 0, 0);
            case "mount_health" -> extractVehicleHealth(graphics);
            case "held_item_tooltip" -> extractSelectedItemName(graphics);
            case "mob_effects" -> extractEffects(graphics, delta);
            case "boss_bar" -> extractBossOverlay(graphics, delta);
            case "sleep" -> extractSleepOverlay(graphics, delta);
            case "demo_timer" -> extractDemoOverlay(graphics, delta);
            case "scoreboard" -> extractScoreboardSidebar(graphics, delta);
            case "overlay_message" -> extractOverlayMessage(graphics, delta);
            case "title_and_subtitle" -> extractTitle(graphics, delta);
            case "chat" -> extractChat(graphics, delta);
            case "player_list" -> extractTabList(graphics, delta);
            case "subtitles" -> extractSubtitleOverlay(graphics, false);
            default -> { } // the spectator's menu and tooltip, the info bar: vanilla's, with no method of their own here
        }
    }

    // Vanilla's layers, the game's methods for them (names and signatures from Minecraft 26.3; Fabric's HudMixin
    // names the same as where its layers are): Horizon draws what they draw. The positions the game works out for the
    // player's bars are its own: here they get 0.
    private void extractCameraOverlays(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        ran("misc_overlays");
    }

    private void extractCrosshair(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        ran("crosshair");
    }

    /** The hotbar and what the game draws with it: the spectator's menu to its tooltip, the player's bars in it. */
    private void extractHotbarAndDecorations(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        ran("hotbar_and_decorations");
        inHotbar = true;
        try {
            drawGroup(graphics, delta);
        } finally {
            inHotbar = false;
        }
    }

    private void extractItemHotbar(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        ran("hotbar");
    }

    /** The player's bars: armour, hearts, food, air. */
    private void extractPlayerHealth(GuiGraphicsExtractor graphics) {
        ran("player_health");
        drawGroup(graphics, net.minecraft.client.DeltaTracker.ZERO);
    }

    private static void extractArmor(GuiGraphicsExtractor graphics, net.minecraft.world.entity.player.Player player,
            int a, int b, int c, int d) {
        ran("armor_bar");
    }

    private void extractHearts(GuiGraphicsExtractor graphics, net.minecraft.world.entity.player.Player player, int a,
            int b, int c, int d, float e, int f, int g, int h, boolean blinking) {
        ran("health_bar");
    }

    private void extractFood(GuiGraphicsExtractor graphics, net.minecraft.world.entity.player.Player player, int a,
            int b) {
        ran("food_bar");
    }

    private void extractAirBubbles(GuiGraphicsExtractor graphics, net.minecraft.world.entity.player.Player player,
            int a, int b, int c) {
        ran("air_bar");
    }

    private void extractVehicleHealth(GuiGraphicsExtractor graphics) {
        ran("mount_health");
    }

    private void extractSelectedItemName(GuiGraphicsExtractor graphics) {
        ran("held_item_tooltip");
    }

    private void extractEffects(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        ran("mob_effects");
    }

    private void extractBossOverlay(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        ran("boss_bar");
    }

    private void extractSleepOverlay(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        ran("sleep");
    }

    private void extractDemoOverlay(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        ran("demo_timer");
    }

    private void extractScoreboardSidebar(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        ran("scoreboard");
    }

    private void extractOverlayMessage(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        ran("overlay_message");
    }

    private void extractTitle(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        ran("title_and_subtitle");
    }

    private void extractChat(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        ran("chat");
    }

    private void extractTabList(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        ran("player_list");
    }

    private void extractSubtitleOverlay(GuiGraphicsExtractor graphics, boolean deferred) {
        ran("subtitles");
    }

    // Which of vanilla's layers Horizon is to hide (Kiln's Mixin bridge, D-145): a layer whose method's own code didn't
    // run in this tick's HUD, because a mod's mixin cancelled it at its head, overwrote it, or cancelled the HUD's own
    // method, or because a mod's element replaced or removed the layer without drawing it (Fabric's
    // HudElementRegistry). Horizon draws vanilla's layers: its gui_layer_pre hook hides the ones named here, a tick
    // late at most.

    /** The layers whose method's own code ran since {@link #kiln$begin}, by the names in {@link #vanilla}. */
    private static final java.util.Set<String> RAN = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Whether this tick's HUD was drawn ({@link #kiln$begin}). */
    private static boolean begun;

    /** Horizon's layers (native_gui_layer_pre's ids) a layer method of this class stands for, the method's name first. */
    private static final String[][] LAYERS = {
        {"crosshair", "crosshair"}, {"hotbar", "hotbar"}, {"mob_effects", "effects"}, {"boss_bar", "boss_overlay"},
        {"mount_health", "vehicle_health"}, {"held_item_tooltip", "selected_item_name"}, {"sleep", "sleep_overlay"},
        {"overlay_message", "overlay_message"}, {"title_and_subtitle", "title"}, {"scoreboard", "scoreboard_sidebar"},
        {"chat", "chat"}, {"player_list", "tab_list"}, {"subtitles", "subtitle_overlay"},
    };
    /** Horizon's layers the hotbar's method draws with it (vanilla's extractHotbarAndDecorations). */
    private static final String[] WITH_HOTBAR = {"hotbar", "contextual_info_bar", "player_health", "vehicle_health",
        "selected_item_name"};
    /** Horizon draws the player's four rows as one layer: hidden only when none of their methods ran. */
    private static final String[] BARS = {"armor_bar", "health_bar", "food_bar", "air_bar"};

    private static void ran(String layer) {
        RAN.add(layer);
    }

    /** Kiln's own: before this tick's HUD is drawn. */
    public static void kiln$begin() {
        RAN.clear();
        begun = true;
    }

    /**
     * Kiln's own: Horizon's layers to hide after this tick's HUD, comma-separated ({@code minecraft:crosshair,...}),
     * {@code -} for none; null when no HUD was drawn since the last call.
     */
    public static String kiln$hidden() {
        if (!begun) {
            return null;
        }
        begun = false;
        java.util.Set<String> hidden = new java.util.LinkedHashSet<>();
        if (!RAN.contains("hud")) {
            // The whole HUD's method cancelled: none of vanilla's layers.
            for (String[] l : LAYERS) {
                hidden.add(l[1]);
            }
            java.util.Collections.addAll(hidden, WITH_HOTBAR);
        } else {
            for (String[] l : LAYERS) {
                if (!RAN.contains(l[0])) {
                    hidden.add(l[1]);
                }
            }
            if (RAN.contains("called hotbar_and_decorations") && !RAN.contains("hotbar_and_decorations")) {
                java.util.Collections.addAll(hidden, WITH_HOTBAR);
            }
            boolean bars = false;
            for (String b : BARS) {
                bars |= RAN.contains(b);
            }
            if (!bars) {
                hidden.add("player_health");
            }
        }
        if (hidden.isEmpty()) {
            return "-";
        }
        List<String> names = new java.util.ArrayList<>();
        for (String h : hidden) {
            names.add("minecraft:" + h);
        }
        return String.join(",", names);
    }

    /** The HUD's tick. */
    private void tick() {
        tickCount++;
    }

    /** Kiln's own: {@link #tick}, from the client's tick. */
    public void kiln$tick() {
        tick();
    }
    private final net.minecraft.client.gui.components.ChatComponent chat;

    /** Kiln's own: the HUD's ticks so far (a chat line's time). */
    public int kiln$ticks() {
        return tickCount;
    }

    /** The HUD's ticks so far. */
    public int getGuiTicks() {
        return tickCount;
    }

    /** The player the camera follows: the window's. */
    public net.minecraft.world.entity.player.Player getCameraPlayer() {
        return minecraft.getCameraEntity() instanceof net.minecraft.world.entity.player.Player p ? p : null;
    }
    private boolean hidden;

    public net.minecraft.client.gui.components.ChatComponent getChat() {
        return chat;
    }

    /** A line over the hotbar; Kiln shows it in chat, as a player's overlay message. */
    public void setOverlayMessage(net.minecraft.network.chat.Component message, boolean animate) {
        kiln.Native.chat(message.getString());
    }

    /** The big title in the middle of the screen: Horizon shows it, as the {@code title} command does. */
    public void setTitle(net.minecraft.network.chat.Component title) {
        kiln.Back.act("title", title.kiln$json().toString());
    }

    public void setSubtitle(net.minecraft.network.chat.Component subtitle) {
        kiln.Back.act("subtitle", subtitle.kiln$json().toString());
    }

    /** How long the title fades in, stays and fades out, in ticks; a negative one keeps the game's. */
    public void setTimes(int fadeIn, int stay, int fadeOut) {
        kiln.Back.act("title_times", fadeIn + " " + stay + " " + fadeOut);
    }

    public void clearTitles() {
        kiln.Back.act("clear_titles", "");
    }

    public void resetTitleTimes() {
        kiln.Back.act("reset_title_times", "");
    }

    /** The HUD's sprite of an effect: {@code mob_effect/<its path>}, as vanilla's. */
    public static Identifier getMobEffectSprite(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        return effect.unwrapKey().map(k -> Identifier.fromNamespaceAndPath(k.identifier().getNamespace(),
                "mob_effect/" + k.identifier().getPath()))
                .orElse(Identifier.withDefaultNamespace("mob_effect/missing"));
    }

    public boolean isHidden() {
        return hidden;
    }

    /** Kiln's own: F1, as the game said. */
    public void kiln$hidden(boolean hidden) {
        this.hidden = hidden;
    }
    /** The kinds of heart the health bar draws, each by its sprites (the game's names, {@code hud/heart/...}). */
    public enum HeartType {
        CONTAINER("container"),
        NORMAL(""),
        POISIONED("poisoned_"),
        WITHERED("withered_"),
        ABSORBING("absorbing_"),
        FROZEN("frozen_");

        public final net.minecraft.resources.Identifier full;
        public final net.minecraft.resources.Identifier fullBlinking;
        public final net.minecraft.resources.Identifier half;
        public final net.minecraft.resources.Identifier halfBlinking;
        public final net.minecraft.resources.Identifier hardcoreFull;
        public final net.minecraft.resources.Identifier hardcoreFullBlinking;
        public final net.minecraft.resources.Identifier hardcoreHalf;
        public final net.minecraft.resources.Identifier hardcoreHalfBlinking;

        HeartType(String prefix) {
            boolean container = prefix.equals("container");
            full = heart(container ? "container" : prefix + "full");
            fullBlinking = heart(container ? "container_blinking" : prefix + "full_blinking");
            half = heart(container ? "container" : prefix + "half");
            halfBlinking = heart(container ? "container_blinking" : prefix + "half_blinking");
            hardcoreFull = heart(container ? "container_hardcore" : prefix + "hardcore_full");
            hardcoreFullBlinking = heart(container ? "container_hardcore_blinking" : prefix + "hardcore_full_blinking");
            hardcoreHalf = heart(container ? "container_hardcore" : prefix + "hardcore_half");
            hardcoreHalfBlinking = heart(container ? "container_hardcore_blinking" : prefix + "hardcore_half_blinking");
        }

        private static net.minecraft.resources.Identifier heart(String name) {
            return net.minecraft.resources.Identifier.withDefaultNamespace("hud/heart/" + name);
        }

        /** The sprite for a heart: hardcore or not, half or whole, blinking or not. */
        public net.minecraft.resources.Identifier getSprite(boolean hardcore, boolean half, boolean blinking) {
            if (hardcore) {
                return half ? (blinking ? hardcoreHalfBlinking : hardcoreHalf)
                        : (blinking ? hardcoreFullBlinking : hardcoreFull);
            }
            return half ? (blinking ? halfBlinking : this.half) : (blinking ? fullBlinking : full);
        }

        /** The player's hearts: Kiln reads no effect here, so the normal ones. */
        public static HeartType forPlayer(net.minecraft.world.entity.player.Player player) {
            return NORMAL;
        }
    }

    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    private static final net.minecraft.resources.Identifier HOTBAR_SPRITE = null;
    private static final net.minecraft.resources.Identifier HOTBAR_OFFHAND_LEFT_SPRITE = null;
    private net.minecraft.util.RandomSource random;
    private net.minecraft.network.chat.Component overlayMessageString;
    private int overlayMessageTime;
    private int toolHighlightTimer;
    public java.lang.Object tabList;
    private net.minecraft.network.chat.Component title;
    private net.minecraft.network.chat.Component subtitle;
    public int displayHealth;
    private boolean canRenderCrosshairForSpectator(net.minecraft.world.phys.HitResult p0) { kiln.Missing.called("net.minecraft.client.gui.Hud.canRenderCrosshairForSpectator"); return false; }
    public net.minecraft.world.entity.LivingEntity getPlayerVehicleWithHealth() { kiln.Missing.called("net.minecraft.client.gui.Hud.getPlayerVehicleWithHealth"); return null; }
    public int getVehicleMaxHearts(net.minecraft.world.entity.LivingEntity p0) { kiln.Missing.called("net.minecraft.client.gui.Hud.getVehicleMaxHearts"); return 0; }
    public int getVisibleVehicleHeartRows(int p0) { kiln.Missing.called("net.minecraft.client.gui.Hud.getVisibleVehicleHeartRows"); return 0; }
    private void extractSlot(net.minecraft.client.gui.GuiGraphicsExtractor p0, int p1, int p2, net.minecraft.client.DeltaTracker p3, net.minecraft.world.entity.player.Player p4, net.minecraft.world.item.ItemStack p5, int p6) { kiln.Missing.called("net.minecraft.client.gui.Hud.extractSlot"); }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static enum ContextualInfo {
        EMPTY, EXPERIENCE, LOCATOR, JUMPABLE_VEHICLE;
    }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
}
