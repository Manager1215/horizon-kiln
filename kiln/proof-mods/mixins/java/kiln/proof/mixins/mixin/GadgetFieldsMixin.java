package kiln.proof.mixins.mixin;

import java.util.ArrayList;
import java.util.List;
import kiln.proof.mixins.Gadget;
import kiln.proof.mixins.GadgetFields;
import kiln.proof.mixins.ProofMixins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Field initializers on the mod's own class: a number, a list, a branch, a counter, a lambda. */
@Mixin(Gadget.class)
public abstract class GadgetFieldsMixin implements GadgetFields {
    @Unique
    private int kiln$count = 3;

    @Unique
    private final List<String> kiln$seen = new ArrayList<>(List.of("made"));

    @Unique
    private String kiln$kind = Gadget.class.getSimpleName().length() > 3 ? "long" : "short";

    @Unique
    private final int kiln$serial = ++ProofMixins.serials;

    @Unique
    private final Runnable kiln$bumper = () -> kiln$count++;

    @Override
    public int kiln$count() {
        return kiln$count;
    }

    @Override
    public List<String> kiln$seen() {
        return kiln$seen;
    }

    @Override
    public String kiln$kind() {
        return kiln$kind;
    }

    @Override
    public int kiln$serial() {
        return kiln$serial;
    }

    @Override
    public void kiln$bump() {
        kiln$bumper.run();
    }
}
