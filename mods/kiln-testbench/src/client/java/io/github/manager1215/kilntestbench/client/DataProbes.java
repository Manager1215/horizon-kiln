package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.check;
import static io.github.manager1215.kilntestbench.client.Game.expect;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.INIT;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.WORLD;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Probe;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

/** Plain data a mod uses everywhere: identifiers, registries, items, blocks, text, math, NBT, and the libraries
 * Minecraft ships (Gson, Guava, fastutil, SLF4J, JOML, Brigadier, DataFixerUpper). */
final class DataProbes {
    private DataProbes() {}

    static void add(Bench b) {
        b.add("id.parse", INIT, () -> new Probe() {
            public Object run() {
                return expect(Identifier.parse("kiln:probe").getNamespace(), "kiln");
            }
        });
        b.add("id.defaultNamespace", INIT, () -> new Probe() {
            public Object run() {
                return expect(Identifier.withDefaultNamespace("stone"), "minecraft:stone");
            }
        });
        b.add("id.resourceKey", INIT, () -> new Probe() {
            public Object run() {
                return expect(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("stone")).identifier(),
                        "minecraft:stone");
            }
        });
        b.add("registry.blockKey", INIT, () -> new Probe() {
            public Object run() {
                return expect(BuiltInRegistries.BLOCK.getKey(Blocks.STONE), "minecraft:stone");
            }
        });
        b.add("registry.itemValue", INIT, () -> new Probe() {
            public Object run() {
                return expect(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("diamond")) == Items.DIAMOND,
                        true);
            }
        });
        b.add("registry.size", INIT, () -> new Probe() {
            public Object run() {
                // Every block of the game, not only the few a mod named.
                int blocks = BuiltInRegistries.BLOCK.keySet().size();
                check(blocks > 100, "only " + blocks + " blocks");
                check(BuiltInRegistries.BLOCK.containsKey(Identifier.withDefaultNamespace("oak_stairs")), "no oak_stairs");
                return expect(blocks > 100, true);
            }
        });
        // Not at start: an ItemStack needs the data components, bound once the registries are loaded.
        b.add("item.stack", WORLD, () -> new Probe() {
            public Object run() {
                ItemStack stack = new ItemStack(Items.DIAMOND, 3);
                return expect(stack.copy().getCount(), 3);
            }
        });
        b.add("item.hoverName", WORLD, () -> new Probe() {
            public Object run() {
                return expect(new ItemStack(Items.DIAMOND).getHoverName().getString(), "Diamond");
            }
        });
        b.add("block.state", INIT, () -> new Probe() {
            public Object run() {
                return expect(Blocks.STONE.defaultBlockState().isAir(), false);
            }
        });
        b.add("block.asItem", INIT, () -> new Probe() {
            public Object run() {
                return expect(Blocks.STONE.asItem() == Items.STONE, true);
            }
        });
        b.add("math.blockPos", INIT, () -> new Probe() {
            public Object run() {
                BlockPos pos = new BlockPos(1, 2, 3).above().offset(1, 1, 1);
                return expect(pos.getX() + " " + pos.getY() + " " + pos.getZ(), "2 4 4");
            }
        });
        b.add("math.vec3", INIT, () -> new Probe() {
            public Object run() {
                return expect(new Vec3(3, 4, 0).length(), 5.0);
            }
        });
        b.add("math.mth", INIT, () -> new Probe() {
            public Object run() {
                return expect(Mth.clamp(15, 0, 10) + " " + Mth.floor(-0.5), "10 -1");
            }
        });
        b.add("math.direction", INIT, () -> new Probe() {
            public Object run() {
                return expect(Direction.NORTH.getOpposite().getName(), "south");
            }
        });
        b.add("text.literal", INIT, () -> new Probe() {
            public Object run() {
                return expect(Component.literal("Ki").append("ln").getString(), "Kiln");
            }
        });
        b.add("text.style", INIT, () -> new Probe() {
            public Object run() {
                return expect(Component.literal("Kiln").withStyle(ChatFormatting.RED).getStyle().getColor().getValue(),
                        0xFF5555);
            }
        });
        b.add("text.color", INIT, () -> new Probe() {
            public Object run() {
                return expect(Component.literal("Kiln").withColor(TextColor.fromRgb(0x123456)).getStyle().getColor()
                        .getValue(), 0x123456);
            }
        });
        b.add("text.translatable", WORLD, () -> new Probe() {
            public Object run() {
                return expect(Component.translatable("key.jump").getString(), "Jump");
            }
        });
        b.add("nbt.compound", INIT, () -> new Probe() {
            public Object run() {
                CompoundTag tag = new CompoundTag();
                tag.putInt("kiln", 5);
                return expect(tag.getIntOr("kiln", 0), 5);
            }
        });
        b.add("lib.dataFixerUpper", INIT, () -> new Probe() {
            public Object run() {
                return expect(Codec.INT.encodeStart(JsonOps.INSTANCE, 5).getOrThrow(), 5);
            }
        });
        b.add("lib.gson", INIT, () -> new Probe() {
            public Object run() {
                return expect(new Gson().toJson(Map.of("kiln", 1)), "{\"kiln\":1}");
            }
        });
        b.add("lib.guava", INIT, () -> new Probe() {
            public Object run() {
                return expect(ImmutableList.of(1, 2, 3).reverse(), "[3, 2, 1]");
            }
        });
        b.add("lib.fastutil", INIT, () -> new Probe() {
            public Object run() {
                Int2ObjectOpenHashMap<String> map = new Int2ObjectOpenHashMap<>();
                map.put(7, "kiln");
                return expect(map.get(7), "kiln");
            }
        });
        b.add("lib.slf4j", INIT, () -> new Probe() {
            public Object run() {
                org.slf4j.Logger logger = LoggerFactory.getLogger(KilnTestbenchClient.ID);
                logger.info("SLF4J probe");
                return expect(logger.getName(), KilnTestbenchClient.ID);
            }
        });
        b.add("lib.mojangLogging", INIT, () -> new Probe() {
            public Object run() {
                // LogUtils names the logger after the class that asks: this probe, a class in DataProbes.
                org.slf4j.Logger logger = LogUtils.getLogger();
                logger.info("[Kiln Testbench] LogUtils probe");
                check(logger.getName().startsWith(DataProbes.class.getName()), "named " + logger.getName());
                return logger.getName().substring(logger.getName().lastIndexOf('.') + 1);
            }
        });
        b.add("lib.joml", INIT, () -> new Probe() {
            public Object run() {
                return expect(new Vector3f(1, 2, 2).length(), 3.0f);
            }
        });
        b.add("lib.brigadier", INIT, () -> new Probe() {
            public Object run() throws Exception {
                CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
                dispatcher.register(LiteralArgumentBuilder.literal("kiln").executes(c -> 7));
                return expect(dispatcher.execute("kiln", new Object()), 7);
            }
        });
    }
}
