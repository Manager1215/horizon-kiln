package net.fabricmc.fabric.api.client.message.v1;

import net.fabricmc.fabric.api.event.Event;

/**
 * Kiln's stand-in for Fabric's {@code ClientSendMessageEvents}: the window's player sends a chat message or a command.
 * The events keep their listeners and never fire: Horizon sends what the player types, and Kiln doesn't see it yet. A
 * mod that listens starts; what it does with what is sent doesn't happen.
 */
public final class ClientSendMessageEvents {
    public static final Event<AllowChat> ALLOW_CHAT = Event.kiln$of(listeners -> message -> {
        for (AllowChat l : listeners) { if (!l.allowSendChatMessage(message)) { return false; } } return true;
    });

    public static final Event<AllowCommand> ALLOW_COMMAND = Event.kiln$of(listeners -> message -> {
        for (AllowCommand l : listeners) { if (!l.allowSendCommandMessage(message)) { return false; } } return true;
    });

    public static final Event<ModifyChat> MODIFY_CHAT = Event.kiln$of(listeners -> message -> {
        for (ModifyChat l : listeners) { message = l.modifySendChatMessage(message); } return message;
    });

    public static final Event<ModifyCommand> MODIFY_COMMAND = Event.kiln$of(listeners -> message -> {
        for (ModifyCommand l : listeners) { message = l.modifySendCommandMessage(message); } return message;
    });

    public static final Event<Chat> CHAT = Event.kiln$of(listeners -> message -> {
        for (Chat l : listeners) { l.onSendChatMessage(message); }
    });

    public static final Event<Command> COMMAND = Event.kiln$of(listeners -> message -> {
        for (Command l : listeners) { l.onSendCommandMessage(message); }
    });

    public static final Event<ChatCanceled> CHAT_CANCELED = Event.kiln$of(listeners -> message -> {
        for (ChatCanceled l : listeners) { l.onSendChatMessageCanceled(message); }
    });

    public static final Event<CommandCanceled> COMMAND_CANCELED = Event.kiln$of(listeners -> message -> {
        for (CommandCanceled l : listeners) { l.onSendCommandMessageCanceled(message); }
    });

    private ClientSendMessageEvents() {}

    @FunctionalInterface
    public interface AllowChat {
        boolean allowSendChatMessage(String message);
    }

    @FunctionalInterface
    public interface AllowCommand {
        boolean allowSendCommandMessage(String message);
    }

    @FunctionalInterface
    public interface ModifyChat {
        String modifySendChatMessage(String message);
    }

    @FunctionalInterface
    public interface ModifyCommand {
        String modifySendCommandMessage(String message);
    }

    @FunctionalInterface
    public interface Chat {
        void onSendChatMessage(String message);
    }

    @FunctionalInterface
    public interface Command {
        void onSendCommandMessage(String message);
    }

    @FunctionalInterface
    public interface ChatCanceled {
        void onSendChatMessageCanceled(String message);
    }

    @FunctionalInterface
    public interface CommandCanceled {
        void onSendCommandMessageCanceled(String message);
    }
}
