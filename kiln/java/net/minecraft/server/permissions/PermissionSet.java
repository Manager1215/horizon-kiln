package net.minecraft.server.permissions;

/** Kiln's stand-in for {@code PermissionSet}: what a command's source is allowed. */
public interface PermissionSet {
    PermissionSet NO_PERMISSIONS = permission -> false;
    PermissionSet ALL_PERMISSIONS = permission -> true;

    boolean hasPermission(Permission permission);

    default PermissionSet union(PermissionSet other) {
        return permission -> hasPermission(permission) || other.hasPermission(permission);
    }
}
