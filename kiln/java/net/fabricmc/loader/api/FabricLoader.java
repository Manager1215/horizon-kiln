package net.fabricmc.loader.api;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import kiln.Mods;
import net.fabricmc.api.EnvType;

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

    /** Kiln runs the mods in the game's window: the client. */
    default EnvType getEnvironmentType() {
        return EnvType.CLIENT;
    }

    default boolean isDevelopmentEnvironment() {
        return false;
    }

    /** The mods on Java's class path, from their fabric.mod.json: Kiln's own stand-ins aren't among them. */
    default Collection<ModContainer> getAllMods() {
        return Mods.all();
    }

    default Optional<ModContainer> getModContainer(String id) {
        return Mods.all().stream().filter(m -> m.getMetadata().getId().equals(id)).findFirst();
    }

    default boolean isModLoaded(String id) {
        return getModContainer(id).isPresent();
    }

    /** The entrypoints of {@code key} that are a {@code type} ({@link #getEntrypointContainers}). */
    default <T> java.util.List<T> getEntrypoints(String key, Class<T> type) {
        return kiln.Loader.containers(key).stream().map(c -> c.getEntrypoint()).filter(type::isInstance).map(type::cast)
                .toList();
    }

    /**
     * The entrypoints of {@code key} that are a {@code type}, with the mods they are of: {@code main} and {@code
     * client} as they ran, a mod's own kind (JEI's plugins) made when first asked for ({@link kiln.Loader#containers}).
     */
    @SuppressWarnings("unchecked")
    default <T> java.util.List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<T>> getEntrypointContainers(String key,
            Class<T> type) {
        java.util.List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<T>> found = new java.util.ArrayList<>();
        for (net.fabricmc.loader.api.entrypoint.EntrypointContainer<Object> c : kiln.Loader.containers(key)) {
            if (type.isInstance(c.getEntrypoint())) {
                found.add((net.fabricmc.loader.api.entrypoint.EntrypointContainer<T>) (Object) c);
            } else {
                kiln.Native.log("the " + key + " entrypoint " + c.getDefinition() + " isn't a " + type.getName());
            }
        }
        return found;
    }

    default <T> void invokeEntrypoints(String key, Class<T> type, java.util.function.Consumer<? super T> invoker) {
        for (net.fabricmc.loader.api.entrypoint.EntrypointContainer<T> c : getEntrypointContainers(key, type)) {
            invoker.accept(c.getEntrypoint());
        }
    }

    final class Holder {
        private static final Path GAME = Path.of(System.getProperty("kiln.dir", "."));
        private static final FabricLoader LOADER = new FabricLoader() {
            @Override
            public Path getGameDir() {
                return GAME;
            }

            /** Made when first asked for, as Fabric makes it at start: mods write into it without checking. */
            @Override
            public Path getConfigDir() {
                Path config = GAME.resolve("config");
                try {
                    Files.createDirectories(config);
                } catch (IOException e) {
                    // The mod's own write fails and says why.
                }
                return config;
            }
        };

        private Holder() {}
    }
}
