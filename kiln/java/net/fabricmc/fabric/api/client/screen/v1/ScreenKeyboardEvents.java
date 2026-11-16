package net.fabricmc.fabric.api.client.screen.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * Kiln's stand-in for Fabric's {@code ScreenKeyboardEvents}: a screen's keys and typed characters, each screen its own
 * (D-156), fired as {@link ScreenMouseEvents} are: for a mod's own screen and the game's container screens. On a
 * game's screen the game leaves the keys alone only while a mod's text field has the focus.
 */
public final class ScreenKeyboardEvents {
    private ScreenKeyboardEvents() {}

    public static Event<AllowKeyPress> allowKeyPress(Screen screen) {
        return ScreenEvents.own(screen, AllowKeyPress.class,
                ls -> (s, e) -> ScreenMouseEvents.allAllow(ls, l -> l.allowKeyPress(s, e)));
    }

    public static Event<BeforeKeyPress> beforeKeyPress(Screen screen) {
        return ScreenEvents.own(screen, BeforeKeyPress.class,
                ls -> (s, e) -> ScreenMouseEvents.each(ls, l -> l.beforeKeyPress(s, e)));
    }

    public static Event<AfterKeyPress> afterKeyPress(Screen screen) {
        return ScreenEvents.own(screen, AfterKeyPress.class,
                ls -> (s, e) -> ScreenMouseEvents.each(ls, l -> l.afterKeyPress(s, e)));
    }

    public static Event<AllowKeyRelease> allowKeyRelease(Screen screen) {
        return ScreenEvents.own(screen, AllowKeyRelease.class,
                ls -> (s, e) -> ScreenMouseEvents.allAllow(ls, l -> l.allowKeyRelease(s, e)));
    }

    public static Event<BeforeKeyRelease> beforeKeyRelease(Screen screen) {
        return ScreenEvents.own(screen, BeforeKeyRelease.class,
                ls -> (s, e) -> ScreenMouseEvents.each(ls, l -> l.beforeKeyRelease(s, e)));
    }

    public static Event<AfterKeyRelease> afterKeyRelease(Screen screen) {
        return ScreenEvents.own(screen, AfterKeyRelease.class,
                ls -> (s, e) -> ScreenMouseEvents.each(ls, l -> l.afterKeyRelease(s, e)));
    }

    public static Event<AllowCharType> allowCharType(Screen screen) {
        return ScreenEvents.own(screen, AllowCharType.class,
                ls -> (s, e) -> ScreenMouseEvents.allAllow(ls, l -> l.allowCharType(s, e)));
    }

    public static Event<BeforeCharType> beforeCharType(Screen screen) {
        return ScreenEvents.own(screen, BeforeCharType.class,
                ls -> (s, e) -> ScreenMouseEvents.each(ls, l -> l.beforeCharType(s, e)));
    }

    public static Event<AfterCharType> afterCharType(Screen screen) {
        return ScreenEvents.own(screen, AfterCharType.class,
                ls -> (s, e) -> ScreenMouseEvents.each(ls, l -> l.afterCharType(s, e)));
    }

    @FunctionalInterface
    public interface AllowKeyPress {
        boolean allowKeyPress(Screen screen, KeyEvent event);
    }

    @FunctionalInterface
    public interface BeforeKeyPress {
        void beforeKeyPress(Screen screen, KeyEvent event);
    }

    @FunctionalInterface
    public interface AfterKeyPress {
        void afterKeyPress(Screen screen, KeyEvent event);
    }

    @FunctionalInterface
    public interface AllowKeyRelease {
        boolean allowKeyRelease(Screen screen, KeyEvent event);
    }

    @FunctionalInterface
    public interface BeforeKeyRelease {
        void beforeKeyRelease(Screen screen, KeyEvent event);
    }

    @FunctionalInterface
    public interface AfterKeyRelease {
        void afterKeyRelease(Screen screen, KeyEvent event);
    }

    @FunctionalInterface
    public interface AllowCharType {
        boolean allowCharType(Screen screen, CharacterEvent event);
    }

    @FunctionalInterface
    public interface BeforeCharType {
        void beforeCharType(Screen screen, CharacterEvent event);
    }

    @FunctionalInterface
    public interface AfterCharType {
        void afterCharType(Screen screen, CharacterEvent event);
    }
}
