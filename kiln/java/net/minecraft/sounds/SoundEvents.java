package net.minecraft.sounds;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Kiln's stand-in for {@code SoundEvents}: a few of the game's sounds, by their ids. Horizon has them all; Kiln
 * names the ones mods it runs play. Some are entries ({@code Holder.Reference}), as in vanilla.
 */
public final class SoundEvents {
    public static final SoundEvent AMETHYST_BLOCK_CHIME = sound("block.amethyst_block.chime");
    public static final SoundEvent ANVIL_USE = sound("block.anvil.use");
    public static final SoundEvent BEACON_ACTIVATE = sound("block.beacon.activate");
    public static final SoundEvent BOTTLE_FILL = sound("item.bottle.fill");
    public static final SoundEvent CHEST_OPEN = sound("block.chest.open");
    public static final SoundEvent ENCHANTMENT_TABLE_USE = sound("block.enchantment_table.use");
    public static final SoundEvent ENDER_CHEST_OPEN = sound("block.ender_chest.open");
    public static final SoundEvent ENDERMAN_TELEPORT = sound("entity.enderman.teleport");
    public static final SoundEvent EXPERIENCE_ORB_PICKUP = sound("entity.experience_orb.pickup");
    public static final SoundEvent FIRECHARGE_USE = sound("item.firecharge.use");
    public static final Holder.Reference<SoundEvent> GENERIC_EAT = entry("entity.generic.eat");
    public static final Holder.Reference<SoundEvent> ITEM_BREAK = entry("entity.item.break");
    public static final SoundEvent ITEM_PICKUP = sound("entity.item.pickup");
    public static final Holder.Reference<SoundEvent> NOTE_BLOCK_PLING = entry("block.note_block.pling");
    public static final SoundEvent PLAYER_BURP = sound("entity.player.burp");
    public static final SoundEvent PLAYER_LEVELUP = sound("entity.player.levelup");
    public static final SoundEvent TOTEM_USE = sound("item.totem.use");
    public static final Holder.Reference<SoundEvent> UI_BUTTON_CLICK = entry("ui.button.click");
    public static final SoundEvent ZOMBIE_VILLAGER_CURE = sound("entity.zombie_villager.cure");

    private SoundEvents() {}

    private static SoundEvent sound(String name) {
        return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(name));
    }

    private static Holder.Reference<SoundEvent> entry(String name) {
        Identifier id = Identifier.withDefaultNamespace(name);
        return Holder.Reference.kiln$of(ResourceKey.create(Registries.SOUND_EVENT, id), SoundEvent.createVariableRangeEvent(id));
    }
}
