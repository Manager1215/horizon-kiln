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
 * of vanilla's own where Kiln has one), and tells the mods it opened, ticks and went, as vanilla's {@code setScreen}:
 * Kiln sets it by the game's {@code Gui.setScreen}, so a mixin into that method runs as the screen changes (D-080).
 */
public final class Screens {
    private static String name = "playing";
    private static Screen open;
    /** Whether Kiln is setting the game's screen ({@link #show}): {@code Gui.setScreen} is the game's then. */
    private static boolean setting;
    /** The GUI's size the screen being set is made for. */
    private static int width;
    private static int height;

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
            name = screen;
            show(mc, make(mc, screen), width, height);
        } else if (open != null) {
            ScreenEvents.beforeTick(open).invoker().beforeTick(open);
            open.tick();
            ScreenEvents.afterTick(open).invoker().afterTick(open);
        }
    }

    /** Out of the world: the screen Kiln knew goes. */
    static void leave() {
        if (open != null) {
            show(Minecraft.getInstance(), null, width, height);
        }
        name = "playing";
    }

    /** The game's screen set by its {@code Gui.setScreen}; it is the one open after, whatever a mixin did. */
    private static void show(Minecraft mc, Screen screen, int width, int height) {
        Screens.width = width;
        Screens.height = height;
        setting = true;
        try {
            mc.gui.setScreen(screen);
        } finally {
            setting = false;
            open = screen;
        }
    }

    /**
     * {@code Gui.setScreen}: Kiln's for the game's screen ({@link #show}), the one before removed and the new one
     * made ready, with Fabric's events; a mod's isn't shown yet, said in Kiln's log.
     */
    public static void set(Screen screen) {
        if (!setting) {
            if (screen != null) {
                Native.log("Kiln can't show mods' screens yet: " + screen.getClass().getName() + " stays closed");
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Screen old = open;
        open = screen;
        if (old != null) {
            old.removed();
            ScreenEvents.remove(old).invoker().onRemove(old);
        }
        if (screen != null) {
            ScreenEvents.BEFORE_INIT.invoker().beforeInit(mc, screen, width, height);
            screen.added();
            screen.init(width, height);
            ScreenEvents.AFTER_INIT.invoker().afterInit(mc, screen, width, height);
        }
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
