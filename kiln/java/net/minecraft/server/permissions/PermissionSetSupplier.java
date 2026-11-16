package net.minecraft.server.permissions;

/** Kiln's stand-in for {@code PermissionSetSupplier}: something with permissions (a command's source). */
public interface PermissionSetSupplier {
    PermissionSet permissions();
}
