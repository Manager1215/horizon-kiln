package kiln.test.loader;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

/**
 * The test mod's client entrypoint, shaped as a Kotlin {@code object} compiles: a private constructor and the one
 * instance in a static {@code INSTANCE}, which Fabric Language Kotlin's adapter hands Fabric (D-098). It checks what
 * mods look up as they start, and throws with what went wrong.
 */
final class Checks implements ClientModInitializer {
    public static final Checks INSTANCE = new Checks();

    private Checks() {}

    @Override
    public void onInitializeClient() {
        List<String> wrong = new ArrayList<>();
        FabricLoader loader = FabricLoader.getInstance();
        String game = loader.getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("none");
        if (!game.equals("26.3")) {
            wrong.add("minecraft's version: " + game);
        }
        for (String id : new String[] {"java", "fabricloader", "fabric-api"}) {
            if (!loader.isModLoaded(id)) {
                wrong.add(id + " isn't listed");
            }
        }
        if (BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("kiln_test_loader", "nothing"))
                .isPresent()) {
            wrong.add("a registry's getOptional of nothing isn't empty");
        }
        // A mod with mixins and no entrypoint beside it (kiln/test-mods/mixinonly): Kiln's Java applied its mixin as the
        // game's options were made (D-099).
        net.minecraft.client.Options options = net.minecraft.client.Minecraft.getInstance().options;
        if (options == null || !"applied".equals(System.getProperty("kiln.test.mixinOnly"))) {
            wrong.add("the mixin-only mod's mixin didn't run: " + System.getProperty("kiln.test.mixinOnly"));
        }
        if (!wrong.isEmpty()) {
            throw new IllegalStateException(String.join("; ", wrong));
        }
        LoggerFactory.getLogger("kiln_test_loader").info("Kiln Test Loader: its checks passed");
    }
}
