package kiln;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * One of the window's game's screens Kiln has no stand-in of its own for (the inventory, the options, a container): the
 * game draws it; {@link #kiln$name} is the game's name for it ({@code inventory}, {@code options}, ...).
 */
public final class GameScreen extends Screen {
    private final String name;

    GameScreen(String name) {
        super(Component.empty());
        this.name = name;
    }

    public String kiln$name() {
        return name;
    }

    @Override
    public String toString() {
        return "the game's " + name + " screen";
    }
}
