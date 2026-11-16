package kiln.test.fov;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A mod for Kiln's test of a mod setting the game's options (kiln/tests/options.rs): its key, V, switches the field
 * of view between 30 degrees and 70, as FOV Toggle does, through the game's options, and says in chat what it read
 * and what it set. Written against Minecraft 26.3 and Fabric's API as any mod is; built against Kiln's stand-ins.
 */
public final class TestFov implements ClientModInitializer {
    public static final String ID = "kiln_test_fov";

    @Override
    public void onInitializeClient() {
        KeyMapping.Category group = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "fov"));
        // 25 is V: Minecraft 26.3's key codes are USB HID scancodes, and a mod compiles the constant in.
        KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + ID + ".toggle", 25, group));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                OptionInstance<Integer> fov = client.options.fov();
                int now = fov.get();
                int next = now == 30 ? 70 : 30;
                fov.set(next);
                client.gui.hud.getChat().addClientSystemMessage(Component.literal("Test FOV: " + now + " to " + next));
            }
        });
    }
}
