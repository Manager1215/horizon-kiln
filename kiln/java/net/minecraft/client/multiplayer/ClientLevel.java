package net.minecraft.client.multiplayer;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code ClientLevel}: the dimension the player is in, as the window's game said with the tick:
 * its time of day, weather and height. Of its entities Kiln knows the window's player.
 */
public class ClientLevel extends Level {
    private long dayTime;
    private float rainLevel;
    private float thunderLevel;
    private int minY = -64;
    private int height = 384;

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

    /** The entities Kiln knows in it: the window's player. */
    public Iterable<Entity> entitiesForRendering() {
        Entity player = Minecraft.getInstance().player;
        return player == null ? List.of() : List.of(player);
    }

    public Entity getEntity(int id) {
        Entity player = Minecraft.getInstance().player;
        return player != null && player.getId() == id ? player : null;
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

    public net.minecraft.world.level.chunk.LevelChunk getChunkAt(net.minecraft.core.BlockPos pos) {
        return getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** Whether the game has the chunk {@code pos} is in. */
    @Override
    public boolean isLoaded(net.minecraft.core.BlockPos pos) {
        return chunks.containsKey(net.minecraft.world.level.ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
    }

    /** Kiln's own: the chunks that came ({@code x,z} joined by {@code ;}), then those that went, each told to mods. */
    public void kiln$chunks(String loaded, String unloaded) {
        for (long at : kiln$positions(unloaded)) {
            net.minecraft.world.level.chunk.LevelChunk chunk = chunks.remove(at);
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

    /** Kiln's own: the level is left: each chunk it had goes, told to mods. */
    public void kiln$unloadAll() {
        for (net.minecraft.world.level.chunk.LevelChunk chunk : java.util.List.copyOf(chunks.values())) {
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_UNLOAD.invoker()
                    .onChunkUnload(this, chunk);
        }
        chunks.clear();
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
}
