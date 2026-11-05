package kiln;

import java.lang.reflect.Method;
import java.util.Arrays;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

/**
 * What a machine's screen shows, for the game to send to the player who has it open (a menu's data slots, 16 bits
 * each): {@code [energy in thousandths of its capacity, progress in hundredths or -1, energy, capacity]}, the last two
 * each as two 15-bit halves, low first; then, for a machine with a tank, {@code [the tank's fill in thousandths, its
 * fluid's colour (4 bits each of red, green, blue), amount, capacity]}, in millibuckets, again as halves. Energy is what
 * Team Reborn Energy's lookup ({@code teamreborn:sided_energy}, the energy API of Fabric's tech mods) finds at the
 * machine; progress, a machine's {@code getProgressScaled(int)} (Tech Reborn's); the tank, the first part of what
 * Fabric's {@code FluidStorage.SIDED} finds there. A block entity with none of them has none.
 */
public final class MachineData {
    private static final Identifier ENERGY = Identifier.parse("teamreborn:sided_energy");
    private static final int[] NONE = new int[0];

    private MachineData() {}

    public static int[] of(BlockEntity entity) {
        Level level = entity.getLevel();
        long energy = -1;
        long capacity = 0;
        BlockApiLookup<?, ?> lookup = level == null ? null : ApiLookups.existingBlock(ENERGY);
        if (lookup != null) {
            try {
                Object storage = lookup.find(level, entity.getBlockPos(), entity.getBlockState(), entity, null);
                if (storage != null) {
                    energy = (Long) lookup.apiClass().getMethod("getAmount").invoke(storage);
                    capacity = (Long) lookup.apiClass().getMethod("getCapacity").invoke(storage);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                Missing.called("a machine's energy (" + e + ")");
                energy = -1;
            }
        }
        int progress = progress(entity);
        int[] tank = level == null ? NONE : tank(level, entity);
        if (energy < 0 && progress < 0 && tank.length == 0) {
            return NONE;
        }
        energy = Math.max(0, energy);
        int share = capacity > 0 ? (int) Math.min(1000, energy * 1000 / capacity) : 0;
        int[] shown = {share, progress, half(energy, 0), half(energy, 1), half(capacity, 0), half(capacity, 1)};
        if (tank.length == 0) {
            return shown;
        }
        int[] both = Arrays.copyOf(shown, shown.length + tank.length);
        System.arraycopy(tank, 0, both, shown.length, tank.length);
        return both;
    }

    /** The machine's tank, as its screen shows it; none when it has none. */
    private static int[] tank(Level level, BlockEntity entity) {
        Storage<FluidVariant> storage;
        try {
            storage = FluidStorage.SIDED.find(level, entity.getBlockPos(), entity.getBlockState(), entity, null);
        } catch (RuntimeException e) {
            Missing.called("a machine's tank (" + e + ")");
            return NONE;
        }
        if (storage == null) {
            return NONE;
        }
        for (StorageView<FluidVariant> view : storage) {
            long capacity = view.getCapacity();
            if (capacity <= 0) {
                continue;
            }
            long amount = view.isResourceBlank() ? 0 : view.getAmount();
            int share = (int) Math.min(1000, amount * 1000 / capacity);
            long millibuckets = amount * 1000 / FluidConstants.BUCKET;
            long most = capacity * 1000 / FluidConstants.BUCKET;
            return new int[] {share, colour(view.getResource()), half(millibuckets, 0), half(millibuckets, 1),
                    half(most, 0), half(most, 1)};
        }
        return NONE;
    }

    /**
     * A fluid's colour on the screen, 4 bits each of red, green and blue: the game's water blue, its lava orange, a
     * mod's fluid one of its own from its id (Kiln draws no fluid textures yet).
     */
    static int colour(FluidVariant fluid) {
        if (fluid.isBlank()) {
            return 0;
        }
        if (fluid.isOf(Fluids.WATER)) {
            return 0x37E;
        }
        if (fluid.isOf(Fluids.LAVA)) {
            return 0xF60;
        }
        int hash = String.valueOf(BuiltInRegistries.FLUID.getKey(fluid.getFluid())).hashCode();
        return (4 + (hash & 7)) << 8 | (4 + (hash >> 3 & 7)) << 4 | (4 + (hash >> 6 & 7));
    }

    private static int half(long value, int which) {
        long clamped = Math.min(value, (1L << 30) - 1);
        return (int) (clamped >> (15 * which)) & 0x7FFF;
    }

    private static int progress(BlockEntity entity) {
        try {
            Method method = entity.getClass().getMethod("getProgressScaled", int.class);
            Object value = method.invoke(entity, 100);
            return value instanceof Integer i ? Math.max(0, Math.min(100, i)) : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }
}
