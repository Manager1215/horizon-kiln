package net.minecraft.world.level.storage;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Kiln's stand-in for {@code SavedDataStorage}: the Java mods' saved data for one world, kept as JSON under
 * {@code <world>/kiln/data/<namespace>/<path>.json} (vanilla keeps NBT in {@code <world>/data/}; that is Horizon's).
 * Read when a mod first asks, written by {@link #kiln$save} once the mod has marked it dirty.
 */
public class SavedDataStorage {
    private final Path dir;
    private final Map<Identifier, Entry<?>> data = new LinkedHashMap<>();

    private record Entry<T extends SavedData>(SavedDataType<T> type, T value) {}

    /** Kiln's own: a world's storage in {@code dir} ({@code <world>/kiln/data}). */
    public SavedDataStorage(Path dir) {
        this.dir = dir;
    }

    public <T extends SavedData> T computeIfAbsent(SavedDataType<T> type) {
        T found = get(type);
        if (found == null) {
            found = type.constructor().get();
            set(type, found);
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    public <T extends SavedData> T get(SavedDataType<T> type) {
        Entry<?> cached = data.get(type.id());
        if (cached != null) {
            return (T) cached.value();
        }
        Path file = file(type.id());
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            T value = type.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
            data.put(type.id(), new Entry<>(type, value));
            return value;
        } catch (IOException | RuntimeException e) {
            System.err.println("Kiln: can't read " + file + ": " + e);
            return null;
        }
    }

    public <T extends SavedData> void set(SavedDataType<T> type, T value) {
        data.put(type.id(), new Entry<>(type, value));
        value.setDirty();
    }

    /** Kiln's own: writes the data marked dirty. A failed write stays dirty, to be tried again. */
    public void kiln$save() {
        for (Entry<?> entry : data.values()) {
            save(entry);
        }
    }

    private <T extends SavedData> void save(Entry<T> entry) {
        if (!entry.value().isDirty()) {
            return;
        }
        Path file = file(entry.type().id());
        try {
            JsonElement json = entry.type().codec().encodeStart(JsonOps.INSTANCE, entry.value()).getOrThrow();
            Files.createDirectories(file.getParent());
            Path partial = file.resolveSibling(file.getFileName() + ".partial");
            Files.writeString(partial, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
            Files.move(partial, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            entry.value().setDirty(false);
        } catch (IOException | RuntimeException e) {
            System.err.println("Kiln: can't write " + file + ": " + e);
        }
    }

    private Path file(Identifier id) {
        return dir.resolve(id.getNamespace()).resolve(id.getPath() + ".json");
    }
}
