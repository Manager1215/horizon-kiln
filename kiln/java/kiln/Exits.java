package kiln;

import java.nio.charset.StandardCharsets;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * In the game's process (D-112), a mod's {@code System.exit}, {@code Runtime.exit} and {@code Runtime.halt} would end
 * the game. Kiln's agent rewrites those calls in every class it loads that isn't the JDK's or Kiln's own ({@link
 * #rewrite}) into these, which throw instead: the call into Java that reached it fails, as when a mod's code throws,
 * and the game goes on. Said once a place in Kiln's log, with its trace in java.log.
 */
public final class Exits {
    private Exits() {}

    /** What a mod's exit throws. */
    public static final class Stopped extends RuntimeException {
        Stopped(String what) {
            super(what);
        }
    }

    public static void exit(int status) {
        throw stopped("System.exit(" + status + ")");
    }

    public static void exit(Runtime runtime, int status) {
        throw stopped("Runtime.exit(" + status + ")");
    }

    public static void halt(Runtime runtime, int status) {
        throw stopped("Runtime.halt(" + status + ")");
    }

    private static Stopped stopped(String call) {
        Stopped stopped = new Stopped("a mod called " + call + ", which would end the game: Kiln stopped it");
        // From the mod's call on: Kiln's two frames (this, and the exit the mod's call became) aren't where it was.
        StackTraceElement[] trace = stopped.getStackTrace();
        if (trace.length > 2) {
            trace = java.util.Arrays.copyOfRange(trace, 2, trace.length);
            stopped.setStackTrace(trace);
        }
        String where = trace.length > 0 ? trace[0].getClassName() + "." + trace[0].getMethodName() : "?";
        Mixins.sayOnce("Kiln: " + where + " called " + call + "; with Java in the game's process that would end the "
                + "game, so the call it was in stops instead");
        stopped.printStackTrace();
        return stopped;
    }

    /** The class with its exits rewritten, or null when it has none. */
    static byte[] rewrite(byte[] bytes) {
        // Most classes name neither: a look at the constant pool's text first.
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        boolean system = text.contains("java/lang/System") && text.contains("exit");
        boolean runtime = text.contains("java/lang/Runtime") && (text.contains("exit") || text.contains("halt"));
        if (!system && !runtime) {
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
                        String self = "kiln/Exits";
                        if (op == Opcodes.INVOKESTATIC && owner.equals("java/lang/System") && method.equals("exit")
                                && d.equals("(I)V")) {
                            changed[0] = true;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, self, "exit", "(I)V", false);
                        } else if (op == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Runtime")
                                && (method.equals("exit") || method.equals("halt")) && d.equals("(I)V")) {
                            changed[0] = true;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, self, method, "(Ljava/lang/Runtime;I)V", false);
                        } else {
                            super.visitMethodInsn(op, owner, method, d, itf);
                        }
                    }
                };
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : null;
    }
}
