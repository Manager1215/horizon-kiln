package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.expect;
import static io.github.manager1215.kilntestbench.client.Game.mc;
import static io.github.manager1215.kilntestbench.client.Game.player;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.WORLD;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Probe;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/** The client: window, options, language, chat, sounds, input, the connection. */
final class ClientProbes {
    private ClientProbes() {}

    static void add(Bench b) {
        b.add("client.version", WORLD, () -> new Probe() {
            public Object run() {
                return mc().getLaunchedVersion();
            }
        });
        b.add("client.fps", WORLD, () -> new Probe() {
            public Object run() {
                return mc().getFps();
            }
        });
        b.add("client.isPaused", WORLD, () -> new Probe() {
            public Object run() {
                return expect(mc().isPaused(), false);
            }
        });
        b.add("client.singleplayer", WORLD, () -> new Probe() {
            public Object run() {
                return expect(mc().hasSingleplayerServer(), true);
            }
        });
        b.add("client.window", WORLD, () -> new Probe() {
            public Object run() {
                return mc().getWindow().getGuiScaledWidth() + "x" + mc().getWindow().getGuiScaledHeight() + " at "
                        + mc().getWindow().getGuiScale();
            }
        });
        b.add("client.options.fov", WORLD, () -> new Probe() {
            public Object run() {
                return mc().options.fov().get();
            }
        });
        b.add("client.options.renderDistance", WORLD, () -> new Probe() {
            public Object run() {
                return mc().options.renderDistance().get();
            }
        });
        b.add("client.options.keyJump", WORLD, () -> new Probe() {
            public Object run() {
                return mc().options.keyJump.getName() + " " + mc().options.keyJump.isDown();
            }
        });
        b.add("client.language", WORLD, () -> new Probe() {
            public Object run() {
                return mc().getLanguageManager().getSelected();
            }
        });
        b.add("client.translation", WORLD, () -> new Probe() {
            public Object run() {
                return Language.getInstance().getOrDefault("key.jump");
            }
        });
        b.add("client.user", WORLD, () -> new Probe() {
            public Object run() {
                return !mc().getUser().getName().isEmpty();
            }
        });
        b.add("client.font", WORLD, () -> new Probe() {
            public Object run() {
                return mc().font.width("Kiln") + " wide, " + mc().font.lineHeight + " high";
            }
        });
        b.add("client.screen", WORLD, () -> new Probe() {
            public Object run() {
                return expect(mc().gui.screen(), null);
            }
        });
        b.add("client.cameraEntity", WORLD, () -> new Probe() {
            public Object run() {
                return expect(mc().getCameraEntity() == player(), true);
            }
        });
        b.add("client.deltaTracker", WORLD, () -> new Probe() {
            public Object run() {
                return mc().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            }
        });
        b.add("client.mouse", WORLD, () -> new Probe() {
            public Object run() {
                return mc().mouseHandler.xpos() >= 0;
            }
        });
        b.add("client.inputKey", WORLD, () -> new Probe() {
            public Object run() {
                return expect(InputConstants.getKey("key.keyboard.j").getValue(), InputConstants.KEY_J);
            }
        });
        b.add("client.resource", WORLD, () -> new Probe() {
            public Object run() {
                return expect(mc().getResourceManager().getResource(Identifier.withDefaultNamespace("lang/en_us.json"))
                        .isPresent(), true);
            }
        });
        b.add("client.textureManager", WORLD, () -> new Probe() {
            public Object run() {
                return mc().getTextureManager() != null;
            }
        });
        b.add("client.chat", WORLD, () -> new Probe() {
            public Object run() {
                mc().gui.hud.getChat().addClientSystemMessage(Component.literal("[Kiln Testbench] chat probe"));
                return "added";
            }
        });
        b.add("client.overlayMessage", WORLD, () -> new Probe() {
            public Object run() {
                mc().gui.hud.setOverlayMessage(Component.literal("Kiln Testbench"), false);
                return "set";
            }
        });
        b.add("client.toast", WORLD, () -> new Probe() {
            public Object run() {
                SystemToast.add(mc().gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Kiln Testbench"), Component.literal("toast probe"));
                return "added";
            }
        });
        b.add("client.sound", WORLD, () -> new Probe() {
            public Object run() {
                return mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
            }
        });
        b.add("client.onlinePlayers", WORLD, () -> new Probe() {
            public Object run() {
                return mc().getConnection().getOnlinePlayers().size();
            }
        });
        b.add("client.sendCommand", WORLD, () -> new Probe() {
            public Object run() {
                // The server's reply is a game message: event.receiveGameMessage.fired checks it arrived.
                player().connection.sendCommand("help help");
                return "sent";
            }
        });
    }
}
