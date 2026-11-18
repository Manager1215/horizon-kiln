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
        described = true;
    }

    /** Whether the constructor is done: who the player is is known. */
    private boolean described;

    /** Kiln's own: whether the player is made, and who they are known. */
    public boolean kiln$described() {
        return described;
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

    /** Their profile: id and name (REI's server side reads the name each tick, D-215). */
    @Override
    public com.mojang.authlib.GameProfile getGameProfile() {
        return new com.mojang.authlib.GameProfile(getUUID(), getScoreboardName());
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

    /** Kiln's own: whether this is the server call's own player, whose state the game can tell and change. */
    public boolean kiln$acting() {
        return described && !quiet && Server.inCall();
    }

    /** What the player wears is the game's, as their inventory's slots are (D-209). */
    @Override
    public ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
        if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) {
            getInventory().kiln$read();
        }
        return super.getItemBySlot(slot);
    }

    /** The game's ender chest, for the call's own player (D-209); another's stays empty. */
    @Override
    protected void kiln$fillEnderChest(net.minecraft.world.inventory.PlayerEnderChestContainer chest) {
        if (!quiet) {
            kiln.EnderChests.fill(this, chest);
        }
    }

    /**
     * Closes the menu the player has open, back to their inventory's, as the game does: the menu's {@code removed}
     * runs. What Horizon shows stays: Kiln shows no menu a command opens yet (D-209).
     */
    public void closeContainer() {
        if (containerMenu != null && containerMenu != inventoryMenu) {
            containerMenu.removed(this);
        }
        containerMenu = inventoryMenu;
        opened = null;
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

    /** Kiln's own: this player's motion sent to their game (a mod's motion packet, #220, D-187). */
    public void kiln$motion(net.minecraft.world.phys.Vec3 movement) {
        kiln$effect("motion " + movement.x + " " + movement.y + " " + movement.z);
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
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.server.MinecraftServer server;
    public boolean wonGame;
    public net.minecraft.world.level.portal.TeleportTransition findRespawnPositionAndUseSpawnBlock(boolean p0, net.minecraft.world.level.portal.TeleportTransition.PostTeleportTransition p1) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.findRespawnPositionAndUseSpawnBlock"); return new net.minecraft.world.level.portal.TeleportTransition((net.minecraft.server.level.ServerLevel) null, (net.minecraft.world.phys.Vec3) null, (net.minecraft.world.phys.Vec3) null, (float) 0.0F, (float) 0.0F, (boolean) false, (boolean) false, (java.util.Set) null, (net.minecraft.world.level.portal.TeleportTransition.PostTeleportTransition) null); }
    public net.minecraft.server.level.ServerPlayer teleport(net.minecraft.world.level.portal.TeleportTransition p0) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.teleport"); return this; }
    public boolean bedInRange(net.minecraft.core.BlockPos p0, net.minecraft.core.Direction p1) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.bedInRange"); return false; }
    public void triggerRecipeCrafted(net.minecraft.world.item.crafting.RecipeHolder<?> p0, java.util.List<net.minecraft.world.item.ItemStack> p1) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.triggerRecipeCrafted"); }
    public void awardRecipesByKey(java.util.List<net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>> p0) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.awardRecipesByKey"); }
    public boolean hasDisconnected() { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.hasDisconnected"); return false; }
    public void lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor p0, net.minecraft.world.phys.Vec3 p1) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.lookAt"); }
    public void teleportTo(double p0, double p1, double p2) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.teleportTo"); }
    public boolean teleportTo(net.minecraft.server.level.ServerLevel p0, double p1, double p2, double p3, java.util.Set<net.minecraft.world.entity.Relative> p4, float p5, float p6, boolean p7) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.teleportTo"); return false; }
    public net.minecraft.commands.CommandSource commandSource() { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.commandSource"); return null; }
    public void sendChatMessage(net.minecraft.network.chat.OutgoingChatMessage p0, boolean p1, net.minecraft.network.chat.ChatType.Bound p2) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.sendChatMessage"); }
    public void updateOptions(net.minecraft.server.level.ClientInformation p0) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.updateOptions"); }
    public net.minecraft.server.level.ClientInformation clientInformation() { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.clientInformation"); return new net.minecraft.server.level.ClientInformation((java.lang.String) null, (int) 0, (net.minecraft.world.entity.player.ChatVisiblity) null, (boolean) false, (int) 0, (net.minecraft.world.entity.HumanoidArm) null, (boolean) false, (boolean) false, (net.minecraft.server.level.ParticleStatus) null); }
    public void resetLastActionTime() { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.resetLastActionTime"); }
    public net.minecraft.stats.ServerRecipeBook getRecipeBook() { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.getRecipeBook"); return null; }
    public net.minecraft.world.entity.Entity getCamera() { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.getCamera"); return null; }
    public void setCamera(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.setCamera"); }
    public boolean isChangingDimension() { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.isChangingDimension"); return false; }
    public void hasChangedDimension() { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.hasChangedDimension"); }
    public net.minecraft.server.PlayerAdvancements getAdvancements() { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.getAdvancements"); return null; }
    public net.minecraft.server.level.ServerPlayer.RespawnConfig getRespawnConfig() { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.getRespawnConfig"); return null; }
    public void setRespawnPosition(net.minecraft.server.level.ServerPlayer.RespawnConfig p0, boolean p1) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.setRespawnPosition"); }
    public net.minecraft.core.SectionPos getLastSectionPos() { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.getLastSectionPos"); return null; }
    public net.minecraft.server.level.ChunkTrackingView getChunkTrackingView() { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.getChunkTrackingView"); return null; }
    public void setChunkTrackingView(net.minecraft.server.level.ChunkTrackingView p0) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.setChunkTrackingView"); }
    public void drop(boolean p0) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.drop"); }
    public net.minecraft.network.chat.RemoteChatSession getChatSession() { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.getChatSession"); return new net.minecraft.network.chat.RemoteChatSession((java.util.UUID) null, (net.minecraft.world.entity.player.ProfilePublicKey) null); }
    public boolean startRiding(net.minecraft.world.entity.Entity p0, boolean p1, boolean p2) { kiln.Missing.called("net.minecraft.server.level.ServerPlayer.startRiding"); return false; }
    public static record RespawnConfig(net.minecraft.world.level.storage.LevelData.RespawnData respawnData, boolean forced) {
    }
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
