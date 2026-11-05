package net.minecraft.world.level.material;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Kiln's stand-in for {@code Fluids}: the game's fluids, registered in {@code BuiltInRegistries.FLUID} as vanilla's
 * are, so a mod's recipe or tank names water and lava by id, and its buckets are the game's. Horizon has the liquids
 * themselves; these only say which is which.
 */
public class Fluids {
    public static final Fluid EMPTY = new Empty();
    public static final FlowingFluid FLOWING_WATER = new Game("water", false);
    public static final FlowingFluid WATER = new Game("water", true);
    public static final FlowingFluid FLOWING_LAVA = new Game("lava", false);
    public static final FlowingFluid LAVA = new Game("lava", true);

    // Registered once they all exist: the registries' start makes the game's buckets, which name these, so a mod that
    // reaches Fluids first finds them made.
    static {
        register("empty", EMPTY);
        register("flowing_water", FLOWING_WATER);
        register("water", WATER);
        register("flowing_lava", FLOWING_LAVA);
        register("lava", LAVA);
    }

    public Fluids() {}

    private static void register(String name, Fluid fluid) {
        Registry.register(BuiltInRegistries.FLUID, Identifier.withDefaultNamespace(name), fluid);
    }

    /** No fluid: what an empty tank holds. */
    private static final class Empty extends Fluid {
        @Override
        protected boolean isEmpty() {
            return true;
        }
    }

    /** Water or lava, as a source or flowing. */
    private static final class Game extends FlowingFluid {
        private final String name;
        private final boolean source;

        Game(String name, boolean source) {
            this.name = name;
            this.source = source;
        }

        @Override
        public Fluid getFlowing() {
            return name.equals("water") ? FLOWING_WATER : FLOWING_LAVA;
        }

        @Override
        public Fluid getSource() {
            return name.equals("water") ? WATER : LAVA;
        }

        @Override
        public Item getBucket() {
            return name.equals("water") ? Items.WATER_BUCKET : Items.LAVA_BUCKET;
        }

        @Override
        public boolean isSame(Fluid fluid) {
            return fluid == getSource() || fluid == getFlowing();
        }

        @Override
        public boolean isSource(FluidState state) {
            return source;
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        protected BlockState createLegacyBlock(FluidState state) {
            return (name.equals("water") ? Blocks.WATER : Blocks.LAVA).defaultBlockState();
        }

        @Override
        public String toString() {
            return "minecraft:" + (source ? "" : "flowing_") + name;
        }
    }
}
