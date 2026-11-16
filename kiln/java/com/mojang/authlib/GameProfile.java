package com.mojang.authlib;

import java.util.UUID;

/** Kiln's stand-in for authlib's {@code GameProfile}: a player's id and name. */
public record GameProfile(UUID id, String name) {
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
