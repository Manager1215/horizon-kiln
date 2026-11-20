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
    /** The player's fishing hook while one is out, as the game had it with the tick; null otherwise. */
    public net.minecraft.world.entity.projectile.FishingHook fishing;

    /**
     * The sprint started or stopped, as the game's own sprint key would: Horizon's player sprints from the next tick
     * (and stops by the game's rules: hungry, against a wall, no longer moving forward).
     */
    @Override
    public void setSprinting(boolean sprinting) {
        if (sprinting != isSprinting()) {
            kiln.Back.act("sprinting", sprinting ? "1" : "0");
        }
        super.setSprinting(sprinting);
    }

    /**
     * A mod turned the window's player: the yaw and pitch go back to the game, which turns the player and the
     * camera from the next tick, as horizon-ctl's {@code look}. Another player's stays in Java.
     */
    @Override
    protected void kiln$turned() {
        if (this == net.minecraft.client.Minecraft.getInstance().player) {
            kiln.Back.act("look", getYRot() + " " + getXRot());
        }
    }

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
            Item item = Item.kiln$byId(w[1].contains("|") ? w[1].substring(0, w[1].indexOf('|')) : w[1]);
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
        getAbilities().kiln$gameFlyingSpeed((float) decimal(state, "flying_speed", 0.05));
        getAbilities().invulnerable = flag(state, "invulnerable");
        getAbilities().instabuild = isCreative();
        getAbilities().mayBuild = gameMode != 2 && gameMode != 3;
        onGround = flag(state, "on_ground");
        wasTouchingWater = flag(state, "in_water");
        sprinting = flag(state, "sprinting");
        fallFlying = flag(state, "fall_flying");
        horizontalCollision = flag(state, "horizontal_collision");
        minorHorizontalCollision = flag(state, "minor_collision");
        kiln$hook(state.getOrDefault("hook", ""));
        kiln$leftHanded = flag(state, "left_handed");
        String[] v = state.getOrDefault("velocity", "0 0 0").split(" ");
        if (v.length == 3) {
            deltaMovement = new net.minecraft.world.phys.Vec3(Double.parseDouble(v[0]), Double.parseDouble(v[1]),
                    Double.parseDouble(v[2]));
        }
        armorValue = decimal(state, "armor", 0.0);
        yRot = (float) decimal(state, "yaw", 0.0);
        xRot = (float) decimal(state, "pitch", 0.0);
        yHeadRot = yRot;
        net.minecraft.core.NonNullList<ItemStack> items =
                net.minecraft.core.NonNullList.withSize(net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE, ItemStack.EMPTY);
        net.minecraft.world.SimpleContainer grid = getInventory().kiln$grid();
        grid.clearContent();
        for (String entry : slots.split(";")) {
            String[] w = entry.split(" ");
            if (w.length != 5) {
                continue;
            }
            int slot = Integer.parseInt(w[0]);
            if (slot >= 100 && slot < 109) {
                // The crafting grid's cell (the game's slot 100 and up, D-166).
                ItemStack cell = stack(w[1], w[2]);
                cell.setDamageValue(Integer.parseInt(w[3]));
                grid.setItem(slot - 100, cell);
                continue;
            }
            if (slot < 0 || slot >= items.size()) {
                continue;
            }
            Item item = Item.kiln$byId(w[1].contains("|") ? w[1].substring(0, w[1].indexOf('|')) : w[1]);
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

    /** Kiln's own: the fishing hook the game said, {@code <x> <y> <z> <dx> <dy> <dz>}, or none. */
    private void kiln$hook(String field) {
        String[] w = field.isEmpty() ? new String[0] : field.split(" ");
        if (w.length != 6) {
            fishing = null;
            return;
        }
        try {
            double[] v = new double[6];
            for (int i = 0; i < 6; i++) {
                v[i] = Double.parseDouble(w[i]);
            }
            if (fishing == null) {
                fishing = new net.minecraft.world.entity.projectile.FishingHook(this);
            }
            fishing.kiln$set(v[0], v[1], v[2], new net.minecraft.world.phys.Vec3(v[3], v[4], v[5]));
        } catch (NumberFormatException e) {
            fishing = null;
        }
    }

    /**
     * The effect taken off the window's player, as a mod does it on the client (vanilla's {@code removeEffect} there):
     * Horizon takes it off the game's player with its next tick, so its look goes (darkness, nausea) and the HUD
     * stops showing it.
     */
    @Override
    public boolean removeEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        boolean had = activeEffects.remove(effect) != null;
        if (had) {
            effect.unwrapKey().ifPresent(k -> kiln.Back.act("remove_effect", k.identifier().toString()));
        }
        return had;
    }

    /** A sound the player makes: the window's player hears it where they stand. */
    @Override
    public void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level != null && sound != null) {
            level.playLocalSound(getX(), getY(), getZ(), sound, net.minecraft.sounds.SoundSource.PLAYERS, volume, pitch, false);
        }
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

    /**
     * A slot's stack as the tick says it: {@code <id>}, or {@code <id>|<the stack's SNBT>} %-escaped when it has
     * components (D-169: a water bottle is a potion with its contents, as a recipe viewer's "can I do it" needs).
     */
    private static ItemStack stack(String item, String count) {
        int n = Integer.parseInt(count);
        if (item.isEmpty() || n <= 0) {
            return ItemStack.EMPTY;
        }
        int bar = item.indexOf('|');
        if (bar >= 0) {
            String snbt = item.substring(bar + 1).replace("%7C", "|").replace("%3B", ";").replace("%20", " ").replace("%25", "%");
            try {
                ItemStack whole = ItemStack.kiln$fromSnbt(snbt);
                if (!whole.isEmpty()) {
                    whole.setCount(n);
                    return whole;
                }
            } catch (RuntimeException e) {
                // As a plain stack of its item.
            }
            item = item.substring(0, bar);
        }
        return new ItemStack(Item.kiln$byId(item), n);
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.client.player.ClientInput input;
    protected net.minecraft.client.Minecraft minecraft;
    public float yBob;
    public float xBob;
    public float yBobO;
    public float xBobO;
    private int waterVisionTime;
    public void displayItemActivation(net.minecraft.world.item.ItemStack p0) { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.displayItemActivation"); }
    public void itemUsed(net.minecraft.world.InteractionHand p0) { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.itemUsed"); }
    public net.minecraft.client.player.FirstPersonHandsAndItems firstPersonHandsAndItems() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.firstPersonHandsAndItems"); return null; }
    public float getCurrentMood() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.getCurrentMood"); return 0.0F; }
    public void closeContainer() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.closeContainer"); }
    public void clientSideCloseContainer() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.clientSideCloseContainer"); }
    public void hurtTo(float p0) { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.hurtTo"); }
    public boolean isSuppressingSlidingDownLadder() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.isSuppressingSlidingDownLadder"); return false; }
    public void sendOpenInventory() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.sendOpenInventory"); }
    public net.minecraft.stats.StatsCounter getStats() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.getStats"); return null; }
    public net.minecraft.client.ClientRecipeBook getRecipeBook() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.getRecipeBook"); return null; }
    public net.minecraft.server.permissions.PermissionSet permissions() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.permissions"); return null; }
    public net.minecraft.client.multiplayer.chat.ChatAbilities chatAbilities() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.chatAbilities"); return null; }
    public boolean shouldShowDeathScreen() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.shouldShowDeathScreen"); return false; }
    public boolean getDoLimitedCrafting() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.getDoLimitedCrafting"); return false; }
    public boolean isUsingItem() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.isUsingItem"); return false; }
    public net.minecraft.world.InteractionHand getUsedItemHand() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.getUsedItemHand"); return null; }
    public net.minecraft.world.entity.PlayerRideableJumping jumpableVehicle() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.jumpableVehicle"); return null; }
    public float getJumpRidingScale() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.getJumpRidingScale"); return 0.0F; }
    public void openTextEdit(net.minecraft.world.level.block.entity.SignBlockEntity p0, net.minecraft.world.level.block.entity.SignTextSlot p1) { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.openTextEdit"); }
    public boolean isCrouching() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.isCrouching"); return false; }
    public boolean isMovingSlowly() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.isMovingSlowly"); return false; }
    public void resetPos() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.resetPos"); }
    public boolean isHandsBusy() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.isHandsBusy"); return false; }
    public void move(net.minecraft.world.entity.MoverType p0, net.minecraft.world.phys.Vec3 p1) { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.move"); }
    public float getWaterVision() { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.getWaterVision"); return 0.0F; }
    public void onGameModeChanged(net.minecraft.world.level.GameType p0) { kiln.Missing.called("net.minecraft.client.player.LocalPlayer.onGameModeChanged"); }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
}
