package kiln.proof.mixins;

import java.util.List;

/** What SlotWholeMixin adds to every slot. */
public interface SlotStats {
    /** The times mayPlace was asked (counted by its @WrapMethod). */
    int kiln$asked();

    /** A list its field initializer made, one for each slot. */
    List<String> kiln$notes();
}
