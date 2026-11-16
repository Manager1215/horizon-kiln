package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.resources.Identifier;

/**
 * The mods' GUI pictures for the game (D-160). Horizon's GUI atlas has vanilla's sprites and its content mods'; a Java
 * mod's own ({@code jei:} buttons and backgrounds, a machine's {@code textures/gui/*.png}) it doesn't have. Kiln reads
 * such a picture from the mod's jar the first time a mod draws it, and sends it to the game ({@code sprite}): the game
 * packs them into the one image a native mod may upload into its atlas ({@code native_mod_image}) and names each part
 * there, so the op that draws a sprite by name ({@code b}) draws it. Until it arrives (the next frame) that op draws its
 * fallback box.
 *
 * <p>Names: a sprite of the GUI atlas {@code ns:path} is {@code ns:gui/sprites/path} (vanilla's {@code
 * gui/sprites/path}, which the game has); a part of a texture a mod blits is {@code
 * kiln:blit/<ns>/<path>/<u>_<v>_<w>_<h>} in the picture's own pixels.
 */
public final class Sprites {
    private static final Map<String, int[]> SIZES = new ConcurrentHashMap<>();
    /** The largest picture the game's image takes (Horizon's {@code MOD_IMAGE_SIZE}). */
    static final int MAX = 1024;
    /** Pictures read, by file: null for one no jar has or that doesn't decode. */
    private static final Map<String, Optional<Picture>> PICTURES = new HashMap<>();
    /** Names sent to the game: what each one stands for, null when it can't be drawn. */
    private static final Map<String, String> SENT = new HashMap<>();

    private Sprites() {}

    /** A decoded picture: {@code width * height} RGBA pixels, four bytes each. */
    record Picture(int width, int height, byte[] rgba) {
        Picture crop(int x, int y, int w, int h) {
            byte[] out = new byte[w * h * 4];
            for (int row = 0; row < h; row++) {
                System.arraycopy(rgba, ((y + row) * width + x) * 4, out, row * w * 4, w * 4);
            }
            return new Picture(w, h, out);
        }
    }

    public static int[] size(Identifier atlas, Identifier sprite) {
        String file = file(atlas, sprite);
        return SIZES.computeIfAbsent(file, Sprites::read);
    }

    /**
     * The name the game draws GUI sprite {@code sprite} by ({@code blitSprite(pipeline, Identifier, ...)}: {@code
     * jei:button_enabled_v2}, {@code widget/button}), for the {@code b} op as it is; null when neither the game nor a
     * mod's jar has its picture. A mod's picture goes to the game with this call, the first time.
     */
    public static String lookup(Identifier sprite) {
        if (sprite == null) {
            return null;
        }
        if (sprite.getNamespace().equals("minecraft")) {
            return "gui/sprites/" + sprite.getPath();
        }
        String name = sprite.getNamespace() + ":gui/sprites/" + sprite.getPath();
        return send(name, "assets/" + sprite.getNamespace() + "/textures/gui/sprites/" + sprite.getPath() + ".png",
                null);
    }

    /**
     * The name of sprite {@code sprite} of atlas {@code atlas} ({@code TextureAtlasSprite.atlasLocation()}, {@code
     * contents().name()}): the GUI atlas's as {@link #lookup(Identifier)}; the other atlases' (blocks, items) aren't
     * the game's to draw by name: null.
     */
    public static String lookup(Identifier atlas, Identifier sprite) {
        if (atlas != null && sprite != null && !isGui(atlas) && modAtlasFile(atlas, sprite) != null) {
            return send("kiln:atlas/" + atlas.getNamespace() + "/" + sprite.getNamespace() + "/" + sprite.getPath(),
                    modAtlasFile(atlas, sprite), null);
        }
        return atlas != null && isGui(atlas) ? lookup(sprite) : null;
    }

    /**
     * The name of sprite {@code sprite} of any atlas for the {@code b} op: a GUI one's as {@link #lookup(Identifier)};
     * one of the blocks' or items' atlas ({@code minecraft:block/water_still}, a mod's fluid) is its picture's first
     * frame sent as {@code kiln:atlas/<ns>/<path>} (the HUD's atlas has the GUI's sprites, not the blocks', D-168).
     * Null when neither a jar nor the game's resources have it.
     */
    public static String lookupAny(Identifier atlas, Identifier sprite) {
        if (sprite == null) {
            return null;
        }
        if (atlas == null || isGui(atlas)) {
            return lookup(sprite);
        }
        return send("kiln:atlas/" + sprite.getNamespace() + "/" + sprite.getPath(), file(atlas, sprite), null);
    }

