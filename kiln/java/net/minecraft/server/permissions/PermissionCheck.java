package net.minecraft.server.permissions;

/** Kiln's stand-in for {@code PermissionCheck}: a test of a permission set. */
public interface PermissionCheck {
    boolean check(PermissionSet permissions);

    /** Passes a set that has {@code permission}. */
    record Require(Permission permission) implements PermissionCheck {
        @Override
        public boolean check(PermissionSet permissions) {
            return permissions.hasPermission(permission);
        }
    }

    /** Passes every set. */
    class AlwaysPass implements PermissionCheck {
        public static final AlwaysPass INSTANCE = new AlwaysPass();

        @Override
        public boolean check(PermissionSet permissions) {
            return true;
        }
    }
}
