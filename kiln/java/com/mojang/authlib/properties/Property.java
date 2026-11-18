package com.mojang.authlib.properties;

/**
 * Kiln's stand-in for authlib's {@code Property}: one of a profile's properties (a skin's {@code textures}: its value
 * base64 JSON), with the signature the session server gave it, if any.
 */
public record Property(String name, String value, String signature) {
    public Property(String name, String value) {
        this(name, value, null);
    }

    public boolean hasSignature() {
        return signature != null;
    }
}
