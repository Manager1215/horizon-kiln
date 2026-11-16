package kiln;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The game's own containers on Kiln's server side (D-144): a chest, a barrel, a hopper, a dispenser, a dropper, a
 * shulker box. Horizon keeps their slots; a mod's code reaches them as Fabric does, through {@code ItemStorage.SIDED}'s
 * fallback, as a {@code Container} Kiln makes for the call: its slots are asked of the game the first time the call
 * reaches it ({@code container <x> <y> <z>}), and what the call changed in them goes back when it ends
 * ({@code container <x> <y> <z> {Before:[...],Items:[...]}}: the game changes only the slots that still hold what
 * Kiln read). A double chest is both halves, its right half first, as vanilla's {@code ChestBlock.getContainer}
 * combines them. Which blocks are containers, and the rules each adds to its slots, is {@link #KINDS}.
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
    }

    /** The game's blocks whose block entity is a {@code Container} with no rules of its own beyond {@link Kind}. */
    private static final Map<String, Kind> KINDS = Map.of(
            "minecraft:chest", Kind.CHEST,
            "minecraft:trapped_chest", Kind.CHEST,
            "minecraft:barrel", Kind.PLAIN,
            "minecraft:hopper", Kind.PLAIN,
            "minecraft:dispenser", Kind.PLAIN,
            "minecraft:dropper", Kind.PLAIN);

    /** The containers this call reached, by dimension and position; null where the game has none. */
    private static final Map<String, Half> reached = new LinkedHashMap<>();

    private GameContainers() {}

    /**
     * The game's container at {@code pos} as a mod's code sees it, or null where there is none (another block, a
     * client's level, no server call running).
     */
    public static Container at(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server) || !Server.inCall()) {
            return null;
        }
        BlockState here = state != null ? state : level.getBlockState(pos);
        String id = here.getBlock().kiln$id().toString();
        Kind kind = id.endsWith("shulker_box") && id.startsWith("minecraft:") ? Kind.SHULKER : KINDS.get(id);
        if (kind == null) {
            return null;
        }
        Half half = half(server, pos, kind);
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
        Half otherHalf = half(server, otherPos, kind);
        if (otherHalf == null) {
            return half;
        }
        return left ? new DoubleChest(otherHalf, half) : new DoubleChest(half, otherHalf);
    }

    /** The call is done: each container whose slots it changed goes back to the game. */
    static void save() {
        for (Half half : reached.values()) {
            if (half != null) {
                half.save();
            }
        }
        reached.clear();
    }

    /** The call failed, so the game keeps its slots as they were. */
    static void drop() {
        reached.clear();
    }

    private static Half half(ServerLevel level, BlockPos pos, Kind kind) {
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
                half = kind == Kind.SHULKER ? new Shulker(pos, size, answer.substring(space + 1))
                        : new Half(pos, size, answer.substring(space + 1));
            } catch (NumberFormatException e) {
                Host.send("log", "the game answered a container with " + answer);
            }
        }
        reached.put(key, half);
        return half;
    }

    /** One block's container: its slots as the game had them when the call reached it, and as they are now. */
    private static class Half implements Container {
        private final BlockPos pos;
        private final NonNullList<ItemStack> slots;
        private final List<ItemStack> before = new ArrayList<>();

        Half(BlockPos pos, int size, String saved) {
            this.pos = pos;
            this.slots = NonNullList.withSize(size, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(TagValues.input(Snbt.compound(saved)), slots);
            for (ItemStack stack : slots) {
                before.add(stack.copy());
            }
        }

        void save() {
            boolean changed = false;
            for (int slot = 0; slot < slots.size(); slot++) {
                changed |= !ItemStack.matches(before.get(slot), slots.get(slot));
            }
            if (!changed) {
                return;
            }
            NonNullList<ItemStack> was = NonNullList.withSize(slots.size(), ItemStack.EMPTY);
            for (int slot = 0; slot < was.size(); slot++) {
                was.set(slot, before.get(slot));
            }
            Map<String, Object> wasSaved = new TreeMap<>();
            ContainerHelper.saveAllItems(TagValues.output(wasSaved), was);
            Map<String, Object> saved = new TreeMap<>();
            ContainerHelper.saveAllItems(TagValues.output(saved), slots);
            saved.put("Before", wasSaved.get(ContainerHelper.TAG_ITEMS));
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
            return "GameContainer[" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "]";
        }
    }

    /** A shulker box: every slot through every face, but no shulker box goes in. */
    private static final class Shulker extends Half implements WorldlyContainer {
        private final int[] all;

        Shulker(BlockPos pos, int size, String saved) {
            super(pos, size, saved);
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
