package net.minecraft.server.permissions;

/** Kiln's stand-in for {@code LevelBasedPermissionSet}: the permissions of an operator level. */
public interface LevelBasedPermissionSet extends PermissionSet {
    LevelBasedPermissionSet ALL = () -> PermissionLevel.ALL;
    LevelBasedPermissionSet MODERATOR = () -> PermissionLevel.MODERATORS;
    LevelBasedPermissionSet GAMEMASTER = () -> PermissionLevel.GAMEMASTERS;
    LevelBasedPermissionSet ADMIN = () -> PermissionLevel.ADMINS;
    LevelBasedPermissionSet OWNER = () -> PermissionLevel.OWNERS;

    PermissionLevel level();

    @Override
    default boolean hasPermission(Permission permission) {
        if (permission instanceof Permission.HasCommandLevel(PermissionLevel needed)) {
            return level().isEqualOrHigherThan(needed);
        }
        // Selectors from the game masters' level up, as vanilla's selectors in a command; chat for everyone.
        return permission.equals(Permissions.COMMANDS_ENTITY_SELECTORS)
                ? level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS)
                : Permissions.CHAT_PERMISSIONS.contains(permission);
    }

    @Override
    default PermissionSet union(PermissionSet other) {
        if (other instanceof LevelBasedPermissionSet o) {
            return o.level().isEqualOrHigherThan(level()) ? o : this;
        }
        return PermissionSet.super.union(other);
    }

    static LevelBasedPermissionSet forLevel(PermissionLevel level) {
        return switch (level) {
            case ALL -> ALL;
            case MODERATORS -> MODERATOR;
            case GAMEMASTERS -> GAMEMASTER;
            case ADMINS -> ADMIN;
            case OWNERS -> OWNER;
        };
    }
}
