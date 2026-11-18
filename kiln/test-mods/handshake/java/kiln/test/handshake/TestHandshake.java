package kiln.test.handshake;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;

/**
 * A mod for Kiln's tests of Fabric's configuration phase (kiln/tests/handshake.rs, D-199): BEFORE_CONFIGURE says hello
 * 1 through {@code ServerConfigurationNetworking.send}, CONFIGURE hello 7 through a packet the connection sends, as
 * Tech Reborn's ore depths go; the server's receiver of the window's thanks tells the player in chat; JOIN, in play,
 * sends a welcome. Written against Fabric's API as any mod is; built against Kiln's stand-ins.
 */
public final class TestHandshake implements ModInitializer {
    public static final String ID = "kiln_test_handshake";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundConfiguration().register(Hello.TYPE, Hello.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(Thanks.TYPE, Thanks.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Welcome.TYPE, Welcome.CODEC);
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((handler, server) ->
                ServerConfigurationNetworking.send(handler, new Hello(1)));
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, Hello.TYPE)) {
                handler.send(ServerConfigurationNetworking.createClientboundPacket(new Hello(7)), null);
            }
        });
        ServerConfigurationNetworking.registerGlobalReceiver(Thanks.TYPE, (thanks, context) ->
                context.responseSender().sendPacket(new Hello(thanks.number() + 1)));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(new Welcome("welcome"));
            handler.getPlayer().sendSystemMessage(Component.literal("Kiln Test Handshake: joined"));
        });
    }
}
