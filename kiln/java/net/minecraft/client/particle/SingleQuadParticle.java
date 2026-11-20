package net.minecraft.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

/**
 * Kiln's stand-in for {@code SingleQuadParticle}: a particle that is one square of a sprite, its size, colour and
 * alpha. It lives as {@link Particle} does; Kiln draws no particle of a mod's ({@link #extract} gives nothing).
 */
public abstract class SingleQuadParticle extends Particle {
    public float quadSize;
    public float rCol = 1.0F;
    public float gCol = 1.0F;
    public float bCol = 1.0F;
    public float alpha = 1.0F;
    public float roll;
    public float oRoll;
    public TextureAtlasSprite sprite;

    protected SingleQuadParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z);
        this.sprite = sprite;
        this.quadSize = 0.1F * (random.nextFloat() * 0.5F + 0.5F) * 2.0F;
    }

    protected SingleQuadParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
            TextureAtlasSprite sprite) {
        super(level, x, y, z, xd, yd, zd);
        this.sprite = sprite;
        this.quadSize = 0.1F * (random.nextFloat() * 0.5F + 0.5F) * 2.0F;
    }

    /** Kiln's own: a particle in no level (a generated subclass's). */
    protected SingleQuadParticle() {
        super();
    }

    public FacingCameraMode getFacingCameraMode() {
        return FacingCameraMode.LOOKAT_XYZ;
    }

    /** What the game draws of it: nothing in Kiln. */
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {}

    protected void extractRotatedQuad(QuadParticleRenderState state, Camera camera, Quaternionf rotation,
            float partialTick) {}

    protected void extractRotatedQuad(QuadParticleRenderState state, Quaternionf rotation, float x, float y, float z,
            float partialTick) {}

    public float getQuadSize(float partialTick) {
        return quadSize;
    }

    @Override
    public Particle scale(float scale) {
        quadSize *= scale;
        return super.scale(scale);
    }

    @Override
    public ParticleRenderType getGroup() {
        return ParticleRenderType.SINGLE_QUADS;
    }

    /** Its sprite for its age, from the set. */
    public void setSpriteFromAge(SpriteSet sprites) {
        if (!removed && sprites != null) {
            setSprite(sprites.get(age, lifetime));
        }
    }

    protected void setSprite(TextureAtlasSprite sprite) {
        this.sprite = sprite;
    }

    protected float getU0() {
        return sprite == null ? 0 : sprite.getU0();
    }

    protected float getU1() {
        return sprite == null ? 1 : sprite.getU1();
    }

    protected float getV0() {
        return sprite == null ? 0 : sprite.getV0();
    }

    protected float getV1() {
        return sprite == null ? 1 : sprite.getV1();
    }

    protected abstract Layer getLayer();

    public void setColor(float r, float g, float b) {
        rCol = r;
        gCol = g;
        bCol = b;
    }

    protected void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    @Override
    public String toString() {
        return super.toString() + ", RGBA (" + rCol + "," + gCol + "," + bCol + "," + alpha + "), Scale " + quadSize;
    }

    /** Which atlas its sprite is on, and whether it is see-through; the pipelines are the game's, none in Kiln. */
    public record Layer(boolean translucent, Identifier textureAtlasLocation,
            com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
            net.minecraft.client.renderer.oit.OitPipelineSet oitPipelineSet) {
        private static final Identifier ITEMS = Identifier.withDefaultNamespace("textures/atlas/items.png");
        private static final Identifier PARTICLES = Identifier.withDefaultNamespace("textures/atlas/particles.png");
        public static final Layer OPAQUE_TERRAIN = new Layer(false, TextureAtlas.LOCATION_BLOCKS, null);
        public static final Layer TRANSLUCENT_TERRAIN = new Layer(true, TextureAtlas.LOCATION_BLOCKS, null);
        public static final Layer OPAQUE_ITEMS = new Layer(false, ITEMS, null);
        public static final Layer TRANSLUCENT_ITEMS = new Layer(true, ITEMS, null);
        public static final Layer OPAQUE = new Layer(false, PARTICLES, null);
        public static final Layer TRANSLUCENT = new Layer(true, PARTICLES, null);

        public Layer(boolean translucent, Identifier textureAtlasLocation,
                com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline) {
            this(translucent, textureAtlasLocation, pipeline, null);
        }

        /** The see-through layer of the sprite's atlas. */
        public static Layer bySprite(TextureAtlasSprite sprite) {
            Identifier atlas = sprite == null ? PARTICLES : sprite.atlasLocation();
            if (TextureAtlas.LOCATION_BLOCKS.equals(atlas)) {
                return TRANSLUCENT_TERRAIN;
            }
            return ITEMS.equals(atlas) ? TRANSLUCENT_ITEMS : TRANSLUCENT;
        }
    }

    /** How the square turns to the camera: Kiln draws none, so it turns nothing. */
    public interface FacingCameraMode {
        FacingCameraMode LOOKAT_XYZ = (rotation, camera, partialTick) -> {};
        FacingCameraMode LOOKAT_Y = (rotation, camera, partialTick) -> {};

        void setRotation(Quaternionf rotation, Camera camera, float partialTick);
    }
}
