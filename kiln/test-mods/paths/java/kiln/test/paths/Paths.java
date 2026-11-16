package kiln.test.paths;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;

/**
 * The test mod's entrypoint: it reads config/kiln_test_paths.json by relative paths, the ways mods do (java.io's
 * constructors with text, a File, NIO's Path and its toFile), and writes a file beside it. Each must find Kiln's
 * config folder, not the game's folder: it throws if one doesn't, so the mod doesn't start.
 */
public final class Paths implements ModInitializer {
    private static final String WANTED = "{\"kiln\": true}";

    @Override
    public void onInitialize() {
        try {
            check("java.io.File exists", new File("config/kiln_test_paths.json").exists());
            check("NIO exists", Files.exists(Path.of("config", "kiln_test_paths.json")));
            try (BufferedReader reader = new BufferedReader(new FileReader("config/kiln_test_paths.json"))) {
                check("FileReader(String)", WANTED.equals(reader.readLine()));
            }
            try (InputStream in = new FileInputStream(new File("config", "kiln_test_paths.json"))) {
                check("FileInputStream(File)", WANTED.equals(new String(in.readAllBytes(), StandardCharsets.UTF_8).trim()));
            }
            try (InputStream in = new FileInputStream(Path.of("config/kiln_test_paths.json").toFile())) {
                check("Path.toFile", WANTED.equals(new String(in.readAllBytes(), StandardCharsets.UTF_8).trim()));
            }
            try (FileWriter writer = new FileWriter("config/kiln_test_paths.written", false)) {
                writer.write("written");
            }
            check("FileWriter(String, boolean)", Files.exists(Path.of("config/kiln_test_paths.written")));
        } catch (IOException e) {
            throw new IllegalStateException("a relative path wasn't Kiln's folder's: " + e, e);
        }
        System.out.println("Kiln Test Paths: every relative path was Kiln's folder's");
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            throw new IllegalStateException("a relative path wasn't Kiln's folder's: " + what);
        }
    }
}
