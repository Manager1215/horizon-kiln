package kiln.proof.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A line of the server's for a player's chat: who joined, left or pinged, as the server's players saw it. */
public record News(String line) implements CustomPacketPayload {
    public static final Type<News> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ProofNet.ID, "news"));
    public static final StreamCodec<ByteBuf, News> CODEC = ByteBufCodecs.STRING_UTF8.map(News::new, News::line);

    @Override
    public Type<News> type() {
        return TYPE;
    }
}
