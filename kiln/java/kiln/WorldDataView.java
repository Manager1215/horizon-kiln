package kiln;

import java.lang.reflect.Proxy;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;

/**
 * {@code MinecraftServer.getWorldData} (D-211): the world the player is in has been opened before Java starts, so its
 * data says it is initialized ({@code ServerLevelData.isInitialized}: Collective writes its world-creation settings
 * only for a world not yet initialized). Everything else is a name Kiln has only the name of: logged once, and a
 * zero, false or null answered.
 */
public final class WorldDataView {
    private WorldDataView() {}

    public static WorldData create() {
        return create(null);
    }

    /** The same, naming the world by {@code levelName} (its folder) when asked. */
    public static WorldData create(java.util.function.Supplier<String> levelName) {
        ServerLevelData overworld = view(ServerLevelData.class, null, null);
        return view(WorldData.class, overworld, levelName);
    }

    private static <T> T view(Class<T> type, Object overworld, java.util.function.Supplier<String> levelName) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, m, args) -> {
            switch (m.getName()) {
                case "getLevelName" -> {
                    if (levelName != null) {
                        return levelName.get();
                    }
                    return null;
                }
                case "overworldData" -> {
                    return overworld;
                }
                case "isInitialized" -> {
                    return true;
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "toString" -> {
                    return "Kiln's " + type.getSimpleName();
                }
                default -> {
                    if (m.isDefault()) {
                        return java.lang.reflect.InvocationHandler.invokeDefault(proxy, m, args);
                    }
                    Missing.called(m.getDeclaringClass().getName() + "." + m.getName());
                    Class<?> r = m.getReturnType();
                    if (r == boolean.class) {
                        return false;
                    }
                    if (r == void.class || !r.isPrimitive()) {
                        return null;
                    }
                    if (r == long.class) {
                        return 0L;
                    }
                    if (r == float.class) {
                        return 0f;
                    }
                    if (r == double.class) {
                        return 0d;
                    }
                    if (r == char.class) {
                        return (char) 0;
                    }
                    if (r == byte.class) {
                        return (byte) 0;
                    }
                    if (r == short.class) {
                        return (short) 0;
                    }
                    return 0;
                }
            }
        }));
    }
}
