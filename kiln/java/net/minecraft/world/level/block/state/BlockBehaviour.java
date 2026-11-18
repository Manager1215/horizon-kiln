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
    /**
     * How well it stands an explosion, as the game's field: its properties', and for a game block the one Horizon's
     * explosion gave (Kiln's Mixin bridge's {@code explosion_resistance} row, D-203).
     */
    protected float explosionResistance;

    public BlockBehaviour(Properties properties) {
        this.properties = properties;
        this.isRandomlyTicking = properties.isRandomlyTicking;
        this.explosionResistance = properties.explosionResistance;
    }

    /** Kiln's own: a game block's resistance as Horizon's explosion has it (D-203). */
    public void kiln$explosionResistance(float resistance) {
        this.explosionResistance = resistance;
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

    /** Whether it can stay at {@code pos} (a plant on what is below it): by default, yes. */
    protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return true;
    }

    /** The sounds of its state: those it was made with. */
    protected SoundType getSoundType(BlockState state) {
        return properties.soundType;
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
        // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
        public java.util.function.Function<net.minecraft.world.level.block.state.BlockState, net.minecraft.world.level.material.MapColor> mapColor;
        public boolean requiresCorrectToolForDrops;
        public float friction;
        public float speedFactor;
        public float jumpFactor;
        public net.minecraft.resources.DependantName<net.minecraft.world.level.block.Block, java.util.Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable>>> drops;
        public net.minecraft.resources.DependantName<net.minecraft.world.level.block.Block, java.lang.String> descriptionId;
        public boolean canOcclude;
        public boolean isAir;
        public boolean ignitedByLava;
        public net.minecraft.world.level.material.PushReaction pushReaction;
        public boolean spawnTerrainParticles;
        public net.minecraft.world.level.block.state.properties.NoteBlockInstrument instrument;
        public boolean replaceable;
        public net.minecraft.world.level.block.state.BlockBehaviour.StateArgumentPredicate<net.minecraft.world.entity.EntityType<?>> isValidSpawn;
        public net.minecraft.world.level.block.state.BlockBehaviour.StatePredicate isRedstoneConductor;
        public net.minecraft.world.level.block.state.BlockBehaviour.StatePredicate isSuffocating;
        public net.minecraft.world.level.block.state.BlockBehaviour.StateArgumentPredicate<net.minecraft.world.phys.AABB> isViewBlocking;
        public boolean dynamicShape;
        public net.minecraft.world.level.block.state.BlockBehaviour.OffsetFunction offsetFunction;
        public static net.minecraft.world.level.block.state.BlockBehaviour.Properties ofLegacyCopy(net.minecraft.world.level.block.state.BlockBehaviour p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofLegacyCopy"); return null; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties mapColor(java.util.function.Function<net.minecraft.world.level.block.state.BlockState, net.minecraft.world.level.material.MapColor> p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.mapColor"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties jumpFactor(float p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.jumpFactor"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties bounceRestitution(float p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.bounceRestitution"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties fallDistanceReduction(float p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.fallDistanceReduction"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties dynamicShape() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.dynamicShape"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties overrideLootTable(java.util.Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable>> p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.overrideLootTable"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties liquid() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.liquid"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties forceSolidOn() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.forceSolidOn"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties forceSolidOff() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.forceSolidOff"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties pushReaction(net.minecraft.world.level.material.PushReaction p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.pushReaction"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties air() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.air"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties isValidSpawn(net.minecraft.world.level.block.state.BlockBehaviour.StateArgumentPredicate<net.minecraft.world.entity.EntityType<?>> p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.isValidSpawn"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties isRedstoneConductor(net.minecraft.world.level.block.state.BlockBehaviour.StatePredicate p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.isRedstoneConductor"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties isSuffocating(net.minecraft.world.level.block.state.BlockBehaviour.StatePredicate p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.isSuffocating"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties isViewBlocking(net.minecraft.world.level.block.state.BlockBehaviour.StateArgumentPredicate<net.minecraft.world.phys.AABB> p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.isViewBlocking"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties postProcess(net.minecraft.world.level.block.state.BlockBehaviour.PostProcess p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.postProcess"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties emissiveRendering(java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.emissiveRendering"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties offsetType(net.minecraft.world.level.block.state.BlockBehaviour.OffsetType p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.offsetType"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties noTerrainParticles() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.noTerrainParticles"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.instrument"); return this; }
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties overrideDescription(java.lang.String p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.Properties.overrideDescription"); return this; }
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

        public boolean canSurvive(net.minecraft.world.level.LevelReader level, BlockPos pos) {
            return owner.canSurvive(asState(), level, pos);
        }

        public SoundType getSoundType() {
            return owner.getSoundType(asState());
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
        public boolean hasBlockEntity() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.hasBlockEntity"); return false; }
        public boolean isFaceSturdy(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1, net.minecraft.core.Direction p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.isFaceSturdy"); return false; }
        // End of what tools/kiln-stubs.py generated.
        // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
        public int lightEmission;
        public net.minecraft.world.level.material.MapColor mapColor;
        public boolean canOcclude;
        public net.minecraft.world.level.block.state.BlockBehaviour.OffsetFunction offsetFunction;
        public boolean spawnTerrainParticles;
        public void initCache() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.initCache"); }
        public boolean isSolid() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.isSolid"); return false; }
        public boolean isValidSpawn(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1, net.minecraft.world.entity.EntityType<?> p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.isValidSpawn"); return false; }
        public boolean propagatesSkylightDown() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.propagatesSkylightDown"); return false; }
        public int getLightDampening() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getLightDampening"); return 0; }
        public net.minecraft.world.phys.shapes.VoxelShape getFaceOcclusionShape(net.minecraft.core.Direction p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getFaceOcclusionShape"); return null; }
        public net.minecraft.world.phys.shapes.VoxelShape getOcclusionShape() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getOcclusionShape"); return null; }
        public boolean hasLargeCollisionShape() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.hasLargeCollisionShape"); return false; }
        public boolean ignitedByLava() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.ignitedByLava"); return false; }
        public net.minecraft.world.level.material.MapColor getMapColor(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getMapColor"); return null; }
        public net.minecraft.world.level.block.state.BlockState mirror(net.minecraft.world.level.block.Mirror p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.mirror"); return null; }
        public net.minecraft.world.level.block.RenderShape getRenderShape() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getRenderShape"); return null; }
        public boolean emissiveRendering() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.emissiveRendering"); return false; }
        public float getShadeBrightness(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getShadeBrightness"); return 0.0F; }
        public boolean isRedstoneConductor(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.isRedstoneConductor"); return false; }
        public boolean isSignalSource() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.isSignalSource"); return false; }
        public int getSignal(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1, net.minecraft.core.Direction p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getSignal"); return 0; }
        public boolean hasAnalogOutputSignal() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.hasAnalogOutputSignal"); return false; }
        public int getDirectSignal(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1, net.minecraft.core.Direction p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getDirectSignal"); return 0; }
        public net.minecraft.world.level.material.PushReaction getPistonPushReaction() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getPistonPushReaction"); return null; }
        public boolean skipRendering(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.core.Direction p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.skipRendering"); return false; }
        public net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getShape"); return null; }
        public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getCollisionShape"); return null; }
        public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1, net.minecraft.world.phys.shapes.CollisionContext p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getCollisionShape"); return null; }
        public net.minecraft.world.phys.shapes.VoxelShape getVisualShape(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1, net.minecraft.world.phys.shapes.CollisionContext p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getVisualShape"); return null; }
        public net.minecraft.world.phys.shapes.VoxelShape getInteractionShape(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getInteractionShape"); return null; }
        public boolean entityCanStandOn(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1, net.minecraft.world.entity.Entity p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.entityCanStandOn"); return false; }
        public net.minecraft.world.phys.Vec3 getOffset(net.minecraft.core.BlockPos p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getOffset"); return null; }
        public boolean hasOffsetFunction() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.hasOffsetFunction"); return false; }
        public void updateNeighbourShapes(net.minecraft.world.level.LevelAccessor p0, net.minecraft.core.BlockPos p1, int p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.updateNeighbourShapes"); }
        public void updateNeighbourShapes(net.minecraft.world.level.LevelAccessor p0, net.minecraft.core.BlockPos p1, int p2, int p3) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.updateNeighbourShapes"); }
        public void updateIndirectNeighbourShapes(net.minecraft.world.level.LevelAccessor p0, net.minecraft.core.BlockPos p1, int p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.updateIndirectNeighbourShapes"); }
        public void updateIndirectNeighbourShapes(net.minecraft.world.level.LevelAccessor p0, net.minecraft.core.BlockPos p1, int p2, int p3) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.updateIndirectNeighbourShapes"); }
        public void affectNeighborsAfterRemoval(net.minecraft.server.level.ServerLevel p0, net.minecraft.core.BlockPos p1, boolean p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.affectNeighborsAfterRemoval"); }
        public void spawnAfterBreak(net.minecraft.server.level.ServerLevel p0, net.minecraft.core.BlockPos p1, net.minecraft.world.item.ItemStack p2, boolean p3) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.spawnAfterBreak"); }
        public void attack(net.minecraft.world.level.Level p0, net.minecraft.core.BlockPos p1, net.minecraft.world.entity.player.Player p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.attack"); }
        public boolean isSuffocating(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.isSuffocating"); return false; }
        public boolean isLightPermeable() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.isLightPermeable"); return false; }
        public boolean isViewBlocking(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1, net.minecraft.world.phys.AABB p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.isViewBlocking"); return false; }
        public net.minecraft.world.level.block.state.BlockState updateShape(net.minecraft.world.level.LevelReader p0, net.minecraft.world.level.ScheduledTickAccess p1, net.minecraft.core.BlockPos p2, net.minecraft.core.Direction p3, net.minecraft.core.BlockPos p4, net.minecraft.world.level.block.state.BlockState p5, net.minecraft.util.RandomSource p6) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.updateShape"); return null; }
        public boolean isPathfindable(net.minecraft.world.level.pathfinder.PathComputationType p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.isPathfindable"); return false; }
        public boolean canBeReplaced() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.canBeReplaced"); return false; }
        public net.minecraft.world.MenuProvider getMenuProvider(net.minecraft.world.level.Level p0, net.minecraft.core.BlockPos p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getMenuProvider"); return null; }
        public boolean is(net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> p0, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase> p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.is"); return false; }
        public boolean shouldChangedStateKeepBlockEntity(net.minecraft.world.level.block.state.BlockState p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.shouldChangedStateKeepBlockEntity"); return false; }
        public long getSeed(net.minecraft.core.BlockPos p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getSeed"); return 0L; }
        public boolean isFaceSturdy(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1, net.minecraft.core.Direction p2, net.minecraft.world.level.block.SupportType p3) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.isFaceSturdy"); return false; }
        public boolean isCollisionShapeFullBlock(net.minecraft.world.level.BlockGetter p0, net.minecraft.core.BlockPos p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.isCollisionShapeFullBlock"); return false; }
        public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader p0, net.minecraft.core.BlockPos p1, boolean p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.getCloneItemStack"); return null; }
        public boolean requiresCorrectToolForDrops() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.requiresCorrectToolForDrops"); return false; }
        public boolean shouldSpawnTerrainParticles() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.shouldSpawnTerrainParticles"); return false; }
        public net.minecraft.world.level.block.state.properties.NoteBlockInstrument instrument() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.instrument"); return null; }
        public static class Cache {
            protected Cache() {  }
        }
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
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static final net.minecraft.core.Direction[] UPDATE_SHAPE_ORDER = null;
    public boolean hasCollision;
    public net.minecraft.world.level.block.SoundType soundType;
    protected float friction;
    public boolean dynamicShape;
    public java.util.Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable>> drops;
    public java.lang.String descriptionId;
    protected net.minecraft.world.level.block.state.BlockState updateShape(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.LevelReader p1, net.minecraft.world.level.ScheduledTickAccess p2, net.minecraft.core.BlockPos p3, net.minecraft.core.Direction p4, net.minecraft.core.BlockPos p5, net.minecraft.world.level.block.state.BlockState p6, net.minecraft.util.RandomSource p7) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.updateShape"); return null; }
    protected boolean skipRendering(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.block.state.BlockState p1, net.minecraft.core.Direction p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.skipRendering"); return false; }
    protected net.minecraft.world.level.block.RenderShape getRenderShape(net.minecraft.world.level.block.state.BlockState p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.getRenderShape"); return null; }
    protected net.minecraft.world.level.block.state.BlockState mirror(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.block.Mirror p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.mirror"); return null; }
    protected boolean canBeReplaced(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.item.context.BlockPlaceContext p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.canBeReplaced"); return false; }
    protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.storage.loot.LootParams.Builder p1) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.getDrops"); return null; }
    protected net.minecraft.world.phys.shapes.VoxelShape getOcclusionShape(net.minecraft.world.level.block.state.BlockState p0) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.getOcclusionShape"); return null; }
    protected net.minecraft.world.phys.shapes.VoxelShape getBlockSupportShape(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.BlockGetter p1, net.minecraft.core.BlockPos p2) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.getBlockSupportShape"); return null; }
    public net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.BlockGetter p1, net.minecraft.core.BlockPos p2, net.minecraft.world.phys.shapes.CollisionContext p3) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.getShape"); return null; }
    protected net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.BlockGetter p1, net.minecraft.core.BlockPos p2, net.minecraft.world.phys.shapes.CollisionContext p3) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.getCollisionShape"); return null; }
    protected float getDestroyProgress(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.entity.player.Player p1, net.minecraft.world.level.BlockGetter p2, net.minecraft.core.BlockPos p3) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.getDestroyProgress"); return 0.0F; }
    protected void spawnAfterBreak(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.server.level.ServerLevel p1, net.minecraft.core.BlockPos p2, net.minecraft.world.item.ItemStack p3, boolean p4) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.spawnAfterBreak"); }
    protected void attack(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.Level p1, net.minecraft.core.BlockPos p2, net.minecraft.world.entity.player.Player p3) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.attack"); }
    protected int getSignal(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.BlockGetter p1, net.minecraft.core.BlockPos p2, net.minecraft.core.Direction p3) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.getSignal"); return 0; }
    public java.util.Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable>> getLootTable() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.getLootTable"); return java.util.Optional.empty(); }
    public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader p0, net.minecraft.core.BlockPos p1, net.minecraft.world.level.block.state.BlockState p2, boolean p3) { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.getCloneItemStack"); return null; }
    public net.minecraft.world.level.material.MapColor defaultMapColor() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.defaultMapColor"); return null; }
    public float defaultDestroyTime() { kiln.Missing.called("net.minecraft.world.level.block.state.BlockBehaviour.defaultDestroyTime"); return 0.0F; }
    public static interface PostProcess {
        net.minecraft.core.BlockPos getPostProcessPos(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.BlockGetter p1, net.minecraft.core.BlockPos p2);
    }
    public static interface StateArgumentPredicate<A> {
        boolean test(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.BlockGetter p1, net.minecraft.core.BlockPos p2, A p3);
    }
    public static interface OffsetFunction {
        net.minecraft.world.phys.Vec3 evaluate(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.core.BlockPos p1);
    }
    public static interface StatePredicate {
        boolean test(net.minecraft.world.level.block.state.BlockState p0, net.minecraft.world.level.BlockGetter p1, net.minecraft.core.BlockPos p2);
    }
    public static enum OffsetType {
        NONE, XZ, XYZ;
    }
    // End of what tools/kiln-stubs.py generated.
}
