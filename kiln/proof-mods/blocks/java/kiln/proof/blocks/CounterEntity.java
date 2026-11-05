package kiln.proof.blocks;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The counter's block entity. It saves the ticks it counted, the level it was placed in (through a codec), the game
 * time it started at, and how often it was loaded: once, while the game keeps the object between ticks.
 */
public class CounterEntity extends BlockEntity {
    public static final int LIGHTS_AT = 40;
    private int ticks;
    private int loads;
    private String placedIn = "";
    private long started;

    public CounterEntity(BlockPos pos, BlockState state) {
        super(ProofBlocks.COUNTER_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CounterEntity counter) {
        if (counter.ticks == 0) {
            counter.started = level.getGameTime();
            counter.placedIn = level.dimension().identifier().toString();
        }
        counter.ticks++;
        counter.setChanged();
        if (counter.ticks >= LIGHTS_AT && !state.getValue(Counter.LIT)) {
            level.setBlock(pos, state.setValue(Counter.LIT, true), Block.UPDATE_ALL);
        }
    }

    public int ticks() {
        return ticks;
    }

    public int loads() {
        return loads;
    }

    public String placedIn() {
        return placedIn;
    }

    public void restart() {
        ticks = 0;
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        ticks = input.getIntOr("ticks", 0);
        loads = input.getIntOr("loads", 0) + 1;
        placedIn = input.read("placed_in", Codec.STRING).orElse("");
        started = input.getLongOr("started", 0L);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        output.putInt("ticks", ticks);
        output.putInt("loads", loads);
        output.store("placed_in", Codec.STRING, placedIn);
        output.putLong("started", started);
    }
}
