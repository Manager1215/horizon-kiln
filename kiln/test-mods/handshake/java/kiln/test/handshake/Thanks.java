package kiln.test.handshake;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The window's configuration answer to a hello: the hello's number, doubled. */
public record Thanks(int number) implements CustomPacketPayload {
    public static final Type<Thanks> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TestHandshake.ID, "thanks"));
    public static final StreamCodec<ByteBuf, Thanks> CODEC = ByteBufCodecs.VAR_INT.map(Thanks::new, Thanks::number);

    @Override
    public Type<Thanks> type() {
        return TYPE;
    }
}
