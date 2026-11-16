package kiln;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The window's game's container screens (its inventory, creative's, a chest's, a station's) as the mods see them
 * (D-156): vanilla's {@code AbstractContainerScreen}s of the screen's kind ({@code InventoryScreen}, {@code
 * CraftingScreen}, {@code ContainerScreen}...), placed where the game drew the window ({@code sin game <id> <menu> x y
 * w h slots}, from Horizon's screen hooks). After each client tick the open one draws as vanilla's draws, with the
 * mods' mixins and Fabric's drawing events in it, and what the mods drew (the game draws its window) goes to the game
 * ({@code odraw}) with where it takes the mouse: the boxes it drew outside the window, merged. The game leaves a click
 * there to the mods, and the keys while a mod's text field has the focus; Java hears all of the screen's input
 * ({@code sin over <kind> ...}) and gives it to the mods through Fabric's {@code allow...} events and the screen.
 */
public final class ContainerScreens {
    /** The game's screens these stand for, by the tick's name of them. */
    static final java.util.Set<String> NAMES = java.util.Set.of("inventory", "container", "station", "creative");
    /** Where the game drew the window and of which menu, as it last said. */
    private static String menu = "-";
    private static int[] rect;
    /** What the open one drew last, as sent (empty: nothing drawn). */
    private static String sent = "";
    /** Whether a mod's text field had the focus as the screen last drew. */
    private static boolean typing;

    private ContainerScreens() {}

    /** The game's container screen {@code name} with the menu it said: a stand-in of the kind vanilla opens for it. */
    static Screen make(Minecraft mc, String name) {
        Player player = mc.player;
        if (player == null) {
            return new GameScreen(name);
        }
        Component title = Component.empty();
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        AbstractContainerScreen<?> screen = switch (name) {
            case "creative" -> new CreativeModeInventoryScreen(player, FeatureFlagSet.of(), false);
            // The game's crafting table is its inventory screen with a 3x3 grid (D-166).
            case "inventory" -> menu.equals("minecraft:crafting") ? new CraftingScreen(new CraftingMenu(1, inv), inv, title)
                    : new InventoryScreen(player);
            default -> switch (menu) {
                case "minecraft:crafting" -> new CraftingScreen(new CraftingMenu(1, inv), inv, title);
                // The game's menus of the kinds a recipe viewer moves recipes into, of the very classes (JEI looks a
                // handler up by the menu's class and type) and with the player's inventory in their slots (D-167); the
                // menu's own slots show what the game's hold, as the tick says them (D-176).
                case "minecraft:furnace" -> new FurnaceScreen(new FurnaceMenu(1, inv, new Seen(inv, 3)), inv, title);
                case "minecraft:blast_furnace" -> new BlastFurnaceScreen(new BlastFurnaceMenu(1, inv, new Seen(inv, 3)), inv, title);
                case "minecraft:smoker" -> new SmokerScreen(new SmokerMenu(1, inv, new Seen(inv, 3)), inv, title);
                case "minecraft:brewing_stand" -> new BrewingStandScreen(new BrewingStandMenu(1, inv, new Seen(inv, 5)), inv, title);
                case "minecraft:anvil" -> new AnvilScreen(new AnvilMenu(1, inv, new Seen(inv, 2)), inv, title);
                case "minecraft:smithing" -> new SmithingScreen(new SmithingMenu(1, inv, new Seen(inv, 4)), inv, title);
                case "minecraft:grindstone" -> new GrindstoneScreen(new GrindstoneMenu(MenuType.GRINDSTONE, 1) {
                    @Override
                    public ItemStack quickMoveStack(Player p, int index) {
                        return ItemStack.EMPTY;
                    }

                    @Override
                    public boolean stillValid(Player p) {
                        return true;
                    }
                }, inv, title);
                case "minecraft:crafter_3x3" -> new CrafterScreen(new CrafterMenu(1, inv, new Seen(inv, 9)), inv, title);
                default -> menu.startsWith("minecraft:generic_9x")
                        ? new ContainerScreen(chest(inv, menu.charAt(menu.length() - 1) - '0'), inv, title)
                        : new Other(player.containerMenu, inv, title);
            };
        };
        place(screen);
        return screen;
    }

    /** A chest's menu of {@code rows} rows (the slots by number only: the game runs them). */
    private static ChestMenu chest(net.minecraft.world.entity.player.Inventory inv, int rows) {
        rows = Math.max(1, Math.min(6, rows));
        MenuType<?> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        return new ChestMenu(type, 1, inv, new Unseen(rows * 9), rows);
    }

    /**
     * A station's own slots as the game's client sees them (D-176): the tick puts them in the player's
     * {@code kiln$grid} (its cells 100 and up), read only; the game moves the items.
     */
    private record Seen(net.minecraft.world.entity.player.Inventory inv, int size) implements net.minecraft.world.Container {
        @Override
        public int getContainerSize() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            for (int slot = 0; slot < size; slot++) {
                if (!getItem(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < size && slot < inv.kiln$grid().getContainerSize() ? inv.kiln$grid().getItem(slot)
                    : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {}

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {}
    }

    /** A container whose stacks the game keeps: Kiln's client sees it empty. */
    private record Unseen(int size) implements net.minecraft.world.Container {
        @Override
        public int getContainerSize() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {}

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {}
    }

    /** A container screen Kiln knows no vanilla class of (a mod's menu, a beacon's): the screen as it is. */
    public static final class Other extends AbstractContainerScreen<AbstractContainerMenu> {
        Other(AbstractContainerMenu menu, net.minecraft.world.entity.player.Inventory inventory, Component title) {
            super(menu, inventory, title);
        }
    }

    /** The open screen where the game drew it, when Kiln knows. */
    private static void place(AbstractContainerScreen<?> screen) {
        if (rect != null) {
            screen.kiln$place(rect[0], rect[1], rect[2], rect[3]);
        }
    }

    /**
     * {@code sin game <id> <menu> x y w h slots}, or {@code sin game -}: where the game drew its container screen, of
     * which menu. Whether the open screen is to be made again (its menu is another than it was made for).
     */
    static boolean geometry(String[] f, Screen open) {
        if (f.length < 9 || f[2].equals("-")) {
            rect = null;
            return false;
        }
        String was = menu;
        menu = f[3];
        try {
            rect = new int[] {(int) Double.parseDouble(f[4]), (int) Double.parseDouble(f[5]),
                    (int) Double.parseDouble(f[6]), (int) Double.parseDouble(f[7])};
        } catch (NumberFormatException e) {
            rect = null;
        }
        if (open instanceof AbstractContainerScreen<?> screen) {
            place(screen);
        }
        boolean table = menu.equals("minecraft:crafting");
        if (f[2].equals("inventory") && table != was.equals("minecraft:crafting")) {
            // The inventory's 2x2 grid and the table's 3x3 are two screens of the same game screen (D-166).
            return open instanceof AbstractContainerScreen<?>;
        }
        return !was.equals(menu) && open instanceof AbstractContainerScreen<?>
                && !(open instanceof InventoryScreen || open instanceof CreativeModeInventoryScreen);
    }

    /** Input over the game's container screen ({@code sin over <kind> ...}), for the mods: Fabric's events first. */
    static void input(Screen screen, String[] f) {
        if (screen instanceof AbstractContainerScreen<?> s) {
            dispatch(s, f, 2, true);
        }
    }

    /**
     * Input for a screen as vanilla's {@code MouseHandler} and {@code KeyboardHandler} give it, through Fabric's
     * {@code allow}, {@code before} and {@code after} screen events around the screen's own handling (JEI's recipe
     * screen takes its clicks and keys only there). {@code f[at]} is the input's kind ({@code press x y button
     * modifiers}, {@code release}, {@code drag x y button dx dy modifiers}, {@code scroll x y dx dy}, {@code key <name>
     * modifiers}, {@code keyup}, {@code char <code point>}). Over the game's container screen ({@code gamesEscape})
     * Escape is the game's: it closes its window, and Kiln hears so with the next tick.
     */
    static void dispatch(Screen s, String[] f, int at, boolean gamesEscape) {
        // The input's own modifier keys, for the game's hasShiftDown and the like while it is handled (D-174).
        int mods = switch (f[at]) {
            case "press", "release", "drag" -> Integer.parseInt(f[f.length - 1]);
            case "key", "keyup", "char" -> f.length > at + 2 ? Integer.parseInt(f[at + 2]) : -1;
            default -> -1;
        };
        Screens.inputModifiers(mods);
        try {
            handle(s, f, at, gamesEscape);
        } finally {
            Screens.inputModifiers(-1);
        }
    }

    /** The last left/right press on a screen, for a double click (vanilla's: the same button within 250 ms). */
    private static Screen lastScreen;
    private static int lastButton = -1;
    private static long lastPress;

    private static boolean doubleClick(Screen s, int button) {
        long now = System.currentTimeMillis();
        boolean twice = s == lastScreen && button == lastButton && now - lastPress < 250;
        lastScreen = s;
        lastButton = button;
        lastPress = twice ? 0 : now;
        return twice;
    }

    private static void handle(Screen s, String[] f, int at, boolean gamesEscape) {
        AbstractContainerScreen<?> container = s instanceof AbstractContainerScreen<?> c ? c : null;
        switch (f[at]) {
            case "press", "release", "drag" -> {
                double x = Double.parseDouble(f[at + 1]);
                double y = Double.parseDouble(f[at + 2]);
                if (container != null) {
                    container.kiln$pointer(x, y);
                }
                int mods = Screens.modifiers(Integer.parseInt(f[f.length - 1]));
                MouseButtonEvent e = new MouseButtonEvent(x, y,
                        new MouseButtonInfo(Screens.button(Integer.parseInt(f[at + 3])), mods));
                if (f[at].equals("press")) {
                    if (ScreenMouseEvents.allowMouseClick(s).invoker().allowMouseClick(s, e)) {
                        ScreenMouseEvents.beforeMouseClick(s).invoker().beforeMouseClick(s, e);
                        boolean used = s.mouseClicked(e, doubleClick(s, e.button()));
                        ScreenMouseEvents.afterMouseClick(s).invoker().afterMouseClick(s, e, used);
                    }
                } else if (f[at].equals("release")) {
                    if (ScreenMouseEvents.allowMouseRelease(s).invoker().allowMouseRelease(s, e)) {
                        ScreenMouseEvents.beforeMouseRelease(s).invoker().beforeMouseRelease(s, e);
                        boolean used = s.mouseReleased(e);
                        ScreenMouseEvents.afterMouseRelease(s).invoker().afterMouseRelease(s, e, used);
                    }
                } else {
                    double dx = Double.parseDouble(f[at + 4]);
                    double dy = Double.parseDouble(f[at + 5]);
                    if (ScreenMouseEvents.allowMouseDrag(s).invoker().allowMouseDrag(s, e, dx, dy)) {
                        ScreenMouseEvents.beforeMouseDrag(s).invoker().beforeMouseDrag(s, e, dx, dy);
                        boolean used = s.mouseDragged(e, dx, dy);
                        ScreenMouseEvents.afterMouseDrag(s).invoker().afterMouseDrag(s, e, dx, dy, used);
                    }
                }
                s.afterMouseAction();
            }
            case "scroll" -> {
                double x = Double.parseDouble(f[at + 1]);
                double y = Double.parseDouble(f[at + 2]);
                double h = Double.parseDouble(f[at + 3]);
                double v = Double.parseDouble(f[at + 4]);
                if (container != null) {
                    container.kiln$pointer(x, y);
                }
                if (ScreenMouseEvents.allowMouseScroll(s).invoker().allowMouseScroll(s, x, y, h, v)) {
                    ScreenMouseEvents.beforeMouseScroll(s).invoker().beforeMouseScroll(s, x, y, h, v);
                    boolean used = s.mouseScrolled(x, y, h, v);
                    ScreenMouseEvents.afterMouseScroll(s).invoker().afterMouseScroll(s, x, y, h, v, used);
                }
                s.afterMouseAction();
            }
            case "key", "keyup" -> {
                int key = com.mojang.blaze3d.platform.InputConstants.getKey(f[at + 1]).getValue();
                KeyEvent e = new KeyEvent(key, key, Screens.modifiers(Integer.parseInt(f[at + 2])));
                if (f[at].equals("key")) {
                    if (ScreenKeyboardEvents.allowKeyPress(s).invoker().allowKeyPress(s, e)) {
                        ScreenKeyboardEvents.beforeKeyPress(s).invoker().beforeKeyPress(s, e);
                        if (!(gamesEscape && e.isEscape())) {
                            s.keyPressed(e);
                        }
                        ScreenKeyboardEvents.afterKeyPress(s).invoker().afterKeyPress(s, e);
                    }
                } else if (ScreenKeyboardEvents.allowKeyRelease(s).invoker().allowKeyRelease(s, e)) {
                    ScreenKeyboardEvents.beforeKeyRelease(s).invoker().beforeKeyRelease(s, e);
                    s.keyReleased(e);
                    ScreenKeyboardEvents.afterKeyRelease(s).invoker().afterKeyRelease(s, e);
                }
                s.afterKeyboardAction();
            }
            case "char" -> {
                CharacterEvent e = new CharacterEvent(Integer.parseInt(f[at + 1]));
                if (ScreenKeyboardEvents.allowCharType(s).invoker().allowCharType(s, e)) {
                    ScreenKeyboardEvents.beforeCharType(s).invoker().beforeCharType(s, e);
                    s.charTyped(e);
                    ScreenKeyboardEvents.afterCharType(s).invoker().afterCharType(s, e);
                }
                s.afterKeyboardAction();
            }
            default -> Native.log("unknown screen input " + f[at]);
        }
    }

    /**
     * After the client's tick: the game's container screen open draws as vanilla's does (Fabric's events around it,
     * the mods' mixins in it); what the mods drew goes to the game when it changed, with where it takes the mouse.
     */
    static void draw(Screen open, int width, int height) {
        if (!(open instanceof AbstractContainerScreen<?> screen)) {
            typing = false;
            if (!sent.isEmpty()) {
                sent = "";
                Host.send("odraw", "0", "");
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double scale = Math.max(1, mc.getWindow().getGuiScale());
        int mouseX = (int) (mc.mouseHandler.xpos() / scale);
        int mouseY = (int) (mc.mouseHandler.ypos() / scale);
        screen.kiln$pointer(mouseX, mouseY);
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(width, height);
        EditBox.kiln$focusDrawn = false;
        try {
            ScreenEvents.beforeExtract(screen).invoker().beforeExtract(screen, graphics, mouseX, mouseY, 0.0F);
            screen.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, 0.0F);
            ScreenEvents.afterExtract(screen).invoker().afterExtract(screen, graphics, mouseX, mouseY, 0.0F);
        } catch (RuntimeException | LinkageError e) {
            Native.log("the mods' drawing over the game's " + screen.getClass().getSimpleName() + " threw " + e);
            e.printStackTrace();
        }
        typing = EditBox.kiln$focusDrawn;
        List<String> ops = graphics.kiln$ops();
        String claims = claims(graphics.kiln$boxes(), screen, width, height);
        String now = (typing ? "1" : "0") + "\n" + claims + "\n" + String.join("\n", ops);
        if (!now.equals(sent)) {
            sent = now;
            List<String> fields = new ArrayList<>();
            fields.add(typing ? "1" : "0");
            fields.add(claims);
            fields.addAll(ops);
            Host.send("odraw", fields.toArray(String[]::new));
        }
    }

    /**
     * Where the mods' drawing takes the mouse from the game: the boxes it drew that reach outside the game's window
     * (not the window itself, not one as big as the screen: a dimming), merged where they touch, as {@code x,y,w,h;...}.
     */
    static String claims(List<float[]> boxes, AbstractContainerScreen<?> screen, int width, int height) {
        List<float[]> taken = new ArrayList<>();
        float wx0 = screen.leftPos;
        float wy0 = screen.topPos;
        float wx1 = wx0 + screen.imageWidth;
        float wy1 = wy0 + screen.imageHeight;
        for (float[] b : boxes) {
            boolean inWindow = b[0] >= wx0 && b[1] >= wy0 && b[2] <= wx1 && b[3] <= wy1;
            boolean screenWide = b[2] - b[0] >= width * 0.9F && b[3] - b[1] >= height * 0.9F;
            if (!inWindow && !screenWide && b[2] > b[0] && b[3] > b[1]) {
                taken.add(b.clone());
            }
        }
        // Boxes closer than a slot's gap join: an item list's icons make one box.
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int i = 0; i < taken.size() && !merged; i++) {
                for (int j = i + 1; j < taken.size(); j++) {
                    float[] a = taken.get(i);
                    float[] b = taken.get(j);
                    if (a[0] <= b[2] + 3 && b[0] <= a[2] + 3 && a[1] <= b[3] + 3 && b[1] <= a[3] + 3) {
                        a[0] = Math.min(a[0], b[0]);
                        a[1] = Math.min(a[1], b[1]);
                        a[2] = Math.max(a[2], b[2]);
                        a[3] = Math.max(a[3], b[3]);
                        taken.remove(j);
                        merged = true;
                        break;
                    }
                }
            }
        }
        StringBuilder out = new StringBuilder();
        for (float[] b : taken) {
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(num(b[0])).append(',').append(num(b[1])).append(',').append(num(b[2] - b[0])).append(',')
                    .append(num(b[3] - b[1]));
        }
        return out.toString();
    }

    private static String num(float v) {
        return v == (int) v ? Integer.toString((int) v) : Float.toString(v);
    }
}
