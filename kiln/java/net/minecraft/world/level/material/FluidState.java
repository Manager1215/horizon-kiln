package net.minecraft.world.level.material;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Kiln's stand-in for {@code FluidState}: a fluid as a block holds it. Kiln keeps one per fluid, its default (a source,
 * or the flowing liquid): Horizon has the liquids in the world, with their levels.
 */
public final class FluidState {
    private final Fluid fluid;

    /** Kiln's own: the default state of {@code fluid}. */
    FluidState(Fluid fluid) {
        this.fluid = fluid;
    }

    public Fluid getType() {
        return fluid;
    }

    public boolean isSource() {
        return fluid.isSource(this);
    }

    public boolean isSourceOfType(Fluid type) {
        return fluid == type && isSource();
    }

    public boolean isEmpty() {
        return fluid.isEmpty();
    }

    public int getAmount() {
        return fluid.getAmount(this);
    }

    public boolean is(Fluid type) {
        return fluid == type;
    }

    public boolean is(net.minecraft.tags.TagKey<Fluid> tag) {
        return fluid.is(tag);
    }

    public BlockState createLegacyBlock() {
        return fluid.createLegacyBlock(this);
    }
}
