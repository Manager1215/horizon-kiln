package net.minecraft.world.item.crafting;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Kiln's stand-in for {@code Ingredient}: the items a recipe's slot takes, listed or as an item tag. A tag's items are
 * the game's (its data, the world's data packs, the content mods'): Kiln asks whether a stack is in it, during a
 * server call ({@code kiln.Tags}).
 */
public final class Ingredient implements Predicate<ItemStack> {
    /**
     * As a data pack writes one: an item's id, a list of them, or {@code #} and an item tag's. An item of a mod that
     * isn't there is an error, which leaves its recipe out, as in the game.
     */
    public static final Codec<Ingredient> CODEC = Codec.either(Codec.STRING, Codec.STRING.listOf()).comapFlatMap(
            either -> either.map(Ingredient::read, ids -> {
                List<Item> items = new java.util.ArrayList<>();
                for (String id : ids) {
                    DataResult<Item> item = item(id);
                    if (item.error().isPresent()) {
                        return DataResult.error(() -> item.error().get().message());
                    }
                    items.add(item.getOrThrow());
                }
                return items.isEmpty() ? DataResult.error(() -> "an ingredient of no items")
                        : DataResult.success(new Ingredient(items, null));
            }),
            ingredient -> ingredient.tag != null ? Either.left("#" + ingredient.tag)
                    : ingredient.items.size() == 1 ? Either.left(ingredient.items.get(0).kiln$id().toString())
                    : Either.right(ingredient.items.stream().map(i -> i.kiln$id().toString()).toList()));

    private final List<Item> items;
    /** An item tag's id, or null for listed items. */
    private final Identifier tag;

    private Ingredient(List<Item> items) {
        this(items, null);
    }

    private Ingredient(List<Item> items, Identifier tag) {
        this.items = List.copyOf(items);
        this.tag = tag;
        this.values = tag != null
                ? new net.minecraft.core.HolderSet.Named<>(
                        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tag),
                        () -> kiln.GameItems.tag(tag).stream().<Holder<Item>>map(Item::builtInRegistryHolder).toList())
                : net.minecraft.core.HolderSet.direct(this.items.stream().<Holder<Item>>map(Item::builtInRegistryHolder)
                        .toList());
    }

    /**
     * The game's field, which mods read by an access widener (Reborn Core's sized ingredients, D-165): the items, or
     * the tag's, as the game's tag holds them in its resources.
     */
    public net.minecraft.core.HolderSet<Item> values;

    private static DataResult<Ingredient> read(String text) {
        if (text.startsWith("#")) {
            Identifier tag = Identifier.tryParse(text.substring(1));
            return tag == null ? DataResult.error(() -> "not a tag: " + text)
                    : DataResult.success(new Ingredient(List.of(), tag));
        }
        return item(text).map(item -> new Ingredient(List.of(item), null));
    }

    private static DataResult<Item> item(String id) {
        return kiln.Recipes.item(id);
    }

    /**
     * Fabric's: the mod's own ingredient this is, or null for the game's kinds, which all of Kiln's are (D-165).
     */
    public net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient getCustomIngredient() {
        return null;
    }

    /** Fabric's: whether a stack's components matter to it; the game's kinds look at the item only. */
    public boolean requiresTesting() {
        return false;
    }

    /** Kiln's own: the item tag it takes, or null. */
    public Identifier kiln$tag() {
        return tag;
    }

    /** The items of {@code set}: a tag's by the tag, as the game's ingredient of a tag. */
    public static Ingredient of(net.minecraft.core.HolderSet<Item> set) {
        Optional<net.minecraft.tags.TagKey<Item>> tag = set.unwrapKey();
        return tag.isPresent() ? new Ingredient(List.of(), tag.get().location())
                : new Ingredient(set.stream().map(Holder::value).toList());
    }

    /** Whether {@code stack} is what an optional ingredient takes: an empty slot when there is none. */
    public static boolean testOptionalIngredient(Optional<Ingredient> ingredient, ItemStack stack) {
        return ingredient.map(i -> i.test(stack)).orElse(stack.isEmpty());
    }

    /** An optional ingredient as a slot: its display, or an empty one. */
    public static net.minecraft.world.item.crafting.display.SlotDisplay optionalIngredientToDisplay(
            Optional<Ingredient> ingredient) {
        return ingredient.map(Ingredient::display)
                .orElse(net.minecraft.world.item.crafting.display.SlotDisplay.Empty.INSTANCE);
    }

    public static Ingredient of(ItemLike item) {
        return new Ingredient(List.of(item.asItem()));
    }

    public static Ingredient of(ItemLike... items) {
        return new Ingredient(Arrays.stream(items).map(ItemLike::asItem).toList());
    }

    public static Ingredient of(Stream<? extends ItemLike> items) {
        return new Ingredient(items.map(ItemLike::asItem).map(Item.class::cast).toList());
    }

    /** Its items; a tag's are the game's own, from its resources ({@code kiln.GameItems}). */
    public Stream<Holder<Item>> items() {
        return (tag != null ? kiln.GameItems.tag(tag) : items).stream().<Holder<Item>>map(Item::builtInRegistryHolder);
    }

    public boolean isEmpty() {
        return items.isEmpty() && tag == null;
    }

    @Override
    public boolean test(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return tag != null ? kiln.Tags.itemIn(tag, stack.getItem()) : items.contains(stack.getItem());
    }

    public boolean acceptsItem(Holder<Item> item) {
        return tag != null ? kiln.Tags.itemIn(tag, item.value()) : items.contains(item.value());
    }

    public Optional<Holder<Item>> getSingleItem() {
        return items.size() == 1 ? Optional.of(items.get(0).builtInRegistryHolder()) : Optional.empty();
    }

    /** What a recipe viewer shows for it: its item, its items, or its tag's (the game's, from its resources). */
    public net.minecraft.world.item.crafting.display.SlotDisplay display() {
        if (tag != null) {
            return new net.minecraft.world.item.crafting.display.SlotDisplay.TagSlotDisplay(
                    net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tag));
        }
        if (items.size() == 1) {
            return new net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay(items.get(0));
        }
        return new net.minecraft.world.item.crafting.display.SlotDisplay.Composite(items.stream()
                .<net.minecraft.world.item.crafting.display.SlotDisplay>map(
                        net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay::new)
                .toList());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Ingredient other && other.items.equals(items) && java.util.Objects.equals(other.tag, tag);
    }

    @Override
    public int hashCode() {
        return items.hashCode() * 31 + java.util.Objects.hashCode(tag);
    }

    @Override
    public String toString() {
        return tag != null ? "Ingredient[#" + tag + "]" : "Ingredient" + items;
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, net.minecraft.world.item.crafting.Ingredient> CONTENTS_STREAM_CODEC = kiln.Constants.streamCodec("net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC");
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, java.util.Optional<net.minecraft.world.item.crafting.Ingredient>> OPTIONAL_CONTENTS_STREAM_CODEC = kiln.Constants.streamCodec("net.minecraft.world.item.crafting.Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC");
    public static net.minecraft.world.item.crafting.display.SlotDisplay displayForSingleItem(net.minecraft.core.Holder<net.minecraft.world.item.Item> p0) { kiln.Missing.called("net.minecraft.world.item.crafting.Ingredient.displayForSingleItem"); return kiln.Constants.nothing(net.minecraft.world.item.crafting.display.SlotDisplay.class); }
    // End of what tools/kiln-stubs.py generated.
}
