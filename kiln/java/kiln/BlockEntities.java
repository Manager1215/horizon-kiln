package kiln;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The mods' block entities on Kiln's server side. Horizon keeps each one in the world with its saved compound (what
 * {@code saveAdditional} wrote, and a container's slots as {@code Items}); Kiln keeps the mod's object, as vanilla's
 * level would, and uses it again while the game's compound is the one it last saved, so fields a mod doesn't save
 * live on between ticks. When only the slots changed (a hopper, a command), they go into that object. A call that reaches a
 * block entity ({@code getBlockEntity}, or its tick) saves it when the call ends: if that changed its compound, the
 * game gets the new one ({@code block_entity <x> <y> <z> <snbt>}), and what its screen shows when that changed
 * ({@code data <x> <y> <z> <value>...}, {@code MachineData}).
 */
public final class BlockEntities {
    /** Positions whose object Kiln keeps: past that, the oldest is loaded again from its compound when next met. */
    private static final int KEPT = 10_000;
    private static final Map<String, Kept> kept = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Kept> eldest) {
            return size() > KEPT;
        }
    };
    /** The block entities this call reached, by position; null where there is none. */
    private static final Map<String, Kept> reached = new LinkedHashMap<>();

    private BlockEntities() {}

    /** A mod's block entity and its compound as the game has it. */
    private static final class Kept {
        final BlockEntity entity;
        String saved;
        /** What its screen shows, as last sent to the game ({@code MachineData}). */
        int[] data = new int[0];

        Kept(BlockEntity entity, String saved) {
            this.entity = entity;
            this.saved = saved;
        }
    }

    /** The mod's block entity at {@code pos}, asked of the game the first time this call reaches it. */
    public static BlockEntity at(ServerLevel level, BlockPos pos) {
        String key = key(level, pos);
        if (reached.containsKey(key)) {
            Kept k = reached.get(key);
            return k == null ? null : k.entity;
        }
        String answer = Server.ask("block_entity", Integer.toString(pos.getX()), Integer.toString(pos.getY()),
                Integer.toString(pos.getZ()));
        int space = answer.indexOf(' ');
        Kept k = space < 0 ? null
                : load(level, pos, level.getBlockState(pos), answer.substring(0, space), answer.substring(space + 1));
        reached.put(key, k);
        return k == null ? null : k.entity;
    }

    /** The block entity of type {@code type} at {@code pos}, whose compound the game handed over: the one ticking. */
    static BlockEntity handed(ServerLevel level, BlockPos pos, BlockState state, String type, String saved) {
        Kept k = load(level, pos, state, type, saved);
        reached.put(key(level, pos), k);
        return k == null ? null : k.entity;
    }

    /** The block at {@code pos} became {@code state} during the call: its block entity follows, as in a chunk. */
    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState state) {
        Kept k = reached.get(key(level, pos));
        if (k != null && k.entity.isValidBlockState(state)) {
            k.entity.setBlockState(state);
        }
    }

    /** The call is done: each block entity it reached whose compound changed goes back to the game. */
    static void save() {
        for (Kept k : reached.values()) {
            if (k == null) {
                continue;
            }
            String now = k.entity.kiln$save();
            BlockPos p = k.entity.getBlockPos();
            if (!now.equals(k.saved)) {
                k.saved = now;
                Server.effect("block_entity " + p.getX() + " " + p.getY() + " " + p.getZ() + " " + now);
            }
            int[] data = MachineData.of(k.entity);
            if (!Arrays.equals(data, k.data)) {
                k.data = data;
                StringBuilder effect = new StringBuilder("data " + p.getX() + " " + p.getY() + " " + p.getZ());
                for (int value : data) {
                    effect.append(' ').append(value);
                }
                Server.effect(effect.toString());
            }
        }
        reached.clear();
    }

    /** The call failed, so the game kept its compounds: the objects it reached are loaded from them again. */
    static void drop() {
        reached.keySet().forEach(kept::remove);
        reached.clear();
    }

    /**
     * A player placed or broke a block at {@code x y z} in {@code dimension}: the block entities Kiln keeps within 16
     * blocks of it are loaded again from their compounds when next met, as a chunk loading would. Kiln hears no other
     * change to the blocks in the game's chunks (a mod that needs them has a Mixin there, as RebornCore does), and a
     * mod's block entity checks the blocks around it when it loads: a multiblock machine then finds the casings a
     * player built after it.
     */
    static void changedNear(String dimension, BlockPos pos) {
        kept.keySet().removeIf(key -> {
            String[] f = key.split(" ");
            return f[0].equals(dimension) && Math.abs(Integer.parseInt(f[1]) - pos.getX()) <= 16
                    && Math.abs(Integer.parseInt(f[2]) - pos.getY()) <= 16
                    && Math.abs(Integer.parseInt(f[3]) - pos.getZ()) <= 16;
        });
    }

    /** Another world opened. */
    static void forget() {
        kept.clear();
        reached.clear();
        Tags.forget();
    }

    private static Kept load(ServerLevel level, BlockPos pos, BlockState state, String typeId, String saved) {
        BlockEntityType<?> type = BlockEntityType.kiln$byId(typeId).orElse(null);
        if (type == null) {
            return null;
        }
        String key = key(level, pos);
        // Written again by Kiln, to compare with what Kiln wrote.
        String compound = Snbt.write(Snbt.compound(saved));
        Kept k = kept.get(key);
        if (k != null && k.entity.getType() == type && !k.entity.isRemoved() && !k.saved.equals(compound)
                && withoutSlots(k.saved).equals(withoutSlots(compound))) {
            // Only the container's slots changed (a hopper, a command): they go into the object the mod keeps.
            k.entity.kiln$slots(Snbt.compound(compound));
            k.saved = compound;
        }
        if (k == null || k.entity.getType() != type || k.entity.isRemoved() || !k.saved.equals(compound)) {
            BlockEntity entity = type.create(pos, state);
            entity.setLevel(level);
            entity.kiln$load(compound);
            k = new Kept(entity, compound);
            kept.put(key, k);
        }
        k.entity.setLevel(level);
        k.entity.setBlockState(state);
        return k;
    }

    /** A saved compound without the container's slots, as text. */
    private static String withoutSlots(String saved) {
        Map<String, Object> compound = Snbt.compound(saved);
        compound.remove(ContainerHelper.TAG_ITEMS);
        return Snbt.write(compound);
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
