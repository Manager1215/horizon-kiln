package net.minecraft.util.context;

import net.minecraft.resources.Identifier;

/** Kiln's stand-in for {@code ContextKey}: the name of a value a loot context carries (the block, the origin...). */
public class ContextKey<T> {
    private final Identifier name;

    public ContextKey(Identifier name) {
        this.name = name;
    }

    public static <T> ContextKey<T> vanilla(String name) {
        return new ContextKey<>(Identifier.withDefaultNamespace(name));
    }

    public Identifier name() {
        return name;
    }

    @Override
    public String toString() {
        return "<parameter " + name + ">";
    }
}
