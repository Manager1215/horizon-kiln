package kiln.proof.mixins.mixin;

import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accessors on a hand-written stand-in, named as the mods on Modrinth name them (malilib's, the game's ALL). */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("name")
    String kiln$name();

    @Accessor
    int getClickCount();

    /** The key it is bound to, the game's field (112 mods' mixins name it, D-108). */
    @Accessor("key")
    com.mojang.blaze3d.platform.InputConstants.Key kiln$key();

    @Accessor
    void setClickCount(int count);

    @Accessor("ALL")
    static Map<String, KeyMapping> kiln$all() {
        throw new AssertionError();
    }

    /** No such field in the game: Kiln gives back nothing and says so. */
    @Accessor("noSuchField")
    String kiln$missing();
}
