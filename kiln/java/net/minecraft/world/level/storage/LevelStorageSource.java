package net.minecraft.world.level.storage;

/**
 * Kiln's stand-in for {@code LevelStorageSource}: where the worlds are. Horizon keeps them; its {@link LevelStorageAccess}
 * names the open one, for a mod that keeps something per world (a recipe viewer's bookmarks and history, D-167).
 */
public class LevelStorageSource {
    public LevelStorageSource(java.nio.file.Path base, java.nio.file.Path backups,
            net.minecraft.world.level.validation.DirectoryValidator validator, com.mojang.datafixers.DataFixer fixer) {}

    protected LevelStorageSource() {}

    /** The open world's folder. */
    public static class LevelStorageAccess implements AutoCloseable {
        public LevelStorageAccess() {}

        @Override
        public void close() {}

        /**
         * The world's folder's name, as the game's server says it with its calls, or the window's game with its tick
         * (a mod's client code may ask before the server's first call); {@code world} when neither said.
         */
        public String getLevelId() {
            java.nio.file.Path world = kiln.Server.worldFolder();
            if (world != null && world.getFileName() != null) {
                return world.getFileName().toString();
            }
            String level = net.minecraft.client.Minecraft.getInstance() == null ? ""
                    : net.minecraft.client.Minecraft.getInstance().kiln$level();
            return level.isEmpty() ? "world" : level;
        }

        /** The world's folder. */
        public java.nio.file.Path getLevelDirectory() {
            return getLevelPath(LevelResource.ROOT);
        }

        /** A file or folder of the world's folder. */
        public java.nio.file.Path getLevelPath(LevelResource resource) {
            return net.minecraft.server.MinecraftServer.kiln$server().getWorldPath(resource);
        }
    }
}
