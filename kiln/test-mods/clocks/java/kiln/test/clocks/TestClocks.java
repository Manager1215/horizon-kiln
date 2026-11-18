package kiln.test.clocks;

import com.mojang.brigadier.arguments.LongArgumentType;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Server commands that reach the world's clocks and structures as mods do: {@code /clocktest set <ticks>} sets the
 * time of day through the dimension's clock and reads it back, {@code /clocktest get} reads it, {@code /clocktest
 * structures} lists the structures at the player's position.
 */
public class TestClocks implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> dispatcher.register(
                Commands.literal("clocktest")
                        .then(Commands.literal("set").then(Commands.argument("ticks", LongArgumentType.longArg())
                                .executes(c -> {
                                    ServerLevel level = c.getSource().getLevel();
                                    Holder<WorldClock> clock =
                                            level.dimensionTypeRegistration().value().defaultClock().orElseThrow();
                                    level.clockManager().setTotalTicks(clock, LongArgumentType.getLong(c, "ticks"));
                                    return say(c.getSource(), "Set " + level.clockManager().getInstance(clock).totalTicks()
                                            + " " + level.getDefaultClockTime());
                                })))
                        .then(Commands.literal("get").executes(c -> {
                            ServerLevel level = c.getSource().getLevel();
                            String clock = level.dimensionType().defaultClock()
                                    .map(h -> h.unwrapKey().orElseThrow().identifier().toString()).orElse("none");
                            return say(c.getSource(), clock + " " + level.getDefaultClockTime() + " "
                                    + level.dimensionTypeRegistration().unwrapKey().orElseThrow().identifier());
                        }))
                        .then(Commands.literal("structures").executes(c -> {
                            ServerLevel level = c.getSource().getLevel();
                            BlockPos pos = c.getSource().getPlayerOrException().blockPosition();
                            ChunkPos chunk = ChunkPos.containing(pos);
                            List<Structure> here = level.structureManager()
                                    .startsForStructure(chunk.x(), chunk.z(), structure -> true).stream()
                                    .filter(start -> start.getBoundingBox().isInside(pos))
                                    .map(StructureStart::getStructure).toList();
                            if (here.isEmpty()) {
                                return say(c.getSource(), "None");
                            }
                            MutableComponent text = Component.literal("Here:");
                            for (Structure structure : here) {
                                String id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure)
                                        .toString();
                                text.append(Component.literal(" ")).append(ComponentUtils.copyOnClickText(id));
                            }
                            c.getSource().sendSuccess(() -> text, false);
                            return here.size();
                        }))));
    }

    private static int say(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
