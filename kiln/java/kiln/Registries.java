package kiln;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Kiln's registries by their key: the ones {@code BuiltInRegistries} holds, hand-written or generated. */
public final class Registries {
    private Registries() {}

    /** The registry under {@code key}, or null when Kiln has none. */
    @SuppressWarnings("unchecked")
    public static <T> Registry<T> of(ResourceKey<? extends Registry<T>> key) {
        for (Field field : BuiltInRegistries.class.getFields()) {
            try {
                if (Modifier.isStatic(field.getModifiers()) && field.get(null) instanceof Registry<?> r
                        && r.key().equals(key)) {
                    return (Registry<T>) r;
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    /** The entry {@code id} of the registry under {@code key}: its holder, or a direct one Kiln can't find. */
    @SuppressWarnings("unchecked")
    public static <T> Holder<T> holder(ResourceKey<? extends Registry<T>> key, Identifier id) {
        Registry<T> registry = of(key);
        return registry == null ? null : registry.get(id).map(h -> (Holder<T>) h).orElse(null);
    }
}
