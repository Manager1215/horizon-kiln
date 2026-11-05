package kiln;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopperCollection;

/**
 * The game's registered constants a generated stand-in names (kiln/java-gen): each made by its id, as the game's
 * static initializer names it, and registered, so a mod that reads one gets a value of the right kind rather than
 * null. A component type made here has no codec yet: its values live on the stack only.
 */
public final class Constants {
    private Constants() {}

    @SuppressWarnings("unchecked")
    public static <T> DataComponentType<T> component(String id) {
        Identifier key = Identifier.withDefaultNamespace(id);
        DataComponentType<?> known = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(key);
        if (known != null) {
            return (DataComponentType<T>) known;
        }
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, key, DataComponentType.<T>builder().build());
    }

    public static SoundEvent sound(String id) {
        return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(id));
    }

    public static Item item(String id) {
        return Item.kiln$byId("minecraft:" + id);
    }

    /** The game's copper blocks called {@code name} ({@code Blocks.CUT_COPPER}): each weather state, unwaxed, waxed. */
    public static WeatheringCopperCollection<Block> copperBlocks(String name) {
        return WeatheringCopperCollection.kiln$ids(name).map(Constants::block);
    }

    /** {@link #copperBlocks}'s items ({@code Items.CUT_COPPER}). */
    public static WeatheringCopperCollection<Item> copperItems(String name) {
        return WeatheringCopperCollection.kiln$ids(name).map(Constants::item);
    }

    public static Block block(String id) {
        return Block.kiln$byId("minecraft:" + id);
    }

    /**
     * A Fabric event a generated stand-in names: its listeners are kept, and its invoker calls them in order as
     * Fabric's callbacks do (the first result that isn't PASS for those that answer with an {@code InteractionResult},
     * else the last result). Kiln fires only the events it knows; this one at least takes its listeners.
     */
    @SuppressWarnings("unchecked")
    public static <T> Event<T> event(Class<T> type) {
        return Event.kiln$of(listeners -> (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(listeners, args);
                    }
                    Object result = defaultOf(method);
                    for (T listener : List.copyOf(listeners)) {
                        Object r = method.invoke(listener, args);
                        if (r instanceof InteractionResult ir) {
                            if (!(ir instanceof InteractionResult.Pass)) {
                                return ir;
                            }
                        } else if (r != null) {
                            result = r;
                        }
                    }
                    return result;
                }));
    }

    private static Object defaultOf(Method method) {
        Class<?> r = method.getReturnType();
        if (r == InteractionResult.class) {
            return InteractionResult.PASS;
        }
        if (r == boolean.class) {
            return true;
        }
        if (r.isPrimitive() && r != void.class) {
            return r == float.class ? 0.0F : r == double.class ? 0.0 : r == long.class ? 0L : 0;
        }
        return null;
    }

    /**
     * An instance of {@code type}, an interface a generated stand-in hands out from a static factory
     * ({@code FlammableBlockRegistry.getDefaultInstance()}): each call does nothing, says so once in the log, and gives
     * back the instance itself where the type allows (a registry's chained calls), else a default.
     */
    @SuppressWarnings("unchecked")
    public static <T> T nothing(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> "Kiln's nothing " + type.getName();
                };
            }
            if (method.isDefault()) {
                return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
            }
            Missing.called(type.getName() + "." + method.getName());
            Class<?> r = method.getReturnType();
            if (r.isAssignableFrom(type)) {
                return proxy;
            }
            Object value = defaultOf(method);
            return value instanceof InteractionResult ? null : r == boolean.class ? false : value;
        });
    }

    /**
     * A codec a generated stand-in names ({@code BlockStateProvider.CODEC}): a mod builds its own from it in a static
     * field, which works; reading or writing a value with it fails, and says so once in the log.
     */
    public static <A> Codec<A> codec(String name) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                return missing(name);
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return missing(name);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    /** {@link #codec}'s kind for a map's fields. */
    public static <A> MapCodec<A> mapCodec(String name) {
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.empty();
            }

            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                return missing(name);
            }

            @Override
            public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return prefix.withErrorsFrom(missing(name));
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    /** {@link #codec}'s kind for the network: made and combined, it fails only on a buffer. */
    public static <B, V> StreamCodec<B, V> streamCodec(String name) {
        return StreamCodec.of((buffer, value) -> {
            missing(name);
            throw new UnsupportedOperationException("Kiln has only the name of " + name + " so far");
        }, buffer -> {
            missing(name);
            throw new UnsupportedOperationException("Kiln has only the name of " + name + " so far");
        });
    }

    private static <R> DataResult<R> missing(String name) {
        Missing.called(name);
        return DataResult.error(() -> "Kiln has only the name of " + name + " so far");
    }
}
