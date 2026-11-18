package net.minecraft.client.gui.components.debug;

import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code DebugScreenEntries}: the game's debug entries (F3's lines and its renderers: hitboxes,
 * chunk borders...), by their ids. Horizon draws them; {@link DebugScreenEntryList} says which show.
 */
public final class DebugScreenEntries {
    public static final Identifier GAME_VERSION = Identifier.withDefaultNamespace("game_version");
    public static final Identifier FPS = Identifier.withDefaultNamespace("fps");
    public static final Identifier TPS = Identifier.withDefaultNamespace("tps");
    public static final Identifier MEMORY = Identifier.withDefaultNamespace("memory");
    public static final Identifier DETAILED_MEMORY = Identifier.withDefaultNamespace("detailed_memory");
    public static final Identifier SYSTEM_SPECS = Identifier.withDefaultNamespace("system_specs");
    public static final Identifier LOOKING_AT_BLOCK_STATE = Identifier.withDefaultNamespace("looking_at_block_state");
    public static final Identifier LOOKING_AT_BLOCK_TAGS = Identifier.withDefaultNamespace("looking_at_block_tags");
    public static final Identifier LOOKING_AT_FLUID_STATE = Identifier.withDefaultNamespace("looking_at_fluid_state");
    public static final Identifier LOOKING_AT_FLUID_TAGS = Identifier.withDefaultNamespace("looking_at_fluid_tags");
    public static final Identifier LOOKING_AT_ENTITY = Identifier.withDefaultNamespace("looking_at_entity");
    public static final Identifier LOOKING_AT_ENTITY_TAGS = Identifier.withDefaultNamespace("looking_at_entity_tags");
    public static final Identifier CHUNK_RENDER_STATS = Identifier.withDefaultNamespace("chunk_render_stats");
    public static final Identifier CHUNK_GENERATION_STATS = Identifier.withDefaultNamespace("chunk_generation_stats");
    public static final Identifier ENTITY_RENDER_STATS = Identifier.withDefaultNamespace("entity_render_stats");
    public static final Identifier PARTICLE_RENDER_STATS = Identifier.withDefaultNamespace("particle_render_stats");
    public static final Identifier CHUNK_SOURCE_STATS = Identifier.withDefaultNamespace("chunk_source_stats");
    public static final Identifier PLAYER_POSITION = Identifier.withDefaultNamespace("player_position");
    public static final Identifier PLAYER_SECTION_POSITION = Identifier.withDefaultNamespace("player_section_position");
    public static final Identifier PLAYER_SPEED = Identifier.withDefaultNamespace("player_speed");
    public static final Identifier LIGHT_LEVELS = Identifier.withDefaultNamespace("light_levels");
    public static final Identifier HEIGHTMAP = Identifier.withDefaultNamespace("heightmap");
    public static final Identifier BIOME = Identifier.withDefaultNamespace("biome");
    public static final Identifier LOCAL_DIFFICULTY = Identifier.withDefaultNamespace("local_difficulty");
    public static final Identifier DAY_COUNT = Identifier.withDefaultNamespace("day_count");
    public static final Identifier ENTITY_SPAWN_COUNTS = Identifier.withDefaultNamespace("entity_spawn_counts");
    public static final Identifier SOUND_MOOD = Identifier.withDefaultNamespace("sound_mood");
    public static final Identifier SOUND_CACHE = Identifier.withDefaultNamespace("sound_cache");
    public static final Identifier POST_EFFECTS = Identifier.withDefaultNamespace("post_effects");
    public static final Identifier ENTITY_HITBOXES = Identifier.withDefaultNamespace("entity_hitboxes");
    public static final Identifier CHUNK_BORDERS = Identifier.withDefaultNamespace("chunk_borders");
    public static final Identifier THREE_DIMENSIONAL_CROSSHAIR = Identifier.withDefaultNamespace("3d_crosshair");
    public static final Identifier CHUNK_SECTION_PATHS = Identifier.withDefaultNamespace("chunk_section_paths");
    public static final Identifier GPU_UTILIZATION = Identifier.withDefaultNamespace("gpu_utilization");
    public static final Identifier SIMPLE_PERFORMANCE_IMPACTORS = Identifier.withDefaultNamespace("simple_performance_impactors");
    public static final Identifier CHUNK_SECTION_OCTREE = Identifier.withDefaultNamespace("chunk_section_octree");
    public static final Identifier VISUALIZE_WATER_LEVELS = Identifier.withDefaultNamespace("visualize_water_levels");
    public static final Identifier VISUALIZE_HEIGHTMAP = Identifier.withDefaultNamespace("visualize_heightmap");
    public static final Identifier VISUALIZE_COLLISION_BOXES = Identifier.withDefaultNamespace("visualize_collision_boxes");
    public static final Identifier VISUALIZE_ENTITY_SUPPORTING_BLOCKS = Identifier.withDefaultNamespace("visualize_entity_supporting_blocks");
    public static final Identifier VISUALIZE_BLOCK_LIGHT_LEVELS = Identifier.withDefaultNamespace("visualize_block_light_levels");
    public static final Identifier VISUALIZE_SKY_LIGHT_LEVELS = Identifier.withDefaultNamespace("visualize_sky_light_levels");
    public static final Identifier VISUALIZE_SOLID_FACES = Identifier.withDefaultNamespace("visualize_solid_faces");
    public static final Identifier VISUALIZE_CHUNKS_ON_SERVER = Identifier.withDefaultNamespace("visualize_chunks_on_server");
    public static final Identifier VISUALIZE_SKY_LIGHT_SECTIONS = Identifier.withDefaultNamespace("visualize_sky_light_sections");
    public static final Identifier CHUNK_SECTION_VISIBILITY = Identifier.withDefaultNamespace("chunk_section_visibility");

    private DebugScreenEntries() {}
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static final java.util.Map<net.minecraft.resources.Identifier, java.lang.Object> ENTRIES_BY_ID = null;
    public static final java.util.Map<java.lang.Object, java.util.Map<net.minecraft.resources.Identifier, net.minecraft.client.gui.components.debug.DebugScreenEntryStatus>> PROFILES = null;
    public static net.minecraft.resources.Identifier register(java.lang.String p0, java.lang.Object p1) { kiln.Missing.called("net.minecraft.client.gui.components.debug.DebugScreenEntries.register"); return null; }
    public static net.minecraft.resources.Identifier register(net.minecraft.resources.Identifier p0, java.lang.Object p1) { kiln.Missing.called("net.minecraft.client.gui.components.debug.DebugScreenEntries.register"); return null; }
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
    public static net.minecraft.resources.Identifier register(java.lang.String p0, net.minecraft.client.gui.components.debug.DebugScreenEntry p1) { kiln.Missing.called("net.minecraft.client.gui.components.debug.DebugScreenEntries.register"); return null; }
    public static net.minecraft.resources.Identifier register(net.minecraft.resources.Identifier p0, net.minecraft.client.gui.components.debug.DebugScreenEntry p1) { kiln.Missing.called("net.minecraft.client.gui.components.debug.DebugScreenEntries.register"); return null; }
    // End of what tools/kiln-stubs.py generated.
}
