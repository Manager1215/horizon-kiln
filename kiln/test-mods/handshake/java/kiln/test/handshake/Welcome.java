package kiln.test.handshake;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The server's play payload at JOIN: a word. */
public record Welcome(String word) implements CustomPacketPayload {
    public static final Type<Welcome> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TestHandshake.ID, "welcome"));
    public static final StreamCodec<ByteBuf, Welcome> CODEC =
            ByteBufCodecs.STRING_UTF8.map(Welcome::new, Welcome::word);

    @Override
    public Type<Welcome> type() {
        return TYPE;
    }
}
