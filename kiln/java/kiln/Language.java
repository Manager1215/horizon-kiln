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
 * The Java mods' own English text, from their jars' {@code assets/<namespace>/lang/en_us.json}: what {@code
 * Component.getString} reads. The game's own text isn't here (Horizon has it): its keys stay keys.
 */
public final class Language {
    private static final Pattern FILE = Pattern.compile("assets/[^/]+/lang/en_us\\.json");
    private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
    private static Map<String, String> strings;

    private Language() {}

    /** The text for {@code key}, or the key when no mod has it. */
    public static synchronized String get(String key) {
        if (strings == null) {
            strings = load();
        }
        return strings.getOrDefault(key, key);
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
