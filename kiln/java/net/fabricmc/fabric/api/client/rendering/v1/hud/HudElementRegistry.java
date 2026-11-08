package net.fabricmc.fabric.api.client.rendering.v1.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for Fabric's {@code HudElementRegistry}: the HUD's layers in the order the game draws them, vanilla's
 * ({@link VanillaHudElements}, Horizon draws them; in Kiln they are the game's {@code Hud} methods, empty, where mods'
 * mixins go) and the mods' around them (D-100). {@code Hud.extractRenderState} goes through them in order.
 */
public interface HudElementRegistry {
    static void addFirst(Identifier id, HudElement element) {
        Elements.insert(0, id, element);
    }

    static void addLast(Identifier id, HudElement element) {
        Elements.insert(Elements.size(), id, element);
    }

    static void attachElementBefore(Identifier before, Identifier id, HudElement element) {
        int at = Elements.indexOf(before);
        Elements.insert(at < 0 ? Elements.size() : at, id, element);
    }

    static void attachElementAfter(Identifier after, Identifier id, HudElement element) {
        int at = Elements.indexOf(after);
        Elements.insert(at < 0 ? Elements.size() : at + 1, id, element);
    }

    /** A mod's element taken out; vanilla's can't be: Horizon draws them. */
    static void removeElement(Identifier id) {
        synchronized (Elements.ALL) {
            Elements.ALL.removeIf(e -> !e.vanilla() && e.id().equals(id));
        }
    }

    /**
     * An element replaced by what {@code replacer} makes of it. A vanilla one's replacement is drawn in its place; it
     * gets the layer (the game's {@code Hud} method) to call, and Horizon still draws vanilla's own.
     */
    static void replaceElement(Identifier id, Function<HudElement, HudElement> replacer) {
        synchronized (Elements.ALL) {
            Elements.ALL.replaceAll(e -> e.id().equals(id) ? new Elements.Entry(id, replacer.apply(
                    e.element() != null ? e.element() : Elements.layer(id)), e.vanilla()) : e);
        }
    }

    /** Kiln's own: the layers in drawing order, vanilla's with no element of a mod's (null) unless replaced. */
    static List<Elements.Entry> kiln$entries() {
        synchronized (Elements.ALL) {
            return List.copyOf(Elements.ALL);
        }
    }

    /** Kiln's own: the mods' elements in drawing order. */
    static Iterable<HudElement> kiln$elements() {
        return kiln$entries().stream().filter(e -> e.element() != null).map(Elements.Entry::element)::iterator;
    }

    final class Elements {
        /** A layer: a mod's element, or vanilla's ({@code vanilla}, its element null unless a mod replaced it). */
        public record Entry(Identifier id, HudElement element, boolean vanilla) {}

        /** Vanilla's layers in the game's order (Fabric's VanillaHudElements). */
        static final List<Identifier> VANILLA = List.of(VanillaHudElements.MISC_OVERLAYS, VanillaHudElements.CROSSHAIR,
                VanillaHudElements.SPECTATOR_MENU, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
                VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR,
                VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
                VanillaHudElements.INFO_BAR_FOREGROUND, VanillaHudElements.HELD_ITEM_TOOLTIP,
                VanillaHudElements.SPECTATOR_TOOLTIP, VanillaHudElements.MOB_EFFECTS, VanillaHudElements.BOSS_BAR,
                VanillaHudElements.SLEEP, VanillaHudElements.DEMO_TIMER, VanillaHudElements.SCOREBOARD,
                VanillaHudElements.OVERLAY_MESSAGE, VanillaHudElements.TITLE_AND_SUBTITLE, VanillaHudElements.CHAT,
                VanillaHudElements.PLAYER_LIST, VanillaHudElements.SUBTITLES);
        static final List<Entry> ALL = new ArrayList<>();
        /** What draws a vanilla layer, set by the game's {@code Hud}: its method for that layer. */
        static volatile Function<Identifier, HudElement> layers = id -> (graphics, delta) -> {};

        static {
            for (Identifier id : VANILLA) {
                ALL.add(new Entry(id, null, true));
            }
        }

        static int size() {
            synchronized (ALL) {
                return ALL.size();
            }
        }

        static int indexOf(Identifier id) {
            synchronized (ALL) {
                for (int i = 0; i < ALL.size(); i++) {
                    if (ALL.get(i).id().equals(id)) {
                        return i;
                    }
                }
                return -1;
            }
        }

        static void insert(int at, Identifier id, HudElement element) {
            synchronized (ALL) {
                ALL.add(Math.min(at, ALL.size()), new Entry(id, element, false));
            }
        }

        /** Vanilla's layer {@code id}, as an element: the game's {@code Hud} method for it. */
        public static HudElement layer(Identifier id) {
            return (graphics, delta) -> layers.apply(id).extractRenderState(graphics, delta);
        }

        /** Kiln's own: the game's {@code Hud} tells which of its methods draws each vanilla layer. */
        public static void kiln$layers(Function<Identifier, HudElement> draw) {
            layers = draw;
        }

        private Elements() {}
    }
}
