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

    public ItemStack(Holder<Item> item) {
        this(item.value(), 1);
    }

    /** A stack of {@code item} with {@code patch}'s components (a removal changes nothing). */
    public ItemStack(Holder<Item> item, int count, net.minecraft.core.component.DataComponentPatch patch) {
        this(item.value(), count);
        kiln$apply(patch);
    }

    /** Kiln's own: {@code patch}'s saved components set on it (a removal, "!id", changes nothing). */
    void kiln$apply(net.minecraft.core.component.DataComponentPatch patch) {
        if (patch.isEmpty() || this == EMPTY) {
            return;
        }
        Map<String, Object> next = new TreeMap<>(components);
        patch.kiln$saved().forEach((k, v) -> {
            if (!k.startsWith("!")) {
                next.put(k, TagValues.tag(v));
            }
        });
        components = next;
        typed.clear();
    }

    /** The changes to its item's components: its saved ones, by id (Kiln's items have none of their own). */
    public net.minecraft.core.component.DataComponentPatch getComponentsPatch() {
        if (components.isEmpty()) {
            return net.minecraft.core.component.DataComponentPatch.EMPTY;
        }
        Map<String, com.google.gson.JsonElement> saved = new TreeMap<>();
        components.forEach((k, v) -> saved.put(k, TagValues.json(v)));
        return net.minecraft.core.component.DataComponentPatch.kiln$of(saved);
    }

    /** Adds {@code enchantment} at {@code level} (or raises it to that), as an enchanting table would. */
    public void enchant(Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment, int level) {
        net.minecraft.world.item.enchantment.ItemEnchantments.Mutable changed =
                new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(getOrDefault(
                        net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                        net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY));
        changed.upgrade(enchantment, level);
        set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, changed.toImmutable());
    }

    public boolean isEnchanted() {
        net.minecraft.world.item.enchantment.ItemEnchantments e =
                get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        return e != null && !e.isEmpty();
    }

    /** Whether more than one fits in a slot: an undamaged stack of an item that stacks. */
    public boolean isStackable() {
        return getMaxStackSize() > 1 && (!isDamageableItem() || !isDamaged());
    }

    /**
     * Whether an enchanting table takes it, as the game's: its item is enchantable ({@code minecraft:enchantable},
     * which Kiln knows of the game's items) and it has no enchantments yet.
     */
    public boolean isEnchantable() {
        return has(net.minecraft.core.component.DataComponents.ENCHANTABLE) && !isEnchanted();
    }

    /**
     * Fabric's: whether {@code enchantment} may go on it, at an enchanting table ({@code PRIMARY}: the enchantment's
     * primary items) or elsewhere ({@code ACCEPTABLE}: its supported items).
     */
    public boolean canBeEnchantedWith(Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment,
            net.fabricmc.fabric.api.item.v1.EnchantingContext context) {
        return context == net.fabricmc.fabric.api.item.v1.EnchantingContext.PRIMARY
                ? enchantment.value().isPrimaryItem(this) : enchantment.value().canEnchant(this);
    }

    /** The namespace of the mod that made it: its item's. */
    public String getCreatorNamespace() {
        return getItem().kiln$id().getNamespace();
    }

    /**
     * The picture its tooltip shows under its lines, as the game's: its item's ({@link Item#getTooltipImage}: a
     * bundle's contents, D-177), none when its {@code tooltip_display} hides the tooltip.
     */
    public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage() {
        if (isEmpty() || kiln.ItemTooltips.hidden(this)) {
            return java.util.Optional.empty();
        }
        return getItem().getTooltipImage(this);
    }

    /** Whether the stack is valid, as the game checks a stack it reads: Kiln takes it as it is. */
    public static com.mojang.serialization.DataResult<ItemStack> validateStrict(ItemStack stack) {
        return com.mojang.serialization.DataResult.success(stack);
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
                .orElseGet(item::builtInRegistryHolder);
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

    /**
     * Its tooltip's lines, as the game shows them: its name, what its item adds ({@link Item#appendHoverText}), and
     * Fabric's {@code ItemTooltipCallback} at the end (the Fabric API's mixin calls it there). Horizon shows a game
     * item's own lines; Kiln adds the ones past the name to them (Kiln's Mixin bridge's {@code tooltip} row, D-145).
     */
    public java.util.List<net.minecraft.network.chat.Component> getTooltipLines(Item.TooltipContext context,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.item.TooltipFlag flag) {
        java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        net.minecraft.network.chat.MutableComponent name =
                net.minecraft.network.chat.Component.empty().append(getHoverName()).withStyle(getRarity().color());
        if (has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            name.withStyle(net.minecraft.ChatFormatting.ITALIC);
        }
        lines.add(name);
        // Horizon writes a game item's component lines itself in its own tooltip, where the bridge's call adds only
        // the mods' lines to them (D-145); every other caller (a recipe viewer) gets the game's lines whole (D-164).
        boolean forGame = kiln.Bridge.calling("getTooltipLines");
        if (!isEmpty() && !forGame && kiln.ItemTooltips.hidden(this)) {
            // Vanilla's: a stack whose tooltip_display hides its tooltip has none.
            return kiln.Bridge.body(new java.util.ArrayList<>(), context, player, flag);
        }
        if (!isEmpty()) {
            getItem().appendHoverText(this, context, null, lines::add, flag);
        }
        if (!isEmpty() && !forGame) {
            kiln.ItemTooltips.details(this, context, player, flag, lines::add);
        }
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.invoker().getTooltip(this, context, flag,
                lines);
        return kiln.Bridge.body(lines, context, player, flag);
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
     * Its name, as the game shows it: a custom name when it has one, else its {@code item_name}, else its item's (a
     * game block's item has the block's name, as vanilla's block items do).
     */
    public net.minecraft.network.chat.Component getHoverName() {
        net.minecraft.network.chat.Component custom = get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (custom != null) {
            return custom;
        }
        // A written book: its title, as the game's getCustomName (not italic: it isn't a custom name).
        String title = isEmpty() ? null : kiln.ItemTooltips.bookTitle(this);
        if (title != null) {
            return net.minecraft.network.chat.Component.literal(title);
        }
        // A potion, a splash or lingering one, a tipped arrow: named by its potion, as the game's PotionItem
        // ("Potion of Swiftness": item.minecraft.potion.effect.swiftness).
        if (!isEmpty() && components.containsKey("minecraft:potion_contents")
                && kiln.ItemTooltips.POTION_ITEMS.contains(getItem().kiln$id().toString())) {
            net.minecraft.world.item.alchemy.PotionContents contents =
                    get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            if (contents != null) {
                return contents.getName(getItem().getDescriptionId() + ".effect.");
            }
        }
        net.minecraft.network.chat.Component named = get(net.minecraft.core.component.DataComponents.ITEM_NAME);
        if (named != null) {
            return named;
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

    /**
     * Its name as chat shows a stack, as vanilla's: in square brackets, italic when it has a custom name, in its
     * rarity's colour, and the stack itself when it is hovered.
     */
    public net.minecraft.network.chat.Component getDisplayName() {
        net.minecraft.network.chat.MutableComponent name =
                net.minecraft.network.chat.Component.empty().append(getHoverName());
        if (has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            name.withStyle(net.minecraft.ChatFormatting.ITALIC);
        }
        net.minecraft.network.chat.MutableComponent shown =
                net.minecraft.network.chat.Component.translatable("chat.square_brackets", name);
        if (!isEmpty()) {
            shown.withStyle(getRarity().color()).withStyle(style -> style.withHoverEvent(
                    new net.minecraft.network.chat.HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(this))));
        }
        return shown;
    }

    /**
     * How rare it is, its name's colour, as the game's: its {@code minecraft:rarity} (its own, else its item's: a game
     * item's from {@code items.json}, a mod's from its properties), one step rarer when it is enchanted (common and
     * uncommon to rare, rare to epic).
     */
    public Rarity getRarity() {
        if (isEmpty()) {
            return Rarity.COMMON;
        }
        Rarity rarity = kiln.ItemTooltips.rarity(components.get("minecraft:rarity"));
        if (rarity == null) {
            rarity = kiln.ItemTooltips.gameRarity(getItem());
        }
        if (rarity == null) {
            rarity = getItem().kiln$rarity();
        }
        if (!isEnchanted()) {
            return rarity;
        }
        return switch (rarity) {
            case COMMON, UNCOMMON -> Rarity.RARE;
            case RARE -> Rarity.EPIC;
            default -> rarity;
        };
    }

    /** Kiln's own: its component {@code id} ({@code minecraft:trim}) as it is saved (Kiln's tags); null without it. */
    public Object kiln$saved(String id) {
        return components.get(id);
    }

    /**
     * Kiln's own: its component {@code id} set to {@code saved}, as saved (Kiln's tags), in no other form; null takes it
     * out (the anvil's result keeps Horizon's own form of a name, D-202).
     */
    public void kiln$setSaved(String id, Object saved) {
        if (this == EMPTY) {
            return;
        }
        Map<String, Object> next = new TreeMap<>(components);
        if (saved == null) {
            next.remove(id);
        } else {
            next.put(id, saved);
        }
        components = next;
        typed.clear();
    }

    /** Kiln's own: the ids of the components it has of its own (saved), in order. */
    public java.util.Set<String> kiln$savedIds() {
        return java.util.Collections.unmodifiableSet(components.keySet());
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

    /** How the arm swings when it is used: the game's usual swing (Horizon swings the arm itself). */
    public net.minecraft.world.item.component.SwingAnimation getInteractAnimation() {
        return net.minecraft.world.item.component.SwingAnimation.DEFAULT;
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
            // Saved as the game saves it, through its tags: a UUID's four ints stay an int array (a head's profile).
            Map<String, Object> next = new TreeMap<>(components);
            Object saved = type.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, value).result()
                    .map(net.minecraft.nbt.Tag::kiln$value)
                    .orElseGet(() -> type.codec().encodeStart(JsonOps.INSTANCE, value).result().map(TagValues::tag)
                            .orElse(null));
            if (saved != null) {
                next.put(id.toString(), saved);
            }
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
        // A patched map, as the game's: REI hashes stacks by it (D-215).
        return new net.minecraft.core.component.PatchedDataComponentMap(builder.build());
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
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.useOn"); return null; }
    public void hurtAndBreak(int p0, net.minecraft.world.entity.LivingEntity p1, net.minecraft.world.entity.EquipmentSlot p2) { kiln.Missing.called("net.minecraft.world.item.ItemStack.hurtAndBreak"); }
    public void applyComponents(net.minecraft.core.component.DataComponentMap p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.applyComponents"); }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, net.minecraft.world.item.ItemStack> OPTIONAL_UNTRUSTED_STREAM_CODEC = kiln.Constants.streamCodec("net.minecraft.world.item.ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC");
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, java.util.List<net.minecraft.world.item.ItemStack>> OPTIONAL_LIST_STREAM_CODEC = kiln.Constants.streamCodec("net.minecraft.world.item.ItemStack.OPTIONAL_LIST_STREAM_CODEC");
    public net.minecraft.core.component.DataComponentMap getPrototype() { kiln.Missing.called("net.minecraft.world.item.ItemStack.getPrototype"); return null; }
    public boolean hasNonDefault(net.minecraft.core.component.DataComponentType<?> p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.hasNonDefault"); return false; }
    public boolean isItemEnabled(net.minecraft.world.flag.FeatureFlagSet p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.isItemEnabled"); return false; }
    public net.minecraft.world.item.ItemStack copyAndClear() { kiln.Missing.called("net.minecraft.world.item.ItemStack.copyAndClear"); return this; }
    public float getDestroySpeed(net.minecraft.world.level.block.state.BlockState p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.getDestroySpeed"); return 0.0F; }
    public net.minecraft.world.InteractionResult use(net.minecraft.world.level.Level p0, net.minecraft.world.entity.player.Player p1, net.minecraft.world.InteractionHand p2) { kiln.Missing.called("net.minecraft.world.item.ItemStack.use"); return null; }
    public net.minecraft.world.item.ItemStack finishUsingItem(net.minecraft.world.level.Level p0, net.minecraft.world.entity.LivingEntity p1) { kiln.Missing.called("net.minecraft.world.item.ItemStack.finishUsingItem"); return this; }
    public boolean nextDamageWillBreak() { kiln.Missing.called("net.minecraft.world.item.ItemStack.nextDamageWillBreak"); return false; }
    public void hurtAndBreak(int p0, net.minecraft.server.level.ServerLevel p1, net.minecraft.server.level.ServerPlayer p2, java.util.function.Consumer<net.minecraft.world.item.ItemStack> p3) { kiln.Missing.called("net.minecraft.world.item.ItemStack.hurtAndBreak"); }
    public void hurtAndBreak(int p0, net.minecraft.world.entity.LivingEntity p1, net.minecraft.world.InteractionHand p2) { kiln.Missing.called("net.minecraft.world.item.ItemStack.hurtAndBreak"); }
    public boolean overrideStackedOnOther(net.minecraft.world.inventory.Slot p0, net.minecraft.world.inventory.ClickAction p1, net.minecraft.world.entity.player.Player p2) { kiln.Missing.called("net.minecraft.world.item.ItemStack.overrideStackedOnOther"); return false; }
    public boolean overrideOtherStackedOnMe(net.minecraft.world.item.ItemStack p0, net.minecraft.world.inventory.Slot p1, net.minecraft.world.inventory.ClickAction p2, net.minecraft.world.entity.player.Player p3, net.minecraft.world.entity.SlotAccess p4) { kiln.Missing.called("net.minecraft.world.item.ItemStack.overrideOtherStackedOnMe"); return false; }
    public void mineBlock(net.minecraft.world.level.Level p0, net.minecraft.world.level.block.state.BlockState p1, net.minecraft.core.BlockPos p2, net.minecraft.world.entity.player.Player p3) { kiln.Missing.called("net.minecraft.world.item.ItemStack.mineBlock"); }
    public boolean isCorrectToolForDrops(net.minecraft.world.level.block.state.BlockState p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.isCorrectToolForDrops"); return false; }
    public net.minecraft.world.item.ItemStack transmuteCopy(net.minecraft.world.level.ItemLike p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.transmuteCopy"); return this; }
    public net.minecraft.world.item.ItemStack transmuteCopy(net.minecraft.world.level.ItemLike p0, int p1) { kiln.Missing.called("net.minecraft.world.item.ItemStack.transmuteCopy"); return this; }
    public static boolean matchesIgnoringComponents(net.minecraft.world.item.ItemStack p0, net.minecraft.world.item.ItemStack p1, java.util.function.Predicate<net.minecraft.core.component.DataComponentType<?>> p2) { kiln.Missing.called("net.minecraft.world.item.ItemStack.matchesIgnoringComponents"); return false; }
    public static int hashItemAndComponents(net.minecraft.world.item.ItemStack p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.hashItemAndComponents"); return 0; }
    public void onCraftedBy(net.minecraft.world.entity.player.Player p0, int p1) { kiln.Missing.called("net.minecraft.world.item.ItemStack.onCraftedBy"); }
    public int getUseDuration(net.minecraft.world.entity.LivingEntity p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.getUseDuration"); return 0; }
    public net.minecraft.world.item.ItemUseAnimation getUseAnimation() { kiln.Missing.called("net.minecraft.world.item.ItemStack.getUseAnimation"); return null; }
    public boolean useOnRelease() { kiln.Missing.called("net.minecraft.world.item.ItemStack.useOnRelease"); return false; }
    public <T> T update(net.minecraft.core.component.DataComponentType<T> p0, T p1, java.util.function.UnaryOperator<T> p2) { kiln.Missing.called("net.minecraft.world.item.ItemStack.update"); return null; }
    public void applyComponentsAndValidate(net.minecraft.core.component.DataComponentPatch p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.applyComponentsAndValidate"); }
    public void applyComponents(net.minecraft.core.component.DataComponentPatch p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.applyComponents"); }
    public net.minecraft.network.chat.Component getCustomName() { kiln.Missing.called("net.minecraft.world.item.ItemStack.getCustomName"); return null; }
    public net.minecraft.network.chat.Component getItemName() { kiln.Missing.called("net.minecraft.world.item.ItemStack.getItemName"); return null; }
    public net.minecraft.network.chat.Component getStyledHoverName() { kiln.Missing.called("net.minecraft.world.item.ItemStack.getStyledHoverName"); return null; }
    public <T> void addToTooltip(net.minecraft.core.component.DataComponentType<T> p0, net.minecraft.world.item.component.TooltipProvider.Getter<T> p1, net.minecraft.world.item.Item.TooltipContext p2, net.minecraft.world.item.component.TooltipDisplay p3, java.util.function.Consumer<net.minecraft.network.chat.Component> p4, net.minecraft.world.item.TooltipFlag p5) { kiln.Missing.called("net.minecraft.world.item.ItemStack.addToTooltip"); }
    public <T extends net.minecraft.world.item.component.TooltipProvider> void addToTooltip(net.minecraft.core.component.DataComponentType<T> p0, net.minecraft.world.item.Item.TooltipContext p1, net.minecraft.world.item.component.TooltipDisplay p2, java.util.function.Consumer<net.minecraft.network.chat.Component> p3, net.minecraft.world.item.TooltipFlag p4) { kiln.Missing.called("net.minecraft.world.item.ItemStack.addToTooltip"); }
    public void addDetailsToTooltip(net.minecraft.world.item.Item.TooltipContext p0, net.minecraft.world.item.component.TooltipDisplay p1, net.minecraft.world.entity.player.Player p2, net.minecraft.world.item.TooltipFlag p3, java.util.function.Consumer<net.minecraft.network.chat.Component> p4) { kiln.Missing.called("net.minecraft.world.item.ItemStack.addDetailsToTooltip"); }
    public boolean hasFoil() { kiln.Missing.called("net.minecraft.world.item.ItemStack.hasFoil"); return false; }
    public net.minecraft.world.item.enchantment.ItemEnchantments getEnchantments() { kiln.Missing.called("net.minecraft.world.item.ItemStack.getEnchantments"); return null; }
    public void forEachModifier(net.minecraft.world.entity.EquipmentSlotGroup p0, org.apache.commons.lang3.function.TriConsumer<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, net.minecraft.world.entity.ai.attributes.AttributeModifier, net.minecraft.world.item.component.ItemAttributeModifiers.Display> p1) { kiln.Missing.called("net.minecraft.world.item.ItemStack.forEachModifier"); }
    public void forEachModifier(net.minecraft.world.entity.EquipmentSlot p0, java.util.function.BiConsumer<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, net.minecraft.world.entity.ai.attributes.AttributeModifier> p1) { kiln.Missing.called("net.minecraft.world.item.ItemStack.forEachModifier"); }
    public net.minecraft.world.item.component.SwingAnimation getAttackAnimation() { kiln.Missing.called("net.minecraft.world.item.ItemStack.getAttackAnimation"); return new net.minecraft.world.item.component.SwingAnimation((net.minecraft.world.item.SwingAnimationType) null, (int) 0); }
    public boolean canPlaceOnBlockInAdventureMode(net.minecraft.world.level.block.state.pattern.BlockInWorld p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.canPlaceOnBlockInAdventureMode"); return false; }
    public int getPopTime() { kiln.Missing.called("net.minecraft.world.item.ItemStack.getPopTime"); return 0; }
    public void setPopTime(int p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.setPopTime"); }
    public int count() { kiln.Missing.called("net.minecraft.world.item.ItemStack.count"); return 0; }
    public void limitSize(int p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.limitSize"); }
    public void consume(int p0, net.minecraft.world.entity.LivingEntity p1) { kiln.Missing.called("net.minecraft.world.item.ItemStack.consume"); }
    public boolean isValidRepairItem(net.minecraft.world.item.ItemStack p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.isValidRepairItem"); return false; }
    public boolean canDestroyBlock(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.Level p1, net.minecraft.core.BlockPos p2, net.minecraft.world.entity.player.Player p3) { kiln.Missing.called("net.minecraft.world.item.ItemStack.canDestroyBlock"); return false; }
    public net.minecraft.world.damagesource.DamageSource getDamageSource(net.minecraft.world.entity.LivingEntity p0) { kiln.Missing.called("net.minecraft.world.item.ItemStack.getDamageSource"); return null; }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
}
