package net.minecraft.world.entity.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code PlayerSkin}: a player's textures and arms. Horizon draws skins; Kiln has the record, and
 * {@link Patch}, what a player head's profile sets in place of its owner's skin, as the game saves it.
 */
public record PlayerSkin(ClientAsset.Texture body, ClientAsset.Texture cape, ClientAsset.Texture elytra,
        PlayerModelType model, boolean secure) {

    /** A skin whose textures aren't signed: the game's {@code insecure}. */
    public static PlayerSkin insecure(ClientAsset.Texture body, ClientAsset.Texture cape, ClientAsset.Texture elytra,
            PlayerModelType model) {
        return new PlayerSkin(body, cape, elytra, model, false);
    }

    /** The textures and arms a profile sets over its owner's: {@code texture}, {@code cape}, {@code elytra}, {@code model}. */
    public record Patch(Optional<ClientAsset.ResourceTexture> body, Optional<ClientAsset.ResourceTexture> cape,
            Optional<ClientAsset.ResourceTexture> elytra, Optional<PlayerModelType> model) {
        public static final Patch EMPTY = new Patch(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        /** A texture by its id ({@code minecraft:entity/player/wide/steve}). */
        private static final Codec<ClientAsset.ResourceTexture> TEXTURE =
                Identifier.CODEC.xmap(ClientAsset.ResourceTexture::new, ClientAsset.ResourceTexture::id);
        public static final MapCodec<Patch> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                TEXTURE.optionalFieldOf("texture").forGetter(Patch::body),
                TEXTURE.optionalFieldOf("cape").forGetter(Patch::cape),
                TEXTURE.optionalFieldOf("elytra").forGetter(Patch::elytra),
                PlayerModelType.CODEC.optionalFieldOf("model").forGetter(Patch::model))
                .apply(i, Patch::create));

        public static Patch create(Optional<ClientAsset.ResourceTexture> body, Optional<ClientAsset.ResourceTexture> cape,
                Optional<ClientAsset.ResourceTexture> elytra, Optional<PlayerModelType> model) {
            return body.isEmpty() && cape.isEmpty() && elytra.isEmpty() && model.isEmpty() ? EMPTY
                    : new Patch(body, cape, elytra, model);
        }
    }
}
