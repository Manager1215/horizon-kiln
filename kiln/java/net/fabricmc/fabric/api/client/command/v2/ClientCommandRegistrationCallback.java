package net.fabricmc.fabric.api.client.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.commands.CommandBuildContext;

/**
 * Kiln's stand-in for Fabric's {@code ClientCommandRegistrationCallback}: the mods register their client commands,
 * each time a connection's commands are made (Kiln: when the window's world is joined). One mod's failure doesn't
 * keep the others' commands out.
 */
public interface ClientCommandRegistrationCallback {
    Event<ClientCommandRegistrationCallback> EVENT = Event.kiln$of(listeners -> (dispatcher, context) -> {
        for (ClientCommandRegistrationCallback listener : listeners) {
            try {
                listener.register(dispatcher, context);
            } catch (RuntimeException | LinkageError e) {
                kiln.Native.log("a mod's client commands failed: " + e);
            }
        }
    });

    void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context);
}
