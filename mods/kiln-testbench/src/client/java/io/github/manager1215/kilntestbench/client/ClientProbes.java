package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.check;
import static io.github.manager1215.kilntestbench.client.Game.expect;
import static io.github.manager1215.kilntestbench.client.Game.mc;
import static io.github.manager1215.kilntestbench.client.Game.player;
import static io.github.manager1215.kilntestbench.client.Game.within;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.WORLD;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Probe;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.MouseHandler;
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
                String version = mc().getLaunchedVersion();
                check(!version.isEmpty(), "no version");
                return version;
            }
        });
        b.add("client.fps", WORLD, () -> new Probe() {
            public Object run() {
                // A second and more after the world opened, the window has drawn frames.
                int fps = mc().getFps();
                check(fps > 0, "no frames counted");
                return fps;
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
                Window w = mc().getWindow();
                check(w.getGuiScale() > 0 && w.getWidth() > 0 && w.getHeight() > 0, "no window");
                // A GUI pixel is getGuiScale() of the window's: the scaled size is the window's, divided and rounded up.
                expect(w.getGuiScaledWidth(), (w.getWidth() + w.getGuiScale() - 1) / w.getGuiScale());
                expect(w.getGuiScaledHeight(), (w.getHeight() + w.getGuiScale() - 1) / w.getGuiScale());
                return w.getGuiScaledWidth() + "x" + w.getGuiScaledHeight() + " at " + w.getGuiScale();
            }
        });
        b.add("client.options.fov", WORLD, () -> new Probe() {
            public Object run() {
                return (int) within(mc().options.fov().get(), 30, 110);
            }
        });
        b.add("client.options.renderDistance", WORLD, () -> new Probe() {
            public Object run() {
                return (int) within(mc().options.renderDistance().get(), 2, 32);
            }
        });
        b.add("client.options.keyJump", WORLD, () -> new Probe() {
            public Object run() {
                // Nobody holds Jump while the testbench runs.
                return expect(mc().options.keyJump.getName() + " " + mc().options.keyJump.isDown(), "key.jump false");
            }
        });
        b.add("client.language", WORLD, () -> new Probe() {
            public Object run() {
                String selected = mc().getLanguageManager().getSelected();
                check(selected.matches("[a-z]{2,3}_[a-z]{2,3}"), "not a language code: " + selected);
                return selected;
            }
        });
        b.add("client.translation", WORLD, () -> new Probe() {
            public Object run() {
                // A key nobody translates is itself; one the game does isn't.
                expect(Language.getInstance().getOrDefault("kiln_testbench.no.such.key"), "kiln_testbench.no.such.key");
                String jump = Language.getInstance().getOrDefault("key.jump");
                check(!jump.isEmpty(), "key.jump is empty");
                return jump;
            }
        });
        b.add("client.user", WORLD, () -> new Probe() {
            public Object run() {
                // The window's user is the player in the world.
                check(!mc().getUser().getName().isEmpty(), "no user name");
                expect(mc().getUser().getName(), player().getName().getString());
                return expect(mc().getUser().getProfileId(), player().getUUID()) != null;
            }
        });
        b.add("client.font", WORLD, () -> new Probe() {
            public Object run() {
                // A plain string is as wide as its letters, side by side.
                int wide = mc().font.width("Kiln");
                check(wide > 0, "no width");
                expect(wide, mc().font.width("K") + mc().font.width("i") + mc().font.width("l") + mc().font.width("n"));
                expect(mc().font.width(""), 0);
                return wide + " wide, " + expect(mc().font.lineHeight, 9) + " high";
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
                return within(mc().getDeltaTracker().getGameTimeDeltaPartialTick(true), 0, 1);
            }
        });
        b.add("client.mouse", WORLD, () -> new Probe() {
            public Object run() {
                // The pointer in the window's pixels, and in GUI pixels: the same place.
                Window w = mc().getWindow();
                double x = mc().mouseHandler.xpos();
                double y = mc().mouseHandler.ypos();
                check(Math.abs(MouseHandler.getScaledXPos(w, x) - x * w.getGuiScaledWidth() / w.getScreenWidth()) < 1e-6,
                        "x in GUI pixels");
                check(Math.abs(MouseHandler.getScaledYPos(w, y) - y * w.getGuiScaledHeight() / w.getScreenHeight()) < 1e-6,
                        "y in GUI pixels");
                expect(mc().mouseHandler.getScaledXPos(w), MouseHandler.getScaledXPos(w, x));
                expect(mc().mouseHandler.getScaledYPos(w), MouseHandler.getScaledYPos(w, y));
                return String.format(java.util.Locale.ROOT, "%.1f %.1f", x, y);
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
                return expect(mc().getTextureManager() != null, true);
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
                // The player is on the player list, in their game mode.
                var info = mc().getConnection().getPlayerInfo(player().getUUID());
                check(info != null, "the player isn't on the list");
                check(mc().getConnection().getOnlinePlayers().contains(info), "getOnlinePlayers lacks the player");
                expect(mc().getConnection().getOnlinePlayerIds().contains(player().getUUID()), true);
                expect(info.getGameMode().isCreative(), player().isCreative());
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
