package kiln.test.player;

import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * A mod for Kiln's tests of the server's player calls (kiln/tests/players.rs): a charm with a use of its own, a pebble
 * without one, a welcome for a player who comes into a level (Fabric's {@code ENTITY_LOAD}, which a respawn makes
 * too) and a line over the hotbar after a respawn. Written against Minecraft 26.3 and Fabric's API as any mod is;
 * built against Kiln's stand-ins.
 */
public final class TestPlayer implements ModInitializer {
    public static final String ID = "kiln_test_player";
    public static final Item CHARM = register("charm", Charm::new);
    public static final Item PEBBLE = register("pebble", Item::new);

    private static Item register(String name, Function<Item.Properties, Item> factory) {
        Identifier id = Identifier.fromNamespaceAndPath(ID, name);
        return Registry.register(BuiltInRegistries.ITEM, id,
                factory.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
    }

    @Override
    public void onInitialize() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("Welcome, " + player.getName().getString()));
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> newPlayer.sendOverlayMessage(
                Component.literal(alive ? "Back from the End" : "Back from the dead")));
    }
}
