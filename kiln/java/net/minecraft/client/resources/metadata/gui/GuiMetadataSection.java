package net.minecraft.client.resources.metadata.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Kiln's stand-in for {@code GuiMetadataSection}: a GUI sprite's {@code gui} section, how it scales, read from the
 * sprite's {@code .mcmeta} in a mod's jar or the game's resources (kiln.Sprites.guiMetadata); stretched without one.
 */
public record GuiMetadataSection(GuiSpriteScaling scaling) {
    public static final GuiMetadataSection DEFAULT = new GuiMetadataSection(GuiSpriteScaling.DEFAULT);
    public static final net.minecraft.server.packs.metadata.MetadataSectionType<GuiMetadataSection> TYPE =
            new net.minecraft.server.packs.metadata.MetadataSectionType<>("gui", null);

    /** Kiln's own: the section {@code gui} of an {@code .mcmeta} as vanilla's codec reads it; DEFAULT for null or a bad one. */
    public static GuiMetadataSection of(JsonObject gui) {
        if (gui == null || !gui.has("scaling") || !gui.get("scaling").isJsonObject()) {
            return DEFAULT;
        }
        try {
            JsonObject s = gui.getAsJsonObject("scaling");
            String type = s.has("type") ? s.get("type").getAsString() : "stretch";
            return switch (type) {
                case "tile" -> new GuiMetadataSection(
                        new GuiSpriteScaling.Tile(s.get("width").getAsInt(), s.get("height").getAsInt()));
                case "nine_slice" -> new GuiMetadataSection(new GuiSpriteScaling.NineSlice(s.get("width").getAsInt(),
                        s.get("height").getAsInt(), border(s.get("border")),
                        s.has("stretch_inner") && s.get("stretch_inner").getAsBoolean()));
                default -> DEFAULT;
            };
        } catch (RuntimeException e) {
            return DEFAULT;
        }
    }

    /** A nine-slice border: one number for every side, or {@code left}, {@code top}, {@code right}, {@code bottom}. */
    private static GuiSpriteScaling.NineSlice.Border border(JsonElement border) {
        if (border == null) {
            return new GuiSpriteScaling.NineSlice.Border(0);
        }
        if (border.isJsonPrimitive()) {
            return new GuiSpriteScaling.NineSlice.Border(border.getAsInt());
        }
        JsonObject b = border.getAsJsonObject();
        return new GuiSpriteScaling.NineSlice.Border(b.get("left").getAsInt(), b.get("top").getAsInt(),
                b.get("right").getAsInt(), b.get("bottom").getAsInt());
    }
}
