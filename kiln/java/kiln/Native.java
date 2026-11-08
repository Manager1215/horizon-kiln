package kiln;

/** What the game gives Java mods directly. */
public final class Native {
    private Native() {}

    /** A line in Kiln's log in the game, as {@code Kiln: [java] message}. */
    public static void log(String message) {
        Host.send("log", message);
    }

    /** A line in the player's chat, as it is (Kiln adds nothing to it). */
    public static void chat(String message) {
        Host.send("chat", message);
    }

    /**
     * A mod's line in the player's chat, with its style: the component as vanilla's JSON ({@code {"text":"...",
     * "color":"red","bold":true}}), which Horizon shows as the game would. Plain text would lose the colour, bold and
     * italic set on it ({@code withStyle}); legacy {@code §} codes in the text are text, and stay either way. A line
     * that is only text ({@code {"text":"..."}}) goes as text, as {@link #chat(String)}: it shows the same, and the
     * chat lines Kiln's tests read stay as they were.
     */
    public static void chat(net.minecraft.network.chat.Component message) {
        com.google.gson.JsonObject json = message.kiln$json();
        if (json.size() == 1 && json.has("text") && json.get("text").isJsonPrimitive()) {
            chat(json.get("text").getAsString());
        } else {
            Host.send("chat_json", json.toString());
        }
    }

    /** The mods' client commands, by name: the game runs a typed command with one of them through Java. */
    public static void commands(java.util.List<String> names) {
        Host.send("commands", names.toArray(String[]::new));
    }

    /**
     * Mods' keys, for their pages in the game's Options > Mods: the entrypoint class they came with (or empty), then
     * four fields a key: its name, its group's name, its label and the key the mod set.
     */
    public static void keys(java.util.List<String> fields) {
        Host.send("keys", fields.toArray(String[]::new));
    }

    /**
     * A command, without its slash, the game sends as the window's player with the next tick: to the mods' client
     * commands first, then the server (attended: as typed, signed).
     */
    public static void send(boolean attended, String command) {
        Host.send("send", attended ? "1" : "0", command);
    }

    /**
     * A sound by its event's id ({@code minecraft:ui.button.click}), which the game plays for the window's player with
     * the next tick: at {@code at} (x, y, z) in the level, or with none as the UI's (where the player is).
     */
    public static void sound(String id, double[] at, float volume, float pitch) {
        String where = at == null ? "" : at[0] + " " + at[1] + " " + at[2];
        Host.send("sound", id, where, Float.toString(volume), Float.toString(pitch));
    }

    /** A particle of one of the game's simple types ({@code minecraft:happy_villager}) at (x, y, z), moving. */
    public static void particle(String id, double x, double y, double z, double dx, double dy, double dz) {
        Host.send("particle", id, x + " " + y + " " + z, dx + " " + dy + " " + dz);
    }

    /**
     * One of the game's system toasts: its title over its message (none when empty), shown {@code millis}, of
     * {@code token} (what it is about). To {@code replace}, one of that token the game still shows takes them instead
     * (and its time again).
     */
    public static void toast(String title, String message, long millis, String token, boolean replace) {
        Host.send("toast", title, message, Long.toString(millis), token, replace ? "1" : "0");
    }
}
