package net.fabricmc.fabric.api.event;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Function;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for Fabric's {@code EventFactory}: a mod's own event, its invoker made by the mod from the array of
 * listeners (made again when one registers). Phases are accepted and ignored, as in {@link Event}.
 */
public final class EventFactory {
    private EventFactory() {}

    public static <T> Event<T> createArrayBacked(Class<? super T> type, Function<T[], T> invokerFactory) {
        return Event.kiln$of(listeners -> invoker(type, listeners, invokerFactory, null));
    }

    public static <T> Event<T> createArrayBacked(Class<T> type, T emptyInvoker, Function<T[], T> invokerFactory) {
        return Event.kiln$of(listeners -> invoker(type, listeners, invokerFactory, emptyInvoker));
    }

    public static <T> Event<T> createWithPhases(Class<? super T> type, Function<T[], T> invokerFactory,
            Identifier... defaultPhases) {
        return createArrayBacked(type, invokerFactory);
    }

    public static boolean isProfilingEnabled() {
        return false;
    }

    /** One invoker for the event's life that hands each call to the mod's invoker over the listeners as they are. */
    @SuppressWarnings("unchecked")
    private static <T> T invoker(Class<? super T> type, List<T> listeners, Function<T[], T> factory, T empty) {
        Object[] made = {null, -1};
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Object target;
            synchronized (made) {
                if ((int) made[1] != listeners.size()) {
                    T[] array = listeners.toArray((T[]) Array.newInstance(type, 0));
                    made[0] = array.length == 0 && empty != null ? empty : factory.apply(array);
                    made[1] = array.length;
                }
                target = made[0];
            }
            if (method.getDeclaringClass() == Object.class && !method.getName().equals("toString")) {
                return method.getName().equals("hashCode") ? System.identityHashCode(proxy) : proxy == args[0];
            }
            try {
                method.trySetAccessible();
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}
