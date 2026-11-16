package net.minecraft.server.permissions;

import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code PermissionLevel}: an operator level, 0 (everyone) to 4 (the server's owner). */
public enum PermissionLevel implements StringRepresentable {
    ALL("all"), MODERATORS("moderators"), GAMEMASTERS("gamemasters"), ADMINS("admins"), OWNERS("owners");

    private final String name;

    PermissionLevel(String name) {
        this.name = name;
    }

    public int id() {
        return ordinal();
    }

    public boolean isEqualOrHigherThan(PermissionLevel other) {
        return ordinal() >= other.ordinal();
    }

    /** The level of that number, clamped to 0..4. */
    public static PermissionLevel byId(int id) {
        PermissionLevel[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, id))];
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
