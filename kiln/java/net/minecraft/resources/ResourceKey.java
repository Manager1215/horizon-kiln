package net.minecraft.resources;

/** Kiln's stand-in for {@code ResourceKey}: what a registry entry, such as a dimension, is called. */
public class ResourceKey<T> {
    private final Identifier identifier;

    private ResourceKey(Identifier identifier) {
        this.identifier = identifier;
    }

    /** Kiln's own: the key named {@code identifier}. */
    public static <T> ResourceKey<T> kiln$of(Identifier identifier) {
        return new ResourceKey<>(identifier);
    }

    public Identifier identifier() {
        return identifier;
    }

    @Override
    public String toString() {
        return "ResourceKey[" + identifier + "]";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ResourceKey<?> k && k.identifier.equals(identifier);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }
}
