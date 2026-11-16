package net.minecraft.client.main;

/**
 * Kiln's stand-in for {@code GameConfig}: what the game was started with. Horizon starts the game, so Kiln has none of
 * it; the one config is what {@code Minecraft}'s constructor takes for the initial resource reload (D-152).
 */
public class GameConfig {
    /** Kiln's own: the one config. */
    public static final GameConfig KILN = new GameConfig();

    private GameConfig() {}
}
