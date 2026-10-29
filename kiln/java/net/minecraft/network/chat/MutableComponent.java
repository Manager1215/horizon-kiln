package net.minecraft.network.chat;

/** Kiln's stand-in for {@code MutableComponent}: text that more text can be added to. */
public final class MutableComponent implements Component {
    private final StringBuilder text;

    MutableComponent(String text) {
        this.text = new StringBuilder(text);
    }

    public MutableComponent append(String more) {
        text.append(more);
        return this;
    }

    public MutableComponent append(Component more) {
        text.append(more.getString());
        return this;
    }

    @Override
    public String getString() {
        return text.toString();
    }

    @Override
    public String toString() {
        return getString();
    }
}
