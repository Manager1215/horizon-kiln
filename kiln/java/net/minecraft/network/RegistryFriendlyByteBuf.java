package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import net.minecraft.core.RegistryAccess;

/**
 * Kiln's stand-in for {@code RegistryFriendlyByteBuf}: a network buffer that knows the registries. A play payload's
 * codec reads and writes one; Kiln's registries are its own ({@code BuiltInRegistries}), so the access may be none.
 */
public class RegistryFriendlyByteBuf extends FriendlyByteBuf {
    private final RegistryAccess registryAccess;

    public RegistryFriendlyByteBuf(ByteBuf source, RegistryAccess registryAccess) {
        super(source);
        this.registryAccess = registryAccess;
    }

    public RegistryAccess registryAccess() {
        return registryAccess;
    }

    public static Function<ByteBuf, RegistryFriendlyByteBuf> decorator(RegistryAccess registryAccess) {
        return source -> new RegistryFriendlyByteBuf(source, registryAccess);
    }
}
