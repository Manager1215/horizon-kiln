package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.check;
import static io.github.manager1215.kilntestbench.client.Game.expect;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.INIT;

import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Probe;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric Loader: what every mod asks first. */
final class LoaderProbes {
    private LoaderProbes() {}

    static void add(Bench b) {
        b.add("loader.configDir", INIT, () -> new Probe() {
            public Object run() {
                // The game folder's config/, as Fabric Loader makes it.
                Path config = FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize();
                expect(config, FabricLoader.getInstance().getGameDir().resolve("config").toAbsolutePath().normalize());
                return config.getFileName();
            }
        });
        b.add("loader.gameDir", INIT, () -> new Probe() {
            public Object run() {
                Path game = FabricLoader.getInstance().getGameDir();
                check(game.isAbsolute(), game + " is not absolute");
                return expect(Files.isDirectory(game), true);
            }
        });
        b.add("loader.isModLoaded", INIT, () -> new Probe() {
            public Object run() {
                check(!FabricLoader.getInstance().isModLoaded("kiln_testbench_no_such_mod"), "a mod nobody has loaded");
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
                check(FabricLoader.getInstance().getAllMods().stream()
                        .anyMatch(m -> m.getMetadata().getId().equals(KilnTestbenchClient.ID)), "the testbench isn't listed");
                return FabricLoader.getInstance().getAllMods().size();
            }
        });
        b.add("loader.entrypoints", INIT, () -> new Probe() {
            public Object run() {
                // The client entrypoints, this one among them: Fabric Loader made it, so it is the same class.
                check(FabricLoader.getInstance().getEntrypoints("client", ClientModInitializer.class).stream()
                        .anyMatch(e -> e instanceof KilnTestbenchClient), "the testbench's entrypoint isn't listed");
                return FabricLoader.getInstance().getEntrypoints("client", ClientModInitializer.class).size();
            }
        });
    }
}
