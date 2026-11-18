package net.minecraft.core.component;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code DataComponents}: the game's component types a mod names. Those with a codec here read
 * their value from the stack as the game saved it; the others ({@code kiln.Constants.component}) live on the stack only.
 */
public class DataComponents {
    /** A stack's own tags, which only mods and commands read; saved with it. */
    public static final DataComponentType<net.minecraft.world.item.component.CustomData> CUSTOM_DATA =
            persistent("custom_data", net.minecraft.world.item.component.CustomData.CODEC);
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.util.Unit> UNBREAKABLE = kiln.Constants.component("unbreakable");
    public static final DataComponentType<net.minecraft.world.item.enchantment.ItemEnchantments> ENCHANTMENTS =
            persistent("enchantments", net.minecraft.world.item.enchantment.ItemEnchantments.CODEC);
    public static final DataComponentType<net.minecraft.world.item.enchantment.ItemEnchantments> STORED_ENCHANTMENTS =
            persistent("stored_enchantments", net.minecraft.world.item.enchantment.ItemEnchantments.CODEC);
    /** What the item adds to its holder's attributes (D-177): saved as the game saves it, so its tooltip lines are written. */
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.ItemAttributeModifiers> ATTRIBUTE_MODIFIERS =
            persistent("attribute_modifiers", net.minecraft.world.item.component.ItemAttributeModifiers.CODEC);
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.TooltipDisplay> TOOLTIP_DISPLAY =
            persistent("tooltip_display", net.minecraft.world.item.component.TooltipDisplay.CODEC);
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.Tool> TOOL = kiln.Constants.component("tool");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.Weapon> WEAPON = kiln.Constants.component("weapon");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.equipment.Equippable> EQUIPPABLE = kiln.Constants.component("equippable");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.core.component.BlockTransformer>> BLOCK_TRANSFORMER = kiln.Constants.component("block_transformer");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.TypedEntityData<net.minecraft.world.level.block.entity.BlockEntityType<?>>> BLOCK_ENTITY_DATA = kiln.Constants.component("block_entity_data");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.CookingFuel> COOKING_FUEL = kiln.Constants.component("cooking_fuel");
    public static final DataComponentType<net.minecraft.world.item.component.ItemContainerContents> CONTAINER =
            persistent("container", net.minecraft.world.item.component.ItemContainerContents.CODEC);
    public static final DataComponentType<net.minecraft.world.item.component.BundleContents> BUNDLE_CONTENTS =
            persistent("bundle_contents", net.minecraft.world.item.component.BundleContents.CODEC);
    public static final DataComponentType<net.minecraft.world.item.alchemy.PotionContents> POTION_CONTENTS =
            persistent("potion_contents", net.minecraft.world.item.alchemy.PotionContents.CODEC);


