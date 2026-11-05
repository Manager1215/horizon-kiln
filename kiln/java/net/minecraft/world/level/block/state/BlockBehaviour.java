package net.minecraft.world.level.block.state;

import java.util.List;
import java.util.SortedMap;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.TypedInstance;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Kiln's stand-in for {@code BlockBehaviour}: what a block does when used, ticked at random or ticked as scheduled.
 * Kiln runs a mod's own when Horizon's server reaches its block (kiln/java/kiln/Server.java).
 */
public abstract class BlockBehaviour {
    protected final boolean isRandomlyTicking;
    protected final Properties properties;

    public BlockBehaviour(Properties properties) {
        this.properties = properties;
        this.isRandomlyTicking = properties.isRandomlyTicking;
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {}

    /** A neighbour changed (a block set beside it, a redstone update). */
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            Orientation orientation, boolean movedByPiston) {}

    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {}

    protected boolean isRandomlyTicking(BlockState state) {
        return isRandomlyTicking;
    }

    /** The properties it was made with. */
    public Properties properties() {
        return properties;
    }

    /** Kiln's own: the properties it was made with. */
    public Properties kiln$properties() {
        return properties;
    }

    public static class Properties {
        /** Public: mods widen it (their access wideners), as Tech Reborn does to read a block's id. */
        public ResourceKey<Block> id;
        private boolean isRandomlyTicking;
        private boolean hasCollision = true;
        private float destroyTime;
        private float explosionResistance;
        private SoundType soundType = SoundType.STONE;
        private ToIntFunction<BlockState> lightEmission = state -> 0;

        public static Properties of() {
            return new Properties();
        }

        public static Properties ofFullCopy(BlockBehaviour block) {
            Properties copy = new Properties();
            Properties from = block.properties;
            copy.isRandomlyTicking = from.isRandomlyTicking;
            copy.hasCollision = from.hasCollision;
            copy.destroyTime = from.destroyTime;
            copy.explosionResistance = from.explosionResistance;
            copy.soundType = from.soundType;
            copy.lightEmission = from.lightEmission;
            return copy;
        }

        public Properties setId(ResourceKey<Block> id) {
            this.id = id;
            return this;
        }

        public Properties randomTicks() {
            isRandomlyTicking = true;
            return this;
        }

        public Properties noCollision() {
            hasCollision = false;
            return this;
        }

        public Properties noOcclusion() {
            return this;
        }

        public Properties strength(float destroyTime, float explosionResistance) {
            this.destroyTime = destroyTime;
            this.explosionResistance = Math.max(0, explosionResistance);
            return this;
        }

        public Properties strength(float strength) {
            return strength(strength, strength);
        }

        public Properties instabreak() {
            return strength(0);
        }

        public Properties destroyTime(float destroyTime) {
            this.destroyTime = destroyTime;
            return this;
        }

        public Properties explosionResistance(float explosionResistance) {
            this.explosionResistance = Math.max(0, explosionResistance);
            return this;
        }

        public Properties sound(SoundType soundType) {
            this.soundType = soundType;
            return this;
        }

        public Properties lightLevel(ToIntFunction<BlockState> lightEmission) {
            this.lightEmission = lightEmission;
            return this;
        }

        public Properties requiresCorrectToolForDrops() {
            return this;
        }

        public Properties noLootTable() {
            return this;
        }

        public Properties replaceable() {
            return this;
        }

        public Properties friction(float friction) {
            return this;
        }

        public Properties speedFactor(float speedFactor) {
            return this;
        }

        /** Kiln's own: the id it was given ({@code setId}). */
        public ResourceKey<Block> kiln$id() {
            return id;
        }
        // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties mapColor(net.minecraft.world.level.material.MapColor p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.mapColor"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties ignitedByLava() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.ignitedByLava"); return this; }
        // End of what tools/kiln-stubs.py generated.
    }

    /** A block state's methods, which ask its block. */
    public abstract static class BlockStateBase extends StateHolder<Block, BlockState> implements TypedInstance<Block> {
        protected BlockStateBase(Block owner, SortedMap<String, String> values) {
            super(owner, values);
        }

        public Block getBlock() {
            return owner;
        }

        /** The block was set where {@code old} was (vanilla's {@code onPlace}): its block's code may act. */
        public void onPlace(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, BlockState old,
                boolean movedByPiston) {
            owner.onPlace(asState(), level, pos, old, movedByPiston);
        }

        public boolean isAir() {
            return owner.kiln$isAir();
        }

