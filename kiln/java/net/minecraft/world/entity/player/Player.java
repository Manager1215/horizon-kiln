package net.minecraft.world.entity.player;

import java.util.OptionalInt;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;

/** Kiln's stand-in for {@code Player}. */
public abstract class Player extends Avatar implements net.minecraft.world.entity.ContainerUser {
    /** How far it reaches a container it uses, as the game's player: as far as it reaches blocks. */
    public double getContainerInteractionRange() {
        return blockInteractionRange();
    }

    /** Whether it has the container {@code counter} counts at {@code pos} open: Horizon's server keeps who has. */
    public boolean hasContainerOpen(net.minecraft.world.level.block.entity.ContainerOpenersCounter counter,
            net.minecraft.core.BlockPos pos) {
        kiln.Missing.called("net.minecraft.world.entity.player.Player.hasContainerOpen");
        return false;
    }

    /** Survival 0, creative 1, adventure 2, spectator 3. */
    protected int gameMode;

    /** Kiln's own: a player the game's side fills in (the window's, a server call's). */
    public Player() {}

    /**
     * The game's constructor, as the mods' own players call it (a fake player, a body standing in for one): in the
     * level, with the profile's id.
     */
    public Player(net.minecraft.world.level.Level level, com.mojang.authlib.GameProfile profile) {
        this.level = level;
        if (profile != null && profile.id() != null) {
            this.uuid = profile.id();
        }
    }

    public void sendSystemMessage(Component message) {
        kiln$effect("tell " + message.kiln$json());
    }

    /** A line over the hotbar (the action bar). */
    public void sendOverlayMessage(Component message) {
        kiln$effect("overlay " + message.kiln$json());
    }

    public net.minecraft.world.entity.EntityType<?> getType() {
        return net.minecraft.world.entity.EntityType.PLAYER;
    }

    public boolean isCreative() {
        return gameMode == 1;
    }

    /** Whether it may use the game master's blocks (command blocks, structure blocks): a creative player here. */
    public boolean canUseGameMasterBlocks() {
        return isCreative();
    }

    public boolean isSpectator() {
        return gameMode == 3;
    }

    /** The menu the player has open: the one {@code openMenu} last built. */
    public AbstractContainerMenu containerMenu;
    private Inventory inventory;
    private int lastContainerId;

    public Inventory getInventory() {
        if (inventory == null) {
            inventory = new Inventory(this);
        }
        return inventory;
    }

    /** The menu of the player's own inventory, which every player has: active on the server's side, as the game's. */
    public final InventoryMenu inventoryMenu = new InventoryMenu(getInventory(), !kiln$client(), this);

    {
        // The window's player has their own inventory's menu open until another opens, as the game's.
        if (kiln$client()) {
            containerMenu = inventoryMenu;
        }
    }

    /** Kiln's own: a client's player (the window's), whose inventory menu isn't active. */
    protected boolean kiln$client() {
        return false;
    }

    /**
     * The player's tick. The game moves and updates the player itself: Kiln's tick of them runs what the mods'
     * mixins put in it, for the window's player with the client's level tick ({@code Minecraft.tick}), after the
     * classes above it, as the game's {@code Player.tick} calls theirs first.
     */
    @Override
    public void tick() {
        super.tick();
    }

    /**
     * Opens {@code provider}'s menu: the mod's code builds it ({@code createMenu}), and the game opens the window and
     * moves the items. Kiln's server side tells the game where its slots are ({@code kiln.Menus}).
     */
    /** Kiln's own: the name of the menu {@code openMenu} last opened (its provider's), the title the game shows. */
    protected net.minecraft.network.chat.Component kiln$openedTitle;

    public OptionalInt openMenu(MenuProvider provider) {
        if (provider == null) {
            return OptionalInt.empty();
        }
        lastContainerId = lastContainerId % 100 + 1;
        AbstractContainerMenu menu = provider.createMenu(lastContainerId, getInventory(), this);
        if (menu == null) {
            return OptionalInt.empty();
        }
        containerMenu = menu;
        kiln$openedTitle = provider.getDisplayName();
        kiln$opened(menu);
        return OptionalInt.of(lastContainerId);
    }

