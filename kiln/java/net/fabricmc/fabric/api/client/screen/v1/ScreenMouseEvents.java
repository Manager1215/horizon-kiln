package net.fabricmc.fabric.api.client.screen.v1;

import java.util.List;
import kiln.Native;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Kiln's stand-in for Fabric's {@code ScreenMouseEvents}: a screen's mouse events, each screen its own, as Fabric's
 * mixin into the game's mouse handler fires them (D-156). Kiln fires them for a mod's own screen (D-094) and for the
 * game's container screens, where a click Java's mods take ({@code allow...} false) is the mods' alone: on a game's
 * screen the game has already left it alone where the mods drew, and does what it does elsewhere.
 */
public final class ScreenMouseEvents {
    private ScreenMouseEvents() {}

    public static Event<AllowMouseClick> allowMouseClick(Screen screen) {
        return ScreenEvents.own(screen, AllowMouseClick.class, ls -> (s, e) -> allAllow(ls, l -> l.allowMouseClick(s, e)));
    }

    public static Event<BeforeMouseClick> beforeMouseClick(Screen screen) {
        return ScreenEvents.own(screen, BeforeMouseClick.class, ls -> (s, e) -> each(ls, l -> l.beforeMouseClick(s, e)));
    }

    public static Event<AfterMouseClick> afterMouseClick(Screen screen) {
        return ScreenEvents.own(screen, AfterMouseClick.class, ls -> (s, e, consumed) -> {
            for (AfterMouseClick l : ls) {
                try {
                    consumed = l.afterMouseClick(s, e, consumed);
                } catch (RuntimeException | LinkageError x) {
                    Native.log("a screen's mouse listener threw " + x);
                }
            }
            return consumed;
        });
    }

    public static Event<AllowMouseRelease> allowMouseRelease(Screen screen) {
        return ScreenEvents.own(screen, AllowMouseRelease.class,
                ls -> (s, e) -> allAllow(ls, l -> l.allowMouseRelease(s, e)));
    }

    public static Event<BeforeMouseRelease> beforeMouseRelease(Screen screen) {
        return ScreenEvents.own(screen, BeforeMouseRelease.class,
                ls -> (s, e) -> each(ls, l -> l.beforeMouseRelease(s, e)));
    }

    public static Event<AfterMouseRelease> afterMouseRelease(Screen screen) {
        return ScreenEvents.own(screen, AfterMouseRelease.class, ls -> (s, e, consumed) -> {
            for (AfterMouseRelease l : ls) {
                try {
                    consumed = l.afterMouseRelease(s, e, consumed);
                } catch (RuntimeException | LinkageError x) {
                    Native.log("a screen's mouse listener threw " + x);
                }
            }
            return consumed;
        });
    }

    public static Event<AllowMouseDrag> allowMouseDrag(Screen screen) {
        return ScreenEvents.own(screen, AllowMouseDrag.class,
                ls -> (s, e, dx, dy) -> allAllow(ls, l -> l.allowMouseDrag(s, e, dx, dy)));
    }

    public static Event<BeforeMouseDrag> beforeMouseDrag(Screen screen) {
        return ScreenEvents.own(screen, BeforeMouseDrag.class,
                ls -> (s, e, dx, dy) -> each(ls, l -> l.beforeMouseDrag(s, e, dx, dy)));
    }

    public static Event<AfterMouseDrag> afterMouseDrag(Screen screen) {
        return ScreenEvents.own(screen, AfterMouseDrag.class, ls -> (s, e, dx, dy, consumed) -> {
            for (AfterMouseDrag l : ls) {
                try {
                    consumed = l.afterMouseDrag(s, e, dx, dy, consumed);
                } catch (RuntimeException | LinkageError x) {
                    Native.log("a screen's mouse listener threw " + x);
                }
            }
            return consumed;
        });
    }

