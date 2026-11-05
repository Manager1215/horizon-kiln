package net.fabricmc.fabric.api.client.screen.v1;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import kiln.Native;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * Kiln's stand-in for Fabric's {@code ScreenEvents}: a screen of the window's game opens (its init, before and after),
 * ticks while open and goes. Kiln tells them with the tick that finds the screen changed. The game draws its own
 * screens: the drawing events of one ({@code beforeExtract} and the rest) take listeners and don't fire.
 */
public final class ScreenEvents {
    public static final Event<BeforeInit> BEFORE_INIT = Event.kiln$of(listeners -> (client, screen, width, height) -> {
        for (BeforeInit l : listeners) {
            try {
                l.beforeInit(client, screen, width, height);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a screen's BEFORE_INIT listener threw " + e);
            }
        }
    });

    public static final Event<AfterInit> AFTER_INIT = Event.kiln$of(listeners -> (client, screen, width, height) -> {
        for (AfterInit l : listeners) {
            try {
                l.afterInit(client, screen, width, height);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a screen's AFTER_INIT listener threw " + e);
            }
        }
    });

    /** Each screen's own events, by kind. */
    private static final Map<Screen, Map<Class<?>, Event<?>>> OWN = new WeakHashMap<>();

    private ScreenEvents() {}

    public static Event<Remove> remove(Screen screen) {
        return own(screen, Remove.class, listeners -> s -> {
            for (Remove l : listeners) {
                try {
                    l.onRemove(s);
                } catch (RuntimeException | LinkageError e) {
                    Native.log("a screen's remove listener threw " + e);
                }
            }
        });
    }

    public static Event<BeforeTick> beforeTick(Screen screen) {
        return own(screen, BeforeTick.class, listeners -> s -> {
            for (BeforeTick l : listeners) {
                try {
                    l.beforeTick(s);
                } catch (RuntimeException | LinkageError e) {
                    Native.log("a screen's beforeTick listener threw " + e);
                }
            }
        });
    }

    public static Event<AfterTick> afterTick(Screen screen) {
        return own(screen, AfterTick.class, listeners -> s -> {
            for (AfterTick l : listeners) {
                try {
                    l.afterTick(s);
                } catch (RuntimeException | LinkageError e) {
                    Native.log("a screen's afterTick listener threw " + e);
                }
            }
        });
    }

    public static Event<BeforeExtract> beforeExtract(Screen screen) {
        return own(screen, BeforeExtract.class, listeners -> (s, g, x, y, delta) -> {});
    }

    public static Event<AfterBackground> afterBackground(Screen screen) {
        return own(screen, AfterBackground.class, listeners -> (s, g, x, y, delta) -> {});
    }

    public static Event<AfterForeground> afterForeground(Screen screen) {
        return own(screen, AfterForeground.class, listeners -> (s, g, x, y, delta) -> {});
    }

    public static Event<AfterExtract> afterExtract(Screen screen) {
        return own(screen, AfterExtract.class, listeners -> (s, g, x, y, delta) -> {});
    }

    @SuppressWarnings("unchecked")
    private static synchronized <T> Event<T> own(Screen screen, Class<T> kind,
            Function<java.util.List<T>, T> invoker) {
        return (Event<T>) OWN.computeIfAbsent(screen, s -> new java.util.HashMap<>())
                .computeIfAbsent(kind, k -> Event.kiln$of(invoker));
    }

    @FunctionalInterface
    public interface BeforeInit {
        void beforeInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight);
    }

    @FunctionalInterface
    public interface AfterInit {
        void afterInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight);
    }

    @FunctionalInterface
    public interface Remove {
        void onRemove(Screen screen);
    }

    @FunctionalInterface
    public interface BeforeTick {
        void beforeTick(Screen screen);
    }

    @FunctionalInterface
    public interface AfterTick {
        void afterTick(Screen screen);
    }

    @FunctionalInterface
    public interface BeforeExtract {
        void beforeExtract(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta);
    }

    @FunctionalInterface
    public interface AfterBackground {
        void afterBackground(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta);
    }

    @FunctionalInterface
    public interface AfterForeground {
        void afterForeground(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta);
    }

    @FunctionalInterface
    public interface AfterExtract {
        void afterExtract(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta);
    }
}
