package io.github.manager1215.kilntimer.core;

/** The Any% splits, in route order. Each happens at most once a run, in any order. */
public enum Split {
    /** First entry into the Nether, by dimension or by "We Need to Go Deeper". */
    NETHER("Nether"),
    /** "Those Were the Days": a bastion. */
    BASTION("Bastion"),
    /** "A Terrible Fortress": a fortress. */
    FORTRESS("Fortress"),
    /** First trip from the Nether back to the Overworld. */
    BLIND("Blind"),
    /** "Eye Spy": inside the stronghold. */
    STRONGHOLD("Stronghold"),
    /** First entry into the End, by dimension or by "The End?". */
    END("End"),
    /** The run's end, as {@link FinishMode} says. */
    FINISH("Finish");

    private final String label;

    Split(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
