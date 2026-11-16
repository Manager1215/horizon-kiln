package net.minecraft.server.permissions;

import java.util.function.Predicate;

/** Kiln's stand-in for {@code PermissionProviderCheck}: a command node's requirement made of a permission check. */
public record PermissionProviderCheck<T extends PermissionSetSupplier>(PermissionCheck test) implements Predicate<T> {
    @Override
    public boolean test(T source) {
        return test.check(source.permissions());
    }
}
