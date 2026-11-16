package net.minecraft.world.level.block;

import java.util.function.Function;
import net.minecraft.world.item.DyeColor;

/** Kiln's stand-in for {@code ColorCollection}: one thing for each of the sixteen dye colours, in their order. */
public record ColorCollection<T>(T white, T orange, T magenta, T lightBlue, T yellow, T lime, T pink, T gray,
        T lightGray, T cyan, T purple, T blue, T brown, T green, T red, T black) {
    public T pick(DyeColor color) {
        return switch (color) {
            case WHITE -> white;
            case ORANGE -> orange;
            case MAGENTA -> magenta;
            case LIGHT_BLUE -> lightBlue;
            case YELLOW -> yellow;
            case LIME -> lime;
            case PINK -> pink;
            case GRAY -> gray;
            case LIGHT_GRAY -> lightGray;
            case CYAN -> cyan;
            case PURPLE -> purple;
            case BLUE -> blue;
            case BROWN -> brown;
            case GREEN -> green;
            case RED -> red;
            case BLACK -> black;
        };
    }

    /** Kiln's own: the collection made from each colour's name ({@code light_blue}). */
    public static <T> ColorCollection<T> kiln$of(Function<String, T> make) {
        return new ColorCollection<>(make.apply("white"), make.apply("orange"), make.apply("magenta"),
                make.apply("light_blue"), make.apply("yellow"), make.apply("lime"), make.apply("pink"),
                make.apply("gray"), make.apply("light_gray"), make.apply("cyan"), make.apply("purple"),
                make.apply("blue"), make.apply("brown"), make.apply("green"), make.apply("red"), make.apply("black"));
    }
}