    /** Drops {@code stack} at the player's feet, as the game does (Kiln's server side: a drop effect, D-166). */
    public net.minecraft.world.entity.item.ItemEntity drop(net.minecraft.world.item.ItemStack stack, boolean thrownFromHand,
            net.minecraft.util.Prediction prediction) {
        return drop(stack, thrownFromHand);
    }

    public net.minecraft.world.entity.item.ItemEntity drop(net.minecraft.world.item.ItemStack stack, boolean thrownFromHand) {
        if (!stack.isEmpty() && !kiln.PlayerMenus.droppedFromStation(stack)) {
            kiln.Server.effect("drop " + (int) Math.floor(getX()) + " " + (int) Math.floor(getY()) + " "
                    + (int) Math.floor(getZ()) + " " + stack.kiln$snbt());
        }
        return null;
    }

    /** Kiln's own: the mod's code opened {@code menu}; a client's view does nothing with it. */
    protected void kiln$opened(AbstractContainerMenu menu) {}

    private net.minecraft.world.inventory.PlayerEnderChestContainer enderChest;

    /**
     * The player's ender chest. A server call's player's is the game's, asked for the first time a mod reaches it
     * (D-209); a client's is empty, as the game's client knows nobody's ender chest until it is open.
     */
    public net.minecraft.world.inventory.PlayerEnderChestContainer getEnderChestInventory() {
        if (enderChest == null) {
            enderChest = new net.minecraft.world.inventory.PlayerEnderChestContainer();
            kiln$fillEnderChest(enderChest);
        }
        return enderChest;
    }

    /** Kiln's own: fills a new ender chest from the game, where there is one to ask. */
    protected void kiln$fillEnderChest(net.minecraft.world.inventory.PlayerEnderChestContainer chest) {}

    /** Who the player is: their UUID and name (Kiln knows no skin or other properties). */
    public com.mojang.authlib.GameProfile getGameProfile() {
        return new com.mojang.authlib.GameProfile(getUUID(), getScoreboardName());
    }

    /** Experience: the level, the progress towards the next (0 to 1) and the total points, as the game said. */
    public int experienceLevel;
    public float experienceProgress;
    public int totalExperience;

    private final Abilities abilities = new Abilities();
    private final net.minecraft.world.food.FoodData foodData = new net.minecraft.world.food.FoodData();

    /**
     * The speed the player moves through the air with: their abilities' flying speed while flying (twice it
     * sprinting), else the game's air speed, as the game's. Mods' mixins in this run once a client tick for the window's
     * flying player, and what they make it give goes to Horizon's flying speed (Kiln's Mixin bridge, D-105).
     */
    protected float getFlyingSpeed() {
        float speed = abilities.flying ? (isSprinting() ? abilities.getFlyingSpeed() * 2.0F
                : abilities.getFlyingSpeed()) : (isSprinting() ? 0.025999999F : 0.02F);
        return kiln.Bridge.body(speed);
    }

    /**
     * The player attacks {@code target}: Horizon hits it; the mods' mixins in this run at Horizon's {@code
     * attack_entity} hook, where one that cancels it at its head cancels the blow (Kiln's Mixin bridge, D-106).
     */
    public void attack(net.minecraft.world.entity.Entity target) {
        kiln.Bridge.body(null, target);
    }

    /** Whether the player's main arm is the left one (the game's Main Hand option, the window's player's). */
    protected boolean kiln$leftHanded;

    /** The player's main arm: the right, unless the Main Hand option says left. */
    public net.minecraft.world.entity.HumanoidArm getMainArm() {
        return kiln$leftHanded ? net.minecraft.world.entity.HumanoidArm.LEFT : net.minecraft.world.entity.HumanoidArm.RIGHT;
    }

    public Abilities getAbilities() {
        return abilities;
    }

    /** Whether the player builds without using things up: their abilities say (creative mode). */
    public boolean hasInfiniteMaterials() {
        return abilities.instabuild;
    }

