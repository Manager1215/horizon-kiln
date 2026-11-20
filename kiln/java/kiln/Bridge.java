package kiln;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Kiln's Mixin bridge (D-102): the game's methods the mods' mixins go into, called where Horizon runs the game's code.
 * Horizon is the game, in Rust: its code never calls a stand-in, so a mixin in one runs only where Kiln calls it.
 * Horizon's native hooks are those places, frozen once merged; the table {@code kiln/bridge.json} says, for each, which
 * of the game's methods to call there, with what, and what of it to give back. A new point is a new row, not new code.
 *
 * <p>Kiln's Rust hands Java a hook's parameters as fields, in the hook's order: {@code hook <name> <fields>...} from
 * the window (nothing waits for it), {@code bridge <call> <world> <game time> <hook> <n> <fields>... [<player>]} from
 * the server, which waits and gets {@code out <name> <value>} effects back. Only the hooks of rows some mod's mixin goes
 * into are forwarded: Java says which ({@code bridge <hook>...}) as the mods load.
 *
 * <p>Whether the method's own code ran is how a cancelled call shows: the body of a stand-in method on the bridge is
 * Kiln's (a generated one's {@link Missing#called}, or {@link #body}), and a mixin that cancels at the method's head
 * skips it. A method a mixin overwrites has the mod's code for body: its row runs the mixins and gives nothing back.
 */
public final class Bridge {
    /** One row of the table. */
    record Row(String id, String by, String hook, Map<String, String> when, String side, String className,
            String method, String desc, String self, List<String> args, Map<String, String> out, String at,
            String whilst, String undo, int each, String late, List<String> also) {}

    private static final List<Row> ROWS = load();
    /** Each row's mixins, by row id, worked out once the mods' configs are in (an empty set: none). */
    private static final Map<String, Set<String>> MIXED = new ConcurrentHashMap<>();
    private static final Set<String> SAID = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static List<String> announced;
    /** The living entities hit, by Horizon's id: Kiln's own, so a mixin's fields on one keep from hit to hit. */
    private static final Map<Integer, LivingEntity> LIVING = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, LivingEntity> eldest) {
                    return size() > 1024;
                }
            });

    /** A bridge call under way: the method called, whether its own code ran, and the arguments it got. */
    private static final class Frame {
        final String method;
        boolean ran;
        Object[] seen;

        Frame(String method) {
            this.method = method;
        }
    }

    private Bridge() {}

    // The table.

    private static List<Row> load() {
        try (InputStream in = Bridge.class.getResourceAsStream("/kiln/bridge.json")) {
            if (in == null) {
                return List.of();
            }
            return rows(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            Host.send("log", "Kiln's Mixin bridge table didn't read: " + e);
            return List.of();
        }
    }

    /** The table's rows, as written. */
    static List<Row> rows(JsonObject table) {
        List<Row> rows = new ArrayList<>();
        for (JsonElement e : table.getAsJsonArray("rows")) {
            JsonObject r = e.getAsJsonObject();
            List<String> args = new ArrayList<>();
            if (r.has("args")) {
                for (JsonElement a : r.getAsJsonArray("args")) {
                    args.add(a.getAsString());
                }
            }
            rows.add(new Row(text(r, "id"), text(r, "by"), text(r, "hook"), strings(r, "when"), text(r, "side"),
                    text(r, "class"), text(r, "method"), text(r, "desc"), text(r, "this"), List.copyOf(args),
                    strings(r, "out"), text(r, "at"), text(r, "while"), text(r, "undo"),
                    r.has("each") ? r.get("each").getAsInt() : 0, text(r, "late"), list(r, "also")));
        }
        return List.copyOf(rows);
    }

    private static List<String> list(JsonObject o, String key) {
        List<String> list = new ArrayList<>();
        if (o.has(key)) {
            o.getAsJsonArray(key).forEach(e -> list.add(e.getAsString()));
        }
        return List.copyOf(list);
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static Map<String, String> strings(JsonObject o, String key) {
        Map<String, String> map = new LinkedHashMap<>();
        if (o.has(key)) {
            o.getAsJsonObject(key).entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
        }
        return Collections.unmodifiableMap(map);
    }

    /** The table's rows. */
    public static List<Row> rows() {
        return ROWS;
    }

    // Which rows the mods' mixins go into.

    /** The mixins in a row's method (its class's or a subclass's), by name; worked out once. */
    static Set<String> mixed(Row row) {
        return MIXED.computeIfAbsent(row.id(), id -> {
            Set<String> found = new java.util.TreeSet<>(Mixins.mixinsInto(row.className(), row.method(), row.desc(),
                    row.each() == 0));
            // 'also' (D-202): a method the row's method calls whose mixins change what it works out
            // (Enchantment.areCompatible in the anvil's result), {@code class.method(desc)}.
            for (String also : row.also()) {
                int open = also.indexOf('(');
                int dot = also.lastIndexOf('.', open);
                if (open > 0 && dot > 0) {
                    found.addAll(Mixins.mixinsInto(also.substring(0, dot), also.substring(dot + 1, open),
                            also.substring(open), false));
                }
            }
            return java.util.Collections.unmodifiableSet(found);
        });
    }

    /** Whether some mod's mixin goes into the method of row {@code id}. */
    public static boolean active(String id) {
        for (Row row : ROWS) {
            if (row.id().equals(id)) {
                return !mixed(row).isEmpty();
            }
        }
        return false;
    }

    /**
     * Whether a row runs: a mod's mixin goes into its method, or it is a row with a late answer ({@code late}), whose
     * method gives what the mods' own code adds too (a mod's item's tooltip lines, Fabric's callback in it).
     */
    static boolean runs(Row row) {
        return row.late() != null || !mixed(row).isEmpty();
    }

    /** The hooks Kiln's Rust is to forward: those of rows a mod's mixin goes into, in the table's order. */
    static List<String> hooks() {
        Set<String> hooks = new LinkedHashSet<>();
        for (Row row : ROWS) {
            if ("hook".equals(row.by()) && runs(row)) {
                hooks.add(row.hook());
            }
        }
        return List.copyOf(hooks);
    }

    /** Kiln's own calls ({@code at}) the server is to make for a row a mod's mixin goes into ({@code connect}...). */
    static void heard(List<String> events) {
        for (Row row : ROWS) {
            if ("kiln".equals(row.by()) && "server".equals(row.side()) && row.at() != null
                    && !events.contains(row.at()) && !mixed(row).isEmpty()) {
                events.add(row.at());
            }
        }
    }

    /** After a mod loads: the hooks to forward, when they changed, and in Kiln's log the rows with their mixins. */
    static void announce() {
        List<String> hooks = hooks();
        if (hooks.equals(announced)) {
            return;
        }
        announced = hooks;
        Host.send("bridge", hooks.toArray(String[]::new));
        for (Row row : ROWS) {
            Set<String> mixins = mixed(row);
            if (!mixins.isEmpty() && SAID.add("row " + row.id())) {
                Host.send("log", "Kiln's Mixin bridge runs " + simple(row.className()) + "." + row.method() + " at "
                        + (row.by().equals("hook") ? "Horizon's " + row.hook() + " hook"
                        : row.by().equals("tick") ? "each client tick, back to Horizon" : row.at()) + " for "
                        + String.join(", ", mixins));
            }
        }
    }

    // The calls.

    /** Kiln's camera: Horizon's is its own; mods' mixins in {@code Camera.calculateFov} run on this one. */
    private static final net.minecraft.client.Camera CAMERA = new net.minecraft.client.Camera();

    /**
     * After a client tick in a world: the {@code tick} rows a mod's mixin goes into, called, and their outs as the
     * values Horizon takes back (kiln.Back's keys).
     */
    static Map<String, String> ticked() {
        Map<String, String> back = new LinkedHashMap<>();
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        for (Row row : ROWS) {
            if (!"tick".equals(row.by()) || mixed(row).isEmpty() || player == null) {
                continue;
            }
            if ("flying".equals(row.whilst()) && !player.getAbilities().flying) {
                continue;
            }
            run(row, new String[0], null, null).forEach((key, value) -> {
                if ("sprint".equals(row.undo()) && player.isSprinting()) {
                    try {
                        value = String.valueOf(Float.parseFloat(value) / 2);
                    } catch (NumberFormatException e) {
                        return;
                    }
                }
                back.put(key, value);
            });
        }
        return back;
    }

    /** The window's hook {@code f[1]} with its fields: its rows run, nothing goes back (the window doesn't wait). */
    static void client(String[] f) {
        String[] fields = java.util.Arrays.copyOfRange(f, 2, f.length);
        for (Row row : ROWS) {
            if ("hook".equals(row.by()) && row.hook().equals(f[1]) && "client".equals(row.side()) && wanted(row, fields)) {
                run(row, fields, null, null);
                if (row.late() != null) {
                    late(row, fields, RETURNED.get());
                }
            }
            RETURNED.remove();
        }
    }

    /**
     * The server's hook, in a server call: {@code f[4]} the hook, {@code f[5]} how many fields it has, then they,
     * then a player when the hook is about one. Each row's outs go back as {@code out <name> <value>} effects. A row
     * with {@code each} is a batch: the fields are a level's dimension and then a group of {@code each} for each call
     * (an entity's id and kind), its {@code $N} counted in the group, and it gives nothing back.
     */
    static String server(String[] f, long gameTime, net.minecraft.world.level.storage.SavedDataStorage storage,
            java.util.function.BiFunction<String[], Integer, ServerPlayer> playerAt) {
        String hook = f[4];
        int n = Integer.parseInt(f[5]);
        String[] fields = java.util.Arrays.copyOfRange(f, 6, 6 + n);
        ServerPlayer player = f.length >= 6 + n + Server.PLAYER ? playerAt.apply(f, 6 + n) : null;
        ServerLevel level = player != null ? player.level() : new ServerLevel("minecraft:overworld", gameTime, storage);
        Map<String, String> outs = new LinkedHashMap<>();
        ServerLevel batchLevel = null;
        for (Row row : ROWS) {
            if (row.each() > 0 && "hook".equals(row.by()) && row.hook().equals(hook) && "server".equals(row.side())
                    && !mixed(row).isEmpty() && fields.length > 0) {
                // A batch (D-145): the level's dimension, then a group of fields for each call, one call each.
                if (batchLevel == null) {
                    batchLevel = new ServerLevel(fields[0], gameTime, storage);
                }
                // A batch with outs (D-203): each out is the groups' values, "<group>=<value>" comma-separated.
                Map<String, StringBuilder> each = new LinkedHashMap<>();
                for (int i = 1, g = 0; i + row.each() <= fields.length; i += row.each(), g++) {
                    String[] group = java.util.Arrays.copyOfRange(fields, i, i + row.each());
                    if (wanted(row, group)) {
                        int at = g;
                        run(row, group, null, batchLevel).forEach((name, value) -> {
                            StringBuilder list = each.computeIfAbsent(name, k -> new StringBuilder());
                            list.append(list.isEmpty() ? "" : ",").append(at).append('=').append(value);
                        });
                    }
                }
                each.forEach((name, list) -> outs.put(name, list.toString()));
                continue;
            }
            if (row.each() == 0 && "hook".equals(row.by()) && row.hook().equals(hook) && "server".equals(row.side())
                    && wanted(row, fields)) {
                // Several rows on one hook: a flag any of them set stays set, a value is the last row's.
                run(row, fields, player, level).forEach((name, value) -> outs.merge(name, value,
                        (a, b) -> isFlag(a, b) ? (a.equals("1") || b.equals("1") ? "1" : "0") : b));
            }
        }
        outs.forEach((name, value) -> Server.effect("out " + name + " " + value));
        return "pass";
    }

    private static boolean isFlag(String a, String b) {
        return (a.equals("0") || a.equals("1")) && (b.equals("0") || b.equals("1"));
    }

    /** Whether the row is for these fields ({@code when}: field index to the value it must have). */
    private static boolean wanted(Row row, String[] fields) {
        for (Map.Entry<String, String> w : row.when().entrySet()) {
            int i = Integer.parseInt(w.getKey());
            if (i >= fields.length || !fields[i].equals(w.getValue())) {
                return false;
            }
        }
        return runs(row);
    }

    /** What the last {@link #run} on this thread got back from the method (a late answer's). */
    private static final ThreadLocal<Object> RETURNED = new ThreadLocal<>();

    /**
     * A client row's late answer (D-145): what its method gave back, to Horizon with the next tick's replies
     * (kiln.Back), for the hook's next time. {@code tooltip}: the stack's key, then its first line when the mods' code
     * changed the name (else {@code null}), then the lines past it, each as vanilla's JSON, separated by U+001F. The
     * game's lines Kiln put in the list (D-218) are left out while every one of them is still there, Horizon showing
     * its own; when the mods' code changed or removed one, the key has a {@code !} before it and the lines are all of
     * the list's past the name, which stand for Horizon's.
     */
    static void late(Row row, String[] fields, Object returned) {
        if (!"tooltip".equals(row.late()) || fields.length == 0) {
            return;
        }
        List<?> lines = returned instanceof List<?> l ? l : List.of();
        List<net.minecraft.network.chat.Component> game = ItemTooltips.takeGameLines();
        boolean whole = false;
        if (game != null && !game.isEmpty()) {
            java.util.Set<Object> there = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            there.addAll(lines);
            whole = !there.containsAll(game);
            if (!whole) {
                java.util.Set<Object> ours = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                ours.addAll(game);
                List<Object> past = new ArrayList<>();
                for (Object line : lines) {
                    if (!ours.contains(line)) {
                        past.add(line);
                    }
                }
                lines = past;
            }
        }
        StringBuilder answer = new StringBuilder(whole ? "!" : "").append(fields[0]);
        Object name = net.minecraft.world.item.ItemStack.kiln$fromSnbt(fields[0]).getHoverName();
        for (int i = 0; i < Math.max(1, lines.size()); i++) {
            Object line = i < lines.size() ? lines.get(i) : null;
            boolean same = i == 0 && (line == null || line instanceof net.minecraft.network.chat.Component c
                    && name instanceof net.minecraft.network.chat.Component n && c.getString().equals(n.getString()));
            answer.append('\u001f').append(same || !(line instanceof net.minecraft.network.chat.Component c2) ? "null"
                    : c2.kiln$json().toString());
        }
        Back.act("tooltip", answer.toString());
    }

    /** Calls the row's method with what its {@code this} and {@code args} make of the fields; its outs. */
    static Map<String, String> run(Row row, String[] fields, ServerPlayer player, ServerLevel level) {
        Map<String, String> outs = new LinkedHashMap<>();
        Method method;
        Object self;
        Object[] args;
        try {
            Class<?> owner = Class.forName(row.className());
            method = find(owner, row.method(), row.desc());
            if (method == null) {
                sayOnce(row, "Kiln's stand-in " + simple(row.className()) + " has no " + row.method() + row.desc());
                return outs;
            }
            method.setAccessible(true);
            self = java.lang.reflect.Modifier.isStatic(method.getModifiers()) ? null
                    : value(row.self(), fields, player, level);
            if (self == null && !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                return outs;
            }
            args = new Object[row.args().size()];
            for (int i = 0; i < args.length; i++) {
                args[i] = value(row.args().get(i), fields, player, level);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            sayOnce(row, "Kiln's Mixin bridge couldn't make the call of " + row.id() + ": " + e);
            return outs;
        }
        Frame frame = new Frame(row.method());
        FRAMES.get().push(frame);
        Object returned;
        try {
            returned = method.invoke(self, args);
            RETURNED.set(returned);
        } catch (InvocationTargetException e) {
            // A mod's handler that throws is guarded where it runs (MixinGuards); what's left is the method's own.
            Mixins.threw(e.getCause(), simple(row.className()) + "." + row.method() + " on Kiln's Mixin bridge");
            return outs;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            sayOnce(row, "Kiln's Mixin bridge couldn't call " + row.id() + ": " + e);
            return outs;
        } finally {
            FRAMES.get().pop();
        }
        // A method a mod overwrote has the mod's code for body: whether "it ran", and what it got, say nothing.
        boolean overwritten = mixed(row).stream().anyMatch(m -> m.endsWith(" (overwrite)"));
        for (Map.Entry<String, String> o : row.out().entrySet()) {
            String spec = o.getValue();
            if (overwritten && !spec.equals("return") && !spec.startsWith("this:")) {
                continue;
            }
            if (spec.startsWith("this:") && self != null) {
                // What the object says after the call, by a method of Kiln's own on it (D-202: the anvil's result).
                try {
                    outs.put(o.getKey(), String.valueOf(self.getClass().getMethod(spec.substring(5)).invoke(self)));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    sayOnce(row, "Kiln's Mixin bridge couldn't read " + spec + " of " + row.id() + ": " + e);
                }
            } else if (spec.equals("skipped")) {
                outs.put(o.getKey(), frame.ran ? "0" : "1");
            } else if (spec.equals("return")) {
                outs.put(o.getKey(), String.valueOf(returned));
            } else if (spec.startsWith("arg:") && frame.ran) {
                int i = Integer.parseInt(spec.substring(4));
                Object v = frame.seen != null && i < frame.seen.length ? frame.seen[i] : args[i];
                outs.put(o.getKey(), String.valueOf(v));
            }
        }
        return outs;
    }

    /** The method of {@code owner} (or a class it extends) with that name and descriptor. */
    private static Method find(Class<?> owner, String name, String desc) {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && org.objectweb.asm.Type.getMethodDescriptor(m).equals(desc)) {
                    return m;
                }
            }
        }
        return null;
    }

    // The bodies of the methods on the bridge.

    /**
     * The body of a stand-in method on the bridge: it ran, with these arguments (as the mixins that change them left
     * them); gives back {@code value}. Called by Kiln's own body, outside a bridge call too (a mod's own call).
     */
    public static <T> T body(T value, Object... seen) {
        Deque<Frame> frames = FRAMES.get();
        Frame frame = frames.peek();
        if (frame != null) {
            frame.ran = true;
            frame.seen = seen;
        }
        return value;
    }

    /** Whether the bridge call under way on this thread is of a method named {@code method} (D-164: the tooltip row). */
    public static boolean calling(String method) {
        Frame frame = FRAMES.get().peek();
        return frame != null && frame.method.equals(method);
    }

    /**
     * A generated stand-in's body ({@link Missing#called}) ran: when it is the method of the bridge call under way,
     * that is the method's own code running (whether a mixin cancelled it). Whether it was, so it isn't said missing.
     */
    static boolean missing(String what) {
        Frame frame = FRAMES.get().peek();
        if (frame != null && what.endsWith("." + frame.method)) {
            frame.ran = true;
            return true;
        }
        return false;
    }

    // What the fields make.

    /** The value a row's spec makes of the hook's fields. */
    static Object value(String spec, String[] fields, ServerPlayer player, ServerLevel level) {
        String[] p = spec.split(":", -1);
        for (int i = 1; i < p.length; i++) {
            if (p[i].startsWith("$")) {
                int at = Integer.parseInt(p[i].substring(1));
                p[i] = at < fields.length ? fields[at] : "";
            }
        }
        return switch (p[0]) {
            case "null" -> null;
            case "int" -> Integer.parseInt(p[1]);
            case "long" -> Long.parseLong(p[1]);
            case "float" -> Float.parseFloat(p[1]);
            case "double" -> Double.parseDouble(p[1]);
            case "bool" -> p[1].equals("1") || p[1].equals("true");
            case "string" -> p[1];
            case "minecraft" -> Minecraft.getInstance();
            case "camera" -> CAMERA;
            case "local_player" -> Minecraft.getInstance().player;
            case "mouse" -> Minecraft.getInstance().mouseHandler;
            case "keyboard" -> Minecraft.getInstance().kiln$keyboard();
            case "player" -> player;
            case "level" -> level;
            case "entity" -> entity(Integer.parseInt(p[1]), p[2], player, level);
            case "other" -> other(Integer.parseInt(p[1]), p[2], level);
            case "damage" -> p.length == 5 ? damage(p[1] + ":" + p[2], p[3], p[4], player, level)
                    : damage(p[1], p[2], p[3], player, level);
            case "death" -> damage(deathType(p[1]), "0", "0", player, level);
            case "key_event" -> keyEvent(p[1]);
            case "stack" -> net.minecraft.world.item.ItemStack.kiln$fromSnbt(p[1]);
            // An item on the ground at the call's player, its item and count (D-200: item_picked_up).
            // An experience orb at the call's player, its value and count (D-200: experience_orb_pickup).
            case "orb" -> new net.minecraft.world.entity.ExperienceOrb(level, player == null ? 0 : player.getX(),
                    player == null ? 0 : player.getY(), player == null ? 0 : player.getZ(), Integer.parseInt(p[1]))
                    .kiln$orb(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            case "item_entity" -> new net.minecraft.world.entity.item.ItemEntity(level, player == null ? 0 : player.getX(),
                    player == null ? 0 : player.getY(), player == null ? 0 : player.getZ(),
                    net.minecraft.world.item.ItemStack.kiln$fromSnbt("{id:\"" + p[1] + "\",count:" + p[2] + "}"));
            // The anvil the call's player has open: its inputs, and the result and cost Horizon worked out (D-202).
            // The player's game mode, and a block position (D-204: destroy_block).
            case "game_mode" -> new net.minecraft.server.level.ServerPlayerGameMode(player);
            case "block_pos" -> new net.minecraft.core.BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                    Integer.parseInt(p[3]));
            // The hand a use is made with ("main", "off"), the stack the call's player holds in it, and the block a
            // use aims at, its position and face (D-229: use_item, use_item_on).
            case "hand" -> p[1].equals("off") ? net.minecraft.world.InteractionHand.OFF_HAND
                    : net.minecraft.world.InteractionHand.MAIN_HAND;
            case "held" -> player == null ? net.minecraft.world.item.ItemStack.EMPTY
                    : player.getItemInHand(p[1].equals("off") ? net.minecraft.world.InteractionHand.OFF_HAND
                            : net.minecraft.world.InteractionHand.MAIN_HAND);
            case "block_hit" -> {
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(Integer.parseInt(p[1]),
                        Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                net.minecraft.core.Direction face = net.minecraft.core.Direction.byName(p[4]);
                yield new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(pos.getX() + 0.5,
                        pos.getY() + 0.5, pos.getZ() + 0.5), face == null ? net.minecraft.core.Direction.UP : face, pos,
                        false);
            }
            case "anvil" -> net.minecraft.world.inventory.AnvilMenu.kiln$bridged(player, p[1], p[2], p[3],
                    Integer.parseInt(p[4]));
            // A block of a state ("minecraft:glass_pane[...]"), a game block with the explosion resistance Horizon has
            // for it (D-203); a mod's block keeps its own.
            case "block" -> {
                net.minecraft.world.level.block.Block block =
                        net.minecraft.world.level.block.state.BlockState.kiln$parse(p[1]).getBlock();
                if (p.length > 2 && !p[2].isEmpty() && p[1].startsWith("minecraft:")) {
                    block.kiln$explosionResistance(Float.parseFloat(p[2]));
                }
                yield block;
            }
            case "tooltip_context" -> TOOLTIP_CONTEXT;
            case "tooltip_flag" -> TOOLTIP_FLAG;
            case "button_info" -> new MouseButtonInfo(button(Integer.parseInt(p[1])), Integer.parseInt(p[2]));
            default -> throw new IllegalArgumentException("no such value in Kiln's Mixin bridge: " + spec);
        };
    }

    /** The context a tooltip is made in, as the client's: Kiln has no registries' lookup or maps for it. */
    private static final net.minecraft.world.item.Item.TooltipContext TOOLTIP_CONTEXT =
            new net.minecraft.world.item.Item.TooltipContext() {
                @Override
                public net.minecraft.core.HolderLookup.Provider registries() {
                    return null;
                }

                @Override
                public float tickRate() {
                    return 20.0F;
                }

                @Override
                public net.minecraft.world.level.saveddata.maps.MapItemSavedData mapData(
                        net.minecraft.world.level.saveddata.maps.MapId id) {
                    return null;
                }

                @Override
                public boolean isPeaceful() {
                    return false;
                }
            };

    /** A tooltip's flag: not advanced (F3+H off), and whether the window's player is in creative. */
    private static final net.minecraft.world.item.TooltipFlag TOOLTIP_FLAG = new net.minecraft.world.item.TooltipFlag() {
        @Override
        public boolean isAdvanced() {
            return false;
        }

        @Override
        public boolean isCreative() {
            net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
            return player != null && player.getAbilities().instabuild;
        }
    };

    /**
     * A key by its name ({@code key.keyboard.j}). A key Kiln has no code for (or a name that isn't a keyboard key's) is
     * vanilla's unknown key ({@code -1}, as GLFW gives a key it can't name): never null, which a mixin into {@code
     * KeyboardHandler.keyPress} would read (D-163). Each such name is said once in Kiln's log.
     */
    private static KeyEvent keyEvent(String name) {
        InputConstants.Key key = InputConstants.getKey(name);
        if (key == InputConstants.UNKNOWN || key.getType() != InputConstants.Type.KEYBOARD) {
            if (UNNAMED_KEYS.add(name)) {
                Native.log("Kiln has no key code for " + name + ": the mods see it as the unknown key");
            }
            return new KeyEvent(InputConstants.UNKNOWN.getValue(), 0, 0);
        }
        return new KeyEvent(key.getValue(), 0, 0);
    }

    /** The key names {@link #keyEvent} had no code for, said once each. */
    private static final java.util.Set<String> UNNAMED_KEYS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** A GLFW mouse button (0 left, 1 right, 2 middle) as the game's since 26.1 (1 left, 2 middle, 3 right). */
    static int button(int glfw) {
        return switch (glfw) {
            case 0 -> InputConstants.MOUSE_BUTTON_LEFT;
            case 1 -> InputConstants.MOUSE_BUTTON_RIGHT;
            case 2 -> InputConstants.MOUSE_BUTTON_MIDDLE;
            default -> glfw + 1;
        };
    }

    /** The entity {@code id} of kind {@code kind}: the call's player when it is them, else Kiln's living entity. */
    static Entity entity(int id, String kind, ServerPlayer player, ServerLevel level) {
        if (id == 0) {
            return null;
        }
        if (player != null && kind.equals("minecraft:player")) {
            return player;
        }
        LivingEntity living = LIVING.computeIfAbsent(id, i -> new Living(i));
        ((Living) living).kiln$place(kind, level);
        return living;
    }

    /**
     * The damage type a death message's key names ({@code death.attack.inFire.player} is {@code minecraft:in_fire}):
     * vanilla's message ids are its types' in camel case. {@code minecraft:generic} for none.
     */
    static String deathType(String key) {
        String id = key.startsWith("death.attack.") ? key.substring("death.attack.".length()) : key.startsWith("death.")
                ? key.substring("death.".length()) : key;
        int dot = id.indexOf('.');
        id = dot > 0 ? id.substring(0, dot) : id;
        if (id.isEmpty()) {
            return "minecraft:generic";
        }
        return "minecraft:" + id.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    /** A damage source of type {@code type}, from the entities so named: an id, {@code player} for the call's, 0 none. */
    static DamageSource damage(String type, String causing, String direct, ServerPlayer player, ServerLevel level) {
        return damage(type, by(causing, player, level), by(direct, player, level), level);
    }

    private static Entity by(String who, ServerPlayer player, ServerLevel level) {
        if (who.equals("player")) {
            return player;
        }
        int id = Integer.parseInt(who);
        return id == 0 ? null : other(id, level);
    }

    /** A damage source of type {@code type} ({@code minecraft:fall}), caused by {@code by}, dealt by {@code directly}. */
    static DamageSource damage(String type, Entity by, Entity directly, ServerLevel level) {
        Identifier id = Identifier.tryParse(type.isEmpty() ? "minecraft:generic" : type);
        if (id == null) {
            id = Identifier.withDefaultNamespace("generic");
        }
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, id);
        Holder<DamageType> holder = Holder.Reference.kiln$of(key, new DamageType(id.getPath(), null, 0.1F, null, null));
        return new DamageSource(holder, directly == null ? by : directly, by);
    }

    /** Another entity in a hit, by id: Kiln doesn't know its kind (a living one stands for it). */
    private static Entity other(int id, ServerLevel level) {
        return other(id, "", level);
    }

    /** An entity other than the call's player, by Horizon's id and kind: Kiln's living entity of it. */
    static LivingEntity other(int id, String kind, ServerLevel level) {
        LivingEntity living = LIVING.computeIfAbsent(id, i -> new Living(i));
        ((Living) living).kiln$place(kind, level);
        return living;
    }

    private static void sayOnce(Row row, String line) {
        if (SAID.add(row.id() + line)) {
            Host.send("log", line);
        }
    }

    private static String simple(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    /** A living entity Horizon hit that isn't a player: Kiln's, of Horizon's id, its kind as Horizon names it. */
    static final class Living extends LivingEntity {
        private final int horizonId;
        private String kind = "";

        Living(int horizonId) {
            this.horizonId = horizonId;
        }

        void kiln$place(String kind, ServerLevel level) {
            if (!kind.isEmpty()) {
                this.kind = kind;
            }
            if (level != null) {
                this.level = level;
            }
        }

        @Override
        public int getId() {
            return horizonId;
        }

        /** Its kind, as Horizon names it ({@code minecraft:zombie}). */
        public String kiln$kind() {
            return kind;
        }

        @Override
        public EntityType<?> getType() {
            Identifier id = Identifier.tryParse(kind);
            // The registry is a defaulted one (a pig for an id it hasn't): a kind Kiln doesn't know is not a pig (D-173).
            var types = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
            return id != null && types.containsKey(id) ? types.getValue(id) : super.getType();
        }
    }
}
