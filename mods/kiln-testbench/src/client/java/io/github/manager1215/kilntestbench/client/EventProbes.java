package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.fired;
import static io.github.manager1215.kilntestbench.client.Game.heard;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.INIT;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.LATE;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Probe;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * The Fabric API's client events: each is registered at start, and most are checked later for having fired (a
 * listener that never runs is as good as a missing event).
 */
final class EventProbes {
    private EventProbes() {}

    static void add(Bench b) {
        b.add("event.startClientTick", INIT, () -> new Probe() {
            public Object run() {
                ClientTickEvents.START_CLIENT_TICK.register(client -> heard("startClientTick"));
                return "registered";
            }
        });
        b.add("event.endLevelTick", INIT, () -> new Probe() {
            public Object run() {
                ClientTickEvents.END_LEVEL_TICK.register(level -> heard("endLevelTick"));
                return "registered";
            }
        });
        b.add("event.clientStarted", INIT, () -> new Probe() {
            public Object run() {
                ClientLifecycleEvents.CLIENT_STARTED.register(client -> heard("clientStarted"));
                return "registered";
            }
        });
        b.add("event.join", INIT, () -> new Probe() {
            public Object run() {
                ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> heard("join"));
                return "registered";
            }
        });
        b.add("event.entityLoad", INIT, () -> new Probe() {
            public Object run() {
                ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> heard("entityLoad"));
                return "registered";
            }
        });
        b.add("event.chunkLoad", INIT, () -> new Probe() {
            public Object run() {
                ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> heard("chunkLoad"));
                return "registered";
            }
        });
        b.add("event.levelChange", INIT, () -> new Probe() {
            public Object run() {
                ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> heard("levelChange"));
                return "registered";
            }
        });
        b.add("event.screenAfterInit", INIT, () -> new Probe() {
            public Object run() {
                ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> heard("screenAfterInit"));
                return "registered";
            }
        });
        b.add("event.levelRender", INIT, () -> new Probe() {
            public Object run() {
                LevelRenderEvents.END_MAIN.register(context -> heard("levelRender"));
                return "registered";
            }
        });
        b.add("event.clientCommands", INIT, () -> new Probe() {
            public Object run() {
                ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
                    dispatcher.register(ClientCommands.literal(KilnTestbenchClient.ID).executes(c -> 1));
                    heard("clientCommands");
                });
                return "registered";
            }
        });
        b.add("event.receiveGameMessage", INIT, () -> new Probe() {
            public Object run() {
                ClientReceiveMessageEvents.GAME.register((message, overlay) -> heard("receiveGameMessage"));
                return "registered";
            }
        });
        b.add("event.itemTooltip", INIT, () -> new Probe() {
            public Object run() {
                ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> heard("itemTooltip"));
                return "registered";
            }
        });
        b.add("event.hudAfterHotbar", INIT, () -> new Probe() {
            public Object run() {
                HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                        Identifier.fromNamespaceAndPath(KilnTestbenchClient.ID, "after_hotbar"),
                        (graphics, delta) -> heard("hudAfterHotbar"));
                return "registered";
            }
        });
        b.add("event.keyMapping", INIT, () -> new Probe() {
            public Object run() {
                KeyMapping.Category category = KeyMapping.Category.register(
                        Identifier.fromNamespaceAndPath(KilnTestbenchClient.ID, "probes"));
                return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + KilnTestbenchClient.ID + ".probe",
                        InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category)).getName();
            }
        });

        for (String event : new String[] {"startClientTick", "endLevelTick", "clientStarted", "join", "entityLoad",
                "chunkLoad", "screenAfterInit", "levelRender", "clientCommands", "receiveGameMessage",
                "hudAfterHotbar"}) {
            b.add("event." + event + ".fired", LATE, () -> new Probe() {
                public Object run() {
                    return fired(event);
                }
            });
        }
    }
}
