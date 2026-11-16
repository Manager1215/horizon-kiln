package kiln.test.soft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A mod for Kiln's test of a mod turned off now (kiln/tests/soft_switch.rs, D-113): its client tick and its mixin in
 * {@code Minecraft.tick} each count, its HUD element draws both counts, and its command says them in chat. Turned off,
 * none of them runs; turned on again, they go on from where they stopped. Written against Minecraft 26.3 and Fabric's
 * API as any mod is; built against Kiln's stand-ins.
 */
public final class TestSoft implements ClientModInitializer {
    public static final String ID = "kiln_test_soft";
    public static int ticks;
    public static int mixin;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> ticks++);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "counts"), (graphics, delta) ->
                graphics.text(Minecraft.getInstance().font, "Soft " + ticks + " " + mixin, 2, 2, 0xFFFFFFFF));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                dispatcher.register(ClientCommands.literal("softtest").executes(c -> {
                    c.getSource().sendFeedback(Component.literal("Soft command " + ticks + " " + mixin));
                    return 1;
                })));
    }
}
