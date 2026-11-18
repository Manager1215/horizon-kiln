package com.mojang.authlib;

import com.mojang.authlib.properties.PropertyMap;
import java.util.UUID;

/**
 * Kiln's stand-in for authlib's {@code GameProfile}: a player's id and name, and the properties their profile carries
 * (a skin's {@code textures}), as authlib 10's record has them.
 */
public record GameProfile(UUID id, String name, PropertyMap properties) {
    public GameProfile(UUID id, String name) {
        this(id, name, PropertyMap.EMPTY);
    }
}
