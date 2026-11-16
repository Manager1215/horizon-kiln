package net.minecraft.world.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code Item}. A mod's item is the mod's own subclass: Kiln calls its {@code use} when a
 * player uses Horizon's item of the same id (Horizon's import of the mod's data has it).
 */
public class Item implements ItemLike {
    /** The id of a weapon's attack damage and speed modifiers, which a tooltip adds the player's own to (D-177). */
    public static final Identifier BASE_ATTACK_DAMAGE_ID = Identifier.withDefaultNamespace("base_attack_damage");
    public static final Identifier BASE_ATTACK_SPEED_ID = Identifier.withDefaultNamespace("base_attack_speed");

    private final ResourceKey<Item> id;
    /** The components its properties gave it (a mod's armour's attributes, D-177), by type. */
    private final java.util.Map<net.minecraft.core.component.DataComponentType<?>, Object> components;
    private final int maxStackSize;
    private final Rarity rarity;
    /** Its durability: a mod's from its properties, a game item's as the game says (0: none). */
    private int maxDamage;
    /** Kiln asked the game for its durability already. */
    private boolean asked;

    /** Kiln's own: how rare its properties say it is (its name's colour). */
    Rarity kiln$rarity() {
        return rarity;
    }

    public Item(Properties properties) {
        if (properties.id == null) {
            throw new NullPointerException("Item id not set");
        }
        this.id = properties.id;
        this.maxStackSize = properties.maxStackSize;
        this.rarity = properties.rarity;
        this.maxDamage = properties.maxDamage;
        this.components = java.util.Map.copyOf(properties.components);
    }

