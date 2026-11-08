package kiln.proof.mixins;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The game's moments as the proof mod's injections and Fabric's events tell them, in the order they came: the client's
 * tick ({@code Minecraft.tick}, {@code Player.tick}), joining a world ({@code setLevel}), a screen opening and closing
 * ({@code Gui.setScreen}), leaving the world ({@code disconnect}, {@code clearClientLevel}). After the tick out of the
 * world that follows leaving it, the mod checks that each came where the game has it, and says so.
 */
public final class Moments {
    private static final List<String> SEEN = new ArrayList<>();
    /** The times MinecraftMomentsMixin's handler that throws was called. */
    public static int thrown;
    /** Whether another class could call LocalPlayer's added getInventory (D-101). */
    private static boolean inventoryFromOutside;
    /** The HUD's tick count after its last tick, as HudMixin's shadow of the game's field saw it. */
    public static int hudTicks;
    private static boolean left;
    private static boolean checked;

    private Moments() {}

    /** A moment: an injection's, or a Fabric event's. */
    public static void saw(String what) {
        if (!checked && SEEN.size() < 10_000) {
            SEEN.add(what);
        }
    }

    /** What the client has: its connection, level and player. */
    public static String has() {
        Minecraft mc = Minecraft.getInstance();
        List<String> has = new ArrayList<>();
        if (mc.getConnection() != null) {
            has.add("connection");
        }
        if (mc.level != null) {
            has.add("level");
        }
        if (mc.player != null) {
            has.add("player");
        }
        return has.toString();
    }

    public static String name(Screen screen) {
        return screen == null ? "none" : screen.getClass().getSimpleName();
    }

    /** The world was left ({@code disconnect}'s RETURN): the checks come at the end of the next client tick. */
    public static void left() {
        left = true;
    }

    /** {@code Minecraft.tick}'s TAIL. */
    public static void tickEnded() {
        if (left && !checked) {
            check();
            checked = true;
        }
    }

