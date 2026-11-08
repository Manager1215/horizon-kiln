package kiln;

import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * What Kiln does when Mixin finds a mod's mixin wrong (D-089): never stops the game, which Mixin would for a config
 * marked {@code required}. A mixin Mixin can't prepare is left out by Mixin itself; one it can't apply to a class is
 * noted here, and {@link Mixins} patches that class again without it, so the class's other mixins stay (where Mixin
 * would load the class with none). Either is said once in Kiln's log, with Mixin's reason.
 */
public final class MixinErrors implements IMixinErrorHandler {
    /** A mixin that failed on a class: the class is patched again without it. */
    record Failure(IMixinInfo mixin, String target, Throwable why) {}

    private static final ThreadLocal<List<Failure>> FAILED = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public ErrorAction onPrepareError(IMixinConfig config, Throwable th, IMixinInfo mixin, ErrorAction action) {
        Mixins.sayOnce("Kiln's Mixin leaves out " + who(mixin) + ": " + reason(th));
        return ErrorAction.NONE;
    }

    @Override
    public ErrorAction onApplyError(String targetClassName, Throwable th, IMixinInfo mixin, ErrorAction action) {
        FAILED.get().add(new Failure(mixin, targetClassName, th));
        return ErrorAction.NONE;
    }

    /** The failures on this thread since the last call, which they leave. */
    static List<Failure> take() {
        List<Failure> failed = FAILED.get();
        if (failed.isEmpty()) {
            return List.of();
        }
        List<Failure> taken = new ArrayList<>(failed);
        failed.clear();
        return taken;
    }

    /** A mixin as Kiln's log names it: its mod's id and its class. */
    static String who(IMixinInfo mixin) {
        String mod = mixin.getConfig() == null ? "?" : FabricUtil.getModId(mixin.getConfig(), "?");
        return mod + "'s " + mixin.getClassName();
    }

    /** Mixin's message, without its exception's class name. */
    static String reason(Throwable th) {
        String message = th.getMessage();
        if (message == null || message.isEmpty()) {
            return th.toString();
        }
        return message;
    }
}
