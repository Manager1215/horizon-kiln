package kiln;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Kiln's stand-ins made to fit the mods' mixins before Mixin applies them (D-089). A stand-in repeats the game's
 * names and signatures, not all of them: where a mixin names a field or method of the game's class that Kiln's
 * stand-in hasn't got, Mixin would refuse the mixin ({@code @Shadow}, {@code @Accessor} and {@code @Invoker} must find
 * their member). Kiln adds the member first, as its generator would have (D-075): a field with its type's default, a
 * method that gives back its type's default and says once in Kiln's log that it was called (kiln.Missing). Only into
 * Kiln's own classes (from its runtime jar), never into a mod's, where a missing member is the mod's error.
 */
final class MixinStandIns {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/";
    private static final String SHADOW = MIXIN + "Shadow;";
    private static final String ACCESSOR = MIXIN + "gen/Accessor;";
    private static final String INVOKER = MIXIN + "gen/Invoker;";
    private static final String OVERWRITE = MIXIN + "Overwrite;";
    private static final String INJECT = MIXIN + "injection/Inject;";
    private static final String RETURN_VALUE = "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;";
    private static final String WRAP_METHOD = "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;";
    private static final String CALLBACK = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String RETURNABLE = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";
    private static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
    /** The points of a method as a whole, where a method Kiln adds (with no code of the game's) is as good as any. */
    private static final java.util.Set<String> WHOLE = java.util.Set.of("HEAD", "RETURN", "TAIL");
    /** Mixin's pattern for an accessor's or invoker's name (its AccessorInfo.AccessorName). */
    private static final Pattern NAME = Pattern.compile("^(get|is|set|call|invoke|new|create)(([A-Z])(.*?))(_\\$md.*)?$");

    /** The access of the method {@link #inherited} last found (its caller reads it right after). */
    private static final ThreadLocal<Integer> INHERITED_ACCESS = ThreadLocal.withInitial(() -> 0);

    private MixinStandIns() {}

