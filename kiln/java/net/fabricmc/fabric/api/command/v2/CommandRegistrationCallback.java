package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Kiln's stand-in for Fabric's {@code CommandRegistrationCallback}: the mods register their server commands, each time
 * the server's commands are made (Kiln: when Horizon's server asks for the commands of its mods, as a world opens). One
 * mod's failure doesn't keep the others' commands out.
 */
public interface CommandRegistrationCallback {
    Event<CommandRegistrationCallback> EVENT = Event.kiln$of(listeners -> (dispatcher, context, environment) -> {
        for (CommandRegistrationCallback listener : listeners) {
            try {
                listener.register(dispatcher, context, environment);
            } catch (RuntimeException | LinkageError e) {
                kiln.Native.log("a mod's server commands threw " + e);
            }
        }
    });

    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context,
            Commands.CommandSelection environment);
}
