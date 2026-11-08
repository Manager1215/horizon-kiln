package net.fabricmc.fabric.api.networking.v1;

import java.util.function.IntSupplier;
import java.util.function.Predicate;
import kiln.Net;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Kiln's stand-in for Fabric's {@code PayloadTypeRegistry}: the payload types a mod sends and receives, each with its
 * codec, by direction and phase. Play's go over Horizon's channel for mods ({@link Net}); the configuration phase's are
 * kept, and never sent (Horizon's configuration carries none). A payload's size limit is Horizon's, whatever a large
 * type asks for.
 */
public interface PayloadTypeRegistry<B extends FriendlyByteBuf> {
    <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(
            CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec);

    default <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(
            CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec, Predicate<PacketContext> when) {
        return register(type, codec);
    }

    default <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> registerLarge(
            CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec, int maxSize) {
        return register(type, codec);
    }

    default <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> registerLarge(
            CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec, int maxSize,
            Predicate<PacketContext> when) {
        return register(type, codec);
    }

    default <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> registerLarge(
            CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec, IntSupplier maxSize) {
        return register(type, codec);
    }

    default <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> registerLarge(
            CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec, IntSupplier maxSize,
            Predicate<PacketContext> when) {
        return register(type, codec);
    }

    static PayloadTypeRegistry<FriendlyByteBuf> serverboundConfiguration() {
        return Net.SERVERBOUND_CONFIGURATION;
    }

    static PayloadTypeRegistry<FriendlyByteBuf> clientboundConfiguration() {
        return Net.CLIENTBOUND_CONFIGURATION;
    }

    static PayloadTypeRegistry<RegistryFriendlyByteBuf> serverboundPlay() {
        return Net.SERVERBOUND_PLAY;
    }

    static PayloadTypeRegistry<RegistryFriendlyByteBuf> clientboundPlay() {
        return Net.CLIENTBOUND_PLAY;
    }
}
