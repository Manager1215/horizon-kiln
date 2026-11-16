package net.minecraft.world.item;

/** Kiln's stand-in for {@code TooltipFlag}: whether a tooltip is advanced (F3+H) and the player in creative. */
public interface TooltipFlag extends net.fabricmc.fabric.api.item.v1.FabricTooltipFlag {
    Default NORMAL = new Default(false, false);
    Default ADVANCED = new Default(true, false);

    boolean isAdvanced();

    boolean isCreative();

    /** The game's flags: advanced or not, creative or not. */
    record Default(boolean advanced, boolean creative) implements TooltipFlag {
        @Override
        public boolean isAdvanced() {
            return advanced;
        }

        @Override
        public boolean isCreative() {
            return creative;
        }

        public Default asCreative() {
            return new Default(advanced, true);
        }
    }
}
