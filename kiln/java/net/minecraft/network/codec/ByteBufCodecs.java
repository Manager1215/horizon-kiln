package net.minecraft.network.codec;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;

/** Kiln's stand-in for {@code ByteBufCodecs}: the network codecs of plain values, as the game writes them. */
public interface ByteBufCodecs {
    StreamCodec<ByteBuf, Boolean> BOOL = StreamCodec.of(ByteBuf::writeBoolean, ByteBuf::readBoolean);
    StreamCodec<ByteBuf, Byte> BYTE = StreamCodec.of((b, v) -> b.writeByte(v), ByteBuf::readByte);
    StreamCodec<ByteBuf, Short> SHORT = StreamCodec.of((b, v) -> b.writeShort(v), ByteBuf::readShort);
    StreamCodec<ByteBuf, Integer> INT = StreamCodec.of(ByteBuf::writeInt, ByteBuf::readInt);
    StreamCodec<ByteBuf, Long> LONG = StreamCodec.of(ByteBuf::writeLong, ByteBuf::readLong);
    StreamCodec<ByteBuf, Float> FLOAT = StreamCodec.of(ByteBuf::writeFloat, ByteBuf::readFloat);
    StreamCodec<ByteBuf, Double> DOUBLE = StreamCodec.of(ByteBuf::writeDouble, ByteBuf::readDouble);
    StreamCodec<ByteBuf, Integer> VAR_INT = StreamCodec.of((b, v) -> writeVarLong(b, v & 0xFFFFFFFFL),
            b -> (int) readVarLong(b));
    StreamCodec<ByteBuf, Long> VAR_LONG = StreamCodec.of(ByteBufCodecs::writeVarLong, ByteBufCodecs::readVarLong);
    StreamCodec<ByteBuf, String> STRING_UTF8 = StreamCodec.of((b, v) -> {
        byte[] bytes = v.getBytes(StandardCharsets.UTF_8);
        writeVarLong(b, bytes.length);
        b.writeBytes(bytes);
    }, b -> {
        byte[] bytes = new byte[(int) readVarLong(b)];
        b.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    });

    /** A compound as its SNBT text: Kiln's network codecs only need to round-trip, nothing goes to a real server. */
    StreamCodec<ByteBuf, net.minecraft.nbt.CompoundTag> COMPOUND_TAG = STRING_UTF8.map(
            net.minecraft.nbt.TagParser::parseCompoundFully, net.minecraft.nbt.CompoundTag::toString);

    static <B extends ByteBuf, V> StreamCodec<B, java.util.Optional<V>> optional(StreamCodec<? super B, V> codec) {
        return StreamCodec.of((buffer, value) -> {
            buffer.writeBoolean(value.isPresent());
            value.ifPresent(v -> codec.encode(buffer, v));
        }, buffer -> buffer.readBoolean() ? java.util.Optional.of(codec.decode(buffer)) : java.util.Optional.empty());
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, java.util.List<V>> list() {
        return list(Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, java.util.List<V>> list(int max) {
        return codec -> collection(java.util.ArrayList::new, codec, max);
    }

    static <B extends ByteBuf, V, C extends java.util.Collection<V>> StreamCodec<B, C> collection(
            java.util.function.IntFunction<C> make, StreamCodec<? super B, V> codec, int max) {
        return StreamCodec.of((buffer, values) -> {
            writeVarLong(buffer, values.size());
            values.forEach(v -> codec.encode(buffer, v));
        }, buffer -> {
            int n = (int) readVarLong(buffer);
            if (n > max) {
                throw new IllegalStateException(n + " elements, more than " + max);
            }
            C values = make.apply(n);
            for (int i = 0; i < n; i++) {
                values.add(codec.decode(buffer));
            }
            return values;
        });
    }

    static <B extends ByteBuf, K, V, M extends java.util.Map<K, V>> StreamCodec<B, M> map(
            java.util.function.IntFunction<? extends M> make, StreamCodec<? super B, K> keys,
            StreamCodec<? super B, V> values) {
        return StreamCodec.of((buffer, map) -> {
            writeVarLong(buffer, map.size());
            map.forEach((k, v) -> {
                keys.encode(buffer, k);
                values.encode(buffer, v);
            });
        }, buffer -> {
            int n = (int) readVarLong(buffer);
            M map = make.apply(n);
            for (int i = 0; i < n; i++) {
                map.put(keys.decode(buffer), values.decode(buffer));
            }
            return map;
        });
    }

    /** A registry's entry by its id: the registry Kiln has under that key ({@code BuiltInRegistries}). */
    static <T> StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, net.minecraft.core.Holder<T>> holderRegistry(
            net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> key) {
        return StreamCodec.of(
                (buffer, holder) -> STRING_UTF8.encode(buffer, holder.unwrapKey().orElseThrow().identifier().toString()),
                buffer -> kiln.Registries.holder(key, net.minecraft.resources.Identifier.parse(STRING_UTF8.decode(buffer))));
    }

    /** Seven bits a byte, the high bit saying more follow. */
    private static void writeVarLong(ByteBuf buffer, long value) {
        while ((value & ~0x7FL) != 0) {
            buffer.writeByte((int) (value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buffer.writeByte((int) value);
    }

    private static long readVarLong(ByteBuf buffer) {
        long value = 0;
        for (int shift = 0; shift < 70; shift += 7) {
            byte b = buffer.readByte();
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IllegalStateException("a VarLong too long");
    }
}
