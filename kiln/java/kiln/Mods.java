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

/** The mods on Java's class path, as Fabric Loader lists them: one for each fabric.mod.json, each id once. */
public final class Mods {
    private static List<ModContainer> all;

    private Mods() {}

    public static synchronized List<ModContainer> all() {
        if (all == null) {
            List<ModContainer> found = new ArrayList<>();
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
