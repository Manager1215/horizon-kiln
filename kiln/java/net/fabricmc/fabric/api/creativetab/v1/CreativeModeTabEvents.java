package net.fabricmc.fabric.api.creativetab.v1;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Kiln's stand-in for Fabric's {@code CreativeModeTabEvents}: a mod adds its items to the creative tabs. Kiln takes
 * the listeners and never calls them: Horizon's import of the mod puts its items in the tabs.
 */
public final class CreativeModeTabEvents {
    public static final Event<ModifyOutputAll> MODIFY_OUTPUT_ALL = Event.kiln$of(listeners -> (tab, output) -> {
        for (ModifyOutputAll l : listeners) {
            l.modifyOutput(tab, output);
        }
    });
    private static final Map<ResourceKey<CreativeModeTab>, Event<ModifyOutput>> EVENTS = new ConcurrentHashMap<>();

    private CreativeModeTabEvents() {}

    public static Event<ModifyOutput> modifyOutputEvent(ResourceKey<CreativeModeTab> tab) {
        return EVENTS.computeIfAbsent(tab, t -> Event.kiln$of(listeners -> output -> {
            for (ModifyOutput l : listeners) {
                l.modifyOutput(output);
            }
        }));
    }

    @FunctionalInterface
    public interface ModifyOutput {
        void modifyOutput(FabricCreativeModeTabOutput output);
    }

    @FunctionalInterface
    public interface ModifyOutputAll {
        void modifyOutput(CreativeModeTab tab, FabricCreativeModeTabOutput output);
    }
}
