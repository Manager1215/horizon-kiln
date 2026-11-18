package kiln.test.handshake;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

/**
 * The window's side: each hello heard in the configuration is kept and thanked (its number doubled); the welcome, in
 * play, writes in the chat the hellos heard before it.
 */
public final class TestHandshakeClient implements ClientModInitializer {
    private static final List<Integer> HEARD = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        ClientConfigurationNetworking.registerGlobalReceiver(Hello.TYPE, (hello, context) -> {
            synchronized (HEARD) {
                HEARD.add(hello.number());
            }
            context.responseSender().sendPacket(new Thanks(hello.number() * 2));
        });
        ClientPlayNetworking.registerGlobalReceiver(Welcome.TYPE, (welcome, context) -> {
            synchronized (HEARD) {
                context.player().sendSystemMessage(Component.literal("Kiln Test Handshake: " + welcome.word()
                        + " after hellos " + HEARD));
            }
        });
    }
}
