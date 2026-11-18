package net.minecraft.server.players;

import java.util.UUID;

/** Kiln's stand-in for {@code NameAndId}: a player as the server knows them, their UUID and name. */
public record NameAndId(UUID id, String name) {
    /** The player {@code profile} names. */
    public NameAndId(com.mojang.authlib.GameProfile profile) {
        this(profile.id(), profile.name());
    }
}
