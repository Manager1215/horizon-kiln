package net.minecraft.world.level.storage.loot;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Kiln's stand-in for {@code BuiltInLootTables}: the game's own loot tables, by key. Kiln fires a mod's loot changes
 * (Fabric's {@code LootTableEvents.MODIFY}) for each of them.
 */
public final class BuiltInLootTables {
    public static final ResourceKey<LootTable> SPAWN_BONUS_CHEST = key("chests/spawn_bonus_chest");
    public static final ResourceKey<LootTable> END_CITY_TREASURE = key("chests/end_city_treasure");
    public static final ResourceKey<LootTable> SIMPLE_DUNGEON = key("chests/simple_dungeon");
    public static final ResourceKey<LootTable> VILLAGE_WEAPONSMITH = key("chests/village/village_weaponsmith");
    public static final ResourceKey<LootTable> VILLAGE_TOOLSMITH = key("chests/village/village_toolsmith");
    public static final ResourceKey<LootTable> VILLAGE_ARMORER = key("chests/village/village_armorer");
    public static final ResourceKey<LootTable> VILLAGE_CARTOGRAPHER = key("chests/village/village_cartographer");
    public static final ResourceKey<LootTable> VILLAGE_MASON = key("chests/village/village_mason");
    public static final ResourceKey<LootTable> VILLAGE_SHEPHERD = key("chests/village/village_shepherd");
    public static final ResourceKey<LootTable> VILLAGE_BUTCHER = key("chests/village/village_butcher");
    public static final ResourceKey<LootTable> VILLAGE_FLETCHER = key("chests/village/village_fletcher");
    public static final ResourceKey<LootTable> VILLAGE_FISHER = key("chests/village/village_fisher");
    public static final ResourceKey<LootTable> VILLAGE_TANNERY = key("chests/village/village_tannery");
    public static final ResourceKey<LootTable> VILLAGE_TEMPLE = key("chests/village/village_temple");
    public static final ResourceKey<LootTable> VILLAGE_DESERT_HOUSE = key("chests/village/village_desert_house");
    public static final ResourceKey<LootTable> VILLAGE_PLAINS_HOUSE = key("chests/village/village_plains_house");
    public static final ResourceKey<LootTable> VILLAGE_TAIGA_HOUSE = key("chests/village/village_taiga_house");
    public static final ResourceKey<LootTable> VILLAGE_SNOWY_HOUSE = key("chests/village/village_snowy_house");
    public static final ResourceKey<LootTable> VILLAGE_SAVANNA_HOUSE = key("chests/village/village_savanna_house");
    public static final ResourceKey<LootTable> ABANDONED_MINESHAFT = key("chests/abandoned_mineshaft");
    public static final ResourceKey<LootTable> NETHER_BRIDGE = key("chests/nether_bridge");
    public static final ResourceKey<LootTable> STRONGHOLD_LIBRARY = key("chests/stronghold_library");
    public static final ResourceKey<LootTable> STRONGHOLD_CROSSING = key("chests/stronghold_crossing");
    public static final ResourceKey<LootTable> STRONGHOLD_CORRIDOR = key("chests/stronghold_corridor");
    public static final ResourceKey<LootTable> DESERT_PYRAMID = key("chests/desert_pyramid");
    public static final ResourceKey<LootTable> JUNGLE_TEMPLE = key("chests/jungle_temple");
    public static final ResourceKey<LootTable> JUNGLE_TEMPLE_DISPENSER = key("chests/jungle_temple_dispenser");
    public static final ResourceKey<LootTable> IGLOO_CHEST = key("chests/igloo_chest");
    public static final ResourceKey<LootTable> WOODLAND_MANSION = key("chests/woodland_mansion");
    public static final ResourceKey<LootTable> UNDERWATER_RUIN_SMALL = key("chests/underwater_ruin_small");
    public static final ResourceKey<LootTable> UNDERWATER_RUIN_BIG = key("chests/underwater_ruin_big");
    public static final ResourceKey<LootTable> BURIED_TREASURE = key("chests/buried_treasure");
    public static final ResourceKey<LootTable> SHIPWRECK_MAP = key("chests/shipwreck_map");
    public static final ResourceKey<LootTable> SHIPWRECK_SUPPLY = key("chests/shipwreck_supply");
    public static final ResourceKey<LootTable> SHIPWRECK_TREASURE = key("chests/shipwreck_treasure");
    public static final ResourceKey<LootTable> PILLAGER_OUTPOST = key("chests/pillager_outpost");
    public static final ResourceKey<LootTable> BASTION_TREASURE = key("chests/bastion_treasure");
    public static final ResourceKey<LootTable> BASTION_OTHER = key("chests/bastion_other");
    public static final ResourceKey<LootTable> BASTION_BRIDGE = key("chests/bastion_bridge");
    public static final ResourceKey<LootTable> BASTION_HOGLIN_STABLE = key("chests/bastion_hoglin_stable");
    public static final ResourceKey<LootTable> ANCIENT_CITY = key("chests/ancient_city");
    public static final ResourceKey<LootTable> ANCIENT_CITY_ICE_BOX = key("chests/ancient_city_ice_box");
    public static final ResourceKey<LootTable> RUINED_PORTAL = key("chests/ruined_portal");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_REWARD = key("chests/trial_chambers/reward");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_REWARD_COMMON = key("chests/trial_chambers/reward_common");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_REWARD_RARE = key("chests/trial_chambers/reward_rare");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_REWARD_UNIQUE = key("chests/trial_chambers/reward_unique");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_REWARD_OMINOUS = key("chests/trial_chambers/reward_ominous");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_REWARD_OMINOUS_COMMON = key("chests/trial_chambers/reward_ominous_common");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_REWARD_OMINOUS_RARE = key("chests/trial_chambers/reward_ominous_rare");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_REWARD_OMINOUS_UNIQUE = key("chests/trial_chambers/reward_ominous_unique");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_SUPPLY = key("chests/trial_chambers/supply");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_CORRIDOR = key("chests/trial_chambers/corridor");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_INTERSECTION = key("chests/trial_chambers/intersection");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_INTERSECTION_BARREL = key("chests/trial_chambers/intersection_barrel");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_ENTRANCE = key("chests/trial_chambers/entrance");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_CORRIDOR_DISPENSER = key("dispensers/trial_chambers/corridor");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_CHAMBER_DISPENSER = key("dispensers/trial_chambers/chamber");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_WATER_DISPENSER = key("dispensers/trial_chambers/water");
    public static final ResourceKey<LootTable> TRIAL_CHAMBERS_CORRIDOR_POT = key("pots/trial_chambers/corridor");
    public static final ResourceKey<LootTable> EQUIPMENT_TRIAL_CHAMBER = key("equipment/trial_chamber");
    public static final ResourceKey<LootTable> EQUIPMENT_TRIAL_CHAMBER_RANGED = key("equipment/trial_chamber_ranged");
    public static final ResourceKey<LootTable> EQUIPMENT_TRIAL_CHAMBER_MELEE = key("equipment/trial_chamber_melee");
    public static final ResourceKey<LootTable> FISHING = key("gameplay/fishing");
    public static final ResourceKey<LootTable> FISHING_JUNK = key("gameplay/fishing/junk");
    public static final ResourceKey<LootTable> FISHING_TREASURE = key("gameplay/fishing/treasure");
    public static final ResourceKey<LootTable> FISHING_FISH = key("gameplay/fishing/fish");
    public static final ResourceKey<LootTable> CAT_MORNING_GIFT = key("gameplay/cat_morning_gift");
    public static final ResourceKey<LootTable> ARMORER_GIFT = key("gameplay/hero_of_the_village/armorer_gift");
    public static final ResourceKey<LootTable> BUTCHER_GIFT = key("gameplay/hero_of_the_village/butcher_gift");
    public static final ResourceKey<LootTable> CARTOGRAPHER_GIFT = key("gameplay/hero_of_the_village/cartographer_gift");
    public static final ResourceKey<LootTable> CLERIC_GIFT = key("gameplay/hero_of_the_village/cleric_gift");
    public static final ResourceKey<LootTable> FARMER_GIFT = key("gameplay/hero_of_the_village/farmer_gift");
    public static final ResourceKey<LootTable> FISHERMAN_GIFT = key("gameplay/hero_of_the_village/fisherman_gift");
    public static final ResourceKey<LootTable> FLETCHER_GIFT = key("gameplay/hero_of_the_village/fletcher_gift");
    public static final ResourceKey<LootTable> LEATHERWORKER_GIFT = key("gameplay/hero_of_the_village/leatherworker_gift");
    public static final ResourceKey<LootTable> LIBRARIAN_GIFT = key("gameplay/hero_of_the_village/librarian_gift");
    public static final ResourceKey<LootTable> MASON_GIFT = key("gameplay/hero_of_the_village/mason_gift");
    public static final ResourceKey<LootTable> SHEPHERD_GIFT = key("gameplay/hero_of_the_village/shepherd_gift");
    public static final ResourceKey<LootTable> TOOLSMITH_GIFT = key("gameplay/hero_of_the_village/toolsmith_gift");
    public static final ResourceKey<LootTable> WEAPONSMITH_GIFT = key("gameplay/hero_of_the_village/weaponsmith_gift");
    public static final ResourceKey<LootTable> UNEMPLOYED_GIFT = key("gameplay/hero_of_the_village/unemployed_gift");
    public static final ResourceKey<LootTable> BABY_VILLAGER_GIFT = key("gameplay/hero_of_the_village/baby_gift");
    public static final ResourceKey<LootTable> SNIFFER_DIGGING = key("gameplay/sniffer_digging");
    public static final ResourceKey<LootTable> PANDA_SNEEZE = key("gameplay/panda_sneeze");
    public static final ResourceKey<LootTable> CHICKEN_LAY = key("gameplay/chicken_lay");
    public static final ResourceKey<LootTable> ARMADILLO_SHED = key("gameplay/armadillo_shed");
    public static final ResourceKey<LootTable> TURTLE_GROW = key("gameplay/turtle_grow");
    public static final ResourceKey<LootTable> HARVEST_CAVE_VINE = key("harvest/cave_vine");
    public static final ResourceKey<LootTable> HARVEST_SWEET_BERRY_BUSH = key("harvest/sweet_berry_bush");
    public static final ResourceKey<LootTable> HARVEST_BEEHIVE = key("harvest/beehive");
    public static final ResourceKey<LootTable> CARVE_PUMPKIN = key("carve/pumpkin");
    public static final ResourceKey<LootTable> TILL_ROOTED_DIRT = key("till/rooted_dirt");
    public static final ResourceKey<LootTable> PIGLIN_BARTERING = key("gameplay/piglin_bartering");
    public static final ResourceKey<LootTable> SPAWNER_TRIAL_CHAMBER_KEY = key("spawners/trial_chamber/key");
    public static final ResourceKey<LootTable> SPAWNER_TRIAL_CHAMBER_CONSUMABLES = key("spawners/trial_chamber/consumables");
    public static final ResourceKey<LootTable> SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY = key("spawners/ominous/trial_chamber/key");
    public static final ResourceKey<LootTable> SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES = key("spawners/ominous/trial_chamber/consumables");
    public static final ResourceKey<LootTable> SPAWNER_TRIAL_ITEMS_TO_DROP_WHEN_OMINOUS = key("spawners/trial_chamber/items_to_drop_when_ominous");
    public static final ResourceKey<LootTable> ARMADILLO_BRUSH = key("brush/armadillo");
    public static final ResourceKey<LootTable> BOGGED_SHEAR = key("shearing/bogged");
    public static final ResourceKey<LootTable> SHEAR_MOOSHROOM = key("shearing/mooshroom");
    public static final ResourceKey<LootTable> SHEAR_RED_MOOSHROOM = key("shearing/mooshroom/red");
    public static final ResourceKey<LootTable> SHEAR_BROWN_MOOSHROOM = key("shearing/mooshroom/brown");
    public static final ResourceKey<LootTable> SHEAR_SNOW_GOLEM = key("shearing/snow_golem");
    public static final ResourceKey<LootTable> SHEAR_SHEEP = key("shearing/sheep");
    public static final ResourceKey<LootTable> CHARGED_CREEPER = key("charged_creeper/root");
    public static final ResourceKey<LootTable> CHARGED_CREEPER_PIGLIN = key("charged_creeper/piglin");
    public static final ResourceKey<LootTable> CHARGED_CREEPER_CREEPER = key("charged_creeper/creeper");
    public static final ResourceKey<LootTable> CHARGED_CREEPER_SKELETON = key("charged_creeper/skeleton");
    public static final ResourceKey<LootTable> CHARGED_CREEPER_WITHER_SKELETON = key("charged_creeper/wither_skeleton");
    public static final ResourceKey<LootTable> CHARGED_CREEPER_ZOMBIE = key("charged_creeper/zombie");
    public static final ResourceKey<LootTable> DESERT_WELL_ARCHAEOLOGY = key("archaeology/desert_well");
    public static final ResourceKey<LootTable> DESERT_PYRAMID_ARCHAEOLOGY = key("archaeology/desert_pyramid");
    public static final ResourceKey<LootTable> TRAIL_RUINS_ARCHAEOLOGY_COMMON = key("archaeology/trail_ruins_common");
    public static final ResourceKey<LootTable> TRAIL_RUINS_ARCHAEOLOGY_RARE = key("archaeology/trail_ruins_rare");
    public static final ResourceKey<LootTable> OCEAN_RUIN_WARM_ARCHAEOLOGY = key("archaeology/ocean_ruin_warm");
    public static final ResourceKey<LootTable> OCEAN_RUIN_COLD_ARCHAEOLOGY = key("archaeology/ocean_ruin_cold");
    public static final ResourceKey<LootTable> ABANDONED_CAMP_BARREL = key("barrels/abandoned_camp_barrel");
    public static final ResourceKey<LootTable> ABANDONED_CAMP_COMMON_CHEST = key("chests/abandoned_camp_common_chest");
    public static final ResourceKey<LootTable> ABANDONED_CAMP_SECRET_CHEST = key("chests/abandoned_camp_secret_chest");

    private BuiltInLootTables() {}

    private static ResourceKey<LootTable> key(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace(path));
    }
}
