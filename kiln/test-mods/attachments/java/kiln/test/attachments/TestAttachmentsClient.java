package kiln.test.attachments;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

/**
 * The player's window: each tick it reads the synced attachments the server sent (D-199), the player's own visits and
 * the level's clock, and writes in the chat when one changed.
 */
public final class TestAttachmentsClient implements ClientModInitializer {
    private static Integer greeted;
    private static Integer clock;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                return;
            }
            Integer nowGreeted = client.player.getAttached(TestAttachments.GREETED);
            if (nowGreeted != null && !nowGreeted.equals(greeted)) {
                greeted = nowGreeted;
                client.player.sendSystemMessage(Component.literal("Kiln Test Attachments: greeted " + greeted));
            }
            Integer nowClock = client.level.getAttached(TestAttachments.CLOCK);
            if (nowClock != null && !nowClock.equals(clock)) {
                clock = nowClock;
                client.player.sendSystemMessage(Component.literal("Kiln Test Attachments: clock " + clock));
            }
        });
    }
}
