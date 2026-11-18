package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Fabric's data attachments on Kiln's side (D-161): the values the mods attach to a block entity, an entity, a level or
 * a chunk ({@link AttachmentTarget}).
 *
 * <p>Kiln's levels, entities and chunks are new objects each call, so their values are kept by what names them: a
 * level's by its side and dimension, an entity's by its UUID, a chunk's by its dimension and position. A block entity's
 * object lives on between ticks ({@link BlockEntities}), so its values stay with it. The persistent ones (a type with a
 * codec) are kept with the world: a block entity's in its saved compound under {@code fabric:attachments}, as Fabric
 * saves them, so they go to the game with the rest of it; a server level's and a player's in Kiln's saved data
 * ({@code <world>/kiln/data/fabric/attachments/...}). An entity's other than a player's, a chunk's and the client's
 * live while the world is open.
 *
 * <p>A synced type's ({@code syncWith}, D-199) value set on the server goes to the players its predicate picks of
 * those who see the target, over Kiln's channel as a message after the id {@link #SYNC} ({@link Net}); the window's
 * side keeps it for the target of the same name on its side. A joining or respawned player gets their own and their
 * level's.
 */
public final class Attachments {
    private static final Map<String, Holder> KEPT = new ConcurrentHashMap<>();
    /** A player who died, during their respawn's call: what they had, apart from the new one's. */
    private static final Map<Object, Holder> DEAD = new IdentityHashMap<>();
    /** The holders whose persistent values a mod read this call: it may have changed them in place. */
    private static final List<Holder> READ = new ArrayList<>();
    /** How long a persistent value read and maybe changed in place waits to be written (vanilla autosaves at 5 min). */
    private static final long WRITE_READ_AFTER_MS = 60_000;

    private Attachments() {}

    /** The id a synced attachment's message starts with (D-199): no mod's payload type, Kiln's own. */
    static final Identifier SYNC = Identifier.fromNamespaceAndPath("kiln", "attachment");
    private static final int LEVEL = 0;
    private static final int ENTITY = 1;
    private static final int BLOCK_ENTITY = 2;
    private static final int CHUNK = 3;

    /** How a synced type is written for the players, and which of them get it. */
    private record Sync(StreamCodec<RegistryFriendlyByteBuf, Object> codec, AttachmentSyncPredicate predicate) {}

    private static final Map<Identifier, Sync> SYNCED = new ConcurrentHashMap<>();

    /** Whether a mod made a synced type: then a joining player's call is wanted ({@link #joined}). */
    static boolean anySynced() {
        return !SYNCED.isEmpty();
    }

    /** A mod's type is synced ({@code AttachmentRegistry.Builder.syncWith}). */
    @SuppressWarnings("unchecked")
    public static <A> void synced(AttachmentType<A> type, StreamCodec<? super RegistryFriendlyByteBuf, A> codec,
            AttachmentSyncPredicate predicate) {
        SYNCED.put(type.identifier(), new Sync((StreamCodec<RegistryFriendlyByteBuf, Object>) (Object) codec, predicate));
    }

    /** One target's values, by type. */
    static final class Holder {
        final Map<AttachmentType<?>, Object> values = new LinkedHashMap<>(4);
        /** What was saved under types no mod has made (a mod taken out): kept as it was. */
        final Map<String, Object> unknown = new TreeMap<>();
        Map<AttachmentType<?>, Event<?>> listeners;
        /** Where its persistent values are kept with the world; null for a block entity's and those kept in memory. */
        SavedDataStorage storage;
        SavedDataType<Saved> savedType;
        Saved saved;
        boolean inStorage;
        boolean read;
        long writtenAt = System.currentTimeMillis();

        /** Its persistent values have changed: written at the call's end. */
        void dirty() {
            writtenAt = System.currentTimeMillis();
            if (storage == null) {
                return;
            }
            if (!inStorage) {
                storage.set(savedType, saved);
                inStorage = true;
            } else {
                saved.setDirty();
            }
        }

        Holder copy() {
            Holder h = new Holder();
            h.values.putAll(values);
            h.unknown.putAll(unknown);
            return h;
        }

