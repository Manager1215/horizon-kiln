package kiln.test.late;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Kiln's test of a late payload (kiln/tests/late_payloads.rs): the server's receiver is slower than Kiln's call waits
 * (as Java is on a loaded computer), then asks for the players, as mods' receivers do, and answers the player with
 * how many it found.
 */
public final class TestLate implements ModInitializer {
    public static final String ID = "kiln_test_late";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(Slow.TYPE, Slow.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Slow.TYPE, Slow.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Slow.TYPE, (slow, context) -> {
            try {
                Thread.sleep(Long.parseLong(slow.line()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ServerPlayer player = context.player();
            int all = PlayerLookup.all(player.level().getServer()).size();
            int near = PlayerLookup.tracking(player).size();
            context.responseSender().sendPacket(new Slow("after " + slow.line() + " ms: " + all + " on the server, "
                    + near + " near " + player.getName().getString()));
        });
    }
}