        @Override
        public Holder<Block> typeHolder() {
            return owner.kiln$holder();
        }

        @Override
        protected List<Property<?>> kiln$declared() {
            return owner.getStateDefinition().kiln$properties();
        }

        public InteractionResult useWithoutItem(Level level, Player player, BlockHitResult hit) {
            return owner.useWithoutItem(asState(), level, hit.getBlockPos(), player, hit);
        }

        public InteractionResult useItemOn(ItemStack stack, Level level, Player player, InteractionHand hand,
                BlockHitResult hit) {
            return owner.useItemOn(stack, asState(), level, hit.getBlockPos(), player, hand, hit);
        }

        public void randomTick(ServerLevel level, BlockPos pos, RandomSource random) {
            owner.randomTick(asState(), level, pos, random);
        }

        public void tick(ServerLevel level, BlockPos pos, RandomSource random) {
            owner.tick(asState(), level, pos, random);
        }

        public void handleNeighborChanged(Level level, BlockPos pos, Block neighborBlock, Orientation orientation,
                boolean movedByPiston) {
            owner.neighborChanged(asState(), level, pos, neighborBlock, orientation, movedByPiston);
        }

        public boolean isRandomlyTicking() {
            return owner.isRandomlyTicking(asState());
        }

        public int getLightEmission() {
            return owner.kiln$properties().lightEmission.applyAsInt(asState());
        }

        public FluidState getFluidState() {
            return owner.getFluidState(asState());
        }

        protected abstract BlockState asState();
        // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
        public boolean liquid() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.liquid"); return false; }
        public net.minecraft.world.level.block.state.BlockState rotate(net.minecraft.world.level.block.Rotation p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.rotate"); return null; }
        public float getDestroySpeed(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getDestroySpeed"); return 0.0F; }
        public float getDestroyProgress(net.minecraft.world.entity.player.Player p0, net.minecraft.world.level.BlockGetter p1, net.minecraft.core.BlockPos p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getDestroyProgress"); return 0.0F; }
        public boolean isSolidRender() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.isSolidRender"); return false; }
        public boolean canOcclude() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.canOcclude"); return false; }
        public net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1, net.minecraft.world.phys.shapes.CollisionContext p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getShape"); return null; }
        public java.util.List<net.minecraft.world.item.ItemStack> getDrops(net.minecraft.world.level.storage.loot.LootParams.Builder p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getDrops"); return null; }
        public boolean canBeReplaced(net.minecraft.world.item.context.BlockPlaceContext p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.canBeReplaced"); return false; }
        public boolean canBeReplaced(net.minecraft.world.level.material.Fluid p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.canBeReplaced"); return false; }
        public boolean canSurvive(net.minecraft.world.level.LevelReader p0, net.minecraft.core.BlockPos p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.canSurvive"); return false; }
        public boolean hasBlockEntity() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.hasBlockEntity"); return false; }
        public boolean isFaceSturdy(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1, net.minecraft.core.Direction p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.isFaceSturdy"); return false; }
        // End of what tools/kiln-stubs.py generated.
    }
    /**
     * The block was set where {@code old} was: its own code may act (a sponge takes up the liquid around it). Kiln's
     * server calls it when a player places the block, before {@code setPlacedBy}, as the game sets the block first.
     */
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            BlockState old, boolean movedByPiston) {}

    /**
     * The fluid in a block of this kind: the game's water and lava hold theirs (a source at level 0, else flowing), other
     * blocks none. A mod's liquid block says its own.
     */
    protected FluidState getFluidState(BlockState state) {
        boolean water = this == Blocks.WATER;
        if (!water && this != Blocks.LAVA) {
            return Fluids.EMPTY.defaultFluidState();
        }
        FlowingFluid fluid = water ? Fluids.WATER : Fluids.LAVA;
        String level = state.kiln$values().getOrDefault("level", "0");
        return level.equals("0") ? fluid.defaultFluidState() : fluid.getFlowing().defaultFluidState();
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    protected void affectNeighborsAfterRemoval(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.server.level.ServerLevel p1, net.minecraft.core.BlockPos p2, boolean p3) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.affectNeighborsAfterRemoval"); }
    protected void entityInside(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.Level p1, net.minecraft.core.BlockPos p2, net.minecraft.world.entity.Entity p3, net.minecraft.world.entity.InsideBlockEffectApplier p4, boolean p5) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.entityInside"); }
    public java.lang.String getDescriptionId() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.getDescriptionId"); return null; }
    // End of what tools/kiln-stubs.py generated.
}
