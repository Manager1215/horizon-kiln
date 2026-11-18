package net.minecraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Kiln's stand-in for {@code CommandSourceStack}: who runs a server command, where, and with what permissions. Kiln
 * makes one for the player whose command Horizon's server hands it ({@link kiln.ServerCommands}); what the command
 * says back ({@link #sendSuccess}, {@link #sendFailure}) is the command's feedback, in that player's chat after it.
 */
public class CommandSourceStack implements SharedSuggestionProvider {
    public static final SimpleCommandExceptionType ERROR_NOT_PLAYER =
            new SimpleCommandExceptionType(Component.translatable("permissions.requires.player"));
    public static final SimpleCommandExceptionType ERROR_NOT_ENTITY =
            new SimpleCommandExceptionType(Component.translatable("permissions.requires.entity"));

    private final CommandSource source;
    private final Vec3 worldPosition;
    private final Vec2 rotation;
    private final ServerLevel level;
    private final PermissionSet permissions;
    private final String textName;
    private final Component displayName;
    private final MinecraftServer server;
    private final Entity entity;
    private final boolean silent;

    /** A source that is an entity: its names are the entity's. */
    public CommandSourceStack(CommandSource source, Vec3 position, Vec2 rotation, ServerLevel level,
            PermissionSet permissions, MinecraftServer server, Entity entity) {
        this(source, position, rotation, level, permissions, entity == null ? "" : entity.getScoreboardName(),
                entity == null ? Component.empty() : entity.getName(), server, entity, false);
    }

    /** A source that is no entity (the console, a command block), named {@code name}. */
    public CommandSourceStack(CommandSource source, Vec3 position, Vec2 rotation, ServerLevel level,
            PermissionSet permissions, Component name, MinecraftServer server) {
        this(source, position, rotation, level, permissions, name.getString(), name, server, null, false);
    }

    private CommandSourceStack(CommandSource source, Vec3 position, Vec2 rotation, ServerLevel level,
            PermissionSet permissions, String textName, Component displayName, MinecraftServer server, Entity entity,
            boolean silent) {
        this.source = source;
        this.worldPosition = position;
        this.rotation = rotation;
        this.level = level;
        this.permissions = permissions;
        this.textName = textName;
        this.displayName = displayName;
        this.server = server;
        this.entity = entity;
        this.silent = silent;
    }

    /** Kiln's own: a player's command, their messages the command's feedback ({@code feedback} effects). */
    public static CommandSourceStack kiln$player(ServerPlayer player, PermissionSet permissions) {
        return new CommandSourceStack(FEEDBACK, player.position(), new Vec2(player.getXRot(), player.getYRot()),
                player.level(), permissions, MinecraftServer.kiln$server(), player);
    }

    /** Kiln's own: what a command says goes to its player's chat after it, as Horizon sends a command's feedback. */
    private static final CommandSource FEEDBACK = new CommandSource() {
        @Override
        public void sendSystemMessage(Component message) {
            kiln.Server.effect("feedback " + message.kiln$json());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    };

    private CommandSourceStack with(CommandSource source, Vec3 position, Vec2 rotation, ServerLevel level,
            PermissionSet permissions, Entity entity, boolean silent) {
        return new CommandSourceStack(source, position, rotation, level, permissions, textName, displayName, server,
                entity, silent);
    }

    public CommandSourceStack withSource(CommandSource source) {
        return with(source, worldPosition, rotation, level, permissions, entity, silent);
    }

    public CommandSourceStack withEntity(Entity entity) {
        return entity == this.entity ? this : new CommandSourceStack(source, worldPosition, rotation, level,
                permissions, entity.getScoreboardName(), entity.getName(), server, entity, silent);
    }

    public CommandSourceStack withPosition(Vec3 position) {
        return with(source, position, rotation, level, permissions, entity, silent);
    }

    public CommandSourceStack withRotation(Vec2 rotation) {
        return with(source, worldPosition, rotation, level, permissions, entity, silent);
    }

    public CommandSourceStack withSuppressedOutput() {
        return with(source, worldPosition, rotation, level, permissions, entity, true);
    }

    public CommandSourceStack withPermission(PermissionSet permissions) {
        return with(source, worldPosition, rotation, level, permissions, entity, silent);
    }

    public CommandSourceStack withMaximumPermission(PermissionSet permissions) {
        return withPermission(this.permissions.union(permissions));
    }

    public CommandSourceStack withLevel(ServerLevel level) {
        return with(source, worldPosition, rotation, level, permissions, entity, silent);
    }

    public Component getDisplayName() {
        return displayName;
    }

    public String getTextName() {
        return textName;
    }

    /** The server's registries. */
    public net.minecraft.core.RegistryAccess registryAccess() {
        return server.registryAccess();
    }

    @Override
    public PermissionSet permissions() {
        return permissions;
    }

    public Vec3 getPosition() {
        return worldPosition;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public Entity getEntity() {
        return entity;
    }

    public Entity getEntityOrException() throws CommandSyntaxException {
        if (entity == null) {
            throw ERROR_NOT_ENTITY.create();
        }
        return entity;
    }

    public ServerPlayer getPlayerOrException() throws CommandSyntaxException {
        if (entity instanceof ServerPlayer player) {
            return player;
        }
        throw ERROR_NOT_PLAYER.create();
    }

    public ServerPlayer getPlayer() {
        return entity instanceof ServerPlayer player ? player : null;
    }

    public boolean isPlayer() {
        return entity instanceof ServerPlayer;
    }

    public Vec2 getRotation() {
        return rotation;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public boolean isSilent() {
        return silent;
    }

    public void sendSystemMessage(Component message) {
        if (!silent) {
            source.sendSystemMessage(message);
        }
    }

    /** The command's answer; {@code broadcast} would tell the operators too: Kiln tells only the source. */
    public void sendSuccess(Supplier<Component> message, boolean broadcast) {
        if (!silent && source.acceptsSuccess()) {
            source.sendSystemMessage(message.get());
        }
    }

    /** What went wrong, in red. */
    public void sendFailure(Component message) {
        if (!silent && source.acceptsFailure()) {
            source.sendSystemMessage(Component.empty().append(message).withStyle(ChatFormatting.RED));
        }
    }

    /** The names of the players on the server ({@link kiln.Players}). */
    @Override
    public Collection<String> getOnlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (kiln.Players.Online online : kiln.Players.online()) {
            names.add(online.player().getScoreboardName());
        }
        return names;
    }

    /** The server's commands ({@link Commands}), those of the Java mods. */
    public CommandDispatcher<CommandSourceStack> dispatcher() {
        return server.getCommands().getDispatcher();
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.commands.CommandSourceStack.NamesProvider namesProvider;
    public net.minecraft.commands.CommandSourceStack withCallback(net.minecraft.commands.CommandResultCallback p0) { kiln.Missing.called("net.minecraft.commands.CommandSourceStack.withCallback"); return this; }
    public net.minecraft.commands.arguments.EntityAnchorArgument.Anchor getAnchor() { kiln.Missing.called("net.minecraft.commands.CommandSourceStack.getAnchor"); return null; }
    public net.minecraft.commands.CommandSigningContext getSigningContext() { kiln.Missing.called("net.minecraft.commands.CommandSourceStack.getSigningContext"); return null; }
    public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { kiln.Missing.called("net.minecraft.commands.CommandSourceStack.enabledFeatures"); return null; }
    public static interface NamesProvider {
        public static final net.minecraft.commands.CommandSourceStack.NamesProvider FOR_ENTITY = null;
        net.minecraft.network.chat.Component displayName(net.minecraft.world.entity.Entity p0);
        java.lang.String textName(net.minecraft.world.entity.Entity p0);
    }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
}
