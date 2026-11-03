package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.expect;
import static io.github.manager1215.kilntestbench.client.Game.level;
import static io.github.manager1215.kilntestbench.client.Game.player;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.WORLD;

import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Probe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

/** The player: where, how healthy, what it holds. */
final class PlayerProbes {
    private PlayerProbes() {}

    static void add(Bench b) {
        b.add("player.xyz", WORLD, () -> new Probe() {
            public Object run() {
                return String.format("%.1f %.1f %.1f", player().getX(), player().getY(), player().getZ());
            }
        });
        b.add("player.position", WORLD, () -> new Probe() {
            public Object run() {
                return player().position();
            }
        });
        b.add("player.blockPosition", WORLD, () -> new Probe() {
            public Object run() {
                return player().blockPosition();
            }
        });
        b.add("player.eyePosition", WORLD, () -> new Probe() {
            public Object run() {
                return player().getEyePosition().y - player().getY();
            }
        });
        b.add("player.rotation", WORLD, () -> new Probe() {
            public Object run() {
                return player().getYRot() + " " + player().getXRot();
            }
        });
        b.add("player.deltaMovement", WORLD, () -> new Probe() {
            public Object run() {
                return player().getDeltaMovement();
            }
        });
        b.add("player.health", WORLD, () -> new Probe() {
            public Object run() {
                return player().getHealth() + " of " + player().getMaxHealth();
            }
        });
        b.add("player.food", WORLD, () -> new Probe() {
            public Object run() {
                return player().getFoodData().getFoodLevel() + " " + player().getFoodData().getSaturationLevel();
            }
        });
        b.add("player.armor", WORLD, () -> new Probe() {
            public Object run() {
                return player().getArmorValue();
            }
        });
        b.add("player.effects", WORLD, () -> new Probe() {
            public Object run() {
                return player().getActiveEffects().size();
            }
        });
        b.add("player.experience", WORLD, () -> new Probe() {
            public Object run() {
                return player().experienceLevel + " " + player().totalExperience;
            }
        });
        b.add("player.inventory", WORLD, () -> new Probe() {
            public Object run() {
                return player().getInventory().getContainerSize() + " slots, slot "
                        + player().getInventory().getSelectedSlot() + " selected";
            }
        });
        b.add("player.selectedItem", WORLD, () -> new Probe() {
            public Object run() {
                return player().getInventory().getSelectedItem().isEmpty();
            }
        });
        b.add("player.mainHand", WORLD, () -> new Probe() {
            public Object run() {
                return player().getMainHandItem().getCount();
            }
        });
        b.add("player.name", WORLD, () -> new Probe() {
            public Object run() {
                return !player().getName().getString().isEmpty();
            }
        });
        b.add("player.uuid", WORLD, () -> new Probe() {
            public Object run() {
                return player().getUUID().version();
            }
        });
        b.add("player.gameMode", WORLD, () -> new Probe() {
            public Object run() {
                return "creative " + player().isCreative() + ", spectator " + player().isSpectator();
            }
        });
        b.add("player.abilities", WORLD, () -> new Probe() {
            public Object run() {
                return "flying " + player().getAbilities().flying + ", may fly " + player().getAbilities().mayfly;
            }
        });
        b.add("player.state", WORLD, () -> new Probe() {
            public Object run() {
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
