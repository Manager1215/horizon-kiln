package kiln;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * The game's tags, for the mods' code on Kiln's server side: whether an item is in an item tag, asked of the game
 * during a server call ({@code tag <tag> <item>}, answered from the world's tables: the game's data, the world's data
 * packs, the content mods'), and kept until another world opens.
 */
public final class Tags {
    private static final Map<String, Boolean> ITEMS = new ConcurrentHashMap<>();

    private Tags() {}

    /** Whether {@code item} is in the item tag {@code tag}; outside a server call Kiln can't ask: no. */
    public static boolean itemIn(Identifier tag, Item item) {
        String key = tag + " " + item.kiln$id();
        Boolean known = ITEMS.get(key);
        if (known != null) {
            return known;
        }
        if (!Server.inCall()) {
            return false;
        }
        boolean in = "true".equals(Server.ask("tag", tag.toString(), item.kiln$id().toString()));
        ITEMS.put(key, in);
        return in;
    }

    /** Another world opened: its tags may differ. */
    static void forget() {
        ITEMS.clear();
    }
}
