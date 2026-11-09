package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The text for translation keys, what {@code Component.getString} reads: the game's own, in the language it shows
 * (from its prepared resources, the {@code kiln.resources} folder: {@code assets/minecraft/lang/<language>.json},
 * English where that lacks a key), then the Java mods' own English text from their jars
 * ({@code assets/<namespace>/lang/en_us.json}).
 */
public final class Language {
    private static final Pattern FILE = Pattern.compile("assets/[^/]+/lang/en_us\\.json");
    private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
    private static Map<String, String> strings;
    /** The keys the game's own text has, in any language loaded: Horizon shows those itself. */
    private static java.util.Set<String> gameKeys = java.util.Set.of();

    private Language() {}

    private static String loadedFor;

    /** The text for {@code key}, or the key when neither the game nor a mod has it. */
    public static synchronized String get(String key) {
        return strings().getOrDefault(key, key);
    }

    /** Whether the game or a mod has text for {@code key}. */
    public static synchronized boolean has(String key) {
        return strings().containsKey(key);
    }

    /**
     * Whether {@code key} is only a mod's: its language file has it and the game's doesn't. Horizon has only the
     * game's text, so such a line goes to it as its text ({@code Component}'s JSON), not as the key.
     */
    public static synchronized boolean modOnly(String key) {
        return strings().containsKey(key) && !gameKeys.contains(key);
    }

    /** The strings, loaded again when the game's language changed. */
    private static Map<String, String> strings() {
        String language = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        if (strings == null || !language.equals(loadedFor)) {
            loadedFor = language;
            strings = load();
            Map<String, String> game = game("en_us");
            if (!language.equals("en_us")) {
                game.putAll(game(language));
            }
            strings.putAll(game);
            gameKeys = java.util.Set.copyOf(game.keySet());
        }
        return strings;
    }

    /** The game's own strings in {@code language}, from its prepared resources; none without them. */
    private static Map<String, String> game(String language) {
        Map<String, String> found = new HashMap<>();
        String resources = System.getProperty("kiln.resources", "");
        if (resources.isEmpty() || !language.matches("[a-z0-9_]+")) {
            return found;
        }
        java.nio.file.Path file = java.nio.file.Path.of(resources, "assets", "minecraft", "lang", language + ".json");
        try (Reader in = java.nio.file.Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(in).getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonPrimitive()) {
                    found.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Kiln: the game's " + language + " text: " + e);
        }
        return found;
    }

    /** {@code key}'s text with its {@code %s} and {@code %1$s} filled in, as vanilla's TranslatableContents. */
    public static String format(String key, Object[] args) {
        String template = get(key);
        Matcher m = ARGUMENT.matcher(template);
        StringBuilder b = new StringBuilder();
        int next = 0;
        while (m.find()) {
            String replacement;
            if (m.group(2).equals("%")) {
                replacement = "%";
            } else {
                int i = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
                Object arg = i >= 0 && i < args.length ? args[i] : null;
                replacement = arg instanceof net.minecraft.network.chat.Component c ? c.getString() : String.valueOf(arg);
            }
            m.appendReplacement(b, Matcher.quoteReplacement(replacement));
        }
        return m.appendTail(b).toString();
    }

    private static Map<String, String> load() {
        Map<String, String> found = new HashMap<>();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!entry.endsWith(".jar")) {
                continue;
            }
            try (ZipFile jar = new ZipFile(entry)) {
                for (ZipEntry file : jar.stream().filter(e -> FILE.matcher(e.getName()).matches()).toList()) {
                    try (Reader in = new InputStreamReader(jar.getInputStream(file), StandardCharsets.UTF_8)) {
                        for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(in).getAsJsonObject().entrySet()) {
                            if (e.getValue().isJsonPrimitive()) {
                                found.putIfAbsent(e.getKey(), e.getValue().getAsString());
                            }
                        }
                    } catch (RuntimeException e) {
                        System.err.println("Kiln: " + entry + " " + file.getName() + ": " + e);
                    }
                }
            } catch (IOException e) {
                System.err.println("Kiln: " + entry + ": " + e);
            }
        }
        return found;
    }
}
