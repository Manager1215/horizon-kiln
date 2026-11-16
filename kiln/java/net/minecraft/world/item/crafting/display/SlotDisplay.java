package net.minecraft.world.item.crafting.display;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Kiln's stand-in for {@code SlotDisplay}: what a recipe's slot shows (an item, a stack, a tag's items, any fuel...),
 * resolved into stacks or a recipe viewer's ingredients by a {@link DisplayContentsFactory}, as vanilla's. A tag's
 * items and the fuels are the game's, from its resources ({@code kiln.GameItems}); the stream codecs are names only
 * (Kiln sends no displays over the network).
 */
public interface SlotDisplay {
    Codec<SlotDisplay> CODEC = Codec.lazyInitialized(() -> Codec.STRING.dispatch(
            display -> Kiln.idOf(display.type()),
            id -> {
                Kiln.vanillaTypes();
                Type<?> type = Kiln.TYPES.get(id.contains(":") ? id : "minecraft:" + id);
                if (type == null) {
                    throw new IllegalArgumentException("no slot display type " + id);
                }
                return type.codec();
            }));

    StreamCodec<RegistryFriendlyByteBuf, SlotDisplay> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.display.SlotDisplay.STREAM_CODEC");

    <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory);

    Type<? extends SlotDisplay> type();

    default boolean isEnabled(FeatureFlagSet flags) {
        return true;
    }

    default List<ItemStack> resolveForStacks(ContextMap context) {
        return resolve(context, ItemStackContentsFactory.INSTANCE).toList();
    }

    default ItemStack resolveForFirstStack(ContextMap context) {
        return resolve(context, ItemStackContentsFactory.INSTANCE).findFirst().orElse(ItemStack.EMPTY);
    }

    record Type<T extends SlotDisplay>(MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {}

    /** Kiln's own: what the codecs of the displays below share. */
    final class Kiln {
        /** The types by id ({@code "minecraft:item"}...), each as its class loads. */
        static final Map<String, Type<?>> TYPES = new ConcurrentHashMap<>();
        static final Codec<Item> ITEM = Codec.STRING.comapFlatMap(kiln.Recipes::item, item -> item.kiln$id().toString());
        static final Codec<Holder<Item>> ITEM_HOLDER = ITEM.xmap(Item::builtInRegistryHolder, Holder::value);

        private Kiln() {}

        static <T extends SlotDisplay> Type<T> type(String id, MapCodec<T> codec) {
            Type<T> type = new Type<>(codec, kiln.Constants.streamCodec("SlotDisplay " + id));
            TYPES.put("minecraft:" + id, type);
            return type;
        }

        static String idOf(Type<?> type) {
            vanillaTypes();
            for (Map.Entry<String, Type<?>> entry : TYPES.entrySet()) {
                if (entry.getValue() == type) {
                    return entry.getKey();
                }
            }
            throw new IllegalArgumentException("a slot display type Kiln doesn't know: " + type);
        }

        /** Loads the displays below, so that their types are known by id. */
        static void vanillaTypes() {
            List.of(Empty.TYPE, AnyFuel.TYPE, Composite.TYPE, ItemSlotDisplay.TYPE, ItemStackSlotDisplay.TYPE,
                    TagSlotDisplay.TYPE, WithRemainder.TYPE, WithAnyPotion.TYPE);
        }

        static HolderSet.Named<Item> tag(TagKey<Item> key) {
            return new HolderSet.Named<>(key, () -> kiln.GameItems.tag(key.location()).stream()
                    .map(Item::builtInRegistryHolder).<Holder<Item>>map(h -> h).toList());
        }
    }

    class ItemStackContentsFactory implements DisplayContentsFactory.ForStacks<ItemStack> {
        public static final ItemStackContentsFactory INSTANCE = new ItemStackContentsFactory();

        @Override
        public ItemStack forStack(ItemStack stack) {
            return stack;
        }
    }

    class Empty implements SlotDisplay {
        public static final Empty INSTANCE = new Empty();
        public static final MapCodec<Empty> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final Type<Empty> TYPE = Kiln.type("empty", MAP_CODEC);

        protected Empty() {}

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            return Stream.empty();
        }

        @Override
        public Type<Empty> type() {
            return TYPE;
        }

        @Override
        public String toString() {
            return "<empty>";
        }
    }

    /** Any of the game's fuels. */
    class AnyFuel implements SlotDisplay {
        public static final AnyFuel INSTANCE = new AnyFuel();
        public static final MapCodec<AnyFuel> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final Type<AnyFuel> TYPE = Kiln.type("any_fuel", MAP_CODEC);

        protected AnyFuel() {}

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            if (factory instanceof DisplayContentsFactory.ForStacks<T> stacks) {
                return kiln.GameItems.fuels().stream().map(stacks::forStack);
            }
            return Stream.empty();
        }

        @Override
        public Type<AnyFuel> type() {
            return TYPE;
        }

        @Override
        public String toString() {
            return "<any fuel>";
        }
    }

    record Composite(List<SlotDisplay> contents) implements SlotDisplay {
        public static final MapCodec<Composite> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                SlotDisplay.CODEC.listOf().fieldOf("contents").forGetter(Composite::contents)).apply(i, Composite::new));
        public static final Type<Composite> TYPE = Kiln.type("composite", MAP_CODEC);

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            return contents.stream().flatMap(display -> display.resolve(context, factory));
        }

        @Override
        public boolean isEnabled(FeatureFlagSet flags) {
            return contents.stream().allMatch(display -> display.isEnabled(flags));
        }

        @Override
        public Type<Composite> type() {
            return TYPE;
        }
    }

    record ItemSlotDisplay(Holder<Item> item) implements SlotDisplay {
        public static final MapCodec<ItemSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Kiln.ITEM_HOLDER.fieldOf("item").forGetter(ItemSlotDisplay::item)).apply(i, ItemSlotDisplay::new));
        public static final Type<ItemSlotDisplay> TYPE = Kiln.type("item", MAP_CODEC);

        public ItemSlotDisplay(Item item) {
            this(item.builtInRegistryHolder());
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            return factory instanceof DisplayContentsFactory.ForStacks<T> stacks
                    ? Stream.of(stacks.forStack(item)) : Stream.empty();
        }

        @Override
        public Type<ItemSlotDisplay> type() {
            return TYPE;
        }
    }

    record ItemStackSlotDisplay(ItemStackTemplate stack) implements SlotDisplay {
        public static final MapCodec<ItemStackSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ItemStackTemplate.CODEC.fieldOf("item").forGetter(ItemStackSlotDisplay::stack))
                .apply(i, ItemStackSlotDisplay::new));
        public static final Type<ItemStackSlotDisplay> TYPE = Kiln.type("item_stack", MAP_CODEC);

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            return factory instanceof DisplayContentsFactory.ForStacks<T> stacks
                    ? Stream.of(stacks.forStack(stack)) : Stream.empty();
        }

        @Override
        public Type<ItemStackSlotDisplay> type() {
            return TYPE;
        }
    }

    /** An item tag's items; a Kiln tag's are the game's own, from its resources. */
    record TagSlotDisplay(HolderSet<Item> tag) implements SlotDisplay {
        public static final MapCodec<TagSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("tag").forGetter(TagSlotDisplay::kiln$location))
                .apply(i, id -> new TagSlotDisplay(TagKey.create(Registries.ITEM, id))));
        public static final Type<TagSlotDisplay> TYPE = Kiln.type("tag", MAP_CODEC);

        public TagSlotDisplay(TagKey<Item> key) {
            this(Kiln.tag(key));
        }

        /** Kiln's own: the tag's id, or none for listed items. */
        public Identifier kiln$location() {
            return tag.unwrapKey().map(TagKey::location).orElse(null);
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            return factory instanceof DisplayContentsFactory.ForStacks<T> stacks
                    ? tag.stream().map(stacks::forStack) : Stream.empty();
        }

        @Override
        public Type<TagSlotDisplay> type() {
            return TYPE;
        }
    }

    record WithRemainder(SlotDisplay input, SlotDisplay remainder) implements SlotDisplay {
        public static final MapCodec<WithRemainder> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                SlotDisplay.CODEC.fieldOf("input").forGetter(WithRemainder::input),
                SlotDisplay.CODEC.fieldOf("remainder").forGetter(WithRemainder::remainder)).apply(i, WithRemainder::new));
        public static final Type<WithRemainder> TYPE = Kiln.type("with_remainder", MAP_CODEC);

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            if (factory instanceof DisplayContentsFactory.ForRemainders<T> remainders) {
                List<T> left = remainder.resolve(context, factory).toList();
                return input.resolve(context, factory).map(entry -> remainders.addRemainder(entry, left));
            }
            return input.resolve(context, factory);
        }

        @Override
        public boolean isEnabled(FeatureFlagSet flags) {
            return input.isEnabled(flags) && remainder.isEnabled(flags);
        }

        @Override
        public Type<WithRemainder> type() {
            return TYPE;
        }
    }

    /** Its display's items, any potion each (a tipped arrow's slot); Kiln shows them as they are. */
    record WithAnyPotion(SlotDisplay display) implements SlotDisplay {
        public static final MapCodec<WithAnyPotion> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                SlotDisplay.CODEC.fieldOf("contents").forGetter(WithAnyPotion::display)).apply(i, WithAnyPotion::new));
        public static final Type<WithAnyPotion> TYPE = Kiln.type("with_any_potion", MAP_CODEC);

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            return display.resolve(context, factory);
        }

        @Override
        public Type<WithAnyPotion> type() {
            return TYPE;
        }
    }
}
