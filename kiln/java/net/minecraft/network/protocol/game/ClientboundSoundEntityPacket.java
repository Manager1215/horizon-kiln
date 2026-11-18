package net.minecraft.network.protocol.game;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Kiln's stand-in for {@code ClientboundSoundEntityPacket}: a sound played at an entity, as a server sends it to a
 * player. Sent through a player's connection ({@code ServerGamePacketListenerImpl.send}), Kiln plays it at the
 * entity's place (#220, D-187).
 */
public class ClientboundSoundEntityPacket implements net.minecraft.network.protocol.Packet<ClientGamePacketListener> {
    private final Holder<SoundEvent> sound;
    private final SoundSource source;
    private final int id;
    private final float volume;
    private final float pitch;
    private final long seed;
    private final Entity entity;

    public ClientboundSoundEntityPacket(Holder<SoundEvent> sound, SoundSource source, Entity entity, float volume,
            float pitch, long seed) {
        this.sound = sound;
        this.source = source;
        this.entity = entity;
        this.id = entity == null ? 0 : entity.getId();
        this.volume = volume;
        this.pitch = pitch;
        this.seed = seed;
    }

    public Holder<SoundEvent> getSound() {
        return sound;
    }

    public SoundSource getSource() {
        return source;
    }

    public int getId() {
        return id;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }

    public long getSeed() {
        return seed;
    }

    /** Kiln's own: the entity it plays at (vanilla's packet keeps only its number). */
    public Entity kiln$entity() {
        return entity;
    }

    public net.minecraft.network.protocol.PacketType<ClientboundSoundEntityPacket> type() { kiln.Missing.called("net.minecraft.network.protocol.game.ClientboundSoundEntityPacket.type"); return new net.minecraft.network.protocol.PacketType<>((net.minecraft.network.protocol.PacketFlow) null, (net.minecraft.resources.Identifier) null); }
    public void handle(ClientGamePacketListener listener) { kiln.Missing.called("net.minecraft.network.protocol.game.ClientboundSoundEntityPacket.handle"); }
}
