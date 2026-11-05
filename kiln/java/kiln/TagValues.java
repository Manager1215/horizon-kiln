package kiln;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Vanilla's {@code TagValueOutput} and {@code TagValueInput} over Kiln's tags ({@link Snbt}): what a block entity
 * saves is the compound Horizon keeps in the world, under the names and types the mod wrote, as the game's would be.
 * A codec's value goes through JSON (DataFixerUpper's {@code JsonOps}, the game's {@code NbtOps} being Mojang's), so
 * its numbers keep their types and its booleans become bytes, as NBT has them.
 */
public final class TagValues {
    private static final HolderLookup.Provider LOOKUP = new HolderLookup.Provider() {};

    private TagValues() {}

    /** Writes into {@code compound}. */
    public static ValueOutput output(Map<String, Object> compound) {
        return new Output(compound);
    }

    /** Reads {@code compound}. */
    public static ValueInput input(Map<String, Object> compound) {
        return new Input(compound);
    }

    private record Output(Map<String, Object> map) implements ValueOutput {
        @Override
        public <T> void store(String key, Codec<T> codec, T value) {
            codec.encodeStart(JsonOps.INSTANCE, value).result().map(TagValues::tag).ifPresent(t -> map.put(key, t));
        }

        @Override
        public <T> void storeNullable(String key, Codec<T> codec, T value) {
            if (value != null) {
                store(key, codec, value);
            }
        }

        @Override
        public <T> void store(MapCodec<T> codec, T value) {
            codec.codec().encodeStart(JsonOps.INSTANCE, value).result()
                    .map(TagValues::tag)
                    .filter(Map.class::isInstance)
                    .ifPresent(t -> ((Map<?, ?>) t).forEach((k, v) -> map.put((String) k, v)));
        }

        @Override
        public void putBoolean(String key, boolean value) {
            map.put(key, (byte) (value ? 1 : 0));
        }

        @Override
        public void putByte(String key, byte value) {
            map.put(key, value);
        }

        @Override
        public void putShort(String key, short value) {
            map.put(key, value);
        }

        @Override
        public void putInt(String key, int value) {
            map.put(key, value);
        }

        @Override
        public void putLong(String key, long value) {
            map.put(key, value);
        }

        @Override
        public void putFloat(String key, float value) {
            map.put(key, value);
        }

        @Override
        public void putDouble(String key, double value) {
            map.put(key, value);
        }

        @Override
        public void putString(String key, String value) {
            map.put(key, value);
        }

        @Override
        public void putIntArray(String key, int[] value) {
            map.put(key, value.clone());
        }

        @Override
        public ValueOutput child(String key) {
            Map<String, Object> child = new TreeMap<>();
            map.put(key, child);
            return new Output(child);
        }

        @Override
        public ValueOutputList childrenList(String key) {
            List<Object> list = new ArrayList<>();
            map.put(key, list);
            return new ValueOutputList() {
                @Override
                public ValueOutput addChild() {
                    Map<String, Object> child = new TreeMap<>();
                    list.add(child);
                    return new Output(child);
                }

                @Override
                public void discardLast() {
                    list.removeLast();
                }

                @Override
                public boolean isEmpty() {
                    return list.isEmpty();
                }
            };
        }

        @Override
        public <T> TypedOutputList<T> list(String key, Codec<T> codec) {
            List<Object> list = new ArrayList<>();
            map.put(key, list);
            return new TypedOutputList<>() {
                @Override
                public void add(T value) {
                    codec.encodeStart(JsonOps.INSTANCE, value).result().map(TagValues::tag).ifPresent(list::add);
                }

                @Override
                public boolean isEmpty() {
                    return list.isEmpty();
                }
            };
        }

        @Override
        public void discard(String key) {
            map.remove(key);
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }
    }

    private record Input(Map<String, Object> map) implements ValueInput {
        @Override
        public <T> Optional<T> read(String key, Codec<T> codec) {
            Object tag = map.get(key);
            return tag == null ? Optional.empty() : codec.parse(JsonOps.INSTANCE, json(tag)).result();
        }

        @Override
        public <T> Optional<T> read(MapCodec<T> codec) {
            return codec.codec().parse(JsonOps.INSTANCE, json(map)).result();
        }

        @Override
        public Optional<ValueInput> child(String key) {
            return map.get(key) instanceof Map<?, ?> child ? Optional.of(new Input(compound(child))) : Optional.empty();
        }

        @Override
        public ValueInput childOrEmpty(String key) {
            return child(key).orElseGet(() -> new Input(new TreeMap<>()));
        }

