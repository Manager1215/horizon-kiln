package kiln;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A message the game's chat received, as the {@code Component} vanilla's client would hand the mods (D-211): its
 * translation and arguments kept, so a mod that asks whether a game message is a death message
 * ({@code TranslatableContents} with a {@code death.} key) sees one. Kiln had only the plain text before. Styles are
 * left out: the game draws the message itself, the mods only read it.
 */
public final class ChatJson {
    private ChatJson() {}

    /**
     * The component the game's chat JSON {@code json} says, or a literal {@code text} when it can't be read. A
     * translation no language here has becomes {@code text} itself at the top, so its {@code getString} is what the
     * player read.
     */
    public static MutableComponent component(String json, String text) {
        try {
            JsonElement e = JsonParser.parseString(json);
            if (e.isJsonObject() && e.getAsJsonObject().has("translate")
                    && !Language.has(e.getAsJsonObject().get("translate").getAsString())) {
                return Component.literal(text);
            }
            return of(e);
        } catch (RuntimeException e) {
            return Component.literal(text);
        }
    }

    private static MutableComponent of(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return Component.empty();
        }
        if (e.isJsonPrimitive()) {
            return Component.literal(e.getAsString());
        }
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            if (a.isEmpty()) {
                return Component.empty();
            }
            MutableComponent first = of(a.get(0));
            for (int i = 1; i < a.size(); i++) {
                first.append(of(a.get(i)));
            }
            return first;
        }
        JsonObject o = e.getAsJsonObject();
        MutableComponent c;
        if (o.has("translate")) {
            String key = o.get("translate").getAsString();
            List<Object> args = new ArrayList<>();
            if (o.has("with") && o.get("with").isJsonArray()) {
                for (JsonElement arg : o.getAsJsonArray("with")) {
                    args.add(arg.isJsonPrimitive() && arg.getAsJsonPrimitive().isNumber() ? arg.getAsNumber() : of(arg));
                }
            }
            String fallback = o.has("fallback") ? o.get("fallback").getAsString() : null;
            c = Component.translatableWithFallback(key, fallback, args.toArray());
        } else if (o.has("text")) {
            c = Component.literal(o.get("text").getAsString());
        } else if (o.has("")) {
            c = Component.literal(o.get("").getAsString());
        } else {
            c = Component.empty();
        }
        if (o.has("extra") && o.get("extra").isJsonArray()) {
            for (JsonElement x : o.getAsJsonArray("extra")) {
                c.append(of(x));
            }
        }
        return c;
    }
}
