package net.minecraft.client.resources.metadata.gui;

/**
 * Kiln's stand-in for {@code GuiSpriteScaling}: how a GUI sprite fills a box bigger than itself, from its
 * {@code .mcmeta}: stretched, tiled, or nine-sliced (its edges kept, its middle stretched or tiled).
 */
public interface GuiSpriteScaling {
    GuiSpriteScaling DEFAULT = new Stretch();

    Type type();

    record Stretch() implements GuiSpriteScaling {
        @Override
        public Type type() {
            return Type.STRETCH;
        }
    }

    record Tile(int width, int height) implements GuiSpriteScaling {
        @Override
        public Type type() {
            return Type.TILE;
        }
    }

    record NineSlice(int width, int height, Border border, boolean stretchInner) implements GuiSpriteScaling {
        public NineSlice(int width, int height, Border border) {
            this(width, height, border, false);
        }

        @Override
        public Type type() {
            return Type.NINE_SLICE;
        }

        public record Border(int left, int top, int right, int bottom) {
            public Border(int all) {
                this(all, all, all, all);
            }
        }
    }

    enum Type implements net.minecraft.util.StringRepresentable {
        STRETCH("stretch"),
        TILE("tile"),
        NINE_SLICE("nine_slice");

        private final String key;

        Type(String key) {
            this.key = key;
        }

        @Override
        public String getSerializedName() {
            return key;
        }
    }
}