    /** Whether the player may change blocks: their abilities say (not in adventure or spectator mode). */
    public boolean mayBuild() {
        return abilities.mayBuild;
    }

    public net.minecraft.world.food.FoodData getFoodData() {
        return foodData;
    }

    @Override
    public float getEyeHeight() {
        return isShiftKeyDown() ? 1.27F : 1.62F;
    }

    /** The points from this level to the next, as vanilla counts them. */
    public int getXpNeededForNextLevel() {
        if (experienceLevel >= 30) {
            return 112 + (experienceLevel - 30) * 9;
        }
        return experienceLevel >= 15 ? 37 + (experienceLevel - 15) * 5 : 7 + experienceLevel * 2;
    }

    /**
     * Points of experience given (taken, when negative), as vanilla counts them: the progress moves, levels go up or
     * down as it passes a level's end, and the total stays from 0 to {@code Integer.MAX_VALUE}. What a server call
     * changes goes to the game after it ({@code xp}).
     */
    public void giveExperiencePoints(int points) {
        experienceProgress += (float) points / getXpNeededForNextLevel();
        totalExperience = (int) Math.max(0, Math.min((long) totalExperience + points, Integer.MAX_VALUE));
        while (experienceProgress < 0.0F) {
            float left = experienceProgress * getXpNeededForNextLevel();
            if (experienceLevel > 0) {
                giveExperienceLevels(-1);
                experienceProgress = 1.0F + left / getXpNeededForNextLevel();
            } else {
                giveExperienceLevels(-1);
                experienceProgress = 0.0F;
            }
        }
        while (experienceProgress >= 1.0F) {
            experienceProgress = (experienceProgress - 1.0F) * getXpNeededForNextLevel();
            giveExperienceLevels(1);
            experienceProgress /= getXpNeededForNextLevel();
        }
    }

    /** Levels given (taken, when negative), as vanilla: below 0 the player has none, and no points either. */
    public void giveExperienceLevels(int levels) {
        experienceLevel = (int) Math.max(Integer.MIN_VALUE, Math.min((long) experienceLevel + levels, Integer.MAX_VALUE));
        if (experienceLevel < 0) {
            experienceLevel = 0;
            experienceProgress = 0.0F;
            totalExperience = 0;
        }
    }

