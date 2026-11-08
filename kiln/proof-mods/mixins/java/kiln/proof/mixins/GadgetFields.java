package kiln.proof.mixins;

import java.util.List;

/** What GadgetFieldsMixin adds to every gadget: its fields, as their initializers made them. */
public interface GadgetFields {
    int kiln$count();

    List<String> kiln$seen();

    String kiln$kind();

    int kiln$serial();

    /** Runs the lambda an initializer made: {@link #kiln$count()} once more. */
    void kiln$bump();
}
