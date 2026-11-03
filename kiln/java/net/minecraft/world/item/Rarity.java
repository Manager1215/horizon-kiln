package net.minecraft.world.item;

import java.util.Locale;
import net.minecraft.ChatFormatting;

/** Kiln's stand-in for {@code Rarity}: the colour of an item's name. */
public enum Rarity {
    COMMON(ChatFormatting.WHITE),
    UNCOMMON(ChatFormatting.YELLOW),
    RARE(ChatFormatting.AQUA),
    EPIC(ChatFormatting.LIGHT_PURPLE);

    private final ChatFormatting color;

    Rarity(ChatFormatting color) {
        this.color = color;
    }

    public ChatFormatting color() {
        return color;
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
