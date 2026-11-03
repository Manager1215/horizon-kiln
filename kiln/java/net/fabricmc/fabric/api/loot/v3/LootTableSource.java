package net.fabricmc.fabric.api.loot.v3;

/** Kiln's stand-in for Fabric's {@code LootTableSource}: where a loot table came from. */
public enum LootTableSource {
    VANILLA(true),
    MOD(true),
    DATA_PACK(false),
    REPLACED(false);

    private final boolean builtin;

    LootTableSource(boolean builtin) {
        this.builtin = builtin;
    }

    public boolean isBuiltin() {
        return builtin;
    }
}
