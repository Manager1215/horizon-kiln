package net.minecraft.network.protocol.common.custom;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code CustomPacketPayload}: a mod's own packet, named by its type's id. A mod registers its
 * type with a codec ({@code PayloadTypeRegistry}) and sends it with Fabric's networking; Kiln carries it over
 * Horizon's channel for mods ({@link kiln.Net}).
 */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    static <B extends ByteBuf, T extends CustomPacketPayload> StreamCodec<B, T> codec(StreamMemberEncoder<B, T> encoder,
            StreamDecoder<B, T> decoder) {
        return StreamCodec.ofMember(encoder, decoder);
    }

    static <T extends CustomPacketPayload> Type<T> createType(String id) {
        return new Type<>(Identifier.withDefaultNamespace(id));
    }

    record Type<T extends CustomPacketPayload>(Identifier id) {}

    record TypeAndCodec<B extends FriendlyByteBuf, T extends CustomPacketPayload>(Type<T> type,
            StreamCodec<B, T> codec) {}
}
