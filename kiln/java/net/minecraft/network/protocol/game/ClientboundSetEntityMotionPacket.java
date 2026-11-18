package net.minecraft.network.protocol.game;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Kiln's stand-in for {@code ClientboundSetEntityMotionPacket}: an entity's motion, as a server sends it to a player.
 * Sent through a player's connection to that player about themself, Kiln sets their motion in the game (#220, D-187).
 */
public record ClientboundSetEntityMotionPacket(int id, Vec3 movement)
        implements net.minecraft.network.protocol.Packet<ClientGamePacketListener> {
    public ClientboundSetEntityMotionPacket(Entity entity) {
        this(entity.getId(), entity.getDeltaMovement());
    }

    public net.minecraft.network.protocol.PacketType<ClientboundSetEntityMotionPacket> type() { kiln.Missing.called("net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket.type"); return new net.minecraft.network.protocol.PacketType<>((net.minecraft.network.protocol.PacketFlow) null, (net.minecraft.resources.Identifier) null); }
    public void handle(ClientGamePacketListener listener) { kiln.Missing.called("net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket.handle"); }
}
