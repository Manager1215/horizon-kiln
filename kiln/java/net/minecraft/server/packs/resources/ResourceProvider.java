package net.minecraft.server.packs.resources;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/** Kiln's stand-in for {@code ResourceProvider}: resources by id ({@code minecraft:lang/en_us.json}). */
@FunctionalInterface
public interface ResourceProvider {
    ResourceProvider EMPTY = id -> Optional.empty();

    Optional<Resource> getResource(Identifier id);

    default Resource getResourceOrThrow(Identifier id) throws FileNotFoundException {
        return getResource(id).orElseThrow(() -> new FileNotFoundException(id.toString()));
    }

    default InputStream open(Identifier id) throws IOException {
        return getResourceOrThrow(id).open();
    }

    default BufferedReader openAsReader(Identifier id) throws IOException {
        return getResourceOrThrow(id).openAsReader();
    }

    static ResourceProvider fromMap(Map<Identifier, Resource> resources) {
        return id -> Optional.ofNullable(resources.get(id));
    }
}
