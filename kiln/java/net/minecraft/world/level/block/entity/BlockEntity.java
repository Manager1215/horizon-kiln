package net.minecraft.world.level.block.entity;

import java.util.Map;
import java.util.TreeMap;
import kiln.Snbt;
import kiln.TagValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Holder;
import net.minecraft.core.TypedInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Kiln's stand-in for {@code BlockEntity}: a mod block's data and code at a position. Horizon keeps the block entity
 * in the world, with what {@code saveAdditional} wrote as its saved compound; Kiln keeps the mod's object while the
 * world is open, and loads it again from that compound whenever the game's copy is not what it last saved.
 */
public abstract class BlockEntity implements TypedInstance<BlockEntityType<?>> {
    protected Level level;
    protected final BlockPos worldPosition;
    protected boolean remove;
    private final BlockEntityType<?> type;
    private BlockState blockState;

    public BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        this.type = type;
        this.worldPosition = pos.immutable();
        this.blockState = state;
    }

    public boolean isValidBlockState(BlockState state) {
        return type.isValid(state);
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public boolean hasLevel() {
        return level != null;
    }

    protected void loadAdditional(ValueInput input) {}

    public final void loadWithComponents(ValueInput input) {
        loadAdditional(input);
    }

    public final void loadCustomOnly(ValueInput input) {
        loadAdditional(input);
    }

    protected void saveAdditional(ValueOutput output) {}

    public void saveWithoutMetadata(ValueOutput output) {
        saveAdditional(output);
    }

    public void saveCustomOnly(ValueOutput output) {
        saveAdditional(output);
    }

    /** Kiln saves what the mod's code changed at the end of each call: nothing more to mark. */
    public void setChanged() {}

    protected static void setChanged(Level level, BlockPos pos, BlockState state) {}

    public BlockPos getBlockPos() {
        return worldPosition;
    }

    public BlockState getBlockState() {
        return blockState;
    }

    public void setBlockState(BlockState state) {
        this.blockState = state;
    }

    public boolean isRemoved() {
        return remove;
    }

    public void setRemoved() {
        remove = true;
    }

    public void clearRemoved() {
        remove = false;
    }

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {}

    public boolean triggerEvent(int id, int param) {
        return false;
    }

    public BlockEntityType<?> getType() {
        return type;
    }

    @Override
    public Holder<BlockEntityType<?>> typeHolder() {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.get(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type))
                .<Holder<BlockEntityType<?>>>map(h -> h)
                .orElseGet(() -> Holder.direct(type));
    }

    /**
     * Kiln's own: loads what the game saved of it ({@code {count:5}}). The game keeps a container's slots apart, and
     * hands them over as vanilla saves them ({@code Items}); they go into the container, whatever name the mod keeps
     * its items under.
     */
    public final void kiln$load(String saved) {
        Map<String, Object> compound = Snbt.compound(saved);
        loadWithComponents(TagValues.input(compound));
        kiln$slots(compound);
    }

    /**
     * Kiln's own: a container's slots as the game has them ({@code Items} in {@code compound}), put in each where
     * they differ from what it holds, as a hopper does ({@code setItem}).
     */
    public final void kiln$slots(Map<String, Object> compound) {
        if (!(this instanceof Container container)) {
            return;
        }
        NonNullList<ItemStack> slots = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(TagValues.input(compound), slots);
        for (int slot = 0; slot < slots.size(); slot++) {
            if (!ItemStack.matches(container.getItem(slot), slots.get(slot))) {
                container.setItem(slot, slots.get(slot));
            }
        }
    }

    /**
     * Kiln's own: what it saves now, as the game keeps it. A container's slots are saved as vanilla saves them
     * ({@code Items}), from the container itself: the game keeps them as its slots, which hoppers and commands reach.
     */
    public final String kiln$save() {
        Map<String, Object> saved = new TreeMap<>();
        saveAdditional(TagValues.output(saved));
        if (this instanceof Container container) {
            saved.remove(ContainerHelper.TAG_ITEMS);
            NonNullList<ItemStack> slots = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
            for (int slot = 0; slot < slots.size(); slot++) {
                slots.set(slot, container.getItem(slot));
            }
            ContainerHelper.saveAllItems(TagValues.output(saved), slots);
        }
        return Snbt.write(saved);
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public void saveWithId(net.minecraft.world.level.storage.ValueOutput p0) { kiln.Missing.called("net.minecraft.world.level.block.entity.BlockEntity.saveWithId"); }
    public net.minecraft.nbt.CompoundTag saveWithoutMetadata(net.minecraft.core.HolderLookup.Provider p0) { kiln.Missing.called("net.minecraft.world.level.block.entity.BlockEntity.saveWithoutMetadata"); return null; }
    public net.minecraft.core.component.DataComponentMap collectComponents() { kiln.Missing.called("net.minecraft.world.level.block.entity.BlockEntity.collectComponents"); return null; }
    public net.minecraft.util.ProblemReporter.PathElement problemPath() { kiln.Missing.called("net.minecraft.world.level.block.entity.BlockEntity.problemPath"); return null; }
    // End of what tools/kiln-stubs.py generated.
}
