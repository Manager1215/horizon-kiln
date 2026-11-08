package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Secret;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Listed for the dedicated server only: a client (Kiln) never applies it. */
@Mixin(Secret.class)
public interface NeverAccessor {
    @Accessor("word")
    String kiln$never();
}