    /**
     * What a recipe viewer (JEI) reads of a stack to tell its kinds apart (D-152): on the stack only, as the others
     * made by {@code kiln.Constants.component}. Those of a value class Kiln hasn't yet are typed {@code Object}: the
     * game's type is erased, so a mod's code that names them still links, and their value is the saved JSON
     * ({@code kiln.Constants.erased}, D-172).
     */
    public static final DataComponentType<Object> BANNER_PATTERNS = kiln.Constants.erased("banner_patterns");
    /** Saved as the game saves it: a shield decorated with a banner keeps the banner's color (D-197). */
    public static final DataComponentType<net.minecraft.world.item.DyeColor> BASE_COLOR =
            persistent("base_color", net.minecraft.world.item.DyeColor.CODEC);
    public static final DataComponentType<net.minecraft.world.item.component.DyedItemColor> DYED_COLOR =
            persistent("dyed_color", net.minecraft.world.item.component.DyedItemColor.CODEC);
    /** Not saved, as in the game: what happens to a map item's map once it is in an inventory. */
    public static final DataComponentType<net.minecraft.world.item.component.MapPostProcessing> MAP_POST_PROCESSING =
            kiln.Constants.component("map_post_processing");
    public static final DataComponentType<Object> BLOCK_STATE = kiln.Constants.erased("block_state");
    public static final DataComponentType<net.minecraft.world.item.component.Compostable> COMPOSTABLE =
            kiln.Constants.component("compostable");
    public static final DataComponentType<net.minecraft.world.item.DyeColor> DYE = kiln.Constants.component("dye");
    public static final DataComponentType<net.minecraft.world.item.component.Fireworks> FIREWORKS =
            persistent("fireworks", net.minecraft.world.item.component.Fireworks.CODEC);
    public static final DataComponentType<net.minecraft.world.item.component.FireworkExplosion> FIREWORK_EXPLOSION =
            persistent("firework_explosion", net.minecraft.world.item.component.FireworkExplosion.CODEC);
    public static final DataComponentType<Object> INSTRUMENT = kiln.Constants.erased("instrument");
    public static final DataComponentType<Object> OMINOUS_BOTTLE_AMPLIFIER = kiln.Constants.erased("ominous_bottle_amplifier");
    public static final DataComponentType<Object> PAINTING_VARIANT = kiln.Constants.erased("painting/variant");
    public static final DataComponentType<Object> POT_DECORATIONS = kiln.Constants.erased("pot_decorations");
    public static final DataComponentType<net.minecraft.world.item.enchantment.Repairable> REPAIRABLE =
            kiln.Constants.component("repairable");
    public static final DataComponentType<net.minecraft.world.item.enchantment.Enchantable> ENCHANTABLE =
            kiln.Constants.component("enchantable");
    public static final DataComponentType<Integer> REPAIR_COST = persistent("repair_cost", Codec.INT);
    public static final DataComponentType<Object> SUSPICIOUS_STEW_EFFECTS = kiln.Constants.erased("suspicious_stew_effects");
    public static final DataComponentType<Identifier> TOOLTIP_STYLE = kiln.Constants.component("tooltip_style");

    /** A name given to the stack (an anvil's, a mod's), shown in italics in its tooltip, as vanilla's. */
    public static final DataComponentType<net.minecraft.network.chat.Component> CUSTOM_NAME =
            persistent("custom_name", net.minecraft.network.chat.ComponentSerialization.CODEC);
    /** The item's own name in place of its translation, not italic. */
    public static final DataComponentType<net.minecraft.network.chat.Component> ITEM_NAME =
            persistent("item_name", net.minecraft.network.chat.ComponentSerialization.CODEC);
    /** Whose head a player head is, and the skin it wears (head mods give one with its own texture), as saved. */
    public static final DataComponentType<net.minecraft.world.item.component.ResolvableProfile> PROFILE =
            persistent("profile", net.minecraft.world.item.component.ResolvableProfile.CODEC);
    /** A signed book's title, author, generation and pages, as the game saves them (#101). */
    public static final DataComponentType<net.minecraft.world.item.component.WrittenBookContent> WRITTEN_BOOK_CONTENT =
            persistent("written_book_content", net.minecraft.world.item.component.WrittenBookContent.CODEC);
    public static final DataComponentType<net.minecraft.world.item.component.ItemLore> LORE =
            persistent("lore", net.minecraft.world.item.component.ItemLore.CODEC);
    /** Whether the stack glints, whatever its enchantments say. */
    public static final DataComponentType<Boolean> ENCHANTMENT_GLINT_OVERRIDE =
            persistent("enchantment_glint_override", Codec.BOOL);
    /** The model the game draws the stack with, in place of its item's. */
    public static final DataComponentType<Identifier> ITEM_MODEL = persistent("item_model", Identifier.CODEC);

    public DataComponents() {}

