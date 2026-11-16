import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Kiln's list widgets on a mod's screen, as the game drives one (D-094): opened by {@code Gui.setScreen}, the game's
 * input through {@code kiln.Screens.input} as it comes ({@code sin ...}), drawn by {@code kiln.Screens.tickMod}. Rows of
 * different heights are laid out, scrolled by the wheel and the scrollbar, selected by a click and the keys, and a
 * switch in a row is clicked. It prints a line a check and exits non-zero on the first that fails, for
 * kiln/tests/list_widget.rs. It runs as a source file with kiln-runtime.jar on the class path:
 * {@code java -cp kiln-runtime.jar ListWidget.java}. {@code Host.main} isn't running, so its output is set here.
 */
public class ListWidget {
    static final int WIDTH = 320;
    static final int HEIGHT = 240;
    static Method input;
    static Method tickMod;
    static Field drawn;

    public static void main(String[] args) throws Exception {
        Field out = Class.forName("kiln.Host").getDeclaredField("out");
        out.setAccessible(true);
        out.set(null, new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        Class<?> screens = Class.forName("kiln.Screens");
        Method size = screens.getDeclaredMethod("size", int.class, int.class);
        size.setAccessible(true);
        size.invoke(null, WIDTH, HEIGHT);
        input = screens.getDeclaredMethod("input", String[].class);
        input.setAccessible(true);
        tickMod = screens.getDeclaredMethod("tickMod");
        tickMod.setAccessible(true);
        drawn = screens.getDeclaredField("drawn");
        drawn.setAccessible(true);

        switches();
        selection();
        System.out.println("all checks passed");
    }

    /** Rows of 1 to 3 lines of text, each with a switch, scrolled by the wheel and the scrollbar; a switch clicked. */
    static void switches() throws Exception {
        SwitchScreen screen = new SwitchScreen();
        Minecraft.getInstance().gui.setScreen(screen);
        SwitchList list = screen.list;
        List<String> ops = draw();

        // 8 rows of 1, 2, 3, 1, 2, 3, 1, 2 lines: 9 pixels a line and 6 of padding, from 4 below the list's top (40).
        check(list.children().size() == 8, "8 rows");
        int[] tops = {44, 59, 83, 116, 131, 155, 188, 203};
        for (int i = 0; i < tops.length; i++) {
            check(list.getRowTop(i) == tops[i], "row " + i + " starts at " + tops[i] + ", not " + list.getRowTop(i));
        }
        check(list.maxScrollAmount() == 91, "the content is 191 high, 91 more than the list: " + list.maxScrollAmount());
        // Row 0's text at its content's corner (row 50 + 2, top 44 + 2); row 6 is below the list, not drawn.
        check(has(ops, "t 52 46 ", "row 0 line 0"), "row 0's text drawn at (52, 46): " + ops);
        check(!has(ops, "", "row 6 line 0"), "row 6, below the list, isn't drawn");
        check(has(ops, "c 0 40 320 140", ""), "the rows cut to the list: " + ops);
        System.out.println("laid out: 8 rows of 15 to 33 pixels, the content 191 high in a list 100 high");

        // The wheel down a notch: 12 pixels (half the default row's 24).
        send("scroll", "100", "60", "0", "-1");
        check(list.scrollAmount() == 12, "a notch scrolls 12: " + list.scrollAmount());
        check(list.getRowTop(0) == 32, "row 0 moved up 12: " + list.getRowTop(0));
        ops = draw();
        check(has(ops, "t 52 34 ", "row 0 line 0"), "row 0's text drawn 12 higher: " + ops);
        System.out.println("wheel: scrolled 12, rows moved with it");

        // The scrollbar (x 274 to 280): pressed and dragged down to the end.
        check(list.scrollBarY() == 46, "the bar at 46: " + list.scrollBarY());
        check(has(ops, "b 274 46 6 52 ", "widget/scroller"), "the bar drawn at (274, 46), 6 by 52: " + ops);
        send("press", "276", "50", "0", "0");
        send("drag", "276", "98", "0", "0", "48", "0");
        send("release", "276", "98", "0", "0");
        check(list.scrollAmount() == 91, "dragged to the end: " + list.scrollAmount());
        check(list.getRowBottom(7) == 136, "the last row ends 4 above the list's bottom: " + list.getRowBottom(7));
        send("drag", "276", "40", "0", "0", "-58", "0");
        check(list.scrollAmount() == 91, "released, a drag doesn't scroll: " + list.scrollAmount());
        System.out.println("scrollbar: dragged to the end, last row at the bottom");

        // Row 7's switch, drawn at its content's right (268 - 40, 112 + 2), 40 by 20: clicked.
        draw();
        SwitchRow row = list.children().get(7);
        check(row.toggle.getX() == 228 && row.toggle.getY() == 114, "the switch at (228, 114): " + row.toggle.getX()
                + ", " + row.toggle.getY());
        check(!row.on, "row 7 starts off");
        send("press", "240", "120", "0", "0");
        send("release", "240", "120", "0", "0");
        check(row.on, "the click switched row 7 on");
        check(list.getFocused() == row && row.getFocused() == row.toggle, "row 7 and its switch have the focus");
        check(screen.getFocused() == list, "the list has the screen's focus");
        check(list.getSelected() == null, "a row of widgets isn't selected");
        ops = draw();
        check(has(ops, "", "row 7: on"), "the switch's text drawn: " + ops);
        // Above the list, on row 0's switch hidden there: nothing.
        boolean before = list.children().get(0).on;
        send("press", "240", "30", "0", "0");
        send("release", "240", "30", "0", "0");
        check(list.children().get(0).on == before, "a click above the list reaches no hidden row");
        // Space presses the focused switch again.
        send("key", "key.keyboard.space", "0");
        check(!row.on, "Space on the focused switch switched row 7 off");
        System.out.println("child widget: row 7's switch clicked on, then off by Space");
    }

    /** Ten rows picked as a whole: one clicked, then the keys move the selection, scrolled into view. */
    static void selection() throws Exception {
        PickScreen screen = new PickScreen();
        Minecraft.getInstance().gui.setScreen(screen);
        PickList list = screen.list;
        draw();
        // Rows of 20 from 44: row 1 from 64 to 84.
        send("press", "100", "70", "0", "0");
        send("release", "100", "70", "0", "0");
        check(list.getSelected() == list.children().get(1), "the click selected row 1");
        List<String> ops = draw();
        check(has(ops, "f 50 64 220 20 4294967295", ""), "row 1 framed white (the list has the focus): " + ops);
        for (int i = 0; i < 6; i++) {
            send("key", "key.keyboard.down", "0");
        }
        check(list.getSelected() == list.children().get(7), "6 times down: row 7");
        check(list.getRowBottom(7) == 140 && list.getRowTop(7) >= 40, "row 7 scrolled into view, at the bottom: "
                + list.getRowTop(7) + " to " + list.getRowBottom(7));
        send("key", "key.keyboard.up", "0");
        check(list.getSelected() == list.children().get(6), "up: row 6");
        for (int i = 0; i < 12; i++) {
            send("key", "key.keyboard.up", "0");
        }
        check(list.getSelected() == list.children().get(0) && list.scrollAmount() == 0, "up past the top: row 0, "
                + "scrolled to the top: " + list.scrollAmount());
        // A click in the list but beside the rows' column selects nothing.
        send("press", "20", "70", "0", "0");
        send("release", "20", "70", "0", "0");
        check(list.getSelected() == list.children().get(0), "a click beside the rows keeps the selection");
        System.out.println("selection: clicked row 1, down to row 7 scrolled into view, up to row 0");
    }

    static void send(String... fields) throws Exception {
        String[] f = new String[fields.length + 1];
        f[0] = "sin";
        System.arraycopy(fields, 0, f, 1, fields.length);
        input.invoke(null, (Object) f);
    }

    @SuppressWarnings("unchecked")
    static List<String> draw() throws Exception {
        tickMod.invoke(null);
        return (List<String>) drawn.get(null);
    }

    static boolean has(List<String> ops, String start, String part) {
        return ops.stream().anyMatch(op -> op.startsWith(start) && op.contains(part));
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    /** A mod's screen with a list of rows holding switches: 320 wide, the list 100 high from 40. */
    static class SwitchScreen extends Screen {
        SwitchList list;

        SwitchScreen() {
            super(Component.literal("Switches"));
        }

        @Override
        protected void init() {
            list = addRenderableWidget(new SwitchList(Minecraft.getInstance(), width, 100, 40, 24));
            for (int i = 0; i < 8; i++) {
                List<String> lines = new ArrayList<>();
                for (int line = 0; line <= i % 3; line++) {
                    lines.add("row " + i + " line " + line);
                }
                list.add(new SwitchRow(i, lines), lines.size() * 9 + 6);
            }
        }
    }

    static class SwitchList extends ContainerObjectSelectionList<SwitchRow> {
        SwitchList(Minecraft minecraft, int width, int height, int y, int rowHeight) {
            super(minecraft, width, height, y, rowHeight);
        }

        void add(SwitchRow row, int height) {
            addEntry(row, height);
        }
    }

    /** A row: its lines of text, and a switch at its right. */
    static class SwitchRow extends ContainerObjectSelectionList.Entry<SwitchRow> {
        final int index;
        final List<String> lines;
        final Button toggle;
        boolean on;

        SwitchRow(int index, List<String> lines) {
            this.index = index;
            this.lines = lines;
            this.toggle = Button.builder(Component.literal("off"), b -> {
                on = !on;
                b.setMessage(Component.literal("row " + index + ": " + (on ? "on" : "off")));
            }).size(40, 20).build();
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(toggle);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
                float partialTick) {
            var font = Minecraft.getInstance().font;
            for (int i = 0; i < lines.size(); i++) {
                graphics.text(font, lines.get(i), getContentX(), getContentY() + i * 9, 0xFFFFFFFF);
            }
            toggle.setSize(40, Math.min(20, getContentHeight()));
            toggle.setPosition(getContentRight() - 40, getContentY());
            toggle.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    /** A mod's screen with a list of ten rows to pick from. */
    static class PickScreen extends Screen {
        PickList list;

        PickScreen() {
            super(Component.literal("Pick"));
        }

        @Override
        protected void init() {
            list = addRenderableWidget(new PickList(Minecraft.getInstance(), width, 100, 40, 20));
            for (int i = 0; i < 10; i++) {
                list.add(new PickRow("pick " + i));
            }
        }
    }

    static class PickList extends ObjectSelectionList<PickRow> {
        PickList(Minecraft minecraft, int width, int height, int y, int rowHeight) {
            super(minecraft, width, height, y, rowHeight);
        }

        void add(PickRow row) {
            addEntry(row);
        }
    }

    static class PickRow extends ObjectSelectionList.Entry<PickRow> {
        final String name;

        PickRow(String name) {
            this.name = name;
        }

        @Override
        public Component getNarration() {
            return Component.literal(name);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
                float partialTick) {
            graphics.text(Minecraft.getInstance().font, name, getContentX(), getContentY(), 0xFFFFFFFF);
        }
    }
}
