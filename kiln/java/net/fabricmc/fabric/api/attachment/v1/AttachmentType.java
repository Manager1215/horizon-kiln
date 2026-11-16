package net.fabricmc.fabric.api.attachment.v1;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for Fabric's {@code AttachmentType}: a kind of data a mod attaches to a block entity, an entity, a
 * level or a chunk ({@link AttachmentTarget}), made by {@link AttachmentRegistry}. A persistent one is kept with the
 * world ({@code kiln.Attachments}); one without a codec lives while the world is open.
 */
public interface AttachmentType<A> {
    Identifier identifier();

    /** How it is saved; null for one kept only while the world is open. */
    Codec<A> persistenceCodec();

    default boolean isPersistent() {
        return persistenceCodec() != null;
    }

    /** What {@link AttachmentTarget#getAttachedOrCreate(AttachmentType)} makes; null for none. */
    Supplier<A> initializer();

    /** Whether a player keeps it when they die and respawn. */
    boolean copyOnDeath();

    /** Whether the mod asked the game to send it to the players ({@code syncWith}): Kiln sends none yet. */
    boolean isSynced();
}
