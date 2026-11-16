package kiln.test.player;

import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A mod for Kiln's tests of the server's player calls (kiln/tests/players.rs): a charm with a use of its own, a pebble
 * without one, a welcome for a player who comes into a level (Fabric's {@code ENTITY_LOAD}, which a respawn makes
 * too), a line over the hotbar after a respawn, and a word for any block a sneaking player uses with an empty hand
 * (Fabric's {@code UseBlockCallback}, the game's blocks included). Its other block listeners read the game's
 * blocks as their classes, as the crop and door mods do: a grown crop on what supports crops is harvested and
 * replanted with a seed from the inventory, a door opens its neighbour, and cane on what supports it is cut, or grown
 * with bone meal. Written against Minecraft 26.3 and Fabric's API as any mod is; built against Kiln's stand-ins.
 */
public final class TestPlayer implements ModInitializer {
    public static final String ID = "kiln_test_player";
    public static final Item CHARM = register("charm", Charm::new);
    public static final Item PEBBLE = register("pebble", Item::new);

    private static Item register(String name, Function<Item.Properties, Item> factory) {
        Identifier id = Identifier.fromNamespaceAndPath(ID, name);
        return Registry.register(BuiltInRegistries.ITEM, id,
                factory.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
    }

    @Override
    public void onInitialize() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("Welcome, " + player.getName().getString()));
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> newPlayer.sendOverlayMessage(
                Component.literal(alive ? "Back from the End" : "Back from the dead")));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                    || !player.getItemInHand(hand).isEmpty()) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            player.sendSystemMessage(Component.literal("Used " + level.getBlockState(pos).getBlock() + " at "
                    + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            return InteractionResult.SUCCESS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (level.isClientSide() || !(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)
                    || !player.mayBuild() || !level.getWorldBorder().isWithinBounds(pos)) {
                return InteractionResult.PASS;
            }
            ItemStack seeds = player.getInventory().getItem(player.getInventory().getSelectedSlot());
            BlockState replanted = crop.getStateForAge(0);
            if (!player.getInventory().contains(seeds) || !replanted.canSurvive(level, pos)) {
                return InteractionResult.PASS;
            }
            Block.dropResources(state, level, pos, null, player, ItemStack.EMPTY);
            level.destroyBlock(pos, false, player);
            level.setBlock(pos, replanted, Block.UPDATE_ALL);
            seeds.shrink(1);
            level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (level.isClientSide() || !(state.getBlock() instanceof DoorBlock)
                    || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                return InteractionResult.PASS;
            }
            BlockPos beside = pos.relative(state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT
                    ? state.getValue(DoorBlock.FACING).getClockWise()
                    : state.getValue(DoorBlock.FACING).getCounterClockWise());
            BlockState other = level.getBlockState(beside);
            if (other.getBlock() instanceof DoorBlock door) {
                door.setOpen(player, level, other, beside, !state.getValue(DoorBlock.OPEN));
            }
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            BlockPos pos = hit.getBlockPos();
            if (level.isClientSide() || !level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.SUGAR_CANE)
                    || !level.getBlockState(pos.below()).is(BlockTags.SUPPORTS_SUGAR_CANE)
                    || !level.isEmptyBlock(pos.above())) {
                return InteractionResult.PASS;
            }
            // Bone meal grows it by two blocks, as Bonemeal Sugarcane does; anything else cuts it.
            if (player.getItemInHand(hand).getItem() == net.minecraft.world.item.Items.BONE_MEAL) {
                for (int i = 1; i <= 2; i++) {
                    level.setBlockAndUpdate(pos.above(i),
                            net.minecraft.world.level.block.Blocks.SUGAR_CANE.defaultBlockState());
                }
                return InteractionResult.SUCCESS;
            }
            level.destroyBlock(pos, true);
            return InteractionResult.CONSUME;
        });
    }
}
