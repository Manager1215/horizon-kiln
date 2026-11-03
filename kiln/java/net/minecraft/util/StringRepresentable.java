package net.minecraft.util;

/** Kiln's stand-in for {@code StringRepresentable}: a value with the name the game's data writes for it. */
public interface StringRepresentable {
    String getSerializedName();
}
