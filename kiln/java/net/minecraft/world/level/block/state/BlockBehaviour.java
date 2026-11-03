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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.Property;
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

    /** Kiln's own: the properties it was made with. */
    public Properties kiln$properties() {
        return properties;
    }

    public static class Properties {
        private ResourceKey<Block> id;
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
    }

    /** A block state's methods, which ask its block. */
    public abstract static class BlockStateBase extends StateHolder<Block, BlockState> implements TypedInstance<Block> {
        protected BlockStateBase(Block owner, SortedMap<String, String> values) {
            super(owner, values);
        }

        public Block getBlock() {
            return owner;
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

        protected abstract BlockState asState();
    }
}
