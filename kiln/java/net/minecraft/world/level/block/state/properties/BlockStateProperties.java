package net.minecraft.world.level.block.state.properties;

import net.minecraft.core.Direction;

/** Kiln's stand-in for {@code BlockStateProperties}: the game's shared block state properties. */
public class BlockStateProperties {
    public static final int MAX_AGE_1 = 1;
    public static final int MAX_AGE_2 = 2;
    public static final int MAX_AGE_3 = 3;
    public static final int MAX_AGE_5 = 5;
    public static final int MAX_AGE_7 = 7;
    public static final int MAX_AGE_15 = 15;
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final BooleanProperty WATERLOGGED = BooleanProperty.create("waterlogged");
    public static final EnumProperty<Direction> FACING = EnumProperty.create("facing", Direction.class);
    public static final EnumProperty<Direction> HORIZONTAL_FACING = EnumProperty.create("facing", Direction.class,
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    public static final IntegerProperty AGE_1 = IntegerProperty.create("age", 0, 1);
    public static final IntegerProperty AGE_2 = IntegerProperty.create("age", 0, 2);
    public static final IntegerProperty AGE_3 = IntegerProperty.create("age", 0, 3);
    public static final IntegerProperty AGE_5 = IntegerProperty.create("age", 0, 5);
    public static final IntegerProperty AGE_7 = IntegerProperty.create("age", 0, 7);
    public static final IntegerProperty AGE_15 = IntegerProperty.create("age", 0, 15);
}
