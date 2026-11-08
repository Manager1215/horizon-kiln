package kiln.test.mixinonly;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The mod's one mixin: it says it ran, where the loader test mod reads it (D-099). */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Inject(method = "load", at = @At("TAIL"))
    private void kiln$loaded(CallbackInfo ci) {
        System.setProperty("kiln.test.mixinOnly", "applied");
    }
}
