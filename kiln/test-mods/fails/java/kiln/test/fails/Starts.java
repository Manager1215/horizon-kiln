package kiln.test.fails;

import net.fabricmc.api.ModInitializer;

/** The test mod's main entrypoint: it runs, before every client entrypoint, as Fabric runs them. */
public final class Starts implements ModInitializer {
    @Override
    public void onInitialize() {
        // Nothing to do: the mod fails in its client entrypoint.
    }
}
