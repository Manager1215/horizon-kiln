package kiln;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Kiln's server side: Horizon's integrated server calls a mod's code where vanilla's server would, with what that
 * code reads (a snapshot of the player and the world), and gets back what it did (effects, for the server to do).
 * The server waits for the answer; Host runs one command at a time, so a call never meets another.
 *
 * <p>Every call is {@code <command> <call> <world folder> <game time> ...}. A player is 13 fields: {@code <uuid>
 * <name> <dimension> <x> <y> <z> <game mode 0-3> <health> <main hand item> <count> <off hand item> <count>
 * <attributes>}, the attributes as {@code <id> <base> <value>} joined by {@code ;}.
 * <ul>
 *   <li>{@code use ... <item> <main|off> <player>}: a player uses Horizon's item of the id a mod registered
 *       ({@code Item.use});
 *   <li>{@code join ... <player>}: a player came into a level, joining the game or changing dimension (Fabric's
 *       {@code ServerEntityEvents.ENTITY_LOAD});
 *   <li>{@code respawn ... <alive 0|1> <old player> <new player>}: a player respawned, the old one as they last were
 *       ({@code ServerPlayerEvents.COPY_FROM}, then {@code ENTITY_LOAD} for the new one, then {@code AFTER_RESPAWN});
 *   <li>{@code use_block ... <x> <y> <z> <state> <face> <main|off> <player>}: a player right clicks a mod's block
 *       ({@code useItemOn}, then {@code useWithoutItem} for the main hand when that says to try without the item);
 *   <li>{@code random_tick ... <dimension> <x> <y> <z> <state>}: a mod's block ticks at random ({@code randomTick});
 *   <li>{@code step_on ... <x> <y> <z> <state> <player>}: a player stands on a mod's block ({@code stepOn});
 *   <li>{@code block_tick ... <dimension> <x> <y> <z> <state>}: its scheduled tick ({@code tick});
 *   <li>{@code neighbor ... <dimension> <x> <y> <z> <state> <moved 0|1>}: a neighbour changed ({@code
 *       neighborChanged});
 *   <li>{@code placement ... <dimension> <x> <y> <z> <default state> <face> <hit x> <hit y> <hit z> <clicked x>
 *       <clicked y> <clicked z> <yaw> <pitch> <sneaking 0|1> <item> <replacing 0|1>}: the state it takes where it is
 *       placed ({@code getStateForPlacement}), answered as a {@code placement} effect;
 *   <li>{@code placed ... <x> <y> <z> <state> <item> <count> <player>}: a player placed it ({@code setPlacedBy});
 *   <li>{@code destroy ... <x> <y> <z> <state> <player>}: a player is breaking it ({@code playerWillDestroy});
 *   <li>{@code block_entity_tick ... <dimension> <x> <y> <z> <state> <type> <snbt>}: the server tick of a mod's block
 *       entity of that type, whose saved compound the game has as that ({@code BlockEntityTicker.tick}).
 * </ul>
 * A state is written as the game's data writes it: {@code mymod:lamp[lit=true]}. While a call runs, the mod's code
 * may read the world: {@code ask <call> block <x> <y> <z>}, answered with the state there, and {@code ask <call>
 * block_entity <x> <y> <z>}, answered with {@code <type> <snbt>} for a mod's block entity there, else nothing.
 * The answer is {@code return <call> <result> <effect>...}: the result {@code pass} (an event's), {@code fail},
 * {@code success}, {@code success_server}, {@code consume}, or {@code error} with why as its first effect; the
 * effects, in order, all to the player of the call (the new one for a respawn):
 * <ul>
 *   <li>{@code attribute <id> <base>}: the player's attribute has a new base value;
 *   <li>{@code health <health>};
 *   <li>{@code hand <main|off> <item> <count>}: the hand holds that now (count 0: nothing);
 *   <li>{@code sound <id> <source> <x> <y> <z> <volume> <pitch>}: the source is SoundSource's ordinal;
 *   <li>{@code overlay <json>} and {@code tell <json>}: a text component over the hotbar, or in chat;
 *   <li>{@code block <x> <y> <z> <flags> <state>}: {@code Level.setBlock};
 *   <li>{@code drop <x> <y> <z> <item> <count>}: {@code Block.popResource};
 *   <li>{@code schedule <x> <y> <z> <block> <delay>}: {@code scheduleTick};
 *   <li>{@code placement <state>} or {@code placement none}: the state a placed block takes, or no placement;
 *   <li>{@code block_entity <x> <y> <z> <snbt>}: what the block entity there saves now ({@link BlockEntities}).
 * </ul>
 */
public final class Server {
    /** A player's fields in a call. */
    /** The fields a player takes in a call (runtime.rs's {@code PLAYER_FIELDS}). */
    static final int PLAYER = 19;
    private static final Map<Identifier, Holder<Attribute>> ATTRIBUTES = namedAttributes();
    private static List<String> effects;
    private static String call;
    private static Path world;
    private static SavedDataStorage storage;
    private static List<String> announcedItems = List.of();
    private static List<String> announcedEvents = List.of();
    private static List<String> announcedBlocks = List.of();

    private Server() {}

    /** Something a mod's code did, for the server to do; outside a call there is nobody to tell. */
    public static void effect(String effect) {
        if (effects != null) {
            effects.add(effect);
        } else {
            Host.send("log", "a mod did something on the server outside a call: " + effect);
        }
    }

    /** Whether a server call is running: only then can Kiln ask the game. */
    public static boolean inCall() {
        return call != null;
    }

    /** The game's answer to {@code what}, asked during a call ({@code block <x> <y> <z>}). */
    public static String ask(String... what) {
        if (call == null) {
            throw new IllegalStateException("Kiln asks the game only during a server call");
        }
        return Host.ask(call, what);
    }

    /** A server call's answer, as the {@code return} reply's fields. */
    static String[] call(String[] f) {
        call = f[1];
        effects = new ArrayList<>();
        try {
            long gameTime = Long.parseLong(f[3]);
            SavedDataStorage storage = storage(Path.of(f[2]));
            String result = switch (f[0]) {
                case "use" -> use(f[4], f[5], player(f, 6, gameTime, storage));
                case "join" -> join(player(f, 4, gameTime, storage));
                case "respawn" -> respawn(f[4].equals("1"), player(f, 5, gameTime, storage),
                        player(f, 5 + PLAYER, gameTime, storage));
                case "use_block" -> useBlock(position(f, 4), BlockState.kiln$parse(f[7]), f[8], f[9],
                        player(f, 10, gameTime, storage));
                case "random_tick" -> randomTick(new ServerLevel(f[4], gameTime, storage), position(f, 5),
                        BlockState.kiln$parse(f[8]));
                case "step_on" -> stepOn(position(f, 4), BlockState.kiln$parse(f[7]), player(f, 8, gameTime, storage));
                case "block_tick" -> scheduledTick(new ServerLevel(f[4], gameTime, storage), position(f, 5),
                        BlockState.kiln$parse(f[8]));
                case "neighbor" -> neighbor(new ServerLevel(f[4], gameTime, storage), position(f, 5),
                        BlockState.kiln$parse(f[8]), f[9].equals("1"));
                case "placement" -> placement(new ServerLevel(f[4], gameTime, storage), f);
                case "placed" -> placed(position(f, 4), BlockState.kiln$parse(f[7]), f[8], f[9],
                        player(f, 10, gameTime, storage));
                case "destroy" -> destroy(position(f, 4), BlockState.kiln$parse(f[7]), player(f, 8, gameTime, storage));
                case "changed" -> {
                    BlockEntities.changedNear(f[4], position(f, 5));
                    yield "pass";
                }
                case "block_entity_tick" -> blockEntityTick(new ServerLevel(f[4], gameTime, storage), position(f, 5),
                        BlockState.kiln$parse(f[8]), f[9], f[10]);
                default -> throw new IllegalArgumentException("unknown call " + f[0]);
            };
            BlockEntities.save();
            storage.kiln$save();
            List<String> reply = new ArrayList<>(List.of(f[1], result));
            reply.addAll(effects);
            return reply.toArray(String[]::new);
        } catch (RuntimeException | LinkageError e) {
            // A mod that calls what Kiln has no stand-in for: the game goes on as Horizon's own.
            BlockEntities.drop();
            return new String[] {f[1], "error", e.toString()};
        } finally {
            effects = null;
            call = null;
        }
    }

    private static String use(String id, String handName, ServerPlayer player) {
        InteractionHand hand = handName.equals("off") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        Hands before = new Hands(player);
        // Fabric's UseItemCallback first: a listener that doesn't pass takes the use, and the item's own doesn't run.
        InteractionResult result = UseItemCallback.EVENT.invoker().interact(player, player.level(), hand);
        if (result == InteractionResult.PASS) {
            result = Item.kiln$byId(id).use(player.level(), player, hand);
        }
        // ServerPlayerGameMode.useItem: the stack the use left in the hand; a creative player keeps the count.
        if (result instanceof InteractionResult.Success s && s.heldItemTransformedTo() != null) {
            player.setItemInHand(hand, s.heldItemTransformedTo());
        }
        ItemStack held = player.getItemInHand(hand);
        if (player.isCreative() && held != ItemStack.EMPTY) {
            held.setCount(hand == InteractionHand.MAIN_HAND ? before.mainCount : before.offCount);
        }
        before.changes(player);
        return name(result);
    }

    private static String join(ServerPlayer player) {
        Hands before = new Hands(player);
        ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(player, player.level());
        before.changes(player);
        return "pass";
    }

    /** The old player is the one who died: what the mods' code does to them goes nowhere. */
    private static String respawn(boolean alive, ServerPlayer old, ServerPlayer player) {
        old.kiln$quiet();
        Hands before = new Hands(player);
        ServerPlayerEvents.COPY_FROM.invoker().copyFromPlayer(old, player, alive);
        ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(player, player.level());
        ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(old, player, alive);
        before.changes(player);
        return "pass";
    }

    /** ServerPlayerGameMode.useItemOn for a mod's block: the item's use on it, then, for the main hand, without it. */
    private static String useBlock(BlockPos pos, BlockState state, String face, String handName, ServerPlayer player) {
        InteractionHand hand = handName.equals("off") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        Direction direction = Direction.byName(face);
        if (direction == null) {
            direction = Direction.UP;
        }
        Vec3 at = Vec3.atCenterOf(pos).add(direction.getStepX() * 0.5, direction.getStepY() * 0.5,
                direction.getStepZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(at, direction, pos, false);
        Hands before = new Hands(player);
        InteractionResult result = state.useItemOn(player.getItemInHand(hand), player.level(), player, hand, hit);
        if (result instanceof InteractionResult.TryEmptyHandInteraction && hand == InteractionHand.MAIN_HAND) {
            result = state.useWithoutItem(player.level(), player, hit);
        }
        before.changes(player);
        if (player.kiln$openedMenu() != null) {
            Menus.opened(player.kiln$openedMenu(), player.getInventory(), player.level().getBlockEntity(pos));
        }
        return name(result);
    }

    private static String randomTick(ServerLevel level, BlockPos pos, BlockState state) {
        state.randomTick(level, pos, level.getRandom());
        return "pass";
    }

    private static String stepOn(BlockPos pos, BlockState state, ServerPlayer player) {
        Hands before = new Hands(player);
        state.getBlock().stepOn(player.level(), pos, state, player);
        before.changes(player);
        return "pass";
    }

    private static String scheduledTick(ServerLevel level, BlockPos pos, BlockState state) {
        state.tick(level, pos, level.getRandom());
        return "pass";
    }

    /** Horizon doesn't say which block changed: the neighbour handed over is air. */
    private static String neighbor(ServerLevel level, BlockPos pos, BlockState state, boolean moved) {
        state.handleNeighborChanged(level, pos, Blocks.AIR, null, moved);
        return "pass";
    }

    /** {@code getStateForPlacement} with the game's {@code BlockPlaceContext}; Horizon names no placer. */
    private static String placement(ServerLevel level, String[] f) {
        BlockPos pos = position(f, 5);
        BlockState state = BlockState.kiln$parse(f[8]);
        Direction face = Direction.byName(f[9]);
        Vec3 hitAt = new Vec3(Double.parseDouble(f[10]), Double.parseDouble(f[11]), Double.parseDouble(f[12]));
        BlockHitResult hit = new BlockHitResult(hitAt, face == null ? Direction.UP : face, position(f, 13), false);
        ItemStack stack = f[19].isEmpty() ? ItemStack.EMPTY : new ItemStack(Item.kiln$byId(f[19]));
        BlockPlaceContext context = new BlockPlaceContext(
                level, null, InteractionHand.MAIN_HAND, stack, hit, Float.parseFloat(f[16]), Float.parseFloat(f[17]),
                f[18].equals("1"), pos, f[20].equals("1"));
        BlockState placed = state.getBlock().getStateForPlacement(context);
        effects.add("placement " + (placed == null ? "none" : placed.kiln$text()));
        return "pass";
    }

    private static String placed(BlockPos pos, BlockState state, String item, String count, ServerPlayer player) {
        Hands before = new Hands(player);
        // As the game does: the block is set (onPlace, where it was air), then the player who placed it is told of.
        state.onPlace(player.level(), pos, Blocks.AIR.defaultBlockState(), false);
        state.getBlock().setPlacedBy(player.level(), pos, state, player, stack(item, count));
        before.changes(player);
        return "pass";
    }

    private static String destroy(BlockPos pos, BlockState state, ServerPlayer player) {
        Hands before = new Hands(player);
        state.getBlock().playerWillDestroy(player.level(), pos, state, player);
        before.changes(player);
        return "pass";
    }

    /** {@code Level.tickBlockEntities} for a mod's block entity: its block's ticker for the state it is in, if any. */
    @SuppressWarnings("unchecked")
    private static String blockEntityTick(ServerLevel level, BlockPos pos, BlockState state, String type, String saved) {
        BlockEntity entity = BlockEntities.handed(level, pos, state, type, saved);
        if (entity != null && !entity.isRemoved() && state.getBlock() instanceof EntityBlock block) {
            BlockEntityTicker<BlockEntity> ticker =
                    block.getTicker(level, state, (BlockEntityType<BlockEntity>) entity.getType());
            if (ticker != null) {
                ticker.tick(level, pos, state, entity);
            }
        }
        return "pass";
    }

    private static BlockPos position(String[] f, int at) {
        return new BlockPos(Integer.parseInt(f[at]), Integer.parseInt(f[at + 1]), Integer.parseInt(f[at + 2]));
    }

    /** What a player held, and their experience, before a call: to tell what the mod's code changed. */
    private static final class Hands {
        final ItemStack main;
        final ItemStack off;
        final int mainCount;
        final int offCount;
        final String mainWhole;
        final String offWhole;
        final int level;
        final float progress;
        final int total;

        Hands(ServerPlayer player) {
            main = player.getMainHandItem();
            off = player.getOffhandItem();
            mainCount = main.getCount();
            offCount = off.getCount();
            mainWhole = main.kiln$snbt();
            offWhole = off.kiln$snbt();
            level = player.experienceLevel;
            progress = player.experienceProgress;
            total = player.totalExperience;
        }

        void changes(ServerPlayer player) {
            changed(player.getMainHandItem(), main, mainCount, mainWhole, "main");
            changed(player.getOffhandItem(), off, offCount, offWhole, "off");
            if (player.experienceLevel != level || player.experienceProgress != progress
                    || player.totalExperience != total) {
                effects.add("xp " + player.experienceLevel + " " + player.experienceProgress + " "
                        + player.totalExperience);
            }
        }
    }

    /** The ids of the items the mods registered whose class has its own {@code use}, sorted. */
    static List<String> usableItems() {
        List<String> ids = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            if (overridesUse(BuiltInRegistries.ITEM.getValue(id).getClass())) {
                ids.add(id.toString());
            }
        }
        ids.sort(null);
        return ids;
    }

    /**
     * The server events the mods listen to, as calls name them: {@code join}, {@code respawn}, {@code use_item}
     * (every item's use: Fabric's UseItemCallback).
     */
    static List<String> heardEvents() {
        List<String> events = new ArrayList<>();
        if (UseItemCallback.EVENT.kiln$heard()) {
            events.add("use_item");
        }
        if (ServerEntityEvents.ENTITY_LOAD.kiln$heard()) {
            events.add("join");
        }
        if (ServerEntityEvents.ENTITY_LOAD.kiln$heard() || ServerPlayerEvents.COPY_FROM.kiln$heard()
                || ServerPlayerEvents.AFTER_RESPAWN.kiln$heard()) {
            events.add("respawn");
        }
        return events;
    }

    /**
     * After a mod's entrypoint ran: the items with a use ({@code items <id>...}) and the events heard ({@code
     * listens <event>...}), each when it changed, so the server calls only for those.
     */
    static void announce() {
        List<String> ids = usableItems();
        if (!ids.equals(announcedItems)) {
            announcedItems = ids;
            Host.send("items", ids.toArray(String[]::new));
        }
        List<String> events = heardEvents();
        if (!events.equals(announcedEvents)) {
            announcedEvents = events;
            Host.send("listens", events.toArray(String[]::new));
        }
        Recipes.prepare();
        List<String> blocks = blocksWithBehaviour();
        if (!blocks.equals(announcedBlocks)) {
            announcedBlocks = blocks;
            Host.send("blocks", blocks.toArray(String[]::new));
        }
    }

    /**
     * The blocks the mods registered with code of their own, each as {@code <id> <what>[,<what>] [<block entity
     * type>]}: {@code use}
     * ({@code useItemOn} or {@code useWithoutItem}), {@code random} ({@code randomTick}), {@code step} ({@code
     * stepOn}), {@code tick}, {@code neighbor} ({@code neighborChanged}), {@code placement} ({@code
     * getStateForPlacement}), {@code placed} ({@code setPlacedBy}), {@code destroy} ({@code playerWillDestroy}),
     * {@code entity} (an {@code EntityBlock}'s block entity, of the type named after) and {@code ticks} (its ticker,
     * for a state of the block on the server). The server calls only for those. A block entity that is a {@code
     * Container} has its slots named last: {@code <count>;<down>;<up>;<north>;<south>;<west>;<east>;<in>;<out>}, the
     * slots each face reaches, those a hopper may put into and take from, comma-separated.
     */
    static List<String> blocksWithBehaviour() {
        List<String> found = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            Class<?> c = BuiltInRegistries.BLOCK.getValue(id).getClass();
            List<String> what = new ArrayList<>();
            if (declares(c, "useItemOn") || declares(c, "useWithoutItem")) {
                what.add("use");
            }
            if (declares(c, "randomTick")) {
                what.add("random");
            }
            if (declares(c, "stepOn")) {
                what.add("step");
            }
            if (declares(c, "tick")) {
                what.add("tick");
            }
            if (declares(c, "neighborChanged")) {
                what.add("neighbor");
            }
            if (declares(c, "getStateForPlacement")) {
                what.add("placement");
            }
            if (declares(c, "setPlacedBy") || declares(c, "onPlace")) {
                what.add("placed");
            }
            if (declares(c, "playerWillDestroy")) {
                what.add("destroy");
            }
            String type = blockEntity(BuiltInRegistries.BLOCK.getValue(id), what);
            if (!what.isEmpty()) {
                found.add(id + " " + String.join(",", what) + (type == null ? "" : " " + type));
            }
        }
        found.sort(null);
        return found;
    }

    /**
     * The type of the block entity {@code block} makes, when it is a registered one: {@code entity} goes into {@code
     * what}, and {@code ticks} when a state of the block has a ticker on the server.
     */
    @SuppressWarnings("unchecked")
    private static String blockEntity(Block block, List<String> what) {
        if (!(block instanceof EntityBlock maker)) {
            return null;
        }
        BlockEntity entity;
        try {
            entity = maker.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
        } catch (RuntimeException | LinkageError e) {
            Host.send("log", block.kiln$id() + " makes no block entity in Kiln: " + e);
            return null;
        }
        Identifier type = entity == null ? null : BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType());
        if (type == null) {
            return null;
        }
        what.add("entity");
        ServerLevel level = new ServerLevel("minecraft:overworld", 0, null);
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            try {
                if (maker.getTicker(level, state, (BlockEntityType<BlockEntity>) entity.getType()) != null) {
                    what.add("ticks");
                    break;
                }
            } catch (RuntimeException | LinkageError e) {
                Host.send("log", block.kiln$id() + "'s ticker fails in Kiln: " + e);
                break;
            }
        }
        return entity instanceof Container container ? type + " " + slots(container) : type.toString();
    }

    /** Items a hopper's offer is tried with: a slot that takes any of them is one hoppers may fill. */
    private static final List<Item> PROBES = List.of(Items.COBBLESTONE, Items.COAL, Items.IRON_INGOT);

    /**
     * A container's slots as Horizon's mod containers say them: its count, the slots each face reaches
     * ({@code getSlotsForFace}; every slot for a plain container), and the slots a hopper may put into ({@code
     * canPlaceItemThroughFace} for any probe item) and take from ({@code canTakeItemThroughFace}).
     */
    private static String slots(Container container) {
        int size = container.getContainerSize();
        List<String> parts = new ArrayList<>(List.of(Integer.toString(size)));
        java.util.Set<Integer> in = new java.util.TreeSet<>();
        java.util.Set<Integer> out = new java.util.TreeSet<>();
        for (Direction face : Direction.values()) {
            int[] reached = container instanceof WorldlyContainer worldly
                    ? worldly.getSlotsForFace(face) : java.util.stream.IntStream.range(0, size).toArray();
            List<String> named = new ArrayList<>();
            for (int slot : reached) {
                named.add(Integer.toString(slot));
                for (Item probe : PROBES) {
                    ItemStack stack = new ItemStack(probe);
                    if (container instanceof WorldlyContainer worldly
                            ? worldly.canPlaceItemThroughFace(slot, stack, face) : container.canPlaceItem(slot, stack)) {
                        in.add(slot);
                    }
                    if (!(container instanceof WorldlyContainer worldly) || worldly.canTakeItemThroughFace(slot, stack, face)) {
                        out.add(slot);
                    }
                }
            }
            parts.add(String.join(",", named));
        }
        parts.add(String.join(",", in.stream().map(String::valueOf).toList()));
        parts.add(String.join(",", out.stream().map(String::valueOf).toList()));
        return String.join(";", parts);
    }

    /** Whether {@code c} or a superclass below Kiln's Block declares a method {@code name}. */
    private static boolean declares(Class<?> c, String name) {
        for (Class<?> k = c; k != null && k != Block.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean overridesUse(Class<?> c) {
        try {
            return c.getMethod("use", Level.class, Player.class, InteractionHand.class).getDeclaringClass() != Item.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** The world's storage: the one open, or a new one when the world changed (the old one written first). */
    private static SavedDataStorage storage(Path folder) {
        if (!folder.equals(world)) {
            if (storage != null) {
                storage.kiln$save();
            }
            world = folder;
            storage = new SavedDataStorage(folder.resolve("kiln").resolve("data"));
            BlockEntities.forget();
        }
        return storage;
    }

    /** The player described by the {@value #PLAYER} fields from {@code at}, in their dimension's level. */
    private static ServerPlayer player(String[] f, int at, long gameTime, SavedDataStorage storage) {
        ServerLevel level = new ServerLevel(f[at + 2], gameTime, storage);
        ServerPlayer player = new ServerPlayer(UUID.fromString(f[at]), f[at + 1], level, Double.parseDouble(f[at + 3]),
                Double.parseDouble(f[at + 4]), Double.parseDouble(f[at + 5]), Integer.parseInt(f[at + 6]),
                Float.parseFloat(f[at + 7]));
        // Each hand's stack whole when the game gave it (its damage, enchantments), else its item and count.
        ItemStack main = f[at + 17].isEmpty() ? stack(f[at + 8], f[at + 9]) : ItemStack.kiln$fromSnbt(f[at + 17]);
        ItemStack off = f[at + 18].isEmpty() ? stack(f[at + 10], f[at + 11]) : ItemStack.kiln$fromSnbt(f[at + 18]);
        player.kiln$setHands(main, off);
        for (String attribute : f[at + 12].split(";")) {
            String[] parts = attribute.split(" ");
            if (parts.length == 3) {
                player.kiln$setAttribute(attribute(Identifier.parse(parts[0])), Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]));
            }
        }
        player.setShiftKeyDown(f[at + 13].equals("1"));
        player.kiln$experience(Integer.parseInt(f[at + 14]), Float.parseFloat(f[at + 15]), Integer.parseInt(f[at + 16]));
        return player;
    }

    /** The attribute called {@code id}: Attributes' own, else a new one (the game has more than Kiln names). */
    private static Holder<Attribute> attribute(Identifier id) {
        return ATTRIBUTES.computeIfAbsent(id, i -> Holder.Reference.kiln$of(ResourceKey.create(Registries.ATTRIBUTE, i),
                new Attribute("attribute.name." + i.getPath(), 0.0) {}));
    }

    @SuppressWarnings("unchecked")
    private static Map<Identifier, Holder<Attribute>> namedAttributes() {
        Map<Identifier, Holder<Attribute>> named = new ConcurrentHashMap<>();
        for (Field field : Attributes.class.getFields()) {
            try {
                if (field.get(null) instanceof Holder<?> holder) {
                    holder.unwrapKey().ifPresent(k -> named.put(k.identifier(), (Holder<Attribute>) holder));
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return named;
    }

    private static ItemStack stack(String item, String count) {
        int n = Integer.parseInt(count);
        return item.isEmpty() || n <= 0 ? ItemStack.EMPTY : new ItemStack(Item.kiln$byId(item), n);
    }

    /** A {@code hand} effect when the hand's stack isn't what the game gave, or its count changed. */
    /**
     * The hand's change: another item or count ({@code hand}), and the stack whole when its components changed too
     * ({@code held}: its damage, its enchantments).
     */
    private static void changed(ItemStack now, ItemStack before, int count, String whole, String hand) {
        boolean other = now != before || now.getCount() != count;
        if (other) {
            effects.add("hand " + hand + " " + now.getItem().kiln$id() + " " + now.getCount());
        }
        String nowWhole = now.kiln$snbt();
        if (!nowWhole.equals(whole) && !nowWhole.isEmpty()
                && (!other || nowWhole.contains("components:"))) {
            effects.add("held " + hand + " " + nowWhole);
        }
    }

    private static String name(InteractionResult result) {
        if (result instanceof InteractionResult.Success s) {
            return switch (s.swingSource()) {
                case CLIENT -> "success";
                case SERVER -> "success_server";
                case NONE -> "consume";
            };
        }
        return result instanceof InteractionResult.Fail ? "fail" : "pass";
    }
}
