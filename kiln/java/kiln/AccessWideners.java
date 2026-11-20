package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * The mods' access wideners (Fabric's {@code accessWidener} file in fabric.mod.json: {@code accessWidener v1/v2} or
 * {@code classTweaker v1}), applied as Fabric Loader applies them: each class a mod's file names loads with the access
 * it asks for (accessible, extendable, mutable), Kiln's stand-ins too. A library such as Forge Config API Port reads
 * and writes members the game keeps private, and fails with an {@code IllegalAccessError} without it (D-228).
 * Interface injection ({@code inject-interface}) is only a compile-time help in Fabric's own files and is left out.
 */
final class AccessWideners {
    private static final int ACCESSIBLE = 1;
    private static final int EXTENDABLE = 2;
    private static final int MUTABLE = 4;

    /** By class (internal name): its own access, and its members' as "name desc". */
    private static final Map<String, Target> TARGETS = new HashMap<>();

    private static final class Target {
        int access;
        final Map<String, Integer> methods = new HashMap<>();
        final Map<String, Integer> fields = new HashMap<>();
    }

    private AccessWideners() {}

    /** Whether any mod widens anything. */
    static boolean any() {
        return !TARGETS.isEmpty();
    }

    /** Every mod's access widener on the class path, read once before the first class loads; what it said. */
    static List<String> load(ClassLoader loader) throws IOException {
        List<String> said = new ArrayList<>();
        Set<String> mods = new LinkedHashSet<>();
        Set<String> seen = new java.util.HashSet<>();
        int entries = 0;
        Enumeration<URL> manifests = loader.getResources("fabric.mod.json");
        while (manifests.hasMoreElements()) {
            URL manifest = manifests.nextElement();
            JsonObject json;
            try (Reader r = new InputStreamReader(manifest.openStream(), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(r).getAsJsonObject();
            } catch (RuntimeException e) {
                continue;
            }
            JsonElement id = json.get("id");
            JsonElement file = json.get("accessWidener");
            if (id == null || !id.isJsonPrimitive() || file == null || !file.isJsonPrimitive()
                    || !seen.add(id.getAsString())) {
                continue;
            }
            String mod = id.getAsString();
            // The widener sits in the same jar as the fabric.mod.json that names it.
            String base = manifest.toString();
            URL url = new URL(base.substring(0, base.length() - "fabric.mod.json".length()) + file.getAsString());
            try (InputStream in = url.openStream()) {
                int n = read(mod, in, said);
                if (n > 0) {
                    entries += n;
                    mods.add(mod);
                }
            } catch (IOException e) {
                said.add("Kiln: " + mod + "'s access widener " + file.getAsString() + " can't be read: " + e);
            }
        }
        if (entries > 0) {
            said.add(0, "Kiln widens access as the mods' access wideners ask: " + entries + " entries of "
                    + String.join(", ", mods));
        }
        return said;
    }

    /** One widener file's entries into TARGETS; how many. */
    static int read(String mod, InputStream in, List<String> said) throws IOException {
        BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String header = null;
        int n = 0;
        Set<String> skipped = new LinkedHashSet<>();
        for (String line; (line = lines.readLine()) != null; ) {
            int hash = line.indexOf('#');
            String text = (hash >= 0 ? line.substring(0, hash) : line).strip();
            if (text.isEmpty()) {
                continue;
            }
            String[] w = text.split("\\s+");
            if (header == null) {
                header = text;
                if (w.length < 3 || !(w[0].equals("accessWidener") || w[0].equals("classTweaker"))) {
                    said.add("Kiln: " + mod + "'s access widener starts with '" + text + "', not a widener's header:"
                            + " left out");
                    return 0;
                }
                // The game runs with Mojang's names since 26.1, Fabric's "official" and "named" alike.
                if (!w[2].equals("named") && !w[2].equals("official")) {
                    said.add("Kiln: " + mod + "'s access widener is in the " + w[2] + " names, which this game"
                            + " doesn't have: left out");
                    return 0;
                }
                continue;
            }
            String kind = w[0].startsWith("transitive-") ? w[0].substring("transitive-".length()) : w[0];
            int access = switch (kind) {
                case "accessible" -> ACCESSIBLE;
                case "extendable" -> EXTENDABLE;
                case "mutable" -> MUTABLE;
                default -> 0;
            };
            if (access == 0 || w.length < 3) {
                skipped.add(w[0]);
                continue;
            }
            Target t = TARGETS.computeIfAbsent(w[2], k -> new Target());
            switch (w[1]) {
                case "class" -> t.access |= access;
                case "method" -> {
                    if (w.length < 5) {
                        continue;
                    }
                    t.methods.merge(w[3] + " " + w[4], access, (a, b) -> a | b);
                    // Fabric's way: a member made accessible makes its class accessible, extendable extendable.
                    t.access |= access == EXTENDABLE ? EXTENDABLE : ACCESSIBLE;
                }
                case "field" -> {
                    if (w.length < 5) {
                        continue;
                    }
                    t.fields.merge(w[3] + " " + w[4], access, (a, b) -> a | b);
                    if (access == ACCESSIBLE) {
                        t.access |= ACCESSIBLE;
                    }
                }
                default -> {
                    skipped.add(w[0] + " " + w[1]);
                    continue;
                }
            }
            n++;
        }
        if (!skipped.isEmpty()) {
            said.add("Kiln: " + mod + "'s access widener asks for what Kiln leaves out: " + String.join(", ", skipped));
        }
        return n;
    }

    /** The class with the access the mods' wideners ask for, or null when none names it. */
    static byte[] apply(String name, byte[] bytes) {
        Target t = TARGETS.get(name);
        if (t == null) {
            return null;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new Widen(writer, name, t, privatesMadePublic(reader, t)), 0);
        return writer.toByteArray();
    }

    private static int publicOf(int access) {
        return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
    }

    /** A class's own access as asked (also its entry in the InnerClasses of its own file). */
    private static int classAccess(int access, int asked) {
        if ((asked & (ACCESSIBLE | EXTENDABLE)) != 0) {
            access = publicOf(access);
        }
        if ((asked & EXTENDABLE) != 0) {
            access &= ~Opcodes.ACC_FINAL;
        }
        return access;
    }

    private static final class Widen extends ClassVisitor {
        private final String name;
        private final Target target;
        /**
         * Private instance methods made public, as Fabric does it: their invokespecial calls in the class become
         * invokevirtual. Found before the methods are visited, so a call above its method is rewritten too.
         */
        private final Set<String> nowVirtual;

        Widen(ClassVisitor next, String name, Target target, Set<String> nowVirtual) {
            super(Opcodes.ASM9, next);
            this.name = name;
            this.target = target;
            this.nowVirtual = nowVirtual;
        }

        @Override
        public void visit(int version, int access, String n, String signature, String superName, String[] ifaces) {
            super.visit(version, classAccess(access, target.access), n, signature, superName, ifaces);
        }

        @Override
        public void visitInnerClass(String n, String outer, String inner, int access) {
            Target t = n.equals(name) ? target : TARGETS.get(n);
            super.visitInnerClass(n, outer, inner, t == null ? access : classAccess(access, t.access));
        }

        @Override
        public FieldVisitor visitField(int access, String n, String desc, String signature, Object value) {
            Integer asked = target.fields.get(n + " " + desc);
            if (asked != null) {
                if ((asked & ACCESSIBLE) != 0) {
                    access = publicOf(access);
                }
                if ((asked & MUTABLE) != 0) {
                    access &= ~Opcodes.ACC_FINAL;
                }
            }
            return super.visitField(access, n, desc, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String n, String desc, String signature, String[] exceptions) {
            Integer asked = target.methods.get(n + " " + desc);
            if (asked != null) {
                boolean wasPrivate = (access & Opcodes.ACC_PRIVATE) != 0;
                boolean special = n.startsWith("<") || (access & Opcodes.ACC_STATIC) != 0;
                if ((asked & EXTENDABLE) != 0) {
                    if ((access & Opcodes.ACC_PUBLIC) == 0) {
                        access = (access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PROTECTED;
                    }
                    access &= ~Opcodes.ACC_FINAL;
                }
                if ((asked & ACCESSIBLE) != 0) {
                    access = publicOf(access);
                    // As Fabric: a private method made public stays out of overriding (final, unless extendable).
                    if (wasPrivate && !special && (asked & EXTENDABLE) == 0) {
                        access |= Opcodes.ACC_FINAL;
                    }
                }
            }
            MethodVisitor mv = super.visitMethod(access, n, desc, signature, exceptions);
            if (nowVirtual.isEmpty()) {
                return mv;
            }
            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitMethodInsn(int op, String owner, String m, String d, boolean itf) {
                    if (op == Opcodes.INVOKESPECIAL && owner.equals(name) && !itf && nowVirtual.contains(m + d)) {
                        op = Opcodes.INVOKEVIRTUAL;
                    }
                    super.visitMethodInsn(op, owner, m, d, itf);
                }
            };
        }
    }

    /** The class's private instance methods (not constructors) a widener makes accessible, as name + desc. */
    private static Set<String> privatesMadePublic(ClassReader reader, Target t) {
        Set<String> found = new java.util.HashSet<>();
        if (t.methods.isEmpty()) {
            return found;
        }
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String n, String desc, String sig, String[] exceptions) {
                Integer asked = t.methods.get(n + " " + desc);
                if (asked != null && (asked & ACCESSIBLE) != 0 && (access & Opcodes.ACC_PRIVATE) != 0
                        && (access & Opcodes.ACC_STATIC) == 0 && !n.equals("<init>")) {
                    found.add(n + desc);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE);
        return found;
    }
}
