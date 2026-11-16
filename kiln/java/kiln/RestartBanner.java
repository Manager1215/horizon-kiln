package kiln;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What the player is told once after the overlay's Restart Java now moved Java out of the game's process (D-134): the
 * game goes on, the first Java stays in the game's memory until the game closes (its size as it measured itself),
 * nothing is broken, and the game can be started again when it suits the player. "Got it" closes it; "Restart the
 * game" says {@code restart_game}, and the game starts itself again and closes. In a world it first asks, as anything
 * since the world was last saved may be lost.
 */
final class RestartBanner extends Screen {
    static final String TITLE = "Java restarted, the game goes on";

    /** The first Java's memory as it measured itself (committed heap and non-heap), in bytes; 0 when it couldn't. */
    private final long oldBytes;
    /** "Restart the game" was clicked in a world: the screen asks again before it does. */
    private boolean asking;

    RestartBanner(long oldBytes) {
        super(Component.literal(TITLE));
        this.oldBytes = oldBytes;
    }

    /** Opens the banner over the game. */
    static void open(long oldBytes) {
        Minecraft.getInstance().gui.setScreen(new RestartBanner(oldBytes));
    }

    /** The old Java's memory as the banner says it, in whole megabytes. */
    static String size(long bytes) {
        return bytes <= 0 ? "" : (bytes + (1 << 20) - 1) / (1 << 20) + " MB";
    }

    /** The banner's text, paragraph by paragraph. */
    static List<String> text(long oldBytes, boolean asking) {
        if (asking) {
            return List.of("Restart the game now?",
                    "You are in a world: anything since the world was last saved may be lost. To keep it, leave the"
                            + " world first, then restart from Kiln's overlay (Options > Mods > Java mods...).");
        }
        String size = size(oldBytes);
        return List.of("Your Java mods run in a new Java now, and the game goes on. Nothing is broken.",
                "The previous Java stays in the game's memory until you close the game" + (size.isEmpty() ? ""
                        : ": at least " + size) + ". It may load your computer a little.",
                "Restart the game when it suits you (the game only, not the computer), and only one Java runs again.");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        clearWidgets();
        int y = height / 2 + 40;
        if (asking) {
            addRenderableWidget(Button.builder(Component.literal("Restart now"), b -> Host.send("restart_game"))
                    .bounds(width / 2 - 124, y, 120, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
                asking = false;
                init();
            }).bounds(width / 2 + 4, y, 120, 20).build());
            return;
        }
        addRenderableWidget(Button.builder(Component.literal("Got it"), b -> onClose())
                .bounds(width / 2 - 124, y, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Restart the game"), b -> {
            if (Minecraft.getInstance().level != null) {
                asking = true;
                init();
            } else {
                Host.send("restart_game");
            }
        }).bounds(width / 2 + 4, y, 120, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int w = Math.min(width - 16, 340);
        int left = (width - w) / 2;
        List<String> lines = new ArrayList<>();
        for (String paragraph : text(oldBytes, asking)) {
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.addAll(wrap(font, paragraph, w - 16));
        }
        int top = height / 2 + 40 - 26 - lines.size() * 10;
        graphics.fill(0, 0, width, height, 0xA0000000);
        graphics.fill(left - 1, top - 23, left + w + 1, height / 2 + 69, 0xFF808080);
        graphics.fill(left, top - 22, left + w, height / 2 + 68, 0xFF202020);
        graphics.centeredText(font, asking ? "Restart the game" : TITLE, width / 2, top - 14, Overlay.YELLOW);
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), left + 8, top + 4 + i * 10, Overlay.WHITE);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** {@code text} in lines of at most {@code width} pixels, cut where words end. */
    static List<String> wrap(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (font.width(next) > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    @Override
    public String toString() {
        return "Kiln's restart banner";
    }
}
