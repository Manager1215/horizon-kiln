package kiln.test.chunks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * A mod for Kiln's test of a mod reading blocks away from the player (kiln/tests/client_chunks.rs, #219): its key, V,
 * reads blocks of the chunk at 6, 6 from a thread of its own, as a block scanner does (blockgrep), through the level
 * and through the chunk's sections, and says in chat what it read. Written against Minecraft 26.3 and Fabric's API as
 * any mod is; built against Kiln's stand-ins.
 */
public final class TestChunks implements ClientModInitializer {
    public static final String ID = "kiln_test_chunks";

    private static volatile String found;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category group = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "chunks"));
        // 25 is V: Minecraft 26.3's key codes are USB HID scancodes, and a mod compiles the constant in.
        KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + ID + ".scan", 25, group));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                ClientLevel level = client.level;
                Thread scan = new Thread(() -> {
                    String ore = text(level.getBlockState(new BlockPos(100, 64, 100)));
                    String beside = text(level.getBlockState(new BlockPos(101, 64, 100)));
                    ChunkAccess chunk = level.getChunk(6, 6, ChunkStatus.FULL, false);
                    String section = chunk == null ? "no chunk"
                            : text(chunk.getSections()[(64 >> 4) - (level.getMinY() >> 4)].getBlockState(4, 0, 4));
                    ChunkAccess none = level.getChunk(40, 40, ChunkStatus.FULL, false);
                    found = ore + " " + beside + " " + section + " " + (none == null ? "none" : "some");
                }, "kiln-test-scan");
                scan.setDaemon(true);
                scan.start();
            }
            String said = found;
            if (said != null) {
                found = null;
                client.gui.hud.getChat().addClientSystemMessage(Component.literal("Test chunks: " + said));
            }
        });
    }

    private static String text(net.minecraft.world.level.block.state.BlockState state) {
        return state == null ? "null" : state.kiln$text();
    }
}
