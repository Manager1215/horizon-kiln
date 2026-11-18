package kiln.test.attachments;

import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

/**
 * A mod for Kiln's tests of Fabric's data attachments (kiln/tests/attachments.rs): each level tick counts in a level's
 * attachment kept with the world and in one kept only while the world is open, and writes both into a third, which
 * the test reads from the world's files; a player who connects counts a visit (gone when they die) and a keepsake
 * (kept through death), and one who respawns writes what they still have. A synced count of visits goes to the
 * player's own window only (D-199), whose side writes it in the chat ({@link TestAttachmentsClient}), and a synced
 * one of the level's ticks to everyone in it. Written against Fabric's API as any mod
 * is; built against Kiln's stand-ins.
 */
public final class TestAttachments implements ModInitializer {
    public static final String ID = "kiln_test_attachments";
    static final AttachmentType<Integer> TICKS =
            AttachmentRegistry.create(id("ticks"), b -> b.persistent(Codec.INT).initializer(() -> 0));
    static final AttachmentType<Integer> SEEN = AttachmentRegistry.createDefaulted(id("seen"), () -> 0);
    static final AttachmentType<List<Integer>> REPORT = AttachmentRegistry.createPersistent(id("report"),
            Codec.INT.listOf());
    static final AttachmentType<Integer> VISITS = AttachmentRegistry.createPersistent(id("visits"), Codec.INT);
    static final AttachmentType<Integer> KEEPSAKES =
            AttachmentRegistry.create(id("keepsakes"), b -> b.persistent(Codec.INT).copyOnDeath());
    static final AttachmentType<List<Integer>> AFTER_DEATH = AttachmentRegistry.createPersistent(id("after_death"),
            Codec.INT.listOf());

    /** The player's visits, sent to their own window (D-199). */
    static final AttachmentType<Integer> GREETED = AttachmentRegistry.create(id("greeted"),
            b -> b.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
    /** The level's ticks, sent to everyone in it. */
    static final AttachmentType<Integer> CLOCK = AttachmentRegistry.create(id("clock"),
            b -> b.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }

    @Override
    public void onInitialize() {
        ServerTickEvents.START_LEVEL_TICK.register(level -> {
            int ticks = level.modifyAttached(TICKS, n -> n == null ? 1 : n + 1);
            int seen = level.getAttachedOrCreate(SEEN) + 1;
            level.setAttached(SEEN, seen);
            level.setAttached(REPORT, List.of(ticks, seen));
            level.setAttached(CLOCK, ticks);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            player.setAttached(VISITS, player.getAttachedOrElse(VISITS, 0) + 1);
            player.setAttached(KEEPSAKES, player.getAttachedOrElse(KEEPSAKES, 0) + 1);
            player.setAttached(GREETED, player.getAttached(VISITS));
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> player.setAttached(AFTER_DEATH,
                List.of(player.getAttachedOrElse(VISITS, 0), player.getAttachedOrElse(KEEPSAKES, 0),
                        old.getAttachedOrElse(VISITS, 0))));
    }
}
