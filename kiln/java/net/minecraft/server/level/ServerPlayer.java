package net.minecraft.server.level;

import java.util.UUID;
import kiln.Server;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Kiln's stand-in for {@code ServerPlayer}: a player as the server saw them when it called the mod. */
public class ServerPlayer extends Player {
    private boolean quiet;

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
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel) level;
    }

    /** Kiln's own: the game's values of an attribute, before the mod's code runs. */
    public void kiln$setAttribute(Holder<Attribute> attribute, double base, double value) {
        attributes.put(attribute, kiln$newAttribute(attribute, base, value));
    }

    /** Kiln's own: what the player holds, before the mod's code runs. */
    public void kiln$setHands(ItemStack main, ItemStack off) {
        mainHand = main;
        offHand = off;
    }

    /** Kiln's own: a player whose changes go nowhere (the one who died, in a respawn). */
    public void kiln$quiet() {
        quiet = true;
    }

    @Override
    protected void kiln$effect(String effect) {
        if (!quiet) {
            Server.effect(effect);
        }
    }
}
