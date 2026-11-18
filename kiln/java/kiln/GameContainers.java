package kiln;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The game's own containers on Kiln's server side (D-144, D-199): a chest, a barrel, a hopper, a dispenser, a dropper,
 * a shulker box, a furnace, a smoker, a blast furnace, a brewing stand, a crafter, a chiseled bookshelf, a composter.
 * Horizon keeps their slots; a mod's code reaches them as Fabric does, through {@code ItemStorage.SIDED}'s fallback, as
 * a {@code Container} Kiln makes for the call, and as the block entity {@code level.getBlockEntity(pos)} returns (a
 * {@code Container}, one half of a double chest, as in vanilla). Its slots are asked of the game the first time the
 * call reaches it ({@code container <x> <y> <z>}), and what the call changed in them goes back when it ends
 * ({@code container <x> <y> <z> {Before:[...],Items:[...]}}: the game changes only the slots that still hold what
 * Kiln read). A double chest is both halves, its right half first, as vanilla's {@code ChestBlock.getContainer}
 * combines them. Which blocks are containers, and the rules each adds to its slots (the game's own, as Horizon's
 * hoppers follow them), is {@link #KINDS}.
 */
public final class GameContainers {
    /** What a container of the game adds to its slots' rules. */
    private enum Kind {
        /** Any item in any slot, through any face. */
        PLAIN,
        /** A chest, which joins the chest beside it of the same block into a double chest. */
        CHEST,
        /** A shulker box, which takes no shulker box ({@code ShulkerBoxBlockEntity.canPlaceItemThroughFace}). */
        SHULKER,
        /** {@code AbstractFurnaceBlockEntity}: input from above, fuel from the sides, the result out below. */
        FURNACE,
        /** {@code BrewingStandBlockEntity}: the ingredient from above, bottles and fuel from the sides. */
        BREWING,
        /** {@code CrafterBlockEntity}: disabled slots, and the smallest stack filled first. */
        CRAFTER,
        /** {@code ChiseledBookShelfBlockEntity}: one book to a slot. */
        BOOKSHELF,
        /** {@code ComposterBlock}'s containers: no block entity, its level is its block's. */
        COMPOSTER,
    }

    /** The game's blocks whose block entity is a {@code Container}, and its {@link Kind}. */
    private static final Map<String, Kind> KINDS = Map.ofEntries(
            Map.entry("minecraft:chest", Kind.CHEST),
            Map.entry("minecraft:trapped_chest", Kind.CHEST),
            Map.entry("minecraft:barrel", Kind.PLAIN),
            Map.entry("minecraft:hopper", Kind.PLAIN),
            Map.entry("minecraft:dispenser", Kind.PLAIN),
            Map.entry("minecraft:dropper", Kind.PLAIN),
            Map.entry("minecraft:furnace", Kind.FURNACE),
            Map.entry("minecraft:smoker", Kind.FURNACE),
            Map.entry("minecraft:blast_furnace", Kind.FURNACE),
            Map.entry("minecraft:brewing_stand", Kind.BREWING),
            Map.entry("minecraft:crafter", Kind.CRAFTER),
            Map.entry("minecraft:chiseled_bookshelf", Kind.BOOKSHELF),
            Map.entry("minecraft:composter", Kind.COMPOSTER));

    private static final Identifier BOOKSHELF_BOOKS = Identifier.fromNamespaceAndPath("minecraft", "bookshelf_books");

    /** The block entity type of each of the game's containers, as {@code getType()} says it (no mod registers them). */
    private static final Map<String, BlockEntityType<?>> TYPES = new HashMap<>();

    /** The containers this call reached, by dimension and position; null where the game has none. */
    private static final Map<String, Half> reached = new LinkedHashMap<>();

    /** What this call asked of items (a furnace's fuel, a brewing stand's slots, a composter's), by question. */
    private static final Map<String, String> items = new HashMap<>();

    private GameContainers() {}

    private static Kind kind(BlockState state) {
        String id = state.getBlock().kiln$id().toString();
        return id.endsWith("shulker_box") && id.startsWith("minecraft:") ? Kind.SHULKER : KINDS.get(id);
    }

    /**
     * The game's container at {@code pos} as a mod's code sees it, or null where there is none (another block, a
     * client's level, no server call running).
     */
    public static Container at(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server) || !Server.inCall()) {
            return null;
        }
        BlockState here = state != null ? state : level.getBlockState(pos);
        Kind kind = kind(here);
        if (kind == null) {
            return null;
        }
        Half half = half(server, pos, here, kind);
        if (half == null || kind != Kind.CHEST) {
            return half;
        }
        // ChestBlock.getContainer: a chest whose type is left or right, beside a chest of the same block facing the
        // same way and of the other type, is a double chest; the right half's slots come first.
        Map<String, String> values = here.kiln$values();
        String type = values.getOrDefault("type", "single");
        Direction facing = Direction.byName(values.getOrDefault("facing", "north"));
        if (type.equals("single") || facing == null || facing.getAxis() == Direction.Axis.Y) {
            return half;
        }
        boolean left = type.equals("left");
        BlockPos otherPos = pos.relative(left ? facing.getClockWise() : facing.getCounterClockWise());
        BlockState other = level.getBlockState(otherPos);
        Map<String, String> otherValues = other.kiln$values();
        if (other.getBlock() != here.getBlock()
                || facing != Direction.byName(otherValues.getOrDefault("facing", ""))
                || !otherValues.getOrDefault("type", "single").equals(left ? "right" : "left")) {
            return half;
        }
        Half otherHalf = half(server, otherPos, other, kind);
        if (otherHalf == null) {
            return half;
        }
        return left ? new DoubleChest(otherHalf, half) : new DoubleChest(half, otherHalf);
    }

    /**
     * The game's container's block entity at {@code pos}, as {@code level.getBlockEntity(pos)} returns it: a {@code
     * Container} (one half of a double chest), or null (no container of the game there, a composter, which has none,
     * no server call running).
     */
    public static BlockEntity entity(ServerLevel level, BlockPos pos) {
        if (!Server.inCall()) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        Kind kind = kind(state);
        return kind == null || kind == Kind.COMPOSTER ? null : half(level, pos, state, kind);
    }

    /** Whether {@code entity} is one of the game's containers ({@link #entity}), not a mod's block entity. */
    public static boolean isGame(BlockEntity entity) {
        return entity instanceof Half;
    }

    /** The call is done: each container whose slots it changed goes back to the game. */
    static void save() {
        for (Half half : reached.values()) {
            if (half != null) {
                half.save();
            }
        }
        reached.clear();
        items.clear();
    }

    /** The call failed, so the game keeps its slots as they were. */
    static void drop() {
        reached.clear();
        items.clear();
    }

    /** The game's answer to {@code what} about {@code stack}'s item, once a call. */
    private static String askItem(String what, ItemStack stack) {
        String id = stack.getItem().kiln$id().toString();
        return items.computeIfAbsent(what + " " + id, k -> Server.ask(what, id).trim());
    }

    private static boolean isItem(ItemStack stack, String id) {
        return !stack.isEmpty() && stack.getItem().kiln$id().toString().equals(id);
    }

    private static Half half(ServerLevel level, BlockPos pos, BlockState state, Kind kind) {
        String key = level.dimension().identifier() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        if (reached.containsKey(key)) {
            return reached.get(key);
        }
        String answer = Server.ask("container", Integer.toString(pos.getX()), Integer.toString(pos.getY()),
                Integer.toString(pos.getZ()));
        int space = answer.indexOf(' ');
        Half half = null;
        if (space > 0) {
            try {
                int size = Integer.parseInt(answer.substring(0, space));
                Map<String, Object> saved = Snbt.compound(answer.substring(space + 1));
                BlockEntityType<?> type = TYPES.computeIfAbsent(state.getBlock().kiln$id().toString(),
                        id -> new BlockEntityType<BlockEntity>((p, s) -> null, Set.of(state.getBlock())));
                half = switch (kind) {
                    case SHULKER -> new Shulker(type, pos, state, size, saved);
                    case FURNACE -> new Furnace(type, pos, state, size, saved);
                    case BREWING -> new Brewing(type, pos, state, size, saved);
                    case CRAFTER -> new Crafter(type, pos, state, size, saved);
                    case BOOKSHELF -> new Bookshelf(type, pos, state, size, saved);
                    case COMPOSTER -> new Composter(type, pos, state, size, saved);
                    default -> new Half(type, pos, state, size, saved);
                };
                half.setLevel(level);
            } catch (RuntimeException e) {
                Host.send("log", "the game answered a container with " + answer + ": " + e);
            }
        }
        reached.put(key, half);
        return half;
    }

    /**
     * One block's container: its slots as the game had them when the call reached it, and as they are now. A block
     * entity, so that {@code level.getBlockEntity(pos) instanceof Container} holds for it as in vanilla.
     */
    private static class Half extends BlockEntity implements Container {
        final NonNullList<ItemStack> slots;
        private final List<ItemStack> before = new ArrayList<>();

        Half(BlockEntityType<?> type, BlockPos pos, BlockState state, int size, Map<String, Object> saved) {
            super(type, pos, state);
            this.slots = NonNullList.withSize(size, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(TagValues.input(saved), slots);
            for (ItemStack stack : slots) {
                before.add(stack.copy());
            }
        }

        /** Whether the call changed its slots. */
        boolean changed() {
            for (int slot = 0; slot < slots.size(); slot++) {
                if (!ItemStack.matches(before.get(slot), slots.get(slot))) {
                    return true;
                }
            }
            return false;
        }

        /** What goes back to the game besides the slots. */
        void extra(Map<String, Object> saved) {}

        void save() {
            Map<String, Object> saved = new TreeMap<>();
            extra(saved);
            if (!changed() && saved.isEmpty()) {
                return;
            }
            NonNullList<ItemStack> was = NonNullList.withSize(slots.size(), ItemStack.EMPTY);
            for (int slot = 0; slot < was.size(); slot++) {
                was.set(slot, before.get(slot));
            }
            Map<String, Object> wasSaved = new TreeMap<>();
            ContainerHelper.saveAllItems(TagValues.output(wasSaved), was);
            ContainerHelper.saveAllItems(TagValues.output(saved), slots);
            saved.put("Before", wasSaved.get(ContainerHelper.TAG_ITEMS));
            BlockPos pos = getBlockPos();
            Server.effect("container " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + Snbt.write(saved));
        }

        @Override
        public int getContainerSize() {
            return slots.size();
        }

        @Override
        public boolean isEmpty() {
            return slots.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < slots.size() ? slots.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return ContainerHelper.removeItem(slots, slot, count);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(slots, slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot >= 0 && slot < slots.size()) {
                slots.set(slot, stack);
            }
        }

        /** What changed goes back when the call ends: nothing to mark. */
        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int slot = 0; slot < slots.size(); slot++) {
                slots.set(slot, ItemStack.EMPTY);
            }
        }

        @Override
        public String toString() {
            BlockPos pos = getBlockPos();
            return "GameContainer[" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "]";
        }
    }

    /** A shulker box: every slot through every face, but no shulker box goes in. */
    private static final class Shulker extends Half implements WorldlyContainer {
        private final int[] all;

        Shulker(BlockEntityType<?> type, BlockPos pos, BlockState state, int size, Map<String, Object> saved) {
            super(type, pos, state, size, saved);
            all = new int[size];
            for (int slot = 0; slot < size; slot++) {
                all[slot] = slot;
            }
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            String id = stack.getItem().kiln$id().toString();
            return !(id.startsWith("minecraft:") && id.endsWith("shulker_box"));
        }

        @Override
        public int[] getSlotsForFace(Direction face) {
            return all.clone();
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction face) {
            return canPlaceItem(slot, stack);
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) {
            return true;
        }
    }

    /**
     * A furnace, a smoker, a blast furnace ({@code AbstractFurnaceBlockEntity}): input (0) from above, fuel (1) from
     * the sides, the result (2) and an emptied bucket out below; nothing goes into the result.
     */
    private static final class Furnace extends Half implements WorldlyContainer {
        Furnace(BlockEntityType<?> type, BlockPos pos, BlockState state, int size, Map<String, Object> saved) {
            super(type, pos, state, size, saved);
        }

        @Override
        public int[] getSlotsForFace(Direction face) {
            return face == Direction.DOWN ? new int[] {2, 1} : face == Direction.UP ? new int[] {0} : new int[] {1};
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (slot == 2) {
                return false;
            }
            if (slot != 1) {
                return true;
            }
            // A fuel, or a bucket where the fuel slot holds no bucket (to take a sponge's water out).
            String[] fuel = askItem("fuel", stack).split(" ");
            boolean burns = fuel.length > 0 && !fuel[0].isEmpty() && Integer.parseInt(fuel[0]) > 0;
            return burns || isItem(stack, "minecraft:bucket") && !isItem(getItem(1), "minecraft:bucket");
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction face) {
            return canPlaceItem(slot, stack);
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) {
            return face != Direction.DOWN || slot != 1 || isItem(stack, "minecraft:water_bucket")
                    || isItem(stack, "minecraft:bucket");
        }
    }

    /**
     * A brewing stand ({@code BrewingStandBlockEntity}): the ingredient (3) from above, the bottles (0 to 2) and the
     * fuel (4) from the sides, the bottles and the ingredient out below (the ingredient only when it is a glass bottle).
     */
    private static final class Brewing extends Half implements WorldlyContainer {
        Brewing(BlockEntityType<?> type, BlockPos pos, BlockState state, int size, Map<String, Object> saved) {
            super(type, pos, state, size, saved);
        }

        @Override
        public int[] getSlotsForFace(Direction face) {
            return face == Direction.UP ? new int[] {3}
                    : face == Direction.DOWN ? new int[] {0, 1, 2, 3} : new int[] {0, 1, 2, 4};
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            // The game's answer: `<fuel> <ingredient> <bottle>`.
            String[] takes = askItem("brewing", stack).split(" ");
            if (takes.length < 3) {
                return false;
            }
            return switch (slot) {
                case 4 -> takes[0].equals("true");
                case 3 -> takes[1].equals("true");
                default -> takes[2].equals("true") && getItem(slot).isEmpty();
            };
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction face) {
            return canPlaceItem(slot, stack);
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) {
            return slot != 3 || isItem(stack, "minecraft:glass_bottle");
        }
    }

    /** A crafter ({@code CrafterBlockEntity.canPlaceItem}): no disabled slot, and the smallest stack first. */
    private static final class Crafter extends Half {
        private final boolean[] disabled = new boolean[9];

        Crafter(BlockEntityType<?> type, BlockPos pos, BlockState state, int size, Map<String, Object> saved) {
            super(type, pos, state, size, saved);
            if (saved.get("disabled_slots") instanceof int[] slots) {
                for (int slot : slots) {
                    if (slot >= 0 && slot < disabled.length) {
                        disabled[slot] = true;
                    }
                }
            }
        }

        private boolean off(int slot) {
            return slot < 0 || slot >= disabled.length || disabled[slot];
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (off(slot)) {
                return false;
            }
            ItemStack current = getItem(slot);
            if (current.isEmpty()) {
                return true;
            }
            if (current.getCount() >= current.getMaxStackSize()) {
                return false;
            }
            for (int other = slot + 1; other < getContainerSize(); other++) {
                ItemStack there = getItem(other);
                if (!off(other) && (there.isEmpty()
                        || there.getCount() < current.getCount() && ItemStack.isSameItemSameComponents(there, current))) {
                    return false;
                }
            }
            return true;
        }
    }

    /** A chiseled bookshelf ({@code ChiseledBookShelfBlockEntity}): a book into an empty slot, one to a slot. */
    private static final class Bookshelf extends Half {
        Bookshelf(BlockEntityType<?> type, BlockPos pos, BlockState state, int size, Map<String, Object> saved) {
            super(type, pos, state, size, saved);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return getItem(slot).isEmpty() && Tags.itemIn(BOOKSHELF_BOOKS, stack.getItem());
        }
    }

    /**
     * A composter ({@code ComposterBlock.getContainer}, Fabric's {@code ComposterWrapper}): below level 7 one
     * compostable item at a time goes in from above, composted when the transaction that put it commits (the game
     * then adds a layer by its chance, when the call ends); at level 8 its bone meal comes out below. No more go in
     * this call than layers are left below 7, as each may add one.
     */
    private static final class Composter extends Half implements WorldlyContainer {
        private final int layers;
        private int room;
        private final NonNullList<ItemStack> composted = NonNullList.create();

        Composter(BlockEntityType<?> type, BlockPos pos, BlockState state, int size, Map<String, Object> saved) {
            super(type, pos, state, size, saved);
            layers = saved.get("level") instanceof Number n ? n.intValue() : 7;
            room = Math.max(0, 7 - layers);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int[] getSlotsForFace(Direction face) {
            if (layers == 8 && face == Direction.DOWN || layers < 7 && face == Direction.UP) {
                return new int[] {0};
            }
            return new int[0];
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return layers < 7 && room > 0 && slot == 0 && getItem(0).isEmpty()
                    && askItem("compostable", stack).equals("true");
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction face) {
            return face == Direction.UP && canPlaceItem(slot, stack);
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) {
            return layers == 8 && face == Direction.DOWN && isItem(stack, "minecraft:bone_meal");
        }

        /** The item a committed transaction put in is composted: the slot is free for the next. */
        @Override
        public void setChanged() {
            ItemStack in = getItem(0);
            if (layers < 7 && !in.isEmpty()) {
                composted.add(in.copy());
                room--;
                slots.set(0, ItemStack.EMPTY);
            }
        }

        @Override
        void extra(Map<String, Object> saved) {
            if (!composted.isEmpty()) {
                Map<String, Object> list = new TreeMap<>();
                ContainerHelper.saveAllItems(TagValues.output(list), composted);
                List<?> items = (List<?>) list.get(ContainerHelper.TAG_ITEMS);
                List<Object> stripped = new ArrayList<>();
                for (Object item : items) {
                    if (item instanceof Map<?, ?> m) {
                        Map<Object, Object> copy = new TreeMap<>(m);
                        copy.remove("Slot");
                        stripped.add(copy);
                    }
                }
                saved.put("Composted", stripped);
            }
        }
    }

    /** A double chest: vanilla's {@code CompoundContainer} of its two halves, {@code first}'s slots first. */
    private record DoubleChest(Half first, Half second) implements Container {
        private Container of(int slot) {
            return slot < first.getContainerSize() ? first : second;
        }

        private int in(int slot) {
            return slot < first.getContainerSize() ? slot : slot - first.getContainerSize();
        }

        @Override
        public int getContainerSize() {
            return first.getContainerSize() + second.getContainerSize();
        }

        @Override
        public boolean isEmpty() {
            return first.isEmpty() && second.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return of(slot).getItem(in(slot));
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return of(slot).removeItem(in(slot), count);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return of(slot).removeItemNoUpdate(in(slot));
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            of(slot).setItem(in(slot), stack);
        }

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            first.clearContent();
            second.clearContent();
        }
    }
}
