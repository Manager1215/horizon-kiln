package kiln;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Horizon's own fluid holders as Fabric's {@code FluidStorage.SIDED} (D-198): a cauldron, and the tanks a native mod
 * declared on its block (Horizon's API 54). Fabric's fallback finds them where no Java mod's block entity is. The tanks
 * are asked of the game the first time a call reaches them ({@code fluid_tanks <x> <y> <z> <face>}, answered as
 * {@code <fluid|-> <amount> <capacity> <fill> <drain>} per tank, {@code ;} between, in millibuckets); what a committed
 * transaction changed goes back when it closes ({@code fluid <x> <y> <z> <face> <fluid> <millibuckets>}, put in when
 * positive, taken out when negative), and the game does it through {@code native_fluid_insert} and
 * {@code native_fluid_extract}. Horizon's fluids are water and lava, a millibucket is 81 droplets, and a cauldron fills
 * and drains by thirds of a bucket, lava only whole, as Horizon's does.
 */
public final class HorizonFluids {
    private static final long DROPLETS = FluidConstants.BUCKET / 1000;
    /** The storages this call reached, by dimension, position and face; null where the game has no tanks. */
    private static final Map<String, Tanks> reached = new HashMap<>();
    private static String reachedIn;

    private HorizonFluids() {}

    /** The game's fluid holder at {@code pos} through {@code face}, or null: another block, no call running. */
    public static Storage<FluidVariant> at(Level level, BlockPos pos, BlockState state, Direction face) {
        if (!(level instanceof ServerLevel server) || !Server.inCall() || face == null) {
            return null;
        }
        String call = Server.currentCall();
        if (!call.equals(reachedIn)) {
            reached.clear();
            reachedIn = call;
        }
        String key = server.dimension().identifier() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " "
                + face.getName();
        if (reached.containsKey(key)) {
            return reached.get(key);
        }
        BlockState here = state != null ? state : level.getBlockState(pos);
        String id = here.getBlock().kiln$id().toString();
        if (id.endsWith("air") && id.startsWith("minecraft:")) {
            return null;
        }
        String answer = Server.ask("fluid_tanks", Integer.toString(pos.getX()), Integer.toString(pos.getY()),
                Integer.toString(pos.getZ()), face.getName());
        Tanks tanks = null;
        if (!answer.isEmpty() && !answer.equals("none")) {
            tanks = Tanks.parse(pos, face, id.startsWith("minecraft:") && id.endsWith("cauldron"), answer);
        }
        reached.put(key, tanks);
        return tanks;
    }

    /** {@code minecraft:water} or {@code minecraft:lava} for Horizon's tanks, or null for any other fluid. */
    static String horizonName(FluidVariant variant) {
        Fluid fluid = variant.getFluid();
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
            return "minecraft:water";
        }
        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) {
            return "minecraft:lava";
        }
        return null;
    }

    /** A block's tanks through one face. */
    static final class Tanks extends SnapshotParticipant<long[][]> implements Storage<FluidVariant> {
        private final BlockPos pos;
        private final Direction face;
        private final boolean cauldron;
        private final List<Tank> tanks;
        /** Each tank's fluid (0 none, 1 water, 2 lava) and amount as the game has it now. */
        private long[][] told;

        private Tanks(BlockPos pos, Direction face, boolean cauldron, List<Tank> tanks) {
            this.pos = pos;
            this.face = face;
            this.cauldron = cauldron;
            this.tanks = tanks;
            this.told = createSnapshot();
        }

        static Tanks parse(BlockPos pos, Direction face, boolean cauldron, String answer) {
            List<Tank> tanks = new ArrayList<>();
            for (String part : answer.split(";")) {
                String[] f = part.trim().split(" ");
                if (f.length != 5) {
                    continue;
                }
                try {
                    tanks.add(new Tank(fluidOf(f[0]), Long.parseLong(f[1]), Long.parseLong(f[2]), f[3].equals("1"),
                            f[4].equals("1")));
                } catch (NumberFormatException e) {
                    Host.send("log", "the game answered a fluid tank with " + part);
                }
            }
            return tanks.isEmpty() ? null : new Tanks(pos, face, cauldron, tanks);
        }

        private static int fluidOf(String name) {
            return switch (name) {
                case "minecraft:water" -> 1;
                case "minecraft:lava" -> 2;
                default -> 0;
            };
        }

        private static FluidVariant variant(int fluid) {
            return switch (fluid) {
                case 1 -> FluidVariant.of(Fluids.WATER);
                case 2 -> FluidVariant.of(Fluids.LAVA);
                default -> FluidVariant.blank();
            };
        }

        /** What of {@code wanted} millibuckets a cauldron moves: whole thirds of a bucket, lava only whole. */
        private long cauldronStep(int fluid, long have, long wanted, boolean filling) {
            if (fluid == 2) {
                return wanted >= 1000 ? 1000 : 0;
            }
            // Rounded: a third is 333 mB, and 333 * 3 / 1000 truncates to level 0.
            int level = (int) Math.min(3, (have * 3 + 500) / 1000);
            long moved = 0;
            while (filling ? level < 3 : level > 0) {
                int next = filling ? level + 1 : level - 1;
                long step = Math.abs(next * 1000L / 3 - level * 1000L / 3);
                if (moved + step > wanted) {
                    break;
                }
                moved += step;
                level = next;
            }
            return moved;
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int fluid = fluidOf(String.valueOf(horizonName(resource)));
            if (fluid == 0) {
                return 0;
            }
            long left = maxAmount / DROPLETS;
            long done = 0;
            // As Horizon fills: the tanks holding the same fluid first, then the empty ones.
            for (int pass = 0; pass < 2 && left > 0; pass++) {
                for (Tank tank : tanks) {
                    if (!tank.canFill || left <= 0 || (pass == 0 ? tank.fluid != fluid : tank.fluid != 0)) {
                        continue;
                    }
                    long room = tank.capacity - tank.amount;
                    long put = Math.min(room, left);
                    if (cauldron) {
                        put = cauldronStep(fluid, tank.amount, put, true);
                    }
                    if (put <= 0) {
                        continue;
                    }
                    updateSnapshots(transaction);
                    tank.fluid = fluid;
                    tank.amount += put;
                    left -= put;
                    done += put;
                }
            }
            return done * DROPLETS;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int fluid = fluidOf(String.valueOf(horizonName(resource)));
            long done = 0;
            for (Tank tank : tanks) {
                done += tank.extract(this, fluid, maxAmount / DROPLETS - done, transaction);
            }
            return done * DROPLETS;
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            List<StorageView<FluidVariant>> views = new ArrayList<>();
            for (Tank tank : tanks) {
                views.add(new View(this, tank));
            }
            return Collections.unmodifiableList(views).iterator();
        }

        @Override
        protected long[][] createSnapshot() {
            long[][] s = new long[tanks.size()][];
            for (int i = 0; i < s.length; i++) {
                s[i] = new long[] {tanks.get(i).fluid, tanks.get(i).amount};
            }
            return s;
        }

        @Override
        protected void readSnapshot(long[][] snapshot) {
            for (int i = 0; i < snapshot.length; i++) {
                tanks.get(i).fluid = (int) snapshot[i][0];
                tanks.get(i).amount = snapshot[i][1];
            }
        }

        /** The commit goes to the game: what each fluid gained or lost since the game last heard. */
        @Override
        protected void onFinalCommit() {
            long[][] now = createSnapshot();
            long water = 0;
            long lava = 0;
            for (int i = 0; i < now.length; i++) {
                water += (now[i][0] == 1 ? now[i][1] : 0) - (told[i][0] == 1 ? told[i][1] : 0);
                lava += (now[i][0] == 2 ? now[i][1] : 0) - (told[i][0] == 2 ? told[i][1] : 0);
            }
            told = now;
            send("minecraft:water", water);
            send("minecraft:lava", lava);
        }

        private void send(String fluid, long millibuckets) {
            if (millibuckets != 0) {
                Server.effect("fluid " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + face.getName() + " "
                        + fluid + " " + millibuckets);
            }
        }
    }

    /** One tank: its fluid (0 none, 1 water, 2 lava), amount and capacity in millibuckets, and this face's leave. */
    private static final class Tank {
        int fluid;
        long amount;
        final long capacity;
        final boolean canFill;
        final boolean canDrain;

        Tank(int fluid, long amount, long capacity, boolean canFill, boolean canDrain) {
            this.fluid = amount > 0 ? fluid : 0;
            this.amount = amount;
            this.capacity = capacity;
            this.canFill = canFill;
            this.canDrain = canDrain;
        }

        long extract(Tanks owner, int wanted, long max, TransactionContext transaction) {
            if (!canDrain || max <= 0 || fluid == 0 || fluid != wanted || amount <= 0) {
                return 0;
            }
            long take = Math.min(amount, max);
            if (owner.cauldron) {
                take = owner.cauldronStep(fluid, amount, take, false);
            }
            if (take <= 0) {
                return 0;
            }
            owner.updateSnapshots(transaction);
            amount -= take;
            if (amount == 0) {
                fluid = 0;
            }
            return take;
        }
    }

    /** A tank as Fabric's views see it, in droplets. */
    private record View(Tanks owner, Tank tank) implements StorageView<FluidVariant> {
        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int fluid = Tanks.fluidOf(String.valueOf(horizonName(resource)));
            return tank.extract(owner, fluid, maxAmount / DROPLETS, transaction) * DROPLETS;
        }

        @Override
        public boolean isResourceBlank() {
            return tank.fluid == 0;
        }

        @Override
        public FluidVariant getResource() {
            return Tanks.variant(tank.fluid);
        }

        @Override
        public long getAmount() {
            return tank.amount * DROPLETS;
        }

        @Override
        public long getCapacity() {
            return tank.capacity * DROPLETS;
        }
    }
}
