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
            try {
                e.element().extractRenderState(graphics, delta);
            } catch (RuntimeException | LinkageError t) {
                kiln.Native.log("a HUD element threw " + t);
                t.printStackTrace();
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
    private void extractCameraOverlays(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {}

    private void extractCrosshair(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {}

    /** The hotbar and what the game draws with it: the spectator's menu to its tooltip, the player's bars in it. */
    private void extractHotbarAndDecorations(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        inHotbar = true;
        try {
            drawGroup(graphics, delta);
        } finally {
            inHotbar = false;
        }
    }

    private void extractItemHotbar(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {}

    /** The player's bars: armour, hearts, food, air. */
    private void extractPlayerHealth(GuiGraphicsExtractor graphics) {
        drawGroup(graphics, net.minecraft.client.DeltaTracker.ZERO);
    }

    private static void extractArmor(GuiGraphicsExtractor graphics, net.minecraft.world.entity.player.Player player,
            int a, int b, int c, int d) {}

    private void extractHearts(GuiGraphicsExtractor graphics, net.minecraft.world.entity.player.Player player, int a,
            int b, int c, int d, float e, int f, int g, int h, boolean blinking) {}

    private void extractFood(GuiGraphicsExtractor graphics, net.minecraft.world.entity.player.Player player, int a,
            int b) {}

    private void extractAirBubbles(GuiGraphicsExtractor graphics, net.minecraft.world.entity.player.Player player,
            int a, int b, int c) {}

    private void extractVehicleHealth(GuiGraphicsExtractor graphics) {}

    private void extractSelectedItemName(GuiGraphicsExtractor graphics) {}

    private void extractEffects(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {}

    private void extractBossOverlay(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {}

    private void extractSleepOverlay(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {}

    private void extractDemoOverlay(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {}

    private void extractScoreboardSidebar(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {}

    private void extractOverlayMessage(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {}

    private void extractTitle(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {}

    private void extractChat(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {}

    private void extractTabList(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {}

    private void extractSubtitleOverlay(GuiGraphicsExtractor graphics, boolean deferred) {}

    /** The HUD's tick. */
    private void tick() {
        tickCount++;
    }

    /** Kiln's own: {@link #tick}, from the client's tick. */
    public void kiln$tick() {
        tick();
    }
    private final net.minecraft.client.gui.components.ChatComponent chat =
            new net.minecraft.client.gui.components.ChatComponent();
    private boolean hidden;

    public net.minecraft.client.gui.components.ChatComponent getChat() {
        return chat;
    }

    /** A line over the hotbar; Kiln shows it in chat, as a player's overlay message. */
    public void setOverlayMessage(net.minecraft.network.chat.Component message, boolean animate) {
        kiln.Native.chat(message.getString());
    }

    public boolean isHidden() {
        return hidden;
    }

    /** Kiln's own: F1, as the game said. */
    public void kiln$hidden(boolean hidden) {
        this.hidden = hidden;
    }
}
