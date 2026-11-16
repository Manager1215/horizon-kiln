package net.minecraft.server.permissions;

import net.minecraft.resources.Identifier;

/** Kiln's stand-in for {@code Permission}: what a permission set may grant, an operator level or a named atom. */
public interface Permission {
    /** Granted at {@code level} and above. */
    record HasCommandLevel(PermissionLevel level) implements Permission {}

    /** A named permission; a level-based set (all Kiln has) grants none. */
    record Atom(Identifier id) implements Permission {
        public static Atom create(String path) {
            return new Atom(Identifier.withDefaultNamespace(path));
        }

        public static Atom create(Identifier id) {
            return new Atom(id);
        }
    }
}
