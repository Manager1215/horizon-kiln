package net.fabricmc.fabric.api.attachment.v1;

import net.minecraft.server.level.ServerPlayer;

/**
 * Kiln's stand-in for Fabric's {@code AttachmentSyncPredicate}: which players an attachment is sent to. Kiln takes it
 * and sends nothing yet ({@link AttachmentType#isSynced}).
 */
@FunctionalInterface
public interface AttachmentSyncPredicate {
    boolean test(AttachmentTarget target, ServerPlayer player);

    static AttachmentSyncPredicate all() {
        return (target, player) -> true;
    }

    static AttachmentSyncPredicate targetOnly() {
        return (target, player) -> target == player;
    }

    static AttachmentSyncPredicate allButTarget() {
        return (target, player) -> target != player;
    }
}
