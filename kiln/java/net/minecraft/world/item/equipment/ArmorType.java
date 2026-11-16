package net.minecraft.world.item.equipment;

import net.minecraft.world.entity.EquipmentSlot;

/** Kiln's stand-in for {@code ArmorType}: a kind of armour, its slot and durability, as the game's (D-171). */
public enum ArmorType implements net.minecraft.util.StringRepresentable {
    HELMET(EquipmentSlot.HEAD, 11, "helmet"),
    CHESTPLATE(EquipmentSlot.CHEST, 16, "chestplate"),
    LEGGINGS(EquipmentSlot.LEGS, 15, "leggings"),
    BOOTS(EquipmentSlot.FEET, 13, "boots"),
    BODY(EquipmentSlot.BODY, 16, "body");

    private final EquipmentSlot slot;
    private final int unitDurability;
    private final String name;

    ArmorType(EquipmentSlot slot, int unitDurability, String name) {
        this.slot = slot;
        this.unitDurability = unitDurability;
        this.name = name;
    }

    public EquipmentSlot getSlot() {
        return slot;
    }

    public int getDurability(int multiplier) {
        return unitDurability * multiplier;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
