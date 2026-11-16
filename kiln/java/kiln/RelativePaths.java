package kiln;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
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
 */
public final class RelativePaths {
    private RelativePaths() {}

    /** The path, absolute against Kiln's folder when it is relative; null and "" as they are. */
    public static String absolute(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        File file = new File(path);
        return file.isAbsolute() ? path : file.getAbsolutePath();
    }

    /** The file, absolute against Kiln's folder when it is relative. */
    public static File absolute(File file) {
        return file == null || file.isAbsolute() || file.getPath().isEmpty() ? file : file.getAbsoluteFile();
    }

    /** {@link Path#toFile}, absolute: a relative NIO path's file would be opened against the game's folder. */
    public static File toFile(Path path) {
        return absolute(path.toFile());
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
        boolean any = text.contains("toFile");
        for (String owner : OWNERS) {
            any = any || text.contains(owner);
        }
        if (!any) {
            return null;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        boolean[] changed = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String method, String d, boolean itf) {
                        if (op == Opcodes.INVOKEINTERFACE && owner.equals("java/nio/file/Path")
                                && method.equals("toFile") && d.equals("()Ljava/io/File;")) {
                            changed[0] = true;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "kiln/RelativePaths", "toFile",
                                    "(Ljava/nio/file/Path;)Ljava/io/File;", false);
                            return;
                        }
                        // Scanner's constructor taking text scans that text: only its file's is a path.
                        if (op == Opcodes.INVOKESPECIAL && method.equals("<init>") && OWNERS.contains(owner)
                                && !(owner.equals("java/util/Scanner") && !d.startsWith("(" + FILE))) {
                            changed[0] |= absoluteFirst(this, d);
                        }
                        super.visitMethodInsn(op, owner, method, d, itf);
                    }
                };
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : null;
    }

    /**
     * Before a constructor's call: its first argument made absolute, when it is a path (text or a file) and at most one
     * argument of one stack slot follows it (a boolean, a charset, a mode), which two swaps step over. True when it
     * wrote that. The constructors with more arguments after the path are left as they are.
     */
    private static boolean absoluteFirst(MethodVisitor mv, String desc) {
        String args = desc.substring(1, desc.indexOf(')'));
        String first = args.startsWith(STRING) ? STRING : args.startsWith(FILE) ? FILE : null;
        if (first == null) {
            return false;
        }
        String rest = args.substring(first.length());
        boolean swap;
        if (rest.isEmpty()) {
            swap = false;
        } else if (oneSlot(rest)) {
            swap = true;
        } else {
            return false;
        }
        if (swap) {
            mv.visitInsn(Opcodes.SWAP);
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "kiln/RelativePaths", "absolute", "(" + first + ")" + first, false);
        if (swap) {
            mv.visitInsn(Opcodes.SWAP);
        }
        return true;
    }

    /** Whether a descriptor's text is exactly one argument of one stack slot (not a long or a double). */
    private static boolean oneSlot(String rest) {
        char c = rest.charAt(0);
        if ("ZBCSIF".indexOf(c) >= 0) {
            return rest.length() == 1;
        }
        if (c == 'L') {
            return rest.indexOf(';') == rest.length() - 1;
        }
        if (c == '[') {
            int i = 0;
            while (rest.charAt(i) == '[') {
                i++;
            }
            int end = rest.charAt(i) == 'L' ? rest.indexOf(';', i) + 1 : i + 1;
            return end == rest.length();
        }
        return false;
    }
}
