package kiln.test.widener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.world.level.storage.LevelResource;

/** Fails to start unless Kiln loaded the classes its class tweaker names with the access it asks for. */
public final class Checks implements ClientModInitializer {
    private static final class Hidden {
        private final int count = 1;

        private int secret() {
            return 41;
        }

        int callsSecret() {
            return secret() + count;
        }
    }

    @Override
    public void onInitializeClient() {
        try {
            check(Modifier.isPublic(Hidden.class.getModifiers()), "class Checks$Hidden isn't public");
            Method secret = Hidden.class.getDeclaredMethod("secret");
            check(Modifier.isPublic(secret.getModifiers()), "Hidden.secret isn't public");
            check(Modifier.isFinal(secret.getModifiers()), "Hidden.secret isn't final, as Fabric makes it");
            check(!Modifier.isFinal(Hidden.class.getDeclaredField("count").getModifiers()), "Hidden.count is final");
            // The call to the method made public still reaches it.
            check(new Hidden().callsSecret() == 42, "Hidden.callsSecret() isn't 42");
            Field id = LevelResource.class.getDeclaredField("id");
            check(Modifier.isPublic(id.getModifiers()), "LevelResource.id isn't public");
            check(!Modifier.isFinal(id.getModifiers()), "LevelResource.id is final");
            // Its constructor is public in Kiln's stand-in, as Fabric API's own widener makes it.
            check(new LevelResource("kiln").toString() != null, "no LevelResource");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("not widened: " + e, e);
        }
        System.err.println("Kiln Test Widener: every class tweaker entry loaded widened");
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException("not widened: " + what);
        }
    }
}
