package kiln;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fabric API's own data that Kiln carries (D-173): the tags of Fabric's conventional namespace ({@code c:}), which
 * mods name in their recipes ({@code #c:tools/shear}) as they name the game's. {@code tools/build-java.sh} puts the
 * tag files of the jars {@code kiln/lib/data.txt} pins into the runtime jar, under {@code kiln/fabric-data/}, with
 * an index; the game merges a tag's files from every pack, and so does Kiln with the game's own.
 */
public final class FabricData {
    private static final String ROOT = "/kiln/fabric-data/";
    private static volatile List<String> index;

    private FabricData() {}

    /** The tag files Kiln carries ({@code data/c/tags/item/tools/shear.json}...). */
    public static List<String> files() {
        List<String> files = index;
        if (files == null) {
            files = new ArrayList<>();
            try (InputStream in = FabricData.class.getResourceAsStream(ROOT + "index.txt")) {
                if (in != null) {
                    for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                        if (!line.isBlank()) {
                            files.add(line.strip());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Kiln: Fabric API's tags: " + e);
            }
            index = files = List.copyOf(files);
        }
        return files;
    }

    /** The file {@code path} ({@code data/<namespace>/tags/<folder>/<tag>.json}), or null when Kiln hasn't it. */
    public static Reader open(String path) {
        if (!files().contains(path)) {
            return null;
        }
        InputStream in = FabricData.class.getResourceAsStream(ROOT + path);
        return in == null ? null : new java.io.InputStreamReader(in, StandardCharsets.UTF_8);
    }

    /** The tags of {@code folder}'s registry ({@code item}, {@code block}...) whose files Kiln carries, as ids. */
    public static List<net.minecraft.resources.Identifier> tags(String folder) {
        List<net.minecraft.resources.Identifier> tags = new ArrayList<>();
        for (String file : files()) {
            String[] parts = file.split("/", 5);
            // data / <namespace> / tags / <folder> / <path>.json
            if (parts.length == 5 && parts[2].equals("tags") && file.startsWith("data/" + parts[1] + "/tags/" + folder + "/")) {
                String path = file.substring(("data/" + parts[1] + "/tags/" + folder + "/").length(), file.length() - 5);
                net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(parts[1] + ":" + path);
                if (id != null) {
                    tags.add(id);
                }
            }
        }
        return tags;
    }
}
