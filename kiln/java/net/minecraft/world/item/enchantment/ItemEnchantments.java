package net.minecraft.world.item.enchantment;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;

/**
 * Kiln's stand-in for {@code ItemEnchantments}: a stack's enchantments ({@code minecraft:enchantments}, an enchanted
 * book's {@code minecraft:stored_enchantments}) and their levels, as the game saves them ({@code {"minecraft:sharpness":
 * 5}}). An id Kiln doesn't know as one of the game's enchantments (kiln.GameEnchantments) is left out.
 */
public class ItemEnchantments implements net.minecraft.world.item.component.TooltipProvider {
    public static final ItemEnchantments EMPTY = new ItemEnchantments(new Object2IntLinkedOpenHashMap<>());
    public static final Codec<ItemEnchantments> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(
            map -> {
                Object2IntLinkedOpenHashMap<Holder<Enchantment>> levels = new Object2IntLinkedOpenHashMap<>();
                map.forEach((id, level) -> {
                    Holder<Enchantment> holder = kiln.GameEnchantments.holder(id);
                    if (holder != null && level > 0) {
                        levels.put(holder, Math.min(level, Enchantment.MAX_LEVEL));
                    }
                });
                return levels.isEmpty() ? EMPTY : new ItemEnchantments(levels);
            },
            enchantments -> {
                Map<String, Integer> out = new LinkedHashMap<>();
                enchantments.enchantments.forEach((holder, level) ->
                        holder.unwrapKey().ifPresent(k -> out.put(k.identifier().toString(), level)));
                return out;
            });

    private final Object2IntLinkedOpenHashMap<Holder<Enchantment>> enchantments;

    ItemEnchantments(Object2IntLinkedOpenHashMap<Holder<Enchantment>> enchantments) {
        this.enchantments = enchantments;
    }

    protected ItemEnchantments() {
        this(new Object2IntLinkedOpenHashMap<>());
    }

    public int getLevel(Holder<Enchantment> enchantment) {
        return enchantments.getInt(enchantment);
    }

    public Set<Holder<Enchantment>> keySet() {
        return java.util.Collections.unmodifiableSet(enchantments.keySet());
    }

    public Set<Object2IntMap.Entry<Holder<Enchantment>>> entrySet() {
        return java.util.Collections.unmodifiableSet(enchantments.object2IntEntrySet());
    }

    public int size() {
        return enchantments.size();
    }

    public boolean isEmpty() {
        return enchantments.isEmpty();
    }

    /**
     * Its lines in a tooltip, each enchantment's name and level, as the game's: those of the game's tag
     * {@code tooltip_order} first, in its order, then the others.
     */
    @Override
    public void addToTooltip(net.minecraft.world.item.Item.TooltipContext context, Consumer<Component> lines,
            net.minecraft.world.item.TooltipFlag flag, net.minecraft.core.component.DataComponentGetter components) {
        java.util.Set<Holder<Enchantment>> shown = new java.util.HashSet<>();
        for (Holder<Enchantment> ordered : kiln.GameEnchantments.tooltipOrder()) {
            String key = kiln.GameEnchantments.key(ordered);
            for (Object2IntMap.Entry<Holder<Enchantment>> e : enchantments.object2IntEntrySet()) {
                if (!shown.contains(e.getKey()) && kiln.GameEnchantments.key(e.getKey()).equals(key)) {
                    shown.add(e.getKey());
                    lines.accept(Enchantment.getFullname(e.getKey(), e.getIntValue()));
                }
            }
        }
        enchantments.forEach((holder, level) -> {
            if (!shown.contains(holder)) {
                lines.accept(Enchantment.getFullname(holder, level));
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemEnchantments other && enchantments.equals(other.enchantments);
    }

    @Override
    public int hashCode() {
        return enchantments.hashCode();
    }

    @Override
    public String toString() {
        return "ItemEnchantments{" + enchantments + "}";
    }

    /** Enchantments being changed, made into an {@link ItemEnchantments} with {@link #toImmutable}. */
    public static class Mutable {
        private final Object2IntLinkedOpenHashMap<Holder<Enchantment>> enchantments;

        public Mutable(ItemEnchantments from) {
            enchantments = new Object2IntLinkedOpenHashMap<>(from.enchantments);
        }

        protected Mutable() {
            this(EMPTY);
        }

        public void set(Holder<Enchantment> enchantment, int level) {
            if (level <= 0) {
                enchantments.removeInt(enchantment);
            } else {
                enchantments.put(enchantment, Math.min(level, Enchantment.MAX_LEVEL));
            }
        }

        public void upgrade(Holder<Enchantment> enchantment, int level) {
            if (level > 0) {
                enchantments.merge(enchantment, Math.min(level, Enchantment.MAX_LEVEL), Integer::max);
            }
        }

        public void removeIf(Predicate<Holder<Enchantment>> predicate) {
            enchantments.keySet().removeIf(predicate);
        }

        public int getLevel(Holder<Enchantment> enchantment) {
            return enchantments.getOrDefault(enchantment, 0);
        }

        public Set<Holder<Enchantment>> keySet() {
            return enchantments.keySet();
        }

        public ItemEnchantments toImmutable() {
            return enchantments.isEmpty() ? EMPTY : new ItemEnchantments(new Object2IntLinkedOpenHashMap<>(enchantments));
        }
    }
}
