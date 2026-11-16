package kiln;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The game's tags, for the mods' code on Kiln's server side: whether an item is in an item tag ({@code tag <tag>
 * <item>}, from the world's tables: the game's data, the world's data packs, the content mods') or a block in a block
 * tag ({@code block_tag <tag> <block>}, the game's), asked of the game during a server call and kept until another
 * world opens.
 */
public final class Tags {
    private static final Map<String, Boolean> ITEMS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> BLOCKS = new ConcurrentHashMap<>();

    private Tags() {}

    /**
     * Whether {@code item} is in the item tag {@code tag}. Outside a server call Kiln can't ask the world, so the
     * game's own tags answer, from its resources (a recipe viewer on the client, D-152, D-159); not kept, as the
     * world's may differ.
     */
    public static boolean itemIn(Identifier tag, Item item) {
        String key = tag + " " + item.kiln$id();
        Boolean known = ITEMS.get(key);
        if (known != null) {
            return known;
        }
        if (!Server.inCall()) {
            return GameItems.inTag("item", tag, item.kiln$id().toString());
        }
        boolean in = "true".equals(Server.ask("tag", tag.toString(), item.kiln$id().toString()));
        ITEMS.put(key, in);
        return in;
    }

    /**
     * Whether {@code block} is in the block tag {@code tag} ({@code block_tag <tag> <block>}, from the game's tags);
     * outside a server call: no.
     */
    public static boolean blockIn(Identifier tag, Block block) {
        String key = tag + " " + block.kiln$id();
        Boolean known = BLOCKS.get(key);
        if (known != null) {
            return known;
        }
        if (!Server.inCall()) {
            return false;
        }
        boolean in = "true".equals(Server.ask("block_tag", tag.toString(), block.kiln$id().toString()));
        BLOCKS.put(key, in);
        return in;
    }

    /** Whether {@code value} (a block or an item) is in {@code tag}: by the registry the tag is of. */
    public static boolean in(TagKey<?> tag, Object value) {
        if (tag == null) {
            return false;
        }
        if (value instanceof Block block && Registries.BLOCK.equals(tag.registry())) {
            return blockIn(tag.location(), block);
        }
        if (value instanceof Item item && Registries.ITEM.equals(tag.registry())) {
            return itemIn(tag.location(), item);
        }
        return false;
    }

    /** Another world opened: its tags may differ. */
    static void forget() {
        ITEMS.clear();
        BLOCKS.clear();
    }
}
