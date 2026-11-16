package net.minecraft.nbt;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Kiln's stand-in for {@code NbtIo}: compounds as the game's binary NBT files hold them (a root compound with an empty
 * name, big-endian), so a file a mod writes here the game reads, and the other way round.
 */
public final class NbtIo {
    private NbtIo() {}

    /** The file as the game writes saves' plain files ({@code write}): not compressed. */
    public static void write(CompoundTag tag, Path path) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            writeRoot(tag, out);
        }
    }

    public static CompoundTag read(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            return readRoot(in);
        }
    }

    /** As the game's saves (level.dat, player files): gzip. */
    public static void writeCompressed(CompoundTag tag, Path path) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(path))))) {
            writeRoot(tag, out);
        }
    }

    public static CompoundTag readCompressed(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(path))))) {
            return readRoot(in);
        }
    }

    private static void writeRoot(CompoundTag tag, DataOutput out) throws IOException {
        out.writeByte(Tag.TAG_COMPOUND);
        out.writeUTF("");
        writePayload(tag, out);
    }

    private static CompoundTag readRoot(DataInput in) throws IOException {
        if (in.readByte() != Tag.TAG_COMPOUND) {
            throw new IOException("Root tag must be a named compound tag");
        }
        in.readUTF();
        return (CompoundTag) readPayload(Tag.TAG_COMPOUND, in);
    }

    private static void writePayload(Tag tag, DataOutput out) throws IOException {
        switch (tag) {
            case ByteTag t -> out.writeByte(t.value());
            case ShortTag t -> out.writeShort(t.value());
            case IntTag t -> out.writeInt(t.value());
            case LongTag t -> out.writeLong(t.value());
            case FloatTag t -> out.writeFloat(t.value());
            case DoubleTag t -> out.writeDouble(t.value());
            case StringTag t -> out.writeUTF(t.value());
            case ByteArrayTag t -> {
                byte[] a = t.getAsByteArray();
                out.writeInt(a.length);
                out.write(a);
            }
            case IntArrayTag t -> {
                int[] a = t.getAsIntArray();
                out.writeInt(a.length);
                for (int v : a) {
                    out.writeInt(v);
                }
            }
            case LongArrayTag t -> {
                long[] a = t.getAsLongArray();
                out.writeInt(a.length);
                for (long v : a) {
                    out.writeLong(v);
                }
            }
            case ListTag t -> {
                byte type = t.isEmpty() ? Tag.TAG_END : t.get(0).getId();
                out.writeByte(type);
                out.writeInt(t.size());
                for (Tag element : t) {
                    writePayload(element, out);
                }
            }
            case CompoundTag t -> {
                for (String key : t.keySet()) {
                    Tag value = t.get(key);
                    out.writeByte(value.getId());
                    out.writeUTF(key);
                    writePayload(value, out);
                }
                out.writeByte(Tag.TAG_END);
            }
            default -> throw new IOException("Kiln can't write " + tag.getClass().getSimpleName());
        }
    }

    private static Tag readPayload(byte type, DataInput in) throws IOException {
        switch (type) {
            case Tag.TAG_BYTE:
                return ByteTag.valueOf(in.readByte());
            case Tag.TAG_SHORT:
                return new ShortTag(in.readShort());
            case Tag.TAG_INT:
                return new IntTag(in.readInt());
            case Tag.TAG_LONG:
                return new LongTag(in.readLong());
            case Tag.TAG_FLOAT:
                return new FloatTag(in.readFloat());
            case Tag.TAG_DOUBLE:
                return new DoubleTag(in.readDouble());
            case Tag.TAG_STRING:
                return StringTag.valueOf(in.readUTF());
            case Tag.TAG_BYTE_ARRAY: {
                byte[] a = new byte[in.readInt()];
                in.readFully(a);
                return new ByteArrayTag(a);
            }
            case Tag.TAG_INT_ARRAY: {
                int[] a = new int[in.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readInt();
                }
                return new IntArrayTag(a);
            }
            case Tag.TAG_LONG_ARRAY: {
                long[] a = new long[in.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readLong();
                }
                return new LongArrayTag(a);
            }
            case Tag.TAG_LIST: {
                byte element = in.readByte();
                int size = in.readInt();
                List<Tag> list = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    list.add(readPayload(element, in));
                }
                return new ListTag(list);
            }
            case Tag.TAG_COMPOUND: {
                CompoundTag compound = new CompoundTag();
                for (byte key; (key = in.readByte()) != Tag.TAG_END; ) {
                    String name = in.readUTF();
                    compound.put(name, readPayload(key, in));
                }
                return compound;
            }
            default:
                throw new IOException("Unknown tag type " + type);
        }
    }
}
