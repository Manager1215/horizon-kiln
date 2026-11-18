package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.check;
import static io.github.manager1215.kilntestbench.client.Game.expect;
import static io.github.manager1215.kilntestbench.client.Game.level;
import static io.github.manager1215.kilntestbench.client.Game.player;
import static io.github.manager1215.kilntestbench.client.Game.within;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.WORLD;

import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Probe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** The client's level: time, weather, blocks, biomes, chunks, entities, effects. */
final class LevelProbes {
    private LevelProbes() {}

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
                return level().getChunk(x, z).getPos();
            }
        });
        b.add("level.entities", WORLD, () -> new Probe() {
            public Object run() {
                int n = 0;
                for (Object ignored : level().entitiesForRendering()) {
                    n++;
                }
                return expect(n > 0, true);
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
                        0, 0, 0);
                return "added";
            }
        });
        b.add("level.particleKinds", WORLD, () -> new Probe() {
            public Object run() {
                // Seen however far away, and past the particle limit: each where it was asked for.
                level().addAlwaysVisibleParticle(ParticleTypes.END_ROD, player().getX(), player().getY() + 3,
                        player().getZ(), 0, 0, 0);
                level().addParticle(ParticleTypes.FLAME, true, true, player().getX(), player().getY() + 4,
                        player().getZ(), 0, 0, 0);
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
        b.add("level.localSound", WORLD, () -> new Probe() {
            public Object run() {
                level().playLocalSound(player().getX(), player().getY(), player().getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.2f, 1f, false);
                return "played";
            }
        });
    }
}
