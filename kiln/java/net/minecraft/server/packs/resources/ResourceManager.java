package net.minecraft.server.packs.resources;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;

/**
 * Kiln's stand-in for {@code ResourceManager}: the resources of the packs in force, the game's at the bottom and the
 * mods' above it; a resource is the top pack's.
 */
public interface ResourceManager extends ResourceProvider {
    Set<String> getNamespaces();

    /** Every pack's file of {@code id}, from the bottom pack up. */
    List<Resource> getResourceStack(Identifier id);

    /** The files under {@code path} the selector takes, each the top pack's. */
    Map<Identifier, Resource> listResources(String path, Selector selector);

    Map<Identifier, List<Resource>> listResourceStacks(String path, Selector selector);

    Stream<PackResources> listPacks();

    @FunctionalInterface
    interface Selector {
        boolean isIncluded(Identifier id);
    }
}
