package kiln.hello;

import kiln.Events;
import kiln.Hud;
import kiln.Native;

/** The spike's Java mod: started by Kiln from its fabric.mod.json, it writes a line on Horizon's HUD. */
public final class HelloMod {
    public void onInitializeClient() {
        Native.log("Hello mod: started on Java " + System.getProperty("java.version"));
        Events.onTick(() -> Hud.line("Hello from Java! tick " + Events.ticks()));
    }
}