    private static <T> DataComponentType<T> persistent(String id, Codec<T> codec) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.withDefaultNamespace(id),
                DataComponentType.<T>builder().persistent(codec).build());
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static final net.minecraft.util.EncoderCache ENCODER_CACHE = null;
    public static final net.minecraft.core.component.DataComponentType<java.lang.Integer> MAX_STACK_SIZE = kiln.Constants.component("max_stack_size");
    public static final net.minecraft.core.component.DataComponentType<java.lang.Integer> MAX_DAMAGE = kiln.Constants.component("max_damage");
    public static final net.minecraft.core.component.DataComponentType<java.lang.Integer> DAMAGE = kiln.Constants.component("damage");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.UseEffects> USE_EFFECTS = kiln.Constants.component("use_effects");
    public static final net.minecraft.core.component.DataComponentType<java.lang.Float> MINIMUM_ATTACK_CHARGE = kiln.Constants.component("minimum_attack_charge");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType>> DAMAGE_TYPE = kiln.Constants.component("damage_type");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.Rarity> RARITY = kiln.Constants.component("rarity");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.AdventureModePredicate> CAN_PLACE_ON = kiln.Constants.component("can_place_on");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.AdventureModePredicate> CAN_BREAK = kiln.Constants.component("can_break");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.CustomModelData> CUSTOM_MODEL_DATA = kiln.Constants.component("custom_model_data");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.util.Unit> CREATIVE_SLOT_LOCK = kiln.Constants.component("creative_slot_lock");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.util.Unit> INTANGIBLE_PROJECTILE = kiln.Constants.component("intangible_projectile");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.food.FoodProperties> FOOD = kiln.Constants.component("food");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.Consumable> CONSUMABLE = kiln.Constants.component("consumable");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.UseRemainder> USE_REMAINDER = kiln.Constants.component("use_remainder");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.UseCooldown> USE_COOLDOWN = kiln.Constants.component("use_cooldown");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.DamageResistant> DAMAGE_RESISTANT = kiln.Constants.component("damage_resistant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.AttackRange> ATTACK_RANGE = kiln.Constants.component("attack_range");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.util.Unit> GLIDER = kiln.Constants.component("glider");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.DeathProtection> DEATH_PROTECTION = kiln.Constants.component("death_protection");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.BlocksAttacks> BLOCKS_ATTACKS = kiln.Constants.component("blocks_attacks");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.PiercingWeapon> PIERCING_WEAPON = kiln.Constants.component("piercing_weapon");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.KineticWeapon> KINETIC_WEAPON = kiln.Constants.component("kinetic_weapon");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.SwingAnimation> ATTACK_ANIMATION = kiln.Constants.component("attack_animation");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.SwingAnimation> INTERACT_ANIMATION = kiln.Constants.component("interact_animation");
    public static final net.minecraft.core.component.DataComponentType<java.lang.Integer> ADDITIONAL_TRADE_COST = kiln.Constants.component("additional_trade_cost");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.food.VillagerFood> VILLAGER_FOOD = kiln.Constants.component("villager_food");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.level.saveddata.maps.MapId> MAP_ID = kiln.Constants.component("map_id");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.MapDecorations> MAP_DECORATIONS = kiln.Constants.component("map_decorations");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.ChargedProjectiles> CHARGED_PROJECTILES = kiln.Constants.component("charged_projectiles");
    public static final net.minecraft.core.component.DataComponentType<java.lang.Float> POTION_DURATION_SCALE = kiln.Constants.component("potion_duration_scale");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.WritableBookContent> WRITABLE_BOOK_CONTENT = kiln.Constants.component("writable_book_content");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.equipment.trim.ArmorTrim> TRIM = kiln.Constants.component("trim");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.DebugStickState> DEBUG_STICK_STATE = kiln.Constants.component("debug_stick_state");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.TypedEntityData<net.minecraft.world.entity.EntityType<?>>> ENTITY_DATA = kiln.Constants.component("entity_data");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.CustomData> BUCKET_ENTITY_DATA = kiln.Constants.component("bucket_entity_data");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.item.equipment.trim.TrimMaterial>> PROVIDES_TRIM_MATERIAL = kiln.Constants.component("provides_trim_material");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.JukeboxPlayable> JUKEBOX_PLAYABLE = kiln.Constants.component("jukebox_playable");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.HolderSet<net.minecraft.world.level.block.entity.BannerPattern>> PROVIDES_BANNER_PATTERNS = kiln.Constants.component("provides_banner_patterns");
    public static final net.minecraft.core.component.DataComponentType<java.util.List<net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>>> RECIPES = kiln.Constants.component("recipes");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.LodestoneTracker> LODESTONE_TRACKER = kiln.Constants.component("lodestone_tracker");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.resources.Identifier> NOTE_BLOCK_SOUND = kiln.Constants.component("note_block_sound");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.Bees> BEES = kiln.Constants.component("bees");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.SulfurCubeContent> SULFUR_CUBE_CONTENT = kiln.Constants.component("sulfur_cube_content");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.LockCode> LOCK = kiln.Constants.component("lock");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.SeededContainerLoot> CONTAINER_LOOT = kiln.Constants.component("container_loot");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent>> BREAK_SOUND = kiln.Constants.component("break_sound");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.BrewingFuel> BREWING_FUEL = kiln.Constants.component("brewing_fuel");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.MobVisibility> MOB_VISIBILITY = kiln.Constants.component("mob_visibility");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.entity.npc.villager.VillagerType>> VILLAGER_VARIANT = kiln.Constants.component("villager/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.entity.animal.wolf.WolfVariant>> WOLF_VARIANT = kiln.Constants.component("wolf/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.entity.animal.wolf.WolfSoundVariant>> WOLF_SOUND_VARIANT = kiln.Constants.component("wolf/sound_variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.DyeColor> WOLF_COLLAR = kiln.Constants.component("wolf/collar");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.entity.animal.fox.Fox.Variant> FOX_VARIANT = kiln.Constants.component("fox/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.entity.animal.fish.Salmon.Variant> SALMON_SIZE = kiln.Constants.component("salmon/size");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.entity.animal.parrot.Parrot.Variant> PARROT_VARIANT = kiln.Constants.component("parrot/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.entity.animal.fish.TropicalFish.Pattern> TROPICAL_FISH_PATTERN = kiln.Constants.component("tropical_fish/pattern");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.DyeColor> TROPICAL_FISH_BASE_COLOR = kiln.Constants.component("tropical_fish/base_color");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.DyeColor> TROPICAL_FISH_PATTERN_COLOR = kiln.Constants.component("tropical_fish/pattern_color");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.entity.animal.cow.MushroomCow.Variant> MOOSHROOM_VARIANT = kiln.Constants.component("mooshroom/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.entity.animal.rabbit.Rabbit.Variant> RABBIT_VARIANT = kiln.Constants.component("rabbit/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.entity.animal.pig.PigVariant>> PIG_VARIANT = kiln.Constants.component("pig/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.entity.animal.pig.PigSoundVariant>> PIG_SOUND_VARIANT = kiln.Constants.component("pig/sound_variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.entity.animal.cow.CowVariant>> COW_VARIANT = kiln.Constants.component("cow/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.entity.animal.cow.CowSoundVariant>> COW_SOUND_VARIANT = kiln.Constants.component("cow/sound_variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.entity.animal.chicken.ChickenVariant>> CHICKEN_VARIANT = kiln.Constants.component("chicken/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.entity.animal.chicken.ChickenSoundVariant>> CHICKEN_SOUND_VARIANT = kiln.Constants.component("chicken/sound_variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariant>> ZOMBIE_NAUTILUS_VARIANT = kiln.Constants.component("zombie_nautilus/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.entity.animal.frog.FrogVariant>> FROG_VARIANT = kiln.Constants.component("frog/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.entity.animal.equine.Variant> HORSE_VARIANT = kiln.Constants.component("horse/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.entity.animal.equine.Llama.Variant> LLAMA_VARIANT = kiln.Constants.component("llama/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.entity.animal.axolotl.Axolotl.Variant> AXOLOTL_VARIANT = kiln.Constants.component("axolotl/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.entity.animal.feline.CatVariant>> CAT_VARIANT = kiln.Constants.component("cat/variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.entity.animal.feline.CatSoundVariant>> CAT_SOUND_VARIANT = kiln.Constants.component("cat/sound_variant");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.DyeColor> CAT_COLLAR = kiln.Constants.component("cat/collar");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.DyeColor> SHEEP_COLOR = kiln.Constants.component("sheep/color");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.DyeColor> SHULKER_COLOR = kiln.Constants.component("shulker/color");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.level.block.entity.DecoratedPotPattern>> PROVIDES_POTTERY_PATTERN = kiln.Constants.component("provides_pottery_pattern");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.level.block.entity.SignText> SIGN_TEXT_FRONT = kiln.Constants.component("sign_text_front");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.level.block.entity.SignText> SIGN_TEXT_BACK = kiln.Constants.component("sign_text_back");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.util.Unit> WAXED = kiln.Constants.component("waxed");
    public static final net.minecraft.core.component.DataComponentType<net.minecraft.world.item.DyeColor> CUSHION_COLOR = kiln.Constants.component("cushion/color");
    public static final net.minecraft.core.component.DataComponentMap COMMON_ITEM_COMPONENTS = null;
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