    static void listen() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(
                mc -> saw("CLIENT_STARTED"));
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("kiln_proof_mixins", "moments"),
                (graphics, delta) -> saw("HUD element"));
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("kiln_proof_mixins", "after_hotbar"),
                (graphics, delta) -> saw("after hotbar"));
        ClientTickEvents.START_CLIENT_TICK.register(mc -> saw("START_CLIENT_TICK"));
        ClientTickEvents.START_LEVEL_TICK.register(level -> saw("START_LEVEL_TICK"));
        ClientTickEvents.END_LEVEL_TICK.register(level -> saw("END_LEVEL_TICK"));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> saw("END_CLIENT_TICK"));
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((mc, level) -> saw("AFTER_CLIENT_LEVEL_CHANGE " + has()));
        ClientPlayConnectionEvents.JOIN.register((listener, sender, mc) -> {
            saw("JOIN " + has());
            // A method Kiln added to LocalPlayer (calling Player's, for LocalPlayerMixin), called from another class:
            // as visible as Player's (D-101).
            try {
                inventoryFromOutside = mc.player.getInventory() != null;
            } catch (IllegalAccessError e) {
                inventoryFromOutside = false;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, mc) -> saw("DISCONNECT " + has()));
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            saw("AFTER_INIT " + name(screen));
            ScreenEvents.remove(screen).register(s -> saw("REMOVE " + name(s)));
        });
    }

    /**
     * A tick in a world (D-080, D-093): the HUD's tick and the keys handled, then the level's, the player ticking down
     * through its classes; the HUD drawn after it, its layers in the game's order with the mods' elements among them
     * (D-100).
     */
    private static final List<String> TICK = List.of("tick HEAD", "START_CLIENT_TICK", "Hud.tick HEAD",
            "handleKeybinds HEAD", "START_LEVEL_TICK", "LocalPlayer.tick HEAD", "Player.tick HEAD",
            "LivingEntity.tick HEAD", "Entity.tick HEAD", "Player.tick TAIL", "END_LEVEL_TICK", "END_CLIENT_TICK",
            "tick TAIL", "Hud HEAD", "Hotbar HEAD", "after hotbar", "Armor HEAD", "HUD element", "Hud TAIL");
    private static final List<String> PAUSED_TICK = List.of("tick HEAD", "START_CLIENT_TICK", "START_LEVEL_TICK",
            "END_LEVEL_TICK", "END_CLIENT_TICK", "tick TAIL", "Hud HEAD", "Hotbar HEAD", "after hotbar", "Armor HEAD", "HUD element", "Hud TAIL");
    private static final List<String> TICK_OUT_OF_A_WORLD = List.of("tick HEAD", "START_CLIENT_TICK", "END_CLIENT_TICK",
            "tick TAIL");

    /** Each moment one after another where the game has it, each after the one before. */
    private static void check() {
        int at = 0;
        at = check("the client's main loop started (Minecraft.run) before Fabric's CLIENT_STARTED, then joining a "
                + "single-player world: its server's loadLevel, setLevel's HEAD with no level or player yet, Fabric's "
                + "level change in it, JOIN after it with the player, then the tick", at,
                List.of("run HEAD", "CLIENT_STARTED", "loadLevel HEAD"), join(), TICK);
        at = check("the client's tick: Minecraft.tick around Fabric's tick events, Player.tick in the level's", at,
                TICK);
        at = check("a screen opening: Gui.setScreen around Fabric's AFTER_INIT, and the player stands still while the "
                + "game is paused", at, List.of("setScreen HEAD PauseScreen over none", "AFTER_INIT PauseScreen",
                "setScreen RETURN PauseScreen"), PAUSED_TICK);
        at = check("a screen closing: Gui.setScreen around Fabric's REMOVE, and the player ticks again", at,
                List.of("setScreen HEAD none over PauseScreen", "REMOVE PauseScreen", "setScreen RETURN none"), TICK);
        check("leaving the world: both disconnects, clearClientLevel in them with the world still there, Fabric's "
                + "DISCONNECT after, nothing left at the RETURN, then a tick out of the world", at, List.of(
                "disconnect HEAD TitleScreen [connection, level, player]",
                "disconnect(ZZ) HEAD [connection, level, player]",
                "clearClientLevel HEAD [connection, level, player]", "DISCONNECT []", "disconnect(ZZ) RETURN []"),
                TICK_OUT_OF_A_WORLD);
        long ticks = SEEN.stream().filter("tick HEAD"::equals).count();
        ProofMixins.check("a handler that throws, called each tick, the tick going on", thrown > 0 && thrown == ticks,
                true);
        long hud = SEEN.stream().filter("Hud.tick HEAD"::equals).count();
        ProofMixins.check("the HUD's tick count, a field of the game's a mixin shadows", hudTicks, (int) hud);
        ProofMixins.check("a method Kiln adds to a stand-in, calling the one it inherits (LocalPlayer.getInventory)",
                ProofMixins.inventoryAsked > 0, true);
        ProofMixins.check("that method called from another class, as visible as the one it overrides",
                inventoryFromOutside, true);
        ProofMixins.report(SEEN);
    }

    private static List<String> join() {
        return List.of("setLevel HEAD [connection] to a level", "AFTER_CLIENT_LEVEL_CHANGE [connection, level]",
                "setLevel RETURN [connection, level]", "JOIN [connection, level, player]");
    }

    /** Whether the parts came one after another, from {@code from} on in what was seen; where they ended. */
    @SafeVarargs
    private static int check(String what, int from, List<String>... parts) {
        List<String> expected = new ArrayList<>();
        for (List<String> part : parts) {
            expected.addAll(part);
        }
        int found = -1;
        for (int i = from; i + expected.size() <= SEEN.size() && found < 0; i++) {
            if (SEEN.subList(i, i + expected.size()).equals(expected)) {
                found = i + expected.size();
            }
        }
        ProofMixins.check(what, found >= 0, true);
        return found >= 0 ? found : from;
    }
}
