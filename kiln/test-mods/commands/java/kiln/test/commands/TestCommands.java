package kiln.test.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Server commands, as a mod registers them with Fabric's API: {@code /kilntest ...} and its alias {@code /kt}. */
public class TestCommands implements ModInitializer {
    private static final SimpleCommandExceptionType NOPE = new SimpleCommandExceptionType(Component.literal("Nope"));

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
            LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("kilntest")
                    .then(Commands.literal("hello").executes(c -> say(c.getSource(), "Hello, " + c.getSource().getTextName())))
                    .then(Commands.literal("add")
                            .then(Commands.argument("a", IntegerArgumentType.integer())
                                    .then(Commands.argument("b", IntegerArgumentType.integer(0, 100))
                                            .executes(c -> say(c.getSource(), String.valueOf(
                                                    IntegerArgumentType.getInteger(c, "a")
                                                            + IntegerArgumentType.getInteger(c, "b")))))))
                    .then(Commands.literal("op").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(c -> say(c.getSource(), "You are an operator")))
                    .then(Commands.literal("heal").executes(c -> {
                        ServerPlayer player = c.getSource().getPlayerOrException();
                        player.heal(4.0F);
                        return say(c.getSource(), "Healed");
                    }))
                    .then(Commands.literal("whois")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .executes(c -> say(c.getSource(),
                                            "Found " + EntityArgument.getPlayer(c, "target").getScoreboardName()))))
                    .then(Commands.literal("say")
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(c -> say(c.getSource(), StringArgumentType.getString(c, "message")))))
                    .then(Commands.literal("fail").executes(c -> {
                        throw NOPE.create();
                    }))
                    .then(Commands.literal("Loud").executes(c -> say(c.getSource(), "LOUD")))
                    // The game's argument types mods use most (#133), and what a command reads of its player.
                    .then(Commands.literal("pos").then(Commands.argument("at", Vec3Argument.vec3()).executes(c -> {
                        Vec3 at = Vec3Argument.getVec3(c, "at");
                        return say(c.getSource(), String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", at.x, at.y, at.z));
                    })))
                    .then(Commands.literal("id").then(Commands.argument("id", IdentifierArgument.id())
                            .executes(c -> say(c.getSource(), IdentifierArgument.getId(c, "id").toString()))))
                    .then(Commands.literal("block").then(Commands.argument("state", BlockStateArgument.block(context))
                            .executes(c -> say(c.getSource(), String.valueOf(BlockStateArgument.getBlock(c, "state").getState())))))
                    .then(Commands.literal("profile").then(Commands.argument("who", GameProfileArgument.gameProfile())
                            .executes(c -> {
                                StringBuilder names = new StringBuilder();
                                for (NameAndId who : GameProfileArgument.getGameProfiles(c, "who")) {
                                    names.append(who.name()).append(' ').append(who.id());
                                }
                                return say(c.getSource(), names.toString());
                            })))
                    .then(Commands.literal("dim").then(Commands.argument("d", DimensionArgument.dimension())
                            .executes(c -> say(c.getSource(),
                                    DimensionArgument.getDimension(c, "d").dimension().identifier().toString()))))
                    .then(Commands.literal("played").executes(c -> {
                        ServerPlayer player = c.getSource().getPlayerOrException();
                        return say(c.getSource(), player.nameAndId().name() + " played "
                                + player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)));
                    }))
                    .then(Commands.literal("creative").executes(c -> say(c.getSource(),
                            c.getSource().getPlayerOrException().setGameMode(GameType.CREATIVE) ? "changed" : "already")))
                    .then(Commands.literal("hover").executes(c -> {
                        c.getSource().sendSuccess(() -> Component.literal("hover me")
                                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal("hi")))), false);
                        return 1;
                    }))
                    .then(Commands.literal("contents").executes(c -> {
                        Component death = Component.translatable("death.attack.mob", "Kiln", "a zombie");
                        return say(c.getSource(), death.getContents() instanceof TranslatableContents t ? t.getKey() : "literal");
                    }))
                    // The held stack's name and lore, as item-rename mods set them, and its name as chat shows it.
                    .then(Commands.literal("rename").then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(c -> {
                                ItemStack held = c.getSource().getPlayerOrException().getMainHandItem();
                                held.set(DataComponents.CUSTOM_NAME, Component.literal(StringArgumentType.getString(c, "name"))
                                        .withStyle(ChatFormatting.GOLD));
                                return say(c.getSource(), held.getHoverName().getString());
                            })))
                    .then(Commands.literal("lore").then(Commands.argument("line", StringArgumentType.greedyString())
                            .executes(c -> {
                                ItemStack held = c.getSource().getPlayerOrException().getMainHandItem();
                                ItemLore lore = held.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
                                        .withLineAdded(Component.literal(StringArgumentType.getString(c, "line")));
                                held.set(DataComponents.LORE, lore);
                                return say(c.getSource(), held.get(DataComponents.LORE).lines().size() + " lines");
                            })))
                    .then(Commands.literal("shown").executes(c -> {
                        ItemStack held = c.getSource().getPlayerOrException().getMainHandItem();
                        c.getSource().sendSuccess(held::getDisplayName, false);
                        return 1;
                    }))
                    .then(Commands.literal("xp").then(Commands.argument("n", IntegerArgumentType.integer()).executes(c -> {
                        ServerPlayer player = c.getSource().getPlayerOrException();
                        player.giveExperiencePoints(IntegerArgumentType.getInteger(c, "n"));
                        return say(c.getSource(), "level " + player.experienceLevel);
                    })))
                    .then(Commands.literal("tryid").then(Commands.argument("ns", StringArgumentType.word())
                            .then(Commands.argument("path", StringArgumentType.word()).executes(c -> say(c.getSource(),
                                    String.valueOf(Identifier.tryBuild(StringArgumentType.getString(c, "ns"),
                                            StringArgumentType.getString(c, "path"))))))))
                    .then(Commands.literal("color").then(Commands.argument("c", StringArgumentType.greedyString())
                            .executes(c -> say(c.getSource(), TextColor.parseColor(StringArgumentType.getString(c, "c"))
                                    .result().map(TextColor::serialize).orElse("invalid"))))));
            dispatcher.register(Commands.literal("kt").redirect(root));
        });
    }

    private static int say(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
