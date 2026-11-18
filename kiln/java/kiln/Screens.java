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
 *
 * <p>A mod's own screen, set by its {@code Gui.setScreen}, Kiln shows over the game's (D-094): it tells the game to
 * open it ({@code screen open}), hands it the mouse, keys and typing the game sends ({@code sin}), ticks it, and after
 * each tick sends what it draws ({@code sdraw}). Setting {@code null}, or the game's screen it was opened over (the
 * parent a mod's screen keeps), closes it ({@code screen close}); the game closing it says so ({@code sclosed}).
 */
public final class Screens {
    private static String name = "playing";
    private static Screen open;
    /** The mod's screen Kiln shows over the game's, if one is open. */
    private static Screen mod;
    /** Where the pointer is over it, in GUI pixels, and the buttons held. */
    private static double mouseX;
    private static double mouseY;
    private static int buttonsHeld;
    /** What it drew last, as sent. */
    private static java.util.List<String> drawn = java.util.List.of();
    /** Whether Kiln is setting the game's screen ({@link #show}): {@code Gui.setScreen} is the game's then. */
    private static boolean setting;
    /** The GUI's size the screen being set is made for. */
    private static int width;
    private static int height;

    private Screens() {}

    /** The screen on top: a mod's Kiln shows, else the game's; none while playing. */
    public static Screen open() {
        return mod != null ? mod : open;
    }

    /** The GUI's size, with each tick: a mod's screen is made for it. */
    static void size(int width, int height) {
        Screens.width = width;
        Screens.height = height;
        if (mod != null && (mod.width != width || mod.height != height)) {
            mod.resize(width, height);
        }
    }

    /** The mod's screen Kiln shows, if any. */
    static Screen mod() {
        return mod;
    }

