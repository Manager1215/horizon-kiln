package net.fabricmc.loader.api;

/** Kiln's stand-in for Fabric Loader's {@code Version}: a mod's version, as its fabric.mod.json writes it. */
public interface Version extends Comparable<Version> {
    String getFriendlyString();

    @Override
    default int compareTo(Version other) {
        return getFriendlyString().compareTo(other.getFriendlyString());
    }
}
