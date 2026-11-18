package kiln;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A mod's relative paths, in the game's process (D-112, D-151). A separate Java ran in {@code <game folder>/kiln/}, so
 * a mod opening {@code config/x.json} read Kiln's config folder (D-052). In the game's process the folder the OS
 * resolves a relative path against is the game's: {@code -Duser.dir} moves only what Java resolves itself (NIO's
 * paths, {@link File#getAbsolutePath}), not {@code java.io}'s opens, {@code exists} and the rest, which hand the
 * relative path to the OS. Kiln's agent rewrites, in every class it loads that isn't the JDK's or Kiln's own, the
 * {@code java.io} constructors that take a path as text, and {@link Path#toFile}, so the path is made absolute
 * against {@code user.dir} (Kiln's folder) first: a mod reads and writes where it did with Java in a process of its
 * own.
 *
 * <p>D-188 (#216) adds what D-151 left out: constructors with any arguments after the path (they wait in locals of
 * the method's own while the path is made absolute), {@code File.getPath()} and {@code toString()} giving back the
 * text the mod wrote (a file made from {@code "config/x.json"} says so, as in vanilla, while it opens Kiln's), and a
 * process the mod starts without a working folder of its own starts in Kiln's ({@code ProcessBuilder.start},
 * {@code Runtime.exec}).
 */
public final class RelativePaths {
    private RelativePaths() {}

    /** The files made from relative text, with that text: what their {@code getPath()} gives the mod back. */
    private static final Map<File, Given> GIVEN = Collections.synchronizedMap(new WeakHashMap<>());

    /** The text, for that very file: another file with the same path (an absolute one) isn't it. */
    private record Given(java.lang.ref.WeakReference<File> file, String text) {}

    private static String given(File file) {
        Given given = GIVEN.get(file);
        return given != null && given.file().get() == file ? given.text() : null;
    }
    /** The last relative path made absolute on this thread, and what it became: for the {@code File} being made. */
    private static final ThreadLocal<String[]> LAST = new ThreadLocal<>();

    /** The path, absolute against Kiln's folder when it is relative; null and "" as they are. */
    public static String absolute(String path) {
        if (path == null || path.isEmpty()) {
            LAST.remove();
            return path;
        }
        File file = new File(path);
        if (file.isAbsolute()) {
            LAST.remove();
            return path;
        }
        String absolute = file.getAbsolutePath();
        LAST.set(new String[] {file.getPath(), absolute});
        return absolute;
    }

    /** The file, absolute against Kiln's folder when it is relative. */
    public static File absolute(File file) {
        if (file == null || file.getPath().isEmpty()) {
            LAST.remove();
            return file;
        }
        if (file.isAbsolute()) {
            String given = given(file);
            if (given != null) {
                // A file made from relative text, as a parent: a child's text starts with that text too.
                LAST.set(new String[] {given, file.getPath()});
            } else {
                LAST.remove();
            }
            return file;
        }
        LAST.set(new String[] {file.getPath(), file.getAbsolutePath()});
        return file.getAbsoluteFile();
    }

    /** {@link Path#toFile}, absolute: a relative NIO path's file would be opened against the game's folder. */
    public static File toFile(Path path) {
        return absolute(path.toFile());
    }

    /** After {@code new File(...)} whose path was made absolute: the file remembers the text the mod gave. */
    public static void made(File file) {
        String[] last = LAST.get();
        LAST.remove();
        if (file == null || last == null) {
            return;
        }
        String path = file.getPath();
        if (path.startsWith(last[1])) {
            GIVEN.put(file, new Given(new java.lang.ref.WeakReference<>(file), last[0] + path.substring(last[1].length())));
        }
    }

    /** {@link File#getPath}: the text the mod made the file from, when it was relative (vanilla's answer). */
    public static String getPath(File file) {
        String given = given(file);
        return given != null ? given : file.getPath();
    }

    /** {@link File#toString}, which is its {@code getPath()}. */
    public static String toString(File file) {
        return getPath(file);
    }

    /** {@link ProcessBuilder#start}: in Kiln's folder when the mod set no folder, as in a Java of its own. */
    public static Process start(ProcessBuilder builder) throws java.io.IOException {
        if (builder.directory() == null) {
            builder.directory(kilnFolder());
        }
        return builder.start();
    }

    public static Process exec(Runtime runtime, String command) throws java.io.IOException {
        return runtime.exec(command, null, kilnFolder());
    }

    public static Process exec(Runtime runtime, String[] command) throws java.io.IOException {
        return runtime.exec(command, null, kilnFolder());
    }

    public static Process exec(Runtime runtime, String command, String[] env) throws java.io.IOException {
        return runtime.exec(command, env, kilnFolder());
    }

    public static Process exec(Runtime runtime, String[] command, String[] env) throws java.io.IOException {
        return runtime.exec(command, env, kilnFolder());
    }

    public static Process exec(Runtime runtime, String command, String[] env, File dir) throws java.io.IOException {
        return runtime.exec(command, env, dir == null ? kilnFolder() : absolute(dir));
    }

    public static Process exec(Runtime runtime, String[] command, String[] env, File dir) throws java.io.IOException {
        return runtime.exec(command, env, dir == null ? kilnFolder() : absolute(dir));
    }

    private static File kilnFolder() {
        return new File(System.getProperty("user.dir"));
    }

    private static final String STRING = "Ljava/lang/String;";
    private static final String FILE = "Ljava/io/File;";

    /** The classes whose constructors take a path as their first argument (text, or a file). */
    private static final Set<String> OWNERS = Set.of("java/io/File", "java/io/FileInputStream",
            "java/io/FileOutputStream", "java/io/FileReader", "java/io/FileWriter", "java/io/RandomAccessFile",
            "java/io/PrintWriter", "java/io/PrintStream", "java/util/zip/ZipFile", "java/util/jar/JarFile",
            "java/util/Scanner");

    /** The class with its relative paths made absolute, or null when it has none of those calls. */
    static byte[] rewrite(byte[] bytes) {
        // Most classes name none: a look at the constant pool's text first.
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        boolean any = text.contains("toFile") || text.contains("java/lang/ProcessBuilder")
                || text.contains("java/lang/Runtime");
        for (String owner : OWNERS) {
            any = any || text.contains(owner);
        }
        if (!any) {
            return null;
        }
        ClassReader reader = new ClassReader(bytes);
        // Each method's locals, in order: an argument waiting while the path is made absolute goes after them.
        List<Integer> locals = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                int at = locals.size();
                locals.add(0);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMaxs(int maxStack, int maxLocals) {
                        locals.set(at, maxLocals);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        boolean[] changed = {false};
        int[] method = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                int free = locals.get(method[0]++);
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
                    /** {@code new File} not yet constructed: its constructor's call leaves the file for {@link #made}. */
                    private int newFiles;

                    @Override
                    public void visitTypeInsn(int op, String type) {
                        if (op == Opcodes.NEW && type.equals("java/io/File")) {
                            newFiles++;
                        }
                        super.visitTypeInsn(op, type);
                    }

                    @Override
                    public void visitMethodInsn(int op, String owner, String method, String d, boolean itf) {
                        if (op == Opcodes.INVOKEINTERFACE && owner.equals("java/nio/file/Path")
                                && method.equals("toFile") && d.equals("()Ljava/io/File;")) {
                            changed[0] = true;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "kiln/RelativePaths", "toFile",
                                    "(Ljava/nio/file/Path;)Ljava/io/File;", false);
                            return;
                        }
                        if (op == Opcodes.INVOKEVIRTUAL && owner.equals("java/io/File")
                                && (method.equals("getPath") || method.equals("toString"))
                                && d.equals("()Ljava/lang/String;")) {
                            changed[0] = true;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "kiln/RelativePaths", method,
                                    "(Ljava/io/File;)Ljava/lang/String;", false);
                            return;
                        }
                        if (op == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/ProcessBuilder")
                                && method.equals("start") && d.equals("()Ljava/lang/Process;")) {
                            changed[0] = true;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "kiln/RelativePaths", "start",
                                    "(Ljava/lang/ProcessBuilder;)Ljava/lang/Process;", false);
                            return;
                        }
                        if (op == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Runtime") && method.equals("exec")) {
                            changed[0] = true;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "kiln/RelativePaths", "exec",
                                    "(Ljava/lang/Runtime;" + d.substring(1), false);
                            return;
                        }
                        // Scanner's constructor taking text scans that text: only its file's is a path.
                        boolean file = false;
                        if (op == Opcodes.INVOKESPECIAL && method.equals("<init>") && OWNERS.contains(owner)
                                && !(owner.equals("java/util/Scanner") && !d.startsWith("(" + FILE))) {
                            boolean made = absoluteFirst(this, d, free);
                            changed[0] |= made;
                            if (owner.equals("java/io/File") && newFiles > 0) {
                                newFiles--;
                                file = made;
                            }
                        }
                        super.visitMethodInsn(op, owner, method, d, itf);
                        if (file) {
                            super.visitInsn(Opcodes.DUP);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "kiln/RelativePaths", "made",
                                    "(Ljava/io/File;)V", false);
                        }
                    }
                };
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : null;
    }

    /**
     * Before a constructor's call: its first argument made absolute, when it is a path (text or a file). One argument
     * of one stack slot after it (a boolean, a charset, a mode) is stepped over by two swaps; more wait in locals from
     * {@code free} on (D-188). True when it wrote that.
     */
    private static boolean absoluteFirst(MethodVisitor mv, String desc, int free) {
        String args = desc.substring(1, desc.indexOf(')'));
        String first = args.startsWith(STRING) ? STRING : args.startsWith(FILE) ? FILE : null;
        if (first == null) {
            return false;
        }
        List<String> rest = arguments(args.substring(first.length()));
        if (rest.size() == 1 && slots(rest.get(0)) == 1) {
            mv.visitInsn(Opcodes.SWAP);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "kiln/RelativePaths", "absolute", "(" + first + ")" + first, false);
            mv.visitInsn(Opcodes.SWAP);
            return true;
        }
        int[] at = new int[rest.size()];
        int next = free;
        for (int i = 0; i < rest.size(); i++) {
            at[i] = next;
            next += slots(rest.get(i));
        }
        for (int i = rest.size() - 1; i >= 0; i--) {
            mv.visitVarInsn(opcode(rest.get(i), Opcodes.ISTORE), at[i]);
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "kiln/RelativePaths", "absolute", "(" + first + ")" + first, false);
        for (int i = 0; i < rest.size(); i++) {
            mv.visitVarInsn(opcode(rest.get(i), Opcodes.ILOAD), at[i]);
        }
        return true;
    }

    /** A descriptor's arguments, one by one. */
    static List<String> arguments(String args) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < args.length()) {
            int start = i;
            while (args.charAt(i) == '[') {
                i++;
            }
            i = args.charAt(i) == 'L' ? args.indexOf(';', i) + 1 : i + 1;
            out.add(args.substring(start, i));
        }
        return out;
    }

    private static int slots(String arg) {
        return arg.equals("J") || arg.equals("D") ? 2 : 1;
    }

    /** The load or store for an argument: {@code ILOAD}/{@code ISTORE} shifted to its kind. */
    private static int opcode(String arg, int intOp) {
        return switch (arg.charAt(0)) {
            case 'J' -> intOp + 1;
            case 'F' -> intOp + 2;
            case 'D' -> intOp + 3;
            case 'L', '[' -> intOp + 4;
            default -> intOp;
        };
    }
}
