package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code FriendlyByteBuf}: the game's network buffer, a {@link ByteBuf} that writes the game's
 * values (var-ints, strings, block positions, ids) over another buffer, as the game's does. A mod's payload goes
 * through one between Kiln's Java sides over Horizon's channel for mods ({@link kiln.Net}). Values are laid out as the
 * game lays them out, except a compound, written as its SNBT text: both ends are Kiln's.
 */
public class FriendlyByteBuf extends ByteBuf {
    /** The longest string {@link #readUtf()} takes, as the game's. */
    private static final int MAX_STRING_LENGTH = 32767;
    /** The longest compound text {@link #readNbt()} takes: what fits in Horizon's message for mods. */
    private static final int NBT_LENGTH = 1 << 15;
    private final ByteBuf source;

    public FriendlyByteBuf(ByteBuf source) {
        this.source = source;
    }

    public static int getVarIntSize(int value) {
        for (int i = 1; i < 5; i++) {
            if ((value & -1 << i * 7) == 0) {
                return i;
            }
        }
        return 5;
    }

    public int readVarInt() {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            byte b = readByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new RuntimeException("VarInt too big");
    }

    public FriendlyByteBuf writeVarInt(int value) {
        while ((value & ~0x7F) != 0) {
            writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
        writeByte(value);
        return this;
    }

    public long readVarLong() {
        long value = 0;
        for (int shift = 0; shift < 70; shift += 7) {
            byte b = readByte();
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new RuntimeException("VarLong too big");
    }

    public FriendlyByteBuf writeVarLong(long value) {
        while ((value & ~0x7FL) != 0) {
            writeByte((int) (value & 0x7F) | 0x80);
            value >>>= 7;
        }
        writeByte((int) value);
        return this;
    }

    public String readUtf() {
        return readUtf(MAX_STRING_LENGTH);
    }

    public String readUtf(int maxLength) {
        int bytes = readVarInt();
        if (bytes < 0 || bytes > maxLength * 3) {
            throw new IllegalStateException("a string of " + bytes + " bytes, longer than " + maxLength * 3);
        }
        String text = toString(readerIndex(), bytes, StandardCharsets.UTF_8);
        readerIndex(readerIndex() + bytes);
        if (text.length() > maxLength) {
            throw new IllegalStateException("a string of " + text.length() + " characters, longer than " + maxLength);
        }
        return text;
    }

    public FriendlyByteBuf writeUtf(String text) {
        return writeUtf(text, MAX_STRING_LENGTH);
    }

    public FriendlyByteBuf writeUtf(String text, int maxLength) {
        if (text.length() > maxLength) {
            throw new IllegalStateException("a string of " + text.length() + " characters, longer than " + maxLength);
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        writeBytes(bytes);
        return this;
    }

    public UUID readUUID() {
        return new UUID(readLong(), readLong());
    }

    public FriendlyByteBuf writeUUID(UUID uuid) {
        writeLong(uuid.getMostSignificantBits());
        writeLong(uuid.getLeastSignificantBits());
        return this;
    }

    public BlockPos readBlockPos() {
        return BlockPos.of(readLong());
    }

    public FriendlyByteBuf writeBlockPos(BlockPos pos) {
        writeLong(pos.asLong());
        return this;
    }

    public Identifier readIdentifier() {
        return Identifier.parse(readUtf(MAX_STRING_LENGTH));
    }

    public FriendlyByteBuf writeIdentifier(Identifier id) {
        return writeUtf(id.toString());
    }

    public <T extends Enum<T>> T readEnum(Class<T> type) {
        return type.getEnumConstants()[readVarInt()];
    }

    public FriendlyByteBuf writeEnum(Enum<?> value) {
        return writeVarInt(value.ordinal());
    }

    public byte[] readByteArray() {
        return readByteArray(readableBytes());
    }

    public byte[] readByteArray(int maxLength) {
        int length = readVarInt();
        if (length < 0 || length > maxLength) {
            throw new IllegalStateException(length + " bytes, more than " + maxLength);
        }
        byte[] bytes = new byte[length];
        readBytes(bytes);
        return bytes;
    }

    public FriendlyByteBuf writeByteArray(byte[] bytes) {
        writeVarInt(bytes.length);
        writeBytes(bytes);
        return this;
    }

    public int[] readVarIntArray() {
        int[] values = new int[readVarInt()];
        for (int i = 0; i < values.length; i++) {
            values[i] = readVarInt();
        }
        return values;
    }

    public FriendlyByteBuf writeVarIntArray(int[] values) {
        writeVarInt(values.length);
        for (int value : values) {
            writeVarInt(value);
        }
        return this;
    }

    public <T, C extends Collection<T>> C readCollection(IntFunction<C> make, Function<? super FriendlyByteBuf, T> reader) {
        int n = readVarInt();
        C values = make.apply(n);
        for (int i = 0; i < n; i++) {
            values.add(reader.apply(this));
        }
        return values;
    }

    public <T> void writeCollection(Collection<T> values, BiConsumer<? super FriendlyByteBuf, T> writer) {
        writeVarInt(values.size());
        for (T value : values) {
            writer.accept(this, value);
        }
    }

    public <T> List<T> readList(Function<? super FriendlyByteBuf, T> reader) {
        return readCollection(ArrayList::new, reader);
    }

    public <K, V> Map<K, V> readMap(Function<? super FriendlyByteBuf, K> keys, Function<? super FriendlyByteBuf, V> values) {
        int n = readVarInt();
        Map<K, V> map = new HashMap<>();
        for (int i = 0; i < n; i++) {
            map.put(keys.apply(this), values.apply(this));
        }
        return map;
    }

    public <K, V> void writeMap(Map<K, V> map, BiConsumer<? super FriendlyByteBuf, K> keys,
            BiConsumer<? super FriendlyByteBuf, V> values) {
        writeVarInt(map.size());
        map.forEach((k, v) -> {
            keys.accept(this, k);
            values.accept(this, v);
        });
    }

    public <T> Optional<T> readOptional(Function<? super FriendlyByteBuf, T> reader) {
        return readBoolean() ? Optional.of(reader.apply(this)) : Optional.empty();
    }

    public <T> void writeOptional(Optional<T> value, BiConsumer<? super FriendlyByteBuf, T> writer) {
        writeBoolean(value.isPresent());
        value.ifPresent(v -> writer.accept(this, v));
    }

    public <T> T readNullable(Function<? super FriendlyByteBuf, T> reader) {
        return readBoolean() ? reader.apply(this) : null;
    }

    public <T> void writeNullable(T value, BiConsumer<? super FriendlyByteBuf, T> writer) {
        writeBoolean(value != null);
        if (value != null) {
            writer.accept(this, value);
        }
    }

    /** A compound as its SNBT text, or nothing: both ends are Kiln's, which reads it back. */
    public CompoundTag readNbt() {
        String text = readNullable(b -> b.readUtf(NBT_LENGTH));
        return text == null ? null : TagParser.parseCompoundFully(text);
    }

    public FriendlyByteBuf writeNbt(Tag tag) {
        writeNullable(tag == null ? null : tag.toString(), (b, text) -> b.writeUtf(text, NBT_LENGTH));
        return this;
    }

    // ByteBuf's own methods, on the buffer underneath; those that return the buffer they were called on return this.
    @Override public ByteBufAllocator alloc() { return source.alloc(); }
    @Override public byte[] array() { return source.array(); }
    @Override public int arrayOffset() { return source.arrayOffset(); }
    @Override public ByteBuf asReadOnly() { return source.asReadOnly(); }
    @Override public int bytesBefore(byte p0) { return source.bytesBefore(p0); }
    @Override public int bytesBefore(int p0, byte p1) { return source.bytesBefore(p0, p1); }
    @Override public int bytesBefore(int p0, int p1, byte p2) { return source.bytesBefore(p0, p1, p2); }
    @Override public int capacity() { return source.capacity(); }
    @Override public FriendlyByteBuf capacity(int p0) { source.capacity(p0); return this; }
    @Override public FriendlyByteBuf clear() { source.clear(); return this; }
    @Override public int compareTo(ByteBuf p0) { return source.compareTo(p0); }
    @Override public ByteBuf copy() { return source.copy(); }
    @Override public ByteBuf copy(int p0, int p1) { return source.copy(p0, p1); }
    @Override public FriendlyByteBuf discardReadBytes() { source.discardReadBytes(); return this; }
    @Override public FriendlyByteBuf discardSomeReadBytes() { source.discardSomeReadBytes(); return this; }
    @Override public ByteBuf duplicate() { return source.duplicate(); }
    @Override public int ensureWritable(int p0, boolean p1) { return source.ensureWritable(p0, p1); }
    @Override public FriendlyByteBuf ensureWritable(int p0) { source.ensureWritable(p0); return this; }
    @Override public boolean equals(Object p0) { return this == p0 || source.equals(p0); }
    @Override public int forEachByte(int p0, int p1, io.netty.util.ByteProcessor p2) { return source.forEachByte(p0, p1, p2); }
    @Override public int forEachByte(io.netty.util.ByteProcessor p0) { return source.forEachByte(p0); }
    @Override public int forEachByteDesc(int p0, int p1, io.netty.util.ByteProcessor p2) { return source.forEachByteDesc(p0, p1, p2); }
    @Override public int forEachByteDesc(io.netty.util.ByteProcessor p0) { return source.forEachByteDesc(p0); }
    @Override public boolean getBoolean(int p0) { return source.getBoolean(p0); }
    @Override public byte getByte(int p0) { return source.getByte(p0); }
    @Override public int getBytes(int p0, java.nio.channels.FileChannel p1, long p2, int p3) throws java.io.IOException { return source.getBytes(p0, p1, p2, p3); }
    @Override public int getBytes(int p0, java.nio.channels.GatheringByteChannel p1, int p2) throws java.io.IOException { return source.getBytes(p0, p1, p2); }
    @Override public FriendlyByteBuf getBytes(int p0, byte[] p1) { source.getBytes(p0, p1); return this; }
    @Override public FriendlyByteBuf getBytes(int p0, byte[] p1, int p2, int p3) { source.getBytes(p0, p1, p2, p3); return this; }
    @Override public FriendlyByteBuf getBytes(int p0, ByteBuf p1) { source.getBytes(p0, p1); return this; }
    @Override public FriendlyByteBuf getBytes(int p0, ByteBuf p1, int p2) { source.getBytes(p0, p1, p2); return this; }
    @Override public FriendlyByteBuf getBytes(int p0, ByteBuf p1, int p2, int p3) { source.getBytes(p0, p1, p2, p3); return this; }
    @Override public FriendlyByteBuf getBytes(int p0, java.io.OutputStream p1, int p2) throws java.io.IOException { source.getBytes(p0, p1, p2); return this; }
    @Override public FriendlyByteBuf getBytes(int p0, java.nio.ByteBuffer p1) { source.getBytes(p0, p1); return this; }
    @Override public char getChar(int p0) { return source.getChar(p0); }
    @Override public CharSequence getCharSequence(int p0, int p1, java.nio.charset.Charset p2) { return source.getCharSequence(p0, p1, p2); }
    @Override public double getDouble(int p0) { return source.getDouble(p0); }
    @Override public float getFloat(int p0) { return source.getFloat(p0); }
    @Override public int getInt(int p0) { return source.getInt(p0); }
    @Override public int getIntLE(int p0) { return source.getIntLE(p0); }
    @Override public long getLong(int p0) { return source.getLong(p0); }
    @Override public long getLongLE(int p0) { return source.getLongLE(p0); }
    @Override public int getMedium(int p0) { return source.getMedium(p0); }
    @Override public int getMediumLE(int p0) { return source.getMediumLE(p0); }
    @Override public short getShort(int p0) { return source.getShort(p0); }
    @Override public short getShortLE(int p0) { return source.getShortLE(p0); }
    @Override public short getUnsignedByte(int p0) { return source.getUnsignedByte(p0); }
    @Override public long getUnsignedInt(int p0) { return source.getUnsignedInt(p0); }
    @Override public long getUnsignedIntLE(int p0) { return source.getUnsignedIntLE(p0); }
    @Override public int getUnsignedMedium(int p0) { return source.getUnsignedMedium(p0); }
    @Override public int getUnsignedMediumLE(int p0) { return source.getUnsignedMediumLE(p0); }
    @Override public int getUnsignedShort(int p0) { return source.getUnsignedShort(p0); }
    @Override public int getUnsignedShortLE(int p0) { return source.getUnsignedShortLE(p0); }
    @Override public boolean hasArray() { return source.hasArray(); }
    @Override public boolean hasMemoryAddress() { return source.hasMemoryAddress(); }
    @Override public int hashCode() { return source.hashCode(); }
    @Override public int indexOf(int p0, int p1, byte p2) { return source.indexOf(p0, p1, p2); }
    @Override public java.nio.ByteBuffer internalNioBuffer(int p0, int p1) { return source.internalNioBuffer(p0, p1); }
    @Override public boolean isDirect() { return source.isDirect(); }
    @Override public boolean isReadOnly() { return source.isReadOnly(); }
    @Override public boolean isReadable() { return source.isReadable(); }
    @Override public boolean isReadable(int p0) { return source.isReadable(p0); }
    @Override public boolean isWritable() { return source.isWritable(); }
    @Override public boolean isWritable(int p0) { return source.isWritable(p0); }
    @Override public FriendlyByteBuf markReaderIndex() { source.markReaderIndex(); return this; }
    @Override public FriendlyByteBuf markWriterIndex() { source.markWriterIndex(); return this; }
    @Override public int maxCapacity() { return source.maxCapacity(); }
    @Override public int maxWritableBytes() { return source.maxWritableBytes(); }
    @Override public long memoryAddress() { return source.memoryAddress(); }
    @Override public java.nio.ByteBuffer nioBuffer() { return source.nioBuffer(); }
    @Override public java.nio.ByteBuffer nioBuffer(int p0, int p1) { return source.nioBuffer(p0, p1); }
    @Override public int nioBufferCount() { return source.nioBufferCount(); }
    @Override public java.nio.ByteBuffer[] nioBuffers() { return source.nioBuffers(); }
    @Override public java.nio.ByteBuffer[] nioBuffers(int p0, int p1) { return source.nioBuffers(p0, p1); }
    @Override public ByteBuf order(java.nio.ByteOrder p0) { return source.order(p0); }
    @Override public java.nio.ByteOrder order() { return source.order(); }
    @Override public boolean readBoolean() { return source.readBoolean(); }
    @Override public byte readByte() { return source.readByte(); }
    @Override public int readBytes(java.nio.channels.FileChannel p0, long p1, int p2) throws java.io.IOException { return source.readBytes(p0, p1, p2); }
    @Override public int readBytes(java.nio.channels.GatheringByteChannel p0, int p1) throws java.io.IOException { return source.readBytes(p0, p1); }
    @Override public FriendlyByteBuf readBytes(byte[] p0) { source.readBytes(p0); return this; }
    @Override public FriendlyByteBuf readBytes(byte[] p0, int p1, int p2) { source.readBytes(p0, p1, p2); return this; }
    @Override public ByteBuf readBytes(int p0) { return source.readBytes(p0); }
    @Override public FriendlyByteBuf readBytes(ByteBuf p0) { source.readBytes(p0); return this; }
    @Override public FriendlyByteBuf readBytes(ByteBuf p0, int p1) { source.readBytes(p0, p1); return this; }
    @Override public FriendlyByteBuf readBytes(ByteBuf p0, int p1, int p2) { source.readBytes(p0, p1, p2); return this; }
    @Override public FriendlyByteBuf readBytes(java.io.OutputStream p0, int p1) throws java.io.IOException { source.readBytes(p0, p1); return this; }
    @Override public FriendlyByteBuf readBytes(java.nio.ByteBuffer p0) { source.readBytes(p0); return this; }
    @Override public char readChar() { return source.readChar(); }
    @Override public CharSequence readCharSequence(int p0, java.nio.charset.Charset p1) { return source.readCharSequence(p0, p1); }
    @Override public double readDouble() { return source.readDouble(); }
    @Override public float readFloat() { return source.readFloat(); }
    @Override public int readInt() { return source.readInt(); }
    @Override public int readIntLE() { return source.readIntLE(); }
    @Override public long readLong() { return source.readLong(); }
    @Override public long readLongLE() { return source.readLongLE(); }
    @Override public int readMedium() { return source.readMedium(); }
    @Override public int readMediumLE() { return source.readMediumLE(); }
    @Override public ByteBuf readRetainedSlice(int p0) { return source.readRetainedSlice(p0); }
    @Override public short readShort() { return source.readShort(); }
    @Override public short readShortLE() { return source.readShortLE(); }
    @Override public ByteBuf readSlice(int p0) { return source.readSlice(p0); }
    @Override public short readUnsignedByte() { return source.readUnsignedByte(); }
    @Override public long readUnsignedInt() { return source.readUnsignedInt(); }
    @Override public long readUnsignedIntLE() { return source.readUnsignedIntLE(); }
    @Override public int readUnsignedMedium() { return source.readUnsignedMedium(); }
    @Override public int readUnsignedMediumLE() { return source.readUnsignedMediumLE(); }
    @Override public int readUnsignedShort() { return source.readUnsignedShort(); }
    @Override public int readUnsignedShortLE() { return source.readUnsignedShortLE(); }
    @Override public int readableBytes() { return source.readableBytes(); }
    @Override public int readerIndex() { return source.readerIndex(); }
    @Override public FriendlyByteBuf readerIndex(int p0) { source.readerIndex(p0); return this; }
    @Override public FriendlyByteBuf resetReaderIndex() { source.resetReaderIndex(); return this; }
    @Override public FriendlyByteBuf resetWriterIndex() { source.resetWriterIndex(); return this; }
    @Override public int refCnt() { return source.refCnt(); }
    @Override public boolean release() { return source.release(); }
    @Override public boolean release(int p0) { return source.release(p0); }
    @Override public FriendlyByteBuf retain() { source.retain(); return this; }
    @Override public FriendlyByteBuf retain(int p0) { source.retain(p0); return this; }
    @Override public ByteBuf retainedDuplicate() { return source.retainedDuplicate(); }
    @Override public ByteBuf retainedSlice() { return source.retainedSlice(); }
    @Override public ByteBuf retainedSlice(int p0, int p1) { return source.retainedSlice(p0, p1); }
    @Override public FriendlyByteBuf setBoolean(int p0, boolean p1) { source.setBoolean(p0, p1); return this; }
    @Override public FriendlyByteBuf setByte(int p0, int p1) { source.setByte(p0, p1); return this; }
    @Override public int setBytes(int p0, java.io.InputStream p1, int p2) throws java.io.IOException { return source.setBytes(p0, p1, p2); }
    @Override public int setBytes(int p0, java.nio.channels.FileChannel p1, long p2, int p3) throws java.io.IOException { return source.setBytes(p0, p1, p2, p3); }
    @Override public int setBytes(int p0, java.nio.channels.ScatteringByteChannel p1, int p2) throws java.io.IOException { return source.setBytes(p0, p1, p2); }
    @Override public FriendlyByteBuf setBytes(int p0, byte[] p1) { source.setBytes(p0, p1); return this; }
    @Override public FriendlyByteBuf setBytes(int p0, byte[] p1, int p2, int p3) { source.setBytes(p0, p1, p2, p3); return this; }
    @Override public FriendlyByteBuf setBytes(int p0, ByteBuf p1) { source.setBytes(p0, p1); return this; }
    @Override public FriendlyByteBuf setBytes(int p0, ByteBuf p1, int p2) { source.setBytes(p0, p1, p2); return this; }
    @Override public FriendlyByteBuf setBytes(int p0, ByteBuf p1, int p2, int p3) { source.setBytes(p0, p1, p2, p3); return this; }
    @Override public FriendlyByteBuf setBytes(int p0, java.nio.ByteBuffer p1) { source.setBytes(p0, p1); return this; }
    @Override public FriendlyByteBuf setChar(int p0, int p1) { source.setChar(p0, p1); return this; }
    @Override public int setCharSequence(int p0, CharSequence p1, java.nio.charset.Charset p2) { return source.setCharSequence(p0, p1, p2); }
    @Override public FriendlyByteBuf setDouble(int p0, double p1) { source.setDouble(p0, p1); return this; }
    @Override public FriendlyByteBuf setFloat(int p0, float p1) { source.setFloat(p0, p1); return this; }
    @Override public FriendlyByteBuf setIndex(int p0, int p1) { source.setIndex(p0, p1); return this; }
    @Override public FriendlyByteBuf setInt(int p0, int p1) { source.setInt(p0, p1); return this; }
    @Override public FriendlyByteBuf setIntLE(int p0, int p1) { source.setIntLE(p0, p1); return this; }
    @Override public FriendlyByteBuf setLong(int p0, long p1) { source.setLong(p0, p1); return this; }
    @Override public FriendlyByteBuf setLongLE(int p0, long p1) { source.setLongLE(p0, p1); return this; }
    @Override public FriendlyByteBuf setMedium(int p0, int p1) { source.setMedium(p0, p1); return this; }
    @Override public FriendlyByteBuf setMediumLE(int p0, int p1) { source.setMediumLE(p0, p1); return this; }
    @Override public FriendlyByteBuf setShort(int p0, int p1) { source.setShort(p0, p1); return this; }
    @Override public FriendlyByteBuf setShortLE(int p0, int p1) { source.setShortLE(p0, p1); return this; }
    @Override public FriendlyByteBuf setZero(int p0, int p1) { source.setZero(p0, p1); return this; }
    @Override public FriendlyByteBuf skipBytes(int p0) { source.skipBytes(p0); return this; }
    @Override public ByteBuf slice() { return source.slice(); }
    @Override public ByteBuf slice(int p0, int p1) { return source.slice(p0, p1); }
    @Override public String toString() { return source.toString(); }
    @Override public String toString(int p0, int p1, java.nio.charset.Charset p2) { return source.toString(p0, p1, p2); }
    @Override public String toString(java.nio.charset.Charset p0) { return source.toString(p0); }
    @Override public FriendlyByteBuf touch() { source.touch(); return this; }
    @Override public FriendlyByteBuf touch(Object p0) { source.touch(p0); return this; }
    @Override public ByteBuf unwrap() { return source.unwrap(); }
    @Override public int writableBytes() { return source.writableBytes(); }
    @Override public FriendlyByteBuf writeBoolean(boolean p0) { source.writeBoolean(p0); return this; }
    @Override public FriendlyByteBuf writeByte(int p0) { source.writeByte(p0); return this; }
    @Override public int writeBytes(java.io.InputStream p0, int p1) throws java.io.IOException { return source.writeBytes(p0, p1); }
    @Override public int writeBytes(java.nio.channels.FileChannel p0, long p1, int p2) throws java.io.IOException { return source.writeBytes(p0, p1, p2); }
    @Override public int writeBytes(java.nio.channels.ScatteringByteChannel p0, int p1) throws java.io.IOException { return source.writeBytes(p0, p1); }
    @Override public FriendlyByteBuf writeBytes(byte[] p0) { source.writeBytes(p0); return this; }
    @Override public FriendlyByteBuf writeBytes(byte[] p0, int p1, int p2) { source.writeBytes(p0, p1, p2); return this; }
    @Override public FriendlyByteBuf writeBytes(ByteBuf p0) { source.writeBytes(p0); return this; }
    @Override public FriendlyByteBuf writeBytes(ByteBuf p0, int p1) { source.writeBytes(p0, p1); return this; }
    @Override public FriendlyByteBuf writeBytes(ByteBuf p0, int p1, int p2) { source.writeBytes(p0, p1, p2); return this; }
    @Override public FriendlyByteBuf writeBytes(java.nio.ByteBuffer p0) { source.writeBytes(p0); return this; }
    @Override public FriendlyByteBuf writeChar(int p0) { source.writeChar(p0); return this; }
    @Override public int writeCharSequence(CharSequence p0, java.nio.charset.Charset p1) { return source.writeCharSequence(p0, p1); }
    @Override public FriendlyByteBuf writeDouble(double p0) { source.writeDouble(p0); return this; }
    @Override public FriendlyByteBuf writeFloat(float p0) { source.writeFloat(p0); return this; }
    @Override public FriendlyByteBuf writeInt(int p0) { source.writeInt(p0); return this; }
    @Override public FriendlyByteBuf writeIntLE(int p0) { source.writeIntLE(p0); return this; }
    @Override public FriendlyByteBuf writeLong(long p0) { source.writeLong(p0); return this; }
    @Override public FriendlyByteBuf writeLongLE(long p0) { source.writeLongLE(p0); return this; }
    @Override public FriendlyByteBuf writeMedium(int p0) { source.writeMedium(p0); return this; }
    @Override public FriendlyByteBuf writeMediumLE(int p0) { source.writeMediumLE(p0); return this; }
    @Override public FriendlyByteBuf writeShort(int p0) { source.writeShort(p0); return this; }
    @Override public FriendlyByteBuf writeShortLE(int p0) { source.writeShortLE(p0); return this; }
    @Override public FriendlyByteBuf writeZero(int p0) { source.writeZero(p0); return this; }
    @Override public int writerIndex() { return source.writerIndex(); }
    @Override public FriendlyByteBuf writerIndex(int p0) { source.writerIndex(p0); return this; }
}
