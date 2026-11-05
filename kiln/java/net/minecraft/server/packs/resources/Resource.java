package net.minecraft.server.packs.resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.packs.PackResources;

/** Kiln's stand-in for {@code Resource}: a file of a pack, opened when read. */
public class Resource {
    private final PackResources source;
    private final IoSupplier<InputStream> streamSupplier;

    public Resource(PackResources source, IoSupplier<InputStream> streamSupplier) {
        this.source = source;
        this.streamSupplier = streamSupplier;
    }

    public PackResources source() {
        return source;
    }

    public String sourcePackId() {
        return source.packId();
    }

    public InputStream open() throws IOException {
        return streamSupplier.get();
    }

    public BufferedReader openAsReader() throws IOException {
        return new BufferedReader(new InputStreamReader(open(), StandardCharsets.UTF_8));
    }

    public String readAllAsString() throws IOException {
        try (InputStream in = open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
