package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client's constructor, as mods hook its start (D-200): its HEAD (before super(), so static, as Mixin wants)
 * before the entrypoints run, its RETURN after, and the object it made, which must be the one the game's code uses.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftInitMixin {
    @Inject(method = "<init>", at = @At("HEAD"))
    private static void kiln$making(CallbackInfo ci) {
        Moments.initHeads++;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void kiln$made(CallbackInfo ci) {
        Moments.initReturns++;
        Moments.made = this;
    }
}
