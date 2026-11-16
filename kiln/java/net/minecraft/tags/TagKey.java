package net.minecraft.tags;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code TagKey}: a tag's registry and id, as vanilla's. */
public record TagKey<T>(ResourceKey<? extends Registry<T>> registry, Identifier location) {
    public static <T> TagKey<T> create(ResourceKey<? extends Registry<T>> registry, Identifier location) {
        return new TagKey<>(registry, location);
    }

    /**
     * Fabric's (its {@code FabricTagKey}, on every TagKey): the tag's translation key, {@code tag.<registry>.<ns>.<path>}
     * with dots for slashes (the registry's namespace before it when it isn't {@code minecraft}).
     */
    public String getTranslationKey() {
        StringBuilder key = new StringBuilder("tag.");
        if (registry != null) {
            Identifier id = registry.identifier();
            if (!id.getNamespace().equals("minecraft")) {
                key.append(id.getNamespace()).append('.');
            }
            key.append(id.getPath().replace('/', '.')).append('.');
        }
        return key.append(location.getNamespace()).append('.')
                .append(location.getPath().replace('/', '.').replace(':', '.')).toString();
    }

    /** Fabric's: the tag's name, translated where a language has it, else {@code #ns:path}. */
    public net.minecraft.network.chat.Component getName() {
        String key = getTranslationKey();
        return net.minecraft.locale.Language.getInstance().has(key)
                ? net.minecraft.network.chat.Component.translatable(key)
                : net.minecraft.network.chat.Component.literal("#" + location);
    }

    @Override
    public String toString() {
        return "TagKey[" + (registry == null ? "?" : registry.identifier()) + " / " + location + "]";
    }
}
