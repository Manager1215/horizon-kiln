package net.minecraft.world.effect;

import net.minecraft.ChatFormatting;

/** Kiln's stand-in for {@code MobEffectCategory}: whether an effect helps, harms or neither. */
public enum MobEffectCategory {
    BENEFICIAL(ChatFormatting.BLUE),
    HARMFUL(ChatFormatting.RED),
    NEUTRAL(ChatFormatting.BLUE);

    private final ChatFormatting tooltipFormatting;

    MobEffectCategory(ChatFormatting tooltipFormatting) {
        this.tooltipFormatting = tooltipFormatting;
    }

    /** The colour of its line in a potion's tooltip, as the game's. */
    public ChatFormatting getTooltipFormatting() {
        return tooltipFormatting;
    }
}
