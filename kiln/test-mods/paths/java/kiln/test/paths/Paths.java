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
            // D-188 (#216): a constructor with two arguments after the path.
            try (FileWriter writer = new FileWriter("config/kiln_test_paths.three", StandardCharsets.UTF_8, false)) {
                writer.write("three");
            }
            check("FileWriter(String, Charset, boolean)", Files.exists(Path.of("config/kiln_test_paths.three")));
            // getPath and toString give the text the mod wrote, while the file is Kiln's folder's.
            File relative = new File("config/kiln_test_paths.json");
            check("getPath", relative.getPath().equals("config" + File.separator + "kiln_test_paths.json"));
            check("toString", relative.toString().equals(relative.getPath()));
            File child = new File(new File("config"), "kiln_test_paths.json");
            check("File(File, String) exists", child.exists());
            check("File(File, String).getPath",
                    child.getPath().equals("config" + File.separator + "kiln_test_paths.json"));
            check("an absolute path stays", new File(relative.getAbsolutePath()).getPath().equals(relative.getAbsolutePath()));
            // A process started without a folder of its own starts in Kiln's.
            if (!System.getProperty("os.name").startsWith("Windows")) {
                String kiln = new File(System.getProperty("user.dir")).getCanonicalPath();
                Process built = new ProcessBuilder("sh", "-c", "pwd -P").start();
                check("ProcessBuilder.start's folder", kiln.equals(new String(built.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8).trim()));
                Process run = Runtime.getRuntime().exec(new String[] {"sh", "-c", "pwd -P"});
                check("Runtime.exec's folder", kiln.equals(new String(run.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8).trim()));
            }
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
