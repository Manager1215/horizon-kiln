package net.minecraft.server.level;

import java.util.HashMap;
import java.util.Map;
import kiln.BlockEntities;
import kiln.Server;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * Kiln's stand-in for {@code ServerLevel}: a dimension where the game's rules run, as Horizon's integrated server
 * said it was when it called the mod. What the mod's code does to it, the server then does.
 */
public class ServerLevel extends Level {
    private final SavedDataStorage storage;
    /** The blocks this call read or set: asked of the game once each. */
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    /** Whether the sky is seen from a position, and the weather: asked once each this call. */
    private final Map<BlockPos, Boolean> sky = new HashMap<>();
    private float[] weather;

    /** Kiln's own: a level of the world whose data is in {@code storage}. */
    public ServerLevel(String dimension, long gameTime, SavedDataStorage storage) {
        kiln$update(dimension, gameTime);
        this.storage = storage;
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    /** The block at {@code pos}, as the game has it now: Kiln asks while the game waits for the mod's answer. */
    @Override
    public BlockState getBlockState(BlockPos pos) {
        return blocks.computeIfAbsent(pos.immutable(), p -> BlockState.kiln$parse(
                Server.ask("block", Integer.toString(p.getX()), Integer.toString(p.getY()), Integer.toString(p.getZ()))));
    }

    /** A mod's block entity there, as the game has it now; none for the game's own (a chest's, a furnace's). */
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return BlockEntities.at(this, pos);
    }

    /** The game sets it once the mod's code is done ({@code block <x> <y> <z> <flags> <state>}). */
    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
        BlockState before = blocks.get(pos);
        if (before == state) {
            return false;
        }
        blocks.put(pos.immutable(), state);
        BlockEntities.blockChanged(this, pos, state);
        Server.effect("block " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + flags + " " + state.kiln$text());
        return true;
    }

    /** The game schedules it once the mod's code is done ({@code schedule <x> <y> <z> <block> <delay>}). */
    @Override
    public void kiln$scheduleTick(BlockPos pos, Block block, int delay) {
        Server.effect("schedule " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block.kiln$id() + " " + delay);
    }

    @Override
    public void kiln$drop(BlockPos pos, ItemStack stack) {
        Server.effect("drop " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + stack.getItem().kiln$id() + " "
                + stack.getCount());
    }

    /** The world's saved data: as in vanilla 26, the world's, not the dimension's. */
    public SavedDataStorage getDataStorage() {
        return storage;
    }

    /**
     * Heard by the players near {@code x y z}. Vanilla leaves out {@code except}, whose own client played it; Kiln
     * runs no client half of the mod's code, so they hear it too.
     */
    @Override
    public void playSound(Entity except, double x, double y, double z, SoundEvent sound, SoundSource source, float volume,
            float pitch) {
        Server.effect("sound " + sound.location() + " " + source.ordinal() + " " + x + " " + y + " " + z + " " + volume
                + " " + pitch);
    }

    @Override
    public net.minecraft.server.MinecraftServer getServer() {
        return net.minecraft.server.MinecraftServer.kiln$server();
    }

    public net.minecraft.world.item.crafting.RecipeManager recipeAccess() {
        return getServer().getRecipeManager();
    }

    /** Whether the sky is seen from {@code pos}, as the game's light says: asked once a position each call. */
    @Override
    public boolean canSeeSky(BlockPos pos) {
        return sky.computeIfAbsent(pos.immutable(), p -> "true".equals(
                Server.ask("sky", Integer.toString(p.getX()), Integer.toString(p.getY()), Integer.toString(p.getZ()))));
    }

    @Override
    public float getRainLevel(float partialTick) {
        return weather()[0];
    }

    @Override
    public float getThunderLevel(float partialTick) {
        return weather()[1];
    }

    /** The dimension's environment: the sun's angle at a position, as the game has it now. */
    @Override
    public EnvironmentAttributeSystem environmentAttributes() {
        return new EnvironmentAttributeSystem((attribute, pos) -> {
            if (attribute != EnvironmentAttributes.SUN_ANGLE) {
                return null;
            }
            String angle = Server.ask("sun_angle", Integer.toString(pos.getX()), Integer.toString(pos.getY()),
                    Integer.toString(pos.getZ()));
            return angle.isEmpty() ? null : Float.parseFloat(angle);
        });
    }

    /** How much it rains and thunders now ({@code weather}, answered {@code <rain> <thunder>}), asked once a call. */
    private float[] weather() {
        if (weather == null) {
            String[] answer = Server.ask("weather").split(" ");
            weather = new float[] {0, 0};
            for (int i = 0; i < 2 && i < answer.length; i++) {
                try {
                    weather[i] = Float.parseFloat(answer[i]);
                } catch (NumberFormatException e) {
                    // The game said nothing: no rain.
                }
            }
        }
        return weather;
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public void resetEmptyTime() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.resetEmptyTime"); }
    public boolean addFreshEntity(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.addFreshEntity"); return false; }
    public void updateNeighborsAt(net.minecraft.core.BlockPos p0, net.minecraft.world.level.block.Block p1, net.minecraft.world.level.redstone.Orientation p2) { kiln.Missing.called("net.minecraft.server.level.ServerLevel.updateNeighborsAt"); }
    public net.minecraft.server.level.ServerChunkCache getChunkSource() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.getChunkSource"); return null; }
    public java.util.List<net.minecraft.server.level.ServerPlayer> players() { kiln.Missing.called("net.minecraft.server.level.ServerLevel.players"); return null; }
    // End of what tools/kiln-stubs.py generated.
}