        @Override
        public Optional<ValueInputList> childrenList(String key) {
            if (!(map.get(key) instanceof List<?> list)) {
                return Optional.empty();
            }
            List<ValueInput> children = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> child) {
                    children.add(new Input(compound(child)));
                }
            }
            return Optional.of(children(children));
        }

        @Override
        public ValueInputList childrenListOrEmpty(String key) {
            return childrenList(key).orElseGet(() -> children(List.of()));
        }

        @Override
        public <T> Optional<TypedInputList<T>> list(String key, Codec<T> codec) {
            if (!(map.get(key) instanceof List<?> list)) {
                return Optional.empty();
            }
            List<T> values = new ArrayList<>();
            for (Object o : list) {
                codec.parse(JsonOps.INSTANCE, json(o)).result().ifPresent(values::add);
            }
            return Optional.of(typed(values));
        }

        @Override
        public <T> TypedInputList<T> listOrEmpty(String key, Codec<T> codec) {
            return list(key, codec).orElseGet(() -> typed(List.of()));
        }

        @Override
        public boolean getBooleanOr(String key, boolean fallback) {
            return map.get(key) instanceof Number n ? n.byteValue() != 0 : fallback;
        }

        @Override
        public byte getByteOr(String key, byte fallback) {
            return map.get(key) instanceof Number n ? n.byteValue() : fallback;
        }

        @Override
        public int getShortOr(String key, short fallback) {
            return map.get(key) instanceof Number n ? n.shortValue() : fallback;
        }

        @Override
        public Optional<Integer> getInt(String key) {
            return map.get(key) instanceof Number n ? Optional.of(n.intValue()) : Optional.empty();
        }

        @Override
        public int getIntOr(String key, int fallback) {
            return getInt(key).orElse(fallback);
        }

        @Override
        public long getLongOr(String key, long fallback) {
            return getLong(key).orElse(fallback);
        }

        @Override
        public Optional<Long> getLong(String key) {
            return map.get(key) instanceof Number n ? Optional.of(n.longValue()) : Optional.empty();
        }

        @Override
        public float getFloatOr(String key, float fallback) {
            return map.get(key) instanceof Number n ? n.floatValue() : fallback;
        }

        @Override
        public double getDoubleOr(String key, double fallback) {
            return map.get(key) instanceof Number n ? n.doubleValue() : fallback;
        }

        @Override
        public Optional<String> getString(String key) {
            return map.get(key) instanceof String s ? Optional.of(s) : Optional.empty();
        }

        @Override
        public String getStringOr(String key, String fallback) {
            return getString(key).orElse(fallback);
        }

        @Override
        public Optional<int[]> getIntArray(String key) {
            return map.get(key) instanceof int[] a ? Optional.of(a.clone()) : Optional.empty();
        }

        @Override
        public HolderLookup.Provider lookup() {
            return LOOKUP;
        }
    }

    private static ValueInput.ValueInputList children(List<ValueInput> children) {
        return new ValueInput.ValueInputList() {
            @Override
            public Iterator<ValueInput> iterator() {
                return children.iterator();
            }

            @Override
            public boolean isEmpty() {
                return children.isEmpty();
            }

            @Override
            public Stream<ValueInput> stream() {
                return children.stream();
            }
        };
    }

    private static <T> ValueInput.TypedInputList<T> typed(List<T> values) {
        return new ValueInput.TypedInputList<>() {
            @Override
            public Iterator<T> iterator() {
                return values.iterator();
            }

            @Override
            public boolean isEmpty() {
                return values.isEmpty();
            }

            @Override
            public Stream<T> stream() {
                return values.stream();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> compound(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    /** A codec's JSON as a tag: numbers keep their Java type, booleans are bytes, null is left out. */
    public static Object tag(JsonElement json) {
        if (json instanceof JsonObject object) {
            Map<String, Object> map = new TreeMap<>();
            object.entrySet().forEach(e -> {
                Object tag = tag(e.getValue());
                if (tag != null) {
                    map.put(e.getKey(), tag);
                }
            });
            return map;
        }
        if (json instanceof JsonArray array) {
            List<Object> list = new ArrayList<>();
            array.forEach(e -> {
                Object tag = tag(e);
                if (tag != null) {
                    list.add(tag);
                }
            });
            return list;
        }
        if (json instanceof JsonPrimitive p) {
            if (p.isBoolean()) {
                return (byte) (p.getAsBoolean() ? 1 : 0);
            }
            if (p.isString()) {
                return p.getAsString();
            }
            Number n = p.getAsNumber();
            return switch (n) {
                case Byte b -> b;
                case Short s -> s;
                case Integer i -> i;
                case Long l -> l;
                case Float f -> f;
                case Double d -> d;
                default -> {
                    double d = n.doubleValue();
                    yield d == Math.rint(d) && Math.abs(d) <= Integer.MAX_VALUE ? (Object) (int) d : (Object) d;
                }
            };
        }
        return null;
    }

    /** A tag as JSON, for a codec to read. */
    public static JsonElement json(Object tag) {
        return switch (tag) {
            case Map<?, ?> map -> {
                JsonObject object = new JsonObject();
                map.forEach((k, v) -> object.add((String) k, json(v)));
                yield object;
            }
            case List<?> list -> {
                JsonArray array = new JsonArray();
                list.forEach(v -> array.add(json(v)));
                yield array;
            }
            case byte[] a -> {
                JsonArray array = new JsonArray();
                for (byte b : a) {
                    array.add(b);
                }
                yield array;
            }
            case int[] a -> {
                JsonArray array = new JsonArray();
                for (int i : a) {
                    array.add(i);
                }
                yield array;
            }
            case long[] a -> {
                JsonArray array = new JsonArray();
                for (long l : a) {
                    array.add(l);
                }
                yield array;
            }
            case Number n -> new JsonPrimitive(n);
            case String s -> new JsonPrimitive(s);
            default -> JsonNull.INSTANCE;
        };
    }
}
