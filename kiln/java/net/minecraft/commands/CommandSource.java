package net.minecraft.commands;

import net.minecraft.network.chat.Component;

/** Kiln's stand-in for {@code CommandSource}: who a command's messages go to (a player, the console). */
public interface CommandSource {
    CommandSource NULL = new CommandSource() {
        @Override
        public void sendSystemMessage(Component message) {}

        @Override
        public boolean acceptsSuccess() {
            return false;
        }

        @Override
        public boolean acceptsFailure() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    };

    void sendSystemMessage(Component message);

    boolean acceptsSuccess();

    boolean acceptsFailure();

    boolean shouldInformAdmins();

    default boolean alwaysAccepts() {
        return false;
    }
}
