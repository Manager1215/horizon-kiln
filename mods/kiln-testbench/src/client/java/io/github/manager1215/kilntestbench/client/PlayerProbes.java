package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.check;
import static io.github.manager1215.kilntestbench.client.Game.expect;
import static io.github.manager1215.kilntestbench.client.Game.within;
import static io.github.manager1215.kilntestbench.client.Game.level;
import static io.github.manager1215.kilntestbench.client.Game.mc;
import static io.github.manager1215.kilntestbench.client.Game.player;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.WORLD;

import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Probe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** The player: where, how healthy, what it holds. */
final class PlayerProbes {
    private PlayerProbes() {}

    static void add(Bench b) {
        b.add("player.xyz", WORLD, () -> new Probe() {
            public Object run() {
                // getX, getY and getZ are position()'s.
                Vec3 p = player().position();
                expect(player().getX(), p.x);
                expect(player().getY(), p.y);
                expect(player().getZ(), p.z);
                return String.format("%.1f %.1f %.1f", player().getX(), player().getY(), player().getZ());
            }
        });
        b.add("player.position", WORLD, () -> new Probe() {
            public Object run() {
                Vec3 p = player().position();
                check(Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z), "not a place: " + p);
                // Within the world's height (a player may stand a little above the top, never below the bottom by much).
                within(p.y, level().getMinY() - 64, level().getMaxY() + 512);
                return p;
            }
        });
        b.add("player.blockPosition", WORLD, () -> new Probe() {
            public Object run() {
                // The block the player is in: their position, rounded down.
                Vec3 p = player().position();
                expect(player().blockPosition(), BlockPos.containing(p.x, p.y, p.z));
                return player().blockPosition();
            }
        });
        b.add("player.eyePosition", WORLD, () -> new Probe() {
            public Object run() {
                // The eyes are getEyeHeight() above the feet, straight up.
                Vec3 eye = player().getEyePosition();
                double height = eye.y - player().getY();
                check(Math.abs(height - player().getEyeHeight()) < 1e-5, height + " up, not " + player().getEyeHeight());
                within(player().getEyeHeight(), 0.2, 1.7);
                expect(eye.x, player().getX());
                expect(eye.z, player().getZ());
                return height;
            }
        });
        b.add("player.rotation", WORLD, () -> new Probe() {
            public Object run() {
                float pitch = player().getXRot();
                float yaw = player().getYRot();
                within(pitch, -90, 90);
                check(Float.isFinite(yaw), "yaw " + yaw);
                // Where the player looks follows from the two: up is a negative pitch, yaw 0 looks south (+z) and 90 west.
                Vec3 look = player().getLookAngle();
                double p = Math.toRadians(pitch);
                double y = Math.toRadians(yaw);
                Vec3 expected = new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
                check(look.distanceToSqr(expected.x, expected.y, expected.z) < 1e-6, "looks " + look + ", not " + expected);
                return yaw + " " + pitch;
            }
        });
        b.add("player.deltaMovement", WORLD, () -> new Probe() {
            public Object run() {
                Vec3 v = player().getDeltaMovement();
                // Blocks a tick: nobody walks or falls 100 a tick, and on the ground nothing lifts them.
                within(v.length(), 0, 100);
                check(!player().onGround() || v.y <= 0, "on the ground, rising " + v.y);
                return v;
            }
        });
        b.add("player.health", WORLD, () -> new Probe() {
            public Object run() {
                // Alive, at most at full health, which is the max_health attribute.
                check(player().getHealth() > 0, "dead: " + player().getHealth());
                check(player().getHealth() <= player().getMaxHealth(), "more health than the max");
                expect(player().getMaxHealth(), (float) player().getAttributeValue(Attributes.MAX_HEALTH));
                return player().getHealth() + " of " + player().getMaxHealth();
            }
        });
        b.add("player.food", WORLD, () -> new Probe() {
            public Object run() {
                int food = player().getFoodData().getFoodLevel();
                float saturation = player().getFoodData().getSaturationLevel();
                within(food, 0, 20);
                // Saturation never passes the food level; a player needs food below 20.
                within(saturation, 0, food);
                expect(player().getFoodData().needsFood(), food < 20);
                return food + " " + saturation;
            }
        });
        b.add("player.armor", WORLD, () -> new Probe() {
            public Object run() {
                // The armor attribute's value, rounded down.
                return expect(player().getArmorValue(), Mth.floor(player().getAttributeValue(Attributes.ARMOR)));
            }
        });
        b.add("player.effects", WORLD, () -> new Probe() {
            public Object run() {
                // Each active effect is had, and the same in the map by effect.
                var effects = player().getActiveEffects();
                expect(effects.size(), player().getActiveEffectsMap().size());
                for (var effect : effects) {
                    check(player().hasEffect(effect.getEffect()), "doesn't have " + effect);
                    check(player().getEffect(effect.getEffect()) == effect, "another instance of " + effect);
                    check(effect.getDuration() != 0, "an effect that ran out: " + effect);
                }
                return effects.size();
            }
        });
        b.add("player.experience", WORLD, () -> new Probe() {
            public Object run() {
                int level = player().experienceLevel;
                check(level >= 0 && player().totalExperience >= 0, "negative experience");
                within(player().experienceProgress, 0, 1);
                // The points the next level takes, as vanilla counts them.
                expect(player().getXpNeededForNextLevel(), level >= 30 ? 112 + (level - 30) * 9
                        : level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2);
                return level + " " + player().totalExperience;
            }
        });
        b.add("player.inventory", WORLD, () -> new Probe() {
            public Object run() {
                Inventory inventory = player().getInventory();
                // 36 slots, then the worn, the off hand, the body and the saddle.
                expect(inventory.getContainerSize(), 43);
                within(inventory.getSelectedSlot(), 0, Inventory.SELECTION_SIZE - 1);
                return inventory.getContainerSize() + " slots, slot " + inventory.getSelectedSlot() + " selected";
            }
        });
        b.add("player.selectedItem", WORLD, () -> new Probe() {
            public Object run() {
                // The selected item is the selected slot's.
                Inventory inventory = player().getInventory();
                ItemStack selected = inventory.getSelectedItem();
                check(ItemStack.matches(selected, inventory.getItem(inventory.getSelectedSlot())), "not the slot's");
                return selected.isEmpty() ? "nothing" : selected.getCount() + " " + selected.getItem();
            }
        });
        b.add("player.mainHand", WORLD, () -> new Probe() {
            public Object run() {
                // The main hand holds the selected slot's item.
                ItemStack main = player().getMainHandItem();
                check(ItemStack.matches(main, player().getInventory().getSelectedItem()), main + " isn't the selected item");
                return main.getCount();
            }
        });
        b.add("player.name", WORLD, () -> new Probe() {
            public Object run() {
                String name = player().getName().getString();
                check(!name.isEmpty(), "no name");
                expect(player().getScoreboardName(), name);
                return true;
            }
        });
        b.add("player.uuid", WORLD, () -> new Probe() {
            public Object run() {
                // Name-based (offline, 3) or random (signed in, 4), and its string the same.
                check(player().getUUID().version() == 3 || player().getUUID().version() == 4,
                        "version " + player().getUUID().version());
                expect(player().getStringUUID(), player().getUUID());
                return player().getUUID().version();
            }
        });
        b.add("player.gameMode", WORLD, () -> new Probe() {
            public Object run() {
                check(!(player().isCreative() && player().isSpectator()), "creative and spectator");
                // Creative builds instantly; creative and spectator fly.
                expect(player().getAbilities().instabuild, player().isCreative());
                if (player().isCreative() || player().isSpectator()) {
                    check(player().getAbilities().mayfly, "creative or spectator and may not fly");
                }
                return "creative " + player().isCreative() + ", spectator " + player().isSpectator();
            }
        });
        b.add("player.abilities", WORLD, () -> new Probe() {
            public Object run() {
                check(!player().getAbilities().flying || player().getAbilities().mayfly, "flying and may not fly");
                // Creative and spectator players take no harm.
                if (player().isCreative() || player().isSpectator()) {
                    check(player().getAbilities().invulnerable, "creative or spectator and vulnerable");
                }
                return "flying " + player().getAbilities().flying + ", may fly " + player().getAbilities().mayfly;
            }
        });
        b.add("player.state", WORLD, () -> new Probe() {
            public Object run() {
                // Sneaking is holding the sneak key; on the ground nobody flies (landing ends flight).
                expect(player().isShiftKeyDown(), mc().options.keyShift.isDown());
                check(!player().onGround() || !player().getAbilities().flying || player().isSpectator(), "flying on the ground");
                return "on ground " + player().onGround() + ", sneaking " + player().isShiftKeyDown() + ", in water "
                        + player().isInWater() + ", sprinting " + player().isSprinting();
            }
        });
        b.add("player.level", WORLD, () -> new Probe() {
            public Object run() {
                return expect(player().level() == level(), true);
            }
        });
        b.add("player.type", WORLD, () -> new Probe() {
            public Object run() {
                return expect(BuiltInRegistries.ENTITY_TYPE.getKey(player().getType()), "minecraft:player");
            }
        });
        b.add("player.systemMessage", WORLD, () -> new Probe() {
            public Object run() {
                player().sendSystemMessage(Component.literal("[Kiln Testbench] player probe"));
                // A system message to the window's own player lands in its chat.
                return "sent";
            }
        });
        b.add("player.overlayMessage", WORLD, () -> new Probe() {
            public Object run() {
                player().sendOverlayMessage(Component.literal("Kiln Testbench"));
                return "sent";
            }
        });
    }
}
