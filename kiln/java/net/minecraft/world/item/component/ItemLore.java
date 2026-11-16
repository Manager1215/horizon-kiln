package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;

/**
 * Kiln's stand-in for {@code ItemLore}: the lines under an item's name in its tooltip ({@code minecraft:lore}), as the
 * game saves them; shown dark purple and italic unless a line says otherwise, as vanilla's.
 */
public record ItemLore(List<Component> lines, List<Component> styledLines) {
    public static final int MAX_LINES = 256;
    public static final ItemLore EMPTY = new ItemLore(List.of());
    private static final Style LORE_STYLE = Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true);
    public static final Codec<ItemLore> CODEC =
            ComponentSerialization.CODEC.sizeLimitedListOf(MAX_LINES).xmap(ItemLore::new, ItemLore::lines);

    public ItemLore {
        lines = List.copyOf(lines);
        styledLines = List.copyOf(styledLines);
    }

    public ItemLore(List<Component> lines) {
        this(lines, styled(lines));
    }

    private static List<Component> styled(List<Component> lines) {
        List<Component> styled = new ArrayList<>(lines.size());
        for (Component line : lines) {
            styled.add(Component.empty().withStyle(LORE_STYLE).append(line));
        }
        return styled;
    }

    /** With {@code line} after the others. */
    public ItemLore withLineAdded(Component line) {
        List<Component> more = new ArrayList<>(lines);
        more.add(line);
        return new ItemLore(more);
    }
}
