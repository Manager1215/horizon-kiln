package kiln.test.late;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A line, both ways: the player's for the server, and the server's answer. */
public record Slow(String line) implements CustomPacketPayload {
    public static final Type<Slow> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TestLate.ID, "slow"));
    public static final StreamCodec<ByteBuf, Slow> CODEC = ByteBufCodecs.STRING_UTF8.map(Slow::new, Slow::line);

    @Override
    public Type<Slow> type() {
        return TYPE;
    }
}
