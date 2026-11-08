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

    /** {@code object}, once {@code init} has filled it. */
    public static <T> T make(T object, Consumer<? super T> init) {
        init.accept(object);
        return object;
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
