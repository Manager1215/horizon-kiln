package net.minecraft.server.level;

import java.util.UUID;
import kiln.Server;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Kiln's stand-in for {@code ServerPlayer}: a player as the server saw them when it called the mod. */
public class ServerPlayer extends Player {
    private boolean quiet;
    private AbstractContainerMenu opened;
    /** Their connection to the server (its packets are Horizon's). */
    public net.minecraft.server.network.ServerGamePacketListenerImpl connection =
            new net.minecraft.server.network.ServerGamePacketListenerImpl(this);

    /** Kiln's own: the player the game described. */
    public ServerPlayer(UUID uuid, String name, ServerLevel level, double x, double y, double z, int gameMode, float health) {
        this.uuid = uuid;
        this.name = name;
        this.level = level;
        this.x = x;
        this.y = y;
        this.z = z;
        this.gameMode = gameMode;
        this.health = health;
        getAbilities().instabuild = gameMode == 1;
        getAbilities().invulnerable = gameMode == 1 || gameMode == 3;
        getAbilities().mayfly = gameMode == 1 || gameMode == 3;
        getAbilities().mayBuild = gameMode != 2 && gameMode != 3;
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel) level;
    }

    /** A line in their chat, or over their hotbar when {@code overlay}. */
    public void sendSystemMessage(net.minecraft.network.chat.Component message, boolean overlay) {
        if (overlay) {
            sendOverlayMessage(message);
        } else {
            sendSystemMessage(message);
        }
    }

    /**
     * Their permissions: the operator level the game gives them, asked during a server call (a recipe viewer's cheat
     * mode is for operators, D-167); none outside one.
     */
    public net.minecraft.server.permissions.PermissionSet permissions() {
        int level = 0;
        if (Server.inCall()) {
            try {
                level = Integer.parseInt(Server.ask("permission").trim());
            } catch (NumberFormatException e) {
                level = 0;
            }
        }
        return net.minecraft.server.permissions.LevelBasedPermissionSet.forLevel(
                net.minecraft.server.permissions.PermissionLevel.byId(level));
    }

    /** A command source for them, with their permissions, as the game makes one. */
    public net.minecraft.commands.CommandSourceStack createCommandSourceStack() {
        return net.minecraft.commands.CommandSourceStack.kiln$player(this, permissions());
    }

    /** Who they are, as the server lists players. */
    public net.minecraft.server.players.NameAndId nameAndId() {
        return new net.minecraft.server.players.NameAndId(getUUID(), getScoreboardName());
    }

    private net.minecraft.stats.ServerStatsCounter stats;

    /** Their statistics: Horizon's, asked for during a server call ({@code ServerStatsCounter}). */
    public net.minecraft.stats.ServerStatsCounter getStats() {
        if (stats == null) {
            stats = new net.minecraft.stats.ServerStatsCounter(getUUID());
        }
        return stats;
    }

    /**
     * Their game mode set, as the game does; false when it is that already. Kiln's player has it from here on in Java;
     * Horizon's player keeps theirs (what a mod's command changes on Horizon's server doesn't reach it yet).
     */
    public boolean setGameMode(net.minecraft.world.level.GameType mode) {
        if (gameMode == mode.getId()) {
            return false;
        }
        gameMode = mode.getId();
        return true;
    }

    /** Kiln's own: the game's values of an attribute, before the mod's code runs. */
    public void kiln$setAttribute(Holder<Attribute> attribute, double base, double value) {
        attributes.put(attribute, kiln$newAttribute(attribute, base, value));
    }

    /**
     * Kiln's own: what the player holds, before the mod's code runs. The main hand's stack is the inventory's in the
     * selected slot too (a mod finds its seeds there); the game's other slots aren't a server call's.
     */
    public void kiln$setHands(ItemStack main, ItemStack off) {
        mainHand = main;
        offHand = off;
        getInventory().kiln$hold(main);
    }

    /**
     * The player's server tick: Horizon ticks them; the mods' mixins in this (and in {@code Player}'s, {@code
     * LivingEntity}'s and {@code Entity}'s, which it calls first) run at Horizon's {@code player_tick} hook, with the
     * level's {@code isClientSide()} false (Kiln's Mixin bridge, D-102).
     */
    @Override
    public void tick() {
        super.tick();
    }

    /**
     * The player died, of {@code source}: Horizon kills them; the mods' mixins in this run at Horizon's {@code
     * player_died} hook (Kiln's Mixin bridge, D-106), and what they do to the player is done as they respawn.
     */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        kiln.Bridge.body(null, source);
    }

    /**
     * The new player made from {@code old} as the game respawns one ({@code PlayerList.respawn}), {@code alive} when
     * they came back from the End: Horizon keeps what it keeps; Fabric's {@code COPY_FROM} at its end.
     */
    public void restoreFrom(ServerPlayer old, boolean alive) {
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.COPY_FROM.invoker().copyFromPlayer(old, this, alive);
    }

    /** Kiln's own: a player whose changes go nowhere (the one who died, in a respawn). */
    public void kiln$quiet() {
        quiet = true;
    }

    /** Kiln's own: the menu the mod's code opened during this call, or null. */
    public AbstractContainerMenu kiln$openedMenu() {
        return opened;
    }

    @Override
    protected void kiln$opened(AbstractContainerMenu menu) {
        if (!quiet) {
            opened = menu;
        }
    }

    @Override
    protected void kiln$effect(String effect) {
        if (!quiet) {
            Server.effect(effect);
        }
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public int awardRecipes(java.util.Collection<net.minecraft.world.item.crafting.RecipeHolder<?>> p0) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.awardRecipes"); return 0; }
    public void onUpdateAbilities() { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.onUpdateAbilities"); }
    // End of what tools/kiln-stubs.py generated.
}
