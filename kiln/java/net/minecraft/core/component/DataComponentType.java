package net.minecraft.core.component;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code DataComponentType}: a kind of data an item stack carries. A persistent one's value is
 * saved with the stack under its registered id, through its codec.
 */
public interface DataComponentType<T> {
    Codec<DataComponentType<?>> CODEC = Codec.STRING.xmap(
            id -> BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(id)),
            type -> String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type)));
    Codec<DataComponentType<?>> PERSISTENT_CODEC = CODEC;

    static <T> Builder<T> builder() {
        return new Builder<>();
    }

    Codec<T> codec();

    default Codec<T> codecOrThrow() {
        Codec<T> codec = codec();
        if (codec == null) {
            throw new IllegalStateException(this + " is not a persistent component");
        }
        return codec;
    }

    default boolean isTransient() {
        return codec() == null;
    }

    boolean ignoreSwapAnimation();

    StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec();

    class Builder<T> {
        private Codec<T> codec;
        private StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec;
        private boolean ignoreSwapAnimation;

        public Builder<T> persistent(Codec<T> codec) {
            this.codec = codec;
            return this;
        }

        public Builder<T> networkSynchronized(StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
            this.streamCodec = streamCodec;
            return this;
        }

        public Builder<T> cacheEncoding() {
            return this;
        }

        public Builder<T> ignoreSwapAnimation() {
            ignoreSwapAnimation = true;
            return this;
        }

        public DataComponentType<T> build() {
            Codec<T> c = codec;
            StreamCodec<? super RegistryFriendlyByteBuf, T> s = streamCodec;
            boolean ignore = ignoreSwapAnimation;
            return new DataComponentType<>() {
                @Override
                public Codec<T> codec() {
                    return c;
                }

                @Override
                public boolean ignoreSwapAnimation() {
                    return ignore;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
                    return s;
                }

                @Override
                public String toString() {
                    return String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(this));
                }
            };
        }
    }
}
