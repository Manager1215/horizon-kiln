package net.fabricmc.fabric.api.client.recipe.v1.sync;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes;
import net.minecraft.client.Minecraft;

/**
 * Kiln's stand-in for Fabric's {@code ClientRecipeSynchronizedEvent}: the client has the server's recipes. Kiln
 * fires it when a world is joined, with the recipes Kiln reads on the client's side ({@code kiln.ClientRecipes}).
 * A listener that throws is said in the log; the others still hear it.
 */
@FunctionalInterface
public interface ClientRecipeSynchronizedEvent {
    Event<ClientRecipeSynchronizedEvent> EVENT = Event.kiln$of(listeners -> (client, recipes) -> {
        for (ClientRecipeSynchronizedEvent l : listeners) {
            try {
                l.onRecipesSynchronized(client, recipes);
            } catch (RuntimeException | LinkageError e) {
                kiln.Native.log("a recipe sync listener threw " + e);
                e.printStackTrace();
            }
        }
    });

    void onRecipesSynchronized(Minecraft client, SynchronizedRecipes recipes);
}
