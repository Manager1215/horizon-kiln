package net.fabricmc.fabric.api.message.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;

/**
 * Kiln's stand-in for Fabric's {@code ServerMessageEvents}: a player's chat message on the server. The events keep
 * their listeners and never fire: Horizon's server takes the chat, and Kiln hands mods no players' messages.
 */
public class ServerMessageEvents {
    public static final Event<AllowChatMessage> ALLOW_CHAT_MESSAGE = Event.kiln$of(listeners -> (message, sender, params) -> {
        for (AllowChatMessage l : listeners) {
            if (!l.allowChatMessage(message, sender, params)) {
                return false;
            }
        }
        return true;
    });

    public static final Event<ChatMessage> CHAT_MESSAGE = Event.kiln$of(listeners -> (message, sender, params) -> {
        for (ChatMessage l : listeners) {
            l.onChatMessage(message, sender, params);
        }
    });

    protected ServerMessageEvents() {}

    @FunctionalInterface
    public interface AllowChatMessage {
        boolean allowChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params);
    }

    @FunctionalInterface
    public interface ChatMessage {
        void onChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params);
    }
}
