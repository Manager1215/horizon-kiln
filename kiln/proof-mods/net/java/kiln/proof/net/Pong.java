package kiln.proof.net;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The server's answer: the ping's number, how many pings it has had, and where it saw the player and who they are, by
 * the buffer's own methods.
 */
public record Pong(int number, int served, BlockPos at, UUID player, String word) implements CustomPacketPayload {
    public static final Type<Pong> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ProofNet.ID, "pong"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Pong> CODEC = CustomPacketPayload.codec(Pong::write, Pong::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(number);
        buffer.writeVarInt(served);
        buffer.writeBlockPos(at);
        buffer.writeUUID(player);
        buffer.writeUtf(word);
    }

    private static Pong read(RegistryFriendlyByteBuf buffer) {
        return new Pong(buffer.readVarInt(), buffer.readVarInt(), buffer.readBlockPos(), buffer.readUUID(),
                buffer.readUtf());
    }

    @Override
    public Type<Pong> type() {
        return TYPE;
    }
}
