package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Kiln's stand-in for Fabric's {@code ServerEntityEvents}: an entity joined or left a server level. */
public final class ServerEntityEvents {
    public static final Event<Load> ENTITY_LOAD = Event.kiln$of(listeners -> (entity, level) -> {
        for (Load l : listeners) {
            l.onLoad(entity, level);
        }
    });
    public static final Event<Unload> ENTITY_UNLOAD = Event.kiln$of(listeners -> (entity, level) -> {
        for (Unload l : listeners) {
            l.onUnload(entity, level);
        }
    });

    private ServerEntityEvents() {}

    @FunctionalInterface
    public interface Load {
        void onLoad(Entity entity, ServerLevel level);
    }

    @FunctionalInterface
    public interface Unload {
        void onUnload(Entity entity, ServerLevel level);
    }
}
