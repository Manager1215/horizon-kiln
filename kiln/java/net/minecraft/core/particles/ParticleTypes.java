package net.minecraft.core.particles;

/**
 * Kiln's stand-in for {@code ParticleTypes}: the game's particle types that need nothing more, by their ids. A
 * client level adds one through the game ({@code ClientLevel.addParticle}). The ones spawned however far away are
 * those the game's own list says.
 */
public final class ParticleTypes {
    public static final SimpleParticleType ANGRY_VILLAGER = SimpleParticleType.kiln$register("angry_villager", false);
    public static final SimpleParticleType BUBBLE = SimpleParticleType.kiln$register("bubble", false);
    public static final SimpleParticleType SULFUR_BUBBLES = SimpleParticleType.kiln$register("sulfur_bubbles", false);
    public static final SimpleParticleType NOXIOUS_GAS = SimpleParticleType.kiln$register("noxious_gas", false);
    public static final SimpleParticleType NOXIOUS_GAS_CLOUD = SimpleParticleType.kiln$register("noxious_gas_cloud", false);
    public static final SimpleParticleType CLOUD = SimpleParticleType.kiln$register("cloud", false);
    public static final SimpleParticleType COPPER_FIRE_FLAME = SimpleParticleType.kiln$register("copper_fire_flame", false);
    public static final SimpleParticleType CRIT = SimpleParticleType.kiln$register("crit", false);
    public static final SimpleParticleType DAMAGE_INDICATOR = SimpleParticleType.kiln$register("damage_indicator", true);
    public static final SimpleParticleType DRIPPING_LAVA = SimpleParticleType.kiln$register("dripping_lava", false);
    public static final SimpleParticleType FALLING_LAVA = SimpleParticleType.kiln$register("falling_lava", false);
    public static final SimpleParticleType LANDING_LAVA = SimpleParticleType.kiln$register("landing_lava", false);
    public static final SimpleParticleType DRIPPING_WATER = SimpleParticleType.kiln$register("dripping_water", false);
    public static final SimpleParticleType FALLING_WATER = SimpleParticleType.kiln$register("falling_water", false);
    public static final SimpleParticleType ELDER_GUARDIAN = SimpleParticleType.kiln$register("elder_guardian", false);
    public static final SimpleParticleType ENCHANTED_HIT = SimpleParticleType.kiln$register("enchanted_hit", false);
    public static final SimpleParticleType ENCHANT = SimpleParticleType.kiln$register("enchant", false);
    public static final SimpleParticleType END_ROD = SimpleParticleType.kiln$register("end_rod", false);
    public static final SimpleParticleType EXPLOSION_EMITTER = SimpleParticleType.kiln$register("explosion_emitter", true);
    public static final SimpleParticleType EXPLOSION = SimpleParticleType.kiln$register("explosion", true);
    public static final SimpleParticleType GUST = SimpleParticleType.kiln$register("gust", true);
    public static final SimpleParticleType SMALL_GUST = SimpleParticleType.kiln$register("small_gust", false);
    public static final SimpleParticleType GUST_EMITTER_LARGE = SimpleParticleType.kiln$register("gust_emitter_large", true);
    public static final SimpleParticleType GUST_EMITTER_SMALL = SimpleParticleType.kiln$register("gust_emitter_small", true);
    public static final SimpleParticleType SONIC_BOOM = SimpleParticleType.kiln$register("sonic_boom", true);
    public static final SimpleParticleType FIREWORK = SimpleParticleType.kiln$register("firework", false);
    public static final SimpleParticleType FISHING = SimpleParticleType.kiln$register("fishing", false);
    public static final SimpleParticleType FLAME = SimpleParticleType.kiln$register("flame", false);
    public static final SimpleParticleType INFESTED = SimpleParticleType.kiln$register("infested", false);
    public static final SimpleParticleType CHERRY_LEAVES = SimpleParticleType.kiln$register("cherry_leaves", false);
    public static final SimpleParticleType PALE_OAK_LEAVES = SimpleParticleType.kiln$register("pale_oak_leaves", false);
    public static final SimpleParticleType RED_POPLAR_LEAVES = SimpleParticleType.kiln$register("red_poplar_leaves", false);
    public static final SimpleParticleType ORANGE_POPLAR_LEAVES = SimpleParticleType.kiln$register("orange_poplar_leaves", false);
    public static final SimpleParticleType YELLOW_POPLAR_LEAVES = SimpleParticleType.kiln$register("yellow_poplar_leaves", false);
    public static final SimpleParticleType SCULK_SOUL = SimpleParticleType.kiln$register("sculk_soul", false);
    public static final SimpleParticleType SCULK_CHARGE_POP = SimpleParticleType.kiln$register("sculk_charge_pop", true);
    public static final SimpleParticleType SOUL_FIRE_FLAME = SimpleParticleType.kiln$register("soul_fire_flame", false);
    public static final SimpleParticleType SOUL = SimpleParticleType.kiln$register("soul", false);
    public static final SimpleParticleType HAPPY_VILLAGER = SimpleParticleType.kiln$register("happy_villager", false);
    public static final SimpleParticleType COMPOSTER = SimpleParticleType.kiln$register("composter", false);
    public static final SimpleParticleType HEART = SimpleParticleType.kiln$register("heart", false);
    public static final SimpleParticleType PAUSE_MOB_GROWTH = SimpleParticleType.kiln$register("pause_mob_growth", false);
    public static final SimpleParticleType RESET_MOB_GROWTH = SimpleParticleType.kiln$register("reset_mob_growth", false);
    public static final SimpleParticleType ITEM_SLIME = SimpleParticleType.kiln$register("item_slime", false);
    public static final SimpleParticleType ITEM_COBWEB = SimpleParticleType.kiln$register("item_cobweb", false);
    public static final SimpleParticleType ITEM_SNOWBALL = SimpleParticleType.kiln$register("item_snowball", false);
    public static final SimpleParticleType LARGE_SMOKE = SimpleParticleType.kiln$register("large_smoke", false);
    public static final SimpleParticleType LAVA = SimpleParticleType.kiln$register("lava", false);
    public static final SimpleParticleType MYCELIUM = SimpleParticleType.kiln$register("mycelium", false);
    public static final SimpleParticleType NOTE = SimpleParticleType.kiln$register("note", false);
    public static final SimpleParticleType POOF = SimpleParticleType.kiln$register("poof", true);
    public static final SimpleParticleType PORTAL = SimpleParticleType.kiln$register("portal", false);
    public static final SimpleParticleType RAIN = SimpleParticleType.kiln$register("rain", false);
    public static final SimpleParticleType SMOKE = SimpleParticleType.kiln$register("smoke", false);
    public static final SimpleParticleType WHITE_SMOKE = SimpleParticleType.kiln$register("white_smoke", false);
    public static final SimpleParticleType SNEEZE = SimpleParticleType.kiln$register("sneeze", false);
    public static final SimpleParticleType SPIT = SimpleParticleType.kiln$register("spit", true);
    public static final SimpleParticleType SQUID_INK = SimpleParticleType.kiln$register("squid_ink", true);
    public static final SimpleParticleType SWEEP_ATTACK = SimpleParticleType.kiln$register("sweep_attack", true);
    public static final SimpleParticleType TOTEM_OF_UNDYING = SimpleParticleType.kiln$register("totem_of_undying", false);
    public static final SimpleParticleType UNDERWATER = SimpleParticleType.kiln$register("underwater", false);
    public static final SimpleParticleType SPLASH = SimpleParticleType.kiln$register("splash", false);
    public static final SimpleParticleType WITCH = SimpleParticleType.kiln$register("witch", false);
    public static final SimpleParticleType BUBBLE_POP = SimpleParticleType.kiln$register("bubble_pop", false);
    public static final SimpleParticleType CURRENT_DOWN = SimpleParticleType.kiln$register("current_down", false);
    public static final SimpleParticleType BUBBLE_COLUMN_UP = SimpleParticleType.kiln$register("bubble_column_up", false);
    public static final SimpleParticleType NAUTILUS = SimpleParticleType.kiln$register("nautilus", false);
    public static final SimpleParticleType DOLPHIN = SimpleParticleType.kiln$register("dolphin", false);
    public static final SimpleParticleType CAMPFIRE_COSY_SMOKE = SimpleParticleType.kiln$register("campfire_cosy_smoke", true);
    public static final SimpleParticleType CAMPFIRE_SIGNAL_SMOKE = SimpleParticleType.kiln$register("campfire_signal_smoke", true);
    public static final SimpleParticleType DRIPPING_HONEY = SimpleParticleType.kiln$register("dripping_honey", false);
    public static final SimpleParticleType FALLING_HONEY = SimpleParticleType.kiln$register("falling_honey", false);
    public static final SimpleParticleType LANDING_HONEY = SimpleParticleType.kiln$register("landing_honey", false);
    public static final SimpleParticleType FALLING_NECTAR = SimpleParticleType.kiln$register("falling_nectar", false);
    public static final SimpleParticleType FALLING_SPORE_BLOSSOM = SimpleParticleType.kiln$register("falling_spore_blossom", false);
    public static final SimpleParticleType ASH = SimpleParticleType.kiln$register("ash", false);
    public static final SimpleParticleType CRIMSON_SPORE = SimpleParticleType.kiln$register("crimson_spore", false);
    public static final SimpleParticleType WARPED_SPORE = SimpleParticleType.kiln$register("warped_spore", false);
    public static final SimpleParticleType SPORE_BLOSSOM_AIR = SimpleParticleType.kiln$register("spore_blossom_air", false);
    public static final SimpleParticleType DRIPPING_OBSIDIAN_TEAR = SimpleParticleType.kiln$register("dripping_obsidian_tear", false);
    public static final SimpleParticleType FALLING_OBSIDIAN_TEAR = SimpleParticleType.kiln$register("falling_obsidian_tear", false);
    public static final SimpleParticleType LANDING_OBSIDIAN_TEAR = SimpleParticleType.kiln$register("landing_obsidian_tear", false);
    public static final SimpleParticleType REVERSE_PORTAL = SimpleParticleType.kiln$register("reverse_portal", false);
    public static final SimpleParticleType WHITE_ASH = SimpleParticleType.kiln$register("white_ash", false);
    public static final SimpleParticleType SMALL_FLAME = SimpleParticleType.kiln$register("small_flame", false);
    public static final SimpleParticleType SNOWFLAKE = SimpleParticleType.kiln$register("snowflake", false);
    public static final SimpleParticleType DRIPPING_DRIPSTONE_LAVA = SimpleParticleType.kiln$register("dripping_dripstone_lava", false);
    public static final SimpleParticleType FALLING_DRIPSTONE_LAVA = SimpleParticleType.kiln$register("falling_dripstone_lava", false);
    public static final SimpleParticleType DRIPPING_DRIPSTONE_WATER = SimpleParticleType.kiln$register("dripping_dripstone_water", false);
    public static final SimpleParticleType FALLING_DRIPSTONE_WATER = SimpleParticleType.kiln$register("falling_dripstone_water", false);
    public static final SimpleParticleType GLOW_SQUID_INK = SimpleParticleType.kiln$register("glow_squid_ink", true);
    public static final SimpleParticleType GLOW = SimpleParticleType.kiln$register("glow", true);
    public static final SimpleParticleType WAX_ON = SimpleParticleType.kiln$register("wax_on", true);
    public static final SimpleParticleType WAX_OFF = SimpleParticleType.kiln$register("wax_off", true);
    public static final SimpleParticleType ELECTRIC_SPARK = SimpleParticleType.kiln$register("electric_spark", true);
    public static final SimpleParticleType SCRAPE = SimpleParticleType.kiln$register("scrape", true);
    public static final SimpleParticleType EGG_CRACK = SimpleParticleType.kiln$register("egg_crack", false);
    public static final SimpleParticleType DUST_PLUME = SimpleParticleType.kiln$register("dust_plume", false);
    public static final SimpleParticleType TRIAL_SPAWNER_DETECTED_PLAYER = SimpleParticleType.kiln$register("trial_spawner_detected_player", false);
    public static final SimpleParticleType TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS = SimpleParticleType.kiln$register("trial_spawner_detected_player_ominous", false);
    public static final SimpleParticleType VAULT_CONNECTION = SimpleParticleType.kiln$register("vault_connection", true);
    public static final SimpleParticleType OMINOUS_SPAWNING = SimpleParticleType.kiln$register("ominous_spawning", true);
    public static final SimpleParticleType RAID_OMEN = SimpleParticleType.kiln$register("raid_omen", false);
    public static final SimpleParticleType TRIAL_OMEN = SimpleParticleType.kiln$register("trial_omen", false);
    public static final SimpleParticleType FIREFLY = SimpleParticleType.kiln$register("firefly", false);
    public static final SimpleParticleType SULFUR_CUBE_GOO = SimpleParticleType.kiln$register("sulfur_cube_goo", false);

    private ParticleTypes() {}
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static final net.minecraft.core.particles.ParticleType<net.minecraft.core.particles.ColorParticleOption> TINTED_LEAVES = null;
    // End of what tools/kiln-stubs.py generated.
}
