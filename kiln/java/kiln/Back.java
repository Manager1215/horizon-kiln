package kiln;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * What the mods change of the game's state Horizon owns, back to Horizon (D-105). Kiln's stand-ins hold the game's
 * values as the window's game said them with each tick; a mod's change (an option set, the player's flying speed) was
 * Java's alone, and the next tick put Horizon's back. Now a value Horizon owns is a {@link Value}: Horizon's until a
 * mod sets it, and a set one goes back with the tick's replies ({@code back <key> <value>...}), for Kiln's Rust to set
 * in Horizon by its key. The same channel carries the results of the bridge's {@code tick} rows (kiln/bridge.json):
 * the game's methods Kiln calls once a client tick for what the mods' mixins make them return (the field of view a
 * zoom mod makes {@code Camera.calculateFov} give, the speed {@code Player.getFlyingSpeed} gives).
 *
 * <p>The keys Kiln's Rust knows: {@code fov} (degrees, the option), {@code render_distance} (chunks), {@code
 * show_subtitles} (1 or 0), {@code gui_scale} (0 auto), {@code gamma} (the brightness, D-192), {@code camera} (0 first person, 1 third person behind, 2 in front), {@code
 * volume_<source>} (a volume slider, 0 to 1, by {@code SoundSource}'s name: {@code volume_master}, D-232), {@code
 * flying_speed} (the player's abilities'), {@code view_gamma} (the brightness the renderer takes over the option's,
 * {@code -} for the option's), {@code view_fov} (the degrees the view
 * takes now: Horizon scales its own by it over the option's), {@code hud_hidden} (vanilla's HUD layers Horizon hides,
 * comma-separated, {@code -} for none: kiln.Bridge's D-145).
 */
public final class Back {
    /** What the mods did once since the last tick's replies, in order: key, value, key, value... ({@link #act}). */
    private static final List<String> ACTS = new ArrayList<>();

    /** Last sent, by key: a value goes again only when it changed. */
    private static final Map<String, String> SENT = new LinkedHashMap<>();

    /** The client ticks a mod's change waits for Horizon to take it before the mod reads Horizon's again: a second. */
    static final int PATIENCE = 20;

    private Back() {}

    /** Whether the game's tick has taken every `back` message Java sent (the state's {@code backs_taken}). */
    private static boolean advanced = true;
    /** How many `back` messages Java has sent. */
    private static long sent;

    /**
     * The state's count of the `back` messages the game's tick has taken. Horizon's patience (below) counts Java's
     * ticks only while it has taken all that Java sent: Java answers late while it warms up, and frames make its
     * ticks while the game's tick stands, so a change must not give way to Horizon's value before Horizon has seen
     * it (D-192).
     */
    static void taken(String count) {
        try {
            advanced = Long.parseLong(count) >= sent;
        } catch (NumberFormatException e) {
            advanced = true;
        }
    }

    /** One of the game's values Horizon owns: Horizon's, until a mod sets another. */
    public static final class Value<T> {
        private final String key;
        private T game;
        private T value;
        /** Ticks Horizon has kept its own value since a mod set another. */
        private int waited;

        public Value(String key, T value) {
            this.key = key;
            this.game = value;
            this.value = value;
        }

        public String key() {
            return key;
        }

        public T get() {
            return value;
        }

        /** A mod's change: it stays, and goes back to Horizon. */
        public void set(T value) {
            this.value = value;
            waited = 0;
        }

        /**
         * Horizon's value, with a tick. When Horizon's changed (the player's own change in its options, or a mod's
         * that reached it), it is the value; when it didn't, a mod's change waits for Horizon to take it, for
         * {@link #PATIENCE} ticks at most: a change Horizon never takes (one it refuses) gives way to Horizon's.
         */
        public void game(T value) {
            if (!Objects.equals(value, game)) {
                game = value;
                this.value = value;
                waited = 0;
            } else if (!Objects.equals(this.value, game) && advanced && ++waited > PATIENCE) {
                this.value = game;
                waited = 0;
            }
        }

        /** Whether a mod changed it from Horizon's. */
        public boolean changed() {
            return !Objects.equals(value, game);
        }
    }

    /** At the end of a client tick: the values the mods changed, and the bridge's tick rows' results, to Horizon. */
    static void send(Minecraft mc) {
        Map<String, String> now = new LinkedHashMap<>();
        List<Value<?>> values = new ArrayList<>(List.of(mc.options.fov().kiln$back(),
                mc.options.renderDistance().kiln$back(), mc.options.showSubtitles().kiln$back(),
                mc.options.guiScale().kiln$back(), mc.options.gamma().kiln$back(), mc.options.kiln$camera()));
        values.addAll(mc.options.kiln$volumesBack());
        LocalPlayer player = mc.player;
        if (player != null) {
            values.add(player.getAbilities().kiln$flyingSpeed());
        }
        for (Value<?> v : values) {
            // A change back to Horizon's value still goes when another one went before it that Horizon may not have
            // taken yet (it says its old value for a tick or two): else Horizon would end on the earlier one.
            String text = text(v.get());
            String sent = SENT.get(v.key());
            if (v.changed() || (sent != null && !sent.equals(text))) {
                now.put(v.key(), text);
            }
        }
        if (player != null) {
            now.putAll(Bridge.ticked());
            // The brightness a mod's mixin makes the option's get return (Resource Gamma Util: its toggle returns 16
            // without setting the option), for Horizon's renderer over the player's own, which stays as the player
            // set it (D-192): as view_fov is the view's field of view over the option's. "-" when none.
            Object shown = mc.options.gamma().get();
            Object own = mc.options.gamma().kiln$back().get();
            boolean viewed = shown instanceof Number n && !java.util.Objects.equals(shown, own)
                    && Double.isFinite(n.doubleValue());
            if (viewed || SENT.containsKey("view_gamma")) {
                now.put("view_gamma", viewed ? text(shown) : "-");
            }
        }
        // Vanilla's HUD layers the mods' mixins or elements kept from drawing, for Horizon to hide (D-145).
        String hidden = net.minecraft.client.gui.Hud.kiln$hidden();
        if (hidden != null) {
            now.put("hud_hidden", hidden);
        }
        List<String> fields = new ArrayList<>();
        now.forEach((key, value) -> {
            if (!value.equals(SENT.get(key))) {
                fields.add(key);
                fields.add(value);
            }
        });
        SENT.keySet().retainAll(now.keySet());
        SENT.putAll(now);
        synchronized (ACTS) {
            fields.addAll(ACTS);
            ACTS.clear();
        }
        if (!fields.isEmpty()) {
            sent++;
            Host.send("back", fields.toArray(String[]::new));
        }
    }

    /**
     * Something a mod did once that Horizon does for the game (a debug entry's status set, the sprint started, an
     * effect removed, a title shown, sounds stopped, the item in hand used): it goes with this tick's replies, in
     * order, for Kiln's Rust to do by its key ({@code debug}, {@code sprinting}, {@code remove_effect}, {@code title},
     * {@code subtitle}, {@code title_times}, {@code clear_titles}, {@code reset_title_times}, {@code stop_sound},
     * {@code use_item}, {@code selected}: the hotbar slot in hand, 0 to 8, D-211; {@code look}: the window's player's
     * yaw and pitch in degrees; {@code leave_world}: the game leaves its world, as its pause menu's quit).
     */
    public static void act(String key, String value) {
        synchronized (ACTS) {
            ACTS.add(key);
            ACTS.add(value);
        }
    }

    private static String text(Object value) {
        return value instanceof Boolean b ? (b ? "1" : "0") : String.valueOf(value);
    }
}
