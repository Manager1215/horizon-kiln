package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.expect;
import static io.github.manager1215.kilntestbench.client.Game.level;
import static io.github.manager1215.kilntestbench.client.Game.player;
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
                return level().getGameTime() > 0;
            }
        });
        b.add("level.clockTime", WORLD, () -> new Probe() {
            public Object run() {
                return level().getDefaultClockTime() >= 0;
            }
        });
        b.add("level.dimension", WORLD, () -> new Probe() {
            public Object run() {
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
                return "raining " + level().isRaining() + ", thundering " + level().isThundering();
            }
        });
        b.add("level.daylight", WORLD, () -> new Probe() {
            public Object run() {
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
                return level().getMinY() + ".." + level().getMaxY() + ", sea " + level().getSeaLevel();
            }
        });
        b.add("level.difficulty", WORLD, () -> new Probe() {
            public Object run() {
                return level().getDifficulty();
            }
        });
        b.add("level.light", WORLD, () -> new Probe() {
            public Object run() {
                return level().getMaxLocalRawBrightness(player().blockPosition());
            }
        });
        b.add("level.chunk", WORLD, () -> new Probe() {
            public Object run() {
                return level().getChunk(player().blockPosition().getX() >> 4, player().blockPosition().getZ() >> 4)
                        .getPos();
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
