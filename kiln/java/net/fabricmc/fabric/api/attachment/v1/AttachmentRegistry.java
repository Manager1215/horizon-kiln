package net.fabricmc.fabric.api.attachment.v1;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for Fabric's {@code AttachmentRegistry}: makes the mods' {@link AttachmentType}s, one per id.
 */
public final class AttachmentRegistry {
    private static final Map<Identifier, AttachmentType<?>> TYPES = new ConcurrentHashMap<>();

    private AttachmentRegistry() {}

    /** One kept only while the world is open, with no initializer. */
    public static <A> AttachmentType<A> create(Identifier id) {
        return create(id, builder -> {});
    }

    public static <A> AttachmentType<A> create(Identifier id, Consumer<Builder<A>> consumer) {
        Builder<A> builder = new Made<>();
        consumer.accept(builder);
        return builder.buildAndRegister(id);
    }

    public static <A> AttachmentType<A> createDefaulted(Identifier id, Supplier<A> initializer) {
        return create(id, builder -> builder.initializer(initializer));
    }

    public static <A> AttachmentType<A> createPersistent(Identifier id, Codec<A> codec) {
        return create(id, builder -> builder.persistent(codec));
    }

    /** Fabric's older way, still in its API: {@code builder()...buildAndRegister(id)}. */
    public static <A> Builder<A> builder() {
        return new Made<>();
    }

    /** Kiln's own: the type made with {@code id}; null for none. */
    public static AttachmentType<?> kiln$get(Identifier id) {
        return TYPES.get(id);
    }

    /** How a mod describes its type before it is made. */
    public interface Builder<A> {
        Builder<A> persistent(Codec<A> codec);

        Builder<A> copyOnDeath();

        Builder<A> initializer(Supplier<A> initializer);

        Builder<A> syncWith(StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, A> packetCodec,
                AttachmentSyncPredicate syncPredicate);

        AttachmentType<A> buildAndRegister(Identifier id);
    }

    private static final class Made<A> implements Builder<A> {
        private Codec<A> codec;
        private boolean copyOnDeath;
        private Supplier<A> initializer;
        private boolean synced;

        @Override
        public Builder<A> persistent(Codec<A> codec) {
            this.codec = Objects.requireNonNull(codec, "codec");
            return this;
        }

        @Override
        public Builder<A> copyOnDeath() {
            this.copyOnDeath = true;
            return this;
        }

        @Override
        public Builder<A> initializer(Supplier<A> initializer) {
            this.initializer = Objects.requireNonNull(initializer, "initializer");
            return this;
        }

        @Override
        public Builder<A> syncWith(StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, A> packetCodec,
                AttachmentSyncPredicate syncPredicate) {
            this.synced = true;
            return this;
        }

        @Override
        public AttachmentType<A> buildAndRegister(Identifier id) {
            Type<A> type = new Type<>(Objects.requireNonNull(id, "id"), codec, initializer, copyOnDeath, synced);
            // A second one with the id takes its place: a mod switched off and on again (D-116) makes its types
            // again; values saved under the id are read by the newest.
            TYPES.put(id, type);
            return type;
        }
    }

    private record Type<A>(Identifier identifier, Codec<A> persistenceCodec, Supplier<A> initializer,
            boolean copyOnDeath, boolean isSynced) implements AttachmentType<A> {
        /** One type per id: a type made again (a mod switched on again) finds the values of the one before. */
        @Override
        public boolean equals(Object o) {
            return o instanceof Type<?> t && t.identifier.equals(identifier);
        }

        @Override
        public int hashCode() {
            return identifier.hashCode();
        }

        @Override
        public String toString() {
            return "AttachmentType[" + identifier + "]";
        }
    }
}
