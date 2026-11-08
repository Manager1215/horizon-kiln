package kiln.test.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** A pad: set where another block was, it turns the block over it into glowstone. */
public class Pad extends Block {
    public Pad(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
        if (old.getBlock() != this) {
            level.setBlock(pos.above(), Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
