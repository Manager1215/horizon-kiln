package net.minecraft.resources;

import java.util.Objects;

/** Kiln's stand-in for Minecraft 26.3's {@code Identifier}: a namespace and a path, {@code minecraft:story/root}. */
public final class Identifier implements Comparable<Identifier> {
    private final String namespace;
    private final String path;

    private Identifier(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static Identifier fromNamespaceAndPath(String namespace, String path) {
        return new Identifier(namespace, path);
    }

    /** {@code namespace:path}, or {@code path} in the {@code minecraft} namespace. */
    public static Identifier parse(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? withDefaultNamespace(id) : new Identifier(id.substring(0, colon), id.substring(colon + 1));
    }

    public static Identifier withDefaultNamespace(String path) {
        return new Identifier("minecraft", path);
    }

    public String getPath() {
        return path;
    }

    public String getNamespace() {
        return namespace;
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Identifier i && i.namespace.equals(namespace) && i.path.equals(path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }

    @Override
    public int compareTo(Identifier o) {
        int c = path.compareTo(o.path);
        return c != 0 ? c : namespace.compareTo(o.namespace);
    }
}
