package net.minecraft.world.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Kiln's stand-in for {@code EquipmentSlotGroup}: a named set of equipment slots, as the game's (Reborn Core walks
 * the armour group's slots every tick, D-171).
 */
public enum EquipmentSlotGroup implements net.minecraft.util.StringRepresentable, Iterable<EquipmentSlot> {
    ANY(0, "any", slot -> true),
    MAINHAND(1, "mainhand", EquipmentSlot.MAINHAND),
    OFFHAND(2, "offhand", EquipmentSlot.OFFHAND),
    HAND(3, "hand", slot -> slot.getType() == EquipmentSlot.Type.HAND),
    FEET(4, "feet", EquipmentSlot.FEET),
    LEGS(5, "legs", EquipmentSlot.LEGS),
    CHEST(6, "chest", EquipmentSlot.CHEST),
    HEAD(7, "head", EquipmentSlot.HEAD),
    ARMOR(8, "armor", EquipmentSlot::isArmor),
    BODY(9, "body", EquipmentSlot.BODY),
    SADDLE(10, "saddle", EquipmentSlot.SADDLE);

    /** By its name as the game saves it ({@code "mainhand"}), an item's attribute modifiers' {@code slot}. */
    public static final com.mojang.serialization.Codec<EquipmentSlotGroup> CODEC =
            net.minecraft.util.StringRepresentable.fromEnum(EquipmentSlotGroup::values);

    private final int id;
    private final String key;
    private final Predicate<EquipmentSlot> predicate;
    private final List<EquipmentSlot> slots;

    EquipmentSlotGroup(int id, String key, Predicate<EquipmentSlot> predicate) {
        this.id = id;
        this.key = key;
        this.predicate = predicate;
        List<EquipmentSlot> in = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (predicate.test(slot)) {
                in.add(slot);
            }
        }
        this.slots = List.copyOf(in);
    }

    EquipmentSlotGroup(int id, String key, EquipmentSlot slot) {
        this(id, key, s -> s == slot);
    }

    public static EquipmentSlotGroup bySlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> MAINHAND;
            case OFFHAND -> OFFHAND;
            case FEET -> FEET;
            case LEGS -> LEGS;
            case CHEST -> CHEST;
            case HEAD -> HEAD;
            case BODY -> BODY;
            case SADDLE -> SADDLE;
        };
    }

    public boolean test(EquipmentSlot slot) {
        return predicate.test(slot);
    }

    public List<EquipmentSlot> slots() {
        return slots;
    }

    @Override
    public String getSerializedName() {
        return key;
    }

    @Override
    public Iterator<EquipmentSlot> iterator() {
        return slots.iterator();
    }
}
