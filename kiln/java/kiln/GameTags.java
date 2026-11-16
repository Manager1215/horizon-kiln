package kiln;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;

/**
 * The names of the game's tags of one registry, from its prepared resources ({@code data/<ns>/tags/<folder>/}), in
 * name order (D-159), with Fabric API's conventional tags ({@code c:}, {@link FabricData}): what
 * {@code Registry.getTags()} lists, so a recipe viewer can show each tag's entries. A mod's own tag files aren't read
 * here.
 */
public final class GameTags {
    private static final Map<String, List<Identifier>> NAMES = new ConcurrentHashMap<>();

    private GameTags() {}

    /** The game's tags of the registry whose tags are in {@code folder} ({@code item}, {@code block}...). */
    public static List<Identifier> names(String folder) {
        return NAMES.computeIfAbsent(folder, GameTags::scan);
    }

    private static List<Identifier> scan(String folder) {
        List<Identifier> names = new ArrayList<>();
        if (folder.contains("..")) {
            return List.of();
        }
        String resources = System.getProperty("kiln.resources", "");
        Path data = resources.isEmpty() ? null : Path.of(resources, "data");
        if (data == null || !Files.isDirectory(data)) {
            return fabric(names, folder);
        }
        try (java.util.stream.Stream<Path> namespaces = Files.list(data)) {
            for (Path namespace : namespaces.sorted().toList()) {
                Path dir = namespace.resolve("tags").resolve(folder);
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (java.util.stream.Stream<Path> files = Files.walk(dir, java.nio.file.FileVisitOption.FOLLOW_LINKS)) {
                    for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                        String path = dir.relativize(file).toString().replace('\\', '/');
                        Identifier tag = Identifier.tryParse(
                                namespace.getFileName() + ":" + path.substring(0, path.length() - 5));
                        if (tag != null) {
                            names.add(tag);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Kiln: the game's " + folder + " tag names: " + e);
        }
        return fabric(names, folder);
    }

    /** {@code names} with Fabric API's conventional tags Kiln carries (D-173), in name order, each once. */
    private static List<Identifier> fabric(List<Identifier> names, String folder) {
        java.util.TreeMap<String, Identifier> all = new java.util.TreeMap<>();
        for (Identifier name : names) {
            all.put(name.toString(), name);
        }
        for (Identifier name : FabricData.tags(folder)) {
            all.putIfAbsent(name.toString(), name);
        }
        return List.copyOf(all.values());
    }
}
