package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Kiln's stand-in for Fabric's {@code HudElement}: something a mod draws on the HUD. Kiln asks once a tick. */
@FunctionalInterface
public interface HudElement {
    void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);
}
