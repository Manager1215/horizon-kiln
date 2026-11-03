package net.minecraft.world.level.block.state.properties;

import java.util.List;
import java.util.Optional;

/** Kiln's stand-in for {@code BooleanProperty}: {@code true} first, as vanilla's. */
public final class BooleanProperty extends Property<Boolean> {
    private static final List<Boolean> VALUES = List.of(true, false);

    private BooleanProperty(String name) {
        super(name, Boolean.class);
    }

    public static BooleanProperty create(String name) {
        return new BooleanProperty(name);
    }

    @Override
    public List<Boolean> getPossibleValues() {
        return VALUES;
    }

    @Override
    public String getName(Boolean value) {
        return value.toString();
    }

    @Override
    public Optional<Boolean> getValue(String name) {
        return switch (name) {
            case "true" -> Optional.of(true);
            case "false" -> Optional.of(false);
            default -> Optional.empty();
        };
    }
}
