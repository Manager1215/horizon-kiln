package kiln.test.exits;

import net.fabricmc.api.ClientModInitializer;

/** The test mod's client entrypoint: it exits. */
public final class Exits implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.exit(3);
    }
}
