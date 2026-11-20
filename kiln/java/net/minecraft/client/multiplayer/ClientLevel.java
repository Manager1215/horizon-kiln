package net.minecraft.client.multiplayer;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code ClientLevel}: the dimension the player is in, as the window's game said with the tick:
 * its time of day, weather and height. Of its entities Kiln knows the window's player and the mobs and animals the
 * game has near them (D-211).
 */
public class ClientLevel extends Level {
    /**
     * The window's level, made as vanilla's is (its constructor's arguments, D-215: the mods hook its end), of the
     * connection joined; what else they say the game keeps. Kiln makes it with the connection alone.
     */
    public ClientLevel(ClientPacketListener connection, ClientLevelData data,
            net.minecraft.resources.ResourceKey<Level> dimension,
            net.minecraft.core.Holder<net.minecraft.world.level.dimension.DimensionType> type, int viewDistance,
            int simulationDistance, net.minecraft.client.renderer.extract.LevelExtractor extractor, boolean debug,
            long seed, int seaLevel) {
        this.connection = connection;
    }

    private long dayTime;
    private float rainLevel;
    private float thunderLevel;
    private int minY = -64;
    private int height = 384;

    /** What the client knows of the recipes: the ones synchronized on joining (D-152, D-165). */
    public net.minecraft.world.item.crafting.RecipeAccess recipeAccess() {
        return RECIPES;
    }

    static final net.minecraft.world.item.crafting.RecipeAccess RECIPES =
            new net.minecraft.world.item.crafting.RecipeAccess() {};

    @Override
    public long getGameTime() {
        return super.getGameTime();
    }

    @Override
    public boolean isClientSide() {
        return true;
    }

    /** How much it rains, 0 to 1, as the window's game said. */
    @Override
    public float getRainLevel(float partialTick) {
        return rainLevel;
    }

    /** How much it thunders, 0 to 1 (and no more than it rains). */
    @Override
    public float getThunderLevel(float partialTick) {
        return thunderLevel * rainLevel;
    }

    /** The time of day in ticks, counting past 24000. */
    public long getDefaultClockTime() {
        return dayTime;
    }

    /** How dark the sky is, 0 (day) to 11 (night), from the time of day and the weather, as vanilla works it out. */
    public int getSkyDarken() {
        double day = Mth.frac(dayTime / 24000.0 - 0.25);
        float angle = (float) (day * 2.0 + (0.5 - Math.cos(day * Math.PI) / 2.0)) / 3.0F;
        float light = 1.0F - (Mth.cos(angle * Mth.TWO_PI) * 2.0F + 0.5F);
        light = 1.0F - Mth.clamp(light, 0.0F, 1.0F);
        light *= 1.0F - getRainLevel(1.0F) * 5.0F / 16.0F;
        light *= 1.0F - getThunderLevel(1.0F) * 5.0F / 16.0F;
        return (int) ((1.0F - light) * 11.0F);
    }

    /** Daylight outside: the Overworld's sky not too dark (the Nether and the End have no day). */
    public boolean isBrightOutside() {
        return dimension() == OVERWORLD && getSkyDarken() < 4;
    }

    public int getMinY() {
        return minY;
    }

    public int getHeight() {
        return height;
    }

    public int getMaxY() {
        return minY + height - 1;
    }

    /** The sea's height in the game's own dimensions. */
    public int getSeaLevel() {
        return dimension() == NETHER ? 32 : dimension() == END ? 0 : 63;
    }

    /**
     * The game's mobs near the player, by the game's number (D-211): each a living entity of its type at the game's
     * position, made when the game first lists it and removed when the game no longer does.
     */
    private final java.util.Map<Integer, Entity> mobs = new java.util.LinkedHashMap<>();

    /** The entities Kiln knows in it: the window's player, then the mobs near them. */
    public Iterable<Entity> entitiesForRendering() {
        Entity player = Minecraft.getInstance().player;
        java.util.List<Entity> all = new java.util.ArrayList<>(mobs.size() + 1);
        if (player != null) {
            all.add(player);
        }
        all.addAll(mobs.values());
        return all;
    }

    public Entity getEntity(int id) {
        Entity player = Minecraft.getInstance().player;
        return player != null && player.getId() == id ? player : mobs.get(id);
    }

    /** How many entities the level has: the player and the mobs near them, as {@link #entitiesForRendering}. */
    public int getEntityCount() {
        return mobs.size() + (Minecraft.getInstance().player != null ? 1 : 0);
    }

