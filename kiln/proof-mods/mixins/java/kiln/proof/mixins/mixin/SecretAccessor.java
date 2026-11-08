package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Secret;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Every kind of accessor and invoker on the mod's own class: instance and static, a final field, a constructor. */
@Mixin(Secret.class)
public interface SecretAccessor {
    @Accessor("word")
    String kiln$word();

    @Accessor
    static String getGREETING() {
        throw new AssertionError();
    }

    @Accessor
    @Mutable
    static void setGREETING(String greeting) {
        throw new AssertionError();
    }

    @Invoker("<init>")
    static Secret kiln$create(String word) {
        throw new AssertionError();
    }

    @Invoker
    static int callTwice(int n) {
        throw new AssertionError();
    }

    @Invoker("shout")
    String kiln$shout(String suffix);
}
