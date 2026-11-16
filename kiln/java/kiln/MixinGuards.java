package kiln;

import java.util.List;
import java.util.function.Function;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * A mod's mixin handler that throws doesn't stop the method it was put in (D-080, kept with Mixin's library in
 * D-089). In the game a throw there stops the game; under Kiln it is mostly a member a stand-in hasn't got yet, and one
 * mod's handler in {@code Minecraft.tick} would stop every mod's tick. So after Mixin applied a class's mixins, each
 * handler whose throw Kiln can stand in for gets its body in a guard that catches {@link RuntimeException} and
 * {@link LinkageError}, says it once ({@link Mixins#threw}) and gives back what the method would have had without it:
 *
 * <ul>
 *   <li>{@code @Inject}: nothing (as if the handler did nothing, the call not cancelled unless it was already);
 *   <li>the handlers that modify a value ({@code @ModifyArg}, {@code @ModifyVariable}, {@code @ModifyConstant}, and
 *       MixinExtras' {@code @ModifyReturnValue}, {@code @ModifyExpressionValue}, {@code @ModifyReceiver}): the value
 *       they were given, their first argument, when it is of the type they return;
 *   <li>MixinExtras' {@code @WrapWithCondition}: true, the call made as without the mixin.
 * </ul>
 *
 * Those that replace or wrap a call ({@code @Redirect}, {@code @WrapOperation}, {@code @WrapMethod},
 * {@code @ModifyArgs}, {@code @Overwrite}) aren't guarded: Kiln can't tell what the code would have had, and a throw
 * goes up as in the game.
 *
 * <p>The same handlers are the ones a soft switch stops (D-113): each starts by asking {@link SoftSwitch#off(int)}
 * whether its mod is turned off now, and gives back what the guard does if it is.
 *
 * <p>And D-112's crash policy: each runs in the breadcrumb ({@link Breadcrumb}: its mod and name from its start to each
 * return), and one that runs Java out of its heap or stack has its mod turned off ({@link Mixins#exhausted}) and gives
 * back what the guard does.
 */
final class MixinGuards {
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    /** Mixin names a merged handler {@code <prefix>$<id>$<mod>$<name>}; the prefix says its kind (HandlerPrefix). */
    private static final java.util.regex.Pattern MERGED_NAME =
            java.util.regex.Pattern.compile("^([A-Za-z]+)\\$[a-z]{3}\\d{3}\\$(.*)$");
    /** {@code @Inject}'s handlers. */
    private static final String INJECT = "handler";
    /** The handlers that modify a value and return it. */
    private static final List<String> MODIFIERS = List.of("modify", "localvar", "constant", "modifyReturnValue",
            "modifyExpressionValue", "modifyReceiver");
    /** MixinExtras' {@code @WrapWithCondition}. */
    private static final String CONDITION = "wrapWithCondition";

    private MixinGuards() {}

    /**
     * Guards the handlers Mixin merged into {@code node}; {@code who} names a mixin class's mod. Returns how many it
     * guarded.
     */
    static int guard(ClassNode node, Function<String, String> who) {
        int guarded = 0;
        for (MethodNode m : node.methods) {
            if (m.instructions.size() == 0 || (m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                    || m.name.startsWith("<")) {
                continue;
            }
            AnnotationNode merged = MixinStandIns.annotation(m.visibleAnnotations, m.invisibleAnnotations, MERGED);
            if (merged == null) {
                continue;
            }
            java.util.regex.Matcher named = MERGED_NAME.matcher(m.name);
            if (!named.matches()) {
                continue;
            }
            String kind = named.group(1);
            Type ret = Type.getReturnType(m.desc);
            Type[] args = Type.getArgumentTypes(m.desc);
            boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
            InsnList fallback = fallback(kind, ret, args, isStatic);
            if (fallback == null) {
                continue;
            }
            Object mixin = MixinStandIns.value(merged, "mixin");
            String mixinName = mixin instanceof String s ? s : "?";
            String mod = who.apply(mixinName);
            String where = mod + "'s " + mixinName + "." + handlerName(m.name, mod);
            String known = mod.equals("?") ? "" : mod;
            // The handler's own returns, before the guard adds its own.
            List<AbstractInsnNode> returns = new java.util.ArrayList<>();
            for (AbstractInsnNode insn : m.instructions) {
                if (insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN) {
                    returns.add(insn);
                }
            }
            int depth = freeLocal(m);
            LabelNode start = new LabelNode();
            LabelNode end = new LabelNode();
            LabelNode caught = new LabelNode();
            LabelNode exhausted = new LabelNode();
            // D-112's breadcrumb: the handler's mod and name, from here to each of its returns and a throw it catches.
            InsnList enter = new InsnList();
            enter.add(new LdcInsnNode(known));
            enter.add(new LdcInsnNode(where));
            enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "kiln/Breadcrumb", "enter",
                    "(Ljava/lang/String;Ljava/lang/String;)I", false));
            enter.add(new VarInsnNode(Opcodes.ISTORE, depth));
            enter.add(start);
            m.instructions.insert(enter);
            for (AbstractInsnNode r : returns) {
                m.instructions.insertBefore(r, leave(depth));
            }
            if (!known.isEmpty()) {
                // Its mod turned off now (D-113's soft switch): the handler gives back at once what the guard does.
                LabelNode on = new LabelNode();
                InsnList off = new InsnList();
                off.add(new LdcInsnNode(SoftSwitch.number(mod)));
                off.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "kiln/SoftSwitch", "off", "(I)Z", false));
                off.add(new JumpInsnNode(Opcodes.IFEQ, on));
                off.add(fallback(kind, ret, args, isStatic));
                off.add(on);
                m.instructions.insert(off);
            }
            m.instructions.add(end);
            m.instructions.add(caught);
            // The thrown value is on the stack: Mixins.threw(thrown, where).
            m.instructions.add(leave(depth));
            m.instructions.add(new LdcInsnNode(where));
            m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "kiln/Mixins", "threw",
                    "(Ljava/lang/Throwable;Ljava/lang/String;)V", false));
            m.instructions.add(fallback);
            // A run out of Java's heap or stack in it (D-112): its mod turned off now, the method it is in goes on.
            m.instructions.add(exhausted);
            m.instructions.add(leave(depth));
            m.instructions.add(new LdcInsnNode(known));
            m.instructions.add(new LdcInsnNode(where));
            m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "kiln/Mixins", "exhausted",
                    "(Ljava/lang/Throwable;Ljava/lang/String;Ljava/lang/String;)V", false));
            m.instructions.add(fallback(kind, ret, args, isStatic));
            // After the method's own handlers, so its own catches come first.
            m.tryCatchBlocks.add(new TryCatchBlockNode(start, end, caught, "java/lang/RuntimeException"));
            m.tryCatchBlocks.add(new TryCatchBlockNode(start, end, caught, "java/lang/LinkageError"));
            m.tryCatchBlocks.add(new TryCatchBlockNode(start, end, exhausted, "java/lang/OutOfMemoryError"));
            m.tryCatchBlocks.add(new TryCatchBlockNode(start, end, exhausted, "java/lang/StackOverflowError"));
            guarded++;
        }
        return guarded;
    }

    /** {@code Breadcrumb.leave(depth)}, the depth in local {@code depth}. */
    private static InsnList leave(int depth) {
        InsnList leave = new InsnList();
        leave.add(new VarInsnNode(Opcodes.ILOAD, depth));
        leave.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "kiln/Breadcrumb", "leave", "(I)V", false));
        return leave;
    }

    /** A local variable no instruction of {@code m} uses: past its arguments, its locals and what it declares. */
    static int freeLocal(MethodNode m) {
        int free = (m.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
        for (Type arg : Type.getArgumentTypes(m.desc)) {
            free += arg.getSize();
        }
        free = Math.max(free, m.maxLocals);
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof VarInsnNode v) {
                int size = v.getOpcode() == Opcodes.LLOAD || v.getOpcode() == Opcodes.DLOAD
                        || v.getOpcode() == Opcodes.LSTORE || v.getOpcode() == Opcodes.DSTORE ? 2 : 1;
                free = Math.max(free, v.var + size);
            } else if (insn instanceof org.objectweb.asm.tree.IincInsnNode i) {
                free = Math.max(free, i.var + 1);
            }
        }
        if (m.localVariables != null) {
            for (var local : m.localVariables) {
                free = Math.max(free, local.index + Type.getType(local.desc).getSize());
            }
        }
        m.maxLocals = Math.max(m.maxLocals, free + 1);
        return free;
    }

    /** What a guarded handler of {@code kind} gives back in place of its body; null for one Kiln doesn't guard. */
    private static InsnList fallback(String kind, Type ret, Type[] args, boolean isStatic) {
        InsnList fallback = new InsnList();
        if (kind.equals(INJECT) && ret == Type.VOID_TYPE) {
            fallback.add(new InsnNode(Opcodes.RETURN));
        } else if (MODIFIERS.contains(kind) && args.length > 0 && args[0].equals(ret)) {
            fallback.add(new VarInsnNode(ret.getOpcode(Opcodes.ILOAD), isStatic ? 0 : 1));
            fallback.add(new InsnNode(ret.getOpcode(Opcodes.IRETURN)));
        } else if (kind.equals(CONDITION) && ret == Type.BOOLEAN_TYPE) {
            fallback.add(new InsnNode(Opcodes.ICONST_1));
            fallback.add(new InsnNode(Opcodes.IRETURN));
        } else {
            return null;
        }
        return fallback;
    }

    /**
     * A handler's own name: Mixin renames it {@code <kind>$<id>$<mod>$<name>} as it merges it (handler$zza000$...),
     * and the name may have a {@code $} of its own.
     */
    static String handlerName(String merged, String mod) {
        java.util.regex.Matcher m = MERGED_NAME.matcher(merged);
        if (!m.matches()) {
            return merged;
        }
        String rest = m.group(2);
        return rest.startsWith(mod + "$") ? rest.substring(mod.length() + 1) : rest;
    }
}