        boolean hasPersistent() {
            if (!unknown.isEmpty()) {
                return true;
            }
            for (AttachmentType<?> type : values.keySet()) {
                if (type.isPersistent()) {
                    return true;
                }
            }
            return false;
        }

        /** Its persistent values as JSON, by type id. */
        JsonObject json() {
            JsonObject json = new JsonObject();
            unknown.forEach((id, value) -> json.add(id, (JsonElement) value));
            values.forEach((type, value) -> {
                JsonElement encoded = encode(type, value);
                if (encoded != null) {
                    json.add(type.identifier().toString(), encoded);
                }
            });
            return json;
        }

        void read(JsonElement json) {
            if (!json.isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                AttachmentType<?> type = type(e.getKey());
                if (type == null) {
                    unknown.put(e.getKey(), e.getValue());
                } else {
                    Object value = type.persistenceCodec().parse(JsonOps.INSTANCE, e.getValue()).result().orElse(null);
                    if (value != null) {
                        values.put(type, value);
                    }
                }
            }
        }
    }

    /** A holder's persistent values as a world's saved data. */
    static final class Saved extends SavedData {
        final Holder holder;

        Saved(Holder holder) {
            this.holder = holder;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(AttachmentTarget target, AttachmentType<T> type) {
        Holder h = holder(target, false);
        if (h == null) {
            return null;
        }
        Object value = h.values.get(type);
        if (value != null && type.isPersistent() && h.storage != null && !h.read) {
            h.read = true;
            synchronized (READ) {
                READ.add(h);
            }
        }
        return (T) value;
    }

    @SuppressWarnings("unchecked")
    public static <T> T set(AttachmentTarget target, AttachmentType<T> type, T value) {
        Holder h = holder(target, value != null);
        if (h == null) {
            return null;
        }
        T before = (T) (value == null ? h.values.remove(type) : h.values.put(type, value));
        if (type.isPersistent() && (before != null || value != null)) {
            h.dirty();
        }
        if (type.isSynced() && (before != null || value != null)) {
            send(target, type, value, null);
        }
        if (h.listeners != null && before != value) {
            Event<AttachmentTarget.OnAttachedSet<T>> event =
                    (Event<AttachmentTarget.OnAttachedSet<T>>) (Event<?>) h.listeners.get(type);
            if (event != null) {
                event.invoker().onAttachedSet(before, value);
            }
        }
        return before;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <A> Event<AttachmentTarget.OnAttachedSet<A>> onSet(AttachmentTarget target, AttachmentType<A> type) {
        Holder h = holder(target, true);
        if (h.listeners == null) {
            h.listeners = new LinkedHashMap<>();
        }
        return (Event<AttachmentTarget.OnAttachedSet<A>>) h.listeners.computeIfAbsent(type,
                t -> EventFactory.createArrayBacked(AttachmentTarget.OnAttachedSet.class,
                        (AttachmentTarget.OnAttachedSet[] listeners) -> (before, after) -> {
                            for (AttachmentTarget.OnAttachedSet l : listeners) {
                                l.onAttachedSet(before, after);
                            }
                        }));
    }

    /** {@code target}'s values; null when it has none and {@code create} is false. */
    private static Holder holder(AttachmentTarget target, boolean create) {
        if (target instanceof BlockEntity entity && entity.getLevel() != null && entity.getLevel().isClientSide()) {
            // The window's: kept by name, as what the server sends it names it (D-199).
            return KEPT.computeIfAbsent(clientKey(BLOCK_ENTITY, entity.getLevel().dimension().identifier().toString(),
                    entity.getBlockPos().getX(), entity.getBlockPos().getY(), entity.getBlockPos().getZ(), null),
                    k -> new Holder());
        }
        if (target instanceof BlockEntity entity) {
            Holder h = (Holder) entity.kiln$attachments;
            if (h == null && create) {
                entity.kiln$attachments = h = new Holder();
            }
            return h;
        }
        if (!DEAD.isEmpty()) {
            Holder dead = DEAD.get(target);
            if (dead != null) {
                return dead;
            }
        }
        if (target instanceof Level level) {
            String dimension = level.dimension().identifier().toString();
            if (level.isClientSide()) {
                return KEPT.computeIfAbsent(clientKey(LEVEL, dimension, 0, 0, 0, null), k -> new Holder());
            }
            return KEPT.computeIfAbsent("level " + dimension, k -> stored(
                    level instanceof ServerLevel server ? server.getDataStorage() : null, "level/" + dimension.replace(':', '/')));
        }
        if (target instanceof Entity entity) {
            boolean client = entity.level() != null && entity.level().isClientSide();
            String key = client ? clientKey(ENTITY, null, 0, 0, 0, entity.getUUID()) : "entity " + entity.getUUID();
            if (!client && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel server) {
                return KEPT.computeIfAbsent(key, k -> stored(server.getDataStorage(), "player/" + player.getUUID()));
            }
            return KEPT.computeIfAbsent(key, k -> new Holder());
        }
        if (target instanceof LevelChunk chunk) {
            Level level = chunk.getLevel();
            String dimension = level == null ? "" : level.dimension().identifier().toString();
            String key = level != null && level.isClientSide()
                    ? clientKey(CHUNK, dimension, chunk.getPos().x(), 0, chunk.getPos().z(), null)
                    : "chunk " + dimension + " " + chunk.getPos().x() + " " + chunk.getPos().z();
            return KEPT.computeIfAbsent(key, k -> new Holder());
        }
        throw new UnsupportedOperationException("Kiln keeps no attachments on " + target.getClass().getName());
    }

    /** A holder whose persistent values are kept in {@code storage} as {@code fabric:attachments/<path>}. */
    private static Holder stored(SavedDataStorage storage, String path) {
        Holder h = new Holder();
        if (storage == null) {
            return h;
        }
        h.storage = storage;
        h.saved = new Saved(h);
        Codec<Saved> codec = Codec.PASSTHROUGH.xmap(dynamic -> {
            h.read(dynamic.convert(JsonOps.INSTANCE).getValue());
            return h.saved;
        }, saved -> new Dynamic<>(JsonOps.INSTANCE, saved.holder.json()));
        h.savedType = new SavedDataType<>(Identifier.fromNamespaceAndPath("fabric", "attachments/" + path),
                () -> h.saved, codec, null);
        h.inStorage = storage.get(h.savedType) != null;
        return h;
    }

    /**
     * A block entity's saved compound: its persistent values go in under {@code fabric:attachments}, as Fabric saves
     * them, and what was there under types no mod has made stays.
     */
    public static void save(BlockEntity entity, Map<String, Object> compound) {
        Holder h = (Holder) entity.kiln$attachments;
        if (h == null || !h.hasPersistent()) {
            return;
        }
        Map<String, Object> saved = new TreeMap<>(h.unknown);
        ValueOutput out = TagValues.output(saved);
        h.values.forEach((type, value) -> store(out, type, value));
        if (!saved.isEmpty()) {
            compound.put(AttachmentTarget.NAME_IN_NBT, saved);
        }
    }

    /** A block entity loaded from its saved compound: its persistent values, as {@link #save} wrote them. */
    @SuppressWarnings("unchecked")
    public static void load(BlockEntity entity, Map<String, Object> compound) {
        if (!(compound.get(AttachmentTarget.NAME_IN_NBT) instanceof Map<?, ?> saved)) {
            return;
        }
        Holder h = new Holder();
        ValueInput in = TagValues.input((Map<String, Object>) saved);
        for (Map.Entry<?, ?> e : saved.entrySet()) {
            String id = (String) e.getKey();
            AttachmentType<?> type = type(id);
            if (type == null) {
                h.unknown.put(id, e.getValue());
            } else {
                in.read(id, type.persistenceCodec()).ifPresent(value -> h.values.put(type, value));
            }
        }
        entity.kiln$attachments = h;
    }

    /**
     * A player died and respawns: the new one keeps only the types made {@code copyOnDeath}, as in Fabric; the old one
     * has all it had during the call (a mod copying its own from it, in Fabric's {@code COPY_FROM}).
     */
    public static void died(ServerPlayer old, ServerPlayer respawned) {
        Holder h = holder(respawned, true);
        synchronized (DEAD) {
            DEAD.put(old, h.copy());
        }
        boolean persistent = false;
        List<AttachmentType<?>> unsent = new ArrayList<>();
        for (var it = h.values.keySet().iterator(); it.hasNext();) {
            AttachmentType<?> type = it.next();
            if (!type.copyOnDeath()) {
                persistent |= type.isPersistent();
                if (type.isSynced()) {
                    unsent.add(type);
                }
                it.remove();
            }
        }
        if (persistent) {
            h.dirty();
        }
        // The window keeps a player's by their UUID, which the new one has too: what they lost goes from it (D-199).
        for (AttachmentType<?> type : unsent) {
            send(respawned, type, null, null);
        }
    }

    /**
     * A server call is done: the persistent values a mod read and may have changed in place are written once a minute
     * at most ({@link #WRITE_READ_AFTER_MS}); what a mod set is written at once.
     */
    static void callDone() {
        synchronized (DEAD) {
            DEAD.clear();
        }
        synchronized (READ) {
            long now = System.currentTimeMillis();
            READ.removeIf(h -> {
                if (now - h.writtenAt < WRITE_READ_AFTER_MS) {
                    return false;
                }
                h.read = false;
                h.dirty();
                return true;
            });
        }
    }

    /** Another world opens: the open one's values read this session are written to its storage first. */
    static void forget() {
        synchronized (READ) {
            READ.forEach(Holder::dirty);
            READ.clear();
        }
        KEPT.clear();
    }

    /** The window's holder of a target the server named: a level, an entity (by UUID), a block entity, a chunk. */
    private static String clientKey(int kind, String dimension, int x, int y, int z, UUID uuid) {
        return switch (kind) {
            case LEVEL -> "client level " + dimension;
            case ENTITY -> "client entity " + uuid;
            case BLOCK_ENTITY -> "client block entity " + dimension + " " + x + " " + y + " " + z;
            default -> "client chunk " + dimension + " " + x + " " + z;
        };
    }

    /**
     * A synced value of {@code target}'s set on the server (null: removed): to the players who see the target, as
     * Fabric's, and its predicate picks; {@code only}, when given, is the one player it may go to (one joining).
     */
    private static <T> void send(AttachmentTarget target, AttachmentType<T> type, T value, ServerPlayer only) {
        Sync sync = SYNCED.get(type.identifier());
        if (sync == null) {
            return;
        }
        Collection<ServerPlayer> seeing;
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        buffer.writeIdentifier(SYNC);
        if (target instanceof Level level && level instanceof ServerLevel server) {
            buffer.writeByte(LEVEL);
            buffer.writeUtf(level.dimension().identifier().toString());
            seeing = only != null ? List.of(only) : PlayerLookup.level(server);
        } else if (target instanceof Entity entity && entity.level() instanceof ServerLevel) {
            buffer.writeByte(ENTITY);
            buffer.writeLong(entity.getUUID().getMostSignificantBits());
            buffer.writeLong(entity.getUUID().getLeastSignificantBits());
            if (only != null) {
                seeing = List.of(only);
            } else {
                // Those who see it, and a player themselves, as Fabric's (the predicate picks among them).
                Collection<ServerPlayer> all = new LinkedHashSet<>(PlayerLookup.tracking(entity));
                if (entity instanceof ServerPlayer player) {
                    all.add(player);
                }
                seeing = all;
            }
        } else if (target instanceof BlockEntity entity && entity.getLevel() instanceof ServerLevel server) {
            BlockPos at = entity.getBlockPos();
            buffer.writeByte(BLOCK_ENTITY);
            buffer.writeUtf(server.dimension().identifier().toString());
            buffer.writeInt(at.getX());
            buffer.writeInt(at.getY());
            buffer.writeInt(at.getZ());
            seeing = only != null ? List.of(only) : PlayerLookup.tracking(entity);
        } else if (target instanceof LevelChunk chunk && chunk.getLevel() instanceof ServerLevel server) {
            buffer.writeByte(CHUNK);
            buffer.writeUtf(server.dimension().identifier().toString());
            buffer.writeInt(chunk.getPos().x());
            buffer.writeInt(chunk.getPos().z());
            seeing = only != null ? List.of(only) : PlayerLookup.tracking(server, chunk.getPos());
        } else {
            return; // the window's own, or a target with no level: nobody to send it to
        }
        buffer.writeIdentifier(type.identifier());
        buffer.writeBoolean(value != null);
        if (value != null) {
            try {
                sync.codec().encode(buffer, value);
            } catch (RuntimeException e) {
                Host.send("log", "a synced attachment " + type.identifier() + " didn't write, and isn't sent: " + e);
                return;
            }
        }
        if (buffer.readableBytes() > Net.MAX_BYTES) {
            Host.send("log", "a synced attachment " + type.identifier() + " is " + buffer.readableBytes()
                    + " bytes, more than Horizon carries: not sent");
            return;
        }
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        for (ServerPlayer player : seeing) {
            boolean picked;
            try {
                picked = sync.predicate().test(target, player);
            } catch (RuntimeException e) {
                picked = false;
                Host.send("log", "the sync predicate of " + type.identifier() + " threw " + e);
            }
            if (picked) {
                Net.send(player, bytes);
            }
        }
    }

    /**
     * A player joined or respawned (D-199): the synced values of their own and of their level go to them, as
     * Fabric's join sync does; an entity's, a block entity's or a chunk's go when set, not when the player comes to see
     * the target (Kiln doesn't hear when a player starts to see one).
     */
    @SuppressWarnings("unchecked")
    public static void joined(ServerPlayer player) {
        if (SYNCED.isEmpty()) {
            return;
        }
        for (AttachmentTarget target : new AttachmentTarget[] {player.level(), player}) {
            Holder h = target == null ? null : holder(target, false);
            if (h == null) {
                continue;
            }
            for (Map.Entry<AttachmentType<?>, Object> e : new ArrayList<>(h.values.entrySet())) {
                if (e.getKey().isSynced()) {
                    send(target, (AttachmentType<Object>) e.getKey(), e.getValue(), player);
                }
            }
        }
    }

    /** The window's side: a synced value from the server (D-199), for the target of that name on the window's side. */
    static void received(byte[] bytes) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), null);
        buffer.readIdentifier(); // SYNC
        int kind = buffer.readByte();
        String key = switch (kind) {
            case LEVEL -> clientKey(LEVEL, buffer.readUtf(), 0, 0, 0, null);
            case ENTITY -> clientKey(ENTITY, null, 0, 0, 0, new UUID(buffer.readLong(), buffer.readLong()));
            case BLOCK_ENTITY -> clientKey(BLOCK_ENTITY, buffer.readUtf(), buffer.readInt(), buffer.readInt(),
                    buffer.readInt(), null);
            case CHUNK -> {
                String dimension = buffer.readUtf();
                int x = buffer.readInt();
                yield clientKey(CHUNK, dimension, x, 0, buffer.readInt(), null);
            }
            default -> null;
        };
        Identifier id = buffer.readIdentifier();
        Sync sync = SYNCED.get(id);
        AttachmentType<?> type = AttachmentRegistry.kiln$get(id);
        if (key == null || sync == null || type == null) {
            Host.send("log", "a synced attachment from the server no mod made: " + id);
            return;
        }
        Object value = buffer.readBoolean() ? sync.codec().decode(buffer) : null;
        Holder h = KEPT.computeIfAbsent(key, k -> new Holder());
        Object before = value == null ? h.values.remove(type) : h.values.put(type, value);
        if (h.listeners != null && before != value) {
            @SuppressWarnings("unchecked")
            Event<AttachmentTarget.OnAttachedSet<Object>> event =
                    (Event<AttachmentTarget.OnAttachedSet<Object>>) (Event<?>) h.listeners.get(type);
            if (event != null) {
                event.invoker().onAttachedSet(before, value);
            }
        }
    }

    private static AttachmentType<?> type(String id) {
        Identifier parsed = Identifier.tryParse(id);
        AttachmentType<?> type = parsed == null ? null : AttachmentRegistry.kiln$get(parsed);
        return type != null && type.isPersistent() ? type : null;
    }

    @SuppressWarnings("unchecked")
    private static <T> JsonElement encode(AttachmentType<T> type, Object value) {
        Codec<T> codec = type.persistenceCodec();
        return codec == null ? null : codec.encodeStart(JsonOps.INSTANCE, (T) value).result().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static <T> void store(ValueOutput out, AttachmentType<T> type, Object value) {
        if (type.persistenceCodec() != null) {
            out.store(type.identifier().toString(), type.persistenceCodec(), (T) value);
        }
    }
}
