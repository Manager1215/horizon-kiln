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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                    URL manifest = manifests.nextElement();
                    try (Reader r = new InputStreamReader(manifest.openStream(), StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                        String id = text(json, "id");
                        if (!id.isEmpty() && found.stream().noneMatch(m -> m.getMetadata().getId().equals(id))) {
                            found.add(container(id, text(json, "version"), text(json, "name"),
                                    text(json, "description"), json.get("icon"), manifest));
                            if (json.get("entrypoints") instanceof JsonObject e) {
                                ENTRYPOINTS.put(id, e);
                            }
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

    /** Each mod's {@code entrypoints} as its fabric.mod.json has them, by mod id, in the mods' order. */
    private static final Map<String, JsonObject> ENTRYPOINTS = new LinkedHashMap<>();

    /**
     * The entrypoints of {@code key} the mods' fabric.mod.json files name (JEI's {@code jei_mod_plugin}, D-152): each
     * the mod's id and the class (or {@code Class::member}) as written, a string or an object's {@code value}.
     */
    public static synchronized List<String[]> entrypoints(String key) {
        all();
        List<String[]> found = new ArrayList<>();
        ENTRYPOINTS.forEach((id, e) -> {
            if (e.get(key) instanceof com.google.gson.JsonArray list) {
                for (com.google.gson.JsonElement entry : list) {
                    String value = entry.isJsonPrimitive() ? entry.getAsString()
                            : entry.isJsonObject() ? text(entry.getAsJsonObject(), "value") : "";
                    if (!value.isEmpty()) {
                        found.add(new String[] {id, value});
                    }
                }
            }
        });
        return found;
    }

    private static String text(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
    }

    private static ModContainer container(String id, String version, String name, String description) {
        return container(id, version, name, description, null, null);
    }

    /**
     * A mod Fabric Loader lists: its fabric.mod.json's fields, its {@code icon} (a path, or paths by size) and where
     * that file is ({@code manifest}, its URL in the jar), for its files ({@code findPath}): a config screen's or
     * JEI's mod icons (D-179).
     */
    private static ModContainer container(String id, String version, String name, String description,
            com.google.gson.JsonElement icon, URL manifest) {
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

            @Override
            public java.util.Optional<String> getIconPath(int size) {
                return Mods.iconPath(icon, size);
            }
        };
        return new ModContainer() {
            @Override
            public ModMetadata getMetadata() {
                return metadata;
            }

            @Override
            public java.util.Optional<java.nio.file.Path> findPath(String file) {
                return manifest == null ? java.util.Optional.empty() : Mods.findPath(id, manifest, file);
            }
        };
    }

    /**
     * Fabric Loader's choice of a mod's icon for {@code size}: the one path, or of the paths by size the smallest at
     * least that large, else the largest.
     */
    static java.util.Optional<String> iconPath(com.google.gson.JsonElement icon, int size) {
        if (icon == null || icon.isJsonNull()) {
            return java.util.Optional.empty();
        }
        if (icon.isJsonPrimitive()) {
            return java.util.Optional.of(icon.getAsString());
        }
        if (!(icon instanceof JsonObject sizes)) {
            return java.util.Optional.empty();
        }
        String best = null, largest = null;
        int bestSize = Integer.MAX_VALUE, largestSize = Integer.MIN_VALUE;
        for (Map.Entry<String, com.google.gson.JsonElement> e : sizes.entrySet()) {
            int s;
            try {
                s = Integer.parseInt(e.getKey());
            } catch (NumberFormatException x) {
                continue;
            }
            if (!e.getValue().isJsonPrimitive()) {
                continue;
            }
            if (s >= size && s < bestSize) {
                bestSize = s;
                best = e.getValue().getAsString();
            }
            if (s > largestSize) {
                largestSize = s;
                largest = e.getValue().getAsString();
            }
        }
        return java.util.Optional.ofNullable(best != null ? best : largest);
    }

    /** The jars' file systems, by jar, opened once. */
    private static final Map<java.nio.file.Path, java.nio.file.FileSystem> JARS = new java.util.HashMap<>();
    private static java.nio.file.Path copies;

    /**
     * File {@code file} of the mod whose fabric.mod.json is {@code manifest}: in its folder, or in its jar (through
     * Java's zip file system, or a copy of the file where Java has none), as Fabric Loader's {@code findPath}.
     */
    static synchronized java.util.Optional<java.nio.file.Path> findPath(String id, URL manifest, String file) {
        String name = file.startsWith("/") ? file.substring(1) : file;
        if (name.isEmpty() || name.contains("..")) {
            return java.util.Optional.empty();
        }
        String where = manifest.toString();
        String base = where.substring(0, where.length() - "fabric.mod.json".length());
        try {
            if (base.startsWith("file:")) {
                java.nio.file.Path path = java.nio.file.Path.of(java.net.URI.create(base)).resolve(name);
                return java.nio.file.Files.exists(path) ? java.util.Optional.of(path) : java.util.Optional.empty();
            }
            if (base.startsWith("jar:file:") && base.endsWith("!/")) {
                java.nio.file.Path jar = java.nio.file.Path.of(java.net.URI.create(base.substring(4, base.length() - 2)));
                try {
                    java.nio.file.FileSystem fs = JARS.get(jar);
                    if (fs == null) {
                        fs = java.nio.file.FileSystems.newFileSystem(jar);
                        JARS.put(jar, fs);
                    }
                    java.nio.file.Path path = fs.getPath("/" + name);
                    return java.nio.file.Files.exists(path) ? java.util.Optional.of(path) : java.util.Optional.empty();
                } catch (java.nio.file.ProviderNotFoundException | UnsupportedOperationException e) {
                    // No zip file system in this Java: a copy of the file below.
                }
            }
            byte[] bytes;
            try (java.io.InputStream in = new URL(base + name).openStream()) {
                bytes = in.readAllBytes();
            } catch (IOException e) {
                return java.util.Optional.empty();
            }
            if (copies == null) {
                copies = java.nio.file.Files.createTempDirectory("kiln-mod-files");
                copies.toFile().deleteOnExit();
            }
            java.nio.file.Path copy = copies.resolve(id).resolve(name);
            java.nio.file.Files.createDirectories(copy.getParent());
            java.nio.file.Files.write(copy, bytes);
            copy.toFile().deleteOnExit();
            return java.util.Optional.of(copy);
        } catch (IOException | RuntimeException e) {
            Host.send("log", "Kiln can't find " + file + " of the mod " + id + ": " + e);
            return java.util.Optional.empty();
        }
    }
}
