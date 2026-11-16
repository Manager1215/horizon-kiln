package net.minecraft.server.packs.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;

/**
 * Kiln's stand-in for {@code ReloadableResourceManager}: the client's resources ({@link kiln.Resources}), and the
 * listeners mods register to hear them load. The game loads its resources itself; the listeners are kept, and run
 * when Kiln reloads them (not yet: D-152).
 */
public class ReloadableResourceManager implements ResourceManager, AutoCloseable {
    private final ResourceManager resources;
    private final List<PreparableReloadListener> listeners = new ArrayList<>();

    public ReloadableResourceManager(ResourceManager resources) {
        this.resources = resources;
    }

    public synchronized void registerReloadListener(PreparableReloadListener listener) {
        listeners.add(listener);
    }

    /** Kiln's own: the listeners registered, in order. */
    public synchronized List<PreparableReloadListener> kiln$listeners() {
        return List.copyOf(listeners);
    }

    @Override
    public Optional<Resource> getResource(Identifier id) {
        return resources.getResource(id);
    }

    @Override
    public Set<String> getNamespaces() {
        return resources.getNamespaces();
    }

    @Override
    public List<Resource> getResourceStack(Identifier id) {
        return resources.getResourceStack(id);
    }

    @Override
    public Map<Identifier, Resource> listResources(String path, Selector selector) {
        return resources.listResources(path, selector);
    }

    @Override
    public Map<Identifier, List<Resource>> listResourceStacks(String path, Selector selector) {
        return resources.listResourceStacks(path, selector);
    }

    @Override
    public Stream<PackResources> listPacks() {
        return resources.listPacks();
    }

    @Override
    public void close() {}
}
