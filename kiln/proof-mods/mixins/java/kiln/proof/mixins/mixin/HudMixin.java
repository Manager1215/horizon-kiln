package kiln.proof.mixins.mixin;

import kiln.proof.mixins.Moments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The HUD drawn and ticked, as the HUD mods inject into it, with the game's own fields they shadow. */
@Mixin(Hud.class)
public abstract class HudMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private int tickCount;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void kiln$draws(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        Moments.saw("Hud HEAD" + (minecraft == Minecraft.getInstance() ? "" : " without the client"));
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void kiln$drew(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        Moments.saw("Hud TAIL");
    }

    /** A vanilla layer of the HUD's, inside the hotbar's group (D-100). */
    @Inject(method = "extractItemHotbar", at = @At("HEAD"))
    private void kiln$hotbar(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        Moments.saw("Hotbar HEAD");
    }

    /** A static one, inside the player's bars. */
    @Inject(method = "extractArmor", at = @At("HEAD"))
    private static void kiln$armor(GuiGraphicsExtractor graphics, net.minecraft.world.entity.player.Player player,
            int a, int b, int c, int d, CallbackInfo ci) {
        Moments.saw("Armor HEAD");
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void kiln$ticks(CallbackInfo ci) {
        Moments.saw("Hud.tick HEAD");
        Moments.hudTicks = tickCount + 1;
    }
}
