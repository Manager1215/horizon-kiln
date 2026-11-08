package kiln;

import java.util.List;
import java.util.function.Function;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
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
                continue;
            }
            Object mixin = MixinStandIns.value(merged, "mixin");
            String mixinName = mixin instanceof String s ? s : "?";
            String mod = who.apply(mixinName);
            String where = mod + "'s " + mixinName + "." + handlerName(m.name, mod);
            LabelNode start = new LabelNode();
            LabelNode end = new LabelNode();
            LabelNode caught = new LabelNode();
            m.instructions.insert(start);
            m.instructions.add(end);
            m.instructions.add(caught);
            // The thrown value is on the stack: Mixins.threw(thrown, where).
            m.instructions.add(new LdcInsnNode(where));
            m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "kiln/Mixins", "threw",
                    "(Ljava/lang/Throwable;Ljava/lang/String;)V", false));
            m.instructions.add(fallback);
            // After the method's own handlers, so its own catches come first.
            m.tryCatchBlocks.add(new TryCatchBlockNode(start, end, caught, "java/lang/RuntimeException"));
            m.tryCatchBlocks.add(new TryCatchBlockNode(start, end, caught, "java/lang/LinkageError"));
            guarded++;
        }
        return guarded;
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
