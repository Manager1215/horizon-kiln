package net.fabricmc.fabric.api.client.message.v1;

import kiln.Native;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.network.chat.Component;

/**
 * Kiln's stand-in for Fabric's {@code ClientReceiveMessageEvents}: a message from the game (a command's answer, a
 * server's notice) came to the window's chat. Kiln tells it with the next tick, as plain text, once the game has shown
 * it: a listener can't stop or change it. Not the lines mods wrote there themselves, nor players' messages.
 */
public final class ClientReceiveMessageEvents {
    public static final Event<Game> GAME = Event.kiln$of(listeners -> (message, overlay) -> {
        for (Game l : listeners) {
            try {
                l.onReceiveGameMessage(message, overlay);
            } catch (RuntimeException | LinkageError e) {
                Native.log("a GAME message listener threw " + e);
            }
        }
    });

    private ClientReceiveMessageEvents() {}

    @FunctionalInterface
    public interface Game {
        void onReceiveGameMessage(Component message, boolean overlay);
    }
}
