package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.check;
import static io.github.manager1215.kilntestbench.client.Game.expect;
import static io.github.manager1215.kilntestbench.client.Game.graphics;
import static io.github.manager1215.kilntestbench.client.Game.mc;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.HUD;

import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Probe;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector2f;

/** Drawing on the HUD with {@code GuiGraphicsExtractor}, for one frame, in the top left corner. */
final class HudProbes {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int X = 4;
    private static final int Y = 40;

    private HudProbes() {}

    static void add(Bench b) {
        b.add("hud.size", HUD, () -> new Probe() {
            public Object run() {
                // The HUD is the window, in GUI pixels.
                expect(graphics().guiWidth(), mc().getWindow().getGuiScaledWidth());
                expect(graphics().guiHeight(), mc().getWindow().getGuiScaledHeight());
                return graphics().guiWidth() + "x" + graphics().guiHeight();
            }
        });
        b.add("hud.fill", HUD, () -> new Probe() {
            public Object run() {
                graphics().fill(X, Y, X + 60, Y + 50, 0x40000000);
                return "drawn";
            }
        });
        b.add("hud.fillGradient", HUD, () -> new Probe() {
            public Object run() {
                graphics().fillGradient(X, Y, X + 4, Y + 4, 0x80FF0000, 0x800000FF);
                return "drawn";
            }
        });
        b.add("hud.outline", HUD, () -> new Probe() {
            public Object run() {
                graphics().outline(X, Y, 60, 50, WHITE);
                return "drawn";
            }
        });
        b.add("hud.lines", HUD, () -> new Probe() {
            public Object run() {
                graphics().horizontalLine(X, X + 60, Y + 25, WHITE);
                graphics().verticalLine(X + 30, Y, Y + 50, WHITE);
                return "drawn";
            }
        });
        b.add("hud.textString", HUD, () -> new Probe() {
            public Object run() {
                graphics().text(mc().font, "Kiln", X + 2, Y + 2, WHITE);
                return "drawn";
            }
        });
        b.add("hud.textShadow", HUD, () -> new Probe() {
            public Object run() {
                graphics().text(mc().font, "Kiln", X + 2, Y + 12, WHITE, true);
                return "drawn";
            }
        });
        b.add("hud.textComponent", HUD, () -> new Probe() {
            public Object run() {
                graphics().text(mc().font, Component.literal("Kiln"), X + 2, Y + 22, WHITE);
                return "drawn";
            }
        });
        b.add("hud.centeredText", HUD, () -> new Probe() {
            public Object run() {
                graphics().centeredText(mc().font, "Kiln", X + 30, Y + 32, WHITE);
                return "drawn";
            }
        });
        b.add("hud.wordWrap", HUD, () -> new Probe() {
            public Object run() {
                // Four words, none 40 wide, two never fit on a line together: one line each, lineHeight apart. It
                // answers where the next line would start.
                check(mc().font.width("Kiln runs") > 40, "two words fit in 40");
                return expect(graphics().textWithWordWrap(mc().font, Component.literal("Kiln runs Java mods"), X, Y + 52,
                        40, WHITE), Y + 52 + 4 * mc().font.lineHeight);
            }
        });
        b.add("hud.item", HUD, () -> new Probe() {
            public Object run() {
                graphics().item(new ItemStack(Items.DIAMOND), X + 40, Y + 2);
                return "drawn";
            }
        });
        b.add("hud.pose", HUD, () -> new Probe() {
            public Object run() {
                // One pose for the whole drawing: what is translated is drawn moved, and popping undoes it.
                Vector2f origin = graphics().pose().transformPosition(0, 0, new Vector2f());
                Vector2f step = graphics().pose().transformDirection(1, 1, new Vector2f());
                graphics().pose().pushMatrix();
                graphics().pose().translate(1, 1);
                Vector2f moved = graphics().pose().transformPosition(0, 0, new Vector2f());
                check(moved.distance(origin.x + step.x, origin.y + step.y) < 1e-4, "translated to " + moved);
                graphics().pose().popMatrix();
                check(graphics().pose().transformPosition(0, 0, new Vector2f()).distance(origin) < 1e-4, "not popped");
                return "pushed and popped";
            }
        });
        b.add("hud.scissor", HUD, () -> new Probe() {
            public Object run() {
                graphics().enableScissor(X, Y, X + 60, Y + 50);
                graphics().disableScissor();
                return "enabled and disabled";
            }
        });
        b.add("hud.deltaTracker", HUD, () -> new Probe() {
            public Object run() {
                return Game.within(Game.delta.getGameTimeDeltaPartialTick(true), 0, 1);
            }
        });
    }
}
