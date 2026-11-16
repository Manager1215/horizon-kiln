package net.minecraft.world.entity;

import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code EquipmentSlot}: where an entity holds or wears an item, by vanilla's names. */
public enum EquipmentSlot implements StringRepresentable {
    MAINHAND(Type.HAND, 0, "mainhand"),
    OFFHAND(Type.HAND, 1, "offhand"),
    FEET(Type.HUMANOID_ARMOR, 0, "feet"),
    LEGS(Type.HUMANOID_ARMOR, 1, "legs"),
    CHEST(Type.HUMANOID_ARMOR, 2, "chest"),
    HEAD(Type.HUMANOID_ARMOR, 3, "head"),
    BODY(Type.ANIMAL_ARMOR, 0, "body"),
    SADDLE(Type.SADDLE, 0, "saddle");

    private final Type type;
    private final int index;
    private final String name;

    EquipmentSlot(Type type, int index, String name) {
        this.type = type;
        this.index = index;
        this.name = name;
    }

    /** What kind of slot: a hand, worn armour, an animal's armour, a saddle. */
    public Type getType() {
        return type;
    }

    /** Its place among its kind's slots (the hands 0 and 1, the armour from the feet up). */
    public int getIndex() {
        return index;
    }

    public int getIndex(int base) {
        return base + index;
    }

    public boolean isArmor() {
        return type == Type.HUMANOID_ARMOR || type == Type.ANIMAL_ARMOR;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Kiln's own: the slot vanilla names {@code name}, or null. */
    public static EquipmentSlot kiln$byName(String name) {
        for (EquipmentSlot slot : values()) {
            if (slot.name.equals(name)) {
                return slot;
            }
        }
        return null;
    }

    /** The kinds of slot, as vanilla's. */
    public enum Type {
        HAND,
        HUMANOID_ARMOR,
        ANIMAL_ARMOR,
        SADDLE;
    }
}
