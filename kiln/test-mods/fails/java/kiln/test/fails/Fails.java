package kiln.test.fails;

import net.fabricmc.api.ClientModInitializer;

/** The test mod's client entrypoint: it throws, as a mod does when the game lacks what it needs. */
public final class Fails implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        throw new IllegalStateException("this mod fails on purpose");
    }
}
