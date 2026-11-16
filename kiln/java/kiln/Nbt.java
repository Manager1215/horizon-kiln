package kiln;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * NBT as the game sends it over the network (a tag's type, then its payload, no root name), for Kiln's own model of
 * it ({@link Snbt}: {@code Byte} ... {@code Map}): a block entity's saved compound goes to the game and comes from it
 * in this form, base64 in a line's field, not as SNBT (D-140). A list whose elements are of different types is written
 * as vanilla writes one, each element that is not a compound wrapped in one under the empty name, and read back
 * unwrapped.
 */
public final class Nbt {
    private static final int MAX_DEPTH = 512;

    private Nbt() {}

    /** {@code compound} in the network's binary form. */
    public static byte[] write(Map<String, Object> compound) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            out.writeByte(10);
            payload(compound, out, 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** The compound {@code bytes} hold; a tag that is not one, or broken bytes, read as an empty compound. */
    public static Map<String, Object> compound(byte[] bytes) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            int type = in.readByte();
            if (type == 10 && read(type, in, 0) instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> compound = (Map<String, Object>) map;
                return compound;
            }
        } catch (IOException | RuntimeException e) {
            Host.send("log", "Kiln can't read the saved data it was given (" + bytes.length + " bytes): " + e);
        }
        return new TreeMap<>();
    }

    /** {@code compound} as a line's field: its binary form in base64. */
    public static String field(Map<String, Object> compound) {
        return Base64.getEncoder().encodeToString(write(compound));
    }

    /** A line's field of base64: the compound its binary form holds. */
    public static Map<String, Object> fromField(String field) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(field);
        } catch (IllegalArgumentException e) {
            Host.send("log", "Kiln can't read the saved data it was given: " + e.getMessage());
            return new TreeMap<>();
        }
        return compound(bytes);
    }

    /** The type of a tag of Kiln's model: NBT's id. */
    static int type(Object tag) {
        return switch (tag) {
            case Byte b -> 1;
            case Short s -> 2;
            case Integer i -> 3;
            case Long l -> 4;
            case Float f -> 5;
            case Double d -> 6;
            case byte[] a -> 7;
            case String s -> 8;
            case List<?> l -> 9;
            case Map<?, ?> m -> 10;
            case int[] a -> 11;
            case long[] a -> 12;
            default -> throw new IllegalArgumentException("not a tag: " + tag.getClass().getName());
        };
    }

    private static void payload(Object tag, DataOutputStream out, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("NBT nested deeper than " + MAX_DEPTH);
        }
        switch (tag) {
            case Byte b -> out.writeByte(b);
            case Short s -> out.writeShort(s);
            case Integer i -> out.writeInt(i);
            case Long l -> out.writeLong(l);
            case Float f -> out.writeFloat(f);
            case Double d -> out.writeDouble(d);
            case byte[] a -> {
                out.writeInt(a.length);
                out.write(a);
            }
            case String s -> out.writeUTF(s);
            case List<?> list -> {
                int type = list.isEmpty() ? 0 : type(list.get(0));
                boolean mixed = false;
                for (Object e : list) {
                    mixed |= type(e) != type;
                }
                out.writeByte(mixed ? 10 : type);
                out.writeInt(list.size());
                for (Object e : list) {
                    if (mixed && !(e instanceof Map)) {
                        // ListTag's wrapping: {"": element}.
                        out.writeByte(type(e));
                        out.writeUTF("");
                        payload(e, out, depth + 2);
                        out.writeByte(0);
                    } else {
                        payload(e, out, depth + 1);
                    }
                }
            }
            case Map<?, ?> map -> {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    out.writeByte(type(e.getValue()));
                    out.writeUTF((String) e.getKey());
                    payload(e.getValue(), out, depth + 1);
                }
                out.writeByte(0);
            }
            case int[] a -> {
                out.writeInt(a.length);
                for (int v : a) {
                    out.writeInt(v);
                }
            }
            case long[] a -> {
                out.writeInt(a.length);
                for (long v : a) {
                    out.writeLong(v);
                }
            }
            default -> throw new IllegalArgumentException("not a tag: " + tag.getClass().getName());
        }
    }

    private static Object read(int type, DataInputStream in, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nested deeper than " + MAX_DEPTH);
        }
        return switch (type) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                byte[] a = new byte[length(in)];
                in.readFully(a);
                yield a;
            }
            case 8 -> in.readUTF();
            case 9 -> {
                int elements = in.readByte();
                int n = length(in);
                List<Object> list = new ArrayList<>(Math.min(n, 1024));
                for (int k = 0; k < n; k++) {
                    Object e = read(elements, in, depth + 1);
                    // ListTag's unwrapping of a mixed list's elements.
                    if (e instanceof Map<?, ?> m && m.size() == 1 && m.containsKey("")) {
                        e = m.get("");
                    }
                    list.add(e);
                }
                yield list;
            }
            case 10 -> {
                Map<String, Object> compound = new TreeMap<>();
                for (int t = in.readByte(); t != 0; t = in.readByte()) {
                    String key = in.readUTF();
                    compound.put(key, read(t, in, depth + 1));
                }
                yield compound;
            }
            case 11 -> {
                int[] a = new int[length(in)];
                for (int k = 0; k < a.length; k++) {
                    a[k] = in.readInt();
                }
                yield a;
            }
            case 12 -> {
                long[] a = new long[length(in)];
                for (int k = 0; k < a.length; k++) {
                    a[k] = in.readLong();
                }
                yield a;
            }
            default -> throw new IOException("unknown NBT type " + type);
        };
    }

    private static int length(DataInputStream in) throws IOException {
        int n = in.readInt();
        // Each element takes a byte at least: a length past the data's end is broken data, not an array to make.
        if (n < 0 || n > in.available()) {
            throw new IOException("an NBT length of " + n + " past the data's end");
        }
        return n;
    }
}