    /** The tick's screen, by the game's name ({@code playing} for none), {@code width} by {@code height} GUI pixels. */
    static void tick(Minecraft mc, String screen, int width, int height) {
        if (screen.isEmpty()) {
            screen = "playing";
        }
        // The game names its screen for a mod's "mod": that is the one Kiln shows, over the game's screen as it was.
        if (screen.equals("mod")) {
            return;
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

    /** Out of the world: the screen Kiln knew goes (a mod's shown over the title screen's stays). */
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
            setMod(screen);
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

    /**
     * A mod's {@code Gui.setScreen}: its screen shown over the game's, in place of the one shown before; {@code null}, the
     * game's own screen or a stand-in of one closes the mod's.
     */
    private static void setMod(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        Screen old = mod;
        if (screen == old) {
            return;
        }
        boolean games = screen == null || screen == open || screen instanceof GameScreen || screen instanceof TitleScreen
                || screen instanceof PauseScreen || screen instanceof ChatScreen || screen instanceof DeathScreen;
        if (games && old == null) {
            if (screen != null && screen != open) {
                Native.log("Kiln doesn't open the game's screens for mods: " + screen + " stays closed");
            }
            return;
        }
        mod = games ? null : screen;
        drawn = java.util.List.of();
        if (old != null) {
            old.removed();
            ScreenEvents.remove(old).invoker().onRemove(old);
        }
        if (mod != null) {
            ScreenEvents.BEFORE_INIT.invoker().beforeInit(mc, mod, width, height);
            mod.added();
            mod.init(width, height);
            ScreenEvents.AFTER_INIT.invoker().afterInit(mc, mod, width, height);
            if (old == null) {
                Host.send("screen", "open", mod.isPauseScreen() ? "1" : "0", mod.getClass().getName());
            }
        } else {
            Host.send("screen", "close");
        }
    }

    /** The game closed the mod's screen (another screen came, the world went): it goes without its {@code onClose}. */
    static void closed() {
        Screen old = mod;
        if (old != null) {
            mod = null;
            drawn = java.util.List.of();
            old.removed();
            ScreenEvents.remove(old).invoker().onRemove(old);
        }
    }

    /**
     * The game's input over the mod's screen ({@code sin <kind> ...}): {@code move x y}, {@code press x y button
     * modifiers}, {@code release x y button modifiers}, {@code drag x y button dx dy modifiers}, {@code scroll x y dx
     * dy}, {@code key <name> modifiers}, {@code keyup <name> modifiers} and {@code char <code point> modifiers}.
     * Buttons come as the game numbers them (0 left, 1 right, 2 middle) and modifiers as GLFW's bits (shift 1,
     * control 2, alt 4, super 8); the screen gets them as 26.3 does.
     */
    static void input(String[] f) {
        // The game's container screen (D-156): where it drew it, and the input over it for the mods.
        if (f[1].equals("game")) {
            if (ContainerScreens.geometry(f, open) && mod == null) {
                show(Minecraft.getInstance(), make(Minecraft.getInstance(), name), width, height);
            }
            return;
        }
        // The system's clipboard, read by the game with a paste key that follows (D-174).
        if (f[1].equals("clip")) {
            net.minecraft.client.KeyboardHandler.kiln$clipboard(f.length > 2 ? f[2] : "");
            return;
        }
        if (f[1].equals("over")) {
            if (mod == null) {
                try {
                    ContainerScreens.input(open, f);
                } catch (RuntimeException | LinkageError e) {
                    Native.log("a mod's handling of input over the game's screen threw " + e);
                    e.printStackTrace();
                }
            }
            return;
        }
        Screen screen = mod;
        if (screen == null) {
            return;
        }
        // The pointer and the buttons held, then the input as vanilla gives it, with Fabric's screen events (D-163).
        switch (f[1]) {
            case "move" -> {
                mouseX = Double.parseDouble(f[2]);
                mouseY = Double.parseDouble(f[3]);
                screen.mouseMoved(mouseX, mouseY);
                screen.afterMouseMove();
                return;
            }
            case "press", "release", "drag", "scroll" -> {
                mouseX = Double.parseDouble(f[2]);
                mouseY = Double.parseDouble(f[3]);
                if (!f[1].equals("scroll") && !f[1].equals("drag")) {
                    int bit = 1 << button(Integer.parseInt(f[4]));
                    buttonsHeld = f[1].equals("press") ? buttonsHeld | bit : buttonsHeld & ~bit;
                }
            }
            default -> {}
        }
        try {
            ContainerScreens.dispatch(screen, f, 1, false);
        } catch (RuntimeException | LinkageError e) {
            Native.log("a mod's screen's handling of input threw " + e);
            e.printStackTrace();
        }
    }

    /** The game's mouse button (0 left, 1 right, 2 middle) as 26.3 numbers it (1 left, 2 middle, 3 right). */
    static int button(int game) {
        return switch (game) {
            case 0 -> 1;
            case 1 -> 3;
            case 2 -> 2;
            default -> game + 1;
        };
    }

    /**
     * The modifier keys held with the input being handed to a screen now, as GLFW's bits (shift 1, control 2, alt 4,
     * super 8), or -1 between inputs: the game's {@code hasShiftDown} and the like answer the input's own keys then, as
     * vanilla's read the keyboard while it handles the key (D-174).
     */
    private static int inputModifiers = -1;

    /** Kiln's own: the modifier keys of the input being handled now (GLFW's bits), or -1 between inputs. */
    public static int kiln$inputModifiers() {
        return inputModifiers;
    }

    static void inputModifiers(int glfw) {
        inputModifiers = glfw;
    }

    /** Kiln's own: a mod copied text ({@code KeyboardHandler.setClipboard}): onto the system's clipboard (D-174). */
    public static void copied(String text) {
        Host.send("clip", text);
    }

    /** GLFW's modifier bits (shift 1, control 2, alt 4, super 8) as 26.3's (SDL's left keys: 0x1, 0x40, 0x100, 0x400). */
    static int modifiers(int glfw) {
        return ((glfw & 1) != 0 ? 0x1 : 0) | ((glfw & 2) != 0 ? 0x40 : 0) | ((glfw & 4) != 0 ? 0x100 : 0)
                | ((glfw & 8) != 0 ? 0x400 : 0);
    }

    /**
     * After the client's tick: the mod's screen ticks, then draws; what it drew goes to the game when it changed. With
     * none, the game's container screen open draws the mods' drawing over it (D-156).
     */
    static void tickMod() {
        Screen screen = mod;
        ContainerScreens.draw(screen == null ? open : null, width, height);
        if (screen == null) {
            return;
        }
        try {
            ScreenEvents.beforeTick(screen).invoker().beforeTick(screen);
            screen.tick();
            ScreenEvents.afterTick(screen).invoker().afterTick(screen);
        } catch (RuntimeException | LinkageError e) {
            Native.log("a mod's screen's tick threw " + e);
        }
        if (mod != screen) {
            return;
        }
        net.minecraft.client.gui.GuiGraphicsExtractor graphics = new net.minecraft.client.gui.GuiGraphicsExtractor(
                width, height);
        try {
            Minecraft.getInstance().gui.extractRenderState(graphics, screen, (int) mouseX, (int) mouseY, 0.0F);
        } catch (RuntimeException | LinkageError e) {
            Native.log("a mod's screen's drawing threw " + e);
            e.printStackTrace();
        }
        java.util.List<String> ops = new java.util.ArrayList<>(graphics.kiln$ops());
        if (!ops.equals(drawn)) {
            drawn = ops;
            Host.send("sdraw", ops.toArray(String[]::new));
        }
    }

    private static Screen make(Minecraft mc, String name) {
        return switch (name) {
            case "playing" -> null;
            case "title" -> new TitleScreen();
            case "paused" -> new PauseScreen(true);
            case "chat" -> new ChatScreen("", false);
            case "dead" -> new DeathScreen(Component.empty(), false, mc.player);
            default -> ContainerScreens.NAMES.contains(name) ? ContainerScreens.make(mc, name) : new GameScreen(name);
        };
    }
}
