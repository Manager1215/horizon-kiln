package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;
import kiln.TagValues;
import net.minecraft.core.Holder;
import net.minecraft.core.TypedInstance;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/**
 * Kiln's stand-in for {@code ItemStack}: an item and a count. Its components are kept as the game saved them, so a
 * stack a mod's container saves comes back whole; a mod reads and sets them by their registered types, through each
 * type's codec.
 */
public final class ItemStack implements DataComponentHolder, TypedInstance<Item> {
    public static final ItemStack EMPTY = new ItemStack((Item) null, 0);
    public static final MapCodec<ItemStack> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(s -> s.getItem().kiln$id().toString()),
            Codec.INT.fieldOf("count").orElse(1).forGetter(ItemStack::getCount),
            Codec.unboundedMap(Codec.STRING, Codec.PASSTHROUGH).optionalFieldOf("components", Map.of())
                    .forGetter(ItemStack::kiln$componentsOut))
            .apply(i, ItemStack::kiln$decoded));
    public static final Codec<ItemStack> CODEC = MAP_CODEC.codec();
    public static final Codec<ItemStack> OPTIONAL_CODEC = CODEC;
    /** Over the network, as the codec's JSON: Kiln's packets only need to round-trip, none goes to a real server. */
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ItemStack>
            OPTIONAL_STREAM_CODEC = net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8
                    .<ItemStack>map(json -> CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(json))
                            .result().orElse(EMPTY),
                            stack -> CODEC.encodeStart(JsonOps.INSTANCE, stack).result().map(Object::toString).orElse("{}"))
                    .cast();
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ItemStack>
            STREAM_CODEC = OPTIONAL_STREAM_CODEC;
    private static final String DAMAGE = "minecraft:damage";
    private static final String MAX_DAMAGE = "minecraft:max_damage";
    private static final String UNBREAKABLE = "minecraft:unbreakable";
    private final Item item;
    private int count;
    /** The components as the game saved them, by type ({@code minecraft:damage}); Kiln's tags ({@code kiln.Snbt}). */
    private Map<String, Object> components = Map.of();
    /** Components read or set by their type, and those a type without a codec keeps only here. */
    private Map<DataComponentType<?>, Object> typed = new IdentityHashMap<>();

    public ItemStack(ItemLike item) {
        this(item, 1);
    }

    public ItemStack(ItemLike item, int count) {
        this.item = item == null ? null : item.asItem();
        this.count = count;
    }

    public boolean isEmpty() {
        return this == EMPTY || item == null || item == Items.AIR || count <= 0;
    }

    public Item getItem() {
        return isEmpty() ? Items.AIR : item;
    }

    @Override
    public boolean is(Item item) {
        return getItem() == item;
    }

    @Override
    public Holder<Item> typeHolder() {
        Item item = getItem();
        return BuiltInRegistries.ITEM.get(BuiltInRegistries.ITEM.getKey(item))
                .<Holder<Item>>map(h -> h)
                .orElseGet(() -> Holder.direct(item));
    }

    public int getCount() {
        return isEmpty() ? 0 : count;
    }

    public void setCount(int count) {
        if (this != EMPTY) {
            this.count = count;
        }
    }

    public void grow(int n) {
        setCount(count + n);
    }

    public void shrink(int n) {
        grow(-n);
    }

    /** Kiln's own: the stack whole, as vanilla saves an item (SNBT: id, count, components); "" for nothing. */
    public String kiln$snbt() {
        if (isEmpty()) {
            return "";
        }
        Map<String, Object> tag = new TreeMap<>();
        tag.put("id", getItem().kiln$id().toString());
        tag.put("count", getCount());
        if (!components.isEmpty()) {
            tag.put("components", new TreeMap<>(components));
        }
        return kiln.Snbt.write(tag);
    }

    /** Kiln's own: a stack as the game saved it (SNBT); EMPTY for "" or air. */
    @SuppressWarnings("unchecked")
    public static ItemStack kiln$fromSnbt(String text) {
        if (text == null || text.isEmpty()) {
            return EMPTY;
        }
        Map<String, Object> tag = kiln.Snbt.compound(text);
        if (!(tag.get("id") instanceof String id)) {
            return EMPTY;
        }
        int count = tag.get("count") instanceof Number n ? n.intValue() : 1;
        Item item = Item.kiln$byId(id);
        if (item == Items.AIR || count <= 0) {
            return EMPTY;
        }
        ItemStack stack = new ItemStack(item, count);
        if (tag.get("components") instanceof Map<?, ?> saved) {
            stack.components = new TreeMap<>((Map<String, Object>) saved);
        }
        return stack;
    }

    /**
     * Its name, as the game shows it: a custom name when it has one, else its item's (a game block's item has the
     * block's name, as vanilla's block items do).
     */
    public net.minecraft.network.chat.Component getHoverName() {
        if (components.get("minecraft:custom_name") instanceof String name) {
            return net.minecraft.network.chat.Component.literal(name);
        }
        String key = getItem().getDescriptionId();
        if (!kiln.Language.has(key) && key.startsWith("item.")) {
            String block = "block." + key.substring("item.".length());
            if (kiln.Language.has(block)) {
                key = block;
            }
        }
        return net.minecraft.network.chat.Component.translatable(key);
    }

    /** How much it is worn: its {@code minecraft:damage}, 0 when it has none. */
    public int getDamageValue() {
        return components.get(DAMAGE) instanceof Number n ? Math.max(0, Math.min(n.intValue(), getMaxDamage())) : 0;
    }

    public void setDamageValue(int damage) {
        if (this == EMPTY) {
            return;
        }
        Map<String, Object> next = new TreeMap<>(components);
        if (damage <= 0) {
            next.remove(DAMAGE);
        } else {
            next.put(DAMAGE, Math.min(damage, getMaxDamage()));
        }
        components = next;
    }

    /** Its durability: its own {@code minecraft:max_damage}, else its item's. */
    public int getMaxDamage() {
        if (isEmpty()) {
            return 0;
        }
        return components.get(MAX_DAMAGE) instanceof Number n ? n.intValue() : item.kiln$maxDamage();
    }

    public boolean isDamageableItem() {
        return getMaxDamage() > 0 && !components.containsKey(UNBREAKABLE);
    }

    public boolean isDamaged() {
        return isDamageableItem() && getDamageValue() > 0;
    }

    /** Vanilla's durability bar: shown for a damaged item. */
    public boolean isBarVisible() {
        return isDamaged();
    }

    /** The bar's width out of 13 pixels: what is left of the item's durability. */
    public int getBarWidth() {
        int max = getMaxDamage();
        return max <= 0 ? 13 : Math.round(13.0F - getDamageValue() * 13.0F / max);
    }

    /** The bar's colour: green when new, through yellow to red when nearly broken. */
    public int getBarColor() {
        int max = getMaxDamage();
        float left = max <= 0 ? 1.0F : Math.max(0.0F, (max - (float) getDamageValue()) / max);
        return net.minecraft.util.Mth.hsvToRgb(left / 3.0F, 1.0F, 1.0F);
    }

    public int getMaxStackSize() {
        return getItem().getDefaultMaxStackSize();
    }

    public ItemStack copy() {
        return isEmpty() ? EMPTY : withComponents(new ItemStack(item, count));
    }

    public ItemStack copyWithCount(int count) {
        return isEmpty() ? EMPTY : withComponents(new ItemStack(item, count));
    }

    public ItemStack split(int n) {
        int taken = Math.min(n, getCount());
        ItemStack part = isEmpty() ? EMPTY : withComponents(new ItemStack(item, taken));
        shrink(taken);
        return part;
    }

    public static boolean isSameItem(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem();
    }

    public static boolean isSameItemSameComponents(ItemStack a, ItemStack b) {
        return isSameItem(a, b) && a.components.equals(b.components);
    }

    public static boolean matches(ItemStack a, ItemStack b) {
        return a == b || a.getCount() == b.getCount() && isSameItemSameComponents(a, b);
    }

    private ItemStack withComponents(ItemStack copy) {
        copy.components = components;
        copy.typed = new IdentityHashMap<>(typed);
        return copy;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(DataComponentType<? extends T> type) {
        if (typed.containsKey(type)) {
            return (T) typed.get(type);
        }
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        Object saved = id == null ? null : components.get(id.toString());
        if (saved == null) {
            // The item's own value, what the game knows of it (a game item's fuel), when Kiln can ask.
            return isEmpty() ? null : kiln.Recipes.component(item, type);
        }
        if (type.codec() == null) {
            return null;
        }
        T value = (T) type.codec().parse(JsonOps.INSTANCE, TagValues.json(saved)).result().orElse(null);
        typed.put(type, value);
        return value;
    }

    @Override
    public boolean has(DataComponentType<?> type) {
        return get(type) != null;
    }

    /** Sets a component, saved through its type's codec; returns what it was. */
    public <T> T set(DataComponentType<T> type, T value) {
        if (value == null) {
            return remove(type);
        }
        T before = get(type);
        if (this == EMPTY) {
            return before;
        }
        typed.put(type, value);
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        if (id != null && type.codec() != null) {
            Map<String, Object> next = new TreeMap<>(components);
            type.codec().encodeStart(JsonOps.INSTANCE, value).result()
                    .ifPresent(json -> next.put(id.toString(), TagValues.tag(json)));
            components = next;
        }
        return before;
    }

    public <T> T remove(DataComponentType<? extends T> type) {
        T before = get(type);
        if (this == EMPTY) {
            return before;
        }
        typed.remove(type);
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        if (id != null && components.containsKey(id.toString())) {
            Map<String, Object> next = new TreeMap<>(components);
            next.remove(id.toString());
            components = next;
        }
        return before;
    }

    /** The components a mod can read by type: those set here, and the saved ones of registered types. */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public DataComponentMap getComponents() {
        DataComponentMap.Builder builder = DataComponentMap.builder();
        for (String id : components.keySet()) {
            DataComponentType type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(id));
            if (type != null && get(type) != null) {
                builder.set(type, get(type));
            }
        }
        typed.forEach((type, value) -> builder.set((DataComponentType) type, value));
        return builder.build();
    }

    private Map<String, Dynamic<?>> kiln$componentsOut() {
        Map<String, Dynamic<?>> out = new TreeMap<>();
        components.forEach((k, v) -> out.put(k, new Dynamic<>(JsonOps.INSTANCE, TagValues.json(v))));
        return out;
    }

    private static ItemStack kiln$decoded(String id, int count, Map<String, Dynamic<?>> components) {
        Item item = Item.kiln$byId(id);
        if (item == Items.AIR || count <= 0) {
            return EMPTY;
        }
        ItemStack stack = new ItemStack(item, count);
        if (!components.isEmpty()) {
            Map<String, Object> kept = new TreeMap<>();
            components.forEach((k, v) -> kept.put(k, TagValues.tag(v.convert(JsonOps.INSTANCE).getValue())));
            stack.components = kept;
        }
        return stack;
    }

    @Override
    public String toString() {
        return getCount() + " " + getItem();
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.core.component.DataComponentPatch getComponentsPatch() { kiln.Missing.called("net.minecraft.world.item.ItemStack.getComponentsPatch"); return null; }
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.useOn"); return null; }
    public void hurtAndBreak(int p0, net.minecraft.world.entity.LivingEntity p1, net.minecraft.world.entity.EquipmentSlot p2) { kiln.Missing.called("net.minecraft.world.item.ItemStack.hurtAndBreak"); }
    public void applyComponents(net.minecraft.core.component.DataComponentMap p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.applyComponents"); }
    public void enchant(net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> p0, int p1) { kiln.Missing.called("net.minecraft.world.item.ItemStack.enchant"); }
    public boolean isEnchanted() { kiln.Missing.called("net.minecraft.world.item.ItemStack.isEnchanted"); return false; }
    // End of what tools/kiln-stubs.py generated.
}
