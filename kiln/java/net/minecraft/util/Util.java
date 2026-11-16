package net.minecraft.util;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Kiln's stand-in for Minecraft's {@code Util}: the helpers mods call while their classes load (Tech Reborn's cables
 * fill their table of connection properties with {@code make}).
 */
public class Util {
    public Util() {}

    /** Milliseconds of a clock that only goes forward (a widget's animation, D-165), as the game's. */
    public static long getMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    /** Nanoseconds of the same clock. */
    public static long getNanos() {
        return System.nanoTime();
    }

    /** Milliseconds since the epoch. */
    public static long getEpochMillis() {
        return System.currentTimeMillis();
    }

    /** {@code object}, once {@code init} has filled it. */
    public static <T> T make(T object, Consumer<? super T> init) {
        init.accept(object);
        return object;
    }

    /**
     * {@code latest} put in place of {@code current}, the file it replaces kept as {@code backup}: a save that a
     * crash half way leaves whole, one way or the other.
     */
    public static void safeReplaceFile(java.nio.file.Path current, java.nio.file.Path latest, java.nio.file.Path backup) {
        try {
            if (java.nio.file.Files.exists(current)) {
                java.nio.file.Files.move(current, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            java.nio.file.Files.move(latest, current, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public static <T> T make(Supplier<T> factory) {
        return factory.get();
    }

    /** A value of {@code property} by its name ({@code true}, {@code north}). */
    @SuppressWarnings("unchecked")
    public static <T extends Comparable<T>> String getPropertyName(Property<T> property, Object value) {
        return property.getName((T) value);
    }

    /** {@code block.mymod.lamp}, as vanilla names a registered thing's translation. */
    public static String makeDescriptionId(String type, Identifier id) {
        return id == null ? type + ".unregistered_sadface"
                : type + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }
}
