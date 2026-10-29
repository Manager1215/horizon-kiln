package net.fabricmc.loader.api;

import java.io.File;
import java.nio.file.Path;

/**
 * Kiln's stand-in for Fabric Loader's {@code FabricLoader}: where the game is. Kiln's folder
 * ({@code <game folder>/kiln}, the {@code kiln.dir} property) is the mods' game folder; their config is in
 * its {@code config} folder.
 */
public interface FabricLoader {
    static FabricLoader getInstance() {
        return Holder.LOADER;
    }

    Path getGameDir();

    Path getConfigDir();

    default File getGameDirectory() {
        return getGameDir().toFile();
    }

    default File getConfigDirectory() {
        return getConfigDir().toFile();
    }

    final class Holder {
        private static final Path GAME = Path.of(System.getProperty("kiln.dir", "."));
        private static final FabricLoader LOADER = new FabricLoader() {
            @Override
            public Path getGameDir() {
                return GAME;
            }

            @Override
            public Path getConfigDir() {
                return GAME.resolve("config");
            }
        };

        private Holder() {}
    }
}
