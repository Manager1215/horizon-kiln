package net.fabricmc.fabric.api.transfer.v1.storage;

/** Kiln's stand-in for Fabric's {@code StoragePreconditions}: the checks storage operations start with. */
public final class StoragePreconditions {
    private StoragePreconditions() {}

    public static void notBlank(TransferVariant<?> variant) {
        if (variant.isBlank()) {
            throw new IllegalArgumentException("a blank variant");
        }
    }

    public static void notNegative(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("a negative amount: " + amount);
        }
    }

    public static void notBlankNotNegative(TransferVariant<?> variant, long amount) {
        notBlank(variant);
        notNegative(amount);
    }
}
