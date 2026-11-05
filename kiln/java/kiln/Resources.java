package kiln;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The client's resources ({@code Minecraft.getResourceManager}): the game's own, from its prepared resources (the
 * {@code kiln.resources} folder's {@code assets}), at the bottom; above them each Java mod's jar ({@code assets}), as
 * Fabric puts the mods' packs above vanilla's. Files only: Kiln reads no pack's settings.
 */
public final class Resources implements ResourceManager {
    public static final Resources INSTANCE = new Resources();

    private List<Pack> packs;

    private Resources() {}

    /** A pack: the game's folder, or a mod's jar. */
    private record Pack(String id, Path folder, Path jar) implements PackResources {
        @Override
        public String packId() {
            return id;
        }

        /** Its file at {@code assets/<namespace>/<path>}, if it has one. */
        Optional<Resource> find(Identifier id) {
            String name = "assets/" + id.getNamespace() + "/" + id.getPath();
            if (folder != null) {
                Path file = folder.resolve(name).normalize();
                return file.startsWith(folder) && Files.isRegularFile(file)
                        ? Optional.of(new Resource(this, IoSupplier.create(file))) : Optional.empty();
            }
            try (JarFile in = new JarFile(jar.toFile())) {
                if (in.getJarEntry(name) == null) {
                    return Optional.empty();
                }
            } catch (IOException e) {
                return Optional.empty();
            }
            return Optional.of(new Resource(this, () -> {
                JarFile in = new JarFile(jar.toFile());
                JarEntry entry = in.getJarEntry(name);
                if (entry == null) {
                    in.close();
                    throw new java.io.FileNotFoundException(name);
                }
                return new java.io.FilterInputStream(in.getInputStream(entry)) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        in.close();
                    }
                };
            }));
        }

        /** Every file of it under {@code assets/<namespace>/<path>/}, by id. */
        Map<Identifier, Resource> under(String path) {
            Map<Identifier, Resource> found = new TreeMap<>(java.util.Comparator.comparing(Identifier::toString));
            try {
                if (folder != null) {
                    Path assets = folder.resolve("assets");
                    if (!Files.isDirectory(assets)) {
                        return found;
                    }
                    try (Stream<Path> namespaces = Files.list(assets)) {
                        for (Path ns : namespaces.toList()) {
                            Path root = ns.resolve(path);
                            if (!Files.isDirectory(root)) {
                                continue;
                            }
                            try (Stream<Path> files = Files.walk(root)) {
                                for (Path f : files.filter(Files::isRegularFile).toList()) {
                                    String rest = ns.relativize(f).toString().replace('\\', '/');
                                    Identifier id = identifier(ns.getFileName().toString(), rest);
                                    if (id != null) {
                                        found.put(id, new Resource(this, IoSupplier.create(f)));
                                    }
                                }
                            }
                        }
                    }
                } else {
                    try (JarFile in = new JarFile(jar.toFile())) {
                        for (JarEntry e : java.util.Collections.list(in.entries())) {
                            String[] parts = e.getName().split("/", 3);
                            if (e.isDirectory() || parts.length < 3 || !parts[0].equals("assets")
                                    || !parts[2].startsWith(path + "/")) {
                                continue;
                            }
                            Identifier id = identifier(parts[1], parts[2]);
                            if (id != null) {
                                find(id).ifPresent(r -> found.put(id, r));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Kiln: the resources of " + this.id + ": " + e);
            }
            return found;
        }

        Set<String> namespaces() {
            Set<String> found = new TreeSet<>();
            try {
                if (folder != null) {
                    Path assets = folder.resolve("assets");
                    if (Files.isDirectory(assets)) {
                        try (Stream<Path> namespaces = Files.list(assets)) {
                            namespaces.filter(Files::isDirectory).forEach(p -> found.add(p.getFileName().toString()));
                        }
                    }
                } else {
                    try (JarFile in = new JarFile(jar.toFile())) {
                        for (JarEntry e : java.util.Collections.list(in.entries())) {
                            String[] parts = e.getName().split("/", 3);
                            if (parts.length >= 3 && parts[0].equals("assets") && !parts[1].isEmpty()) {
                                found.add(parts[1]);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Kiln: the resources of " + id + ": " + e);
            }
            return found;
        }
    }

    /** {@code namespace:path}, or null when it isn't a valid id. */
    private static Identifier identifier(String namespace, String path) {
        return Identifier.tryParse(namespace + ":" + path);
    }

    /** The packs, bottom first: the game's (when the game said where its resources are), then each mod's jar. */
    private synchronized List<Pack> packs() {
        if (packs == null) {
            List<Pack> found = new ArrayList<>();
            String game = System.getProperty("kiln.resources", "");
            if (!game.isEmpty() && Files.isDirectory(Path.of(game))) {
                found.add(new Pack("vanilla", Path.of(game).toAbsolutePath().normalize(), null));
            }
            try {
                Enumeration<URL> manifests = Resources.class.getClassLoader().getResources("fabric.mod.json");
                while (manifests.hasMoreElements()) {
                    URL url = manifests.nextElement();
                    if (!url.getProtocol().equals("jar")) {
                        continue;
                    }
                    Path jar = Path.of(((JarURLConnection) url.openConnection()).getJarFileURL().toURI());
                    String id = jar.getFileName().toString();
                    try (Reader r = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                        if (json.has("id")) {
                            id = json.get("id").getAsString();
                        }
                    } catch (RuntimeException e) {
                        // The jar's name stands for its id.
                    }
                    found.add(new Pack(id, null, jar));
                }
            } catch (IOException | java.net.URISyntaxException e) {
                Host.send("log", "Kiln can't list the mods' resources: " + e);
            }
            packs = List.copyOf(found);
        }
        return packs;
    }

    @Override
    public Optional<Resource> getResource(Identifier id) {
        List<Resource> stack = getResourceStack(id);
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.get(stack.size() - 1));
    }

    @Override
    public List<Resource> getResourceStack(Identifier id) {
        List<Resource> stack = new ArrayList<>();
        for (Pack pack : packs()) {
            pack.find(id).ifPresent(stack::add);
        }
        return stack;
    }

    @Override
    public Set<String> getNamespaces() {
        Set<String> found = new TreeSet<>();
        for (Pack pack : packs()) {
            found.addAll(pack.namespaces());
        }
        return found;
    }

    @Override
    public Map<Identifier, Resource> listResources(String path, Selector selector) {
        Map<Identifier, Resource> found = new LinkedHashMap<>();
        for (Pack pack : packs()) {
            pack.under(path).forEach((id, r) -> {
                if (selector.isIncluded(id)) {
                    found.put(id, r);
                }
            });
        }
        return found;
    }

    @Override
    public Map<Identifier, List<Resource>> listResourceStacks(String path, Selector selector) {
        Map<Identifier, List<Resource>> found = new LinkedHashMap<>();
        for (Pack pack : packs()) {
            pack.under(path).forEach((id, r) -> {
                if (selector.isIncluded(id)) {
                    found.computeIfAbsent(id, k -> new ArrayList<>()).add(r);
                }
            });
        }
        return found;
    }

    @Override
    public Stream<PackResources> listPacks() {
        return packs().stream().map(p -> p);
    }
}
