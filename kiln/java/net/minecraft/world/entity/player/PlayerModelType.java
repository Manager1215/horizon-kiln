package net.minecraft.world.entity.player;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code PlayerModelType}: a skin's arms, slim or wide, saved by those names. */
public enum PlayerModelType implements StringRepresentable {
    SLIM("slim"), WIDE("wide");

    public static final Codec<PlayerModelType> CODEC = StringRepresentable.fromEnum(PlayerModelType::values);

    private final String id;

    PlayerModelType(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