    public static Event<AllowMouseScroll> allowMouseScroll(Screen screen) {
        return ScreenEvents.own(screen, AllowMouseScroll.class,
                ls -> (s, x, y, h, v) -> allAllow(ls, l -> l.allowMouseScroll(s, x, y, h, v)));
    }

    public static Event<BeforeMouseScroll> beforeMouseScroll(Screen screen) {
        return ScreenEvents.own(screen, BeforeMouseScroll.class,
                ls -> (s, x, y, h, v) -> each(ls, l -> l.beforeMouseScroll(s, x, y, h, v)));
    }

    public static Event<AfterMouseScroll> afterMouseScroll(Screen screen) {
        return ScreenEvents.own(screen, AfterMouseScroll.class, ls -> (s, x, y, h, v, consumed) -> {
            for (AfterMouseScroll l : ls) {
                try {
                    consumed = l.afterMouseScroll(s, x, y, h, v, consumed);
                } catch (RuntimeException | LinkageError e) {
                    Native.log("a screen's mouse listener threw " + e);
                }
            }
            return consumed;
        });
    }

    /** Whether every listener lets the event through: the first that doesn't stops it (a throw lets it through). */
    static <T> boolean allAllow(List<T> listeners, java.util.function.Predicate<T> allows) {
        for (T l : listeners) {
            try {
                if (!allows.test(l)) {
                    return false;
                }
            } catch (RuntimeException | LinkageError e) {
                Native.log("a screen's input listener threw " + e);
                e.printStackTrace();
            }
        }
        return true;
    }

    static <T> void each(List<T> listeners, java.util.function.Consumer<T> call) {
        for (T l : listeners) {
            try {
                call.accept(l);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a screen's input listener threw " + e);
                e.printStackTrace();
            }
        }
    }

    @FunctionalInterface
    public interface AllowMouseClick {
        boolean allowMouseClick(Screen screen, MouseButtonEvent event);
    }

    @FunctionalInterface
    public interface BeforeMouseClick {
        void beforeMouseClick(Screen screen, MouseButtonEvent event);
    }

    @FunctionalInterface
    public interface AfterMouseClick {
        boolean afterMouseClick(Screen screen, MouseButtonEvent event, boolean consumed);
    }

    @FunctionalInterface
    public interface AllowMouseRelease {
        boolean allowMouseRelease(Screen screen, MouseButtonEvent event);
    }

    @FunctionalInterface
    public interface BeforeMouseRelease {
        void beforeMouseRelease(Screen screen, MouseButtonEvent event);
    }

    @FunctionalInterface
    public interface AfterMouseRelease {
        boolean afterMouseRelease(Screen screen, MouseButtonEvent event, boolean consumed);
    }

    @FunctionalInterface
    public interface AllowMouseDrag {
        boolean allowMouseDrag(Screen screen, MouseButtonEvent event, double horizontalAmount, double verticalAmount);
    }

    @FunctionalInterface
    public interface BeforeMouseDrag {
        void beforeMouseDrag(Screen screen, MouseButtonEvent event, double horizontalAmount, double verticalAmount);
    }

    @FunctionalInterface
    public interface AfterMouseDrag {
        boolean afterMouseDrag(Screen screen, MouseButtonEvent event, double horizontalAmount, double verticalAmount,
                boolean consumed);
    }

    @FunctionalInterface
    public interface AllowMouseScroll {
        boolean allowMouseScroll(Screen screen, double mouseX, double mouseY, double horizontalAmount,
                double verticalAmount);
    }

    @FunctionalInterface
    public interface BeforeMouseScroll {
        void beforeMouseScroll(Screen screen, double mouseX, double mouseY, double horizontalAmount,
                double verticalAmount);
    }

    @FunctionalInterface
    public interface AfterMouseScroll {
        boolean afterMouseScroll(Screen screen, double mouseX, double mouseY, double horizontalAmount,
                double verticalAmount, boolean consumed);
    }
}
