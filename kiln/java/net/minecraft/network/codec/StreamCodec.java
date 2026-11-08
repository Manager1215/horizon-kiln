package net.minecraft.network.codec;

import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import java.util.function.Function;

/**
 * Kiln's stand-in for {@code StreamCodec}: how a value goes over the network. A mod's payloads go through theirs
 * ({@link kiln.Net}), between Kiln's Java sides.
 */
public interface StreamCodec<B, V> extends StreamDecoder<B, V>, StreamEncoder<B, V> {
    static <B, V> StreamCodec<B, V> of(StreamEncoder<B, V> encoder, StreamDecoder<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buffer) {
                return decoder.decode(buffer);
            }

            @Override
            public void encode(B buffer, V value) {
                encoder.encode(buffer, value);
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(StreamMemberEncoder<B, V> encoder, StreamDecoder<B, V> decoder) {
        return of((buffer, value) -> encoder.encode(value, buffer), decoder);
    }

    static <B, V> StreamCodec<B, V> unit(V value) {
        return of((buffer, v) -> {}, buffer -> value);
    }

    default <O> StreamCodec<B, O> map(Function<? super V, ? extends O> to, Function<? super O, ? extends V> from) {
        StreamCodec<B, V> self = this;
        return of((buffer, value) -> self.encode(buffer, from.apply(value)), buffer -> to.apply(self.decode(buffer)));
    }

    default <O> StreamCodec<B, O> apply(CodecOperation<B, V, O> operation) {
        return operation.apply(this);
    }

    @SuppressWarnings("unchecked")
    default <S extends B> StreamCodec<S, V> cast() {
        return (StreamCodec<S, V>) this;
    }

    /** A codec made from another (a list of its values, an optional one). */
    @FunctionalInterface
    interface CodecOperation<B, S, T> {
        StreamCodec<B, T> apply(StreamCodec<B, S> codec);
    }

    static <B, C, T1> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            Function<T1, C> make) {
        return of((buffer, value) -> c1.encode(buffer, g1.apply(value)), buffer -> make.apply(c1.decode(buffer)));
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2, java.util.function.BiFunction<T1, T2, C> make) {
        return of((buffer, value) -> {
            c1.encode(buffer, g1.apply(value));
            c2.encode(buffer, g2.apply(value));
        }, buffer -> make.apply(c1.decode(buffer), c2.decode(buffer)));
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2, StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            Function3<T1, T2, T3, C> make) {
        return of((buffer, value) -> {
            c1.encode(buffer, g1.apply(value));
            c2.encode(buffer, g2.apply(value));
            c3.encode(buffer, g3.apply(value));
        }, buffer -> make.apply(c1.decode(buffer), c2.decode(buffer), c3.decode(buffer)));
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2, StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4, Function4<T1, T2, T3, T4, C> make) {
        return of((buffer, value) -> {
            c1.encode(buffer, g1.apply(value));
            c2.encode(buffer, g2.apply(value));
            c3.encode(buffer, g3.apply(value));
            c4.encode(buffer, g4.apply(value));
        }, buffer -> make.apply(c1.decode(buffer), c2.decode(buffer), c3.decode(buffer), c4.decode(buffer)));
    }

    /** What it reads and writes on a buffer made from another ({@code RegistryFriendlyByteBuf} from a plain one). */
    default <O extends io.netty.buffer.ByteBuf> StreamCodec<O, V> mapStream(Function<O, ? extends B> buffer) {
        StreamCodec<B, V> self = this;
        return of((b, value) -> self.encode(buffer.apply(b), value), b -> self.decode(buffer.apply(b)));
    }

    /** A value written as its type (this codec) and then by that type's own codec. */
    @SuppressWarnings("unchecked")
    default <U> StreamCodec<B, U> dispatch(Function<? super U, ? extends V> type,
            Function<? super V, ? extends StreamCodec<? super B, ? extends U>> codec) {
        StreamCodec<B, V> self = this;
        return of((buffer, value) -> {
            V key = type.apply(value);
            self.encode(buffer, key);
            ((StreamCodec<? super B, U>) codec.apply(key)).encode(buffer, value);
        }, buffer -> codec.apply(self.decode(buffer)).decode(buffer));
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            com.mojang.datafixers.util.Function5<T1, T2, T3, T4, T5, C> make) {
        return of((buffer, value) -> {
            c1.encode(buffer, g1.apply(value));
            c2.encode(buffer, g2.apply(value));
            c3.encode(buffer, g3.apply(value));
            c4.encode(buffer, g4.apply(value));
            c5.encode(buffer, g5.apply(value));
        }, buffer -> make.apply(c1.decode(buffer), c2.decode(buffer), c3.decode(buffer), c4.decode(buffer),
                c5.decode(buffer)));
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            StreamCodec<? super B, T6> c6, Function<C, T6> g6,
            com.mojang.datafixers.util.Function6<T1, T2, T3, T4, T5, T6, C> make) {
        return of((buffer, value) -> {
            c1.encode(buffer, g1.apply(value));
            c2.encode(buffer, g2.apply(value));
            c3.encode(buffer, g3.apply(value));
            c4.encode(buffer, g4.apply(value));
            c5.encode(buffer, g5.apply(value));
            c6.encode(buffer, g6.apply(value));
        }, buffer -> make.apply(c1.decode(buffer), c2.decode(buffer), c3.decode(buffer), c4.decode(buffer),
                c5.decode(buffer), c6.decode(buffer)));
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            StreamCodec<? super B, T6> c6, Function<C, T6> g6,
            StreamCodec<? super B, T7> c7, Function<C, T7> g7,
            com.mojang.datafixers.util.Function7<T1, T2, T3, T4, T5, T6, T7, C> make) {
        return of((buffer, value) -> {
            c1.encode(buffer, g1.apply(value));
            c2.encode(buffer, g2.apply(value));
            c3.encode(buffer, g3.apply(value));
            c4.encode(buffer, g4.apply(value));
            c5.encode(buffer, g5.apply(value));
            c6.encode(buffer, g6.apply(value));
            c7.encode(buffer, g7.apply(value));
        }, buffer -> make.apply(c1.decode(buffer), c2.decode(buffer), c3.decode(buffer), c4.decode(buffer),
                c5.decode(buffer), c6.decode(buffer), c7.decode(buffer)));
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            StreamCodec<? super B, T6> c6, Function<C, T6> g6,
            StreamCodec<? super B, T7> c7, Function<C, T7> g7,
            StreamCodec<? super B, T8> c8, Function<C, T8> g8,
            com.mojang.datafixers.util.Function8<T1, T2, T3, T4, T5, T6, T7, T8, C> make) {
        return of((buffer, value) -> {
            c1.encode(buffer, g1.apply(value));
            c2.encode(buffer, g2.apply(value));
            c3.encode(buffer, g3.apply(value));
            c4.encode(buffer, g4.apply(value));
            c5.encode(buffer, g5.apply(value));
            c6.encode(buffer, g6.apply(value));
            c7.encode(buffer, g7.apply(value));
            c8.encode(buffer, g8.apply(value));
        }, buffer -> make.apply(c1.decode(buffer), c2.decode(buffer), c3.decode(buffer), c4.decode(buffer),
                c5.decode(buffer), c6.decode(buffer), c7.decode(buffer), c8.decode(buffer)));
    }
}
