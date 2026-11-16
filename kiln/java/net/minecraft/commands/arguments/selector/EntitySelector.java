package net.minecraft.commands.arguments.selector;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Kiln's stand-in for {@code EntitySelector}: who an entity argument names, a player's name or UUID, or a selector
 * ({@code @s}, {@code @p}, {@code @a}, {@code @r}, {@code @e}, {@code @n}). Kiln's server knows its players only
 * ({@link kiln.Players}): a selector finds players, and its filters in brackets are read past and not applied.
 */
public class EntitySelector {
    public static final int INFINITE = Integer.MAX_VALUE;

    private final char kind;
    private final String name;
    private final int maxResults;
    private final boolean includesEntities;

    /** Kiln's own: selector {@code @kind} (0 for a name or UUID). */
    public EntitySelector(char kind, String name, int maxResults, boolean includesEntities) {
        this.kind = kind;
        this.name = name;
        this.maxResults = maxResults;
        this.includesEntities = includesEntities;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public boolean includesEntities() {
        return includesEntities;
    }

    public boolean isSelfSelector() {
        return kind == 's';
    }

    public boolean isWorldLimited() {
        return false;
    }

    public boolean usesSelector() {
        return kind != 0;
    }

    private void checkPermissions(CommandSourceStack source) throws CommandSyntaxException {
        if (usesSelector() && !source.permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS)) {
            throw EntityArgument.ERROR_SELECTORS_NOT_ALLOWED.create();
        }
    }

    public Entity findSingleEntity(CommandSourceStack source) throws CommandSyntaxException {
        List<? extends Entity> found = findEntities(source);
        if (found.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        }
        if (found.size() > 1) {
            throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.create();
        }
        return found.get(0);
    }

    public List<? extends Entity> findEntities(CommandSourceStack source) throws CommandSyntaxException {
        return find(source);
    }

    public ServerPlayer findSinglePlayer(CommandSourceStack source) throws CommandSyntaxException {
        List<ServerPlayer> found = findPlayers(source);
        if (found.size() != 1) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }
        return found.get(0);
    }

    public List<ServerPlayer> findPlayers(CommandSourceStack source) throws CommandSyntaxException {
        return find(source);
    }

    private List<ServerPlayer> find(CommandSourceStack source) throws CommandSyntaxException {
        checkPermissions(source);
        List<ServerPlayer> players = new ArrayList<>();
        if (kind == 's') {
            if (source.getEntity() instanceof ServerPlayer self) {
                players.add(self);
            }
            return players;
        }
        for (kiln.Players.Online online : kiln.Players.online()) {
            ServerPlayer p = online.player();
            if (kind != 0 || p.getScoreboardName().equalsIgnoreCase(name) || p.getStringUUID().equalsIgnoreCase(name)) {
                players.add(p);
            }
        }
        // The call's own player is the one the source has, so a mod that changes "them" changes the source's player.
        if (source.getEntity() instanceof ServerPlayer self) {
            players.replaceAll(p -> p.getUUID().equals(self.getUUID()) ? self : p);
        }
        Vec3 from = source.getPosition();
        switch (kind) {
            case 'p', 'n' -> players.sort(Comparator.comparingDouble(p -> p.position().distanceToSqr(from)));
            case 'r' -> java.util.Collections.shuffle(players);
            default -> {}
        }
        return players.size() > maxResults ? new ArrayList<>(players.subList(0, maxResults)) : players;
    }

    /** The names of {@code entities}, joined as the game joins them in a message. */
    public static Component joinNames(List<? extends Entity> entities) {
        StringBuilder names = new StringBuilder();
        for (Entity e : entities) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(e.getName().getString());
        }
        return Component.literal(names.toString());
    }

    @Override
    public String toString() {
        return kind == 0 ? name : "@" + kind;
    }
}
