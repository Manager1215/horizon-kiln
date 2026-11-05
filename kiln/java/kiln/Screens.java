package kiln;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * The screen open in the window's game, as the game names it with each tick in a world: Kiln stands it in (a stand-in
 * of vanilla's own where Kiln has one), and tells the mods it opened, ticks and went, as vanilla's {@code setScreen}.
 */
public final class Screens {
    private static String name = "playing";
    private static Screen open;

    private Screens() {}

    /** The screen on top: none while playing. */
    public static Screen open() {
        return open;
    }

    /** The tick's screen, by the game's name ({@code playing} for none), {@code width} by {@code height} GUI pixels. */
    static void tick(Minecraft mc, String screen, int width, int height) {
        if (screen.isEmpty()) {
            screen = "playing";
        }
        if (!screen.equals(name)) {
            Screen old = open;
            name = screen;
            open = make(mc, screen);
            if (old != null) {
                old.removed();
                ScreenEvents.remove(old).invoker().onRemove(old);
            }
            if (open != null) {
                ScreenEvents.BEFORE_INIT.invoker().beforeInit(mc, open, width, height);
                open.added();
                open.init(width, height);
                ScreenEvents.AFTER_INIT.invoker().afterInit(mc, open, width, height);
            }
        } else if (open != null) {
            ScreenEvents.beforeTick(open).invoker().beforeTick(open);
            open.tick();
            ScreenEvents.afterTick(open).invoker().afterTick(open);
        }
    }

    /** Out of the world: the screen Kiln knew goes. */
    static void leave() {
        if (open != null) {
            Screen old = open;
            open = null;
            old.removed();
            ScreenEvents.remove(old).invoker().onRemove(old);
        }
        name = "playing";
    }

    private static Screen make(Minecraft mc, String name) {
        return switch (name) {
            case "playing" -> null;
            case "title" -> new TitleScreen();
            case "paused" -> new PauseScreen(true);
            case "chat" -> new ChatScreen("", false);
            case "dead" -> new DeathScreen(Component.empty(), false, mc.player);
            default -> new GameScreen(name);
        };
    }
}
