package kiln.test.keys;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A mod for Kiln's test of a mod's key the player binds (kiln/tests/keys.rs): one key, V as the mod sets it, in a
 * group of its own; each press it takes says so in chat, and whether the key is held. Written against Minecraft 26.3
 * and Fabric's API as any mod is; built against Kiln's stand-ins.
 */
public final class TestKeys implements ClientModInitializer {
    public static final String ID = "kiln_test_keys";
    private int presses;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category group = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "keys"));
        // 25 is V: Minecraft 26.3's key codes are USB HID scancodes, and a mod compiles the constant in.
        KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + ID + ".press", 25, group));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                presses++;
                String held = key.isDown() ? ", held" : "";
                client.gui.hud.getChat().addClientSystemMessage(Component.literal("Test Keys: press " + presses + held));
            }
        });
    }
}
