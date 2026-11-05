package net.minecraft.nbt;

/** Kiln's stand-in for {@code EndTag}: nothing, as an empty value is written. */
public final class EndTag implements Tag {
    public static final EndTag INSTANCE = new EndTag();

    private EndTag() {}

    @Override
    public byte getId() {
        return TAG_END;
    }

    @Override
    public EndTag copy() {
        return this;
    }

    @Override
    public Object kiln$value() {
        return null;
    }

    @Override
    public String toString() {
        return "END";
    }
}
