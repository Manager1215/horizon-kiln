package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client's methods mods inject into most, as they do: the main loop's start, the tick and the keys handled in it,
 * the level set, the world left (both of the game's {@code disconnect}s, by their descriptors) and the level cleared;
 * and a handler that throws.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMomentsMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void kiln$tickStarts(CallbackInfo ci) {
        Moments.saw("tick HEAD");
    }

    /** A handler that throws, each tick: Kiln says so once, and the tick goes on. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void kiln$throws(CallbackInfo ci) {
        Moments.thrown++;
        throw new IllegalStateException("the proof mod's handler throws, each tick");
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void kiln$tickEnds(CallbackInfo ci) {
        Moments.saw("tick TAIL");
        Moments.tickEnded();
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void kiln$levelComes(ClientLevel level, CallbackInfo ci) {
        Moments.saw("setLevel HEAD " + Moments.has() + (level == null ? " to none" : " to a level"));
    }

    @Inject(method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V", at = @At("RETURN"))
    private void kiln$levelCame(CallbackInfo ci) {
        Moments.saw("setLevel RETURN " + Moments.has());
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void kiln$leaves(Screen screen, boolean keepResourcePacks, CallbackInfo ci) {
        Moments.saw("disconnect HEAD " + Moments.name(screen) + " " + Moments.has());
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void kiln$leavesAll(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        Moments.saw("disconnect(ZZ) HEAD " + Moments.has());
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("RETURN"))
    private void kiln$left(CallbackInfo ci) {
        Moments.saw("disconnect(ZZ) RETURN " + Moments.has());
        Moments.left();
    }

    @Inject(method = "run", at = @At("HEAD"))
    private void kiln$runs(CallbackInfo ci) {
        Moments.saw("run HEAD");
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void kiln$keys(CallbackInfo ci) {
        Moments.saw("handleKeybinds HEAD");
    }

    @Inject(method = "clearClientLevel", at = @At("HEAD"))
    private void kiln$clears(Screen screen, CallbackInfo ci) {
        Moments.saw("clearClientLevel HEAD " + Moments.has());
    }
}
