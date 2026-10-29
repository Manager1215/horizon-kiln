package kiln.hello;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * The spike's Java mod, written against Fabric's API: counts client ticks and draws a line on the HUD. Kiln
 * runs it in Horizon from its fabric.mod.json.
 */
public final class HelloMod implements ClientModInitializer {
    private long ticks;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> ticks++);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("kiln_hello", "line"), (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            String line = "Hello from Java! tick " + ticks;
            int x = graphics.guiWidth() - mc.font.width(line) - 4;
            int y = graphics.guiHeight() - 24;
            graphics.fill(x - 2, y - 2, x + mc.font.width(line) + 2, y + mc.font.lineHeight + 1, 0x78000000);
            graphics.text(mc.font, line, x, y, 0xFFFFFFFF);
        });
    }
}