    /** Kiln's own: the experience the game said. */
    public void kiln$experience(int level, float progress, int total) {
        experienceLevel = level;
        experienceProgress = progress;
        totalExperience = total;
    }
    /**
     * A sound the player makes, heard by the players near: in the window, the window's player hears their own (the
     * client's level plays it where they stand).
     */
    public void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        net.minecraft.world.level.Level level = level();
        if (level != null && sound != null) {
            level.playSound(this, getX(), getY(), getZ(), sound, net.minecraft.sounds.SoundSource.PLAYERS, volume, pitch);
        }
    }

    /**
     * A statistic counted up: the statistics are Horizon's, so nothing here. The pickup stand-ins call it where vanilla's
     * {@code ItemEntity.playerTouch} does, so a mixin at it finds its place (D-213).
     */
    public void awardStat(net.minecraft.stats.Stat<?> stat, int amount) {
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public void awardStat(net.minecraft.resources.Identifier p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.awardStat"); }
    public void awardStat(net.minecraft.stats.Stat<?> p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.awardStat"); }
    public boolean mayUseItemAt(net.minecraft.core.BlockPos p0, net.minecraft.core.Direction p1, net.minecraft.world.item.ItemStack p2) { kiln.Missing.called("net.minecraft.world.entity.player.Player.mayUseItemAt"); return false; }
    public void onUpdateAbilities() { kiln.Missing.called("net.minecraft.world.entity.player.Player.onUpdateAbilities"); }
    public boolean isSwimming() { kiln.Missing.called("net.minecraft.world.entity.player.Player.isSwimming"); return false; }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    protected net.minecraft.world.inventory.PlayerEnderChestContainer enderChestInventory;
    public int takeXpDelay;
    public int enchantmentSeed;
    public net.minecraft.world.entity.projectile.FishingHook fishing;
    public boolean blockActionRestricted(net.minecraft.world.level.Level p0, net.minecraft.core.BlockPos p1, net.minecraft.world.level.GameType p2) { kiln.Missing.called("net.minecraft.world.entity.player.Player.blockActionRestricted"); return false; }
    public boolean isSecondaryUseActive() { kiln.Missing.called("net.minecraft.world.entity.player.Player.isSecondaryUseActive"); return false; }
    protected void updatePlayerPose() { kiln.Missing.called("net.minecraft.world.entity.player.Player.updatePlayerPose"); }
    public net.minecraft.sounds.SoundSource getSoundSource() { kiln.Missing.called("net.minecraft.world.entity.player.Player.getSoundSource"); return null; }
    public void closeContainer() { kiln.Missing.called("net.minecraft.world.entity.player.Player.closeContainer"); }
    public int getScore() { kiln.Missing.called("net.minecraft.world.entity.player.Player.getScore"); return 0; }
    public void setScore(int p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.setScore"); }
    public void startAutoSpinAttack(int p0, float p1, net.minecraft.world.item.ItemStack p2) { kiln.Missing.called("net.minecraft.world.entity.player.Player.startAutoSpinAttack"); }
    public float getDestroySpeed(net.minecraft.world.level.block.state.BlockState p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.getDestroySpeed"); return 0.0F; }
    public boolean hasCorrectToolForDrops(net.minecraft.world.level.block.state.BlockState p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.hasCorrectToolForDrops"); return false; }
    public boolean canHarmPlayer(net.minecraft.world.entity.player.Player p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.canHarmPlayer"); return false; }
    public net.minecraft.world.InteractionResult interactOn(net.minecraft.world.entity.Entity p0, net.minecraft.world.InteractionHand p1, net.minecraft.world.phys.Vec3 p2) { kiln.Missing.called("net.minecraft.world.entity.player.Player.interactOn"); return null; }
    public float getVoicePitch() { kiln.Missing.called("net.minecraft.world.entity.player.Player.getVoicePitch"); return 0.0F; }
    public void remove(net.minecraft.world.entity.Entity.RemovalReason p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.remove"); }
    public boolean isLocalPlayer() { kiln.Missing.called("net.minecraft.world.entity.player.Player.isLocalPlayer"); return false; }
    public net.minecraft.server.players.NameAndId nameAndId() { kiln.Missing.called("net.minecraft.world.entity.player.Player.nameAndId"); return new net.minecraft.server.players.NameAndId((java.util.UUID) null, (java.lang.String) null); }
    public boolean preventsBlockDrops() { kiln.Missing.called("net.minecraft.world.entity.player.Player.preventsBlockDrops"); return false; }
    public com.mojang.datafixers.util.Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, net.minecraft.util.Unit> startSleepInBed(net.minecraft.world.level.block.AbstractBedBlock p0, net.minecraft.world.level.block.state.BlockState p1, net.minecraft.world.attribute.BedRule p2, net.minecraft.core.BlockPos p3) { kiln.Missing.called("net.minecraft.world.entity.player.Player.startSleepInBed"); return null; }
    public int awardRecipes(java.util.Collection<net.minecraft.world.item.crafting.RecipeHolder<?>> p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.awardRecipes"); return 0; }
    public void triggerRecipeCrafted(net.minecraft.world.item.crafting.RecipeHolder<?> p0, java.util.List<net.minecraft.world.item.ItemStack> p1) { kiln.Missing.called("net.minecraft.world.entity.player.Player.triggerRecipeCrafted"); }
    public void travel(net.minecraft.world.phys.Vec3 p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.travel"); }
    protected boolean canGlide() { kiln.Missing.called("net.minecraft.world.entity.player.Player.canGlide"); return false; }
    public float getSpeed() { kiln.Missing.called("net.minecraft.world.entity.player.Player.getSpeed"); return 0.0F; }
    public boolean tryToStartFallFlying() { kiln.Missing.called("net.minecraft.world.entity.player.Player.tryToStartFallFlying"); return false; }
    public void startFallFlying() { kiln.Missing.called("net.minecraft.world.entity.player.Player.startFallFlying"); }
    public void causeFoodExhaustion(float p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.causeFoodExhaustion"); }
    public boolean canEat(boolean p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.canEat"); return false; }
    public boolean isHurt() { kiln.Missing.called("net.minecraft.world.entity.player.Player.isHurt"); return false; }
    public boolean addItem(net.minecraft.world.item.ItemStack p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.addItem"); return false; }
    public net.minecraft.world.level.GameType gameMode() { kiln.Missing.called("net.minecraft.world.entity.player.Player.gameMode"); return null; }
    public float getAbsorptionAmount() { kiln.Missing.called("net.minecraft.world.entity.player.Player.getAbsorptionAmount"); return 0.0F; }
    public boolean isReducedDebugInfo() { kiln.Missing.called("net.minecraft.world.entity.player.Player.isReducedDebugInfo"); return false; }
    public void setRemainingFireTicks(int p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.setRemainingFireTicks"); }
    public java.util.Optional<net.minecraft.world.entity.animal.parrot.Parrot.Variant> getShoulderParrotLeft() { kiln.Missing.called("net.minecraft.world.entity.player.Player.getShoulderParrotLeft"); return java.util.Optional.empty(); }
    public java.util.Optional<net.minecraft.world.entity.animal.parrot.Parrot.Variant> getShoulderParrotRight() { kiln.Missing.called("net.minecraft.world.entity.player.Player.getShoulderParrotRight"); return java.util.Optional.empty(); }
    public float getCurrentItemAttackStrengthDelay() { kiln.Missing.called("net.minecraft.world.entity.player.Player.getCurrentItemAttackStrengthDelay"); return 0.0F; }
    public float getAttackStrengthScale(float p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.getAttackStrengthScale"); return 0.0F; }
    public void resetAttackStrengthTicker() { kiln.Missing.called("net.minecraft.world.entity.player.Player.resetAttackStrengthTicker"); }
    public void onAttack() { kiln.Missing.called("net.minecraft.world.entity.player.Player.onAttack"); }
    public void resetOnlyAttackStrengthTicker() { kiln.Missing.called("net.minecraft.world.entity.player.Player.resetOnlyAttackStrengthTicker"); }
    public net.minecraft.world.item.ItemCooldowns getCooldowns() { kiln.Missing.called("net.minecraft.world.entity.player.Player.getCooldowns"); return null; }
    public float getLuck() { kiln.Missing.called("net.minecraft.world.entity.player.Player.getLuck"); return 0.0F; }
    public net.minecraft.server.permissions.PermissionSet permissions() { kiln.Missing.called("net.minecraft.world.entity.player.Player.permissions"); return null; }
    public net.minecraft.world.item.ItemStack getProjectile(net.minecraft.world.item.ItemStack p0) { kiln.Missing.called("net.minecraft.world.entity.player.Player.getProjectile"); return null; }
    public boolean isScoping() { kiln.Missing.called("net.minecraft.world.entity.player.Player.isScoping"); return false; }
    public double blockInteractionRange() { kiln.Missing.called("net.minecraft.world.entity.player.Player.blockInteractionRange"); return 0.0; }
    public double entityInteractionRange() { kiln.Missing.called("net.minecraft.world.entity.player.Player.entityInteractionRange"); return 0.0; }
    public boolean isWithinBlockInteractionRange(net.minecraft.core.BlockPos p0, double p1) { kiln.Missing.called("net.minecraft.world.entity.player.Player.isWithinBlockInteractionRange"); return false; }
    public boolean shouldRotateWithMinecart() { kiln.Missing.called("net.minecraft.world.entity.player.Player.shouldRotateWithMinecart"); return false; }
    public boolean onClimbable() { kiln.Missing.called("net.minecraft.world.entity.player.Player.onClimbable"); return false; }
    public static record BedSleepingProblem(net.minecraft.network.chat.Component message) {
        public static final net.minecraft.world.entity.player.Player.BedSleepingProblem OTHER_PROBLEM = null;
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
