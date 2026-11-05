package kiln;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Kiln's Fabric resource variants: one per item, as Fabric caches them (Kiln keeps no components on them yet). */
public final class Variants {
    private static final Map<Item, ItemVariant> ITEMS = new ConcurrentHashMap<>();
    private static final Map<Fluid, FluidVariant> FLUIDS = new ConcurrentHashMap<>();

    private Variants() {}

    public static ItemVariant item(Item item) {
        return ITEMS.computeIfAbsent(item, Of::new);
    }

    /** The item's registry entry, or a direct one for an item no registry has. */
    public static Holder<Item> holder(Item item) {
        return BuiltInRegistries.ITEM.get(item.kiln$id()).<Holder<Item>>map(h -> h).orElseGet(() -> Holder.direct(item));
    }

    /**
     * A fluid's variant, one per fluid, as Fabric's: the empty fluid (or null) is the blank one, and a flowing fluid's
     * is its source's.
     */
    public static FluidVariant fluid(Fluid fluid) {
        Fluid still = fluid == null ? Fluids.EMPTY : fluid;
        if (still instanceof FlowingFluid flowing && !still.defaultFluidState().isSource()) {
            still = flowing.getSource();
        }
        return FLUIDS.computeIfAbsent(still, OfFluid::new);
    }

    private record OfFluid(Fluid fluid) implements FluidVariant {
        @Override
        public boolean isBlank() {
            return fluid == Fluids.EMPTY;
        }

        @Override
        public Fluid getObject() {
            return fluid;
        }

        @Override
        public DataComponentPatch getComponentsPatch() {
            return DataComponentPatch.EMPTY;
        }

        @Override
        public DataComponentMap getComponents() {
            return DataComponentMap.EMPTY;
        }

        @Override
        public FluidVariant withComponents(DataComponentPatch patch) {
            return this;
        }
    }

    private record Of(Item item) implements ItemVariant {
        @Override
        public boolean isBlank() {
            return item == Items.AIR;
        }

        @Override
        public Item getObject() {
            return item;
        }

        @Override
        public DataComponentPatch getComponentsPatch() {
            return DataComponentPatch.EMPTY;
        }

        @Override
        public DataComponentMap getComponents() {
            return DataComponentMap.EMPTY;
        }

        @Override
        public ItemVariant withComponents(DataComponentPatch patch) {
            return this;
        }

        @Override
        public String toString() {
            return "ItemVariant{" + item + "}";
        }
    }
}
