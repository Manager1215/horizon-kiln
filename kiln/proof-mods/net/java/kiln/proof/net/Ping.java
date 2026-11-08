package kiln.proof.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A player's ping for the server: its number and a word from the player's side, by the game's own codecs. */
public record Ping(int number, String word) implements CustomPacketPayload {
    public static final Type<Ping> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ProofNet.ID, "ping"));
    public static final StreamCodec<ByteBuf, Ping> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Ping::number, ByteBufCodecs.STRING_UTF8, Ping::word, Ping::new);

    @Override
    public Type<Ping> type() {
        return TYPE;
    }
}
