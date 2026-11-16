package net.fabricmc.fabric.api.resource;

import java.util.Collection;
import java.util.Collections;
import net.minecraft.resources.Identifier;

/** Kiln's stand-in for Fabric's {@code IdentifiableResourceReloadListener} (resource loader v0): a reload listener with an id. */
public interface IdentifiableResourceReloadListener extends net.minecraft.server.packs.resources.PreparableReloadListener {
    Identifier getFabricId();

    default Collection<Identifier> getFabricDependencies() {
        return Collections.emptyList();
    }
}
