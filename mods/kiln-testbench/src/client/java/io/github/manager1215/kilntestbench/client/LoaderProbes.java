package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.expect;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.INIT;

import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Probe;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric Loader: what every mod asks first. */
final class LoaderProbes {
    private LoaderProbes() {}

    static void add(Bench b) {
        b.add("loader.configDir", INIT, () -> new Probe() {
            public Object run() {
                return FabricLoader.getInstance().getConfigDir().getFileName();
            }
        });
        b.add("loader.gameDir", INIT, () -> new Probe() {
            public Object run() {
                return FabricLoader.getInstance().getGameDir().isAbsolute();
            }
        });
        b.add("loader.isModLoaded", INIT, () -> new Probe() {
            public Object run() {
                return expect(FabricLoader.getInstance().isModLoaded(KilnTestbenchClient.ID), true);
            }
        });
        b.add("loader.modContainer", INIT, () -> new Probe() {
            public Object run() {
                return FabricLoader.getInstance().getModContainer(KilnTestbenchClient.ID).orElseThrow().getMetadata()
                        .getVersion().getFriendlyString();
            }
        });
        b.add("loader.environment", INIT, () -> new Probe() {
            public Object run() {
                return expect(FabricLoader.getInstance().getEnvironmentType(), EnvType.CLIENT);
            }
        });
        b.add("loader.allMods", INIT, () -> new Probe() {
            public Object run() {
                return FabricLoader.getInstance().getAllMods().size();
            }
        });
        b.add("loader.entrypoints", INIT, () -> new Probe() {
            public Object run() {
                return FabricLoader.getInstance().getEntrypoints("client", ClientModInitializer.class).size();
            }
        });
    }
}
