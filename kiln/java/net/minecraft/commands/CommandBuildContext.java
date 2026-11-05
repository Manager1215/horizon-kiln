package net.minecraft.commands;

import net.minecraft.core.HolderLookup;

/** Kiln's stand-in for {@code CommandBuildContext}: the registries commands are built against (Kiln reads none). */
public interface CommandBuildContext extends HolderLookup.Provider {
    /** Kiln's own: the context the window's client commands are built in. */
    CommandBuildContext KILN = new CommandBuildContext() {};
}
