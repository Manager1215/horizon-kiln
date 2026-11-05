package net.minecraft.client;

import java.util.Optional;
import java.util.UUID;

/** Kiln's stand-in for {@code User}: the player signed in to the window's game. Kiln holds no session or token. */
public class User {
    private final String name;
    private final UUID uuid;

    public User(String name, UUID uuid, String accessToken, Optional<String> xuid, Optional<String> clientId) {
        this.name = name;
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public UUID getProfileId() {
        return uuid;
    }

    /** Kiln has no session: empty. */
    public String getSessionId() {
        return "";
    }

    public String getAccessToken() {
        return "";
    }

    public Optional<String> getClientId() {
        return Optional.empty();
    }

    public Optional<String> getXuid() {
        return Optional.empty();
    }
}