    /** The entities but {@code except} whose box touches {@code box} and that {@code test} takes. */
    public List<Entity> getEntities(Entity except, net.minecraft.world.phys.AABB box,
            java.util.function.Predicate<? super Entity> test) {
        List<Entity> found = new java.util.ArrayList<>();
        for (Entity e : entitiesForRendering()) {
            if (e != except && e.getBoundingBox().intersects(box) && (test == null || test.test(e))) {
                found.add(e);
            }
        }
        return found;
    }

    public List<Entity> getEntities(Entity except, net.minecraft.world.phys.AABB box) {
        return getEntities(except, box, e -> true);
    }

    public <T extends Entity> List<T> getEntities(net.minecraft.world.level.entity.EntityTypeTest<Entity, T> type,
            net.minecraft.world.phys.AABB box, java.util.function.Predicate<? super T> test) {
        List<T> found = new java.util.ArrayList<>();
        for (Entity e : entitiesForRendering()) {
            T t = type.tryCast(e);
            if (t != null && e.getBoundingBox().intersects(box) && (test == null || test.test(t))) {
                found.add(t);
            }
        }
        return found;
    }

    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> type, net.minecraft.world.phys.AABB box,
            java.util.function.Predicate<? super T> test) {
        List<T> found = new java.util.ArrayList<>();
        for (Entity e : entitiesForRendering()) {
            if (type.isInstance(e) && e.getBoundingBox().intersects(box) && (test == null || test.test(type.cast(e)))) {
                found.add(type.cast(e));
            }
        }
        return found;
    }

    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> type, net.minecraft.world.phys.AABB box) {
        return getEntitiesOfClass(type, box, e -> true);
    }

    /**
     * Kiln's own: the game's mobs near the player now, {@code <number> <type> <x> <y> <z>} joined by {@code ,}: one
     * new to the level is made and loaded (Fabric's {@code ClientEntityEvents.ENTITY_LOAD}), one gone is removed and
     * unloaded, the rest move. A type Kiln doesn't know (a typo, a type of a mod that isn't here) is left out.
     */
    public void kiln$mobs(String field) {
        java.util.Map<Integer, String[]> now = new java.util.LinkedHashMap<>();
        if (!field.isEmpty()) {
            for (String entry : field.split(",")) {
                String[] f = entry.split(" ");
                if (f.length == 5) {
                    try {
                        now.put(Integer.parseInt(f[0]), f);
                    } catch (NumberFormatException e) {
                        // Left out.
                    }
                }
            }
        }
        java.util.Iterator<java.util.Map.Entry<Integer, Entity>> it = mobs.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Integer, Entity> e = it.next();
            if (!now.containsKey(e.getKey())) {
                it.remove();
                e.getValue().kiln$removed();
                net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_UNLOAD.invoker()
                        .onUnload(e.getValue(), this);
            }
        }
        for (java.util.Map.Entry<Integer, String[]> e : now.entrySet()) {
            String[] f = e.getValue();
            double x, y, z;
            try {
                x = Double.parseDouble(f[2]);
                y = Double.parseDouble(f[3]);
                z = Double.parseDouble(f[4]);
            } catch (NumberFormatException bad) {
                continue;
            }
            Entity mob = mobs.get(e.getKey());
            if (mob == null) {
                net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(
                        java.net.URLDecoder.decode(f[1], java.nio.charset.StandardCharsets.UTF_8));
                if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                    continue;
                }
                mob = net.minecraft.world.entity.EntityType.kiln$client(
                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(id), this);
                mob.setId(e.getKey());
                mob.kiln$at(x, y, z);
                mobs.put(e.getKey(), mob);
                net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_LOAD.invoker()
                        .onLoad(mob, this);
            } else {
                mob.kiln$at(x, y, z);
            }
        }
    }

    /** The players Kiln knows in it: the window's own. */
    public List<net.minecraft.client.player.LocalPlayer> players() {
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? List.of() : List.of(player);
    }

    /** The blocks around the player's feet, 3 by 3 by 3, as the game said, and where their middle is. */
    private String[] blocks = new String[0];
    private net.minecraft.core.BlockPos middle = net.minecraft.core.BlockPos.ZERO;

    /**
     * The block at {@code pos}, in its default state: Kiln knows the 27 around the player's feet; elsewhere air,
     * said once in Kiln's log.
     */
    @Override
    public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos pos) {
        int dx = pos.getX() - middle.getX();
        int dy = pos.getY() - middle.getY();
        int dz = pos.getZ() - middle.getZ();
        if (blocks.length == 27 && Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && Math.abs(dz) <= 1) {
            String name = blocks[(dx + 1) * 9 + (dy + 1) * 3 + dz + 1];
            if (!name.isEmpty()) {
                return net.minecraft.world.level.block.state.BlockState.kiln$parse(name);
            }
        }
        // Away from the player: the chunk's blocks, asked of the game (D-186).
        if (kiln$has(pos.getX() >> 4, pos.getZ() >> 4)) {
            net.minecraft.world.level.block.state.BlockState state = kiln.ClientChunks.block(pos.getX(), pos.getY(),
                    pos.getZ(), getMinY() >> 4);
            if (state != null) {
                return state;
            }
        }
        kiln.Missing.called("the blocks away from the player");
        return super.getBlockState(pos);
    }

    private net.minecraft.world.Difficulty difficulty = net.minecraft.world.Difficulty.NORMAL;
    private String biome = "";
    private int blockLight;
    private int skyLight;

    public net.minecraft.world.Difficulty getDifficulty() {
        return difficulty;
    }

    /**
     * The biome at {@code pos}: Kiln knows the one where the player stands, and gives it for any position in the
     * player's chunk; elsewhere, the same, said once in Kiln's log.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public net.minecraft.core.Holder getBiome(net.minecraft.core.BlockPos pos) {
        Entity player = Minecraft.getInstance().player;
        if (player != null && (pos.getX() >> 4 != player.blockPosition().getX() >> 4
                || pos.getZ() >> 4 != player.blockPosition().getZ() >> 4)) {
            kiln.Missing.called("the biome away from the player's chunk");
        }
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(
                biome.isEmpty() ? "minecraft:plains" : biome.contains(":") ? biome : "minecraft:" + biome);
        net.minecraft.resources.ResourceKey key = net.minecraft.resources.ResourceKey.create(
                net.minecraft.resources.ResourceKey.createRegistryKey(
                        net.minecraft.resources.Identifier.withDefaultNamespace("worldgen/biome")), id);
        return net.minecraft.core.Holder.Reference.kiln$of(key, null);
    }

    /**
     * The light at {@code pos}, 0 to 15: the brighter of the block light and the sky's, darkened by the night and
     * the weather, as vanilla's {@code getMaxLocalRawBrightness}. Kiln knows the light where the player stands;
     * elsewhere it gives 0, said once in Kiln's log.
     */
    public int getMaxLocalRawBrightness(net.minecraft.core.BlockPos pos) {
        Entity player = Minecraft.getInstance().player;
        if (player == null || !player.blockPosition().equals(pos)) {
            kiln.Missing.called("the light away from where the player stands");
            return 0;
        }
        return Math.max(blockLight, skyLight - getSkyDarken());
    }

    /**
     * The light of a layer at {@code pos}: where the player stands, the block light or sky light the game said (a light
     * level HUD reads them); elsewhere, as Kiln's level guesses.
     */
    @Override
    public int getBrightness(net.minecraft.world.level.LightLayer layer, net.minecraft.core.BlockPos pos) {
        Entity player = Minecraft.getInstance().player;
        if (player != null && player.blockPosition().equals(pos)) {
            return layer == net.minecraft.world.level.LightLayer.SKY ? skyLight : blockLight;
        }
        return super.getBrightness(layer, pos);
    }

    /**
     * A sound the window's player hears at (x, y, z), played by the game with the next tick (Kiln doesn't hold one back
     * for its distance).
     */
    @Override
    public void playLocalSound(double x, double y, double z, net.minecraft.sounds.SoundEvent sound,
            net.minecraft.sounds.SoundSource source, float volume, float pitch, boolean distanceDelay) {
        kiln.Native.sound(sound.location().toString(), new double[] {x, y, z}, volume, pitch);
    }

    public void playLocalSound(net.minecraft.core.BlockPos pos, net.minecraft.sounds.SoundEvent sound,
            net.minecraft.sounds.SoundSource source, float volume, float pitch, boolean distanceDelay) {
        playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, source, volume, pitch, distanceDelay);
    }

    public void playLocalSound(Entity at, net.minecraft.sounds.SoundEvent sound, net.minecraft.sounds.SoundSource source,
            float volume, float pitch) {
        playLocalSound(at.getX(), at.getY(), at.getZ(), sound, source, volume, pitch, false);
    }

    /** A sound for the players near (x, y, z): on the client, the window's player hears the ones they make. */
    @Override
    public void playSound(Entity except, double x, double y, double z, net.minecraft.sounds.SoundEvent sound,
            net.minecraft.sounds.SoundSource source, float volume, float pitch) {
        if (except == Minecraft.getInstance().player) {
            playLocalSound(x, y, z, sound, source, volume, pitch, false);
        }
    }

    /**
     * A particle at (x, y, z), moving, drawn by the game with the next tick: one of the game's types that need nothing
     * more ({@link net.minecraft.core.particles.ParticleTypes}), near the player; another kind is said once in Kiln's log.
     */
    @Override
    public void addParticle(net.minecraft.core.particles.ParticleOptions particle, double x, double y, double z,
            double dx, double dy, double dz) {
        if (!(particle instanceof net.minecraft.core.particles.SimpleParticleType type)) {
            kiln.Missing.called("particles with options of their own (" + particle.getClass().getName() + ")");
            return;
        }
        net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(type);
        if (id == null) {
            kiln.Missing.called("unregistered particle types");
            return;
        }
        kiln.Native.particle(id.toString(), x, y, z, dx, dy, dz);
    }

    public void addParticle(net.minecraft.core.particles.ParticleOptions particle, boolean overrideLimiter,
            boolean alwaysShow, double x, double y, double z, double dx, double dy, double dz) {
        addParticle(particle, x, y, z, dx, dy, dz);
    }

    public void addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleOptions particle, double x, double y,
            double z, double dx, double dy, double dz) {
        addParticle(particle, x, y, z, dx, dy, dz);
    }

    public void addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleOptions particle, boolean overrideLimiter,
            double x, double y, double z, double dx, double dy, double dz) {
        addParticle(particle, x, y, z, dx, dy, dz);
    }

    /** The chunks the window's game has of this level, by {@link net.minecraft.world.level.ChunkPos#pack}. */
    private final java.util.Map<Long, net.minecraft.world.level.chunk.LevelChunk> chunks = new java.util.LinkedHashMap<>();

    /** The chunk at chunk x and z: one the game has, or an empty one (vanilla's client gives that too). */
    @Override
    public net.minecraft.world.level.chunk.LevelChunk getChunk(int x, int z) {
        net.minecraft.world.level.chunk.LevelChunk chunk = chunks.get(net.minecraft.world.level.ChunkPos.pack(x, z));
        return chunk != null ? chunk
                : net.minecraft.world.level.chunk.LevelChunk.kiln$empty(this, new net.minecraft.world.level.ChunkPos(x, z));
    }

    /**
     * The chunk at chunk x and z the game has; one it hasn't is null, or an empty one when {@code create} (vanilla's
     * client chunk cache).
     */
    public net.minecraft.world.level.chunk.ChunkAccess getChunk(int x, int z,
            net.minecraft.world.level.chunk.status.ChunkStatus status, boolean create) {
        net.minecraft.world.level.chunk.LevelChunk chunk = chunks.get(net.minecraft.world.level.ChunkPos.pack(x, z));
        if (chunk != null || !create) {
            return chunk;
        }
        return net.minecraft.world.level.chunk.LevelChunk.kiln$empty(this, new net.minecraft.world.level.ChunkPos(x, z));
    }

    public net.minecraft.world.level.chunk.LevelChunk getChunkAt(net.minecraft.core.BlockPos pos) {
        return getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** Whether the game has the chunk {@code pos} is in. */
    @Override
    public boolean isLoaded(net.minecraft.core.BlockPos pos) {
        return kiln$has(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** True for any chunk, as the game's client says (its chunks come by its packets); {@link #isLoaded} tells. */
    @Override
    public boolean hasChunk(int x, int z) {
        return true;
    }

    /** Kiln's own: whether the game has the chunk at chunk x and z. */
    private boolean kiln$has(int x, int z) {
        return chunks.containsKey(net.minecraft.world.level.ChunkPos.pack(x, z));
    }

    /** Kiln's own: the chunks that came ({@code x,z} joined by {@code ;}), then those that went, each told to mods. */
    public void kiln$chunks(String loaded, String unloaded) {
        for (long at : kiln$positions(unloaded)) {
            net.minecraft.world.level.chunk.LevelChunk chunk = chunks.remove(at);
            kiln.ClientChunks.forget(at);
            if (chunk != null) {
                net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_UNLOAD.invoker()
                        .onChunkUnload(this, chunk);
            }
        }
        for (long at : kiln$positions(loaded)) {
            net.minecraft.world.level.chunk.LevelChunk chunk = new net.minecraft.world.level.chunk.LevelChunk(this,
                    net.minecraft.world.level.ChunkPos.unpack(at));
            if (chunks.putIfAbsent(at, chunk) == null) {
                net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_LOAD.invoker()
                        .onChunkLoad(this, chunk);
            }
        }
    }

    /** Kiln's own: the level is left: each chunk it had goes, told to mods, and each mob. */
    public void kiln$unloadAll() {
        kiln$mobs("");
        for (net.minecraft.world.level.chunk.LevelChunk chunk : java.util.List.copyOf(chunks.values())) {
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_UNLOAD.invoker()
                    .onChunkUnload(this, chunk);
        }
        chunks.clear();
        kiln.ClientChunks.clear();
    }

    private static long[] kiln$positions(String field) {
        if (field.isEmpty()) {
            return new long[0];
        }
        String[] entries = field.split(";");
        long[] out = new long[entries.length];
        int n = 0;
        for (String entry : entries) {
            int comma = entry.indexOf(',');
            if (comma > 0) {
                out[n++] = net.minecraft.world.level.ChunkPos.pack(Integer.parseInt(entry.substring(0, comma)),
                        Integer.parseInt(entry.substring(comma + 1)));
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /** Kiln's own: the blocks around the player's feet the game said (27, x then y then z, from one below each). */
    public void kiln$blocks(net.minecraft.core.BlockPos feet, String blocks) {
        this.middle = feet;
        this.blocks = blocks.isEmpty() ? new String[0] : blocks.split(",", -1);
    }

    /** Kiln's own: the difficulty, and the biome and light where the player stands, as the game said. */
    public void kiln$place(String difficulty, String biome, int blockLight, int skyLight) {
        this.difficulty = net.minecraft.world.Difficulty.kiln$byKey(difficulty);
        this.biome = biome;
        this.blockLight = blockLight;
        this.skyLight = skyLight;
    }

    /** Kiln's own: the time of day, weather and height the game said. */
    public void kiln$state(long dayTime, float rain, float thunder, int minY, int height) {
        this.dayTime = dayTime;
        this.rainLevel = rain;
        this.thunderLevel = thunder;
        this.minY = minY;
        this.height = height;
    }
    /** The reason a player gives as they leave the world (a quit key's), as vanilla's. */
    public static final net.minecraft.network.chat.Component DEFAULT_QUIT_MESSAGE =
            net.minecraft.network.chat.Component.translatable("multiplayer.status.quitting");

    /** The game's tick rate: Horizon's client ticks 20 times a second and isn't frozen ({@code /tick} is a server's). */
    private final net.minecraft.world.TickRateManager tickRateManager = new net.minecraft.world.TickRateManager();

    public net.minecraft.world.TickRateManager tickRateManager() {
        return tickRateManager;
    }

    /** The world left, with a reason: the game leaves it ({@code Minecraft.kiln$askLeave}). */
    public void disconnect(net.minecraft.network.chat.Component reason) {
        net.minecraft.client.Minecraft.getInstance().kiln$askLeave();
    }

    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    private net.minecraft.world.level.entity.EntityTickList tickingEntities;
    public net.minecraft.client.multiplayer.ClientPacketListener connection;
    public net.minecraft.client.Minecraft minecraft;
    public java.util.Map<net.minecraft.world.level.saveddata.maps.MapId, net.minecraft.world.level.saveddata.maps.MapItemSavedData> mapData;
    public int skyFlashTime;
    public java.util.Deque<java.lang.Runnable> lightUpdateQueue;
    public net.minecraft.world.attribute.EnvironmentAttributeSystem environmentAttributes;
    public java.util.Set<net.minecraft.world.level.block.entity.BlockEntity> getGloballyRenderedBlockEntities() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.getGloballyRenderedBlockEntities"); return null; }
    public net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler getBlockStatePredictionHandler() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.getBlockStatePredictionHandler"); return null; }
    public net.minecraft.world.attribute.EnvironmentAttributeSystem.Builder addEnvironmentAttributeLayers(net.minecraft.world.attribute.EnvironmentAttributeSystem.Builder p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.addEnvironmentAttributeLayers"); return null; }
    public void queueLightUpdate(java.lang.Runnable p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.queueLightUpdate"); }
    public void pollLightUpdates() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.pollLightUpdates"); }
    public java.lang.Object endFlashState() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.endFlashState"); return null; }
    public it.unimi.dsi.fastutil.longs.Long2ObjectMap<java.util.SortedSet<net.minecraft.server.level.BlockDestructionProgress>> destructionProgress() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.destructionProgress"); return null; }
    public void tickNonPassenger(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.tickNonPassenger"); }
    public void update() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.update"); }
    public void onChunkLoaded(net.minecraft.world.level.ChunkPos p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.onChunkLoaded"); }
    public void clearTintCaches() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.clearTintCaches"); }
    public void addEntity(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.addEntity"); }
    public void removeEntity(int p0, net.minecraft.world.entity.Entity.RemovalReason p1) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.removeEntity"); }
    public net.minecraft.client.ClientClockManager clockManager() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.clockManager"); return null; }
    public net.minecraft.client.multiplayer.ClientChunkCache getChunkSource() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.getChunkSource"); return null; }
    public net.minecraft.world.level.saveddata.maps.MapItemSavedData getMapData(net.minecraft.world.level.saveddata.maps.MapId p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.getMapData"); return null; }
    public net.minecraft.world.scores.Scoreboard getScoreboard() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.getScoreboard"); return null; }
    public void setSectionDirtyWithNeighbors(int p0, int p1, int p2) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.setSectionDirtyWithNeighbors"); }
    public void levelEvent(net.minecraft.world.entity.Entity p0, int p1, net.minecraft.core.BlockPos p2, int p3) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.levelEvent"); }
    public net.minecraft.server.level.ParticleStatus calculateParticleLevel(boolean p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.calculateParticleLevel"); return null; }
    public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getUncachedNoiseBiome(int p0, int p1, int p2) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.getUncachedNoiseBiome"); return null; }
    public void setSkyFlashTime(int p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.setSkyFlashTime"); }
    public net.minecraft.world.level.CardinalLighting cardinalLighting() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.cardinalLighting"); return new net.minecraft.world.level.CardinalLighting((float) 0.0F, (float) 0.0F, (float) 0.0F, (float) 0.0F, (float) 0.0F, (float) 0.0F); }
    public int getBlockTint(net.minecraft.core.BlockPos p0, net.minecraft.world.level.ColorResolver p1) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.getBlockTint"); return 0; }
    public net.minecraft.world.level.storage.LevelData.RespawnData getRespawnData() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.getRespawnData"); return new net.minecraft.world.level.storage.LevelData.RespawnData((net.minecraft.core.GlobalPos) null, (float) 0.0F, (float) 0.0F); }
    public net.minecraft.client.multiplayer.ClientLevel.ClientLevelData getLevelData() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.getLevelData"); return null; }
    protected java.util.Map<net.minecraft.world.level.saveddata.maps.MapId, net.minecraft.world.level.saveddata.maps.MapItemSavedData> getAllMapData() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.getAllMapData"); return null; }
    public void addDestroyBlockEffect(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.state.BlockState p1) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.addDestroyBlockEffect"); }
    public int getServerSimulationDistance() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.getServerSimulationDistance"); return 0; }
    public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.enabledFeatures"); return null; }
    public static class ClientLevelData implements net.minecraft.world.level.storage.WritableLevelData {
        public boolean isFlat;
        public ClientLevelData(net.minecraft.world.Difficulty p0, boolean p1, boolean p2) {  }
        public net.minecraft.world.level.storage.LevelData.RespawnData getRespawnData() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.ClientLevelData.getRespawnData"); return new net.minecraft.world.level.storage.LevelData.RespawnData((net.minecraft.core.GlobalPos) null, (float) 0.0F, (float) 0.0F); }
        public long getGameTime() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.ClientLevelData.getGameTime"); return 0L; }
        public void setSpawn(net.minecraft.world.level.storage.LevelData.RespawnData p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.ClientLevelData.setSpawn"); }
        public boolean isHardcore() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.ClientLevelData.isHardcore"); return false; }
        public net.minecraft.world.Difficulty getDifficulty() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.ClientLevelData.getDifficulty"); return null; }
        public boolean isDifficultyLocked() { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.ClientLevelData.isDifficultyLocked"); return false; }
        public double getHorizonHeight(net.minecraft.world.level.LevelHeightAccessor p0) { kiln.Missing.called("net.minecraft.client.multiplayer.ClientLevel.ClientLevelData.getHorizonHeight"); return 0.0; }
        protected ClientLevelData() {  }
    }
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
