package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The ids in the game's own registries, from its prepared resources (the {@code kiln.resources} folder's {@code
 * registries.json}, the game's registry report): what vanilla's registries hold before any mod adds to them. None
 * without the resources.
 */
public final class GameRegistries {
    private GameRegistries() {}

    /**
     * The game's own ids in {@code registry} ({@code minecraft:block}), in the report's order. With content mods on,
     * Horizon's prepared resources (its {@code mod-content} cache) list their entries too, a Java mod's blocks among
     * them: those are the mods' to register, so only the {@code minecraft} namespace's are vanilla's (D-103).
     */
    public static List<String> ids(String registry) {
        List<String> ids = new ArrayList<>();
        String resources = System.getProperty("kiln.resources", "");
        if (resources.isEmpty()) {
            return ids;
        }
        Path file = Path.of(resources, "registries.json");
        if (!Files.isRegularFile(file)) {
            return ids;
        }
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement found = JsonParser.parseReader(in).getAsJsonObject().get(registry);
            if (found != null && found.isJsonObject() && found.getAsJsonObject().get("entries") instanceof JsonObject entries) {
                entries.keySet().stream().filter(id -> id.startsWith("minecraft:")).forEach(ids::add);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Kiln: the game's " + registry + " registry: " + e);
        }
        return ids;
    }
}
