package kiln.test.exits;

import net.fabricmc.api.ModInitializer;

/** The test mod's main entrypoint: it halts Java, as a mod that gives up on a broken setup might. */
public final class Halts implements ModInitializer {
    @Override
    public void onInitialize() {
        Runtime.getRuntime().halt(4);
    }
}
