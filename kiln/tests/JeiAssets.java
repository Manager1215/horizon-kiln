import com.mojang.blaze3d.platform.InputConstants;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

/**
 * kiln/tests/jei_assets.rs's Java (D-160), run as a source file with kiln-runtime.jar on the class path.
 * {@code write <jar>} makes a mod jar of test pictures; {@code check [jars...]}, with that jar (and JEI's, when given)
 * on the class path, draws them through {@code kiln.Sprites} and puts JEI's way of keeping its keys through Kiln's
 * {@code KeyMapping}. The game's lines ({@code sprite}, {@code keys}, {@code chosen}) go to standard output as Java
 * sends them; the checks' own say {@code result <what> <value>}.
 */
public class JeiAssets {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("write")) {
            write(Path.of(args[1]));
            return;
        }
        // Host's replies to standard output, as Java in a process of its own sends them.
        java.lang.reflect.Field out = Class.forName("kiln.Host").getDeclaredField("out");
        out.setAccessible(true);
        out.set(null, new PrintStream(new FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        sprites();
        for (int i = 1; i < args.length; i++) {
            jar(Path.of(args[i]));
        }
        keys();
        System.out.flush();
    }

    static void result(String what, Object value) {
        System.out.println("result " + what + " " + value);
    }

    static void sprites() {
        result("button", kiln.Sprites.lookup(Identifier.fromNamespaceAndPath("testassets", "button")));
        // Asked again: the same name, not sent again.
        result("button_again", kiln.Sprites.lookup(Identifier.fromNamespaceAndPath("testassets", "button")));
        result("palette", kiln.Sprites.lookup(Identifier.fromNamespaceAndPath("testassets", "palette")));
        result("animated", kiln.Sprites.lookup(Identifier.fromNamespaceAndPath("testassets", "animated")));
        result("interlaced", kiln.Sprites.lookup(Identifier.fromNamespaceAndPath("testassets", "interlaced")));
        result("missing", kiln.Sprites.lookup(Identifier.fromNamespaceAndPath("testassets", "missing")));
        result("vanilla", kiln.Sprites.lookup(Identifier.withDefaultNamespace("widget/button")));
        result("blocks_atlas", kiln.Sprites.lookup(Identifier.withDefaultNamespace("blocks"),
                Identifier.fromNamespaceAndPath("testassets", "button")));
        result("gui_atlas", kiln.Sprites.lookup(Identifier.withDefaultNamespace("gui"),
                Identifier.fromNamespaceAndPath("testassets", "button")));
        // A machine's 64x64 texture blitted as if 256x256 (vanilla's GUI textures' size): part (64, 32) 32x16 is
        // its pixels (16, 8) 8x4.
        result("blit", kiln.Sprites.lookup(Identifier.fromNamespaceAndPath("testassets", "textures/gui/machine.png"),
                64, 32, 32, 16, 256, 256));
        result("blit_outside", kiln.Sprites.lookup(
                Identifier.fromNamespaceAndPath("testassets", "textures/gui/machine.png"), 250, 0, 32, 16, 256, 256));
        com.google.gson.JsonObject meta = kiln.Sprites.guiMetadata(Identifier.withDefaultNamespace("gui"),
                Identifier.fromNamespaceAndPath("testassets", "button"));
        result("scaling", meta == null ? null : meta.getAsJsonObject("scaling").get("type").getAsString());
    }

    /** Every GUI sprite of a real mod's jar (JEI's): each one sent, none failing. */
    static void jar(Path jar) throws IOException {
        int sprites = 0, sent = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("assets/([^/]+)/textures/gui/sprites/(.+)\\.png").matcher(entry.getName());
                if (m.matches()) {
                    sprites++;
                    if (kiln.Sprites.lookup(Identifier.fromNamespaceAndPath(m.group(1), m.group(2))) != null) {
                        sent++;
                    }
                }
            }
        }
        result("jar_sprites", sprites);
        result("jar_sent", sent);
    }

    /** JEI's FabricKeyMapping: it keeps its key itself and sets vanilla's to none, out of vanilla's table. */
    static final class JeiLike extends KeyMapping {
        InputConstants.Key real;

        JeiLike(String name, int code, Category category) {
            super(name, InputConstants.Type.KEYBOARD, code, category);
            real = getDefaultKey();
            hide();
        }

        private void hide() {
            super.setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
        }

        @Override
        public void setKey(InputConstants.Key key) {
            real = key;
            hide();
        }

        @Override
        public String saveString() {
            return real.getName();
        }

        @Override
        public boolean matches(KeyEvent event) {
            return real.getValue() == event.key();
        }
    }

    static int code(String key) {
        return InputConstants.getKey(key).getValue();
    }

    static void keys() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("jei", "overlays"));
        System.out.println("result phase construct");
        JeiLike show = new JeiLike("key.jei.showRecipe", code("key.keyboard.r"), category);
        KeyMapping plain = new KeyMapping("key.testassets.plain", code("key.keyboard.k"), category);
        plain.setKey(InputConstants.getKey("key.keyboard.l"));
        System.out.println("result phase register");
        KeyMappingHelper.registerKeyMapping(show);
        KeyMappingHelper.registerKeyMapping(plain);
        KeyMappingHelper.kiln$announce("mezz.jei.fabric.JustEnoughItemsClient");
        System.out.println("result phase bind");
        // The player's choice on JEI's page.
        KeyMapping.kiln$bind("key.jei.showRecipe", "key.keyboard.y");
        result("y_matches", show.matches(new KeyEvent(code("key.keyboard.y"), 0, 0)));
        result("r_matches", show.matches(new KeyEvent(code("key.keyboard.r"), 0, 0)));
        result("bound_of", KeyMappingHelper.getBoundKeyOf(show).getName());
        System.out.println("result phase mod_sets");
        // JEI's own key screen sets it.
        show.setKey(InputConstants.getKey("key.keyboard.u"));
        result("u_matches", show.matches(new KeyEvent(code("key.keyboard.u"), 0, 0)));
        plain.setKey(InputConstants.getKey("key.keyboard.j"));
        System.out.println("result phase forget");
        KeyMapping.kiln$bind("key.jei.showRecipe", "");
        result("default_matches", show.matches(new KeyEvent(code("key.keyboard.r"), 0, 0)));
        result("plain_down_key", plain.kiln$key());
    }

    // The test jar: a mod's pictures as PNGs of several kinds.

    static void write(Path jar) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(jar.toFile()))) {
            put(zip, "fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"testassets\",\"version\":\"1\"}"
                    .getBytes(StandardCharsets.UTF_8));
            String sprites = "assets/testassets/textures/gui/sprites/";
            // 4x3 RGBA, each pixel (x*60, y*80, x+y, 255 - x), every row another filter.
            byte[] rgba = new byte[4 * 3 * 4];
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 4; x++) {
                    int i = (y * 4 + x) * 4;
                    rgba[i] = (byte) (x * 60);
                    rgba[i + 1] = (byte) (y * 80);
                    rgba[i + 2] = (byte) (x + y);
                    rgba[i + 3] = (byte) (255 - x);
                }
            }
            put(zip, sprites + "button.png", png(4, 3, 6, 8, rgba, 4, null, null, false));
            put(zip, sprites + "button.png.mcmeta", ("{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":4,"
                    + "\"height\":3,\"border\":1}}}").getBytes(StandardCharsets.UTF_8));
            // 3x2, 2-bit palette: red, green, blue (half transparent), white.
            byte[] palette = {(byte) 255, 0, 0, 0, (byte) 255, 0, 0, 0, (byte) 255, (byte) 255, (byte) 255, (byte) 255};
            byte[] alpha = {(byte) 255, (byte) 255, (byte) 128};
            byte[] indices = {0, 1, 2, 3, 2, 1};
            put(zip, sprites + "palette.png", png(3, 2, 3, 2, indices, 1, palette, alpha, false));
            // 2 wide, 3 frames of 2x2 (one colour each): the first frame alone.
            byte[] frames = new byte[2 * 6 * 4];
            for (int i = 0; i < frames.length; i += 4) {
                int frame = i / (2 * 2 * 4);
                frames[i] = (byte) (frame * 100);
                frames[i + 3] = (byte) 255;
            }
            put(zip, sprites + "animated.png", png(2, 6, 6, 8, frames, 4, null, null, false));
            put(zip, sprites + "animated.png.mcmeta", "{\"animation\":{\"frametime\":2}}"
                    .getBytes(StandardCharsets.UTF_8));
            // 9x9 RGB, Adam7: pixel (x, y) is (x*20, y*20, 7).
            byte[] rgb = new byte[9 * 9 * 3];
            for (int y = 0; y < 9; y++) {
                for (int x = 0; x < 9; x++) {
                    int i = (y * 9 + x) * 3;
                    rgb[i] = (byte) (x * 20);
                    rgb[i + 1] = (byte) (y * 20);
                    rgb[i + 2] = 7;
                }
            }
            put(zip, sprites + "interlaced.png", png(9, 9, 2, 8, rgb, 3, null, null, true));
            // 64x64 RGBA: pixel (x, y) is (x, y, 0, 255).
            byte[] machine = new byte[64 * 64 * 4];
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int i = (y * 64 + x) * 4;
                    machine[i] = (byte) x;
                    machine[i + 1] = (byte) y;
                    machine[i + 3] = (byte) 255;
                }
            }
            put(zip, "assets/testassets/textures/gui/machine.png", png(64, 64, 6, 8, machine, 4, null, null, false));
        }
    }

    static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    /**
     * A PNG of {@code samples} ({@code channels} a pixel, each {@code depth} bits: for depth < 8 one sample a byte,
     * packed here), colour type {@code type}; each row filtered with the next of the five filters.
     */
    static byte[] png(int width, int height, int type, int depth, byte[] samples, int channels, byte[] palette,
            byte[] alpha, boolean interlace) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        int[][] passes = interlace
                ? new int[][] {{0, 0, 8, 8}, {4, 0, 8, 8}, {0, 4, 4, 8}, {2, 0, 4, 4}, {0, 2, 2, 4}, {1, 0, 2, 2},
                        {0, 1, 1, 2}}
                : new int[][] {{0, 0, 1, 1}};
        int row = 0;
        for (int[] pass : passes) {
            int pw = (width - pass[0] + pass[2] - 1) / pass[2];
            int ph = (height - pass[1] + pass[3] - 1) / pass[3];
            if (pw <= 0 || ph <= 0) {
                continue;
            }
            int bits = channels * depth;
            int stride = (pw * bits + 7) / 8;
            int bpp = Math.max(1, bits / 8);
            byte[] previous = new byte[stride];
            for (int r = 0; r < ph; r++) {
                byte[] line = new byte[stride];
                int y = pass[1] + r * pass[3];
                for (int c = 0; c < pw; c++) {
                    int x = pass[0] + c * pass[2];
                    for (int s = 0; s < channels; s++) {
                        int v = samples[(y * width + x) * channels + s] & 0xFF;
                        if (depth == 8) {
                            line[c * channels + s] = (byte) v;
                        } else {
                            int bit = (c * channels + s) * depth;
                            line[bit >> 3] |= (byte) (v << (8 - depth - (bit & 7)));
                        }
                    }
                }
                int filter = row++ % 5;
                raw.write(filter);
                byte[] filtered = new byte[stride];
                for (int i = 0; i < stride; i++) {
                    int a = i >= bpp ? line[i - bpp] & 0xFF : 0;
                    int b = previous[i] & 0xFF;
                    int cc = i >= bpp ? previous[i - bpp] & 0xFF : 0;
                    int predict = switch (filter) {
                        case 0 -> 0;
                        case 1 -> a;
                        case 2 -> b;
                        case 3 -> (a + b) >>> 1;
                        default -> {
                            int p = a + b - cc, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - cc);
                            yield pa <= pb && pa <= pc ? a : pb <= pc ? b : cc;
                        }
                    };
                    filtered[i] = (byte) ((line[i] & 0xFF) - predict);
                }
                raw.write(filtered);
                previous = line;
            }
        }
        Deflater deflater = new Deflater();
        deflater.setInput(raw.toByteArray());
        deflater.finish();
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            idat.write(buffer, 0, deflater.deflate(buffer));
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream h = new DataOutputStream(ihdr);
        h.writeInt(width);
        h.writeInt(height);
        h.write(new byte[] {(byte) depth, (byte) type, 0, 0, (byte) (interlace ? 1 : 0)});
        chunk(png, "IHDR", ihdr.toByteArray());
        if (palette != null) {
            chunk(png, "PLTE", palette);
        }
        if (alpha != null) {
            chunk(png, "tRNS", alpha);
        }
        chunk(png, "IDAT", idat.toByteArray());
        chunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    static void chunk(ByteArrayOutputStream png, String type, byte[] data) throws IOException {
        DataOutputStream d = new DataOutputStream(png);
        d.writeInt(data.length);
        byte[] name = type.getBytes(StandardCharsets.ISO_8859_1);
        d.write(name);
        d.write(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        d.writeInt((int) crc.getValue());
    }
}
