package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import kiln.Native;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/**
 * Kiln's stand-in for Fabric's {@code ClientEntityEvents}: an entity comes into or leaves the client's level. Kiln's
 * client level holds the player alone.
 */
public final class ClientEntityEvents {
    public static final Event<Load> ENTITY_LOAD = Event.kiln$of(listeners -> (entity, level) -> {
        for (Load l : listeners) {
            try {
                l.onLoad(entity, level);
            } catch (RuntimeException | LinkageError e) {
                Native.log("an ENTITY_LOAD listener threw " + e);
            }
        }
    });

    public static final Event<Unload> ENTITY_UNLOAD = Event.kiln$of(listeners -> (entity, level) -> {
        for (Unload l : listeners) {
            try {
                l.onUnload(entity, level);
            } catch (RuntimeException | LinkageError e) {
                Native.log("an ENTITY_UNLOAD listener threw " + e);
            }
        }
    });

    private ClientEntityEvents() {}

    @FunctionalInterface
    public interface Load {
        void onLoad(Entity entity, ClientLevel level);
    }

    @FunctionalInterface
    public interface Unload {
        void onUnload(Entity entity, ClientLevel level);
    }
}
