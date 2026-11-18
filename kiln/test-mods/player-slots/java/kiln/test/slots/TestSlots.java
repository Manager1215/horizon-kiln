package kiln.test.slots;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Server commands that reach their player's slots, as inventory mods' do: {@code /kilnslots ender}, {@code ender put},
 * {@code ender take}, {@code snapshot}, {@code chest}, {@code craft}, {@code who}.
 */
public class TestSlots implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> dispatcher.register(
                Commands.literal("kilnslots")
                        // The ender chest as the game has it; three ender pearls put in its first slot; its fourth
                        // slot emptied.
                        .then(Commands.literal("ender")
                                .executes(c -> say(c, slots(player(c).getEnderChestInventory())))
                                .then(Commands.literal("put").executes(c -> {
                                    player(c).getEnderChestInventory().setItem(0, new ItemStack(Items.ENDER_PEARL, 3));
                                    return say(c, slots(player(c).getEnderChestInventory()));
                                }))
                                .then(Commands.literal("take").executes(c -> {
                                    ItemStack taken = player(c).getEnderChestInventory().removeItemNoUpdate(3);
                                    return say(c, "took " + taken.getCount());
                                })))
                        // The inventory, armour, off hand and ender chest saved as an inventory snapshot mod saves
                        // them (invshots), through a file and back.
                        .then(Commands.literal("snapshot").executes(TestSlots::snapshot))
                        // The ender chest opened in a chest's menu, as /ec does; then closed.
                        .then(Commands.literal("chest").executes(c -> {
                            ServerPlayer player = player(c);
                            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestMenu(MenuType.GENERIC_9x3,
                                    id, inventory, p.getEnderChestInventory(), 3), Component.translatable("container.enderchest")));
                            String open = player.containerMenu instanceof ChestMenu chest
                                    ? "chest " + chest.slots.size() + " " + chest.getContainer().getItem(3).getCount()
                                    : String.valueOf(player.containerMenu);
                            player.closeContainer();
                            return say(c, open + ", closed " + (player.containerMenu == player.inventoryMenu));
                        }))
                        // A crafting table's menu anywhere, as /craft does.
                        .then(Commands.literal("craft").executes(c -> {
                            ServerPlayer player = player(c);
                            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new CraftingMenu(id, inventory,
                                    ContainerLevelAccess.create(p.level(), p.blockPosition())),
                                    Component.translatable("container.crafting")));
                            return say(c, player.containerMenu instanceof CraftingMenu crafting
                                    ? "crafting " + crafting.slots.size() : String.valueOf(player.containerMenu));
                        }))
                        // Who the player is to the server: alive, their operator level, the same registries everywhere.
                        .then(Commands.literal("who").executes(c -> {
                            ServerPlayer player = player(c);
                            NameAndId me = new NameAndId(player.getGameProfile());
                            boolean same = player.registryAccess() == c.getSource().registryAccess()
                                    && c.getSource().registryAccess() == c.getSource().getServer().registryAccess();
                            return say(c, me.name() + " alive " + player.isAlive()
                                    + " level " + c.getSource().getServer().getProfilePermissions(me).level().id()
                                    + " owner " + c.getSource().getServer().isSingleplayerOwner(me)
                                    + " registries " + same);
                        }))));
    }

    private static int snapshot(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = player(c);
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
        player.getInventory().save(out.list("Inventory", ItemStackWithSlot.CODEC));
        ValueOutput.TypedOutputList<ItemStackWithSlot> worn = out.list("Worn", ItemStackWithSlot.CODEC);
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (slot.isArmor() || slot == EquipmentSlot.OFFHAND) {
                ItemStack stack = player.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    worn.add(new ItemStackWithSlot(100 + slot.ordinal(), stack));
                }
            }
        }
        player.getEnderChestInventory().storeAsSlots(out.list("EnderChest", ItemStackWithSlot.CODEC));
        CompoundTag read;
        try {
            Path file = Files.createTempFile("kiln-slots", ".dat");
            NbtIo.writeCompressed(out.buildResult(), file);
            read = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            Files.delete(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ValueInput in = TagValueInput.create(ProblemReporter.DISCARDING, c.getSource().registryAccess(), read);
        PlayerEnderChestContainer chest = new PlayerEnderChestContainer();
        chest.fromSlots(in.listOrEmpty("EnderChest", ItemStackWithSlot.CODEC));
        StringJoiner inventory = new StringJoiner(",");
        for (ItemStackWithSlot s : in.listOrEmpty("Inventory", ItemStackWithSlot.CODEC)) {
            inventory.add(s.slot() + ":" + s.stack().getItem().kiln$id().getPath() + "x" + s.stack().getCount());
        }
        StringJoiner wornRead = new StringJoiner(",");
        for (ItemStackWithSlot s : in.listOrEmpty("Worn", ItemStackWithSlot.CODEC)) {
            wornRead.add(s.slot() + ":" + s.stack().getItem().kiln$id().getPath());
        }
        return say(c, inventory + " | " + wornRead + " | " + slots(chest));
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return c.getSource().getPlayerOrException();
    }

    /** Each slot that holds something: {@code <slot>:<item>x<count>}. */
    private static String slots(Container container) {
        StringJoiner slots = new StringJoiner(",");
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                slots.add(slot + ":" + stack.getItem().kiln$id().getPath() + "x" + stack.getCount());
            }
        }
        return slots.length() == 0 ? "empty" : slots.toString();
    }

    private static int say(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