    /** Adds to {@code target} (a stand-in) what {@code mixins} name and it lacks; what was added, by name. */
    static List<String> fit(ClassNode target, List<? extends IMixinInfo> mixins) {
        List<String> added = new ArrayList<>();
        for (IMixinInfo info : mixins) {
            ClassNode mixin;
            try {
                mixin = info.getClassNode(0);
            } catch (RuntimeException e) {
                continue;
            }
            if (mixin == null) {
                continue;
            }
            String who = MixinErrors.who(info);
            for (FieldNode f : mixin.fields) {
                AnnotationNode shadow = annotation(f.visibleAnnotations, f.invisibleAnnotations, SHADOW);
                if (shadow != null) {
                    String name = unprefixed(f.name, shadow);
                    if (field(target, name) == null && inherited(target.superName, name, null) == null) {
                        addField(target, name, f.desc, (f.access & Opcodes.ACC_STATIC) != 0, added, who);
                    }
                }
            }
            for (MethodNode m : mixin.methods) {
                AnnotationNode shadow = annotation(m.visibleAnnotations, m.invisibleAnnotations, SHADOW);
                AnnotationNode accessor = annotation(m.visibleAnnotations, m.invisibleAnnotations, ACCESSOR);
                AnnotationNode invoker = annotation(m.visibleAnnotations, m.invisibleAnnotations, INVOKER);
                boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
                AnnotationNode overwrite = annotation(m.visibleAnnotations, m.invisibleAnnotations, OVERWRITE);
                if (overwrite != null) {
                    // The game's method the mixin replaces: added, then Mixin puts the mod's code in it.
                    if (!m.name.startsWith("<") && method(target, m.name, m.desc) == null) {
                        addMethod(target, m.name, m.desc, m.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED
                                | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC), added, who);
                    }
                    continue;
                }
                for (String[] wanted : injectedInto(m)) {
                    String name = wanted[0];
                    String desc = wanted[1];
                    if (desc == null ? hasNamed(target, name) : method(target, name, desc) != null) {
                        continue;
                    }
                    if (desc == null) {
                        desc = inferred(m);
                    }
                    // A @ModifyReturnValue handler may be static for an instance method: only the others say.
                    boolean returnValue = annotation(m.visibleAnnotations, m.invisibleAnnotations, RETURN_VALUE) != null;
                    if (desc != null) {
                        addMethod(target, name, desc, isStatic && !returnValue ? Opcodes.ACC_STATIC : 0, added, who);
                    }
                }
                if (shadow != null) {
                    String name = unprefixed(m.name, shadow);
                    if (!name.equals("<init>") && method(target, name, m.desc) == null) {
                        addMethod(target, name, m.desc, isStatic ? Opcodes.ACC_STATIC : 0, added, who);
                    }
                } else if (accessor != null) {
                    String name = named(accessor, m.name);
                    Type[] args = Type.getArgumentTypes(m.desc);
                    Type ret = Type.getReturnType(m.desc);
                    String desc = ret == Type.VOID_TYPE ? (args.length == 1 ? args[0].getDescriptor() : null)
                            : ret.getDescriptor();
                    if (name != null && desc != null && field(target, name) == null
                            && inherited(target.superName, name, null) == null) {
                        addField(target, name, desc, isStatic, added, who);
                    }
                } else if (invoker != null) {
                    String name = named(invoker, m.name);
                    // A constructor invoker names <init> or the target itself: making constructors is for the
                    // stand-in's author, as the generator's report says.
                    if (name != null && !name.equals("<init>") && !name.equals(target.name)
                            && !name.equals(target.name.replace('/', '.')) && !(isStatic
                            && Type.getReturnType(m.desc).getInternalName().equals(target.name)
                            && (m.name.startsWith("new") || m.name.startsWith("create")))
                            && method(target, name, m.desc) == null) {
                        addMethod(target, name, m.desc, isStatic ? Opcodes.ACC_STATIC : 0, added, who);
                    }
                }
            }
        }
        return added;
    }

    /**
     * The methods an injector that takes a method as a whole names ({@code @Inject} at HEAD, RETURN or TAIL,
     * MixinExtras' {@code @ModifyReturnValue} and {@code @WrapMethod}), as name and descriptor (null when its
     * selector gives none). One that hooks a point inside a method needs that method's code, which an added method
     * hasn't: none. Wildcards, patterns and constructors aren't for adding.
     */
    private static List<String[]> injectedInto(MethodNode m) {
        AnnotationNode a = annotation(m.visibleAnnotations, m.invisibleAnnotations, INJECT);
        if (a != null) {
            Object at = value(a, "at");
            List<?> ats = at instanceof List<?> l ? l : at == null ? List.of() : List.of(at);
            if (ats.isEmpty()) {
                return List.of();
            }
            for (Object o : ats) {
                Object point = o instanceof AnnotationNode n ? value(n, "value") : null;
                if (!(point instanceof String p) || !WHOLE.contains(p)) {
                    return List.of();
                }
            }
        } else {
            a = annotation(m.visibleAnnotations, m.invisibleAnnotations, RETURN_VALUE);
            if (a != null) {
                Object at = value(a, "at");
                List<?> ats = at instanceof List<?> l ? l : at == null ? List.of() : List.of(at);
                for (Object o : ats) {
                    Object point = o instanceof AnnotationNode n ? value(n, "value") : null;
                    if (!(point instanceof String p) || !WHOLE.contains(p)) {
                        return List.of();
                    }
                }
            } else {
                a = annotation(m.visibleAnnotations, m.invisibleAnnotations, WRAP_METHOD);
            }
        }
        if (a == null) {
            return List.of();
        }
        Object methods = value(a, "method");
        List<?> selectors = methods instanceof List<?> l ? l : methods == null ? List.of() : List.of(methods);
        List<String[]> found = new ArrayList<>();
        for (Object o : selectors) {
            if (!(o instanceof String s) || s.isEmpty() || s.startsWith("/") || s.contains("*")) {
                continue;
            }
            if (s.startsWith("L") && s.indexOf(';') > 0) {
                s = s.substring(s.indexOf(';') + 1);
            }
            String name = s.contains("(") ? s.substring(0, s.indexOf('(')) : s;
            String desc = s.contains("(") ? s.substring(s.indexOf('(')) : null;
            if (name.isEmpty() || name.startsWith("<") || (desc != null && !desc.matches("\\(.*\\).+"))) {
                continue;
            }
            found.add(new String[] {name, desc});
        }
        return found;
    }

    /**
     * A handler's target's descriptor, when its selector gives only a name: an {@code @Inject}'s arguments before its
     * callback, and what its {@code CallbackInfoReturnable} holds (the generic's type, a primitive for a boxed one);
     * a {@code @ModifyReturnValue}'s value and the arguments after it; a {@code @WrapMethod}'s arguments before its
     * operation, and what it returns.
     */
    private static String inferred(MethodNode m) {
        Type[] args = Type.getArgumentTypes(m.desc);
        StringBuilder params = new StringBuilder("(");
        if (annotation(m.visibleAnnotations, m.invisibleAnnotations, INJECT) != null) {
            for (Type arg : args) {
                if (arg.getDescriptor().equals(CALLBACK)) {
                    return params.append(")V").toString();
                }
                if (arg.getDescriptor().equals(RETURNABLE)) {
                    return params.append(")").append(returned(m.signature)).toString();
                }
                params.append(arg.getDescriptor());
            }
            return null;
        }
        if (annotation(m.visibleAnnotations, m.invisibleAnnotations, RETURN_VALUE) != null) {
            if (args.length == 0) {
                return null;
            }
            for (int i = 1; i < args.length; i++) {
                params.append(args[i].getDescriptor());
            }
            return params.append(")").append(args[0].getDescriptor()).toString();
        }
        for (Type arg : args) {
            if (arg.getDescriptor().equals(OPERATION)) {
                return params.append(")").append(Type.getReturnType(m.desc).getDescriptor()).toString();
            }
            params.append(arg.getDescriptor());
        }
        return null;
    }

    /** What a handler's {@code CallbackInfoReturnable<T>} holds, from its generic signature: T, unboxed. */
    private static String returned(String signature) {
        String marker = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable<";
        int at = signature == null ? -1 : signature.indexOf(marker);
        if (at < 0) {
            return "Ljava/lang/Object;";
        }
        int start = at + marker.length();
        int depth = 0;
        StringBuilder type = new StringBuilder();
        for (int i = start; i < signature.length(); i++) {
            char c = signature.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                if (depth == 0) {
                    break;
                }
                depth--;
            } else if (depth == 0) {
                type.append(c);
            }
        }
        String t = type.toString();
        if (t.startsWith("T") || t.startsWith("*") || t.startsWith("+") || t.startsWith("-")) {
            return "Ljava/lang/Object;";
        }
        return switch (t) {
            case "Ljava/lang/Boolean;" -> "Z";
            case "Ljava/lang/Integer;" -> "I";
            case "Ljava/lang/Long;" -> "J";
            case "Ljava/lang/Float;" -> "F";
            case "Ljava/lang/Double;" -> "D";
            case "Ljava/lang/Short;" -> "S";
            case "Ljava/lang/Byte;" -> "B";
            case "Ljava/lang/Character;" -> "C";
            default -> t;
        };
    }

    private static boolean hasNamed(ClassNode c, String name) {
        for (MethodNode m : c.methods) {
            if (m.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** The member an accessor or invoker names: its value (a name or a selector), else its own name's. */
    private static String named(AnnotationNode a, String method) {
        Object value = value(a, "value");
        if (value instanceof String s && !s.isEmpty()) {
            if (s.startsWith("L") && s.indexOf(';') > 0) {
                s = s.substring(s.indexOf(';') + 1);
            }
            int stop = s.length();
            for (char c : new char[] {'(', ':'}) {
                if (s.indexOf(c) >= 0) {
                    stop = Math.min(stop, s.indexOf(c));
                }
            }
            return s.substring(0, stop);
        }
        Matcher m = NAME.matcher(method);
        if (!m.matches()) {
            return null;
        }
        String rest = m.group(2);
        return rest.equals(rest.toUpperCase(java.util.Locale.ROOT)) ? rest
                : Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
    }

    /** A {@code @Shadow}'s name without its prefix ({@code shadow$} unless the annotation names another). */
    private static String unprefixed(String name, AnnotationNode shadow) {
        Object prefix = value(shadow, "prefix");
        String p = prefix instanceof String s ? s : "shadow$";
        return name.startsWith(p) ? name.substring(p.length()) : name;
    }

    private static void addField(ClassNode target, String name, String desc, boolean isStatic, List<String> added,
            String who) {
        target.fields.add(new FieldNode(Opcodes.ACC_PROTECTED | (isStatic ? Opcodes.ACC_STATIC : 0), name, desc,
                null, null));
        added.add(name + " (a field, for " + who + ")");
    }

    /** A method added: {@code access} its static flag and, when it has one, its visibility (protected otherwise). */
    private static void addMethod(ClassNode target, String name, String desc, int access, List<String> added,
            String who) {
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        int visibility = access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
        MethodNode m = new MethodNode((visibility == 0 ? Opcodes.ACC_PROTECTED : visibility)
                | (isStatic ? Opcodes.ACC_STATIC : 0), name, desc, null, null);
        // Its sizes, as a compiler would give them: Mixin puts its own locals (a callback) after the arguments.
        m.maxLocals = Type.getArgumentsAndReturnSizes(desc) >> 2;
        if (isStatic) {
            m.maxLocals--;
        }
        m.maxStack = Math.max(m.maxLocals, 2) + 1;
        InsnList code = m.instructions;
        // Declared by a class the stand-in extends (where the game declares it in the class itself): the method calls
        // that one, so the stand-in's code stays what it was.
        String owner = inherited(target.superName, name, desc);
        if (owner != null) {
            // It overrides that one: never less visible, or other classes' calls to it fail (IllegalAccessError) where
            // they reached the inherited one before (D-101).
            int inheritedAccess = INHERITED_ACCESS.get();
            if ((inheritedAccess & Opcodes.ACC_PUBLIC) != 0) {
                m.access = (m.access & ~(Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE)) | Opcodes.ACC_PUBLIC;
            } else if ((inheritedAccess & Opcodes.ACC_PROTECTED) != 0 && (m.access & Opcodes.ACC_PUBLIC) == 0) {
                m.access = (m.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PROTECTED;
            }
            int slot = 0;
            if (!isStatic) {
                code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                slot = 1;
            }
            for (Type arg : Type.getArgumentTypes(desc)) {
                code.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), slot));
                slot += arg.getSize();
            }
            code.add(new MethodInsnNode(isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL, owner, name, desc,
                    false));
            code.add(new InsnNode(Type.getReturnType(desc).getOpcode(Opcodes.IRETURN)));
            target.methods.add(m);
            added.add(name + desc + " (calling " + owner.replace('/', '.') + "'s, for " + who + ")");
            return;
        }
        code.add(new LdcInsnNode(target.name.replace('/', '.') + "." + name));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "kiln/Missing", "called", "(Ljava/lang/String;)V", false));
        Type ret = Type.getReturnType(desc);
        switch (ret.getSort()) {
            case Type.VOID -> code.add(new InsnNode(Opcodes.RETURN));
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> {
                code.add(new InsnNode(Opcodes.ICONST_0));
                code.add(new InsnNode(Opcodes.IRETURN));
            }
            case Type.LONG -> {
                code.add(new InsnNode(Opcodes.LCONST_0));
                code.add(new InsnNode(Opcodes.LRETURN));
            }
            case Type.FLOAT -> {
                code.add(new InsnNode(Opcodes.FCONST_0));
                code.add(new InsnNode(Opcodes.FRETURN));
            }
            case Type.DOUBLE -> {
                code.add(new InsnNode(Opcodes.DCONST_0));
                code.add(new InsnNode(Opcodes.DRETURN));
            }
            default -> {
                code.add(new InsnNode(Opcodes.ACONST_NULL));
                code.add(new InsnNode(Opcodes.ARETURN));
            }
        }
        target.methods.add(m);
        added.add(name + desc + " (a method, for " + who + ")");
    }

    /**
     * The class above {@code start} (itself or one it extends) that declares the member, not private: a method by name
     * and descriptor, a field ({@code desc} null) by name; null when none does. Read from the class files, nothing
     * loaded.
     */
    private static String inherited(String start, String name, String desc) {
        for (String c = start; c != null && !c.equals("java/lang/Object"); ) {
            ClassNode node = new ClassNode();
            try {
                new org.objectweb.asm.ClassReader(MixinService.bytes(c)).accept(node,
                        org.objectweb.asm.ClassReader.SKIP_CODE);
            } catch (Exception e) {
                return null;
            }
            if (desc == null) {
                FieldNode f = field(node, name);
                if (f != null) {
                    return (f.access & Opcodes.ACC_PRIVATE) == 0 ? c : null;
                }
            } else {
                MethodNode m = method(node, name, desc);
                if (m != null) {
                    INHERITED_ACCESS.set(m.access);
                    return (m.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_ABSTRACT)) == 0 ? c : null;
                }
            }
            c = node.superName;
        }
        return null;
    }

    private static FieldNode field(ClassNode c, String name) {
        for (FieldNode f : c.fields) {
            if (f.name.equals(name)) {
                return f;
            }
        }
        return null;
    }

    private static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return m;
            }
        }
        return null;
    }

    static AnnotationNode annotation(List<AnnotationNode> visible, List<AnnotationNode> invisible, String desc) {
        for (List<AnnotationNode> list : java.util.Arrays.asList(visible, invisible)) {
            if (list != null) {
                for (AnnotationNode a : list) {
                    if (a.desc.equals(desc)) {
                        return a;
                    }
                }
            }
        }
        return null;
    }

    static Object value(AnnotationNode a, String key) {
        if (a != null && a.values != null) {
            for (int i = 0; i + 1 < a.values.size(); i += 2) {
                if (a.values.get(i).equals(key)) {
                    return a.values.get(i + 1);
                }
            }
        }
        return null;
    }
}
