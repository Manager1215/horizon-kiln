package net.fabricmc.api;

/** Kiln's stand-in for Fabric's {@code EnvType}: the client or a dedicated server. Kiln runs as the client. */
public enum EnvType {
    CLIENT,
    SERVER
}
