package net.minecraft.world.level.block.state.properties;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;

/**
 * Kiln's stand-in for {@code BlockSetType}: what a family of doors, trapdoors, plates and buttons share (whether a hand
 * opens them, their sounds), as vanilla's record has it. The game's door blocks carry theirs from its prepared
 * resources.
 */
public record BlockSetType(String name, boolean canOpenByHand, boolean canOpenByWindCharge,
        boolean canButtonBeActivatedByArrows, BlockSetType.PressurePlateSensitivity pressurePlateSensitivity,
        SoundType soundType, SoundEvent doorClose, SoundEvent doorOpen, SoundEvent trapdoorClose,
        SoundEvent trapdoorOpen, SoundEvent pressurePlateClickOff, SoundEvent pressurePlateClickOn,
        SoundEvent buttonClickOff, SoundEvent buttonClickOn) {
    public static final BlockSetType IRON = new BlockSetType("iron", false, false, false,
            PressurePlateSensitivity.EVERYTHING, SoundType.METAL, sound("block.iron_door.close"),
            sound("block.iron_door.open"), sound("block.iron_trapdoor.close"), sound("block.iron_trapdoor.open"),
            sound("block.metal_pressure_plate.click_off"), sound("block.metal_pressure_plate.click_on"),
            sound("block.stone_button.click_off"), sound("block.stone_button.click_on"));
    public static final BlockSetType OAK = new BlockSetType("oak");
    public static final BlockSetType SPRUCE = new BlockSetType("spruce");
    public static final BlockSetType BIRCH = new BlockSetType("birch");
    public static final BlockSetType ACACIA = new BlockSetType("acacia");
    public static final BlockSetType JUNGLE = new BlockSetType("jungle");
    public static final BlockSetType DARK_OAK = new BlockSetType("dark_oak");
    public static final BlockSetType PALE_OAK = new BlockSetType("pale_oak");
    public static final BlockSetType MANGROVE = new BlockSetType("mangrove");

    /** A wooden set: what a hand opens, with the wooden door's sounds. */
    public BlockSetType(String name) {
        this(name, true, true, true, PressurePlateSensitivity.EVERYTHING, SoundType.WOOD,
                sound("block.wooden_door.close"), sound("block.wooden_door.open"), sound("block.wooden_trapdoor.close"),
                sound("block.wooden_trapdoor.open"), sound("block.wooden_pressure_plate.click_off"),
                sound("block.wooden_pressure_plate.click_on"), sound("block.wooden_button.click_off"),
                sound("block.wooden_button.click_on"));
    }

    private static SoundEvent sound(String id) {
        return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(id));
    }

    /** Kiln's own: a sound the game's data names ({@code minecraft:block.wooden_door.open}); none for none. */
    public static SoundEvent kiln$sound(String id) {
        return id == null || id.isEmpty() ? null : SoundEvent.createVariableRangeEvent(Identifier.parse(id));
    }

    public static enum PressurePlateSensitivity {
        EVERYTHING,
        MOBS;
    }
}
