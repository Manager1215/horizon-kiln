package kiln;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The mods' settings pages (D-094): a mod's {@code modmenu} entrypoint ({@code ModMenuApi}) gives its config screen, as
 * Mod Menu opens it from the mod's list; the game shows a button for it on the mod's row in Options > Mods, and its
 * click opens the screen over that page ({@link Screens}). The game hears which mods have one ({@code settings <mod
 * id>...}) when that changes.
 */
public final class Settings {
    /** The mods' Mod Menu entrypoints with a config screen of their own, by mod id. */
    private static final Map<String, ModMenuApi> PAGES = new TreeMap<>();

    private Settings() {}

    /** Mod {@code modId}'s {@code modmenu} entrypoint {@code className}: made now, its screen when asked. */
    static void register(String modId, String className) {
        try {
            Class<?> c = Class.forName(className, true, Loader.class.getClassLoader());
            if (!ModMenuApi.class.isAssignableFrom(c)) {
                Native.log(className + " is no ModMenuApi: " + modId + " gets no settings page");
                return;
            }
            ModMenuApi api = (ModMenuApi) c.getDeclaredConstructor().newInstance();
            if (c.getMethod("getModConfigScreenFactory").getDeclaringClass() == ModMenuApi.class) {
                return;
            }
            PAGES.put(modId, api);
            Host.send("settings", PAGES.keySet().toArray(String[]::new));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Native.log("the settings page of " + modId + " didn't load: " + e);
        }
    }

    /** The player pressed the button on mod {@code modId}'s row: its config screen opens, over the game's screen. */
    static void open(String modId) {
        ModMenuApi api = PAGES.get(modId);
        if (api == null) {
            Native.log(modId + " has no settings page");
            return;
        }
        ConfigScreenFactory<?> factory = api.getModConfigScreenFactory();
        Screen screen = factory == null ? null : factory.create(Screens.open());
        if (screen == null) {
            Native.log("the settings page of " + modId + " gave no screen");
            return;
        }
        Minecraft.getInstance().gui.setScreen(screen);
    }
}