    /**
     * The picture under its stacks' tooltips: a bundle's contents for the game's bundles (an item with
     * {@code bundle_contents}, D-177), unless the stack's {@code tooltip_display} hides them; none for others. A mod's
     * item overrides it with its own.
     */
    public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack stack) {
        return java.util.Optional.ofNullable(kiln.ItemTooltips.bundle(stack));
    }

    /** Kiln's own: the component of {@code type} its properties gave it; null for none. */
    @SuppressWarnings("unchecked")
    public <T> T kiln$component(net.minecraft.core.component.DataComponentType<? extends T> type) {
        return (T) components.get(type);
    }

    /** Kiln's own: the components its properties gave it. */
    public java.util.Map<net.minecraft.core.component.DataComponentType<?>, Object> kiln$components() {
        return components;
    }

    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public Item asItem() {
        return this;
    }

    public ItemStack getDefaultInstance() {
        return new ItemStack(this);
    }

    public int getDefaultMaxStackSize() {
        return maxStackSize;
    }

    public final String getDescriptionId() {
        return "item." + id.identifier().getNamespace() + "." + id.identifier().getPath().replace('/', '.');
    }

    /**
     * Kiln's own: its durability, 0 for an item that takes no damage. A game item's comes from the game: in a server
     * call Kiln asks the first time ({@code item <id>}), and the window's tick says it for what the player wears.
     */
    public int kiln$maxDamage() {
        if (maxDamage == 0 && !asked && kiln$id().getNamespace().equals("minecraft")) {
            if (kiln.Server.inCall()) {
                asked = true;
                String[] facts = kiln.Server.ask("item", kiln$id().toString()).trim().split(" ");
                if (facts.length == 2) {
                    maxDamage = Integer.parseInt(facts[0]);
                }
            } else if (kiln.GameItems.itemComponent(kiln$id().toString(), "minecraft:max_damage")
                    instanceof com.google.gson.JsonPrimitive max && max.isNumber()) {
                // On the client (a recipe viewer's repairs, D-159): the game's resources say it; not marked asked, so
                // the game's own answer still comes in the next server call that wants it.
                return max.getAsInt();
            }
        }
        return maxDamage;
    }

    /** Kiln's own: a game item's durability, as the game said with a stack of it. */
    public void kiln$maxDamage(int maxDamage) {
        this.maxDamage = maxDamage;
    }

    /** Kiln's own: the id it was made with. */
    public Identifier kiln$id() {
        return id.identifier();
    }

    /**
     * Kiln's own: the item called {@code id}: a mod's when one registered it, else the game's, registered as Kiln
     * meets it (vanilla's registry has them all).
     */
    public static synchronized Item kiln$byId(String id) {
        Identifier name = Identifier.parse(id);
        if (BuiltInRegistries.ITEM.containsKey(name)) {
            return BuiltInRegistries.ITEM.getValue(name);
        }
        return net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, name,
                kiln$game(name, new Properties().setId(ResourceKey.create(Registries.ITEM, name))));
    }

    /**
     * The game's item {@code name} as the class the game makes it, where a mod checks it by class (D-159): a banner
     * is a {@link BannerItem} of its color, a spawn egg a {@link SpawnEggItem}, an item named after the game's block of its id (as {@code items.json}
     * says) is a {@link BlockItem}, the rest plain items.
     */
    static Item kiln$game(Identifier name, Properties properties) {
        if (name.getNamespace().equals("minecraft")) {
            String path = name.getPath();
            if (path.endsWith("_banner")) {
                DyeColor color = DyeColor.byName(path.substring(0, path.length() - "_banner".length()), null);
                if (color != null) {
                    return new BannerItem(name, color, properties);
                }
            }
            if (path.endsWith("_spawn_egg")) {
                return new SpawnEggItem(properties);
            }
            if (kiln.GameItems.itemComponent(name.toString(), "minecraft:item_name")
                    instanceof com.google.gson.JsonObject itemName
                    && itemName.get("translate") instanceof com.google.gson.JsonPrimitive key
                    && key.getAsString().equals("block.minecraft." + path)) {
                return new BlockItem(name, properties);
            }
        }
        return new Item(properties);
    }

    @Override
    public String toString() {
        return id.identifier().toString();
    }

    public static class Properties {
        private ResourceKey<Item> id;
        private int maxStackSize = 64;
        private int maxDamage;
        private Rarity rarity = Rarity.COMMON;
        private final java.util.Map<net.minecraft.core.component.DataComponentType<?>, Object> components =
                new java.util.LinkedHashMap<>();

        public Properties setId(ResourceKey<Item> id) {
            this.id = id;
            return this;
        }

        public Properties stacksTo(int max) {
            maxStackSize = max;
            return this;
        }

        public Properties durability(int durability) {
            maxStackSize = 1;
            maxDamage = durability;
            return this;
        }

        public Properties rarity(Rarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public Properties fireResistant() {
            return this;
        }

        public Properties useBlockDescriptionPrefix() {
            return this;
        }

        /** A component the item's stacks have unless they change it (the game's item components). */
        public <T> Properties component(net.minecraft.core.component.DataComponentType<T> type, T value) {
            if (value == null) {
                components.remove(type);
            } else {
                components.put(type, value);
            }
            return this;
        }

        public Properties attributes(net.minecraft.world.item.component.ItemAttributeModifiers modifiers) {
            return component(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
        }

        public Properties enchantable(int value) {
            return component(net.minecraft.core.component.DataComponents.ENCHANTABLE,
                    new net.minecraft.world.item.enchantment.Enchantable(value));
        }

        public Properties repairable(net.minecraft.tags.TagKey<Item> tag) {
            return component(net.minecraft.core.component.DataComponents.REPAIRABLE,
                    new net.minecraft.world.item.enchantment.Repairable(kiln.ClientComponents.itemTag(tag.location())));
        }

        /**
         * A piece of armour of {@code material} (D-177), as the game's: its durability, its armor, toughness and
         * knockback resistance in its slot (modifier {@code minecraft:armor.<type>}), how well it enchants, what
         * repairs it, and the slot it is worn in.
         */
        public Properties humanoidArmor(net.minecraft.world.item.equipment.ArmorMaterial material,
                net.minecraft.world.item.equipment.ArmorType type) {
            durability(type.getDurability(material.durability()));
            attributes(kiln.ItemAttributes.armor(material, type));
            enchantable(material.enchantmentValue());
            if (material.repairIngredient() != null) {
                repairable(material.repairIngredient());
            }
            return component(net.minecraft.core.component.DataComponents.EQUIPPABLE,
                    new net.minecraft.world.item.equipment.Equippable(type.getSlot(), material.equipSound(),
                            java.util.Optional.ofNullable(material.assetId()), java.util.Optional.empty(),
                            java.util.Optional.empty(), true, true, true, false, false, null));
        }

        /** A tool of {@code material} that mines {@code mineable} (D-177): its durability, repairs and attack. */
        public Properties tool(ToolMaterial material, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> mineable,
                float attackDamage, float attackSpeed, float disableBlockingForSeconds) {
            durability(material.durability());
            if (material.repairItems() != null) {
                repairable(material.repairItems());
            }
            enchantable(material.enchantmentValue());
            return attributes(kiln.ItemAttributes.weapon(attackDamage + material.attackDamageBonus(), attackSpeed));
        }

        public Properties pickaxe(ToolMaterial material, float attackDamage, float attackSpeed) {
            return tool(material, null, attackDamage, attackSpeed, 0.0F);
        }

        public Properties axe(ToolMaterial material, float attackDamage, float attackSpeed) {
            return tool(material, null, attackDamage, attackSpeed, 5.0F);
        }

        public Properties hoe(ToolMaterial material, float attackDamage, float attackSpeed) {
            return tool(material, null, attackDamage, attackSpeed, 0.0F);
        }

        public Properties shovel(ToolMaterial material, float attackDamage, float attackSpeed) {
            return tool(material, null, attackDamage, attackSpeed, 0.0F);
        }

        /** A sword of {@code material} (D-177): its durability, repairs and attack. */
        public Properties sword(ToolMaterial material, float attackDamage, float attackSpeed) {
            return tool(material, null, attackDamage, attackSpeed, 0.0F);
        }
        // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
        public net.minecraft.world.item.Item.Properties craftRemainder(net.minecraft.world.item.Item p0) { kiln.Missing.called("net.minecraft.world.item.Item.Properties.craftRemainder"); return this; }
        public net.minecraft.world.item.Item.Properties cookingFuel(net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider> p0) { kiln.Missing.called("net.minecraft.world.item.Item.Properties.cookingFuel"); return this; }
        public net.minecraft.world.item.Item.Properties overrideDescription(java.lang.String p0) { kiln.Missing.called("net.minecraft.world.item.Item.Properties.overrideDescription"); return this; }
        public net.minecraft.world.item.Item.Properties compostable(net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider> p0) { kiln.Missing.called("net.minecraft.world.item.Item.Properties.compostable"); return this; }
        // End of what tools/kiln-stubs.py generated.
    }
    /**
     * The lines an item adds to its tooltip under its name; a game item's are Horizon's, so none here. A mod's item
     * overrides it: its lines reach Horizon's tooltip (ItemStack.getTooltipLines, Kiln's Mixin bridge, D-145).
     */
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {}

    private net.minecraft.core.Holder.Reference<Item> holder;

    /** An item by its id, as a holder, as the game's item codec reads and writes it. */
    public static final com.mojang.serialization.Codec<net.minecraft.core.Holder<Item>> CODEC =
            net.minecraft.resources.Identifier.CODEC.xmap(
                    id -> (net.minecraft.core.Holder<Item>) kiln$byId(id.toString()).builtInRegistryHolder(),
                    h -> h.unwrapKey().orElseThrow().identifier());

    /**
     * Its own components, those its stacks have unless they change them: what Kiln can ask the game of one (a game
     * item's fuel, during a server call), by type; Kiln lists none of them.
     */
    public net.minecraft.core.component.DataComponentMap components() {
        Item self = this;
        return new net.minecraft.core.component.DataComponentMap() {
            @Override
            public <T> T get(net.minecraft.core.component.DataComponentType<? extends T> type) {
                return kiln.Recipes.component(self, type);
            }

            @Override
            public java.util.Set<net.minecraft.core.component.DataComponentType<?>> keySet() {
                return self.components.keySet();
            }
        };
    }

    /** Its entry in the item registry, by its id: one per item, so two lookups of it are the same holder. */
    public synchronized net.minecraft.core.Holder.Reference<Item> builtInRegistryHolder() {
        if (holder == null) {
            holder = net.minecraft.core.Holder.Reference.kiln$of(id, this);
        }
        return holder;
    }

    /**
     * What an item's tooltip may look up: the registries, the tick rate, a map's data, whether the world is peaceful.
     * Kiln has one set of registries for every world ({@code RegistryAccess.KILN}), the game's tick rate, and no
     * maps' data.
     */
    public interface TooltipContext {
        /** Outside a world: Kiln's registries all the same, so a tooltip still names enchantments and the rest. */
        TooltipContext EMPTY = of(net.minecraft.core.RegistryAccess.KILN);

        net.minecraft.core.HolderLookup.Provider registries();

        float tickRate();

        net.minecraft.world.level.saveddata.maps.MapItemSavedData mapData(net.minecraft.world.level.saveddata.maps.MapId id);

        default boolean isPeaceful() {
            return false;
        }

        static TooltipContext of(net.minecraft.core.HolderLookup.Provider registries) {
            return new TooltipContext() {
                @Override
                public net.minecraft.core.HolderLookup.Provider registries() {
                    return registries;
                }

                @Override
                public float tickRate() {
                    return 20.0F;
                }

                @Override
                public net.minecraft.world.level.saveddata.maps.MapItemSavedData mapData(
                        net.minecraft.world.level.saveddata.maps.MapId id) {
                    return null;
                }
            };
        }

        /** A world's: its registries; Kiln has no maps' data nor the world's tick rate, so the game's 20. */
        static TooltipContext of(net.minecraft.world.level.Level level) {
            return level == null ? EMPTY : of(level.registryAccess());
        }
    }

    /**
     * Its name for {@code stack}, as the game's {@code Item.getName}: the stack's name without a custom one (a
     * potion's by its potion, D-164).
     */
    public net.minecraft.network.chat.Component getName(net.minecraft.world.item.ItemStack stack) {
        net.minecraft.world.item.ItemStack named = stack;
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            named = stack.copy();
            named.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        }
        return named.getHoverName();
    }

    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext p0) { kiln.Missing.called("net.minecraft.world.item.Item.useOn"); return null; }
    public float getDestroySpeed(net.minecraft.world.item.ItemStack p0, net.minecraft.world.level.block.state.BlockState p1) { kiln.Missing.called("net.minecraft.world.item.Item.getDestroySpeed"); return 0.0F; }
    public boolean isCorrectToolForDrops(net.minecraft.world.item.ItemStack p0, net.minecraft.world.level.block.state.BlockState p1) { kiln.Missing.called("net.minecraft.world.item.Item.isCorrectToolForDrops"); return false; }
    public net.minecraft.world.item.ItemStackTemplate getCraftingRemainder() { kiln.Missing.called("net.minecraft.world.item.Item.getCraftingRemainder"); return null; }
    public void onCraftedPostProcess(net.minecraft.world.item.ItemStack p0, net.minecraft.world.level.Level p1) { kiln.Missing.called("net.minecraft.world.item.Item.onCraftedPostProcess"); }
    // End of what tools/kiln-stubs.py generated.
}
