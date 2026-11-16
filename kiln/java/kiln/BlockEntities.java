package kiln;

import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
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
 * game gets the new one ({@code block_entity <x> <y> <z> <nbt>}), and what its screen shows when that changed
 * ({@code data <x> <y> <z> <value>...}, {@code MachineData}).
 *
 * <p>The compounds go both ways as binary NBT in base64 ({@link Nbt}), never as SNBT (D-140). A tick names its compound
 * by a token, which the game gives each one it hands over whole: while the game's is still the one Kiln last had of it
 * (Kiln's save, or the game's compound with that token), the tick sends the token alone, and Kiln ticks the object it
 * keeps with nothing to read. When Kiln no longer keeps that object, it asks the game for the compound ({@code ask
 * <call> ticking}).
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
        /** Its compound as the game has it, in binary ({@link Nbt}). */
        byte[] saved;
        /** The token of the compound the game handed over for a tick, while the game's is still that one; else 0. */
        long token;
        /** What its screen shows, as last sent to the game ({@code MachineData}). */
        int[] data = new int[0];

        Kept(BlockEntity entity, byte[] saved) {
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
                : load(level, pos, level.getBlockState(pos), answer.substring(0, space),
                        Nbt.fromField(answer.substring(space + 1)));
        reached.put(key, k);
        return k == null ? null : k.entity;
    }

    /**
     * The block entity of type {@code type} at {@code pos}, the one ticking: the game handed over its compound
     * ({@code saved}, binary in base64) with the token {@code token}, or only the token ({@code saved} empty) when its
     * compound is still the one Kiln last had of it.
     */
    static BlockEntity handed(ServerLevel level, BlockPos pos, BlockState state, String type, long token, String saved) {
        String key = key(level, pos);
        Kept k = saved.isEmpty() ? kept.get(key) : null;
        if (k != null && k.token == token && !k.entity.isRemoved()
                && BlockEntityType.kiln$byId(type).orElse(null) == k.entity.getType()) {
            k.entity.setLevel(level);
            k.entity.setBlockState(state);
        } else {
            // Handed over whole, or Kiln no longer keeps that object (another world, a block changed near it, a call
            // that failed): the game says what it has.
            k = load(level, pos, state, type, Nbt.fromField(saved.isEmpty() ? Server.ask("ticking") : saved));
        }
        if (k != null) {
            k.token = token;
        }
        reached.put(key, k);
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
            byte[] now = Nbt.write(k.entity.kiln$compound());
            BlockPos p = k.entity.getBlockPos();
            if (!Arrays.equals(now, k.saved)) {
                // The game keeps the token with what Kiln saved, of the one ticking or of another the call reached
                // (a cable's tick that gave a machine energy); one it didn't take gets its compound sent whole.
                k.saved = now;
                Server.effect("block_entity " + p.getX() + " " + p.getY() + " " + p.getZ() + " "
                        + Base64.getEncoder().encodeToString(now));
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
        reached.keySet().forEach(BlockEntities::letGo);
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
        kept.entrySet().removeIf(e -> {
            String[] f = e.getKey().split(" ");
            boolean near = f[0].equals(dimension) && Math.abs(Integer.parseInt(f[1]) - pos.getX()) <= 16
                    && Math.abs(Integer.parseInt(f[2]) - pos.getY()) <= 16
                    && Math.abs(Integer.parseInt(f[3]) - pos.getZ()) <= 16;
            if (near) {
                removed(e.getValue().entity);
            }
            return near;
        });
    }

    /** Kiln no longer keeps the object at {@code key}: let go, as a chunk unloading lets go of its block entities. */
    private static void letGo(String key) {
        Kept k = kept.remove(key);
        if (k != null) {
            removed(k.entity);
        }
    }

    /** The mod's {@code setRemoved}: one that fails is said in Kiln's log, and the object is let go all the same. */
    private static void removed(BlockEntity entity) {
        try {
            entity.setRemoved();
        } catch (RuntimeException | LinkageError e) {
            Host.send("log", "Kiln: " + entity.getClass().getName() + ".setRemoved failed: " + e);
        }
    }

    /** Another world opened. */
    static void forget() {
        kept.clear();
        reached.clear();
        Tags.forget();
    }

    private static Kept load(ServerLevel level, BlockPos pos, BlockState state, String typeId,
            Map<String, Object> compound) {
        BlockEntityType<?> type = BlockEntityType.kiln$byId(typeId).orElse(null);
        if (type == null) {
            return null;
        }
        String key = key(level, pos);
        // Written again by Kiln, to compare with what Kiln wrote.
        byte[] bytes = Nbt.write(compound);
        Kept k = kept.get(key);
        if (k != null && k.entity.getType() == type && !k.entity.isRemoved() && !Arrays.equals(k.saved, bytes)
                && Arrays.equals(withoutSlots(Nbt.compound(k.saved)), withoutSlots(compound))) {
            // Only the container's slots changed (a hopper, a command): they go into the object the mod keeps.
            k.entity.kiln$slots(compound);
            k.saved = bytes;
        }
        if (k == null || k.entity.getType() != type || k.entity.isRemoved() || !Arrays.equals(k.saved, bytes)) {
            if (k != null && !k.entity.isRemoved()) {
                // The object Kiln kept is let go, as a chunk unloading lets go of its block entities.
                removed(k.entity);
            }
            BlockEntity entity = type.create(pos, state);
            entity.setLevel(level);
            entity.kiln$load(compound);
            // As the game's chunk does when a block entity joins it: RebornCore's multiblock parts join their
            // level's registry here (D-161).
            entity.clearRemoved();
            k = new Kept(entity, bytes);
            kept.put(key, k);
        }
        k.entity.setLevel(level);
        k.entity.setBlockState(state);
        return k;
    }

    /** A saved compound without the container's slots, in binary. */
    private static byte[] withoutSlots(Map<String, Object> saved) {
        Map<String, Object> compound = new TreeMap<>(saved);
        compound.remove(ContainerHelper.TAG_ITEMS);
        return Nbt.write(compound);
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
