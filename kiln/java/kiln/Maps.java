package kiln;

import java.util.Map;

/**
 * The maps whose data the game's window has (D-177), as it told them ({@code maps <id>:<scale>:<locked>,...}, when
 * they change): a filled map's tooltip reads its id and whether it is locked from here, as the game's
 * {@code MapId.addToTooltip} reads the client's map data; a map the window hasn't is "Unknown Map", as in the game.
 */
public final class Maps {
    /** A map's data as a tooltip needs it: its scale (0 to 4) and whether a cartography table locked it. */
    public record Known(int scale, boolean locked) {}

    private static volatile Map<Integer, Known> known = Map.of();

    private Maps() {}

    /** What the window said: {@code id:scale:locked} entries, comma-separated; empty for none. */
    public static void update(String list) {
        Map<Integer, Known> next = new java.util.HashMap<>();
        for (String entry : list.split(",")) {
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                try {
                    next.put(Integer.parseInt(parts[0]),
                            new Known(Integer.parseInt(parts[1]), parts[2].equals("1")));
                } catch (NumberFormatException e) {
                    // Not one of the window's: left out.
                }
            }
        }
        known = Map.copyOf(next);
    }

    /** Map {@code id}'s data, or null when the window hasn't it. */
    public static Known known(int id) {
        return known.get(id);
    }
}
