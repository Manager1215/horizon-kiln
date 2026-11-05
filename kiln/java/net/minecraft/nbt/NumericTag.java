package net.minecraft.nbt;

/** Kiln's stand-in for {@code NumericTag}: a number. */
public interface NumericTag extends Tag {
    Number box();

    default int intValue() {
        return box().intValue();
    }

    default long longValue() {
        return box().longValue();
    }

    default double doubleValue() {
        return box().doubleValue();
    }

    default float floatValue() {
        return box().floatValue();
    }

    default byte byteValue() {
        return box().byteValue();
    }

    default short shortValue() {
        return box().shortValue();
    }
}
