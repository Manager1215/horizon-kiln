package kiln;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;

/**
 * The mods on Java's class path, as Fabric Loader lists them: one for each fabric.mod.json, each id once, and the
 * ones Fabric Loader lists of its own, which mods look up for their versions (D-098): the game ({@code minecraft}),
 * {@code java}, {@code fabricloader}, and the Fabric API Kiln stands in for.
 */
public final class Mods {
    private static List<ModContainer> all;

    private Mods() {}

    public static synchronized List<ModContainer> all() {
        if (all == null) {
            List<ModContainer> found = new ArrayList<>();
            found.add(container("minecraft", "26.3", "Minecraft", "Minecraft 26.3, as Horizon runs it"));
            found.add(container("java", Runtime.version().feature() + "." + Runtime.version().interim() + "."
                    + Runtime.version().update(), "Java", "The Java Kiln runs the mods in"));
            found.add(container("fabricloader", "0.20.1", "Fabric Loader", "Kiln stands in for Fabric Loader"));
            found.add(container("fabric-api", "0.165.0+26.3", "Fabric API", "Kiln stands in for the Fabric API"));
            try {
                Enumeration<URL> manifests = Mods.class.getClassLoader().getResources("fabric.mod.json");
                while (manifests.hasMoreElements()) {
                    try (Reader r = new InputStreamReader(manifests.nextElement().openStream(), StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                        String id = text(json, "id");
                        if (!id.isEmpty() && found.stream().noneMatch(m -> m.getMetadata().getId().equals(id))) {
                            found.add(container(id, text(json, "version"), text(json, "name"),
                                    text(json, "description")));
                        }
                    } catch (RuntimeException e) {
                        Host.send("log", "a fabric.mod.json Kiln can't read: " + e);
                    }
                }
            } catch (IOException e) {
                Host.send("log", "Kiln can't list the mods: " + e);
            }
            all = Collections.unmodifiableList(found);
        }
        return all;
    }

    private static String text(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
    }

    private static ModContainer container(String id, String version, String name, String description) {
        Version v = () -> version;
        ModMetadata metadata = new ModMetadata() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public Version getVersion() {
                return v;
            }

            @Override
            public String getName() {
                return name.isEmpty() ? id : name;
            }

            @Override
            public String getDescription() {
                return description;
            }
        };
        return () -> metadata;
    }
}