    /**
     * The name of part ({@code u}, {@code v}, {@code width} by {@code height}) of texture {@code texture} ({@code
     * jei:textures/gui/gui_vanilla.png}), whose whole is {@code textureWidth} by {@code textureHeight} as the mod
     * blits it ({@code blit(pipeline, texture, x, y, u, v, w, h, [regionWidth, regionHeight,] textureWidth,
     * textureHeight)}: the region's size). A picture larger than the mod says (a resource pack's) is cut where the
     * same part of it is. Null when no jar nor the game's resources have the texture, or the part is outside it.
     */
    public static String lookup(Identifier texture, float u, float v, int width, int height, int textureWidth,
            int textureHeight) {
        if (texture == null || width <= 0 || height <= 0 || textureWidth <= 0 || textureHeight <= 0) {
            return null;
        }
        net.minecraft.client.renderer.texture.DynamicTexture dynamic;
        synchronized (Sprites.class) {
            dynamic = DYNAMIC.get(texture);
        }
        if (dynamic != null) {
            return dynamicPart(texture, dynamic, u, v, width, height, textureWidth, textureHeight);
        }
        String path = texture.getPath();
        String file = "assets/" + texture.getNamespace() + "/" + (path.endsWith(".png") ? path : path + ".png");
        String stem = path.endsWith(".png") ? path.substring(0, path.length() - 4) : path;
        Picture whole;
        synchronized (Sprites.class) {
            whole = picture(file);
        }
        if (whole == null) {
            return null;
        }
        double sx = (double) whole.width() / textureWidth;
        double sy = (double) whole.height() / textureHeight;
        int x = (int) Math.round(u * sx), y = (int) Math.round(v * sy);
        int w = (int) Math.round(width * sx), h = (int) Math.round(height * sy);
        if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > whole.width() || y + h > whole.height()) {
            return null;
        }
        String name = "kiln:blit/" + texture.getNamespace() + "/" + stem + "/" + x + "_" + y + "_" + w + "_" + h;
        int[] part = {x, y, w, h};
        return send(name, file, part);
    }

    /** The textures mods registered from pixels in memory ({@code TextureManager.register}), by their ids. */
    private static final Map<Identifier, net.minecraft.client.renderer.texture.DynamicTexture> DYNAMIC = new HashMap<>();
    /** The names of dynamic textures' parts sent to the game, and which of the texture's versions each was. */
    private static final Map<String, Integer> DYNAMIC_SENT = new HashMap<>();

    /**
     * Texture {@code id} is the pixels of {@code texture} now ({@code TextureManager.register}), or no longer one of
     * pixels in memory (null: released, or another kind registered in its place). D-179.
     */
    public static synchronized void dynamic(Identifier id, net.minecraft.client.renderer.texture.DynamicTexture texture) {
        if (id == null) {
            return;
        }
        if (texture == null) {
            DYNAMIC.remove(id);
        } else {
            DYNAMIC.put(id, texture);
        }
    }

    /**
     * The name of a part of dynamic texture {@code texture} (registered as {@code id}), as {@link #lookup(Identifier,
     * float, float, int, int, int, int)} names a jar's picture's part: {@code kiln:dyn/<ns>/<path>/<x>_<y>_<w>_<h>}.
     * Its pixels go to the game the first time and again once the mod changed them ({@code upload}, {@code
     * setPixels}): the game puts a part of the same size again where it was (D-179). Null without pixels.
     */
    private static synchronized String dynamicPart(Identifier id, net.minecraft.client.renderer.texture.DynamicTexture texture,
            float u, float v, int width, int height, int textureWidth, int textureHeight) {
        com.mojang.blaze3d.platform.NativeImage image = texture.getPixels();
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return null;
        }
        double sx = (double) image.getWidth() / textureWidth;
        double sy = (double) image.getHeight() / textureHeight;
        int x = (int) Math.round(u * sx), y = (int) Math.round(v * sy);
        int w = (int) Math.round(width * sx), h = (int) Math.round(height * sy);
        if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > image.getWidth() || y + h > image.getHeight() || w > MAX
                || h > MAX) {
            return null;
        }
        String name = "kiln:dyn/" + id.getNamespace() + "/" + id.getPath() + "/" + x + "_" + y + "_" + w + "_" + h;
        Integer sent = DYNAMIC_SENT.get(name);
        if (sent == null || sent != texture.kiln$version()) {
            byte[] rgba = new byte[w * h * 4];
            for (int row = 0; row < h; row++) {
                for (int col = 0; col < w; col++) {
                    int p = image.getPixel(x + col, y + row);
                    int at = (row * w + col) * 4;
                    rgba[at] = (byte) p;
                    rgba[at + 1] = (byte) (p >> 8);
                    rgba[at + 2] = (byte) (p >> 16);
                    rgba[at + 3] = (byte) (p >>> 24);
                }
            }
            DYNAMIC_SENT.put(name, texture.kiln$version());
            Host.send("sprite", name, Integer.toString(w), Integer.toString(h),
                    Base64.getEncoder().encodeToString(rgba));
        }
        return name;
    }

    /**
     * The {@code gui} section of GUI sprite {@code sprite}'s {@code .png.mcmeta} (its {@code scaling}: stretch, tile,
     * nine_slice and their sizes), for {@code SpriteContents.getAdditionalMetadata}; null without one.
     */
    public static JsonObject guiMetadata(Identifier atlas, Identifier sprite) {
        JsonObject meta = metadata(file(atlas, sprite));
        return meta != null && meta.has("gui") && meta.get("gui").isJsonObject() ? meta.getAsJsonObject("gui") : null;
    }

    /** The picture of a sprite of atlas {@code atlas} in a jar or the game's resources. */
    private static String file(Identifier atlas, Identifier sprite) {
        String own = isGui(atlas) ? null : modAtlasFile(atlas, sprite);
        if (own != null) {
            return own;
        }
        return "assets/" + sprite.getNamespace() + "/textures/" + (isGui(atlas) ? "gui/sprites/" : "")
                + sprite.getPath() + ".png";
    }

    private static boolean isGui(Identifier atlas) {
        String path = atlas.getPath();
        return atlas.getNamespace().equals("minecraft") && (path.equals("gui") || path.equals("textures/atlas/gui.png"));
    }

    /** {@code name} for {@code file}'s picture (or {@code part} of it), sent to the game once. */
    private static synchronized String send(String name, String file, int[] part) {
        if (SENT.containsKey(name)) {
            return SENT.get(name) != null ? name : null;
        }
        Picture picture = picture(file);
        if (picture != null && part != null) {
            picture = picture.crop(part[0], part[1], part[2], part[3]);
        } else if (picture != null && animated(file)) {
            // An animated sprite is a column of frames, square unless its mcmeta says how high: the first one.
            int frame = frameHeight(file, picture);
            if (frame < picture.height()) {
                picture = picture.crop(0, 0, picture.width(), frame);
            }
        }
        if (picture == null || picture.width() > MAX || picture.height() > MAX) {
            SENT.put(name, null);
            return null;
        }
        SENT.put(name, file);
        Host.send("sprite", name, Integer.toString(picture.width()), Integer.toString(picture.height()),
                Base64.getEncoder().encodeToString(picture.rgba()));
        return name;
    }

    /** Forgets what the game was sent: a new game (Java started again for it) hears it all again. For tests. */
    static synchronized void kiln$forget() {
        SENT.clear();
        DYNAMIC_SENT.clear();
        PICTURES.clear();
        SIZES.clear();
        METADATA.clear();
    }

    private static Picture picture(String file) {
        return PICTURES.computeIfAbsent(file, f -> {
            byte[] bytes = bytes(f);
            if (bytes == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(decode(bytes));
            } catch (IOException | RuntimeException e) {
                Host.send("log", "Kiln can't read the picture " + f + ": " + e.getMessage());
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static boolean animated(String file) {
        JsonObject meta = metadata(file);
        return meta != null && meta.has("animation");
    }

    private static int frameHeight(String file, Picture picture) {
        JsonObject meta = metadata(file);
        JsonElement animation = meta == null ? null : meta.get("animation");
        if (animation != null && animation.isJsonObject() && animation.getAsJsonObject().has("height")) {
            try {
                return Math.max(1, Math.min(picture.height(), animation.getAsJsonObject().get("height").getAsInt()));
            } catch (RuntimeException e) {
                // as square
            }
        }
        return Math.min(picture.width(), picture.height());
    }

    /**
     * A picture's {@code .mcmeta}, read once: the drawing asks for it each frame (a sprite's nine slices), and a read
     * opens every mod's jar.
     */
    private static JsonObject metadata(String file) {
        return METADATA.computeIfAbsent(file, f -> Optional.ofNullable(readMetadata(f))).orElse(null);
    }

    private static final Map<String, Optional<JsonObject>> METADATA = new ConcurrentHashMap<>();

    private static JsonObject readMetadata(String file) {
        byte[] bytes = bytes(file + ".mcmeta");
        if (bytes == null) {
            return null;
        }
        try (InputStreamReader r = new InputStreamReader(new java.io.ByteArrayInputStream(bytes),
                StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(r);
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** A file of the mods' jars, else of the game's prepared resources ({@code -Dkiln.resources}); null: none has it. */
    private static byte[] bytes(String file) {
        for (Path jar : Recipes.modJars()) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry entry = zip.getEntry(file);
                if (entry != null) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        return in.readAllBytes();
                    }
                }
            } catch (IOException e) {
                // the next jar
            }
        }
        String resources = System.getProperty("kiln.resources", "");
        if (!resources.isEmpty()) {
            Path path = Path.of(resources, file);
            if (Files.isRegularFile(path)) {
                try {
                    return Files.readAllBytes(path);
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static int[] read(String file) {
        for (Path jar : Recipes.modJars()) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry entry = zip.getEntry(file);
                if (entry != null) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        return frame(header(in), zip.getEntry(file + ".mcmeta") != null);
                    }
                }
            } catch (IOException e) {
                // the next jar
            }
        }
        String resources = System.getProperty("kiln.resources", "");
        if (!resources.isEmpty()) {
            Path png = Path.of(resources, file);
            if (Files.isRegularFile(png)) {
                try (InputStream in = Files.newInputStream(png)) {
                    return frame(header(in), Files.isRegularFile(Path.of(resources, file + ".mcmeta")));
                } catch (IOException e) {
                    // unknown
                }
            }
        }
        return new int[] {16, 16};
    }

    private static int[] frame(int[] size, boolean animated) {
        return animated && size[1] > size[0] ? new int[] {size[0], size[0]} : size;
    }

    /** A PNG's width and height, from its IHDR chunk. */
    private static int[] header(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        byte[] signature = new byte[16];
        data.readFully(signature);
        return new int[] {data.readInt(), data.readInt()};
    }

    /**
     * A PNG as RGBA: every colour type and bit depth, interlaced or not, a palette's or a colour's transparency
     * ({@code tRNS}); 16-bit samples keep their high byte. Kiln's own decoder: AWT's ImageIO would load AWT into the
     * game's process (D-112).
     */
    static Picture decode(byte[] png) throws IOException {
        if (png.length < 8 || (png[0] & 0xFF) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
            throw new IOException("not a PNG");
        }
        int width = 0, height = 0, depth = 0, type = 0, interlace = 0;
        byte[] palette = null, transparency = null;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int at = 8;
        while (at + 8 <= png.length) {
            int length = int32(png, at);
            String chunk = new String(png, at + 4, 4, StandardCharsets.ISO_8859_1);
            int start = at + 8;
            if (length < 0 || start + length > png.length) {
                throw new IOException("a chunk runs past the file's end");
            }
            switch (chunk) {
                case "IHDR" -> {
                    width = int32(png, start);
                    height = int32(png, start + 4);
                    depth = png[start + 8] & 0xFF;
                    type = png[start + 9] & 0xFF;
                    interlace = png[start + 12] & 0xFF;
                }
                case "PLTE" -> palette = java.util.Arrays.copyOfRange(png, start, start + length);
                case "tRNS" -> transparency = java.util.Arrays.copyOfRange(png, start, start + length);
                case "IDAT" -> data.write(png, start, length);
                default -> {}
            }
            if (chunk.equals("IEND")) {
                break;
            }
            at = start + length + 4;
        }
        if (width <= 0 || height <= 0 || (long) width * height > 64L * 1024 * 1024) {
            throw new IOException("size " + width + "x" + height);
        }
        int channels = switch (type) {
            case 0, 3 -> 1;
            case 2 -> 3;
            case 4 -> 2;
            case 6 -> 4;
            default -> throw new IOException("colour type " + type);
        };
        if (depth != 1 && depth != 2 && depth != 4 && depth != 8 && depth != 16) {
            throw new IOException("bit depth " + depth);
        }
        if (type == 3 && palette == null) {
            throw new IOException("no palette");
        }
        byte[] raw = inflate(data.toByteArray());
        int bits = channels * depth;
        int bpp = Math.max(1, bits / 8);
        byte[] rgba = new byte[width * height * 4];
        int[][] passes = interlace == 1
                ? new int[][] {{0, 0, 8, 8}, {4, 0, 8, 8}, {0, 4, 4, 8}, {2, 0, 4, 4}, {0, 2, 2, 4}, {1, 0, 2, 2},
                        {0, 1, 1, 2}}
                : new int[][] {{0, 0, 1, 1}};
        int offset = 0;
        for (int[] pass : passes) {
            int pw = (width - pass[0] + pass[2] - 1) / pass[2];
            int ph = (height - pass[1] + pass[3] - 1) / pass[3];
            if (pw <= 0 || ph <= 0) {
                continue;
            }
            int stride = (pw * bits + 7) / 8;
            byte[] previous = new byte[stride];
            byte[] line = new byte[stride];
            for (int row = 0; row < ph; row++) {
                if (offset + 1 + stride > raw.length) {
                    throw new IOException("the image data ends early");
                }
                int filter = raw[offset] & 0xFF;
                System.arraycopy(raw, offset + 1, line, 0, stride);
                offset += 1 + stride;
                unfilter(filter, line, previous, bpp);
                int y = pass[1] + row * pass[3];
                for (int col = 0; col < pw; col++) {
                    int x = pass[0] + col * pass[2];
                    pixel(line, col, type, depth, channels, palette, transparency, rgba, (y * width + x) * 4);
                }
                byte[] swap = previous;
                previous = line;
                line = swap;
            }
        }
        return new Picture(width, height, rgba);
    }

    private static void unfilter(int filter, byte[] line, byte[] previous, int bpp) throws IOException {
        for (int i = 0; i < line.length; i++) {
            int a = i >= bpp ? line[i - bpp] & 0xFF : 0;
            int b = previous[i] & 0xFF;
            int c = i >= bpp ? previous[i - bpp] & 0xFF : 0;
            int add = switch (filter) {
                case 0 -> 0;
                case 1 -> a;
                case 2 -> b;
                case 3 -> (a + b) >>> 1;
                case 4 -> {
                    int p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
                    yield pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
                }
                default -> throw new IOException("filter " + filter);
            };
            line[i] = (byte) (line[i] + add);
        }
    }

    /** Sample {@code index} (0..channels) of pixel {@code col} of an unfiltered line, as 8 bits. */
    private static int sample(byte[] line, int col, int index, int depth, int channels) {
        if (depth == 8) {
            return line[col * channels + index] & 0xFF;
        }
        if (depth == 16) {
            return line[(col * channels + index) * 2] & 0xFF;
        }
        int bit = (col * channels + index) * depth;
        int value = ((line[bit >> 3] & 0xFF) >> (8 - depth - (bit & 7))) & ((1 << depth) - 1);
        return value;
    }

    private static void pixel(byte[] line, int col, int type, int depth, int channels, byte[] palette,
            byte[] transparency, byte[] rgba, int out) {
        int r, g, b, a = 255;
        switch (type) {
            case 3 -> {
                int i = sample(line, col, 0, depth, 1);
                r = i * 3 + 2 < palette.length ? palette[i * 3] & 0xFF : 0;
                g = i * 3 + 2 < palette.length ? palette[i * 3 + 1] & 0xFF : 0;
                b = i * 3 + 2 < palette.length ? palette[i * 3 + 2] & 0xFF : 0;
                a = transparency != null && i < transparency.length ? transparency[i] & 0xFF : 255;
            }
            case 0, 4 -> {
                int v = sample(line, col, 0, depth, channels);
                int raw = v;
                if (depth < 8) {
                    v = v * 255 / ((1 << depth) - 1);
                }
                r = g = b = v;
                if (type == 4) {
                    a = sample(line, col, 1, depth, channels);
                } else if (transparency != null && transparency.length >= 2) {
                    int key = ((transparency[0] & 0xFF) << 8) | (transparency[1] & 0xFF);
                    int full = depth == 16 ? fullSample(line, col, 0, channels) : raw;
                    a = full == key ? 0 : 255;
                }
            }
            default -> {
                r = sample(line, col, 0, depth, channels);
                g = sample(line, col, 1, depth, channels);
                b = sample(line, col, 2, depth, channels);
                if (type == 6) {
                    a = sample(line, col, 3, depth, channels);
                } else if (transparency != null && transparency.length >= 6) {
                    boolean same = depth == 16
                            ? fullSample(line, col, 0, channels) == int16(transparency, 0)
                                    && fullSample(line, col, 1, channels) == int16(transparency, 2)
                                    && fullSample(line, col, 2, channels) == int16(transparency, 4)
                            : r == int16(transparency, 0) && g == int16(transparency, 2) && b == int16(transparency, 4);
                    a = same ? 0 : 255;
                }
            }
        }
        rgba[out] = (byte) r;
        rgba[out + 1] = (byte) g;
        rgba[out + 2] = (byte) b;
        rgba[out + 3] = (byte) a;
    }

    private static int fullSample(byte[] line, int col, int index, int channels) {
        int i = (col * channels + index) * 2;
        return ((line[i] & 0xFF) << 8) | (line[i + 1] & 0xFF);
    }

    private static int int16(byte[] b, int at) {
        return ((b[at] & 0xFF) << 8) | (b[at + 1] & 0xFF);
    }

    private static int int32(byte[] b, int at) {
        return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }

    private static byte[] inflate(byte[] data) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4);
        byte[] buffer = new byte[64 * 1024];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                out.write(buffer, 0, n);
            }
        } catch (DataFormatException e) {
            throw new IOException("bad image data: " + e.getMessage());
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    /**
     * A PNG's pixels as RGBA bytes, its width and height in {@code size} (D-170: {@code NativeImage.read}, a mod's own
     * picture in memory).
     */
    public static byte[] rgba(byte[] png, int[] size) throws IOException {
        Picture p = decode(png);
        size[0] = p.width();
        size[1] = p.height();
        return p.rgba();
    }

    /**
     * The picture of sprite {@code sprite} of a mod's own atlas ({@code mezz_config:textures/atlas/gui.png}): where
     * the atlas's {@code atlases/<name>.json} directory sources say ({@code textures/<source>/<path without the
     * prefix>.png}); null for the game's atlases or a sprite none of them has (D-170: Mezz Config GUI's).
     */
    private static String modAtlasFile(Identifier atlas, Identifier sprite) {
        if (atlas.getNamespace().equals("minecraft")) {
            return null;
        }
        return MOD_ATLAS_FILES.computeIfAbsent(atlas + " " + sprite, k -> {
            String name = atlas.getPath().replaceFirst("^textures/atlas/", "").replaceFirst("\\.png$", "");
            byte[] json = bytes("assets/" + atlas.getNamespace() + "/atlases/" + name + ".json");
            if (json == null) {
                return Optional.empty();
            }
            try {
                for (JsonElement source : JsonParser.parseString(new String(json, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("sources")) {
                    JsonObject o = source.getAsJsonObject();
                    if (!"directory".equals(text(o, "type")) && !"minecraft:directory".equals(text(o, "type"))) {
                        continue;
                    }
                    String prefix = text(o, "prefix");
                    if (!sprite.getPath().startsWith(prefix)) {
                        continue;
                    }
                    String file = "assets/" + sprite.getNamespace() + "/textures/" + text(o, "source") + "/"
                            + sprite.getPath().substring(prefix.length()) + ".png";
                    if (bytes(file) != null) {
                        return Optional.of(file);
                    }
                }
            } catch (RuntimeException e) {
                // no sources read: none
            }
            return Optional.empty();
        }).orElse(null);
    }

    private static final Map<String, Optional<String>> MOD_ATLAS_FILES = new ConcurrentHashMap<>();

    private static String text(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }
}
