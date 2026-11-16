package net.minecraft.world.item;

import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code SwingAnimationType}: how an arm swings with what it holds. */
public enum SwingAnimationType implements StringRepresentable {
    NONE("none"),
    WHACK("whack"),
    STAB("stab");

    private final String name;

    SwingAnimationType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
