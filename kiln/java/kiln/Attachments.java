package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
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
                return KEPT.computeIfAbsent("client level " + dimension, k -> new Holder());
            }
            return KEPT.computeIfAbsent("level " + dimension, k -> stored(
                    level instanceof ServerLevel server ? server.getDataStorage() : null, "level/" + dimension.replace(':', '/')));
        }
        if (target instanceof Entity entity) {
            boolean client = entity.level() != null && entity.level().isClientSide();
            String key = (client ? "client entity " : "entity ") + entity.getUUID();
            if (!client && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel server) {
                return KEPT.computeIfAbsent(key, k -> stored(server.getDataStorage(), "player/" + player.getUUID()));
            }
            return KEPT.computeIfAbsent(key, k -> new Holder());
        }
        if (target instanceof LevelChunk chunk) {
            Level level = chunk.getLevel();
            String key = (level != null && level.isClientSide() ? "client chunk " : "chunk ")
                    + (level == null ? "" : level.dimension().identifier()) + " " + chunk.getPos().x() + " "
                    + chunk.getPos().z();
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
        for (var it = h.values.keySet().iterator(); it.hasNext();) {
            AttachmentType<?> type = it.next();
            if (!type.copyOnDeath()) {
                persistent |= type.isPersistent();
                it.remove();
            }
        }
        if (persistent) {
            h.dirty();
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
