package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.check;
import static io.github.manager1215.kilntestbench.client.Game.expect;
import static io.github.manager1215.kilntestbench.client.Game.level;
import static io.github.manager1215.kilntestbench.client.Game.player;
import static io.github.manager1215.kilntestbench.client.Game.within;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.LATE;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.WORLD;

import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Probe;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/** The client's level: time, weather, blocks, biomes, chunks, entities, effects. */
final class LevelProbes {
    private LevelProbes() {}

    /** A block a chunk east of the player's, under their feet: away from what the game says with each tick. */
    private static BlockPos far() {
        return player().blockPosition().below().offset(16, 0, 0);
    }

    static void add(Bench b) {
        b.add("level.gameTime", WORLD, () -> new Probe() {
            public Object run() {
                // A world a player is in has ticked.
                return expect(level().getGameTime() > 0, true);
            }
        });
        b.add("level.clockTime", WORLD, () -> new Probe() {
            public Object run() {
                // The time of day the game said: a day is 24000 ticks.
                long time = level().getDefaultClockTime();
                check(time >= 0, "time " + time);
                return time;
            }
        });
        b.add("level.dimension", WORLD, () -> new Probe() {
            public Object run() {
                // The player's level's, and a dimension's key.
                expect(level().dimension(), player().level().dimension());
                expect(level().dimension().registry(), "minecraft:dimension");
                return level().dimension().identifier();
            }
        });
        b.add("level.isClientSide", WORLD, () -> new Probe() {
            public Object run() {
                return expect(level().isClientSide(), true);
            }
        });
        b.add("level.blockState", WORLD, () -> new Probe() {
            public Object run() {
                return level().getBlockState(player().blockPosition().below()).getBlock().getName().getString()
                        .isEmpty() ? "no name" : "named";
            }
        });
        b.add("level.blockKey", WORLD, () -> new Probe() {
            public Object run() {
                return BuiltInRegistries.BLOCK.getKey(level().getBlockState(player().blockPosition()).getBlock())
                        .getNamespace();
            }
        });
        b.add("level.weather", WORLD, () -> new Probe() {
            public Object run() {
                float rain = level().getRainLevel(1.0F);
                within(rain, 0, 1);
                within(level().getThunderLevel(1.0F), 0, 1);
                // It rains when the rain level is above 0.2; it thunders only while it rains.
                expect(level().isRaining(), rain > 0.2F);
                check(!level().isThundering() || level().isRaining(), "thunder without rain");
                return "raining " + level().isRaining() + ", thundering " + level().isThundering();
            }
        });
        b.add("level.daylight", WORLD, () -> new Probe() {
            public Object run() {
                // Only the overworld has days: the nether and the end are never bright outside.
                // Only the overworld has days: the nether and the end are never bright outside. Elsewhere it is bright
                // while the sky is darkened by less than 4 of its 15 levels.
                String dimension = level().dimension().identifier().toString();
                boolean fixed = dimension.equals("minecraft:the_nether") || dimension.equals("minecraft:the_end");
                int darken = (int) within(level().getSkyDarken(), 0, 15);
                expect(level().isBrightOutside(), !fixed && darken < 4);
                return "bright " + level().isBrightOutside() + ", sky darkened " + darken;
            }
        });
        b.add("level.biome", WORLD, () -> new Probe() {
            public Object run() {
                return level().getBiome(player().blockPosition()).unwrapKey().orElseThrow().identifier();
            }
        });
        b.add("level.height", WORLD, () -> new Probe() {
            public Object run() {
                // From getMinY() up getHeight() blocks, in whole sections of 16.
                expect(level().getMaxY(), level().getMinY() + level().getHeight() - 1);
                check(level().getHeight() > 0 && level().getHeight() % 16 == 0, "height " + level().getHeight());
                check(level().getMinY() % 16 == 0, "bottom " + level().getMinY());
                return level().getMinY() + ".." + level().getMaxY() + ", sea " + level().getSeaLevel();
            }
        });
        b.add("level.difficulty", WORLD, () -> new Probe() {
            public Object run() {
                var difficulty = level().getDifficulty();
                check(difficulty != null, "no difficulty");
                return expect(net.minecraft.world.Difficulty.byId(difficulty.getId()), difficulty);
            }
        });
        b.add("level.light", WORLD, () -> new Probe() {
            public Object run() {
                // The brighter of the block's light and the sky's, the sky's darkened by the time and the weather.
                BlockPos at = player().blockPosition();
                int block = (int) within(level().getBrightness(LightLayer.BLOCK, at), 0, 15);
                int sky = (int) within(level().getBrightness(LightLayer.SKY, at), 0, 15);
                int raw = level().getMaxLocalRawBrightness(at);
                expect(raw, Math.max(block, sky - level().getSkyDarken()));
                return raw + " (block " + block + ", sky " + sky + ")";
            }
        });
        b.add("level.chunk", WORLD, () -> new Probe() {
            public Object run() {
                // The chunk asked for, by the player's block: 16 blocks a chunk.
                int x = player().blockPosition().getX() >> 4;
                int z = player().blockPosition().getZ() >> 4;
                expect(level().getChunk(x, z).getPos(), "[" + x + ", " + z + "]");
                expect(level().getChunkAt(player().blockPosition()).getPos(), level().getChunk(x, z).getPos());
                // The player's chunk is loaded; one 16000 blocks away isn't, and asked for without making it, is none.
                // (The client's hasChunk is true for any chunk, as the game's: its blocks come by its packets.)
                check(level().hasChunk(x, z) && level().hasChunk(x + 1000, z), "the client says it has no chunk");
                check(level().isLoaded(player().blockPosition()), "the player's block isn't loaded");
                check(level().getChunk(x, z, ChunkStatus.FULL, false) != null, "the player's chunk is none");
                check(!level().isLoaded(player().blockPosition().offset(16000, 0, 0)), "a block 16000 away is loaded");
                check(level().getChunk(x + 1000, z, ChunkStatus.FULL, false) == null, "a chunk far away is made");
                // Asked for to be made, it is an empty one (the game's client has one for all); the player's isn't empty.
                var made = level().getChunk(x + 1000, z, ChunkStatus.FULL, true);
                check(made != null && ((LevelChunk) made).isEmpty(), "a chunk far away made: " + made);
                check(!level().getChunk(x, z).isEmpty(), "the player's chunk is empty");
                return level().getChunk(x, z).getPos();
            }
        });
        b.add("level.entities", WORLD, () -> new Probe() {
            public Object run() {
                // The player among them, each once; the level counts as many.
                int n = 0;
                int players = 0;
                for (var e : level().entitiesForRendering()) {
                    n++;
                    players += e == player() ? 1 : 0;
                }
                expect(players, 1);
                return expect(level().getEntityCount(), n);
            }
        });
        b.add("level.entitiesNear", WORLD, () -> new Probe() {
            public Object run() {
                // The entities in a box: the player in a box around them, but not when left out, nor when the test
                // or the class turns them down, nor in a box far away.
                AABB around = player().getBoundingBox().inflate(1);
                AABB away = around.move(0, 1000, 0);
                check(level().getEntities((Entity) null, around).contains(player()), "the player isn't near themselves");
                check(!level().getEntities(player(), around).contains(player()), "the player left out is there");
                check(!level().getEntities((Entity) null, around, e -> e != player()).contains(player()),
                        "the test let the player in");
                check(!level().getEntities((Entity) null, away).contains(player()), "the player is 1000 blocks up");
                expect(level().getEntitiesOfClass(LocalPlayer.class, around), List.of(player()));
                expect(level().getEntitiesOfClass(LocalPlayer.class, around, p -> false), List.of());
                expect(level().getEntitiesOfClass(LocalPlayer.class, away), List.of());
                expect(level().getEntities(EntityTypeTest.forClass(LocalPlayer.class), around, p -> true),
                        List.of(player()));
                expect(level().getEntities(EntityTypeTest.forClass(LocalPlayer.class), away, p -> true), List.of());
                return level().getEntities((Entity) null, around).size() + " near";
            }
        });
        b.add("level.entityById", WORLD, () -> new Probe() {
            public Object run() {
                check(level().getEntity(player().getId() + 1_000_000) == null, "an entity no one is");
                return expect(level().getEntity(player().getId()) == player(), true);
            }
        });
        b.add("level.players", WORLD, () -> new Probe() {
            public Object run() {
                return expect(level().players().size(), 1);
            }
        });
        b.add("level.particle", WORLD, () -> new Probe() {
            public Object run() {
                level().addParticle(ParticleTypes.HAPPY_VILLAGER, player().getX(), player().getY() + 2, player().getZ(),
                        0.125, 0.25, -0.5);
                return "added";
            }
        });
        b.add("level.particleKinds", WORLD, () -> new Probe() {
            public Object run() {
                // Seen however far away, and past the particle limit: each where it was asked for.
                level().addAlwaysVisibleParticle(ParticleTypes.END_ROD, player().getX(), player().getY() + 3,
                        player().getZ(), 0.25, 0, 0);
                level().addParticle(ParticleTypes.FLAME, true, true, player().getX(), player().getY() + 4,
                        player().getZ(), 0, 0.25, 0);
                level().addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, player().getX(), player().getY() + 5,
                        player().getZ(), 0, 0, 0.25);
                return "added";
            }
        });
        b.add("level.blockSound", WORLD, () -> new Probe() {
            public Object run() {
                // A sound at a block plays at the block's middle.
                level().playLocalSound(player().blockPosition().above(5), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                        0.2f, 1f, false);
                return "played";
            }
        });
        b.add("level.chunkSections", WORLD, () -> new Probe() {
            public Object run() {
                // A chunk's sections, 16 blocks high each, from the level's bottom up: the player's block in the one
                // its height says.
                BlockPos at = player().blockPosition();
                LevelChunk chunk = level().getChunkAt(at);
                expect(chunk.getSections().length, level().getHeight() >> 4);
                expect(chunk.getMinSectionY(), level().getMinY() >> 4);
                expect(level().getMinSectionY(), chunk.getMinSectionY());
                int index = chunk.getSectionIndex(at.getY());
                expect(index, level().getSectionIndex(at.getY()));
                expect(index, (at.getY() >> 4) - (level().getMinY() >> 4));
                expect(chunk.getSectionYFromSectionIndex(index), at.getY() >> 4);
                // Asked now, read by level.farBlock: a block scanner asks for a chunk's blocks the game hasn't sent.
                level().getBlockState(far());
                return chunk.getSections().length + " sections from " + chunk.getMinSectionY();
            }
        });
        b.add("level.farBlock", LATE, () -> new Probe() {
            public Object run() {
                // A block a chunk away, read three ways that must agree: the level's, its chunk's, its section's cell.
                BlockPos far = far();
                BlockState state = level().getBlockState(far);
                LevelChunk chunk = level().getChunkAt(far);
                expect(chunk.getPos(), level().getChunk(far.getX() >> 4, far.getZ() >> 4).getPos());
                expect(chunk.getBlockState(far), state);
                LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(far.getY()));
                expect(section.getBlockState(far.getX() & 15, far.getY() & 15, far.getZ() & 15), state);
                return BuiltInRegistries.BLOCK.getKey(state.getBlock());
            }
        });
        b.add("level.entitySound", WORLD, () -> new Probe() {
            public Object run() {
                // A sound at an entity plays where it is.
                level().playLocalSound(player(), SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 0.2f, 1f);
                return "played";
            }
        });
        b.add("level.playerSound", WORLD, () -> new Probe() {
            public Object run() {
                // A sound for the players near a place: the client plays the one its own player makes, not one made
                // by no one (the server sends those).
                level().playSound(player(), player().getX(), player().getY() + 6, player().getZ(), SoundEvents.CHEST_OPEN,
                        SoundSource.PLAYERS, 0.2f, 1f);
                level().playSound(null, player().getX(), player().getY() + 6, player().getZ(), SoundEvents.ANVIL_LAND,
                        SoundSource.PLAYERS, 0.2f, 1f);
                return "played";
            }
        });
        b.add("level.localSound", WORLD, () -> new Probe() {
            public Object run() {
                level().playLocalSound(player().getX(), player().getY(), player().getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.2f, 1f, false);
                return "played";
            }
        });
    }
}
