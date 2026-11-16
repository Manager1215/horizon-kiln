package net.minecraft.server.players;

import java.util.UUID;

/** Kiln's stand-in for {@code NameAndId}: a player as the server knows them, their UUID and name. */
public record NameAndId(UUID id, String name) {}
