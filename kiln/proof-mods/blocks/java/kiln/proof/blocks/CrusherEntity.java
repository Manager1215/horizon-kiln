package kiln.proof.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The crusher's block entity: slot 0 takes cobblestone (from above and the sides), slot 1 gives gravel (from below).
 * Every {@link #WORK} ticks it turns one into the other, lit while it has work.
 */
public class CrusherEntity extends BlockEntity implements WorldlyContainer {
    public static final int WORK = 10;
    private static final int[] IN = {0};
    private static final int[] OUT = {1};
    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int progress;
    private int crushed;

    public CrusherEntity(BlockPos pos, BlockState state) {
        super(ProofBlocks.CRUSHER_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrusherEntity crusher) {
        ItemStack in = crusher.items.get(0);
        ItemStack out = crusher.items.get(1);
        boolean work = in.is(Items.COBBLESTONE) && (out.isEmpty() || out.is(Items.GRAVEL) && out.getCount() < 64);
        if (work && ++crusher.progress >= WORK) {
            crusher.progress = 0;
            crusher.crushed++;
            in.shrink(1);
            if (out.isEmpty()) {
                crusher.items.set(1, new ItemStack(Items.GRAVEL));
            } else {
                out.grow(1);
            }
            crusher.setChanged();
        }
        if (state.getValue(Crusher.LIT) != work) {
            level.setBlock(pos, state.setValue(Crusher.LIT, work), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("progress", 0);
        crushed = input.getIntOr("crushed", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        ContainerHelper.saveAllItems(output, items);
        output.putInt("progress", progress);
        output.putInt("crushed", crushed);
    }

    @Override
    public int[] getSlotsForFace(Direction face) {
        return face == Direction.DOWN ? OUT : IN;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction face) {
        return slot == 0 && stack.is(Items.COBBLESTONE);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) {
        return slot == 1 && face == Direction.DOWN;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack taken = ContainerHelper.removeItem(items, slot, count);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
    }
}
