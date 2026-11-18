package kiln.test.handshake;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The server's configuration payload for a joining player: a number. */
public record Hello(int number) implements CustomPacketPayload {
    public static final Type<Hello> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TestHandshake.ID, "hello"));
    public static final StreamCodec<ByteBuf, Hello> CODEC = ByteBufCodecs.VAR_INT.map(Hello::new, Hello::number);

    @Override
    public Type<Hello> type() {
        return TYPE;
    }
}
