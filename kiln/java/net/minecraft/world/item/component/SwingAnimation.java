package net.minecraft.world.item.component;

import net.minecraft.world.item.SwingAnimationType;

/** Kiln's stand-in for {@code SwingAnimation}: an item's swing, its kind and how many ticks it takes. */
public record SwingAnimation(SwingAnimationType type, int duration) {
    public static final SwingAnimation DEFAULT = new SwingAnimation(SwingAnimationType.WHACK, 6);
}
