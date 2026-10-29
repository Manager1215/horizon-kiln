package net.fabricmc.fabric.api.client.rendering.v1.hud;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for Fabric's {@code HudElementRegistry}: the mods' HUD elements, drawn over Horizon's HUD in
 * the order they were added. Vanilla's own elements aren't in it: "before" and "after" one of them is the end.
 */
public interface HudElementRegistry {
    static void addFirst(Identifier id, HudElement element) {
        Elements.put(id, element, true);
    }

    static void addLast(Identifier id, HudElement element) {
        Elements.put(id, element, false);
    }

    static void attachElementBefore(Identifier before, Identifier id, HudElement element) {
        Elements.put(id, element, false);
    }

    static void attachElementAfter(Identifier after, Identifier id, HudElement element) {
        Elements.put(id, element, false);
    }

    static void removeElement(Identifier id) {
        Elements.BY_ORDER.values().removeIf(e -> e.id().equals(id));
    }

    static void replaceElement(Identifier id, Function<HudElement, HudElement> replacer) {
        Elements.BY_ORDER.replaceAll((order, e) -> e.id().equals(id) ? new Elements.Entry(id, replacer.apply(e.element())) : e);
    }

    /** Kiln's own: the elements in drawing order. */
    static Iterable<HudElement> kiln$elements() {
        return Elements.BY_ORDER.values().stream().map(Elements.Entry::element)::iterator;
    }

    final class Elements {
        record Entry(Identifier id, HudElement element) {}

        static final Map<Long, Entry> BY_ORDER = new ConcurrentSkipListMap<>();
        private static long first = 0;
        private static long last = 0;

        static synchronized void put(Identifier id, HudElement element, boolean atStart) {
            BY_ORDER.put(atStart ? --first : ++last, new Entry(id, element));
        }

        private Elements() {}
    }
}
