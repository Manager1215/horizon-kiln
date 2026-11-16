package net.fabricmc.fabric.api.resource;

import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

/**
 * Kiln's stand-in for Fabric's {@code ResourceManagerHelper} (resource loader v0): the reload listeners go to the
 * pack type's {@link net.fabricmc.fabric.api.resource.v1.ResourceLoader}, by their id, kept as v1's are and not run yet
 * (D-152). One made from the registries (the function) is kept by id only: Kiln has no reload to make it for (D-171).
 */
public interface ResourceManagerHelper {
    void registerReloadListener(IdentifiableResourceReloadListener listener);

    void registerReloadListener(Identifier id, Function<HolderLookup.Provider, IdentifiableResourceReloadListener> factory);

    static ResourceManagerHelper get(PackType type) {
        return Helpers.BY_TYPE.computeIfAbsent(type, Helpers.Helper::new);
    }

    /** A mod's built-in pack: the game has the jar's resources itself (Horizon's import), so none is added. */
    static boolean registerBuiltinResourcePack(Identifier id, net.fabricmc.loader.api.ModContainer container,
            ResourcePackActivationType activation) {
        return false;
    }

    /** Kiln's: the helper of each pack type. */
    final class Helpers {
        private static final Map<PackType, ResourceManagerHelper> BY_TYPE =
                new java.util.concurrent.ConcurrentHashMap<>();

        private Helpers() {}

        private record Helper(PackType type) implements ResourceManagerHelper {
            @Override
            public void registerReloadListener(IdentifiableResourceReloadListener listener) {
                net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(type)
                        .registerReloadListener(listener.getFabricId(), listener);
            }

            @Override
            public void registerReloadListener(Identifier id,
                    Function<HolderLookup.Provider, IdentifiableResourceReloadListener> factory) {}
        }
    }
}
