package kiln;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * What a soft switch ({@link SoftSwitch}) can stop of a mod, read from its jar with nothing run: Kiln's own copy of
 * {@code tools/soft-off.py}'s reading (D-113, D-116), for a jar the catalogue's copy ({@link SoftOff}) hasn't. Same
 * classes, same reasons, same words:
 * <ul>
 *   <li>{@code safe}: the mod works only through what the switch stops: events, HUD, keys, commands, {@code @Inject}
 *   and the {@code @Modify*} handlers Kiln guards ({@link MixinGuards});</li>
 *   <li>{@code partial}: it also leaves something a switch can't take back: blocks, items or entities registered, its
 *   own threads, network channels, world generation, a data or resource pack's files;</li>
 *   <li>{@code restart}: a mixin into the game's classes that replaces or wraps the game's code ({@code @Overwrite},
 *   {@code @Redirect}, MixinExtras' {@code @WrapOperation} and {@code @WrapMethod}, {@code @ModifyArgs}), or a
 *   modifier Kiln can't guard.</li>
 * </ul>
 * {@link #RULES} is the reading's version, {@code tools/soft-off.py}'s {@code RULES}: the two change together.
 */
public final class SoftOffScan {
    /** The reading's version; raised with {@code tools/soft-off.py}'s whenever a jar can get another class. */
    public static final int RULES = 1;

    /** A jar's class ({@code safe}, {@code partial}, {@code restart}) and why, in the reading's words. */
    public record Reading(String cls, List<String> why) {
        public String whyText() {
            return String.join("; ", why);
        }
    }

    private static final String[] GAME = {"net/minecraft/", "com/mojang/blaze3d/"};
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/";
    private static final String EXTRAS = "Lcom/llamalad7/mixinextras/";
    private static final Map<String, String> KINDS = new HashMap<>();

    static {
        KINDS.put(MIXIN + "gen/Accessor;", "accessor");
        KINDS.put(MIXIN + "gen/Invoker;", "invoker");
        KINDS.put(MIXIN + "injection/Inject;", "inject");
        KINDS.put(MIXIN + "injection/Redirect;", "redirect");
        KINDS.put(MIXIN + "injection/ModifyArg;", "modify-arg");
        KINDS.put(MIXIN + "injection/ModifyArgs;", "modify-args");
        KINDS.put(MIXIN + "injection/ModifyVariable;", "modify-variable");
        KINDS.put(MIXIN + "injection/ModifyConstant;", "modify-constant");
        KINDS.put(MIXIN + "Overwrite;", "overwrite");
        KINDS.put(MIXIN + "Shadow;", "shadow");
        KINDS.put(MIXIN + "Unique;", "unique");
        KINDS.put(MIXIN + "Intrinsic;", "intrinsic");
        KINDS.put(EXTRAS + "injector/ModifyReturnValue;", "extras:modify-return-value");
        KINDS.put(EXTRAS + "injector/ModifyExpressionValue;", "extras:modify-expression-value");
        KINDS.put(EXTRAS + "injector/ModifyReceiver;", "extras:modify-receiver");
        KINDS.put(EXTRAS + "injector/wrapoperation/WrapOperation;", "extras:wrap-operation");
        KINDS.put(EXTRAS + "injector/WrapWithCondition;", "extras:wrap-with-condition");
        KINDS.put(EXTRAS + "injector/v2/WrapWithCondition;", "extras:wrap-with-condition");
        KINDS.put(EXTRAS + "injector/wrapmethod/WrapMethod;", "extras:wrap-method");
    }

    private static final Set<String> INJECTORS = Set.of("inject", "redirect", "modify-arg", "modify-args",
            "modify-variable", "modify-constant", "extras:modify-return-value", "extras:modify-expression-value",
            "extras:modify-receiver", "extras:wrap-operation", "extras:wrap-with-condition", "extras:wrap-method");
    private static final Set<String> MODIFIERS = Set.of("modify-arg", "modify-variable", "modify-constant",
            "extras:modify-return-value", "extras:modify-expression-value", "extras:modify-receiver");
    private static final Set<String> REPLACING = Set.of("overwrite", "redirect", "modify-args",
            "extras:wrap-operation", "extras:wrap-method");

    private static final Set<String> REGISTERS = Set.of("net/minecraft/core/Registry.register",
            "net/minecraft/core/Registry.registerForHolder", "net/minecraft/core/Registry.registerReference");
    private static final Set<String> THREADS = Set.of("java/lang/Thread.start", "java/lang/Thread.startVirtualThread",
            "java/lang/Thread$Builder.start", "java/util/Timer.<init>",
            "java/util/concurrent/Executors.newSingleThreadExecutor",
            "java/util/concurrent/Executors.newFixedThreadPool", "java/util/concurrent/Executors.newCachedThreadPool",
            "java/util/concurrent/Executors.newScheduledThreadPool",
            "java/util/concurrent/Executors.newSingleThreadScheduledExecutor",
            "java/util/concurrent/Executors.newVirtualThreadPerTaskExecutor",
            "java/util/concurrent/CompletableFuture.runAsync", "java/util/concurrent/CompletableFuture.supplyAsync");
    private static final Set<String> NETWORK_CLASSES =
            Set.of("net/fabricmc/fabric/api/networking/v1/PayloadTypeRegistry");
    private static final Set<String> NETWORK_RECEIVERS = Set.of(
            "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking.registerGlobalReceiver",
            "net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking.registerGlobalReceiver");
    private static final Set<String> WORLDGEN_CLASSES = Set.of("net/fabricmc/fabric/api/biome/v1/BiomeModifications");
    private static final String[] DATA_WORLDGEN =
            {"/worldgen/", "/structure/", "/structures/", "/dimension/", "/dimension_type/"};
    private static final String[] ASSET_DIRS = {"/textures/", "/models/", "/sounds/", "/shaders/"};

    private SoftOffScan() {}

    /** The jar's class and why: the mod in it and the jars it carries. */
    public static Reading read(Path jar) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry e : java.util.Collections.list(zip.entries())) {
                if (e.isDirectory()) {
                    entries.put(e.getName(), null);
                    continue;
                }
                try (InputStream in = zip.getInputStream(e)) {
                    entries.put(e.getName(), in.readAllBytes());
                }
            }
        }
        return read(entries);
    }

    static Reading read(Map<String, byte[]> entries) {
        Refs refs = new Refs();
        List<String> classes = new ArrayList<>();
        List<String> data = new ArrayList<>();
        boolean assets = false;
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            String name = e.getKey();
            if (name.endsWith(".class") && !name.startsWith("META-INF/")) {
                classes.add(name);
                refs.add(e.getValue());
            }
            if (name.startsWith("data/") && !name.endsWith("/")) {
                data.add(name);
            }
            if (name.startsWith("assets/") && containsAny(name, ASSET_DIRS)) {
                assets = true;
            }
            // The jars it carries: a library's own code counts as the mod's.
            if (name.startsWith("META-INF/jars/") && name.endsWith(".jar") && e.getValue() != null) {
                for (Map.Entry<String, byte[]> inner : unzip(e.getValue()).entrySet()) {
                    if (inner.getKey().endsWith(".class")) {
                        refs.add(inner.getValue());
                    }
                }
            }
        }
        Set<String> replaced = new TreeSet<>();
        Set<String> unguarded = new TreeSet<>();
        for (Mixin x : mixins(entries, new HashSet<>())) {
            if (x.side.equals("server") || x.targets.stream().noneMatch(SoftOffScan::isGame)) {
                continue;
            }
            for (String[] m : x.members) {
                String kind = m[0];
                String shown = "@" + (kind.startsWith("extras:") ? kind.substring("extras:".length()) : kind);
                if (REPLACING.contains(kind)) {
                    replaced.add(shown);
                } else if (INJECTORS.contains(kind) && !handlerStops(kind, m[1])) {
                    unguarded.add(shown);
                }
            }
        }
        List<String> restart = new ArrayList<>();
        List<String> partial = new ArrayList<>();
        if (!replaced.isEmpty()) {
            restart.add(String.join(" and ", replaced) + " into the game's classes");
        }
        if (!unguarded.isEmpty()) {
            restart.add(String.join(" and ", unguarded) + " Kiln doesn't guard");
        }
        if (refs.anyCall(REGISTERS)) {
            partial.add("registers blocks, items or entities (they stay, inert)");
        }
        if (refs.anyCall(THREADS)) {
            partial.add("runs threads of its own");
        }
        if (refs.anyCall(NETWORK_RECEIVERS) || refs.anyClass(NETWORK_CLASSES)) {
            partial.add("has network channels");
        }
        if (refs.anyClass(WORLDGEN_CLASSES) || data.stream().anyMatch(n -> containsAny(n, DATA_WORLDGEN))) {
            partial.add("generates in the world");
        } else if (!data.isEmpty()) {
            partial.add("data pack files (recipes, loot, tags) stay");
        }
        if (classes.isEmpty() && assets) {
            partial.add("resource pack files stay");
        }
        if (!restart.isEmpty()) {
            List<String> why = new ArrayList<>(restart);
            why.addAll(partial);
            return new Reading("restart", why);
        }
        if (!partial.isEmpty()) {
            return new Reading("partial", partial);
        }
        if (classes.isEmpty()) {
            return new Reading("safe", List.of("no code"));
        }
        return new Reading("safe", List.of("only events, HUD, keys, commands and guarded mixin handlers"));
    }

    private static boolean isGame(String target) {
        for (String g : GAME) {
            if (target.startsWith(g)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String s, String[] parts) {
        for (String p : parts) {
            if (s.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /** Whether Kiln's guard is on this handler, so the soft switch stops it ({@link MixinGuards}' rule). */
    static boolean handlerStops(String kind, String desc) {
        if (kind.equals("inject")) {
            return desc.endsWith(")V");
        }
        Type[] args;
        Type ret;
        try {
            args = Type.getArgumentTypes(desc);
            ret = Type.getReturnType(desc);
        } catch (RuntimeException e) {
            return false;
        }
        if (MODIFIERS.contains(kind)) {
            return args.length > 0 && args[0].equals(ret);
        }
        if (kind.equals("extras:wrap-with-condition")) {
            return ret.equals(Type.BOOLEAN_TYPE);
        }
        return false;
    }

    // ---- The mods' mixin classes (tools/kiln-scan.py's read_jar_mixins) ----

    /** A mixin class: its config's list ({@code mixins}, {@code client}, {@code server}), targets, members. */
    private record Mixin(String side, List<String> targets, List<String[]> members) {}

    private static List<Mixin> mixins(Map<String, byte[]> entries, Set<String> seen) {
        List<Mixin> found = new ArrayList<>();
        JsonObject manifest = json(entries.get("fabric.mod.json"));
        if (manifest == null) {
            return found;
        }
        String id = text(manifest, "id", "?");
        if (!seen.add(id)) {
            return found;
        }
        // Kiln stands in for the Fabric API itself: what its modules patch, Kiln does its own way.
        if (!id.startsWith("fabric-") && !id.equals("fabricloader")) {
            for (JsonElement entry : list(manifest.get("mixins"))) {
                String config = entry.isJsonPrimitive() ? entry.getAsString()
                        : entry.isJsonObject() ? text(entry.getAsJsonObject(), "config", null) : null;
                JsonObject cfg = config == null ? null : json(entries.get(config));
                if (cfg == null) {
                    continue;
                }
                String pkg = text(cfg, "package", "");
                for (String side : new String[] {"mixins", "client", "server"}) {
                    for (JsonElement name : list(cfg.get(side))) {
                        if (!name.isJsonPrimitive()) {
                            continue;
                        }
                        String path = (pkg + "." + name.getAsString()).replace('.', '/') + ".class";
                        byte[] bytes = entries.get(path);
                        if (bytes == null) {
                            continue;
                        }
                        try {
                            found.add(mixin(bytes, side));
                        } catch (RuntimeException unreadable) {
                            // As the census: an unreadable mixin class says nothing.
                        }
                    }
                }
            }
        }
        for (JsonElement nested : list(manifest.get("jars"))) {
            String file = nested.isJsonObject() ? text(nested.getAsJsonObject(), "file", null) : null;
            byte[] bytes = file == null ? null : entries.get(file);
            if (bytes != null) {
                found.addAll(mixins(unzip(bytes), seen));
            }
        }
        return found;
    }

    private static Mixin mixin(byte[] bytes, String side) {
        List<String> targets = new ArrayList<>();
        List<String[]> members = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!descriptor.equals(MIXIN + "Mixin;")) {
                    return null;
                }
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String unused, Object value) {
                                if (name.equals("value") && value instanceof Type t) {
                                    targets.add(t.getInternalName());
                                } else if (name.equals("targets") && value instanceof String s) {
                                    targets.add(s.replace('.', '/'));
                                }
                            }
                        };
                    }

                    @Override
                    public void visit(String name, Object value) {
                        // A single value given without braces.
                        if (name.equals("value") && value instanceof Type t) {
                            targets.add(t.getInternalName());
                        } else if (name.equals("targets") && value instanceof String s) {
                            targets.add(s.replace('.', '/'));
                        }
                    }
                };
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String sig, Object value) {
                String[] member = {null, descriptor};
                return new FieldVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String type, boolean visible) {
                        if (member[0] == null && KINDS.containsKey(type)) {
                            member[0] = KINDS.get(type);
                            members.add(member);
                        }
                        return null;
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String sig, String[] ex) {
                String[] member = {null, descriptor};
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String type, boolean visible) {
                        if (member[0] == null && KINDS.containsKey(type)) {
                            member[0] = KINDS.get(type);
                            members.add(member);
                        }
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new Mixin(side, targets, members);
    }

    // ---- What the code refers to (tools/kiln-scan.py's parse_class: the constant pool) ----

    /** The members ({@code owner.name}) and classes the classes read so far refer to. */
    private static final class Refs {
        final Set<String> calls = new HashSet<>();
        final Set<String> classes = new HashSet<>();

        void add(byte[] bytes) {
            if (bytes == null) {
                return;
            }
            try {
                ClassReader r = new ClassReader(bytes);
                char[] buf = new char[r.getMaxStringLength()];
                for (int i = 1; i < r.getItemCount(); i++) {
                    int at = r.getItem(i);
                    if (at == 0) {
                        continue;
                    }
                    int tag = r.readByte(at - 1);
                    if (tag == 7) {
                        String name = r.readUTF8(at, buf);
                        if (name.startsWith("[")) {
                            name = Type.getType(name).getElementType().getInternalName();
                        }
                        classes.add(name);
                    } else if (tag == 9 || tag == 10 || tag == 11) {
                        String owner = r.readClass(at, buf);
                        int nat = r.getItem(r.readUnsignedShort(at + 2));
                        String name = r.readUTF8(nat, buf);
                        String desc = r.readUTF8(nat + 2, buf);
                        calls.add(owner + "." + name);
                        classes.addAll(typesIn(desc));
                    }
                }
            } catch (RuntimeException unreadable) {
                // As the census: a class it can't read says nothing.
            }
        }

        boolean anyCall(Set<String> wanted) {
            for (String w : wanted) {
                if (calls.contains(w)) {
                    return true;
                }
            }
            return false;
        }

        boolean anyClass(Set<String> wanted) {
            for (String w : wanted) {
                if (classes.contains(w)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static List<String> typesIn(String desc) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while ((i = desc.indexOf('L', i)) >= 0) {
            int end = desc.indexOf(';', i);
            if (end < 0) {
                break;
            }
            out.add(desc.substring(i + 1, end));
            i = end + 1;
        }
        return out;
    }

    // ---- Small helpers ----

    private static Map<String, byte[]> unzip(byte[] jar) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(jar))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                out.put(e.getName(), e.isDirectory() ? null : in.readAllBytes());
            }
        } catch (IOException e) {
            // A jar inside that isn't one: what was read so far stands.
        }
        return out;
    }

    private static JsonObject json(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            JsonElement e = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<JsonElement> list(JsonElement e) {
        List<JsonElement> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) {
            return out;
        }
        if (e instanceof JsonArray a) {
            a.forEach(out::add);
        } else {
            out.add(e);
        }
        return out;
    }

    private static String text(JsonObject o, String key, String otherwise) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : otherwise;
    }
}
