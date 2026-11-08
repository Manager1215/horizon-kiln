package net.minecraft.client.player;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import kiln.Native;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Kiln's stand-in for {@code LocalPlayer}: the player in Horizon's window. */
public class LocalPlayer extends Player {
    /** The connection to the world the player is in. */
    public net.minecraft.client.multiplayer.ClientPacketListener connection;

    /** A line in the player's chat, shown only to them, with its style. */
    @Override
    public void sendSystemMessage(Component message) {
        Native.chat(message);
    }

    /** A line over the hotbar; Kiln shows it in chat, with its style. */
    @Override
    public void sendOverlayMessage(Component message) {
        Native.chat(message);
    }

    @Override
    public Level level() {
        return Minecraft.getInstance().level;
    }

    @Override
    protected boolean kiln$client() {
        return true;
    }

    /** The window's player's tick, with the client's level tick: {@link Player#tick}'s (D-093). */
    @Override
    public void tick() {
        super.tick();
    }

    /**
     * Kiln's own: the player as the window's game has them this tick, from the tick's fields at {@code at}: {@code
     * <uuid> <name> <dimension> <x> <y> <z> <game mode> <health> <main hand item> <count> <off hand item> <count>
     * <attributes> <sneaking> <level> <progress> <total> <main hand stack> <off hand stack>}, as a server call
     * carries a player. A UUID of 0 (Horizon without one) is the name's offline one.
     */
    public void kiln$update(String[] f, int at) {
        UUID id = UUID.fromString(f[at]);
        name = f[at + 1];
        uuid = id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0
                ? UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)) : id;
        x = Double.parseDouble(f[at + 3]);
        y = Double.parseDouble(f[at + 4]);
        z = Double.parseDouble(f[at + 5]);
        gameMode = Integer.parseInt(f[at + 6]);
        health = Float.parseFloat(f[at + 7]);
        mainHand = f.length > at + 17 && !f[at + 17].isEmpty() ? ItemStack.kiln$fromSnbt(f[at + 17]) : stack(f[at + 8], f[at + 9]);
        offHand = f.length > at + 18 && !f[at + 18].isEmpty() ? ItemStack.kiln$fromSnbt(f[at + 18]) : stack(f[at + 10], f[at + 11]);
        if (f.length > at + 16) {
            setShiftKeyDown(f[at + 13].equals("1"));
            kiln$experience(Integer.parseInt(f[at + 14]), Float.parseFloat(f[at + 15]), Integer.parseInt(f[at + 16]));
        }
    }

    /**
     * Kiln's own: what the player wears and holds, with each item's damage: {@code <slot> <item> <count> <damage>
     * <max damage>}, joined by {@code ;} (vanilla's slot names). A slot not named holds nothing.
     */
    public void kiln$equipment(String field) {
        java.util.Map<EquipmentSlot, ItemStack> now = new java.util.EnumMap<>(EquipmentSlot.class);
        for (String entry : field.split(";")) {
            String[] w = entry.split(" ");
            EquipmentSlot slot = w.length == 5 ? EquipmentSlot.kiln$byName(w[0]) : null;
            if (slot == null) {
                continue;
            }
            Item item = Item.kiln$byId(w[1]);
            int maxDamage = Integer.parseInt(w[4]);
            if (maxDamage > 0 && item.kiln$maxDamage() == 0) {
                item.kiln$maxDamage(maxDamage);
            }
            ItemStack stack = stack(w[1], w[2]);
            stack.setDamageValue(Integer.parseInt(w[3]));
            now.put(slot, stack);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setItemSlot(slot, now.getOrDefault(slot, ItemStack.EMPTY));
        }
    }

    /**
     * Kiln's own: the rest of what the game said of the player with the tick: {@code key=value} pairs joined by
     * {@code ;} (food, abilities, motion, rotation, the slot in hand), and the 36 slots that hold something
     * ({@code <slot> <item> <count> <damage> <max damage>}, joined by {@code ;}).
     */
    public void kiln$state(java.util.Map<String, String> state, String slots) {
        getFoodData().kiln$set(number(state, "food", 20), (float) decimal(state, "saturation", 5.0));
        getAbilities().flying = flag(state, "flying");
        getAbilities().mayfly = flag(state, "may_fly");
        getAbilities().invulnerable = flag(state, "invulnerable");
        getAbilities().instabuild = isCreative();
        getAbilities().mayBuild = gameMode != 2 && gameMode != 3;
        onGround = flag(state, "on_ground");
        wasTouchingWater = flag(state, "in_water");
        sprinting = flag(state, "sprinting");
        String[] v = state.getOrDefault("velocity", "0 0 0").split(" ");
        if (v.length == 3) {
            deltaMovement = new net.minecraft.world.phys.Vec3(Double.parseDouble(v[0]), Double.parseDouble(v[1]),
                    Double.parseDouble(v[2]));
        }
        armorValue = decimal(state, "armor", 0.0);
        yRot = (float) decimal(state, "yaw", 0.0);
        xRot = (float) decimal(state, "pitch", 0.0);
        net.minecraft.core.NonNullList<ItemStack> items =
                net.minecraft.core.NonNullList.withSize(net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE, ItemStack.EMPTY);
        for (String entry : slots.split(";")) {
            String[] w = entry.split(" ");
            if (w.length != 5) {
                continue;
            }
            int slot = Integer.parseInt(w[0]);
            if (slot < 0 || slot >= items.size()) {
                continue;
            }
            Item item = Item.kiln$byId(w[1]);
            int maxDamage = Integer.parseInt(w[4]);
            if (maxDamage > 0 && item.kiln$maxDamage() == 0) {
                item.kiln$maxDamage(maxDamage);
            }
            ItemStack stack = stack(w[1], w[2]);
            stack.setDamageValue(Integer.parseInt(w[3]));
            items.set(slot, stack);
        }
        getInventory().kiln$set(items, number(state, "selected", 0));
        kiln$effects(state.getOrDefault("effects", ""));
    }

    /**
     * Kiln's own: the effects the game said, {@code <id>:<ticks>:<amplifier>:<ambient 0|1>:<particles 0|1>} joined by
     * {@code ,} (the id %-escaped; the game's own without their namespace). One the Java mods haven't registered is
     * an entry of its own.
     */
    private void kiln$effects(String field) {
        java.util.Map<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>, net.minecraft.world.effect.MobEffectInstance> now =
                new java.util.LinkedHashMap<>();
        for (String entry : field.isEmpty() ? new String[0] : field.split(",")) {
            String[] e = entry.split(":");
            if (e.length != 5) {
                continue;
            }
            String name = java.net.URLDecoder.decode(e[0], java.nio.charset.StandardCharsets.UTF_8);
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(
                    name.contains(":") ? name : "minecraft:" + name);
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect =
                    net.minecraft.world.effect.MobEffect.kiln$entry(id);
            net.minecraft.world.effect.MobEffectInstance instance = activeEffects.get(effect);
            if (instance == null) {
                instance = new net.minecraft.world.effect.MobEffectInstance(effect);
            }
            instance.kiln$set(Integer.parseInt(e[1]), Integer.parseInt(e[2]), e[3].equals("1"), e[4].equals("1"));
            now.put(effect, instance);
        }
        activeEffects.clear();
        activeEffects.putAll(now);
    }

    private static int number(java.util.Map<String, String> state, String key, int fallback) {
        try {
            return Integer.parseInt(state.getOrDefault(key, ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double decimal(java.util.Map<String, String> state, String key, double fallback) {
        try {
            return Double.parseDouble(state.getOrDefault(key, ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean flag(java.util.Map<String, String> state, String key) {
        return "1".equals(state.get(key));
    }

    private static ItemStack stack(String item, String count) {
        int n = Integer.parseInt(count);
        return item.isEmpty() || n <= 0 ? ItemStack.EMPTY : new ItemStack(Item.kiln$byId(item), n);
    }
}
