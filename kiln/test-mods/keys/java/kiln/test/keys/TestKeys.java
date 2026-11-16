package kiln.test.keys;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A mod for Kiln's test of a mod's key the player binds (kiln/tests/keys.rs): one key, V as the mod sets it, in a
 * group of its own; each press it takes says so in chat, and whether the key is held. {@code /testkeys <code>} sets the
 * key in code ({@code KeyMapping.setKey}), as a mod's own key screen or a config library does, and says what it is
 * bound to now and whether a key event for that code matches it. Written against Minecraft 26.3
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
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommands.literal("testkeys")
                        .then(ClientCommands.argument("code", IntegerArgumentType.integer(0, 255)).executes(c -> {
                            int code = IntegerArgumentType.getInteger(c, "code");
                            key.setKey(InputConstants.Type.KEYBOARD.getOrCreate(code));
                            KeyMapping.resetMapping();
                            String matches = key.matches(new net.minecraft.client.input.KeyEvent(code, 0, 0))
                                    ? ", matches" : "";
                            c.getSource().sendFeedback(Component.literal(
                                    "Test Keys: set to " + KeyMappingHelper.getBoundKeyOf(key).getName() + matches));
                            return 1;
                        }))));
    }
}
