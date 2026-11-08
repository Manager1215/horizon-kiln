package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.check;
import static io.github.manager1215.kilntestbench.client.Game.expect;
import static io.github.manager1215.kilntestbench.client.Game.level;
import static io.github.manager1215.kilntestbench.client.Game.player;
import static io.github.manager1215.kilntestbench.client.Game.within;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.WORLD;

import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Probe;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

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
                return expect(level().getDefaultClockTime() >= 0, true);
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
                check(!level().isBrightOutside() || !level().dimension().identifier().toString().equals("minecraft:the_nether")
                        && !level().dimension().identifier().toString().equals("minecraft:the_end"), "bright outside "
                        + level().dimension().identifier());
                return "bright " + level().isBrightOutside();
            }
        });
        b.add("level.biome", WORLD, () -> new Probe() {
            public Object run() {
                return level().getBiome(player().blockPosition()).unwrapKey().orElseThrow().identifier()
                        .getNamespace();
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
                return (int) within(level().getMaxLocalRawBrightness(player().blockPosition()), 0, 15);
            }
        });
        b.add("level.chunk", WORLD, () -> new Probe() {
            public Object run() {
                // The chunk asked for, by the player's block: 16 blocks a chunk.
                int x = player().blockPosition().getX() >> 4;
                int z = player().blockPosition().getZ() >> 4;
                return expect(level().getChunk(x, z).getPos(), "[" + x + ", " + z + "]");
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
        b.add("level.localSound", WORLD, () -> new Probe() {
            public Object run() {
                level().playLocalSound(player().getX(), player().getY(), player().getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.2f, 1f, false);
                return "played";
            }
        });
    }
}
