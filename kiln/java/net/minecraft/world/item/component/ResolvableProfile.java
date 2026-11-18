package net.minecraft.world.item.component;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Kiln's stand-in for {@code ResolvableProfile}: whose head a player head is ({@code minecraft:profile}), as the game
 * saves it. A whole profile (an id, a name, its properties) or a part of one (a skin's {@code textures} with a made-up
 * id, as head mods give) is static; a name or an id alone is dynamic, which the game resolves to its player's profile
 * (Horizon's: Kiln looks no one up). Either way the skin patch's textures and arms may go over the profile's.
 */
public abstract class ResolvableProfile implements TooltipProvider {
    private static final UUID NIL = new UUID(0L, 0L);
    private static final Codec<ResolvableProfile> FULL_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.mapEither(ExtraCodecs.STORED_GAME_PROFILE, Partial.MAP_CODEC).forGetter(ResolvableProfile::unpack),
            PlayerSkin.Patch.MAP_CODEC.forGetter(ResolvableProfile::skinPatch))
            .apply(i, ResolvableProfile::create));
    /** The profile's compound, or a bare player name (a dynamic profile of that name). */
    public static final Codec<ResolvableProfile> CODEC =
            Codec.withAlternative(FULL_CODEC, ExtraCodecs.PLAYER_NAME, ResolvableProfile::createUnresolved);

    protected final GameProfile partialProfile;
    protected final PlayerSkin.Patch skinPatch;

    protected ResolvableProfile(GameProfile partialProfile, PlayerSkin.Patch skinPatch) {
        this.partialProfile = partialProfile;
        this.skinPatch = skinPatch;
    }

    /** As the game reads one: a name or an id alone, with no properties, is dynamic; anything else static. */
    private static ResolvableProfile create(Either<GameProfile, Partial> contents, PlayerSkin.Patch patch) {
        return contents.map(profile -> new Static(Either.left(profile), patch),
                partial -> partial.properties().isEmpty() && partial.id().isPresent() != partial.name().isPresent()
                        ? partial.name().<ResolvableProfile>map(name -> new Dynamic(Either.left(name), patch))
                                .orElseGet(() -> new Dynamic(Either.right(partial.id().get()), patch))
                        : new Static(Either.right(partial), patch));
    }

    /** A whole profile, as it is. */
    public static ResolvableProfile createResolved(GameProfile profile) {
        return new Static(Either.left(profile), PlayerSkin.Patch.EMPTY);
    }

    /** The profile of the player called {@code name}, to be looked up. */
    public static ResolvableProfile createUnresolved(String name) {
        return new Dynamic(Either.left(name), PlayerSkin.Patch.EMPTY);
    }

    /** The profile of the player {@code id}, to be looked up. */
    public static ResolvableProfile createUnresolved(UUID id) {
        return new Dynamic(Either.right(id), PlayerSkin.Patch.EMPTY);
    }

    protected abstract Either<GameProfile, Partial> unpack();

    /** What it knows of the profile: a missing id is the nil UUID, a missing name empty. */
    public GameProfile partialProfile() {
        return partialProfile;
    }

    public PlayerSkin.Patch skinPatch() {
        return skinPatch;
    }

    public abstract Optional<String> name();

    /** The whole profile: a static one's own; a dynamic one's from {@code resolver}, or what it knows when none is found. */
    public abstract CompletableFuture<GameProfile> resolveProfile(ProfileResolver resolver);

    @Override
    public void addToTooltip(net.minecraft.world.item.Item.TooltipContext context,
            java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag,
            net.minecraft.core.component.DataComponentGetter components) {
        kiln.Missing.called("net.minecraft.world.item.component.ResolvableProfile.addToTooltip");
    }

    private static GameProfile createPartialProfile(Optional<String> name, Optional<UUID> id, PropertyMap properties) {
        return new GameProfile(id.orElse(NIL), name.orElse(""), properties);
    }

    /** A whole profile, or a part of one; never looked up. */
    public static final class Static extends ResolvableProfile {
        public static final Static EMPTY = new Static(Either.right(Partial.EMPTY), PlayerSkin.Patch.EMPTY);
        private final Either<GameProfile, Partial> contents;

        private Static(Either<GameProfile, Partial> contents, PlayerSkin.Patch skinPatch) {
            super(contents.map(profile -> profile, Partial::createProfile), skinPatch);
            this.contents = contents;
        }

        @Override
        protected Either<GameProfile, Partial> unpack() {
            return contents;
        }

        @Override
        public Optional<String> name() {
            return contents.map(profile -> Optional.of(profile.name()), Partial::name);
        }

        @Override
        public CompletableFuture<GameProfile> resolveProfile(ProfileResolver resolver) {
            return CompletableFuture.completedFuture(partialProfile);
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Static s && contents.equals(s.contents) && skinPatch.equals(s.skinPatch);
        }

        @Override
        public int hashCode() {
            return Objects.hash(contents, skinPatch);
        }
    }

    /** A player's name or id, whose profile the game looks up. */
    public static final class Dynamic extends ResolvableProfile {
        private final Either<String, UUID> nameOrId;

        private Dynamic(Either<String, UUID> nameOrId, PlayerSkin.Patch skinPatch) {
            super(createPartialProfile(nameOrId.left(), nameOrId.right(), PropertyMap.EMPTY), skinPatch);
            this.nameOrId = nameOrId;
        }

        @Override
        public Optional<String> name() {
            return nameOrId.left();
        }

        @Override
        public CompletableFuture<GameProfile> resolveProfile(ProfileResolver resolver) {
            return CompletableFuture.completedFuture(
                    nameOrId.map(resolver::fetchByName, resolver::fetchById).orElse(partialProfile));
        }

        @Override
        protected Either<GameProfile, Partial> unpack() {
            return Either.right(new Partial(nameOrId.left(), nameOrId.right(), PropertyMap.EMPTY));
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Dynamic d && nameOrId.equals(d.nameOrId) && skinPatch.equals(d.skinPatch);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nameOrId, skinPatch);
        }
    }

    /** What a saved profile has when it isn't whole: a name, an id, its properties, each maybe missing. */
    public record Partial(Optional<String> name, Optional<UUID> id, PropertyMap properties) {
        public static final Partial EMPTY = new Partial(Optional.empty(), Optional.empty(), PropertyMap.EMPTY);
        private static final MapCodec<Partial> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ExtraCodecs.PLAYER_NAME.optionalFieldOf("name").forGetter(Partial::name),
                UUIDUtil.CODEC.optionalFieldOf("id").forGetter(Partial::id),
                ExtraCodecs.PROPERTY_MAP.optionalFieldOf("properties", PropertyMap.EMPTY).forGetter(Partial::properties))
                .apply(i, Partial::new));

        private GameProfile createProfile() {
            return createPartialProfile(name, id, properties);
        }
    }
}
