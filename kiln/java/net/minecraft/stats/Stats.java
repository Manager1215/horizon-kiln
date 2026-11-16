package net.minecraft.stats;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Kiln's stand-in for {@code Stats}: the game's statistic types and its custom statistics, by the ids it keeps them
 * under. A player's values are Horizon's ({@code ServerPlayer.getStats}).
 */
public final class Stats {
    public static final StatType<Block> BLOCK_MINED = new StatType<>("minecraft:mined");
    public static final StatType<Item> ITEM_CRAFTED = new StatType<>("minecraft:crafted");
    public static final StatType<Item> ITEM_USED = new StatType<>("minecraft:used");
    public static final StatType<Item> ITEM_BROKEN = new StatType<>("minecraft:broken");
    public static final StatType<Item> ITEM_PICKED_UP = new StatType<>("minecraft:picked_up");
    public static final StatType<Item> ITEM_DROPPED = new StatType<>("minecraft:dropped");
    public static final StatType<EntityType<?>> ENTITY_KILLED = new StatType<>("minecraft:killed");
    public static final StatType<EntityType<?>> ENTITY_KILLED_BY = new StatType<>("minecraft:killed_by");
    public static final StatType<Identifier> CUSTOM = new StatType<>("minecraft:custom");
    public static final Identifier LEAVE_GAME = makeCustomStat("leave_game");
    public static final Identifier PLAY_TIME = makeCustomStat("play_time");
    public static final Identifier TOTAL_WORLD_TIME = makeCustomStat("total_world_time");
    public static final Identifier TIME_SINCE_DEATH = makeCustomStat("time_since_death");
    public static final Identifier TIME_SINCE_REST = makeCustomStat("time_since_rest");
    public static final Identifier SNEAK_TIME = makeCustomStat("sneak_time");
    public static final Identifier WALK_ONE_CM = makeCustomStat("walk_one_cm");
    public static final Identifier CROUCH_ONE_CM = makeCustomStat("crouch_one_cm");
    public static final Identifier SPRINT_ONE_CM = makeCustomStat("sprint_one_cm");
    public static final Identifier WALK_ON_WATER_ONE_CM = makeCustomStat("walk_on_water_one_cm");
    public static final Identifier FALL_ONE_CM = makeCustomStat("fall_one_cm");
    public static final Identifier CLIMB_ONE_CM = makeCustomStat("climb_one_cm");
    public static final Identifier FLY_ONE_CM = makeCustomStat("fly_one_cm");
    public static final Identifier WALK_UNDER_WATER_ONE_CM = makeCustomStat("walk_under_water_one_cm");
    public static final Identifier MINECART_ONE_CM = makeCustomStat("minecart_one_cm");
    public static final Identifier BOAT_ONE_CM = makeCustomStat("boat_one_cm");
    public static final Identifier PIG_ONE_CM = makeCustomStat("pig_one_cm");
    public static final Identifier HORSE_ONE_CM = makeCustomStat("horse_one_cm");
    public static final Identifier AVIATE_ONE_CM = makeCustomStat("aviate_one_cm");
    public static final Identifier SWIM_ONE_CM = makeCustomStat("swim_one_cm");
    public static final Identifier STRIDER_ONE_CM = makeCustomStat("strider_one_cm");
    public static final Identifier JUMP = makeCustomStat("jump");
    public static final Identifier DROP = makeCustomStat("drop");
    public static final Identifier DAMAGE_DEALT = makeCustomStat("damage_dealt");
    public static final Identifier DAMAGE_DEALT_ABSORBED = makeCustomStat("damage_dealt_absorbed");
    public static final Identifier DAMAGE_DEALT_RESISTED = makeCustomStat("damage_dealt_resisted");
    public static final Identifier DAMAGE_TAKEN = makeCustomStat("damage_taken");
    public static final Identifier DAMAGE_BLOCKED_BY_SHIELD = makeCustomStat("damage_blocked_by_shield");
    public static final Identifier DAMAGE_ABSORBED = makeCustomStat("damage_absorbed");
    public static final Identifier DAMAGE_RESISTED = makeCustomStat("damage_resisted");
    public static final Identifier DEATHS = makeCustomStat("deaths");
    public static final Identifier MOB_KILLS = makeCustomStat("mob_kills");
    public static final Identifier ANIMALS_BRED = makeCustomStat("animals_bred");
    public static final Identifier PLAYER_KILLS = makeCustomStat("player_kills");
    public static final Identifier FISH_CAUGHT = makeCustomStat("fish_caught");
    public static final Identifier TALKED_TO_VILLAGER = makeCustomStat("talked_to_villager");
    public static final Identifier TRADED_WITH_VILLAGER = makeCustomStat("traded_with_villager");
    public static final Identifier EAT_CAKE_SLICE = makeCustomStat("eat_cake_slice");
    public static final Identifier FILL_CAULDRON = makeCustomStat("fill_cauldron");
    public static final Identifier USE_CAULDRON = makeCustomStat("use_cauldron");
    public static final Identifier CLEAN_ARMOR = makeCustomStat("clean_armor");
    public static final Identifier CLEAN_BANNER = makeCustomStat("clean_banner");
    public static final Identifier CLEAN_SHULKER_BOX = makeCustomStat("clean_shulker_box");
    public static final Identifier INTERACT_WITH_BREWINGSTAND = makeCustomStat("interact_with_brewingstand");
    public static final Identifier INTERACT_WITH_BEACON = makeCustomStat("interact_with_beacon");
    public static final Identifier INSPECT_DROPPER = makeCustomStat("inspect_dropper");
    public static final Identifier INSPECT_HOPPER = makeCustomStat("inspect_hopper");
    public static final Identifier INSPECT_DISPENSER = makeCustomStat("inspect_dispenser");
    public static final Identifier PLAY_NOTEBLOCK = makeCustomStat("play_noteblock");
    public static final Identifier TUNE_NOTEBLOCK = makeCustomStat("tune_noteblock");
    public static final Identifier POT_FLOWER = makeCustomStat("pot_flower");
    public static final Identifier TRIGGER_TRAPPED_CHEST = makeCustomStat("trigger_trapped_chest");
    public static final Identifier OPEN_ENDERCHEST = makeCustomStat("open_enderchest");
    public static final Identifier ENCHANT_ITEM = makeCustomStat("enchant_item");
    public static final Identifier PLAY_RECORD = makeCustomStat("play_record");
    public static final Identifier INTERACT_WITH_FURNACE = makeCustomStat("interact_with_furnace");
    public static final Identifier INTERACT_WITH_CRAFTING_TABLE = makeCustomStat("interact_with_crafting_table");
    public static final Identifier OPEN_CHEST = makeCustomStat("open_chest");
    public static final Identifier SLEEP_IN_BED = makeCustomStat("sleep_in_bed");
    public static final Identifier OPEN_SHULKER_BOX = makeCustomStat("open_shulker_box");
    public static final Identifier OPEN_BARREL = makeCustomStat("open_barrel");
    public static final Identifier INTERACT_WITH_BLAST_FURNACE = makeCustomStat("interact_with_blast_furnace");
    public static final Identifier INTERACT_WITH_SMOKER = makeCustomStat("interact_with_smoker");
    public static final Identifier INTERACT_WITH_LECTERN = makeCustomStat("interact_with_lectern");
    public static final Identifier INTERACT_WITH_CAMPFIRE = makeCustomStat("interact_with_campfire");
    public static final Identifier INTERACT_WITH_CARTOGRAPHY_TABLE = makeCustomStat("interact_with_cartography_table");
    public static final Identifier INTERACT_WITH_LOOM = makeCustomStat("interact_with_loom");
    public static final Identifier INTERACT_WITH_STONECUTTER = makeCustomStat("interact_with_stonecutter");
    public static final Identifier BELL_RING = makeCustomStat("bell_ring");
    public static final Identifier RAID_TRIGGER = makeCustomStat("raid_trigger");
    public static final Identifier RAID_WIN = makeCustomStat("raid_win");
    public static final Identifier INTERACT_WITH_ANVIL = makeCustomStat("interact_with_anvil");
    public static final Identifier INTERACT_WITH_GRINDSTONE = makeCustomStat("interact_with_grindstone");
    public static final Identifier TARGET_HIT = makeCustomStat("target_hit");
    public static final Identifier INTERACT_WITH_SMITHING_TABLE = makeCustomStat("interact_with_smithing_table");

    private Stats() {}

    private static Identifier makeCustomStat(String name) {
        return Identifier.withDefaultNamespace(name);
    }
}
