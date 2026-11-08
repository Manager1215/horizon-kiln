package kiln.proof.mixins;

/** What KeyMappingMixin adds to every key mapping: an interface it implements, so the mod's code can ask. */
public interface KeyMappingStats {
    /** The clicks taken from this mapping since it was made. */
    int kiln$consumed();

    /** The clicks waiting (the mapping's own field, through @Shadow). */
    int kiln$waiting();

    /** The mappings made with the game's four-argument constructor (a static field the mixin adds). */
    int kiln$made();
}
